package com.get.detail.rentmatrix.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.get.detail.rentmatrix.domain.model.TenantInfo
import com.get.detail.rentmatrix.utils.YearMonth

@Entity(tableName = "property_tenant_info")
data class PropertyTenantInfo(
    @PrimaryKey
    val propertyId: String,
    val entityName: String,
    val addressId: String? = null,
    val tenantInfo: TenantInfo? = null,
    val startMonthYear: YearMonth? = null
)