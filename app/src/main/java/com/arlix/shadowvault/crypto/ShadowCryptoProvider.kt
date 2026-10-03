package com.arlix.shadowvault.crypto

import com.arlix.shadowvault.domain.ICryptoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

class ShadowCryptoProvider : ICryptoProvider {

    override suspend fun deriveMasterKey(password: CharArray, salt: ByteArray): ByteArray {
        // Argon2id runs on a background thread to avoid freezing the UI (ANR).
        return withContext(Dispatchers.Default) {
            val result = ByteArray(32) // 256-bit key

            // [T8] Argon2id: 64 MiB memory, 3 passes, 4 lanes.
            val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(3)
                .withMemoryAsKB(65536)
                .withParallelism(4)
                .withSalt(salt)
                .build()

            val generator = Argon2BytesGenerator()
            generator.init(parameters)

            val passwordBytes = charArrayToUtf8Bytes(password)
            try {
                generator.generateBytes(passwordBytes, result, 0, result.size)
            } finally {
                wipe(passwordBytes)
            }
            result
        }
    }

    override fun wipe(buffer: ByteArray) {
        try {
            MemorySanitizer.wipeNative(buffer)
        } catch (e: UnsatisfiedLinkError) {
            // Host-JVM unit tests: native library not loaded.
            buffer.fill(0)
        }
    }
}

/**
 * Converts a CharArray to UTF-8 bytes without creating a String.
 *
 * The encoder needs a scratch buffer that is larger than the final result. That scratch
 * buffer also contains the password, so it is zeroed before returning. The caller must
 * wipe the returned array after use.
 *
 * Malformed input (e.g. a lone surrogate) is replaced instead of throwing, so a strange
 * character can never crash vault creation. The same input always gives the same bytes.
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
 * Compares two locally typed passphrases (setup confirm field). Constant-time is harmless
 * here but not security-critical: both values come from the same user on the same device.
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
