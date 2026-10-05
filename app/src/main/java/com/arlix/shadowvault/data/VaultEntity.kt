package com.arlix.shadowvault.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arlix.shadowvault.crypto.utf8BytesToChars
import com.arlix.shadowvault.domain.VaultEntry

/**
 * The SQLite table definition. SQLCipher encrypts every page of the file, so the BLOB is
 * protected on disk. In memory, [passwordBytes] lives only briefly: the repository wipes it
 * right after [toDomain] is called.
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
    fun toDomain(): VaultEntry = VaultEntry(
        id = id,
        title = title,
        username = username,
        notes = notes,
        passwordSecret = utf8BytesToChars(passwordBytes),
        category = category,
        createdAt = createdAt,
        modifiedAt = modifiedAt
    )

    // ByteArray uses identity equality, so compare by id only.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VaultEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
