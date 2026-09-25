package com.get.detail.rentdesk.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "record_transaction",
    foreignKeys = [
        ForeignKey(
            entity = PropertyTenantInfo::class,
            parentColumns = ["propertyId"],
            childColumns = ["propertyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["propertyId"]),
        Index(value = ["paymentDateUtc"])
    ]
)
data class RecordTransaction(
    @PrimaryKey
    val transactionId: String,
    val propertyId: String,
    @ColumnInfo(defaultValue = "0")
    val paymentDateUtc: Long,
    val reading: Int,
    val amountPaid: Double,
    val previousBalance: Double? = null,
    val previousReading: Int? = null,
    val rentCharged: Double? = null,
    val electricityRateCharged: Double? = null,
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val createdAtUtc: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val modifiedAtUtc: Long = createdAtUtc
)
