package com.get.detail.rentdesk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.get.detail.rentdesk.domain.model.TenantInfo

@Entity(tableName = "property_tenant_info")
data class PropertyTenantInfo(
    @PrimaryKey
    val propertyId: String,
    val entityName: String,
    val addressId: String? = null,
    val tenantInfo: TenantInfo? = null
)
