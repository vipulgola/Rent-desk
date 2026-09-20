package com.get.detail.rentdesk.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.data.repository.RentRepository
import com.get.detail.rentdesk.utils.DataExporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class PropertyViewModel(private val repository: RentRepository) : ViewModel() {
    val allProperties: Flow<List<PropertyTenantInfo>> = repository.allProperties
    val allTransactions: Flow<List<RecordTransaction>> = repository.allTransactions

    suspend fun getExportDataCsv(): String {
        val properties = repository.getAllPropertiesList()
        val transactions = repository.getAllTransactions()
        return DataExporter.toCsv(properties, transactions)
    }

    suspend fun getExportDataJson(): String {
        val properties = repository.getAllPropertiesList()
        val transactions = repository.getAllTransactions()
        return DataExporter.toJson(properties, transactions)
    }

    fun insertProperty(property: PropertyTenantInfo) = viewModelScope.launch {
        repository.insertProperty(property)
    }

    fun updateProperty(property: PropertyTenantInfo) = viewModelScope.launch {
        repository.updateProperty(property)
    }

    fun deleteProperty(property: PropertyTenantInfo) = viewModelScope.launch {
        repository.deleteProperty(property)
    }

    fun deleteProperties(properties: List<PropertyTenantInfo>) = viewModelScope.launch {
        repository.deleteProperties(properties)
    }
}

class PropertyViewModelFactory(private val repository: RentRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PropertyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PropertyViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
