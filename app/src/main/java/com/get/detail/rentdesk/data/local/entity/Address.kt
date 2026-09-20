package com.get.detail.rentdesk.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "addresses")
data class Address(
    @PrimaryKey
    val dataUUID: String = UUID.randomUUID().toString(),
    val address: String,
    @ColumnInfo(defaultValue = "0")
    val createdAtUtc: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val modifiedAtUtc: Long = createdAtUtc
)
