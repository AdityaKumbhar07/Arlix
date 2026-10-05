package com.arlix.shadowvault.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room schema, version 3.
 *
 * Rules that keep existing vaults safe:
 *  - Never bump [version] without a written Migration. There is deliberately NO destructive
 *    fallback in any build type, because a debug build with real data would lose the vault.
 *  - Schemas are exported to app/schemas/ (commit them to git) so migrations can be written
 *    and tested against the real previous schema.
 */
@Database(entities = [VaultEntity::class], version = 3, exportSchema = true)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
}
