package com.arlix.shadowvault.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arlix.shadowvault.domain.VaultEntry
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/**
 * The SQLite table definition. SQLCipher encrypts every page of the file, so the BLOB is
 * protected on disk. In memory, the password lives in [passwordBytes] only briefly:
 * the repository wipes it right after [toDomain] is called.
 */
@Entity(tableName = "credentials")
data class VaultEntity(
    @PrimaryKey val id: String,
    val title: String,
    val username: String,
    val notes: String,
    val category: String,
    val passwordBytes: ByteArray,
    val createdAt: Long,
    val modifiedAt: Long
) {
    /** Converts this row to the domain object without creating a String for the password. */
    fun toDomain(): VaultEntry {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        // UTF-8 never produces more chars than bytes, so this is always big enough.
        val scratch = CharBuffer.allocate(passwordBytes.size)
        val passwordChars: CharArray
        try {
            decoder.decode(ByteBuffer.wrap(passwordBytes), scratch, true)
            decoder.flush(scratch)
            scratch.flip()
            passwordChars = CharArray(scratch.remaining())
            scratch.get(passwordChars)
        } finally {
            // The scratch buffer also holds the password; zero it.
            java.util.Arrays.fill(scratch.array(), '\u0000')
        }
        return VaultEntry(
            id = id,
            title = title,
            username = username,
            notes = notes,
            passwordSecret = passwordChars,
            category = category,
            createdAt = createdAt,
            modifiedAt = modifiedAt
        )
    }

    // ByteArray uses identity equality, so compare by id only.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VaultEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
