package com.arlix.shadowvault.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arlix.shadowvault.crypto.constantTimeEquals
import com.arlix.shadowvault.domain.IVaultRepository
import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.domain.VaultFileException
import com.arlix.shadowvault.domain.VaultRules
import com.arlix.shadowvault.domain.usecase.LockVaultUseCase
import com.arlix.shadowvault.domain.usecase.UnlockVaultUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class VaultUiState {
    /** First launch: no vault file exists yet. */
    data class Setup(val isColdVault: Boolean = false) : VaultUiState()

    /** Vault exists and is locked, waiting for the passphrase. */
    object Locked : VaultUiState()

    /** Key derivation and SQLCipher open in progress. */
    object Unlocking : VaultUiState()

    /**
     * Vault is open. [revision] changes on every publish so the UI always receives fresh
     * entries (StateFlow would otherwise skip "equal" states, and entries compare by id only).
     */
    data class Unlocked(
        val entries: List<VaultEntry>,
        val isColdVault: Boolean,
        val revision: Long = 0L
    ) : VaultUiState()

    /**
     * A problem shown on the LOCK screen (wrong passphrase, damaged vault, ...). The vault is
     * always closed while this is shown. Problems while unlocked use [VaultViewModel.userMessage].
     */
    data class Error(val message: String, val isSetupMode: Boolean = false) : VaultUiState()
}

