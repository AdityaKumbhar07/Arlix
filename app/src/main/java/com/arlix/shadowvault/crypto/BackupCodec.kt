package com.arlix.shadowvault.crypto

import com.arlix.shadowvault.domain.ICryptoProvider
import com.arlix.shadowvault.domain.VaultEntry
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The file is not a (supported, intact) Arlix backup. */
class BackupFormatException(message: String) : Exception(message)

/** Authentication failed: wrong backup passphrase, or the file was damaged or altered. */
class BackupPassphraseException : Exception("Wrong passphrase or damaged backup")

/** The backup was made from the other kind of vault (hot vs cold) than the one it is restored into. */
class BackupKindMismatchException(val backupIsCold: Boolean) : Exception(
    if (backupIsCold) "This backup belongs to the cold vault. Open the cold vault to restore it."
    else "This backup belongs to the hot vault. Open the hot vault to restore it."
)

/**
 * Encrypted backup file. Independent of Room and SQLCipher, so it stays readable whatever
 * happens to the database libraries. The file name/extension is irrelevant: a file is
 * recognised by its header.
 *
 * Layout, version 2 (written by this app):
 *   MAGIC(6) | version(1) = 2 | vault kind(1) | salt(16) | nonce(12) | AES-256-GCM(ciphertext + tag)
 * Version 1 (older files) has no vault-kind byte and is still readable:
 *   MAGIC(6) | version(1) = 1 | salt(16) | nonce(12) | AES-256-GCM(ciphertext + tag)
 *
 * The whole header, including the vault kind, is authenticated (GCM additional data), so any
 * change to the file makes decryption fail instead of returning wrong data.
 *
 * The key is Argon2id(backup passphrase, fresh random salt) with the same frozen cost
 * parameters as the vaults, so a new salt per backup means a nonce is never reused with a key.
 *
 * Vault kind: a backup is labelled hot or cold and may only be restored into the same kind,
 * so the two vaults can never be mixed by restoring. The label lives in the header, not in
 * the file name, because the file name ends up in the cloud.
 *
 * Plaintext layout (both versions): count(int), then per entry: id, title, username, notes,
 * category, password (each as length(int) + UTF-8 bytes), createdAt(long), modifiedAt(long).
 * Any change to this layout needs a new version.
 */
class BackupCodec(private val crypto: ICryptoProvider) {

    private companion object {
        val MAGIC = "ARLXBK".toByteArray(Charsets.US_ASCII)
        const val VERSION_1 = 1
        const val VERSION_2 = 2
        const val KIND_HOT: Byte = 1
        const val KIND_COLD: Byte = 2
        const val SALT_SIZE = 16
        const val NONCE_SIZE = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
        val V1_HEADER = MAGIC.size + 1 + SALT_SIZE + NONCE_SIZE
        val V2_HEADER = V1_HEADER + 1
        const val MAX_ENTRIES = 100_000
        const val CORRUPT = "The backup is corrupt."
    }

    /** What the unauthenticated-looking header says; the kind is verified by the GCM tag on decrypt. */
    private class Header(val isCold: Boolean?, val size: Int)

    private fun hasMagic(data: ByteArray): Boolean =
        data.size > MAGIC.size && MAGIC.indices.all { data[it] == MAGIC[it] }

    private fun parseHeader(data: ByteArray): Header {
        if (!hasMagic(data)) throw BackupFormatException("That file is not an Arlix backup.")
        val version = data[MAGIC.size].toInt() and 0xFF
        val header = when (version) {
            VERSION_1 -> Header(isCold = null, size = V1_HEADER)
            VERSION_2 -> {
                if (data.size < V2_HEADER) throw BackupFormatException(CORRUPT)
                val isCold = when (data[MAGIC.size + 1]) {
                    KIND_HOT -> false
                    KIND_COLD -> true
                    else -> throw BackupFormatException(CORRUPT)
                }
                Header(isCold = isCold, size = V2_HEADER)
            }
            else -> throw BackupFormatException("This backup was made by a different app version ($version).")
        }
        if (data.size < header.size + TAG_BYTES) throw BackupFormatException(CORRUPT)
        return header
    }

    /** Cheap check on the header only; real validation happens in [decrypt]. */
    fun looksLikeBackup(data: ByteArray): Boolean =
        data.size >= V1_HEADER + TAG_BYTES && hasMagic(data)

    /**
     * Which vault this backup came from: true = cold, false = hot, null = an older (version 1)
     * file without a label, or an unreadable header.
     */
    fun backupIsCold(data: ByteArray): Boolean? =
        try {
            parseHeader(data).isCold
        } catch (e: BackupFormatException) {
            null
        }

