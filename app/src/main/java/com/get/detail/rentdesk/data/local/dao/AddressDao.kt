package com.get.detail.rentdesk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.get.detail.rentdesk.data.local.entity.Address
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

    @Query("SELECT * FROM addresses")
    suspend fun getAllAddressesList(): List<Address>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddresses(addresses: List<Address>)

    @Query("DELETE FROM addresses")
    suspend fun deleteAllAddresses()
}

data class AddressWithPropertyCount(
    val dataUUID: String,
    val address: String,
    val propertyCount: Int
)
