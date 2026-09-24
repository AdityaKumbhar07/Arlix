package com.arlix.svault.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arlix.svault.domain.VaultEntry
import java.nio.ByteBuffer
import java.nio.CharBuffer

/**
 * The internal SQLite table definition for SQLCipher.
 *
 * HONEST NAMING NOTE:
 * The field is named `passwordBytes` (not `passwordEncrypted`) because at this phase,
 * the password is stored as raw UTF-8 bytes in a BLOB column. SQLCipher transparently
 * AES-256 encrypts every page of the database file — so the bytes are protected at the
 * storage level, but there is no second field-level AES-GCM pass on this column yet.
 * Field-level encryption is a planned Phase 4 milestone (see ICryptoProvider.encryptData).
 *
 * MEMORY NOTE:
 * Room maps this BLOB column to a ByteArray. We never create a String from it. The ByteArray
 * is converted back to a CharArray via NIO (ByteBuffer → CharBuffer) without touching
 * the JVM String pool. This closes the T11 (JVM String Trap) gap in the data layer.
 */
@Entity(tableName = "credentials")
data class VaultEntity(
    @PrimaryKey val id: String,
    val title: String,
    val username: String,
    val notes: String,
    // Stored as UTF-8 BLOB. Room maps ByteArray to SQLite BLOB natively — no TypeConverter needed.
    val passwordBytes: ByteArray,
    val createdAt: Long,
    val modifiedAt: Long
) {
    /**
     * Maps this DB row back to the pure Domain object.
     *
     * ByteArray → CharArray via NIO — no String allocation on the JVM heap.
     * ByteBuffer.wrap() references the existing array in-place; CharBuffer.decode() writes
     * directly into a CharArray without creating an intermediate String object.
     */
    fun toDomain(): VaultEntry {
        val charBuffer: CharBuffer = Charsets.UTF_8.decode(ByteBuffer.wrap(passwordBytes))
        // charBuffer.array() may be larger than the actual content; use limit() to be precise
        val passwordChars = CharArray(charBuffer.limit())
        charBuffer.get(passwordChars)
        return VaultEntry(
            id = id,
            title = title,
            username = username,
            notes = notes,
            passwordSecret = passwordChars,
            createdAt = createdAt,
            modifiedAt = modifiedAt
        )
    }

    // Required because ByteArray overrides equals/hashCode by identity, not content
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VaultEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