    /** Encrypts [entries] from the hot or cold vault. The caller wipes [passphrase] and the entries afterwards. */
    suspend fun encrypt(entries: List<VaultEntry>, passphrase: CharArray, isColdVault: Boolean): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_SIZE).also { random.nextBytes(it) }
        val header = ByteBuffer.allocate(V2_HEADER)
            .put(MAGIC)
            .put(VERSION_2.toByte())
            .put(if (isColdVault) KIND_COLD else KIND_HOT)
            .put(salt)
            .put(nonce)
            .array()

        val key = crypto.deriveMasterKey(passphrase, salt)
        var plain: ByteArray? = null
        try {
            plain = serialize(entries)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            return header + cipher.doFinal(plain)
        } finally {
            crypto.wipe(key)
            plain?.let { crypto.wipe(it) }
        }
    }

    /**
     * Decrypts a backup that is being restored into the hot ([expectedIsCold] = false) or cold
     * vault. The caller owns the returned entries and must [VaultEntry.annihilate] them.
     *
     * @throws BackupFormatException not an Arlix backup, or a different file version
     * @throws BackupKindMismatchException the backup belongs to the other kind of vault
     * @throws BackupPassphraseException wrong passphrase, or the file is damaged or altered
     */
    suspend fun decrypt(data: ByteArray, passphrase: CharArray, expectedIsCold: Boolean): List<VaultEntry> {
        val header = parseHeader(data)
        val kind = header.isCold
        // Checked before the expensive key derivation. The kind byte is part of the GCM
        // additional data, so a changed byte can only ever make decryption fail below.
        if (kind != null && kind != expectedIsCold) throw BackupKindMismatchException(kind)

        val saltStart = header.size - SALT_SIZE - NONCE_SIZE
        val salt = data.copyOfRange(saltStart, saltStart + SALT_SIZE)
        val nonce = data.copyOfRange(saltStart + SALT_SIZE, header.size)
        val aad = data.copyOfRange(0, header.size)

        val key = crypto.deriveMasterKey(passphrase, salt)
        var plain: ByteArray? = null
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(aad)
            plain = try {
                cipher.doFinal(data, header.size, data.size - header.size)
            } catch (e: GeneralSecurityException) {
                throw BackupPassphraseException()
            }
            return deserialize(plain)
        } finally {
            crypto.wipe(key)
            plain?.let { crypto.wipe(it) }
        }
    }

    // ---- plaintext layout ----

    private fun serialize(entries: List<VaultEntry>): ByteArray {
        val passwords = ArrayList<ByteArray>(entries.size)
        try {
            val rows = entries.map { e ->
                val pw = charArrayToUtf8Bytes(e.passwordSecret)
                passwords += pw
                listOf(e.id.toByteArray(), e.title.toByteArray(), e.username.toByteArray(),
                    e.notes.toByteArray(), e.category.toByteArray(), pw)
            }
            // Exact size up front, so no growing buffer leaves extra copies of the secrets behind.
            var size = 4
            rows.forEach { blocks -> size += 16; blocks.forEach { size += 4 + it.size } }
            val buf = ByteBuffer.allocate(size)
            buf.putInt(rows.size)
            rows.forEachIndexed { i, blocks ->
                blocks.forEach { buf.putInt(it.size); buf.put(it) }
                buf.putLong(entries[i].createdAt)
                buf.putLong(entries[i].modifiedAt)
            }
            return buf.array()
        } finally {
            passwords.forEach { it.fill(0) }
        }
    }

    private fun deserialize(plain: ByteArray): List<VaultEntry> {
        val buf = ByteBuffer.wrap(plain)
        val out = ArrayList<VaultEntry>()
        var ok = false
        try {
            val count = buf.getInt()
            if (count !in 0..MAX_ENTRIES) throw BackupFormatException(CORRUPT)
            repeat(count) {
                val id = buf.readString()
                val title = buf.readString()
                val username = buf.readString()
                val notes = buf.readString()
                val category = buf.readString()
                val pwBytes = buf.readBlock()
                val password = try { utf8BytesToChars(pwBytes) } finally { pwBytes.fill(0) }
                out += VaultEntry(
                    id = id, title = title, username = username, notes = notes,
                    passwordSecret = password, category = category,
                    createdAt = buf.getLong(), modifiedAt = buf.getLong()
                )
            }
            if (buf.hasRemaining()) throw BackupFormatException(CORRUPT)
            ok = true
            return out
        } catch (e: BufferUnderflowException) {
            throw BackupFormatException(CORRUPT)
        } finally {
            if (!ok) out.forEach { it.annihilate() }
        }
    }

    private fun ByteBuffer.readBlock(): ByteArray {
        val n = getInt()
        if (n < 0 || n > remaining()) throw BackupFormatException(CORRUPT)
        return ByteArray(n).also { get(it) }
    }

    private fun ByteBuffer.readString(): String = String(readBlock(), Charsets.UTF_8)
}
