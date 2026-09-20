package com.get.detail.rentdesk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM record_transaction WHERE propertyId = :propertyId ORDER BY paymentDateUtc DESC, createdAtUtc DESC")
    fun getTransactionsForProperty(propertyId: String): Flow<List<RecordTransaction>>

    @Query("SELECT * FROM record_transaction")
    fun getAllTransactionsFlow(): Flow<List<RecordTransaction>>

    @Query("SELECT * FROM record_transaction")
    suspend fun getAllTransactions(): List<RecordTransaction>

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
        meterReading: Int,
        balanceAmount: Double,
        modifiedAtUtc: Long
    ) {
        insertTransaction(transaction)
        updatePropertyPaymentState(
            transaction.propertyId,
            meterReading,
            balanceAmount,
            modifiedAtUtc
        )
    }

    @Transaction
    suspend fun updateRecordedPayment(
        transaction: RecordTransaction,
        balanceDelta: Double,
        modifiedAtUtc: Long
    ) {
        updateTransaction(transaction)
        adjustPropertyBalance(transaction.propertyId, balanceDelta, modifiedAtUtc)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<RecordTransaction>)

    @Update
    suspend fun updateTransaction(transaction: RecordTransaction)

    @androidx.room.Delete
    suspend fun deleteTransaction(transaction: RecordTransaction)

    @androidx.room.Delete
    suspend fun deleteTransactions(transactions: List<RecordTransaction>)

    @Transaction
    suspend fun deleteTransactionsAndRestoreBalance(
        transactions: List<RecordTransaction>,
        modifiedAtUtc: Long
    ) {
        transactions.groupBy { it.propertyId }.forEach { (propertyId, propertyTransactions) ->
            adjustPropertyBalance(
                propertyId,
                propertyTransactions.sumOf { it.amountPaid },
                modifiedAtUtc
            )
        }
        deleteTransactions(transactions)
    }

    @Query("DELETE FROM record_transaction")
    suspend fun deleteAllTransactions()
}
