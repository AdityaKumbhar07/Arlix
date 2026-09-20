package com.arlix.svault.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [C_db_VaultEntity::class],
    version = 1,
    exportSchema = false
)
abstract class C_db_VaultDatabase : RoomDatabase() {
    abstract  fun f_db_vaultDao(): I_db_VaultDao

    companion object {
        @Volatile
        private var v_db_instance: C_db_VaultDatabase? = null

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