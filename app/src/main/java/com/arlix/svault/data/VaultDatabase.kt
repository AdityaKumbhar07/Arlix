package com.arlix.svault.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Schema version history:
 * v1 — Initial schema. passwordEncrypted stored as TEXT (String) — T11 violation.
 * v2 — passwordEncrypted renamed to passwordBytes, type changed TEXT→BLOB (ByteArray).
 *      Uses fallbackToDestructiveMigration() since no production data exists yet.
 *      This closes the T11/T19 gap in the data layer.
 */
@Database(entities = [VaultEntity::class], version = 2, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
}