class VaultViewModel(
    private val unlockVaultUseCase: UnlockVaultUseCase,
    private val lockVaultUseCase: LockVaultUseCase,
    private val vaultRepository: IVaultRepository,
    private val saltProvider: (isColdVault: Boolean) -> ByteArray,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private companion object {
        const val MSG_WRONG = "Incorrect Password or Corrupted Vault."
        const val MSG_WRONG_COLD = "Incorrect Cold Vault Key or Corrupted Vault."
        const val MSG_MEMORY = "Not enough free memory to unlock. Close other apps and try again."
        const val MSG_FILES = "A vault key file is missing or damaged. Restore from a backup."
        const val MSG_LIBRARY = "The encryption library failed to load. Reinstall the app."
    }

    private val _uiState = MutableStateFlow<VaultUiState>(VaultUiState.Locked)
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    /** Errors from cold-vault operations while the dashboard is showing (must not navigate away). */
    private val _coldVaultError = MutableStateFlow<String?>(null)
    val coldVaultError: StateFlow<String?> = _coldVaultError.asStateFlow()

    /** One-shot message for failures while the vault is unlocked (shown as a toast, then cleared). */
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private var currentEntries: List<VaultEntry> = emptyList()
    private var openVaultIsCold = false // which vault is actually open right now
    private var revision = 0L
    private var dbJob: Job? = null
    private var unlockJob: Job? = null

    /** True while a cold-vault open/create runs; the UI shows a spinner and blocks a second attempt. */
    private val _coldVaultBusy = MutableStateFlow(false)
    val coldVaultBusy: StateFlow<Boolean> = _coldVaultBusy.asStateFlow()

    /** True while switching hot to cold; the hot reader's errors are expected then. */
    @Volatile private var switchingVaults = false

    init {
        if (!vaultRepository.vaultExists(isColdVault = false)) {
            _uiState.value = VaultUiState.Setup(isColdVault = false)
        }
        viewModelScope.launch {
            lockVaultUseCase.lockEvents.collect {
                resetToLocked()
                // Safety net: an unlock that finished opening in the instant between the lock's
                // close and this event must not stay open behind the lock screen.
                vaultRepository.closeVault()
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun isLockedOrSetup(): Boolean =
        _uiState.value.let { it is VaultUiState.Locked || it is VaultUiState.Setup }

    /** Publishes the current entries to the UI. The category filter only applies to the hot vault. */
    private fun publishUnlocked() {
        val cat = _selectedCategory.value
        val shown = if (openVaultIsCold || cat == "All") currentEntries
        else currentEntries.filter { it.category == cat }
        _uiState.value = VaultUiState.Unlocked(shown, openVaultIsCold, ++revision)
    }

    private fun wipeCurrentEntries() {
        currentEntries.forEach { it.annihilate() }
        currentEntries = emptyList()
    }

    /** Wipes everything held in memory and returns to the lock (or setup) screen. */
    private fun resetToLocked(cancelUnlockJob: Boolean = true) {
        if (cancelUnlockJob) {
            unlockJob?.cancel()
            unlockJob = null
        }
        dbJob?.cancel()
        dbJob = null
        wipeCurrentEntries()
        openVaultIsCold = false
        _selectedCategory.value = "All"
        _coldVaultError.value = null
        _uiState.value = if (vaultRepository.vaultExists()) VaultUiState.Locked
        else VaultUiState.Setup()
    }

    /** Shows [message] on the lock screen after an unlock attempt failed. */
    private fun failHot(message: String) {
        if (isLockedOrSetup()) return
        _uiState.value = VaultUiState.Error(
            message,
            isSetupMode = !vaultRepository.vaultExists(isColdVault = false)
        )
    }

    /**
     * Closes the vault for real, wipes memory, and shows [message] on the lock screen, so the
     * lock screen is never shown while the vault is still open.
     */
    private suspend fun lockWithMessage(message: String) {
        vaultRepository.closeVault()
        resetToLocked()
        _uiState.value = VaultUiState.Error(message, isSetupMode = false)
    }

    /** Maps a failure to a message without revealing anything beyond the failure class. */
    private fun failureMessage(e: Throwable, isCold: Boolean): String = when (e) {
        is OutOfMemoryError -> MSG_MEMORY
        is VaultFileException -> MSG_FILES
        is LinkageError -> MSG_LIBRARY // e.g. a native library failed to load
        else -> if (isCold) MSG_WRONG_COLD else MSG_WRONG
    }

    /** Errors other than these are real bugs and must not be swallowed. */
    private fun isHandledError(e: Throwable): Boolean =
        e !is Error || e is OutOfMemoryError || e is LinkageError

    /**
     * Starts reading the open vault. New entries are published to the UI FIRST, and only then
     * are the old arrays wiped, so the UI never reads a wiped password.
     */
    private fun startCollecting(isCold: Boolean) {
        openVaultIsCold = isCold
        dbJob?.cancel()
        dbJob = viewModelScope.launch {
            try {
                vaultRepository.getAllEntries().collect { entries ->
                    val old = currentEntries
                    currentEntries = entries
                    if (!isLockedOrSetup()) publishUnlocked()
                    old.forEach { it.annihilate() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // If the vault was closed on purpose (lock / vault switch) this is expected.
                if (!switchingVaults && vaultRepository.isVaultOpen() && !isLockedOrSetup()) {
                    if (isCold) _coldVaultError.value = "Failed to load cold vault entries."
                    else lockWithMessage("Vault read error. Please unlock again.")
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Category filter
    // ------------------------------------------------------------------

    fun setCategoryFilter(category: String) {
        _selectedCategory.value = category
        if (_uiState.value is VaultUiState.Unlocked && !openVaultIsCold) publishUnlocked()
    }

    // ------------------------------------------------------------------
    // Create vault
    // ------------------------------------------------------------------

    fun createVault(password: CharArray, confirmPassword: CharArray, isColdVault: Boolean = false) {
        val error = when {
            password.size < VaultRules.MIN_PASSPHRASE_LENGTH ->
                "Passphrase must be at least ${VaultRules.MIN_PASSPHRASE_LENGTH} characters."
            !constantTimeEquals(password, confirmPassword) ->
                "Passphrases do not match. Please try again."
            else -> null
        }
        confirmPassword.fill('\u0000')

        if (error != null) {
            if (isColdVault) _coldVaultError.value = error // stay on the dashboard
            else _uiState.value = VaultUiState.Error(error, isSetupMode = true)
            password.fill('\u0000')
            return
        }

        if (isColdVault) {
            if (_coldVaultBusy.value) { password.fill('\u0000'); return }
            unlockColdVaultInternal(password)
        } else {
            unlockHotVaultInternal(password)
        }
    }

    // ------------------------------------------------------------------
    // Unlock
    // ------------------------------------------------------------------

    fun unlock(password: CharArray, isColdVault: Boolean = false) {
        if (isColdVault) {
            if (_coldVaultBusy.value) { password.fill('\u0000'); return }
            unlockColdVaultInternal(password)
        } else {
            if (_uiState.value is VaultUiState.Unlocking) { password.fill('\u0000'); return }
            unlockHotVaultInternal(password)
        }
    }

    private fun unlockHotVaultInternal(password: CharArray) {
        _uiState.value = VaultUiState.Unlocking

        unlockJob = viewModelScope.launch {
            try {
                // Salt file I/O stays off the main thread.
                val salt = withContext(ioDispatcher) { saltProvider(false) }
                unlockVaultUseCase(password, salt, isColdVault = false)
                startCollecting(isCold = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!isHandledError(e)) throw e
                failHot(failureMessage(e, isCold = false))
            } finally {
                password.fill('\u0000') // also covers a cancel before the use case ran
            }
        }
    }

    /**
     * Opens or creates the cold vault without leaving the dashboard. The hot vault stays open
     * until the cold key is proven correct, so a wrong cold key only shows an error.
     */
    private fun unlockColdVaultInternal(password: CharArray) {
        _coldVaultBusy.value = true
        switchingVaults = true
        _coldVaultError.value = null

        unlockJob = viewModelScope.launch {
            try {
                val salt = withContext(ioDispatcher) { saltProvider(true) }
                unlockVaultUseCase(password, salt, isColdVault = true)
                // Success: the repository swapped to the cold DB and closed the hot one.
                startCollecting(isCold = true) // also cancels the old hot reader
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!isHandledError(e)) throw e
                // The hot vault is untouched and still open: show the message, stay on the dashboard.
                _coldVaultError.value = failureMessage(e, isCold = true)
            } finally {
                password.fill('\u0000')
                switchingVaults = false
                _coldVaultBusy.value = false
            }
        }
    }

    // ------------------------------------------------------------------
    // Lock
    // ------------------------------------------------------------------

    fun lock() {
        viewModelScope.launch { lockVaultUseCase() }
    }

    // ------------------------------------------------------------------
    // Cold vault helpers
    // ------------------------------------------------------------------

    fun coldVaultExists(): Boolean = vaultRepository.vaultExists(isColdVault = true)

    fun clearColdVaultError() { _coldVaultError.value = null }

    fun clearUserMessage() { _userMessage.value = null }

    // ------------------------------------------------------------------
    // Credentials. The Room Flow re-emits after every write, so the list refreshes by itself.
    // ------------------------------------------------------------------

    /** Saves a new entry, or overwrites an existing one with the same id (used by Edit). */
    fun addCredential(entry: VaultEntry) {
        viewModelScope.launch {
            try {
                vaultRepository.addEntry(entry)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The vault stays unlocked; only report the failure (e.g. storage full).
                if (!isLockedOrSetup()) _userMessage.value = "Could not save the credential. Is storage full?"
            } finally {
                entry.annihilate()
            }
        }
    }

    fun updateCredential(entry: VaultEntry) = addCredential(entry)

    fun deleteCredential(entryId: String) {
        viewModelScope.launch {
            try {
                vaultRepository.deleteEntry(entryId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isLockedOrSetup()) _userMessage.value = "Could not delete the credential."
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        wipeCurrentEntries()
    }

    class Factory(
        private val unlockVaultUseCase: UnlockVaultUseCase,
        private val lockVaultUseCase: LockVaultUseCase,
        private val vaultRepository: IVaultRepository,
        private val saltProvider: (isColdVault: Boolean) -> ByteArray
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VaultViewModel(unlockVaultUseCase, lockVaultUseCase, vaultRepository, saltProvider) as T
    }
}
