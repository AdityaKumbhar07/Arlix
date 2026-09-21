package com.arlix.svault.db


import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update


@Dao
interface I_db_VaultMetaDao {
    @Query("SELECT meta_value FROM vault_meta WHERE meta_key = :v_key LIMIT 1")
    suspend fun f_db_getMeta(v_key: String): String?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun f_db_insertMeta(v_meta: C_db_VaultMetaEntity)
    @Update
    suspend fun f_db_updateMeta(v_meta: C_db_VaultMetaEntity)
    @Transaction
    suspend fun f_db_setupColdVaultAtomic(
        v_salt: C_db_VaultMetaEntity,
        v_iterations: C_db_VaultMetaEntity,
        v_wrappedDek: C_db_VaultMetaEntity
    ) {
        f_db_insertMeta(v_salt)
        f_db_insertMeta(v_iterations)
        f_db_insertMeta(v_wrappedDek)
    }
}