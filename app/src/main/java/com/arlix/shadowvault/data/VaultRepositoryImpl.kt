package com.arlix.shadowvault.data

import android.content.Context
import android.system.Os
import androidx.room.Room
import androidx.room.withTransaction
import com.arlix.shadowvault.crypto.MemorySanitizer
import com.arlix.shadowvault.crypto.charArrayToUtf8Bytes
import com.arlix.shadowvault.domain.IVaultRepository
import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.domain.WrongPassphraseException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.MessageDigest

class VaultRepositoryImpl(private val context: Context) : IVaultRepository {

    private companion object {
        const val DB_HOT = "vault_primary.db"
        const val DB_COLD = "vault_secondary.db"
    }

    // All state below is only changed while holding [vaultMutex].
    @Volatile private var database: VaultDatabase? = null
    @Volatile private var openDbName: String? = null
    /**
     * The derived key of the open vault. Room's connection pool needs it to open further
     * connections, so it must stay alive until lock. It is zeroed in [closeVaultLocked].
     */
    @Volatile private var activeKey: ByteArray? = null
    private val vaultMutex = Mutex()

    private fun dbNameFor(isCold: Boolean) = if (isCold) DB_COLD else DB_HOT

    override fun vaultExists(isColdVault: Boolean): Boolean =
        context.getDatabasePath(dbNameFor(isColdVault)).exists()

    /** Builds (but does not yet open) an encrypted Room database. [key] must stay alive while it is open. */
    private fun buildDatabase(name: String, key: ByteArray): VaultDatabase {
        System.loadLibrary("sqlcipher")
        // Third argument false: the library must NOT zero [key] after its first connection.
        // Room 2.6+ opens more connections on demand, and a zeroed key would crash them.
        // We zero the key ourselves when the vault is closed.
        val factory = SupportOpenHelperFactory(key, null, false)
        return Room.databaseBuilder(context, VaultDatabase::class.java, name)
            .openHelperFactory(factory)
            // No destructive fallback in any build: a schema change needs a real Migration.
            .build()
    }

    override suspend fun openVault(masterKey: ByteArray, isColdVault: Boolean): Boolean =
        vaultMutex.withLock {
            val dbName = dbNameFor(isColdVault)
            // Re-opening the SAME vault: close the old connection first.
            // Opening the OTHER vault: leave the current one open until the new key is proven
            // correct, so a wrong cold passphrase never closes the hot vault.
            if (openDbName == dbName) closeVaultLocked()

            val keyCopy = masterKey.clone()
            val db = buildDatabase(dbName, keyCopy)
            try {
                db.openHelper.writableDatabase // forces the open; throws on a wrong key
            } catch (t: Throwable) {
                runCatching { db.close() }
                MemorySanitizer.wipe(keyCopy)
                throw t // the previously open vault is untouched
            }

            // Key proven correct: swap, then close the previous vault.
            val oldDb = database
            val oldKey = activeKey
            database = db
            activeKey = keyCopy
            openDbName = dbName
            runCatching { oldDb?.close() }
            oldKey?.let { MemorySanitizer.wipe(it) }
            true
        }

    override suspend fun closeVault() = vaultMutex.withLock { closeVaultLocked() }

