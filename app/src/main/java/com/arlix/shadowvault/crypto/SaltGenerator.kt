package com.arlix.shadowvault.crypto

import android.content.Context
import com.arlix.shadowvault.domain.VaultFileException
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

/**
 * Per-vault Argon2 salt, stored in a plain file next to the database (a salt is not secret).
 */
object SaltGenerator {
    private const val SALT_SIZE = 16

    /**
     * Returns the salt for this vault, creating it only when no vault exists yet.
     *
     * A salt is NEVER regenerated for an existing vault: a new salt would silently make the
     * vault unopenable. A missing or damaged salt file next to an existing database is
     * reported as a [VaultFileException] instead.
     */
    @Synchronized
    fun getSalt(context: Context, isColdVault: Boolean = false): ByteArray {
        val saltName = if (isColdVault) "vault_secondary.salt" else "vault_primary.salt"
        val dbName = if (isColdVault) "vault_secondary.db" else "vault_primary.db"
        val saltFile = context.getDatabasePath(saltName)
        val dbExists = context.getDatabasePath(dbName).exists()

        if (saltFile.exists()) {
            val existing = saltFile.readBytes()
            if (existing.size == SALT_SIZE) return existing
            if (dbExists) throw VaultFileException("Salt file is damaged; this vault cannot be opened.")
            // No vault yet, so a bad leftover file is safe to replace.
        } else if (dbExists) {
            throw VaultFileException("Salt file is missing; this vault cannot be opened.")
        }

        saltFile.parentFile?.mkdirs()
        val newSalt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }

        // Write to a temp file, force it to disk, then rename. The rename is atomic, so a
        // crash can never leave a half-written salt.
        val tmp = File(saltFile.parentFile, "$saltName.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(newSalt)
            out.fd.sync()
        }
        Files.move(tmp.toPath(), saltFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return newSalt
    }
}
