package com.arlix.shadowvault.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arlix.shadowvault.domain.IVaultRepository
import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.domain.usecase.LockVaultUseCase
import com.arlix.shadowvault.domain.usecase.UnlockVaultUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.arlix.shadowvault.crypto.constantTimeEquals

// ---------------------------------------------------------------------------
// UI State — represents every screen the user can be on
// ---------------------------------------------------------------------------

sealed class VaultUiState {
    /**
     * The vault DB file does not exist yet — FIRST LAUNCH state.
     * LockScreen shows "Create Master Passphrase" with a confirm field.
     */
    data class Setup(val isColdVault: Boolean = false) : VaultUiState()

    /** Vault exists, waiting for the user to type their passphrase. */
    object Locked : VaultUiState()

    /** Argon2id derivation + SQLCipher file open in progress. */
    object Unlocking : VaultUiState()

    /** Vault is open, credential list available. */
    data class Unlocked(val entries: List<VaultEntry>, val isColdVault: Boolean) : VaultUiState()

    /** Add Credential form is visible. */
    data class AddingCredential(val isColdVault: Boolean) : VaultUiState()

    /**
     * An error occurred — wrong password, corrupted vault, etc.
     * The LockScreen shows this message and presents the UNLOCK button again.
     * NOTE: We do NOT automatically transition out of this state — the user must retry.
     * This ensures the error message stays visible long enough to be read (Bug 2 fix).
     */
    data class Error(val message: String) : VaultUiState()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class VaultViewModel(
    private val unlockVaultUseCase: UnlockVaultUseCase,
    private val lockVaultUseCase: LockVaultUseCase,
    private val vaultRepository: IVaultRepository,
    private val saltProvider: (isColdVault: Boolean) -> ByteArray
) : ViewModel() {

    private val _uiState = MutableStateFlow<VaultUiState>(VaultUiState.Locked)
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    /** Wipes every password CharArray in a list before the list reference is dropped. */
    private fun wipeEntries(entries: List<VaultEntry>) {
        entries.forEach { it.annihilate() }
    }

    /**
     * Cold vault operation error — used ONLY for errors that happen while the user is
     * already on the Dashboard (cold vault dialog). These must NOT change [_uiState]
     * because that would navigate away from the Dashboard to the LockScreen (Bug 1a fix).
     */
    private val _coldVaultError = MutableStateFlow<String?>(null)
    val coldVaultError: StateFlow<String?> = _coldVaultError.asStateFlow()

    private var currentEntries: List<VaultEntry> = emptyList()
    private var dbJob: Job? = null

    /**
     * Guard against re-entrant cold vault operations.
     * (Hot vault re-entrancy is already guarded by the Unlocking state check.)
     */
    @Volatile private var coldVaultInProgress = false

    init {
        if (!vaultRepository.vaultExists(isColdVault = false)) {
            _uiState.value = VaultUiState.Setup(isColdVault = false)
        }
        viewModelScope.launch {
            lockVaultUseCase.lockEvents.collect {
                dbJob?.cancel()
                dbJob = null
                wipeEntries(currentEntries)
                currentEntries = emptyList()
                _uiState.value = if (vaultRepository.vaultExists()) VaultUiState.Locked
                                 else VaultUiState.Setup()
            }
        }
    }

    // ---------------------------------------------------------------------------
    // FIRST-LAUNCH: Create a new vault with a validated passphrase
    // ---------------------------------------------------------------------------

    fun createVault(password: CharArray, confirmPassword: CharArray, isColdVault: Boolean = false) {
        // [T20] Passphrase strength checked at creation only.
        if (password.size < 5) {
            val msg = "Passphrase must be at least 5 characters."
            if (isColdVault) _coldVaultError.value = msg           // Bug 1a fix: stay on Dashboard
            else _uiState.value = VaultUiState.Error(msg)
            password.fill('\u0000')
            confirmPassword.fill('\u0000')
            return
        }
        if (!constantTimeEquals(password, confirmPassword)) {
            val msg = "Passphrases do not match. Please try again."
            if (isColdVault) _coldVaultError.value = msg           // Bug 1a fix: stay on Dashboard
            else _uiState.value = VaultUiState.Error(msg)
            password.fill('\u0000')
            confirmPassword.fill('\u0000')
            return
        }
        confirmPassword.fill('\u0000')

        // Route to the correct unlock path based on vault type
        if (isColdVault) unlockColdVaultInternal(password)
        else unlockHotVaultInternal(password)
    }

    // ---------------------------------------------------------------------------
    // REGULAR UNLOCK
    // ---------------------------------------------------------------------------

    fun unlock(password: CharArray, isColdVault: Boolean = false) {
        if (isColdVault) {
            // Cold vault: guard with its own flag, NOT the Unlocking state check
            if (coldVaultInProgress) { password.fill('\u0000'); return }
            unlockColdVaultInternal(password)
        } else {
            // Hot vault: re-entrancy guard via state check
            if (_uiState.value is VaultUiState.Unlocking) { password.fill('\u0000'); return }
            unlockHotVaultInternal(password)
        }
    }

    // ---------------------------------------------------------------------------
    // HOT VAULT unlock path — may change global _uiState (normal navigation)
    // ---------------------------------------------------------------------------

    private fun unlockHotVaultInternal(password: CharArray) {
        _uiState.value = VaultUiState.Unlocking

        viewModelScope.launch {
            try {
                unlockVaultUseCase(password, saltProvider(false), isColdVault = false)
                // password is zeroed inside UnlockVaultUseCase's finally block

                dbJob?.cancel()
                dbJob = viewModelScope.launch {
                    try {
                        vaultRepository.getAllEntries().collect { entries ->
                            wipeEntries(currentEntries)
                            currentEntries = entries
                            if (_uiState.value !is VaultUiState.AddingCredential) {
                                _uiState.value = VaultUiState.Unlocked(entries, isColdVault = false)
                            }
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _uiState.value = VaultUiState.Error("Vault read error. Please re-unlock.")
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // DB is already closed; show error without calling lock() to prevent UI race.
                _uiState.value = VaultUiState.Error("Incorrect Password or Corrupted Vault.")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // COLD VAULT unlock path — must NOT change _uiState while Dashboard is showing
    // ---------------------------------------------------------------------------

    /**
     * Unlocks or creates the Cold Vault without triggering LockScreen navigation.
     *
     * Success transitions to Unlocked(cold vault).
     * Failure transitions to Locked because the hot vault was already closed by openVault().
     */
    private fun unlockColdVaultInternal(password: CharArray) {
        coldVaultInProgress = true
        _coldVaultError.value = null // clear any previous error

        viewModelScope.launch {
            try {
                // This closes the hot vault and opens the cold vault (Dispatchers.IO inside UseCase)
                unlockVaultUseCase(password, saltProvider(true), isColdVault = true)

                // Success: transition to Cold Vault Dashboard
                dbJob?.cancel()
                dbJob = viewModelScope.launch {
                    try {
                        vaultRepository.getAllEntries().collect { entries ->
                            wipeEntries(currentEntries)
                            currentEntries = entries
                            if (_uiState.value !is VaultUiState.AddingCredential) {
                                _uiState.value = VaultUiState.Unlocked(entries, isColdVault = true)
                            }
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _coldVaultError.value = "Failed to load cold vault entries."
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Cold vault open failed. openVault() already called closeVault() first,
                // so the hot vault is also now closed. Both vaults are in a closed state.
                // We must go to Locked so the user can re-authenticate.
                _coldVaultError.value = "Incorrect Cold Vault Key or Corrupted Vault."
                // Brief delay so the error appears in the dialog before we navigate away
                kotlinx.coroutines.delay(1500)
                _uiState.value = VaultUiState.Locked
            } finally {
                coldVaultInProgress = false
            }
        }
    }

    // ---------------------------------------------------------------------------
    // LOCK
    // ---------------------------------------------------------------------------

    fun lock() {
        viewModelScope.launch {
            lockVaultUseCase()
        }
    }

    // ---------------------------------------------------------------------------
    // Cold Vault helpers
    // ---------------------------------------------------------------------------

    fun coldVaultExists(): Boolean = vaultRepository.vaultExists(isColdVault = true)

    /** Called by DashboardScreen when the cold vault dialog is dismissed, to clear stale errors. */
    fun clearColdVaultError() { _coldVaultError.value = null }

    // ---------------------------------------------------------------------------
    // Credential management
    // ---------------------------------------------------------------------------

    fun navigateToAddCredential(isColdVault: Boolean) {
        _uiState.value = VaultUiState.AddingCredential(isColdVault)
    }

    fun cancelAddCredential(isColdVault: Boolean) {
        if (_uiState.value !is VaultUiState.Locked) {
            _uiState.value = VaultUiState.Unlocked(currentEntries, isColdVault)
        }
    }

    fun addCredential(entry: VaultEntry) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val isColdVault = if (currentState is VaultUiState.AddingCredential) currentState.isColdVault else false
                vaultRepository.addEntry(entry)
                entry.annihilate()
                if (_uiState.value !is VaultUiState.Locked) {
                    _uiState.value = VaultUiState.Unlocked(currentEntries, isColdVault)
                }
            } catch (e: Exception) {
                entry.annihilate()
                if (_uiState.value !is VaultUiState.Locked) {
                    _uiState.value = VaultUiState.Error("Failed to save credential.")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        wipeEntries(currentEntries)
        currentEntries = emptyList()
    }

    // ---------------------------------------------------------------------------
    // Factory for manual DI
    // ---------------------------------------------------------------------------

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
