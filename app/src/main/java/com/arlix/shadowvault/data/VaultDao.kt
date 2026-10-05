package com.arlix.shadowvault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    /**
     * Live list: Room re-emits after every change to the table.
     * NOCASE makes "amazon" sort next to "Amazon" (plain SQLite sorts all capitals first);
     * the id tie-break keeps the order stable for equal titles.
     */
    @Query("SELECT * FROM credentials ORDER BY title COLLATE NOCASE ASC, id ASC")
    fun getAllCredentials(): Flow<List<VaultEntity>>

    /** One-shot read, used for backup and for rebuilding the database on a passphrase change. */
    @Query("SELECT * FROM credentials ORDER BY title COLLATE NOCASE ASC, id ASC")
    suspend fun getAllOnce(): List<VaultEntity>

    /** Insert, or overwrite the row with the same id (this is how Edit saves). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: VaultEntity)

    @Query("DELETE FROM credentials WHERE id = :credentialId")
    suspend fun deleteCredential(credentialId: String)

    /** Not used by the app itself (kept for tests). Never call from production code. */
    @Query("DELETE FROM credentials")
    suspend fun wipeDatabase()
}
