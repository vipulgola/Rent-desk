package com.get.detail.rentdesk.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.get.detail.rentdesk.utils.YearMonth

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
        Index(value = ["monthYear"])
    ]
)
data class RecordTransaction(
    @PrimaryKey
    val transactionId: String,
    val propertyId: String,
    val monthYear: YearMonth,
    val reading: Int,
    val balanceAmount: Int,
    val amountPaid: Int
)