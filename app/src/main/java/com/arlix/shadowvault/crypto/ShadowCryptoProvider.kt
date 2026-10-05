package com.arlix.shadowvault.crypto

import com.arlix.shadowvault.domain.ICryptoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/**
 * Argon2id cost parameters (RFC 9106, "second recommended option").
 *
 * FROZEN: these values are baked into every existing vault. Changing any of them makes
 * existing vaults impossible to open. A future change must be a versioned scheme with a
 * migration, never an in-place edit.
 */
private object KdfParams {
    const val KEY_BYTES = 32
    const val ITERATIONS = 3
    const val MEMORY_KIB = 65536 // 64 MiB
    const val LANES = 4
}

class ShadowCryptoProvider : ICryptoProvider {

    override suspend fun deriveMasterKey(password: CharArray, salt: ByteArray): ByteArray =
        // Argon2id is CPU- and memory-heavy, so it never runs on the main thread.
        withContext(Dispatchers.Default) {
            val result = ByteArray(KdfParams.KEY_BYTES)
            var passwordBytes: ByteArray? = null
            try {
                val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withIterations(KdfParams.ITERATIONS)
                    .withMemoryAsKB(KdfParams.MEMORY_KIB)
                    .withParallelism(KdfParams.LANES)
                    .withSalt(salt)
                    .build()

                val generator = Argon2BytesGenerator()
                generator.init(parameters)

                passwordBytes = charArrayToUtf8Bytes(password)
                generator.generateBytes(passwordBytes, result, 0, result.size)

                // If the caller was cancelled while we were computing, withContext would drop
                // the result without anyone wiping it. Check here so we can wipe it ourselves.
                ensureActive()
                result
            } catch (t: Throwable) {
                wipe(result)
                throw t
            } finally {
                passwordBytes?.let { wipe(it) }
            }
        }

    override fun wipe(buffer: ByteArray) = MemorySanitizer.wipe(buffer)
}

/**
 * Converts a CharArray to UTF-8 bytes without creating a String.
 *
 * The encoder needs a scratch buffer larger than the final result, and that buffer also
 * contains the secret, so it is zeroed before returning. The caller must wipe the returned
 * array after use. Malformed input (e.g. a lone surrogate) is replaced instead of throwing,
 * and the same input always produces the same bytes.
 */
fun charArrayToUtf8Bytes(chars: CharArray): ByteArray {
    val encoder = Charsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    val scratch = ByteBuffer.allocate((chars.size * encoder.maxBytesPerChar()).toInt() + 4)
    try {
        encoder.encode(CharBuffer.wrap(chars), scratch, true)
        encoder.flush(scratch)
        scratch.flip()
        val out = ByteArray(scratch.remaining())
        scratch.get(out)
        return out
    } finally {
        java.util.Arrays.fill(scratch.array(), 0.toByte())
    }
}

/**
 * Converts UTF-8 bytes to a CharArray without creating a String. The decoder's scratch
 * buffer also holds the secret, so it is zeroed before returning. The caller owns (and must
 * wipe) the returned array.
 */
fun utf8BytesToChars(bytes: ByteArray): CharArray {
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    // UTF-8 never produces more chars than bytes, so this is always big enough.
    val scratch = CharBuffer.allocate(bytes.size)
    try {
        decoder.decode(ByteBuffer.wrap(bytes), scratch, true)
        decoder.flush(scratch)
        scratch.flip()
        val out = CharArray(scratch.remaining())
        scratch.get(out)
        return out
    } finally {
        java.util.Arrays.fill(scratch.array(), '\u0000')
    }
}

/**
 * Compares two locally typed passphrases (the setup "confirm" field). Constant time is
 * harmless here but not security-critical: both values come from the same user on the
 * same device.
 */
fun constantTimeEquals(a: CharArray, b: CharArray): Boolean {
    if (a.size != b.size) return false
    val aBytes = charArrayToUtf8Bytes(a)
    val bBytes = charArrayToUtf8Bytes(b)
    try {
        return java.security.MessageDigest.isEqual(aBytes, bBytes)
    } finally {
        aBytes.fill(0)
        bBytes.fill(0)
    }
}
