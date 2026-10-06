package com.arlix.shadowvault.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arlix.shadowvault.crypto.BackupCodec
import com.arlix.shadowvault.crypto.BackupFormatException
import com.arlix.shadowvault.crypto.BackupKindMismatchException
import com.arlix.shadowvault.crypto.BackupPassphraseException
import com.arlix.shadowvault.crypto.constantTimeEquals
import com.arlix.shadowvault.domain.IVaultRepository
import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.domain.VaultRules
import com.arlix.shadowvault.domain.WrongPassphraseException
import com.arlix.shadowvault.domain.usecase.ChangePassphraseUseCase
import com.arlix.shadowvault.domain.usecase.LockVaultUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Change passphrase, backup and restore for the OPEN vault (hot or cold).
 *
 * The system file picker backgrounds the app, which locks the vault. So:
 *  - a backup is fully encrypted BEFORE the picker opens, and only that ciphertext waits
 *    for the user's choice of location;
 *  - a restore keeps the picked (still encrypted) file until the vault it was started from
 *    is unlocked again, then asks for the backup passphrase.
 * Ciphertext is safe to keep in memory; plaintext entries and passphrases never are.
 */
class MaintenanceViewModel(
    private val changePassphraseUseCase: ChangePassphraseUseCase,
    private val lockVaultUseCase: LockVaultUseCase,
    private val vaultRepository: IVaultRepository,
    private val backupCodec: BackupCodec,
    private val saltProvider: (isColdVault: Boolean) -> ByteArray,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private companion object {
        const val MSG_MEMORY = "Not enough free memory. Close other apps and try again."
    }

    /** True while an operation runs; the UI shows a spinner and blocks a second one. */
    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    /** Short result messages for the UI to toast. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Emits the suggested file name once the encrypted backup is ready for the file picker. */
    private val _exportReady = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val exportReady: SharedFlow<String> = _exportReady.asSharedFlow()

    /** Which vault (true = cold) a picked backup should be restored into; null = none pending. */
    private val _restoreTarget = MutableStateFlow<Boolean?>(null)
    val restoreTarget: StateFlow<Boolean?> = _restoreTarget.asStateFlow()

    private var pendingBackup: ByteArray? = null
    private var pendingRestore: ByteArray? = null
    private var requestedRestoreIsCold = false
    private var job: Job? = null

    init {
        // A lock cancels a running operation. Pending ciphertext is kept on purpose (see above).
        viewModelScope.launch {
            lockVaultUseCase.lockEvents.collect {
                job?.cancel()
                job = null
                _working.value = false
            }
        }
    }

    fun report(message: String) {
        _messages.tryEmit(message)
    }

    private fun isHandled(e: Throwable) = e !is Error || e is OutOfMemoryError || e is LinkageError

    // ------------------------------------------------------------------
    // Change passphrase
    // ------------------------------------------------------------------

    fun changePassphrase(current: CharArray, new: CharArray, confirm: CharArray, isColdVault: Boolean) {
        if (_working.value) {
            current.fill('\u0000'); new.fill('\u0000'); confirm.fill('\u0000')
            return
        }
        val error = when {
            new.size < VaultRules.MIN_PASSPHRASE_LENGTH ->
                "New passphrase must be at least ${VaultRules.MIN_PASSPHRASE_LENGTH} characters."
            !constantTimeEquals(new, confirm) -> "New passphrases do not match."
            constantTimeEquals(current, new) -> "The new passphrase must be different."
            else -> null
        }
        confirm.fill('\u0000')
        if (error != null) {
            current.fill('\u0000'); new.fill('\u0000')
            report(error)
            return
        }

        _working.value = true
        job = viewModelScope.launch {
            try {
                val salt = withContext(ioDispatcher) { saltProvider(isColdVault) }
                changePassphraseUseCase(current, new, salt, isColdVault)
                // The repository has closed the vault; lock the UI. The user unlocks with the
                // new passphrase, which also proves that it works.
                report("Passphrase changed. Unlock with the new passphrase.")
                lockVaultUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (e: WrongPassphraseException) {
                report("Current passphrase is incorrect.")
            } catch (e: Throwable) {
                if (!isHandled(e)) throw e
                // Nothing live is modified before the final file swap. The vault may have been
                // closed by a failed swap, so lock for real and let the user unlock again.
                report(
                    if (e is OutOfMemoryError) MSG_MEMORY
                    else "Could not change the passphrase. Your vault was not modified; unlock again."
                )
                lockVaultUseCase()
            } finally {
                current.fill('\u0000')
                new.fill('\u0000')
                _working.value = false
            }
        }
    }

    // ------------------------------------------------------------------
    // Backup
    // ------------------------------------------------------------------

    /** Encrypts the open vault's entries, then asks the UI to open the file picker. */
    fun prepareBackup(passphrase: CharArray, confirm: CharArray, isColdVault: Boolean) {
        if (_working.value) {
            passphrase.fill('\u0000'); confirm.fill('\u0000')
            return
        }
        val error = when {
            passphrase.size < VaultRules.MIN_BACKUP_PASSPHRASE_LENGTH ->
                "Backup passphrase must be at least ${VaultRules.MIN_BACKUP_PASSPHRASE_LENGTH} characters."
            !constantTimeEquals(passphrase, confirm) -> "Passphrases do not match."
            else -> null
        }
        confirm.fill('\u0000')
        if (error != null) {
            passphrase.fill('\u0000')
            report(error)
            return
        }

        _working.value = true
        job = viewModelScope.launch {
            var entries: List<VaultEntry> = emptyList()
            try {
                entries = vaultRepository.readAllEntries()
                pendingBackup = backupCodec.encrypt(entries, passphrase, isColdVault)
                _exportReady.tryEmit(backupFileName())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!isHandled(e)) throw e
                report(if (e is OutOfMemoryError) MSG_MEMORY else "Could not create the backup.")
            } finally {
                entries.forEach { it.annihilate() }
                passphrase.fill('\u0000')
                _working.value = false
            }
        }
    }

    /** Hands the encrypted backup to the UI exactly once (null if there is none). */
    fun takePendingBackup(): ByteArray? {
        val data = pendingBackup
        pendingBackup = null
        return data
    }

    /**
     * Same pattern for hot and cold on purpose: the name ends up in the cloud, so the vault
     * kind is stored inside the authenticated file header instead.
     */
    private fun backupFileName(): String =
        "arlix-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".arcx"

    // ------------------------------------------------------------------
    // Restore
    // ------------------------------------------------------------------

    /** Call before opening the file picker: remembers which vault the restore is for. */
    fun beginRestore(isColdVault: Boolean) {
        requestedRestoreIsCold = isColdVault
    }

    fun onRestoreFilePicked(bytes: ByteArray?) {
        if (bytes == null || !backupCodec.looksLikeBackup(bytes)) {
            report("That file is not a valid Arlix backup.")
            return
        }
        val backupIsCold = backupCodec.backupIsCold(bytes)
        if (backupIsCold != null && backupIsCold != requestedRestoreIsCold) {
            // Refused before any passphrase prompt: hot and cold data must never be mixed.
            report(BackupKindMismatchException(backupIsCold).message ?: "Wrong kind of vault.")
            return
        }
        pendingRestore = bytes
        _restoreTarget.value = requestedRestoreIsCold
        report(
            if (backupIsCold == null) {
                "Older backup without a vault label: it will go into the open vault, so make sure that is the right one. Enter its passphrase."
            } else {
                "Backup selected. Enter its passphrase to restore it."
            }
        )
    }

    fun cancelRestore() {
        pendingRestore = null
        _restoreTarget.value = null
    }

    /**
     * Merges the backup into the open vault inside ONE transaction: an entry is added if it
     * is new, or overwritten only if the backup's copy is newer. Nothing is ever deleted.
     */
    /**
     * Merges the backup into the open vault inside ONE transaction: an entry is added if it
     * is new, or overwritten only if the backup's copy is newer. Nothing is ever deleted.
     */
    fun restoreBackup(passphrase: CharArray) {
        val data = pendingRestore
        val targetIsCold = _restoreTarget.value
        if (data == null || targetIsCold == null || _working.value) {
            passphrase.fill('\u0000')
            return
        }
        _working.value = true
        job = viewModelScope.launch {
            var incoming: List<VaultEntry> = emptyList()
            var existing: List<VaultEntry> = emptyList()
            try {
                incoming = backupCodec.decrypt(data, passphrase, targetIsCold)
                existing = vaultRepository.readAllEntries()
                val known = existing.associate { it.id to it.modifiedAt }
                val toWrite = incoming.filter { e ->
                    val have = known[e.id]
                    have == null || e.modifiedAt > have
                }
                if (toWrite.isNotEmpty()) vaultRepository.addEntries(toWrite)
                cancelRestore()
                report("Restore complete: ${toWrite.size} added or updated, ${incoming.size - toWrite.size} already up to date.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupKindMismatchException) {
                cancelRestore()
                report(e.message ?: "Wrong kind of vault.")
            } catch (e: BackupPassphraseException) {
                report("Wrong backup passphrase, or the file is damaged.")
            } catch (e: BackupFormatException) {
                cancelRestore()
                report(e.message ?: "That file is not a valid Arlix backup.")
            } catch (e: Throwable) {
                if (!isHandled(e)) throw e
                report(if (e is OutOfMemoryError) MSG_MEMORY else "Could not restore the backup. Your vault was not modified.")
            } finally {
                incoming.forEach { it.annihilate() }
                existing.forEach { it.annihilate() }
                passphrase.fill('\u0000')
                _working.value = false
            }
        }
    }

    class Factory(
        private val changePassphraseUseCase: ChangePassphraseUseCase,
        private val lockVaultUseCase: LockVaultUseCase,
        private val vaultRepository: IVaultRepository,
        private val backupCodec: BackupCodec,
        private val saltProvider: (isColdVault: Boolean) -> ByteArray
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MaintenanceViewModel(
                changePassphraseUseCase, lockVaultUseCase, vaultRepository, backupCodec, saltProvider
            ) as T
    }
}
