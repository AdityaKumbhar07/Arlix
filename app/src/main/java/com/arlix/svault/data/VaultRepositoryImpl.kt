package com.arlix.svault.data

import android.content.Context
import androidx.room.Room
import com.arlix.svault.domain.IVaultRepository
import com.arlix.svault.domain.VaultEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.sqlcipher.database.SupportFactory

class VaultRepositoryImpl(private val context: Context) : IVaultRepository {

    private var database: VaultDatabase? = null

    override suspend fun openVault(masterKey: ByteArray, isColdVault: Boolean): Boolean {
        // If a vault is already open, close it first to prevent memory leaks
        closeVault()

        val dbName = if (isColdVault) "vault_secondary.db" else "vault_primary.db"

        // Pass the Argon2id key directly into SQLCipher's C++ engine
        val factory = SupportFactory(masterKey)

        database = Room.databaseBuilder(context, VaultDatabase::class.java, dbName)
            .openHelperFactory(factory)
            .build()

        return true
    }

    override suspend fun closeVault() {
        database?.close()
        database = null
    }

    override fun isVaultOpen(): Boolean {
        return database != null
    }

    override fun getAllEntries(): Flow<List<VaultEntry>> {
        val db = database ?: throw IllegalStateException("Vault is locked!")
        // Read from DB, map to Domain, and use a dummy CharArray for now
        // (We will hook up the password Decryption in Phase 4)
        return db.vaultDao().getAllCredentials().map { list ->
            list.map { entity -> entity.toDomain(entity.passwordEncrypted.toCharArray()) }
        }
    }

    override suspend fun addEntry(entry: VaultEntry) {
        val db = database ?: throw IllegalStateException("Vault is locked!")
        // (We will hook up the password Encryption in Phase 4)
        val entity = VaultEntity(
            id = entry.id,
            title = entry.title,
            username = entry.username,
            notes = entry.notes,
            passwordEncrypted = String(entry.passwordSecret), // Temp string mapping
            createdAt = entry.createdAt,
            modifiedAt = entry.modifiedAt
        )
        db.vaultDao().insertCredential(entity)
    }

    override suspend fun deleteEntry(entryId: String) {
        val db = database ?: throw IllegalStateException("Vault is locked!")
        db.vaultDao().deleteCredential(entryId)
    }
}
