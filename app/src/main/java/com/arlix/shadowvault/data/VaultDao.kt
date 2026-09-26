package com.arlix.shadowvault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    // Notice it returns a 'Flow'. This means the UI will automatically
    // update in real-time whenever a new password is added to the database.
    @Query("SELECT * FROM credentials ORDER BY title ASC")
    fun getAllCredentials(): Flow<List<VaultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: VaultEntity)

    @Query("DELETE FROM credentials WHERE id = :credentialId")
    suspend fun deleteCredential(credentialId: String)

    @Query("DELETE FROM credentials")
    suspend fun wipeDatabase()
}
