package com.get.detail.rentdesk.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.repository.RentRepository
import com.get.detail.rentdesk.domain.model.TenantInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

import kotlinx.coroutines.launch

class TenantViewModel(private val repository: RentRepository) : ViewModel() {

    val navigateToTransactions = MutableLiveData("")
    
    private val _property = MutableStateFlow<PropertyTenantInfo?>(null)
    val property: StateFlow<PropertyTenantInfo?> = _property

    private val _propertyVacated = MutableSharedFlow<Unit>()
    val propertyVacated: SharedFlow<Unit> = _propertyVacated.asSharedFlow()

    fun getProperty(propertyId: String) = viewModelScope.launch {
        val p = repository.getPropertyById(propertyId)
        _property.value = p
    }

    fun updatePropertyWithTenant(
        propertyId: String,
        tenantInfo: TenantInfo,
        monthlyRent: Int,
        electricityPricePerUnit: Double,
        meterReading: Int
    ) {
        viewModelScope.launch {
            repository.saveTenant(propertyId, tenantInfo, monthlyRent, electricityPricePerUnit, meterReading)
            _property.value = repository.getPropertyById(propertyId)
            navigateToTransactions.postValue(propertyId)
        }
    }

    fun vacateProperty(propertyId: String) = viewModelScope.launch {
        repository.vacateTenant(propertyId)
        _property.value = repository.getPropertyById(propertyId)
        _propertyVacated.emit(Unit)
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
