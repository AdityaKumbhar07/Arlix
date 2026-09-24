package com.arlix.svault.crypto

import com.arlix.svault.domain.ICryptoProvider
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.CharBuffer
import java.nio.ByteBuffer

class ShadowCryptoProvider : ICryptoProvider {

    override suspend fun deriveMasterKey(password: CharArray, salt: ByteArray): ByteArray {
        // NOTE: T20 (passphrase strength) is checked at *creation time* only (in VaultViewModel.createVault).
        // We deliberately do NOT re-check it on every unlock — that would be wrong for a correct
        // passphrase that happens to be exactly at the boundary.

        // Run heavy Argon2id math on a background CPU-thread so the UI never freezes (ANR prevention)
        return withContext(Dispatchers.Default) {
            val result = ByteArray(32) // 256-bit key output

            // [T8]: Argon2id — Memory-hard KDF. Each guess costs 64 MB of RAM + 3 sequential passes.
            // This is deliberately low-end for v1. For maximum offline brute-force resistance,
            // raise withMemoryAsKB to e.g. 262144 (256 MB) once device benchmarks confirm it's
            // within the ANR threshold on the Vivo T3x. OWASP 2024 recommends ≥19 MB minimum;
            // 64 MB is reasonable but not "mathematically infeasible" for weak passphrases.
            val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(3)
                .withMemoryAsKB(65536) // 64 MB — bump to 262144 after T3x benchmarking
                .withParallelism(4)
                .withSalt(salt)
                .build()

            val generator = Argon2BytesGenerator()
            generator.init(parameters)

            // Convert CharArray → UTF-8 ByteArray WITHOUT allocating a String on the JVM heap.
            // String(chars) would pin a plaintext password in the heap indefinitely — T11 violation.
            // NIO path: CharBuffer.wrap() references the original array in-place (no copy),
            // encode() writes UTF-8 bytes into a new ByteBuffer, which we drain into a local array.
            val passwordBytes = charArrayToUtf8Bytes(password)
            try {
                generator.generateBytes(passwordBytes, result, 0, result.size)
            } finally {
                // Wipe the temporary byte representation regardless of success or exception
                wipe(passwordBytes)
            }

            result
        }
    }

    /**
     * Placeholder — field-level AES-256-GCM encryption is a planned Phase 4 milestone.
     * SQLCipher's page-level encryption (AES-256) is the active protection layer right now.
     * This method MUST NOT be called until implemented; it has been removed from all call sites.
     */
    override fun encryptData(plaintext: ByteArray, key: ByteArray): ByteArray {
        throw NotImplementedError(
            "Phase 4 TODO: encryptData not yet implemented. " +
            "SQLCipher page-level encryption is the active defense. " +
            "Do not call this method until Phase 4 is complete."
        )
    }

    /**
     * Placeholder — see encryptData.
     */
    override fun decryptData(ciphertext: ByteArray, key: ByteArray): ByteArray {
        throw NotImplementedError(
            "Phase 4 TODO: decryptData not yet implemented. " +
            "Do not call this method until Phase 4 is complete."
        )
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
internal fun charArrayToUtf8Bytes(chars: CharArray): ByteArray {
    val cb: CharBuffer = CharBuffer.wrap(chars)
    val bb: ByteBuffer = Charsets.UTF_8.newEncoder().encode(cb)
    val bytes = ByteArray(bb.limit())
    bb.get(bytes)
    return bytes
}
