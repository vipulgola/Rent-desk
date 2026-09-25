package com.get.detail.rentdesk.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.repository.RentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TransactionViewModel(private val repository: RentRepository) : ViewModel() {

    fun getTransactionsForProperty(propertyId: String): Flow<List<RecordTransaction>> {
        return repository.getTransactionsForProperty(propertyId)
    }

    fun getPropertyFlow(propertyId: String): Flow<PropertyTenantInfo?> =
        repository.allProperties.map { properties ->
            properties.firstOrNull { it.propertyId == propertyId }
        }

    fun insertTransaction(transaction: RecordTransaction) = viewModelScope.launch {
        repository.insertTransaction(transaction)
    }

    suspend fun getProperty(propertyId: String): PropertyTenantInfo? =
        repository.getPropertyById(propertyId)

    suspend fun recordPayment(transaction: RecordTransaction) =
        repository.recordPayment(transaction)

    suspend fun updateRecordedPayment(transaction: RecordTransaction) =
        repository.updateRecordedPayment(transaction)

    fun updateTransaction(transaction: RecordTransaction) = viewModelScope.launch {
        repository.updateTransaction(transaction)
    }

    suspend fun deleteTransactions(
        transactions: List<RecordTransaction>,
        manualBalance: Double? = null,
        manualReading: Int? = null
    ) = repository.deleteTransactions(transactions, manualBalance, manualReading)
}

class TransactionViewModelFactory(private val repository: RentRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TransactionViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
