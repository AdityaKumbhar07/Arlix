package com.arlix.svault.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow


@Dao
interface I_db_VaultDao {

    @Query("SELECT * FROM vault_entries WHERE chamber = :v_chamber AND section = :v_section AND is_deleted = 0 ORDER BY created_at DESC")
    fun f_db_getEntriesByChamberAndSection(
        v_chamber: String,
        v_section: String
    ): Flow<List<C_db_VaultEntity>>

    @Query("SELECT * FROM vault_entries WHERE chamber = :v_chamber AND is_deleted = 0 ORDER BY created_at DESC")
    fun f_db_getEntriesByChamber(v_chamber: String): Flow<List<C_db_VaultEntity>>

    @Query("SELECT * FROM vault_entries WHERE is_deleted = 0 ORDER BY created_at DESC")
    fun f_db_getAllActiveEntries(): Flow<List<C_db_VaultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun f_db_insertEntry(v_entry: C_db_VaultEntity)

    @Update
    suspend fun f_db_updateEntry(v_entry: C_db_VaultEntity)

    @Query("UPDATE vault_entries SET is_deleted = 1, updated_at = :v_timestamp WHERE id = :v_id")
    suspend fun f_db_softDeleteEntry(
        v_id: String,
        v_timestamp: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun f_db_hardDeleteEntry(v_entry: C_db_VaultEntity)
}
