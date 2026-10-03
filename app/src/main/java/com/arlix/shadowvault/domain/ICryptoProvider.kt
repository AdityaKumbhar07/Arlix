package com.arlix.shadowvault.domain

/**
 * Core Domain Port for all cryptographic operations.
 * This interface isolates the heavy math (Argon2id/AES) from the rest of the app.
 */
interface ICryptoProvider {

    /**
     * Derives a 256-bit master key from the user's password using Argon2id.
     * Uses ByteArray to prevent JVM String traps.
     *
     * @param password The raw Master Passphrase buffer.
     * @param salt The unique cryptographic salt for this device/vault.
     * @return A 32-byte (256-bit) raw key buffer.
     */
    suspend fun deriveMasterKey(password: CharArray, salt: ByteArray): ByteArray

    /**
     * Zeroizes a byte array in memory (overwrites it with zeros).
     */
    fun wipe(buffer: ByteArray)
} 
