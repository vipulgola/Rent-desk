package com.get.detail.rentmatrix.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "addresses")
data class Address(
    @PrimaryKey
    val dataUUID: String = UUID.randomUUID().toString(),
    val address: String
)