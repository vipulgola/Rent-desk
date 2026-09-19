package com.get.detail.rentdesk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM record_transaction WHERE propertyId = :propertyId ORDER BY monthYear DESC")
    fun getTransactionsForProperty(propertyId: String): Flow<List<RecordTransaction>>

    @Query("SELECT * FROM record_transaction")
    fun getAllTransactionsFlow(): Flow<List<RecordTransaction>>

    @Query("SELECT * FROM record_transaction")
    suspend fun getAllTransactions(): List<RecordTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: RecordTransaction)

    @Update
    suspend fun updateTransaction(transaction: RecordTransaction)

    @androidx.room.Delete
    suspend fun deleteTransaction(transaction: RecordTransaction)

    @androidx.room.Delete
    suspend fun deleteTransactions(transactions: List<RecordTransaction>)
    
    @Query("SELECT EXISTS(SELECT 1 FROM record_transaction WHERE propertyId = :propertyId AND monthYear = :monthYear LIMIT 1)")
    suspend fun doesTransactionExist(propertyId: String, monthYear: String): Boolean
}
