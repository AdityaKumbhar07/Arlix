package com.arlix.svault.data

import android.content.Context
import androidx.room.Room
import com.arlix.svault.crypto.charArrayToUtf8Bytes
import com.arlix.svault.domain.IVaultRepository
import com.arlix.svault.domain.VaultEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class VaultRepositoryImpl(private val context: Context) : IVaultRepository {

    private var database: VaultDatabase? = null

    /**
     * Returns true if the encrypted database file for this vault already physically exists on disk.
     *
     * WHY THIS MATTERS (Bug 1 root cause):
     * SQLCipher/Room silently CREATE a brand-new empty database if the file doesn't exist yet —
     * there is no error, no exception, and no distinction between "first launch" and "unlock".
     * Any password typed on first launch would silently become the vault's password.
     * This check is the ONLY correct way to split the "Create Passphrase" flow from "Unlock".
     */
    override fun vaultExists(isColdVault: Boolean): Boolean {
        val dbName = if (isColdVault) "vault_secondary.db" else "vault_primary.db"
        return context.getDatabasePath(dbName).exists()
    }

    override suspend fun openVault(masterKey: ByteArray, isColdVault: Boolean): Boolean {
        // If a vault is already open, close it first to prevent DB handle leaks
        closeVault()

        // Load the SQLCipher native library before Room opens the database.
        // System.loadLibrary("sqlcipher") is the correct bootstrap for net.zetetic v4.6.1:
        // the library is packaged as libsqlcipher.so and must be loaded before any
        // SupportOpenHelperFactory call. This is what the official net.zetetic samples use.
        //
        // NOTE: hasCodec() was tried but it calls nativeHasCodec() via JNI — which itself
        // requires the .so to be loaded first. Calling it before loadLibrary causes
        // UnsatisfiedLinkError (confirmed from device crash log). Removed.
        System.loadLibrary("sqlcipher")

        val dbName = if (isColdVault) "vault_secondary.db" else "vault_primary.db"

        // Pass the Argon2id-derived key directly into SQLCipher's C++ engine.
        // clearPassphrase = true: SQLCipher zeroes the key bytes from its C++ memory after use.
        val factory = SupportOpenHelperFactory(masterKey, null, true)

        database = Room.databaseBuilder(context, VaultDatabase::class.java, dbName)
            .openHelperFactory(factory)
            // Schema v1→v2: passwordEncrypted TEXT → passwordBytes BLOB.
            // No production data exists yet — destructive migration is the correct choice.
            // Remove this when the app has real user data and write a proper Migration instead.
            .fallbackToDestructiveMigration(true)
            .build()

        // Force SQLCipher to open (and validate the key) synchronously right now.
        // If the password is wrong, this throws SQLiteException — which propagates up to
        // VaultViewModel.unlock()'s catch block and sets the Error state. This is correct.
        database?.openHelper?.writableDatabase

        // The return value 'true' means "we got here without an exception". The real success
        // signal is "didn't throw" — the boolean is kept for interface compatibility.
        return true
    }

    override suspend fun closeVault() {
        database?.close()
        database = null
    }

    override fun isVaultOpen(): Boolean = database != null

    override fun getAllEntries(): Flow<List<VaultEntry>> {
        val db = database ?: throw IllegalStateException("Vault is locked!")
        return db.vaultDao().getAllCredentials().map { list ->
            list.map { entity -> entity.toDomain() }
            // toDomain() uses NIO ByteBuffer.decode() to convert passwordBytes → CharArray
            // WITHOUT allocating a String on the JVM heap. T11 is closed here.
        }
    }

    override suspend fun addEntry(entry: VaultEntry) {
        val db = database ?: throw IllegalStateException("Vault is locked!")

        // Convert CharArray → UTF-8 ByteArray using NIO — zero String allocation (T11).
        // charArrayToUtf8Bytes() is defined in ShadowCryptoProvider.kt and uses
        // Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(chars)) — no String() constructor call.
        val passwordBytes = charArrayToUtf8Bytes(entry.passwordSecret)

        val entity = VaultEntity(
            id = entry.id,
            title = entry.title,
            username = entry.username,
            notes = entry.notes,
            passwordBytes = passwordBytes,  // Stored as BLOB in SQLCipher-encrypted page
            createdAt = entry.createdAt,
            modifiedAt = entry.modifiedAt
        )
        db.vaultDao().insertCredential(entity)

        // Wipe the temporary byte array now that Room has consumed it.
        // The original CharArray (entry.passwordSecret) is the domain's property to wipe —
        // callers should call entry.annihilate() after this returns if they're done with it.
        passwordBytes.fill(0)
    }

    override suspend fun deleteEntry(entryId: String) {
        val db = database ?: throw IllegalStateException("Vault is locked!")
        db.vaultDao().deleteCredential(entryId)
    }
}
