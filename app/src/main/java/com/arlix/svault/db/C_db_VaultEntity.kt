package com.arlix.svault.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "vault_entries")
data class C_db_VaultEntity(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val v_db_id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "title")
    val v_db_title: String,

    @ColumnInfo(name = "account")
    val v_db_account: String,

    @ColumnInfo(name = "secret")
    val v_db_secret: String,

    @ColumnInfo(name = "chamber")
    val v_db_chamber: String, // "HOT" or "COLD"

    @ColumnInfo(name = "section")
    val v_db_section: String, // "GENERAL", "PERSONAL", or "FINANCE"

    @ColumnInfo(name = "created_at")
    val v_db_createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val v_db_updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_deleted")
    val v_db_isDeleted: Boolean = false
)
