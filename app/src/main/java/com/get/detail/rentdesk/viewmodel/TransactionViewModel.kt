package com.get.detail.rentdesk.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.data.repository.RentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class TransactionViewModel(private val repository: RentRepository) : ViewModel() {

    fun getTransactionsForProperty(propertyId: String): Flow<List<RecordTransaction>> {
        return repository.getTransactionsForProperty(propertyId)
    }

    fun insertTransaction(transaction: RecordTransaction) = viewModelScope.launch {
        repository.insertTransaction(transaction)
    }

    fun updateTransaction(transaction: RecordTransaction) = viewModelScope.launch {
        repository.updateTransaction(transaction)
    }

    fun deleteTransactions(transactions: List<RecordTransaction>) = viewModelScope.launch {
        repository.deleteTransactions(transactions)
    }
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
