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
import kotlinx.coroutines.flow.combine
import androidx.room.withTransaction
import com.get.detail.rentdesk.data.local.AppDatabase
import com.get.detail.rentdesk.domain.model.TenantInfo
import com.get.detail.rentdesk.domain.model.TenantHistoryEntry
import java.util.UUID

class RentRepository(
    context: Context,
    private val addressDao: AddressDao,
    private val propertyTenantDao: PropertyTenantDao,
    private val transactionDao: TransactionDao
) {
    private val database = AppDatabase.getDatabase(context.applicationContext)
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

    suspend fun saveTenant(propertyId: String, info: TenantInfo, rent: Int, rate: Double, reading: Int) {
        database.withTransaction {
            val property = propertyTenantDao.getPropertyById(propertyId) ?: error("Property not found")
            val previous = property.tenantInfo
            val tenant = info.copy(
                tenancyId = previous?.tenancyId ?: UUID.randomUUID().toString(),
                depositReceived = previous?.depositReceived ?: 0.0,
                depositDeductions = previous?.depositDeductions ?: 0.0,
                depositRefunded = previous?.depositRefunded ?: 0.0,
                depositNotes = previous?.depositNotes
            )
            propertyTenantDao.updateProperty(property.copy(tenantInfo = tenant, monthlyRent = rent,
                balanceAmount = if (previous == null) 0.0 else property.balanceAmount,
                unassignedBalance = if (previous == null && property.balanceAmount != 0.0)
                    property.balanceAmount else property.unassignedBalance,
                electricityPricePerUnit = rate, meterReading = reading, modifiedAtUtc = System.currentTimeMillis()))
        }
        autoBackupScheduler.databaseChanged()
    }

    suspend fun vacateTenant(propertyId: String) {
        database.withTransaction {
            val property = propertyTenantDao.getPropertyById(propertyId) ?: error("Property not found")
            val tenant = property.tenantInfo ?: return@withTransaction
            val now = System.currentTimeMillis()
            val history = TenantHistoryEntry(tenant, property.monthlyRent, now, property.balanceAmount)
            propertyTenantDao.updateProperty(property.copy(tenantInfo = null, balanceAmount = 0.0,
                tenantHistory = property.tenantHistory.orEmpty() + history, modifiedAtUtc = now))
        }
        autoBackupScheduler.databaseChanged()
    }

    suspend fun updateDeposit(propertyId: String, tenancyId: String, received: Double,
        deductions: Double, refunded: Double, notes: String) {
        require(listOf(received, deductions, refunded).all { it.isFinite() && it >= 0.0 })
        require(deductions + refunded <= received + 0.005) { "Deductions and refunds exceed the deposit" }
        database.withTransaction {
            val property = propertyTenantDao.getPropertyById(propertyId) ?: error("Property not found")
            fun updated(tenant: TenantInfo) = tenant.copy(depositReceived = received,
                depositDeductions = deductions, depositRefunded = refunded, depositNotes = notes)
            val active = property.tenantInfo
            val found = active?.tenancyId == tenancyId ||
                property.tenantHistory.orEmpty().any { it.tenant.tenancyId == tenancyId }
            require(found) { "Tenant not found" }
            propertyTenantDao.updateProperty(property.copy(
                tenantInfo = if (active != null && active.tenancyId == tenancyId) updated(active) else active,
                tenantHistory = property.tenantHistory?.map {
                    if (it.tenant.tenancyId == tenancyId) it.copy(tenant = updated(it.tenant)) else it
                },
                modifiedAtUtc = System.currentTimeMillis()
            ))
        }
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
        return combine(transactionDao.getTransactionsForProperty(propertyId), allProperties) { payments, properties ->
            val tenant = properties.firstOrNull { it.propertyId == propertyId }?.tenantInfo
            if (tenant == null) emptyList() else payments.filter { it.tenancyId == tenant.tenancyId }
        }
    }

    suspend fun insertTransaction(transaction: RecordTransaction) {
        transactionDao.insertTransaction(transaction)
        autoBackupScheduler.databaseChanged()
    }

    suspend fun recordPayment(transaction: RecordTransaction) {
        transactionDao.recordPayment(
            transaction = transaction,
            modifiedAtUtc = System.currentTimeMillis()
        )
        autoBackupScheduler.databaseChanged()
    }

    suspend fun updateRecordedPayment(transaction: RecordTransaction) {
        val modifiedAt = System.currentTimeMillis()
        transactionDao.updateRecordedPayment(
            transaction,
            modifiedAt
        )
        autoBackupScheduler.databaseChanged()
    }

    suspend fun updateTransaction(transaction: RecordTransaction) {
        transactionDao.updateTransaction(transaction.copy(modifiedAtUtc = System.currentTimeMillis()))
        autoBackupScheduler.databaseChanged()
    }

    suspend fun deleteTransactions(
        transactions: List<RecordTransaction>,
        manualBalance: Double? = null,
        manualReading: Int? = null
    ) {
        transactionDao.deleteTransactionsAndRestoreBalance(
            transactions,
            System.currentTimeMillis(),
            manualBalance,
            manualReading
        )
        autoBackupScheduler.databaseChanged()
    }
}
