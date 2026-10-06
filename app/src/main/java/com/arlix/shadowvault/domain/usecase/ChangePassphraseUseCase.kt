package com.arlix.shadowvault.domain.usecase

import com.arlix.shadowvault.domain.ICryptoProvider
import com.arlix.shadowvault.domain.IVaultRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Changes the passphrase of the currently open vault.
 *
 * The existing salt is deliberately kept: it must change together with the database file,
 * and two files cannot be replaced atomically. A different passphrase with the same salt
 * still gives a completely different key, so nothing is lost security-wise.
 *
 * On success the vault is closed; the caller must lock the UI.
 */
class ChangePassphraseUseCase(
    private val cryptoProvider: ICryptoProvider,
    private val vaultRepository: IVaultRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /** Both passphrase arrays are zeroed on every exit path. */
    suspend operator fun invoke(
        currentPassphrase: CharArray,
        newPassphrase: CharArray,
        salt: ByteArray,
        isColdVault: Boolean
    ) {
        var oldKey: ByteArray? = null
        var newKey: ByteArray? = null
        try {
            oldKey = cryptoProvider.deriveMasterKey(currentPassphrase, salt)
            newKey = cryptoProvider.deriveMasterKey(newPassphrase, salt)
            // The rebuild does blocking database and file work, so it stays off the main thread.
            withContext(ioDispatcher) {
                vaultRepository.changePassphrase(oldKey, newKey, isColdVault)
            }
        } finally {
            currentPassphrase.fill('\u0000')
            newPassphrase.fill('\u0000')
            oldKey?.let { cryptoProvider.wipe(it) }
            newKey?.let { cryptoProvider.wipe(it) }
        }
    }
}
