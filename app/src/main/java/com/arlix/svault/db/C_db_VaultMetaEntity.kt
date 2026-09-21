package com.arlix.svault.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_meta")
data class C_db_VaultMetaEntity(
    @PrimaryKey
    @ColumnInfo(name = "meta_key")
    val v_db_metaKey: String,
    @ColumnInfo(name = "meta_value")
    val v_db_metaValue: String
)


object K_db_MetaKeys {
    const val COLD_KDF_ITERATIONS = "cold_kdf_iterations"
    const val COLD_SALT = "cold_salt"
    const val COLD_WRAPPED_DEK = "cold_wrapped_dek"
}