    /**
     * Must never fail to zero the key: even if closing the database throws, the key is wiped
     * in the finally block and the repository state is already reset.
     */
    private fun closeVaultLocked() {
        val db = database
        val key = activeKey
        database = null
        openDbName = null
        activeKey = null
        try {
            db?.close()
        } catch (e: Exception) {
            // Nothing more can be done; the state is already reset and the key is wiped below.
        } finally {
            key?.let { MemorySanitizer.wipe(it) }
        }
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

    override suspend fun readAllEntries(): List<VaultEntry> = vaultMutex.withLock {
        val db = database ?: throw IllegalStateException("Vault is locked!")
        db.vaultDao().getAllOnce().map { entity ->
            try {
                entity.toDomain()
            } finally {
                entity.passwordBytes.fill(0)
            }
        }
    }

    override suspend fun addEntry(entry: VaultEntry) = vaultMutex.withLock {
        val db = database ?: throw IllegalStateException("Vault is locked!")
        val passwordBytes = charArrayToUtf8Bytes(entry.passwordSecret)
        try {
            db.vaultDao().insertCredential(entry.toEntity(passwordBytes))
        } finally {
            passwordBytes.fill(0) // wiped even if the insert fails
        }
    }

    override suspend fun addEntries(entries: List<VaultEntry>) = vaultMutex.withLock {
        val db = database ?: throw IllegalStateException("Vault is locked!")
        val buffers = ArrayList<ByteArray>(entries.size)
        try {
            val rows = entries.map { entry ->
                val bytes = charArrayToUtf8Bytes(entry.passwordSecret)
                buffers += bytes
                entry.toEntity(bytes)
            }
            db.withTransaction { rows.forEach { db.vaultDao().insertCredential(it) } }
        } finally {
            buffers.forEach { it.fill(0) }
        }
    }

    override suspend fun deleteEntry(entryId: String) = vaultMutex.withLock {
        val db = database ?: throw IllegalStateException("Vault is locked!")
        db.vaultDao().deleteCredential(entryId)
    }

    override suspend fun changePassphrase(
        oldKey: ByteArray,
        newKey: ByteArray,
        isColdVault: Boolean
    ) {
        vaultMutex.withLock {
            val dbName = dbNameFor(isColdVault)
            val live = database
            val liveKey = activeKey
            if (live == null || liveKey == null || openDbName != dbName) {
                throw IllegalStateException("Vault is not open.")
            }
            if (!MessageDigest.isEqual(oldKey, liveKey)) throw WrongPassphraseException()

            val tmpName = "$dbName.rekey"
            val liveFile = context.getDatabasePath(dbName)
            val tmpFile = context.getDatabasePath(tmpName)
            val tmpKey = newKey.clone()
            var rows: List<VaultEntity> = emptyList()
            var copy: List<VaultEntity> = emptyList()
            var tmpDb: VaultDatabase? = null
            var committed = false
            try {
                context.deleteDatabase(tmpName) // leftover from an interrupted attempt
                rows = live.vaultDao().getAllOnce()

                // 1. Build a complete copy under the new key, in a separate file.
                val t = buildDatabase(tmpName, tmpKey)
                tmpDb = t
                t.openHelper.writableDatabase
                t.withTransaction { rows.forEach { t.vaultDao().insertCredential(it) } }

                // 2. Read it back and compare every field and every password byte.
                copy = t.vaultDao().getAllOnce()
                check(sameRows(rows, copy)) { "Rebuilt vault failed verification." }

                // 3. Close it so its WAL is merged into the main file, and make sure it was.
                t.close()
                tmpDb = null
                check(!hasData(File(tmpFile.path + "-wal"))) { "Rebuilt vault was not fully written." }

                // 4. Commit: close the live vault and atomically replace its file. Not
                //    cancellable, so it cannot be interrupted halfway.
                withContext(NonCancellable) {
                    closeVaultLocked()
                    // A stale WAL or journal next to a replaced file would corrupt it, so we
                    // refuse to swap if one exists. The old vault stays intact.
                    check(!hasData(File(liveFile.path + "-wal")) && !File(liveFile.path + "-journal").exists()) {
                        "Old vault has unmerged data; not replacing it."
                    }
                    File(liveFile.path + "-shm").delete()
                    Os.rename(tmpFile.path, liveFile.path) // atomic replace on the same filesystem
                    committed = true
                }
            } finally {
                runCatching { tmpDb?.close() }
                MemorySanitizer.wipe(tmpKey)
                (rows + copy).forEach { it.passwordBytes.fill(0) }
                if (!committed) context.deleteDatabase(tmpName)
            }
        }
    }

    private fun hasData(file: File): Boolean = file.exists() && file.length() > 0

    private fun sameRows(a: List<VaultEntity>, b: List<VaultEntity>): Boolean {
        if (a.size != b.size) return false
        val byId = b.associateBy { it.id }
        return a.all { x ->
            val y = byId[x.id]
            y != null &&
                x.title == y.title && x.username == y.username && x.notes == y.notes &&
                x.category == y.category && x.createdAt == y.createdAt &&
                x.modifiedAt == y.modifiedAt && x.passwordBytes.contentEquals(y.passwordBytes)
        }
    }

    private fun VaultEntry.toEntity(passwordBytes: ByteArray) = VaultEntity(
        id = id, title = title, username = username, notes = notes, category = category,
        passwordBytes = passwordBytes, createdAt = createdAt, modifiedAt = modifiedAt
    )
}
