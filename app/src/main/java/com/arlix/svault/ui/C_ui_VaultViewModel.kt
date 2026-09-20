package com.arlix.svault.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arlix.svault.E_ui_VaultState
import com.arlix.svault.db.C_db_VaultDatabase
import com.arlix.svault.db.C_db_VaultEntity
import com.arlix.svault.db.I_db_VaultDao
import com.arlix.svault.mem.C_mem_NativeBridge
import com.arlix.svault.crypto.C_crypto_ColdVaultSentinel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Different security chamgers segregating credential according to sensitivity
enum class E_ui_VaultChamber {
    HOT,
    COLD
}

// Sub sections under HOT(casual)
enum class E_ui_HotSection(val v_ui_label: String) {
    GENERAL("🌐 General"),
    PERSONAL("💼 Personal"),
    FINANCE("💳 Finance")
}

data class C_ui_CredentialItem(
    val v_ui_id: String,
    val v_ui_title: String,
    val v_ui_account: String,
    val v_ui_secret: String,
    val v_ui_section: E_ui_HotSection,
    val v_ui_chamber: E_ui_VaultChamber = E_ui_VaultChamber.HOT
)

class C_ui_VaultViewModel : ViewModel() {
    private var v_ui_database: C_db_VaultDatabase? = null
    private var v_ui_vaultDao: I_db_VaultDao? = null
    private var v_ui_streamJob: Job? = null
    var v_ui_credentialsList by mutableStateOf<List<C_ui_CredentialItem>>(emptyList())

    var v_ui_vaultState by mutableStateOf(E_ui_VaultState.LOCKED)
    var v_ui_passwordInput by mutableStateOf("")
    var v_ui_confirmPasswordInput by mutableStateOf("")
    var v_ui_isPasswordVisible by mutableStateOf(false)
    var v_ui_statusMessage by mutableStateOf("")
    var v_ui_isLoading by mutableStateOf(false)
    var v_ui_selectedChamber by mutableStateOf(E_ui_VaultChamber.HOT)
    var v_ui_selectedHotSection by mutableStateOf(E_ui_HotSection.GENERAL)
    var v_ui_isColdVaultUnlocked by mutableStateOf(false)
    var v_ui_showColdAuthDialog by mutableStateOf(false)
    var v_ui_coldPasswordInput by mutableStateOf("")
    var v_ui_coldConfirmPasswordInput by mutableStateOf("")
    var v_ui_isColdConfigured by mutableStateOf(false)


    fun f_ui_startStreamingCredentials() {
        v_ui_streamJob?.cancel()
        val v_dao = v_ui_vaultDao ?: return

        v_ui_streamJob = viewModelScope.launch {
            v_dao.f_db_getEntriesByChamberAndSection(
                v_chamber = v_ui_selectedChamber.name,
                v_section = v_ui_selectedHotSection.name
            ).collectLatest { v_entities ->
                v_ui_credentialsList = v_entities
                    .filter { it.v_db_title != "__SHADOWVAULT_COLD_SENTINEL__" }
                    .map { v_entity ->
                        C_ui_CredentialItem(
                            v_ui_id = v_entity.v_db_id,
                            v_ui_title = v_entity.v_db_title,
                            v_ui_account = v_entity.v_db_account,
                            v_ui_secret = v_entity.v_db_secret,
                            v_ui_section = E_ui_HotSection.valueOf(v_entity.v_db_section),
                            v_ui_chamber = E_ui_VaultChamber.valueOf(v_entity.v_db_chamber)
                        )
                    }

            }
        }
    }

    fun f_ui_checkVaultInitialization(v_context: Context) {
        val v_dbFile = v_context.getDatabasePath("shadowvault.db")
        if (!v_dbFile.exists()) {
            v_ui_vaultState = E_ui_VaultState.SETUP
        } else if (v_ui_vaultState != E_ui_VaultState.UNLOCKED) {
            v_ui_vaultState = E_ui_VaultState.LOCKED
        }
    }

