package com.arlix.shadowvault.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Current schema: version 3. Release builds have no destructive fallback, so any schema
 * change needs a real Migration before shipping.
 */
@Database(entities = [VaultEntity::class], version = 3, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
}
