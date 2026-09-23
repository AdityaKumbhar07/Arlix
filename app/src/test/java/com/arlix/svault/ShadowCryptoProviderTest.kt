package com.arlix.svault.crypto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ShadowCryptoProviderTest {

    private val cryptoProvider = ShadowCryptoProvider()

    @Test
    fun `deriveMasterKey generates correct 32-byte key`() = runBlocking {
        val password = "StrongPassword123".toCharArray()
        val salt = ByteArray(16) { 1 } // Dummy salt

        val key = cryptoProvider.deriveMasterKey(password, salt)

        assertEquals("Key must be exactly 32 bytes (256-bit)", 32, key.size)
        // Verify it isn't just an array of zeros
        assertTrue("Key should not be empty", key.any { it != 0.toByte() })
    }

    @Test
    fun `deriveMasterKey rejects weak passwords under 5 chars`() = runBlocking {
        val weakPassword = "1234".toCharArray()
        val salt = ByteArray(16) { 1 }

        try {
            cryptoProvider.deriveMasterKey(weakPassword, salt)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("Passphrase must be at least 5 characters.", e.message)
        }
    }
}
