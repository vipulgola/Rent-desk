package com.get.detail.rentmatrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.get.detail.rentmatrix.data.local.entity.Address
import kotlinx.coroutines.flow.Flow

@Dao
interface AddressDao {
    @Query("SELECT * FROM addresses")
    fun getAllAddresses(): Flow<List<Address>>

    @Query("""
        SELECT a.dataUUID, a.address, COUNT(p.propertyId) as propertyCount 
        FROM addresses a 
        LEFT JOIN property_tenant_info p ON a.dataUUID = p.addressId 
        GROUP BY a.dataUUID
    """)
    fun getAddressesWithPropertyCount(): Flow<List<AddressWithPropertyCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddress(address: Address)
}

data class AddressWithPropertyCount(
    val dataUUID: String,
    val address: String,
    val propertyCount: Int
)