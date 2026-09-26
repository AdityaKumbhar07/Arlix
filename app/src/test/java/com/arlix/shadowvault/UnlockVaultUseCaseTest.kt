package com.arlix.shadowvault

import com.arlix.shadowvault.domain.ICryptoProvider
import com.arlix.shadowvault.domain.IVaultRepository
import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.domain.usecase.UnlockVaultUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockVaultUseCaseTest {

    @Test
    fun `invoke wipes the derived master key on success`() = runTest {
        var wipedBuffer: ByteArray? = null
        val trackingCrypto = object : ICryptoProvider {
            override suspend fun deriveMasterKey(password: CharArray, salt: ByteArray) = ByteArray(32) { 1 }
            override fun encryptData(plaintext: ByteArray, key: ByteArray) = plaintext
            override fun decryptData(ciphertext: ByteArray, key: ByteArray) = ciphertext
            override fun wipe(buffer: ByteArray) { wipedBuffer = buffer; buffer.fill(0) }
        }
        val fakeRepo = object : IVaultRepository {
            override suspend fun addEntry(entry: VaultEntry) {}
            override suspend fun deleteEntry(entryId: String) {}
            override fun getAllEntries(): Flow<List<VaultEntry>> = kotlinx.coroutines.flow.emptyFlow()
            override fun vaultExists(isColdVault: Boolean): Boolean = true
            override suspend fun openVault(masterKey: ByteArray, isColdVault: Boolean): Boolean { return true }
            override suspend fun closeVault() {}
            override fun isVaultOpen(): Boolean = true
        }
        val useCase = UnlockVaultUseCase(trackingCrypto, fakeRepo, StandardTestDispatcher(testScheduler))
        useCase("password".toCharArray(), ByteArray(16))
        assertNotNull("Master key must be wiped after use", wipedBuffer)
        assertTrue("Wiped buffer must be all zeros", wipedBuffer!!.all { it == 0.toByte() })
    }

    @Test
    fun `invoke wipes the derived master key even when openVault throws`() = runTest {
        var wipedBuffer: ByteArray? = null
        val trackingCrypto = object : ICryptoProvider {
            override suspend fun deriveMasterKey(password: CharArray, salt: ByteArray) = ByteArray(32) { 1 }
            override fun encryptData(plaintext: ByteArray, key: ByteArray) = plaintext
            override fun decryptData(ciphertext: ByteArray, key: ByteArray) = ciphertext
            override fun wipe(buffer: ByteArray) { wipedBuffer = buffer; buffer.fill(0) }
        }
        val fakeRepo = object : IVaultRepository {
            override suspend fun addEntry(entry: VaultEntry) {}
            override suspend fun deleteEntry(entryId: String) {}
            override fun getAllEntries(): Flow<List<VaultEntry>> = kotlinx.coroutines.flow.emptyFlow()
            override fun vaultExists(isColdVault: Boolean): Boolean = true
            override suspend fun openVault(masterKey: ByteArray, isColdVault: Boolean): Boolean {
                throw RuntimeException("Database corrupted")
            }
            override suspend fun closeVault() {}
            override fun isVaultOpen(): Boolean = false
        }
        val useCase = UnlockVaultUseCase(trackingCrypto, fakeRepo, StandardTestDispatcher(testScheduler))
        try {
            useCase("password".toCharArray(), ByteArray(16))
        } catch (e: Exception) {
            // Expected
        }
        assertNotNull("Master key must be wiped even on exception", wipedBuffer)
        assertTrue("Wiped buffer must be all zeros", wipedBuffer!!.all { it == 0.toByte() })
    }
}
