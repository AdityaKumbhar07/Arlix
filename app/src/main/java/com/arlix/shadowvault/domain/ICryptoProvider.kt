package com.arlix.shadowvault.domain

/**
 * Core Domain Port for all cryptographic operations.
 * This interface isolates the heavy math (Argon2id/AES) from the rest of the app.
 */
interface ICryptoProvider {

    /**
     * Derives a massive, mathematically unbreakable Master Key from the user's password.
     * Uses ByteArray to prevent JVM String traps.
     *
     * @param password The raw Master Passphrase buffer.
     * @param salt The unique cryptographic salt for this device/vault.
     * @return A 32-byte (256-bit) raw key buffer.
     */
    suspend fun deriveMasterKey(password: CharArray, salt: ByteArray): ByteArray

    /**
     * Encrypts the raw database bytes before they hit the disk.
     */
    fun encryptData(plaintext: ByteArray, key: ByteArray): ByteArray

    /**
     * Decrypts the raw database bytes back into memory.
     * @throws DecryptionFailedException if the password was wrong or data was tampered with.
     */
    @Throws(DecryptionFailedException::class)
    fun decryptData(ciphertext: ByteArray, key: ByteArray): ByteArray

    /**
     * Zeroizes a byte array in memory (overwrites it with zeros).
     */
    fun wipe(buffer: ByteArray)
}

/**
 * Custom Domain Exception for when a hacker tampers with the database file,
 * or the user types the wrong password.
 */
class DecryptionFailedException(message: String) : Exception(message)
