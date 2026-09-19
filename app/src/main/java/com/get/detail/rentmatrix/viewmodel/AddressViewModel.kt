package com.get.detail.rentmatrix.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.get.detail.rentmatrix.data.local.dao.AddressWithPropertyCount
import com.get.detail.rentmatrix.data.local.entity.Address
import com.get.detail.rentmatrix.data.repository.RentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class AddressViewModel(private val repository: RentRepository) : ViewModel() {
    val addressesWithCount: Flow<List<AddressWithPropertyCount>> = repository.addressesWithCount

    fun insertAddress(address: Address) = viewModelScope.launch {
        repository.insertAddress(address)
    }
}

class AddressViewModelFactory(private val repository: RentRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddressViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddressViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}