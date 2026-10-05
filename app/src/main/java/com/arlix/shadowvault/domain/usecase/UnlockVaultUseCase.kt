package com.arlix.shadowvault.domain.usecase

import com.arlix.shadowvault.domain.ICryptoProvider
import com.arlix.shadowvault.domain.IVaultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class UnlockVaultUseCase(
    private val cryptoProvider: ICryptoProvider,
    private val vaultRepository: IVaultRepository,
    // Injected so unit tests can replace Dispatchers.IO with a test dispatcher.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * Derives the Argon2id master key and opens (or creates) the SQLCipher vault.
     *
     * Threading: key derivation runs on Dispatchers.Default (CPU- and memory-bound);
     * opening the database runs on [ioDispatcher] (file I/O, must not block the main thread).
     *
     * Memory contract: [password] is zeroed on every exit path, and the derived key is wiped
     * as soon as the repository has taken its own copy. The caller must not reuse [password].
     *
     * Cancellation means "a lock was requested". If that happens while the database is being
     * opened, the vault is closed again so it can never stay open behind the lock screen.
     *
     * @return true if the vault was opened.
     */
    suspend operator fun invoke(
        password: CharArray,
        salt: ByteArray,
        isColdVault: Boolean = false
    ): Boolean {
        var masterKey: ByteArray? = null
        try {
            masterKey = cryptoProvider.deriveMasterKey(password, salt)
            return try {
                withContext(ioDispatcher) { vaultRepository.openVault(masterKey, isColdVault) }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { vaultRepository.closeVault() }
                throw e
            }
        } finally {
            password.fill('\u0000')
            masterKey?.let { cryptoProvider.wipe(it) }
        }
    }
}
