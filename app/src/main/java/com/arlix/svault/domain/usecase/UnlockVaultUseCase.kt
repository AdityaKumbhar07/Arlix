package com.arlix.svault.domain.usecase

import com.arlix.svault.domain.ICryptoProvider
import com.arlix.svault.domain.IVaultRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UnlockVaultUseCase(
    private val cryptoProvider: ICryptoProvider,
    private val vaultRepository: IVaultRepository,
    // Injected so unit tests can replace Dispatchers.IO with their test dispatcher.
    // Production code always passes Dispatchers.IO (the default).
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * Derives the Argon2id master key and opens the corresponding SQLCipher vault.
     *
     * @param password The raw password CharArray typed by the user. WILL BE ZEROED after use.
     * @param salt The unique per-device salt loaded from EncryptedSharedPreferences.
     * @param isColdVault True if this is the hidden secondary vault (Cold Vault path).
     * @return True if the vault was opened successfully.
     *
     * THREADING:
     * - deriveMasterKey internally runs on Dispatchers.Default (CPU-bound Argon2id math).
     * - openVault runs on [ioDispatcher] (Dispatchers.IO in production) for file I/O.
     *   [Gemini Fix 2] Without an IO dispatcher, openVault runs on Main, causing a
     *   StrictMode violation and risking an ANR during the SQLCipher page-unlock pass.
     *
     * MEMORY CONTRACT:
     * The [password] CharArray is zeroed in the `finally` block regardless of success or exception.
     * The caller MUST NOT use [password] after this function returns.
     */
    suspend operator fun invoke(
        password: CharArray,
        salt: ByteArray,
        isColdVault: Boolean = false
    ): Boolean {
        var masterKey: ByteArray? = null
        try {
            // CPU-bound: Argon2id key derivation (internally runs on Dispatchers.Default)
            masterKey = cryptoProvider.deriveMasterKey(password, salt)

            // IO-bound: pass the derived key to SQLCipher for file-level decryption
            return withContext(ioDispatcher) {
                vaultRepository.openVault(masterKey, isColdVault)
            }
        } finally {
            // Zero the caller's password CharArray on ALL exit paths — success, exception, everything.
            password.fill('\u0000')
            masterKey?.let { cryptoProvider.wipe(it) }
        }
    }
}
