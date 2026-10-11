package com.arlix.shadowvault.crypto

import kotlinx.coroutines.test.runTest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * Freezes the key derivation. The expected value is computed with the Argon2 parameters
 * written out as literals HERE, independently of KdfParams. If anyone changes the
 * parameters (or the passphrase encoding) in the app, every existing vault would stop
 * opening, and this test fails first.
 */
class KdfFrozenTest {

    private val salt = ByteArray(16) { it.toByte() }

    private fun referenceKey(passphraseBytes: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(3)
            .withMemoryAsKB(65536)
            .withParallelism(4)
            .withSalt(salt)
            .build()
        val out = ByteArray(32)
        Argon2BytesGenerator().also { it.init(params) }
            .generateBytes(passphraseBytes, out, 0, out.size)
        return out
    }

    @Test fun asciiPassphraseMatchesFrozenParameters() = runTest {
        val actual = ShadowCryptoProvider().deriveMasterKey("correct horse".toCharArray(), salt)
        assertArrayEquals(referenceKey("correct horse".toByteArray(Charsets.UTF_8)), actual)
    }

    @Test fun unicodePassphraseIsEncodedAsUtf8() = runTest {
        val text = "pässwörd🙂"
        val actual = ShadowCryptoProvider().deriveMasterKey(text.toCharArray(), salt)
        assertArrayEquals(referenceKey(text.toByteArray(Charsets.UTF_8)), actual)
    }
}
