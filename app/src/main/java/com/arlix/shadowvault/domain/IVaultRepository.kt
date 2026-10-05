package com.arlix.shadowvault.domain

import kotlinx.coroutines.flow.Flow

/**
 * Domain port for vault storage. At most one vault (hot or cold) is open at a time.
 */
interface IVaultRepository {

    // ---- Lifecycle ----

    /**
     * Opens a vault with an already-derived key. Opening the OTHER vault leaves the current
     * one open until the new key is proven correct. Throws if the key is wrong.
     */
    suspend fun openVault(masterKey: ByteArray, isColdVault: Boolean = false): Boolean

    /** Closes the open vault (if any) and zeroes its key. Safe to call at any time. */
    suspend fun closeVault()

    fun isVaultOpen(): Boolean

    /**
     * True if the encrypted database file for this vault exists on disk. This is the ONLY
     * correct way to tell first-time setup from a normal unlock: SQLCipher silently creates a
     * new database if the file does not exist.
     */
    fun vaultExists(isColdVault: Boolean = false): Boolean

    // ---- Entries ----

    fun getAllEntries(): Flow<List<VaultEntry>>
    suspend fun addEntry(entry: VaultEntry)
    suspend fun deleteEntry(entryId: String)

    /** One-shot read of every entry. The caller must [VaultEntry.annihilate] them when done. */
    suspend fun readAllEntries(): List<VaultEntry>

    /** Inserts or overwrites (by id) all entries in ONE transaction: all saved or none. */
    suspend fun addEntries(entries: List<VaultEntry>)

    // ---- Passphrase change ----

    /**
     * Re-keys the open vault by building a verified copy under [newKey] and swapping it in
     * with a single atomic rename; the old file is untouched until then.
     *
     * [oldKey] must equal the open vault's key ([WrongPassphraseException] otherwise).
     * On success the vault is CLOSED; the caller must lock the UI and the user unlocks with
     * the new passphrase. On failure before the swap nothing live has changed.
     */
    suspend fun changePassphrase(oldKey: ByteArray, newKey: ByteArray, isColdVault: Boolean)
}
