package com.arlix.svault.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arlix.svault.domain.VaultEntry

/**
 * The internal SQLite table definition for SQLCipher.
 */
@Entity(tableName = "credentials")
data class VaultEntity(
    @PrimaryKey val id: String,
    val title: String,
    val username: String,
    val notes: String,
    // Room will encrypt this column via SQLCipher automatically
    val passwordEncrypted: String,
    val createdAt: Long,
    val modifiedAt: Long
) {
    // Helper to map our DB row back into our pure Domain object
    fun toDomain(decryptedPassword: CharArray): VaultEntry {
        return VaultEntry(
            id = id,
            title = title,
            username = username,
            notes = notes,
            passwordSecret = decryptedPassword,
            createdAt = createdAt,
            modifiedAt = modifiedAt
        )
    }
}
