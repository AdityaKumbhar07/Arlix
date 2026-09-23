package com.arlix.svault.domain

import kotlinx.coroutines.flow.Flow

/**
 * Domain Port for database operations.
 */
interface IVaultRepository {

    // Lifecycle
    suspend fun openVault(masterKey: ByteArray, isColdVault: Boolean = false): Boolean
    suspend fun closeVault()
    fun isVaultOpen(): Boolean

    // Operations
    fun getAllEntries(): Flow<List<VaultEntry>>
    suspend fun addEntry(entry: VaultEntry)
    suspend fun deleteEntry(entryId: String)
}
