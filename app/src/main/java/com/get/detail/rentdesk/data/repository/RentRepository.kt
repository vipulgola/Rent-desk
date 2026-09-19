package com.get.detail.rentdesk.data.repository

import com.get.detail.rentdesk.data.local.dao.AddressDao
import com.get.detail.rentdesk.data.local.dao.AddressWithPropertyCount
import com.get.detail.rentdesk.data.local.dao.PropertyTenantDao
import com.get.detail.rentdesk.data.local.dao.TransactionDao
import com.get.detail.rentdesk.data.local.entity.Address
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import kotlinx.coroutines.flow.Flow

class RentRepository(
    private val addressDao: AddressDao,
    private val propertyTenantDao: PropertyTenantDao,
    private val transactionDao: TransactionDao
) {
    val allAddresses: Flow<List<Address>> = addressDao.getAllAddresses()
    val addressesWithCount: Flow<List<AddressWithPropertyCount>> = addressDao.getAddressesWithPropertyCount()
    val allProperties: Flow<List<PropertyTenantInfo>> = propertyTenantDao.getAllProperties()
    val allTransactions: Flow<List<RecordTransaction>> = transactionDao.getAllTransactionsFlow()

    suspend fun getAllPropertiesList(): List<PropertyTenantInfo> {
        return propertyTenantDao.getAllPropertiesList()
    }

    suspend fun getAllTransactions(): List<RecordTransaction> {
        return transactionDao.getAllTransactions()
    }

    suspend fun getPropertyById(propertyId: String): PropertyTenantInfo? {
        return propertyTenantDao.getPropertyById(propertyId)
    }

    suspend fun insertProperty(property: PropertyTenantInfo) {
        propertyTenantDao.insertProperty(property)
    }

    suspend fun updateProperty(property: PropertyTenantInfo) {
        propertyTenantDao.updateProperty(property)
    }

    suspend fun deleteProperty(property: PropertyTenantInfo) {
        propertyTenantDao.deleteProperty(property)
    }

    suspend fun deleteProperties(properties: List<PropertyTenantInfo>) {
        propertyTenantDao.deleteProperties(properties)
    }

    suspend fun insertAddress(address: Address) {
        addressDao.insertAddress(address)
    }

    fun getPropertiesByAddress(addressId: String): Flow<List<PropertyTenantInfo>> {
        return propertyTenantDao.getPropertiesByAddress(addressId)
    }

    fun getTransactionsForProperty(propertyId: String): Flow<List<RecordTransaction>> {
        return transactionDao.getTransactionsForProperty(propertyId)
    }

    suspend fun insertTransaction(transaction: RecordTransaction) {
        transactionDao.insertTransaction(transaction)
    }

    suspend fun updateTransaction(transaction: RecordTransaction) {
        transactionDao.updateTransaction(transaction)
    }

    suspend fun deleteTransactions(transactions: List<RecordTransaction>) {
        transactionDao.deleteTransactions(transactions)
    }
}
