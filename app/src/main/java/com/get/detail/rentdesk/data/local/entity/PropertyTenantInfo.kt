package com.get.detail.rentdesk.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.get.detail.rentdesk.domain.model.TenantInfo

@Entity(tableName = "property_tenant_info")
data class PropertyTenantInfo(
    @PrimaryKey
    val propertyId: String,
    val entityName: String,
    val addressId: String? = null,
    val tenantInfo: TenantInfo? = null,
    @ColumnInfo(defaultValue = "0")
    val monthlyRent: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val electricityPricePerUnit: Double = 0.0,
    @ColumnInfo(defaultValue = "0")
    val meterReading: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val balanceAmount: Double = 0.0,
    @ColumnInfo(defaultValue = "0")
    val createdAtUtc: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val modifiedAtUtc: Long = createdAtUtc
)
