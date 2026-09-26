package com.arlix.shadowvault.data

import android.content.Context
import androidx.room.Room
import com.arlix.shadowvault.crypto.charArrayToUtf8Bytes
import com.arlix.shadowvault.domain.IVaultRepository
import com.arlix.shadowvault.domain.VaultEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import com.arlix.shadowvault.BuildConfig

class VaultRepositoryImpl(private val context: Context) : IVaultRepository {

    @Volatile private var database: VaultDatabase? = null
    private val vaultMutex = Mutex()

    /**
     * Returns true if the encrypted database file for this vault already physically exists on disk.
     */
    override fun vaultExists(isColdVault: Boolean): Boolean {
        val dbName = if (isColdVault) "vault_secondary.db" else "vault_primary.db"
        return context.getDatabasePath(dbName).exists()
    }

    override suspend fun openVault(masterKey: ByteArray, isColdVault: Boolean): Boolean =
        vaultMutex.withLock {
            closeVaultLocked()
            System.loadLibrary("sqlcipher")
            val dbName = if (isColdVault) "vault_secondary.db" else "vault_primary.db"
            val factory = SupportOpenHelperFactory(masterKey, null, true)

            database = Room.databaseBuilder(context, VaultDatabase::class.java, dbName)
                .openHelperFactory(factory)
                .apply {
                    if (BuildConfig.DEBUG) {
                        fallbackToDestructiveMigration(true)
                    }
                    // RELEASE BUILDS: no destructive fallback. Add a real Migration(x, y)
                    // here before shipping any schema bump.
                }
                .build()

            database?.openHelper?.writableDatabase
            true
        }

    override suspend fun closeVault() = vaultMutex.withLock { closeVaultLocked() }

    private fun closeVaultLocked() {
        database?.close()
        database = null
    }

    override fun isVaultOpen(): Boolean = database != null

    // Read path: acquire the mutex just long enough to grab a consistent reference,
    // then hand off to Room's own Flow. Full mutex-for-the-life-of-the-Flow isn't
    // worth the complexity here — this app has no sustained concurrent-access scenario,
    // only lifecycle transitions racing each other briefly.
    override fun getAllEntries(): Flow<List<VaultEntry>> = kotlinx.coroutines.flow.flow {
        val db = vaultMutex.withLock { database } ?: throw IllegalStateException("Vault is locked!")
        emitAll(db.vaultDao().getAllCredentials().map { list -> list.map { it.toDomain() } })
    }

    override suspend fun addEntry(entry: VaultEntry) = vaultMutex.withLock {
        val db = database ?: throw IllegalStateException("Vault is locked!")
        val passwordBytes = charArrayToUtf8Bytes(entry.passwordSecret)
        val entity = VaultEntity(
            id = entry.id, title = entry.title, username = entry.username, notes = entry.notes,
            passwordBytes = passwordBytes, createdAt = entry.createdAt, modifiedAt = entry.modifiedAt
        )
        db.vaultDao().insertCredential(entity)
        passwordBytes.fill(0)
    }

    override suspend fun deleteEntry(entryId: String) = vaultMutex.withLock {
        val db = database ?: throw IllegalStateException("Vault is locked!")
        db.vaultDao().deleteCredential(entryId)
    }
}
