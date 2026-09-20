package com.arlix.svault.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object C_crypto_ColdVaultSentinel {

    private const val K_SALT_SIZE_BYTES = 16
    private const val K_IV_SIZE_BYTES = 12
    private const val K_KEY_SIZE_BITS = 256
    private const val K_PBKDF2_ITERATIONS = 100_000
    private const val K_GCM_TAG_LENGTH_BITS = 128
    const val K_SENTINEL_PLAINTEXT = "SHADOWVAULT_COLD_SENTINEL"

    fun f_crypto_encryptSentinel(v_passphrase: String): String {

        val v_random = SecureRandom()
        val v_salt = ByteArray(K_SALT_SIZE_BYTES).also { v_random.nextBytes(it) }
        val v_iv = ByteArray(K_IV_SIZE_BYTES).also { v_random.nextBytes(it) }

        // PBKDF2 Key Derivation
        val v_spec = PBEKeySpec(v_passphrase.toCharArray(), v_salt, K_PBKDF2_ITERATIONS, K_KEY_SIZE_BITS)
        val v_factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val v_keyBytes = v_factory.generateSecret(v_spec).encoded
        val v_secretKey = SecretKeySpec(v_keyBytes, "AES")
        
        // AES-256-GCM Encrypt
        val v_cipher = Cipher.getInstance("AES/GCM/NoPadding")
        v_cipher.init(Cipher.ENCRYPT_MODE, v_secretKey, GCMParameterSpec(K_GCM_TAG_LENGTH_BITS, v_iv))
        val v_ciphertext = v_cipher.doFinal(K_SENTINEL_PLAINTEXT.toByteArray(Charsets.UTF_8))

        // Package: [Salt (16) + IV (12) + Ciphertext]
        val v_combined = ByteArray(v_salt.size + v_iv.size + v_ciphertext.size)
        System.arraycopy(v_salt, 0, v_combined, 0, v_salt.size)
        System.arraycopy(v_iv, 0, v_combined, v_salt.size, v_iv.size)
        System.arraycopy(v_ciphertext, 0, v_combined, v_salt.size + v_iv.size, v_ciphertext.size)
        return Base64.encodeToString(v_combined, Base64.NO_WRAP)
    }

    fun f_crypto_verifySentinel(v_passphrase: String, v_encodedBlob: String): Boolean {
        return try {

            val v_combined = Base64.decode(v_encodedBlob, Base64.NO_WRAP)
            if (v_combined.size < K_SALT_SIZE_BYTES + K_IV_SIZE_BYTES) return false
            val v_salt = ByteArray(K_SALT_SIZE_BYTES)
            val v_iv = ByteArray(K_IV_SIZE_BYTES)
            val v_ciphertextSize = v_combined.size - K_SALT_SIZE_BYTES - K_IV_SIZE_BYTES
            val v_ciphertext = ByteArray(v_ciphertextSize)
            System.arraycopy(v_combined, 0, v_salt, 0, K_SALT_SIZE_BYTES)
            System.arraycopy(v_combined, K_SALT_SIZE_BYTES, v_iv, 0, K_IV_SIZE_BYTES)
            System.arraycopy(v_combined, K_SALT_SIZE_BYTES + K_IV_SIZE_BYTES, v_ciphertext, 0, v_ciphertextSize)

            // Derive key with same salt
            val v_spec = PBEKeySpec(v_passphrase.toCharArray(), v_salt, K_PBKDF2_ITERATIONS, K_KEY_SIZE_BITS)
            val v_factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val v_keyBytes = v_factory.generateSecret(v_spec).encoded
            val v_secretKey = SecretKeySpec(v_keyBytes, "AES")
            
            // AES-256-GCM Decrypt & Tag Authentication
            val v_cipher = Cipher.getInstance("AES/GCM/NoPadding")
            v_cipher.init(Cipher.DECRYPT_MODE, v_secretKey, GCMParameterSpec(K_GCM_TAG_LENGTH_BITS, v_iv))
            val v_decryptedBytes = v_cipher.doFinal(v_ciphertext)
            String(v_decryptedBytes, Charsets.UTF_8) == K_SENTINEL_PLAINTEXT
        } catch (e: Exception) {
            // GMAC Tag failed: Wrong Password 2!
            false
        }
    }
}