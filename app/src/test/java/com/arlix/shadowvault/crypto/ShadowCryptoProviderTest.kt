package com.arlix.shadowvault.crypto

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ShadowCryptoProviderTest {

    @Test
    fun `test master key derivation`() = runTest {
        val cryptoProvider = ShadowCryptoProvider()
        val password = charArrayOf('p', 'a', 's', 's', 'w', 'o', 'r', 'd')
        val salt = ByteArray(16) { 1 }
        
        val key1 = cryptoProvider.deriveMasterKey(password, salt)
        val key2 = cryptoProvider.deriveMasterKey(password, salt)
        
        assertEquals(32, key1.size)
        assertArrayEquals(key1, key2)
    }

    @Test
    fun `test wipe native fallback fills with zero`() {
        val cryptoProvider = ShadowCryptoProvider()
        val buffer = ByteArray(10) { 1 }
        cryptoProvider.wipe(buffer)
        assertArrayEquals(ByteArray(10) { 0 }, buffer)
    }
}
