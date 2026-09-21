package com.arlix.svault.crypto

import android.util.Base64
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object C_crypto_ColdVaultManager {

    const val K_CRYPTO_VERSION: Byte = 0x01
    const val K_SALT_SIZE_BYTES = 16
    const val K_IV_SIZE_BYTES = 12
    const val K_KEY_SIZE_BITS = 256
    const val K_KEY_SIZE_BYTES = 32
    const val K_DEFAULT_PBKDF2_ITERATIONS = 600_000
    private const val K_GCM_TAG_LENGTH_BITS = 128
    private val K_DEK_AAD = "SHADOWVAULT_DEK_V1".toByteArray(Charsets.UTF_8)

    fun f_crypto_generateRandomBytes(v_size: Int): ByteArray {
        val v_bytes = ByteArray(v_size)
        SecureRandom().nextBytes(v_bytes)
        return v_bytes
    }

    fun f_crypto_deriveKek(
        v_passphrase: CharArray,
        v_salt: ByteArray,
        v_iterations: Int = K_DEFAULT_PBKDF2_ITERATIONS
    ): ByteArray {
        val v_spec = PBEKeySpec(v_passphrase, v_salt, v_iterations, K_KEY_SIZE_BITS)
        return try {
            val v_factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            v_factory.generateSecret(v_spec).encoded
        } finally {
            v_spec.clearPassword()
        }
    }

    fun f_crypto_wrapDek(v_dek: ByteArray, v_kek: ByteArray): String {
        val v_iv = f_crypto_generateRandomBytes(K_IV_SIZE_BYTES)
        val v_secretKey = SecretKeySpec(v_kek, "AES")
        val v_cipher = Cipher.getInstance("AES/GCM/NoPadding")
        v_cipher.init(Cipher.ENCRYPT_MODE, v_secretKey, GCMParameterSpec(K_GCM_TAG_LENGTH_BITS, v_iv))
        v_cipher.updateAAD(K_DEK_AAD)
        val v_ciphertext = v_cipher.doFinal(v_dek)
        // Envelope: [Version (1B) || IV (12B) || Ciphertext+Tag (48B)]
        val v_blob = ByteArray(1 + K_IV_SIZE_BYTES + v_ciphertext.size)
        v_blob[0] = K_CRYPTO_VERSION
        System.arraycopy(v_iv, 0, v_blob, 1, K_IV_SIZE_BYTES)
        System.arraycopy(v_ciphertext, 0, v_blob, 1 + K_IV_SIZE_BYTES, v_ciphertext.size)
        return Base64.encodeToString(v_blob, Base64.NO_WRAP)
    }
    
    fun f_crypto_unwrapDek(v_wrappedDekBase64: String, v_kek: ByteArray): ByteArray {
        val v_blob = Base64.decode(v_wrappedDekBase64, Base64.NO_WRAP)
        require(v_blob.size >= 1 + K_IV_SIZE_BYTES + 16) { "Corrupted wrapped DEK payload" }
        require(v_blob[0] == K_CRYPTO_VERSION) { "Unsupported cryptographic version: ${v_blob[0]}" }
        val v_iv = ByteArray(K_IV_SIZE_BYTES)
        System.arraycopy(v_blob, 1, v_iv, 0, K_IV_SIZE_BYTES)
        val v_ciphertextSize = v_blob.size - 1 - K_IV_SIZE_BYTES
        val v_ciphertext = ByteArray(v_ciphertextSize)
        System.arraycopy(v_blob, 1 + K_IV_SIZE_BYTES, v_ciphertext, 0, v_ciphertextSize)
        val v_secretKey = SecretKeySpec(v_kek, "AES")
        val v_cipher = Cipher.getInstance("AES/GCM/NoPadding")
        v_cipher.init(Cipher.DECRYPT_MODE, v_secretKey, GCMParameterSpec(K_GCM_TAG_LENGTH_BITS, v_iv))
        v_cipher.updateAAD(K_DEK_AAD)
        return v_cipher.doFinal(v_ciphertext)
    }

    fun f_crypto_encryptPayload(
        v_plaintext: String,
        v_dek: ByteArray,
        v_entryId: String,
        v_chamber: String
    ): String {
        val v_iv = f_crypto_generateRandomBytes(K_IV_SIZE_BYTES)
        val v_aad = "$v_entryId:$v_chamber".toByteArray(Charsets.UTF_8)
        val v_secretKey = SecretKeySpec(v_dek, "AES")
        val v_cipher = Cipher.getInstance("AES/GCM/NoPadding")
        v_cipher.init(Cipher.ENCRYPT_MODE, v_secretKey, GCMParameterSpec(K_GCM_TAG_LENGTH_BITS, v_iv))
        v_cipher.updateAAD(v_aad)
        val v_ciphertext = v_cipher.doFinal(v_plaintext.toByteArray(Charsets.UTF_8))
        // Envelope: [Version (1B) || IV (12B) || Ciphertext+Tag]
        val v_blob = ByteArray(1 + K_IV_SIZE_BYTES + v_ciphertext.size)
        v_blob[0] = K_CRYPTO_VERSION
        System.arraycopy(v_iv, 0, v_blob, 1, K_IV_SIZE_BYTES)
        System.arraycopy(v_ciphertext, 0, v_blob, 1 + K_IV_SIZE_BYTES, v_ciphertext.size)
        return Base64.encodeToString(v_blob, Base64.NO_WRAP)
    }

    fun f_crypto_decryptPayload(
        v_encodedBlob: String,
        v_dek: ByteArray,
        v_entryId: String,
        v_chamber: String
    ): String {
        val v_blob = Base64.decode(v_encodedBlob, Base64.NO_WRAP)
        require(v_blob.size >= 1 + K_IV_SIZE_BYTES + 16) { "Corrupted ciphertext payload" }
        require(v_blob[0] == K_CRYPTO_VERSION) { "Unsupported cryptographic version: ${v_blob[0]}" }
        val v_iv = ByteArray(K_IV_SIZE_BYTES)
        System.arraycopy(v_blob, 1, v_iv, 0, K_IV_SIZE_BYTES)
        val v_ciphertextSize = v_blob.size - 1 - K_IV_SIZE_BYTES
        val v_ciphertext = ByteArray(v_ciphertextSize)
        System.arraycopy(v_blob, 1 + K_IV_SIZE_BYTES, v_ciphertext, 0, v_ciphertextSize)
        val v_aad = "$v_entryId:$v_chamber".toByteArray(Charsets.UTF_8)
        val v_secretKey = SecretKeySpec(v_dek, "AES")
        val v_cipher = Cipher.getInstance("AES/GCM/NoPadding")
        v_cipher.init(Cipher.DECRYPT_MODE, v_secretKey, GCMParameterSpec(K_GCM_TAG_LENGTH_BITS, v_iv))
        v_cipher.updateAAD(v_aad)
        val v_plaintextBytes = v_cipher.doFinal(v_ciphertext)
        return String(v_plaintextBytes, Charsets.UTF_8)
    }
    fun f_crypto_zeroize(v_buffer: ByteArray) {
        Arrays.fill(v_buffer, 0.toByte())
    }
}