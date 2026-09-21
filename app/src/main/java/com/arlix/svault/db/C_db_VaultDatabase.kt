package com.arlix.svault.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        C_db_VaultEntity::class,
        C_db_VaultMetaEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class C_db_VaultDatabase : RoomDatabase() {
    abstract fun f_db_vaultDao(): I_db_VaultDao
    abstract fun f_db_vaultMetaDao(): I_db_VaultMetaDao

    companion object {
        @Volatile
        private var v_db_instance: C_db_VaultDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vault_meta` (" +
                    "`meta_key` TEXT NOT NULL, " +
                    "`meta_value` TEXT NOT NULL, " +
                    "PRIMARY KEY(`meta_key`))"
                )
                // Purge previous hacky sentinel row from vault_entries if it exists
                db.execSQL(
                    "DELETE FROM `vault_entries` WHERE `title` = '__SHADOWVAULT_COLD_SENTINEL__'"
                )
            }
        }

        fun f_db_getInstance(v_context: Context, v_passphrase: ByteArray): C_db_VaultDatabase {
            return v_db_instance ?: synchronized(this) {
                System.loadLibrary("sqlcipher")
                val v_factory = SupportOpenHelperFactory(v_passphrase)
                val v_instance = Room.databaseBuilder(
                    v_context.applicationContext,
                    C_db_VaultDatabase::class.java,
                    "shadowvault.db"
                )
                    .openHelperFactory(v_factory)
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                v_db_instance = v_instance
                v_instance
            }
        }

        fun f_db_closeDatabase() {
            v_db_instance?.close()
            v_db_instance = null
        }
    }
}