    fun f_ui_onCreateVaultClicked(v_context: Context) {
        if (v_ui_passwordInput.isBlank()) {
            v_ui_statusMessage = "Passphrase cannot be empty"
            return
        }
        if (v_ui_passwordInput.length < 4) {
            v_ui_statusMessage = "Passphrase must be at least 4 characters"
            return
        }
        if (v_ui_passwordInput != v_ui_confirmPasswordInput) {
            v_ui_statusMessage = "Passphrases do not match"
            return
        }

        f_ui_onUnlockedClicked(v_context)
        v_ui_confirmPasswordInput = ""
    }

    fun f_ui_onUnlockedClicked(v_context: Context) {
        if (v_ui_passwordInput.isBlank()) {
            v_ui_statusMessage = "Passphrase cannot be empty"
            return
        }

        var v_passphraseBytes: ByteArray? = null

        try {
            v_ui_isLoading = true
            v_passphraseBytes = v_ui_passwordInput.toByteArray(Charsets.UTF_8)

            // 1. Physical RAM Pinning: Forbids Linux kernel & zRAM from swapping key to disk
            C_mem_NativeBridge.f_mem_lockByteArray(v_passphraseBytes)

            // 2. Decrypts and boots SQLCipher
            v_ui_database = C_db_VaultDatabase.f_db_getInstance(v_context, v_passphraseBytes)
            v_ui_vaultDao = v_ui_database?.f_db_vaultDao()
            v_ui_database?.openHelper?.writableDatabase?.query("SELECT count(*) FROM sqlite_master")?.close()

            // 3. State transition
            v_ui_vaultState = E_ui_VaultState.UNLOCKED
            v_ui_statusMessage = ""
            v_ui_passwordInput = "" // Clear plaintext string from UI

            // 4. Stream credentials from encrypted SQLite
            f_ui_startStreamingCredentials()

        } catch (e: Exception) {
            v_ui_statusMessage = "Decryption failed: Incorrect passphrase"
            v_ui_vaultState = E_ui_VaultState.LOCKED
            C_db_VaultDatabase.f_db_closeDatabase()
        } finally {
            v_ui_isLoading = false
            // 5. Hardware volatile memory wipe & RAM unpinning
            v_passphraseBytes?.let {
                C_mem_NativeBridge.f_mem_wipeByteArray(it)
                C_mem_NativeBridge.f_mem_unlockByteArray(it)
            }
        }
    }

    fun f_ui_insertCredential(
        v_title: String,
        v_account: String,
        v_secret: String,
        v_chamber: E_ui_VaultChamber = v_ui_selectedChamber,
        v_section: E_ui_HotSection = v_ui_selectedHotSection
    ) {
        viewModelScope.launch {
            val v_entity = C_db_VaultEntity(
                v_db_title = v_title,
                v_db_account = v_account,
                v_db_secret = v_secret,
                v_db_chamber = v_chamber.name,
                v_db_section = v_section.name
            )
            v_ui_vaultDao?.f_db_insertEntry(v_entity)
        }
    }

    fun f_ui_selectChamber(v_chamber: E_ui_VaultChamber) {
        if (v_chamber == E_ui_VaultChamber.COLD) {
            f_ui_onColdChamberClicked()
            return
        }
        // Auto-Relock: Exiting Cold Vault immediately locks the enclave
        v_ui_isColdVaultUnlocked = false
        v_ui_selectedChamber = E_ui_VaultChamber.HOT
        f_ui_startStreamingCredentials()
    }

    fun f_ui_onColdChamberClicked() {
        if (v_ui_isColdVaultUnlocked) {
            v_ui_selectedChamber = E_ui_VaultChamber.COLD
            f_ui_startStreamingCredentials()
            return
        }
        viewModelScope.launch {
            val v_sentinel = v_ui_vaultDao?.f_db_getColdSentinel()
            v_ui_isColdConfigured = (v_sentinel != null)
            v_ui_coldPasswordInput = ""
            v_ui_coldConfirmPasswordInput = ""
            v_ui_statusMessage = ""
            v_ui_showColdAuthDialog = true
        }
    }

