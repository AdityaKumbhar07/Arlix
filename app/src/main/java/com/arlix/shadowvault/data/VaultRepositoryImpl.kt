package com.arlix.shadowvault.data

import android.content.Context
import androidx.room.Room
import com.arlix.shadowvault.BuildConfig
import com.arlix.shadowvault.crypto.charArrayToUtf8Bytes
import com.arlix.shadowvault.domain.IVaultRepository
import com.arlix.shadowvault.domain.VaultEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class VaultRepositoryImpl(private val context: Context) : IVaultRepository {

    @Volatile private var database: VaultDatabase? = null
    private val vaultMutex = Mutex()
    @Volatile private var openDbName: String? = null

    override fun vaultExists(isColdVault: Boolean): Boolean {
        val dbName = if (isColdVault) "vault_secondary.db" else "vault_primary.db"
        return context.getDatabasePath(dbName).exists()
    }

    override suspend fun openVault(masterKey: ByteArray, isColdVault: Boolean): Boolean =
        vaultMutex.withLock {
            val dbName = if (isColdVault) "vault_secondary.db" else "vault_primary.db"
            // Re-opening the SAME vault: close the old connection first.
            // Opening the OTHER vault: leave the current one open until the new key is
            // proven correct, so a wrong cold password never closes the hot vault.
            if (openDbName == dbName) closeVaultLocked()

            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(masterKey, null, true)

            val db = Room.databaseBuilder(context, VaultDatabase::class.java, dbName)
                .openHelperFactory(factory)
                .apply {
                    if (BuildConfig.DEBUG) {
                        fallbackToDestructiveMigration(true)
                    }
                    // RELEASE: no destructive fallback. Add a real Migration before any schema bump.
                }
                .build()

            try {
                db.openHelper.writableDatabase // forces the open; throws on a wrong key
            } catch (e: Exception) {
                runCatching { db.close() }
                throw e                        // the previously open vault is untouched
            }

            // Key proven correct: swap, then close the previous vault.
            val old = database
            database = db
            openDbName = dbName
            old?.close()
            true
        }

    override suspend fun closeVault() = vaultMutex.withLock { closeVaultLocked() }

    private fun closeVaultLocked() {
        database?.close()
        database = null
        openDbName = null
    }

    override fun isVaultOpen(): Boolean = database != null

    override fun getAllEntries(): Flow<List<VaultEntry>> = flow {
        val db = vaultMutex.withLock { database } ?: throw IllegalStateException("Vault is locked!")
        emitAll(
            db.vaultDao().getAllCredentials().map { list ->
                list.map { entity ->
                    try {
                        entity.toDomain()
                    } finally {
                        entity.passwordBytes.fill(0) // wipe the raw row copy
                    }
                }
            }
        )
    }

    override suspend fun addEntry(entry: VaultEntry) = vaultMutex.withLock {
        val db = database ?: throw IllegalStateException("Vault is locked!")
        val passwordBytes = charArrayToUtf8Bytes(entry.passwordSecret)
        try {
            db.vaultDao().insertCredential(
                VaultEntity(
                    id = entry.id, title = entry.title, username = entry.username,
                    notes = entry.notes, category = entry.category, passwordBytes = passwordBytes,
                    createdAt = entry.createdAt, modifiedAt = entry.modifiedAt
                )
            )
        } finally {
            passwordBytes.fill(0) // wiped even if the insert fails
        }
    }

    override suspend fun deleteEntry(entryId: String) = vaultMutex.withLock {
        val db = database ?: throw IllegalStateException("Vault is locked!")
        db.vaultDao().deleteCredential(entryId)
    }
}
