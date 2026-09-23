package com.arlix.svault.domain.usecase

import com.arlix.svault.domain.ICryptoProvider
import com.arlix.svault.domain.IVaultRepository

class UnlockVaultUseCase(
    private val cryptoProvider: ICryptoProvider,
    private val vaultRepository: IVaultRepository
) {
    /**
     * @param password The raw password typed by the user.
     * @param salt The unique device salt (we will hook this up to SharedPreferences later).
     * @param isColdVault True if this was triggered from the hidden Settings button.
     * @return True if unlock was successful.
     */
    suspend operator fun invoke(
        password: CharArray,
        salt: ByteArray,
        isColdVault: Boolean = false
    ): Boolean {
        // 1. Derive the 256-bit key (Takes ~500ms, kills GPUs)
        val masterKey = cryptoProvider.deriveMasterKey(password, salt)

        // 2. Pass the raw key to SQLCipher to unlock the respective DB file
        val success = vaultRepository.openVault(masterKey, isColdVault)

        // 3. Immediately wipe the key from Kotlin RAM!
        cryptoProvider.wipe(masterKey)

        return success
    }
}
