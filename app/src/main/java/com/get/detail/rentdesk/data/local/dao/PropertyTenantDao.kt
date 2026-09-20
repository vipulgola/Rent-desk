package com.get.detail.rentdesk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface PropertyTenantDao {
    @Query("SELECT * FROM property_tenant_info")
    fun getAllProperties(): Flow<List<PropertyTenantInfo>>

    @Query("SELECT * FROM property_tenant_info WHERE addressId = :addressId")
    fun getPropertiesByAddress(addressId: String): Flow<List<PropertyTenantInfo>>

    @Query("SELECT * FROM property_tenant_info WHERE propertyId = :propertyId")
    suspend fun getPropertyById(propertyId: String): PropertyTenantInfo?

    @Query("SELECT * FROM property_tenant_info")
    suspend fun getAllPropertiesList(): List<PropertyTenantInfo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProperty(property: PropertyTenantInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProperties(properties: List<PropertyTenantInfo>)

    @Update
    suspend fun updateProperty(property: PropertyTenantInfo)

    @androidx.room.Delete
    suspend fun deleteProperty(property: PropertyTenantInfo)

    @androidx.room.Delete
    suspend fun deleteProperties(properties: List<PropertyTenantInfo>)

    @Query("DELETE FROM property_tenant_info")
    suspend fun deleteAllProperties()
}
