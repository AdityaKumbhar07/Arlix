package com.arlix.svault.ui

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arlix.svault.E_ui_VaultState
import com.arlix.svault.crypto.C_crypto_ColdVaultManager
import com.arlix.svault.db.C_db_VaultDatabase
import com.arlix.svault.db.C_db_VaultEntity
import com.arlix.svault.db.C_db_VaultMetaEntity
import com.arlix.svault.db.I_db_VaultDao
import com.arlix.svault.db.I_db_VaultMetaDao
import com.arlix.svault.db.K_db_MetaKeys
import com.arlix.svault.mem.C_mem_NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay

enum class E_ui_VaultChamber {
    HOT,
    COLD
}

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
    private var v_ui_vaultMetaDao: I_db_VaultMetaDao? = null
    private var v_ui_streamJob: Job? = null
    private var v_ui_clipboardJob: Job? = null

    // In-memory volatile DEK: held strictly while cold chamber is active
    private var v_ui_coldDek: ByteArray? = null

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
    var v_ui_clipboardCopyingId by mutableStateOf<String?>(null)
    var v_ui_clipboardCountdown by mutableIntStateOf(0)
    

    fun f_ui_startStreamingCredentials() {
        v_ui_streamJob?.cancel()
        val v_dao = v_ui_vaultDao ?: return

        v_ui_streamJob = viewModelScope.launch(Dispatchers.IO) {
            v_dao.f_db_getEntriesByChamberAndSection(
                v_chamber = v_ui_selectedChamber.name,
                v_section = v_ui_selectedHotSection.name
            ).collectLatest { v_entities ->
                val v_items = v_entities.map { v_entity ->
                    val v_account = if (v_entity.v_db_chamber == E_ui_VaultChamber.COLD.name) {
                        v_ui_coldDek?.let { v_dek ->
                            try {
                                C_crypto_ColdVaultManager.f_crypto_decryptPayload(
                                    v_encodedBlob = v_entity.v_db_account,
                                    v_dek = v_dek,
                                    v_entryId = v_entity.v_db_id,
                                    v_chamber = v_entity.v_db_chamber
                                )
                            } catch (e: Exception) {
                                "[Decryption Error]"
                            }
                        } ?: "[Locked Account]"
                    } else {
                        v_entity.v_db_account
                    }

                    val v_secret = if (v_entity.v_db_chamber == E_ui_VaultChamber.COLD.name) {
                        v_ui_coldDek?.let { v_dek ->
                            try {
                                C_crypto_ColdVaultManager.f_crypto_decryptPayload(
                                    v_encodedBlob = v_entity.v_db_secret,
                                    v_dek = v_dek,
                                    v_entryId = v_entity.v_db_id,
                                    v_chamber = v_entity.v_db_chamber
                                )
                            } catch (e: Exception) {
                                "[Decryption Error]"
                            }
                        } ?: "[Locked Secret]"
                    } else {
                        v_entity.v_db_secret
                    }

                    C_ui_CredentialItem(
                        v_ui_id = v_entity.v_db_id,
                        v_ui_title = v_entity.v_db_title,
                        v_ui_account = v_account,
                        v_ui_secret = v_secret,
                        v_ui_section = E_ui_HotSection.entries.firstOrNull { it.name == v_entity.v_db_section } ?: E_ui_HotSection.GENERAL,
                        v_ui_chamber = E_ui_VaultChamber.entries.firstOrNull { it.name == v_entity.v_db_chamber } ?: E_ui_VaultChamber.HOT
                    )
                }

                withContext(Dispatchers.Main) {
                    v_ui_credentialsList = v_items
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

        val v_passphraseChars = v_ui_passwordInput.toCharArray()
        v_ui_isLoading = true

        viewModelScope.launch(Dispatchers.IO) {
            var v_passphraseBytes: ByteArray? = null
            try {
                v_passphraseBytes = String(v_passphraseChars).toByteArray(Charsets.UTF_8)

                // 1. RAM Pinning: Enforce OS non-swapping hint
                C_mem_NativeBridge.f_mem_lockByteArray(v_passphraseBytes)

                // 2. Decrypt and boot SQLCipher on background thread
                val v_db = C_db_VaultDatabase.f_db_getInstance(v_context, v_passphraseBytes)
                val v_dao = v_db.f_db_vaultDao()
                val v_metaDao = v_db.f_db_vaultMetaDao()

                // Verification Challenge probe
                v_db.openHelper.writableDatabase.query("SELECT count(*) FROM sqlite_master").close()

                v_ui_database = v_db
                v_ui_vaultDao = v_dao
                v_ui_vaultMetaDao = v_metaDao

                withContext(Dispatchers.Main) {
                    v_ui_vaultState = E_ui_VaultState.UNLOCKED
                    v_ui_statusMessage = ""
                    v_ui_passwordInput = ""
                    f_ui_startStreamingCredentials()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    v_ui_statusMessage = "Decryption failed: Incorrect passphrase"
                    v_ui_vaultState = E_ui_VaultState.LOCKED
                }
                C_db_VaultDatabase.f_db_closeDatabase()
            } finally {
                withContext(Dispatchers.Main) {
                    v_ui_isLoading = false
                }
                v_passphraseBytes?.let {
                    C_mem_NativeBridge.f_mem_wipeByteArray(it)
                    C_mem_NativeBridge.f_mem_unlockByteArray(it)
                }
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
        viewModelScope.launch(Dispatchers.IO) {
            val v_id = UUID.randomUUID().toString()
            val v_storedAccount: String
            val v_storedSecret: String

            if (v_chamber == E_ui_VaultChamber.COLD) {
                val v_dek = v_ui_coldDek
                if (v_dek == null) {
                    withContext(Dispatchers.Main) {
                        v_ui_statusMessage = "Cold Vault is locked: Cannot encrypt"
                    }
                    return@launch
                }
                v_storedAccount = C_crypto_ColdVaultManager.f_crypto_encryptPayload(
                    v_plaintext = v_account,
                    v_dek = v_dek,
                    v_entryId = v_id,
                    v_chamber = v_chamber.name
                )
                v_storedSecret = C_crypto_ColdVaultManager.f_crypto_encryptPayload(
                    v_plaintext = v_secret,
                    v_dek = v_dek,
                    v_entryId = v_id,
                    v_chamber = v_chamber.name
                )
            } else {
                v_storedAccount = v_account
                v_storedSecret = v_secret
            }

            val v_entity = C_db_VaultEntity(
                v_db_id = v_id,
                v_db_title = v_title,
                v_db_account = v_storedAccount,
                v_db_secret = v_storedSecret,
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
        // Auto-Relock: Exiting Cold Chamber wipes DEK from RAM
        v_ui_coldDek?.let { C_crypto_ColdVaultManager.f_crypto_zeroize(it) }
        v_ui_coldDek = null
        v_ui_isColdVaultUnlocked = false
        v_ui_selectedChamber = E_ui_VaultChamber.HOT
        f_ui_startStreamingCredentials()
    }

    fun f_ui_onColdChamberClicked() {
        if (v_ui_isColdVaultUnlocked && v_ui_coldDek != null) {
            v_ui_selectedChamber = E_ui_VaultChamber.COLD
            f_ui_startStreamingCredentials()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val v_wrappedDek = v_ui_vaultMetaDao?.f_db_getMeta(K_db_MetaKeys.COLD_WRAPPED_DEK)
            withContext(Dispatchers.Main) {
                v_ui_isColdConfigured = (v_wrappedDek != null)
                v_ui_coldPasswordInput = ""
                v_ui_coldConfirmPasswordInput = ""
                v_ui_statusMessage = ""
                v_ui_showColdAuthDialog = true
            }
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

        val v_passphraseChars = v_ui_coldPasswordInput.toCharArray()
        v_ui_isLoading = true

        viewModelScope.launch(Dispatchers.IO) {
            var v_kek: ByteArray? = null
            try {
                val v_salt = C_crypto_ColdVaultManager.f_crypto_generateRandomBytes(
                    C_crypto_ColdVaultManager.K_SALT_SIZE_BYTES
                )
                val v_iterations = C_crypto_ColdVaultManager.K_DEFAULT_PBKDF2_ITERATIONS

                // 1. Derive 256-bit KEK from Passphrase 2
                v_kek = C_crypto_ColdVaultManager.f_crypto_deriveKek(
                    v_passphrase = v_passphraseChars,
                    v_salt = v_salt,
                    v_iterations = v_iterations
                )

                // 2. Generate random 256-bit DEK
                val v_dek = C_crypto_ColdVaultManager.f_crypto_generateRandomBytes(
                    C_crypto_ColdVaultManager.K_KEY_SIZE_BYTES
                )

                // 3. Wrap DEK with AES-256-GCM under KEK
                val v_wrappedDek = C_crypto_ColdVaultManager.f_crypto_wrapDek(
                    v_dek = v_dek,
                    v_kek = v_kek
                )

                // 4. Atomically store metadata in vault_meta
                v_ui_vaultMetaDao?.f_db_setupColdVaultAtomic(
                    v_salt = C_db_VaultMetaEntity(
                        K_db_MetaKeys.COLD_SALT,
                        Base64.encodeToString(v_salt, Base64.NO_WRAP)
                    ),
                    v_iterations = C_db_VaultMetaEntity(
                        K_db_MetaKeys.COLD_KDF_ITERATIONS,
                        v_iterations.toString()
                    ),
                    v_wrappedDek = C_db_VaultMetaEntity(
                        K_db_MetaKeys.COLD_WRAPPED_DEK,
                        v_wrappedDek
                    )
                )

                // 5. Retain DEK in volatile memory while Cold chamber is open
                v_ui_coldDek = v_dek

                withContext(Dispatchers.Main) {
                    v_ui_isColdVaultUnlocked = true
                    v_ui_selectedChamber = E_ui_VaultChamber.COLD
                    v_ui_showColdAuthDialog = false
                    v_ui_coldPasswordInput = ""
                    v_ui_coldConfirmPasswordInput = ""
                    v_ui_statusMessage = ""
                    f_ui_startStreamingCredentials()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    v_ui_statusMessage = "Cold Vault setup failed: ${e.localizedMessage}"
                }
            } finally {
                v_kek?.let { C_crypto_ColdVaultManager.f_crypto_zeroize(it) }
                withContext(Dispatchers.Main) {
                    v_ui_isLoading = false
                }
            }
        }
    }

    fun f_ui_onUnlockColdVaultConfirmed() {
        if (v_ui_coldPasswordInput.isBlank()) {
            v_ui_statusMessage = "Cold Passphrase cannot be empty"
            return
        }

        val v_passphraseChars = v_ui_coldPasswordInput.toCharArray()
        v_ui_isLoading = true

        viewModelScope.launch(Dispatchers.IO) {
            var v_kek: ByteArray? = null
            try {
                val v_metaDao = v_ui_vaultMetaDao ?: return@launch
                val v_saltBase64 = v_metaDao.f_db_getMeta(K_db_MetaKeys.COLD_SALT)
                val v_iterationsStr = v_metaDao.f_db_getMeta(K_db_MetaKeys.COLD_KDF_ITERATIONS)
                val v_wrappedDek = v_metaDao.f_db_getMeta(K_db_MetaKeys.COLD_WRAPPED_DEK)

                if (v_saltBase64 == null || v_wrappedDek == null) {
                    withContext(Dispatchers.Main) {
                        v_ui_statusMessage = "Cold Vault configuration missing"
                        v_ui_isColdConfigured = false
                    }
                    return@launch
                }

                val v_salt = Base64.decode(v_saltBase64, Base64.NO_WRAP)
                val v_iterations = v_iterationsStr?.toIntOrNull()
                    ?: C_crypto_ColdVaultManager.K_DEFAULT_PBKDF2_ITERATIONS

                // 1. Derive candidate KEK
                v_kek = C_crypto_ColdVaultManager.f_crypto_deriveKek(
                    v_passphrase = v_passphraseChars,
                    v_salt = v_salt,
                    v_iterations = v_iterations
                )

                // 2. Unwrap DEK (GCM Tag check verifies passphrase)
                val v_dek = C_crypto_ColdVaultManager.f_crypto_unwrapDek(
                    v_wrappedDekBase64 = v_wrappedDek,
                    v_kek = v_kek
                )

                // 3. Success: hold DEK in memory
                v_ui_coldDek = v_dek

                withContext(Dispatchers.Main) {
                    v_ui_isColdVaultUnlocked = true
                    v_ui_selectedChamber = E_ui_VaultChamber.COLD
                    v_ui_showColdAuthDialog = false
                    v_ui_coldPasswordInput = ""
                    v_ui_statusMessage = ""
                    f_ui_startStreamingCredentials()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    v_ui_statusMessage = "Invalid Cold Passphrase"
                }
            } finally {
                v_kek?.let { C_crypto_ColdVaultManager.f_crypto_zeroize(it) }
                withContext(Dispatchers.Main) {
                    v_ui_isLoading = false
                }
            }
        }
    }

    fun f_ui_selectHotSection(v_section: E_ui_HotSection) {
        v_ui_selectedHotSection = v_section
        f_ui_startStreamingCredentials()
    }

    fun f_ui_deleteCredential(v_id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            v_ui_vaultDao?.f_db_softDeleteEntry(v_id)
        }
    }

    fun f_ui_lockImmediate() {
        v_ui_streamJob?.cancel()
        v_ui_streamJob = null

        v_ui_credentialsList = emptyList()

        // 1. Zeroize and discard cold DEK
        v_ui_coldDek?.let { C_crypto_ColdVaultManager.f_crypto_zeroize(it) }
        v_ui_coldDek = null

        // 2. Close SQLCipher
        C_db_VaultDatabase.f_db_closeDatabase()
        v_ui_database = null
        v_ui_vaultDao = null
        v_ui_vaultMetaDao = null

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

    fun f_ui_copyToClipboardWithAutoClear(
        v_context: Context,
        v_itemId: String,
        v_secret: String,
        v_timeoutSeconds: Int = 10
    ) {
        v_ui_clipboardJob?.cancel()
        val v_clipboard = v_context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return

        // 1. Package secret with Android 13+ sensitive suppression flag
        val v_clip = ClipData.newPlainText("secret", v_secret).apply {
            description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        v_clipboard.setPrimaryClip(v_clip)

        v_ui_clipboardCopyingId = v_itemId
        v_ui_clipboardCountdown = v_timeoutSeconds

        // 2. Countdown safely survives screen transitions in viewModelScope
        v_ui_clipboardJob = viewModelScope.launch {
            for (i in v_timeoutSeconds downTo 1) {
                v_ui_clipboardCountdown = i
                delay(1000)
            }
            v_ui_clipboardCountdown = 0
            v_ui_clipboardCopyingId = null

            // 3. Physically wipe clipboard
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                v_clipboard.clearPrimaryClip()
            } else {
                v_clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}