package com.get.detail.rentdesk.data.repository

import android.content.Context
import com.get.detail.rentdesk.backup.AutoBackupScheduler
import com.get.detail.rentdesk.data.local.dao.AddressDao
import com.get.detail.rentdesk.data.local.dao.AddressWithPropertyCount
import com.get.detail.rentdesk.data.local.dao.PropertyTenantDao
import com.get.detail.rentdesk.data.local.dao.TransactionDao
import com.get.detail.rentdesk.data.local.entity.Address
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import kotlinx.coroutines.flow.Flow

class RentRepository(
    context: Context,
    private val addressDao: AddressDao,
    private val propertyTenantDao: PropertyTenantDao,
    private val transactionDao: TransactionDao
) {
    private val autoBackupScheduler = AutoBackupScheduler(context.applicationContext)
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
        autoBackupScheduler.databaseChanged()
    }

    suspend fun updateProperty(property: PropertyTenantInfo) {
        propertyTenantDao.updateProperty(property.copy(modifiedAtUtc = System.currentTimeMillis()))
        autoBackupScheduler.databaseChanged()
    }

    suspend fun deleteProperty(property: PropertyTenantInfo) {
        propertyTenantDao.deleteProperty(property)
        autoBackupScheduler.databaseChanged()
    }

    suspend fun deleteProperties(properties: List<PropertyTenantInfo>) {
        propertyTenantDao.deleteProperties(properties)
        autoBackupScheduler.databaseChanged()
    }

    suspend fun insertAddress(address: Address) {
        addressDao.insertAddress(address)
        autoBackupScheduler.databaseChanged()
    }

    suspend fun updateAddressName(id: String, name: String) {
        addressDao.updateAddressName(id, name, System.currentTimeMillis())
        autoBackupScheduler.databaseChanged()
    }

    fun getPropertiesByAddress(addressId: String): Flow<List<PropertyTenantInfo>> {
        return propertyTenantDao.getPropertiesByAddress(addressId)
    }

    fun getTransactionsForProperty(propertyId: String): Flow<List<RecordTransaction>> {
        return transactionDao.getTransactionsForProperty(propertyId)
    }

    suspend fun insertTransaction(transaction: RecordTransaction) {
        transactionDao.insertTransaction(transaction)
        autoBackupScheduler.databaseChanged()
    }

    suspend fun recordPayment(
        transaction: RecordTransaction,
        meterReading: Int,
        balanceAmount: Double
    ) {
        transactionDao.recordPayment(
            transaction = transaction,
            meterReading = meterReading,
            balanceAmount = balanceAmount,
            modifiedAtUtc = System.currentTimeMillis()
        )
        autoBackupScheduler.databaseChanged()
    }

    suspend fun updateRecordedPayment(
        transaction: RecordTransaction,
        balanceDelta: Double
    ) {
        val modifiedAt = System.currentTimeMillis()
        transactionDao.updateRecordedPayment(
            transaction.copy(modifiedAtUtc = modifiedAt),
            balanceDelta,
            modifiedAt
        )
        autoBackupScheduler.databaseChanged()
    }

    suspend fun updateTransaction(transaction: RecordTransaction) {
        transactionDao.updateTransaction(transaction.copy(modifiedAtUtc = System.currentTimeMillis()))
        autoBackupScheduler.databaseChanged()
    }

    suspend fun deleteTransactions(transactions: List<RecordTransaction>) {
        transactionDao.deleteTransactionsAndRestoreBalance(
            transactions,
            System.currentTimeMillis()
        )
        autoBackupScheduler.databaseChanged()
    }
}