    fun f_ui_onSetupColdVaultConfirmed() {
        if (v_ui_coldPasswordInput.isBlank()) {
            v_ui_statusMessage = "Cold Passphrase cannot be empty"
            return
        }
        if (v_ui_coldPasswordInput.length < 4) {
            v_ui_statusMessage = "Cold Passphrase must be at least 4 characters"
            return
        }
        if (v_ui_coldPasswordInput != v_ui_coldConfirmPasswordInput) {
            v_ui_statusMessage = "Passphrases do not match"
            return
        }
        viewModelScope.launch {
            val v_encoded = C_crypto_ColdVaultSentinel.f_crypto_encryptSentinel(v_ui_coldPasswordInput)
            val v_sentinel = C_db_VaultEntity(
                v_db_title = "__SHADOWVAULT_COLD_SENTINEL__",
                v_db_account = "INTERNAL_ENCLAVE_SENTINEL",
                v_db_secret = v_encoded,
                v_db_chamber = E_ui_VaultChamber.COLD.name,
                v_db_section = E_ui_HotSection.GENERAL.name
            )
            v_ui_vaultDao?.f_db_insertEntry(v_sentinel)
            v_ui_isColdVaultUnlocked = true
            v_ui_selectedChamber = E_ui_VaultChamber.COLD
            v_ui_showColdAuthDialog = false
            v_ui_coldPasswordInput = ""
            v_ui_coldConfirmPasswordInput = ""
            v_ui_statusMessage = ""
            f_ui_startStreamingCredentials()
        }
    }

    fun f_ui_onUnlockColdVaultConfirmed() {
        if (v_ui_coldPasswordInput.isBlank()) {
            v_ui_statusMessage = "Cold Passphrase cannot be empty"
            return
        }
        viewModelScope.launch {
            val v_sentinel = v_ui_vaultDao?.f_db_getColdSentinel()
            if (v_sentinel == null) {
                v_ui_isColdConfigured = false
                return@launch
            }
            val v_isValid = C_crypto_ColdVaultSentinel.f_crypto_verifySentinel(
                v_ui_coldPasswordInput,
                v_sentinel.v_db_secret
            )
            if (v_isValid) {
                v_ui_isColdVaultUnlocked = true
                v_ui_selectedChamber = E_ui_VaultChamber.COLD
                v_ui_showColdAuthDialog = false
                v_ui_coldPasswordInput = ""
                v_ui_statusMessage = ""
                f_ui_startStreamingCredentials()
            } else {
                v_ui_statusMessage = "Invalid Cold Passphrase"
            }
        }
    }

    fun f_ui_selectHotSection(v_section: E_ui_HotSection) {
        v_ui_selectedHotSection = v_section
        f_ui_startStreamingCredentials()
    }

    fun f_ui_deleteCredential(v_id: String) {
        viewModelScope.launch {
            v_ui_vaultDao?.f_db_softDeleteEntry(v_id)
        }
    }

    fun f_ui_lockImmediate() {
        v_ui_streamJob?.cancel()
        v_ui_streamJob = null

        v_ui_credentialsList = emptyList()

        C_db_VaultDatabase.f_db_closeDatabase()
        v_ui_database = null
        v_ui_vaultDao = null

        v_ui_vaultState = E_ui_VaultState.LOCKED
        v_ui_passwordInput = ""
        v_ui_statusMessage = ""
        v_ui_selectedChamber = E_ui_VaultChamber.HOT
        v_ui_selectedHotSection = E_ui_HotSection.GENERAL
        v_ui_isColdVaultUnlocked = false
        v_ui_showColdAuthDialog = false
        v_ui_coldPasswordInput = ""
        v_ui_coldConfirmPasswordInput = ""
    }
}