package com.get.detail.rentdesk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.domain.usecase.LegacyTransactionHistoryException
import com.get.detail.rentdesk.domain.usecase.DeletionAdjustment
import com.get.detail.rentdesk.domain.usecase.TransactionDeletionCalculator
import com.get.detail.rentdesk.domain.usecase.RentCalculator
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM record_transaction WHERE propertyId = :propertyId AND isDeleted = 0 ORDER BY paymentDateUtc DESC, createdAtUtc DESC")
    fun getTransactionsForProperty(propertyId: String): Flow<List<RecordTransaction>>

    @Query("SELECT * FROM record_transaction WHERE isDeleted = 0")
    fun getAllTransactionsFlow(): Flow<List<RecordTransaction>>

    @Query("SELECT * FROM record_transaction WHERE isDeleted = 0")
    suspend fun getAllTransactions(): List<RecordTransaction>

    @Query("SELECT * FROM record_transaction")
    suspend fun getAllTransactionRecords(): List<RecordTransaction>

    @Query("SELECT * FROM record_transaction WHERE propertyId = :propertyId AND isDeleted = 0 ORDER BY createdAtUtc, transactionId")
    suspend fun getActiveTransactionsForProperty(propertyId: String): List<RecordTransaction>

    @Query("SELECT * FROM property_tenant_info WHERE propertyId = :propertyId")
    suspend fun getPropertyForPayment(propertyId: String): PropertyTenantInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: RecordTransaction)

    @Query(
        """UPDATE property_tenant_info
           SET meterReading = :meterReading,
               balanceAmount = :balanceAmount,
               modifiedAtUtc = :modifiedAtUtc
           WHERE propertyId = :propertyId"""
    )
    suspend fun updatePropertyPaymentState(
        propertyId: String,
        meterReading: Int,
        balanceAmount: Double,
        modifiedAtUtc: Long
    )

    @Query(
        """UPDATE property_tenant_info
           SET balanceAmount = :balanceAmount,
               modifiedAtUtc = :modifiedAtUtc
           WHERE propertyId = :propertyId"""
    )
    suspend fun updatePropertyBalance(
        propertyId: String,
        balanceAmount: Double,
        modifiedAtUtc: Long
    )

    @Query(
        """UPDATE property_tenant_info
           SET balanceAmount = balanceAmount + :balanceDelta,
               modifiedAtUtc = :modifiedAtUtc
           WHERE propertyId = :propertyId"""
    )
    suspend fun adjustPropertyBalance(
        propertyId: String,
        balanceDelta: Double,
        modifiedAtUtc: Long
    )

    @Transaction
    suspend fun recordPayment(
        transaction: RecordTransaction,
        modifiedAtUtc: Long
    ) {
        val property = getPropertyForPayment(transaction.propertyId)
            ?: error("Property not found")
        require(property.tenantInfo != null && transaction.tenancyId == property.tenantInfo.tenancyId) {
            "The tenant changed. Reopen Collect rent for the current tenant."
        }
        require(transaction.reading >= property.meterReading) {
            "Current reading cannot be lower than the previous reading"
        }
        require(transaction.amountPaid.isFinite() && transaction.amountPaid >= 0.0)
        val lastRecordedAt = getActiveTransactionsForProperty(transaction.propertyId)
            .lastOrNull()?.createdAtUtc ?: 0L
        val charge = property.monthlyRent +
            RentCalculator.electricityCharge(
                transaction.reading - property.meterReading, property.electricityPricePerUnit
            )
        insertTransaction(
            transaction.copy(
                previousBalance = property.balanceAmount,
                previousReading = property.meterReading,
                rentCharged = property.monthlyRent.toDouble(),
                electricityRateCharged = property.electricityPricePerUnit,
                createdAtUtc = maxOf(transaction.createdAtUtc, lastRecordedAt + 1L),
                modifiedAtUtc = modifiedAtUtc
            )
        )
        updatePropertyPaymentState(
            transaction.propertyId,
            transaction.reading,
            property.balanceAmount + charge - transaction.amountPaid,
            modifiedAtUtc
        )
    }

    @Transaction
    suspend fun updateRecordedPayment(
        transaction: RecordTransaction,
        modifiedAtUtc: Long
    ) {
        val property = getPropertyForPayment(transaction.propertyId) ?: error("Property not found")
        val transactions = getActiveTransactionsForProperty(transaction.propertyId)
            .filter { it.tenancyId == property.tenantInfo?.tenancyId }
        require(property.tenantInfo != null && transaction.tenancyId == property.tenantInfo.tenancyId) {
            "Past tenant payments are read-only"
        }
        val position = transactions.indexOfFirst { it.transactionId == transaction.transactionId }
        require(position >= 0) { "Transaction not found" }
        require(transaction.amountPaid.isFinite() && transaction.amountPaid >= 0.0)
        val current = transactions[position]
        val balanceDelta = current.amountPaid - transaction.amountPaid
        updateTransaction(current.copy(
            paymentDateUtc = transaction.paymentDateUtc,
            billingMonth = transaction.billingMonth,
            amountPaid = transaction.amountPaid,
            modifiedAtUtc = modifiedAtUtc
        ))
        adjustPropertyBalance(transaction.propertyId, balanceDelta, modifiedAtUtc)
        updateTransactions(transactions.drop(position + 1).mapNotNull { later ->
            later.previousBalance?.let { previous ->
                later.copy(previousBalance = previous + balanceDelta, modifiedAtUtc = modifiedAtUtc)
            }
        })
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<RecordTransaction>)

    @Update
    suspend fun updateTransaction(transaction: RecordTransaction)

    @Update
    suspend fun updateTransactions(transactions: List<RecordTransaction>)

    @Query("UPDATE record_transaction SET isDeleted = 1, modifiedAtUtc = :modifiedAtUtc WHERE transactionId IN (:ids) AND isDeleted = 0")
    suspend fun markTransactionsDeleted(ids: List<String>, modifiedAtUtc: Long)

    @Transaction
    suspend fun deleteTransactionsAndRestoreBalance(
        transactions: List<RecordTransaction>,
        modifiedAtUtc: Long,
        manualBalance: Double? = null,
        manualReading: Int? = null
    ) {
        if (transactions.isEmpty()) return
        if (manualBalance != null || manualReading != null) {
            require(transactions.map { it.propertyId }.distinct().size == 1) {
                "A manual correction can only apply to one property"
            }
        }
        transactions.groupBy { it.propertyId }.forEach { (propertyId, selected) ->
            val property = getPropertyForPayment(propertyId) ?: error("Property not found")
            require(property.tenantInfo != null) { "Past tenant payments are read-only" }
            val active = getActiveTransactionsForProperty(propertyId)
                .filter { it.tenancyId == property.tenantInfo.tenancyId }
            val selectedIds = selected.map { it.transactionId }.toSet()
            require(active.count { it.transactionId in selectedIds } == selectedIds.size) {
                "One or more selected transactions no longer exist"
            }
            val adjustment = try {
                TransactionDeletionCalculator.recalculate(active, selectedIds, modifiedAtUtc)
            } catch (legacy: LegacyTransactionHistoryException) {
                if (manualBalance == null || manualReading == null) throw legacy
                require(manualBalance.isFinite() && manualReading >= 0) {
                    "Invalid manual balance or reading"
                }
                // Historic bills have no charge snapshot. A manual correction cannot establish
                // reliable intermediate snapshots for the remaining historic payments.
                val remaining = active.filterNot { it.transactionId in selectedIds }
                    .map { it.copy(previousBalance = null, previousReading = null,
                        modifiedAtUtc = modifiedAtUtc) }
                DeletionAdjustment(
                    manualBalance, manualReading, remaining
                )
            }
            updateTransactions(adjustment.updatedTransactions)
            markTransactionsDeleted(selectedIds.toList(), modifiedAtUtc)
            updatePropertyPaymentState(
                propertyId, adjustment.reading, adjustment.balance, modifiedAtUtc
            )
        }
    }

    @Query("DELETE FROM record_transaction")
    suspend fun deleteAllTransactions()
}
