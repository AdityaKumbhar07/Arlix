package com.arlix.shadowvault.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arlix.shadowvault.crypto.constantTimeEquals
import com.arlix.shadowvault.domain.IVaultRepository
import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.domain.usecase.LockVaultUseCase
import com.arlix.shadowvault.domain.usecase.UnlockVaultUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class VaultUiState {
    /** First launch: no vault file exists yet. */
    data class Setup(val isColdVault: Boolean = false) : VaultUiState()

    /** Vault exists, waiting for the passphrase. */
    object Locked : VaultUiState()

    /** Key derivation + SQLCipher open in progress. */
    object Unlocking : VaultUiState()

    /**
     * Vault is open. [revision] changes on every publish so the UI always receives the
     * fresh entries (StateFlow would otherwise skip "equal" states, and entry equality is by id only).
     */
    data class Unlocked(
        val entries: List<VaultEntry>,
        val isColdVault: Boolean,
        val revision: Long = 0L
    ) : VaultUiState()

    /** Wrong password, corrupted vault, failed save, etc. Shown on the LockScreen. */
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
        const val MIN_PASSPHRASE_LENGTH = 5
        const val MSG_WRONG = "Incorrect Password or Corrupted Vault."
        const val MSG_WRONG_COLD = "Incorrect Cold Vault Key or Corrupted Vault."
        const val MSG_MEMORY = "Not enough free memory to unlock. Close other apps and try again."
    }

    private val _uiState = MutableStateFlow<VaultUiState>(VaultUiState.Locked)
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    /** Errors from cold-vault operations while the Dashboard is showing (must not navigate away). */
    private val _coldVaultError = MutableStateFlow<String?>(null)
    val coldVaultError: StateFlow<String?> = _coldVaultError.asStateFlow()

    private var currentEntries: List<VaultEntry> = emptyList()
    private var openVaultIsCold = false   // which vault is actually open right now
    private var revision = 0L
    private var dbJob: Job? = null
    private var unlockJob: Job? = null

    /** Guard against re-entrant cold vault operations. */
    @Volatile private var coldVaultInProgress = false
    /** True while switching hot to cold; the hot reader's errors are expected then. */
    @Volatile private var switchingVaults = false

    init {
        if (!vaultRepository.vaultExists(isColdVault = false)) {
            _uiState.value = VaultUiState.Setup(isColdVault = false)
        }
        viewModelScope.launch {
            lockVaultUseCase.lockEvents.collect { resetToLocked() }
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

    private fun failHot(message: String) {
        if (isLockedOrSetup()) return
        _uiState.value = VaultUiState.Error(
            message,
            isSetupMode = !vaultRepository.vaultExists(isColdVault = false)
        )
    }

    /**
     * Starts reading the open vault. New entries are published to the UI FIRST,
     * and only then are the old arrays wiped, so the UI never reads a wiped password.
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
                    else _uiState.value = VaultUiState.Error("Vault read error. Please re-unlock.")
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
        // [T20] Passphrase strength is checked at creation only.
        val error = when {
            password.size < MIN_PASSPHRASE_LENGTH ->
                "Passphrase must be at least $MIN_PASSPHRASE_LENGTH characters."
            !constantTimeEquals(password, confirmPassword) ->
                "Passphrases do not match. Please try again."
            else -> null
        }
        confirmPassword.fill('\u0000')

        if (error != null) {
            if (isColdVault) _coldVaultError.value = error   // stay on the Dashboard
            else _uiState.value = VaultUiState.Error(error, isSetupMode = true)
            password.fill('\u0000')
            return
        }

        if (isColdVault) {
            if (coldVaultInProgress) { password.fill('\u0000'); return }
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
            if (coldVaultInProgress) { password.fill('\u0000'); return }
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
            } catch (e: OutOfMemoryError) {
                failHot(MSG_MEMORY)
            } catch (e: Exception) {
                failHot(MSG_WRONG)
            } finally {
                password.fill('\u0000') // also covers a cancel before the use case ran
            }
        }
    }

    /**
     * Opens or creates the Cold Vault without navigating away from the Dashboard.
     * The hot vault stays open until the cold key is proven correct, so a wrong
     * cold key only shows an error and leaves the user where they are.
     */
    private fun unlockColdVaultInternal(password: CharArray) {
        coldVaultInProgress = true
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
                if (e is Error && e !is OutOfMemoryError) throw e
                // Hot vault is untouched and still open: show the message, stay on the Dashboard.
                _coldVaultError.value = if (e is OutOfMemoryError) MSG_MEMORY else MSG_WRONG_COLD
            } finally {
                password.fill('\u0000')
                switchingVaults = false
                coldVaultInProgress = false
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
                if (!isLockedOrSetup()) _uiState.value = VaultUiState.Error("Failed to save credential.")
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
                if (!isLockedOrSetup()) _uiState.value = VaultUiState.Error("Failed to delete credential.")
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
