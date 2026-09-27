package com.arlix.shadowvault.crypto

import com.arlix.shadowvault.domain.ICryptoProvider
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.CharBuffer
import java.nio.ByteBuffer

class ShadowCryptoProvider : ICryptoProvider {

    override suspend fun deriveMasterKey(password: CharArray, salt: ByteArray): ByteArray {
        // [T20] Passphrase strength checked at creation only, not on every unlock.

        // Run Argon2id on background CPU-thread to prevent UI freeze (ANR).
        return withContext(Dispatchers.Default) {
            val result = ByteArray(32) // 256-bit key output

            // [T8] Argon2id — Memory-hard KDF configured for QA memory limits.
            val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(3)
                .withMemoryAsKB(65536)
                .withParallelism(4)
                .withSalt(salt)
                .build()

            val generator = Argon2BytesGenerator()
            generator.init(parameters)

            // Convert CharArray → UTF-8 ByteArray WITHOUT allocating a String on the JVM heap.
            // [T11] String(chars) would pin plaintext in heap indefinitely. See charArrayToUtf8Bytes doc.
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
            // Fallback for host-JVM unit tests where the .so isn't loaded
            buffer.fill(0)
        }
    }
}

/**
 * Converts a CharArray to a UTF-8 encoded ByteArray using the Java NIO path.
 *
 * WHY THIS EXISTS:
 * The naive way — String(chars).toByteArray(UTF_8) — allocates an immutable String on the
 * JVM heap. Immutable Strings cannot be zeroed. A memory forensics tool (or GC heap dump)
 * would expose the plaintext password indefinitely. This is exactly "The JVM String Trap" (T11).
 *
 * THIS approach:
 * 1. CharBuffer.wrap(chars) — wraps the ORIGINAL array in-place. Zero copy. Zero allocation.
 * 2. encoder.encode(cb) — writes UTF-8 bytes directly into a new ByteBuffer. No String object.
 * 3. We drain the ByteBuffer into a plain ByteArray, which we can explicitly wipe later via wipeNative().
 *
 * The caller is responsible for calling wipe() on the returned ByteArray after use.
 */
fun charArrayToUtf8Bytes(chars: CharArray): ByteArray {
    val cb: CharBuffer = CharBuffer.wrap(chars)
    val bb: ByteBuffer = Charsets.UTF_8.newEncoder().encode(cb)
    val bytes = ByteArray(bb.limit())
    bb.get(bytes)
    return bytes
}

/**
 * Constant-time comparison for sensitive character arrays (e.g., passwords).
 * [T9] Mitigates timing side-channel attacks.
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
