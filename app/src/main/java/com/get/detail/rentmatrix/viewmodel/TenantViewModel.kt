package com.get.detail.rentmatrix.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.get.detail.rentmatrix.data.local.entity.PropertyTenantInfo
import com.get.detail.rentmatrix.data.repository.RentRepository
import com.get.detail.rentmatrix.domain.model.TenantInfo
import com.get.detail.rentmatrix.utils.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.launch

class TenantViewModel(private val repository: RentRepository) : ViewModel() {

    val navigateToTransactions = MutableLiveData("")
    
    private val _property = MutableStateFlow<PropertyTenantInfo?>(null)
    val property: StateFlow<PropertyTenantInfo?> = _property

    fun getProperty(propertyId: String) = viewModelScope.launch {
        val p = repository.getPropertyById(propertyId)
        _property.value = p
    }

    fun updatePropertyWithTenant(propertyId: String, tenantInfo: TenantInfo, startMonthYear: YearMonth) {
        CoroutineScope(Dispatchers.IO).launch {
            val property = repository.getPropertyById(propertyId)
            property?.let {
                val updatedProperty = it.copy(
                    tenantInfo = tenantInfo,
                    startMonthYear = startMonthYear
                )
                repository.updateProperty(updatedProperty)
                navigateToTransactions.postValue(propertyId)
            }
        }
    }
}

class TenantViewModelFactory(private val repository: RentRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TenantViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TenantViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}