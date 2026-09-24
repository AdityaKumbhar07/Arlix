package com.arlix.svault.crypto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ShadowCryptoProvider.
 * These run on the host JVM (no Android device needed).
 * MemorySanitizer falls back to buffer.fill(0) when the .so is not loaded — that's expected here.
 */
class ShadowCryptoProviderTest {

    private val cryptoProvider = ShadowCryptoProvider()

    @Test
    fun `deriveMasterKey generates exactly 32 bytes`() = runBlocking {
        val password = "StrongPassword123".toCharArray()
        val salt = ByteArray(16) { 1 }

        val key = cryptoProvider.deriveMasterKey(password, salt)

        assertEquals("Key must be exactly 32 bytes (256-bit AES key)", 32, key.size)
        assertTrue("Key must not be all zeros — derivation must have done real work", key.any { it != 0.toByte() })
    }

    @Test
    fun `deriveMasterKey produces deterministic output for same inputs`() = runBlocking {
        // Argon2id is deterministic — same password + same salt always produces the same key.
        // If this flakes, the derivation is broken.
        val password = "SamePassword".toCharArray()
        val salt = ByteArray(16) { 42 }

        val key1 = cryptoProvider.deriveMasterKey(password.copyOf(), salt)
        val key2 = cryptoProvider.deriveMasterKey(password.copyOf(), salt)

        assertArrayEquals("Same password + salt must derive the same key (Argon2id is deterministic)", key1, key2)
    }

    @Test
    fun `deriveMasterKey produces different keys for different salts`() = runBlocking {
        // Two vaults with the same password but different salts must produce completely different keys.
        // This is the entire point of salting — defeats rainbow tables and cross-vault attacks.
        val password = "SharedPassword".toCharArray()
        val salt1 = ByteArray(16) { 1 }
        val salt2 = ByteArray(16) { 2 }

        val key1 = cryptoProvider.deriveMasterKey(password.copyOf(), salt1)
        val key2 = cryptoProvider.deriveMasterKey(password.copyOf(), salt2)

        assertFalse("Different salts must produce different keys", key1.contentEquals(key2))
    }

    @Test
    fun `deriveMasterKey handles non-ASCII Unicode passphrase without truncation - regression for charsToBytes bug`() = runBlocking {
        // REGRESSION TEST for the charsToBytes Latin-1 truncation bug identified in review1byclaude.txt.
        //
        // The old implementation: bytes[i] = chars[i].code.toByte()
        // This truncates any character above U+00FF to its low 8 bits.
        // For example: '€' (U+20AC) → 0xAC; '©' (U+00A9) → 0xA9 (correct); '中' (U+4E2D) → 0x2D ('−').
        //
        // Multiple distinct Unicode passphrases can map to the same byte sequence under this bug,
        // dramatically shrinking the effective keyspace for non-ASCII users.
        //
        // The fix uses NIO: Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(chars))
        // This correctly represents every Unicode character in UTF-8 encoding.
        //
        // This test verifies that two passphrases which would collapse to the same bytes under
        // Latin-1 truncation produce DIFFERENT derived keys.
        val saltA = ByteArray(16) { 7 }

        // '€' is U+20AC. Under Latin-1 truncation: 0xAC.
        // The ASCII character '¬' is U+00AC. Under Latin-1 truncation: also 0xAC.
        // So "a€b" and "a¬b" would produce the SAME key under the buggy implementation.
        val passphraseWithEuro = "a€b".toCharArray()
        val passphraseWithNot = "a\u00ACb".toCharArray() // U+00AC is the NOT SIGN (¬)

        val key1 = cryptoProvider.deriveMasterKey(passphraseWithEuro, saltA)
        val key2 = cryptoProvider.deriveMasterKey(passphraseWithNot, saltA)

        assertFalse(
            "REGRESSION: '€' and '¬' must produce different keys (they share a low byte under Latin-1, " +
            "but are distinct Unicode characters — old charsToBytes bug would make them collide)",
            key1.contentEquals(key2)
        )
    }

    @Test
    fun `deriveMasterKey handles emoji passphrase without crashing`() = runBlocking {
        // Emoji are above U+FFFF and require surrogate pairs in UTF-16 (and multiple bytes in UTF-8).
        // The old Latin-1 truncation would mangle them beyond recognition.
        // This test just verifies we don't crash and produce a non-zero key.
        val password = "🔐ShadowVault🛡️".toCharArray()
        val salt = ByteArray(16) { 99.toByte() }

        val key = cryptoProvider.deriveMasterKey(password, salt)
        assertEquals(32, key.size)
        assertTrue(key.any { it != 0.toByte() })
    }
}
