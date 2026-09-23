package com.arlix.svault.crypto

import com.arlix.svault.domain.DecryptionFailedException
import com.arlix.svault.domain.ICryptoProvider
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShadowCryptoProvider : ICryptoProvider {

    override suspend fun deriveMasterKey(password: CharArray, salt: ByteArray): ByteArray {
        // [Threat T20]: Reject trivially weak passwords instantly.
        if (password.size < 5) {
            throw IllegalArgumentException("Passphrase must be at least 5 characters.")
        }

        // Run heavy math on a background thread so the UI doesn't freeze
        return withContext(Dispatchers.Default) {
            val result = ByteArray(32) // 256-bit key

            // [Threat T8]: Argon2id configuration (Memory-Hard to kill GPU clusters)
            val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(3)
                .withMemoryAsKB(65536) // 64 MB of RAM required per guess
                .withParallelism(4)
                .withSalt(salt)
                .build()

            val generator = Argon2BytesGenerator()
            generator.init(parameters)

            // Generate the key
            val passwordBytes = charsToBytes(password)
            generator.generateBytes(passwordBytes, result, 0, result.size)

            // Wipe the temporary byte array
            wipe(passwordBytes)

            result
        }
    }

    override fun encryptData(plaintext: ByteArray, key: ByteArray): ByteArray {
        TODO("Step 2.3: AES-256-GCM Encryption coming next")
    }

    override fun decryptData(ciphertext: ByteArray, key: ByteArray): ByteArray {
        TODO("Step 2.3: AES-256-GCM Decryption coming next")
    }

    override fun wipe(buffer: ByteArray) {
        buffer.fill(0)
    }

    // Helper to safely convert CharArray to ByteArray for BouncyCastle
    private fun charsToBytes(chars: CharArray): ByteArray {
        val bytes = ByteArray(chars.size)
        for (i in chars.indices) {
            bytes[i] = chars[i].code.toByte()
        }
        return bytes
    }
}
