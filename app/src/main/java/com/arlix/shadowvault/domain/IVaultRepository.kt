package com.arlix.shadowvault.domain

import kotlinx.coroutines.flow.Flow

/**
 * Domain Port for database operations.
 */
interface IVaultRepository {

    // Lifecycle
    suspend fun openVault(masterKey: ByteArray, isColdVault: Boolean = false): Boolean
    suspend fun closeVault()
    fun isVaultOpen(): Boolean

    /**
     * Returns true if the encrypted database file for this vault already exists on disk.
     * This is the ONLY correct way to distinguish first-time setup from a regular unlock.
     * SQLCipher silently creates a new DB if the file doesn't exist — there is no error to catch.
     */
    fun vaultExists(isColdVault: Boolean = false): Boolean

    // Operations
    fun getAllEntries(): Flow<List<VaultEntry>>
    suspend fun addEntry(entry: VaultEntry)
    suspend fun deleteEntry(entryId: String)
}
