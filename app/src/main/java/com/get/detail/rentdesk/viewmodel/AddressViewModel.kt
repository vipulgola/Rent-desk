package com.get.detail.rentdesk.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.get.detail.rentdesk.data.local.entity.Address
import com.get.detail.rentdesk.data.repository.RentRepository
import com.get.detail.rentdesk.domain.model.AddressOverview
import com.get.detail.rentdesk.domain.usecase.MonthlyCollectionCalculator
import com.get.detail.rentdesk.utils.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class AddressViewModel(private val repository: RentRepository) : ViewModel() {
    val addressOverviews: Flow<List<AddressOverview>> = combine(
        repository.addressesWithCount, repository.allProperties, repository.allTransactions
    ) { addresses, properties, transactions ->
        val propertiesByAddress = properties.groupBy { it.addressId }
        val month = YearMonth.now()
        addresses.map { address ->
            val rooms = propertiesByAddress[address.dataUUID].orEmpty()
            AddressOverview(
                dataUUID = address.dataUUID,
                address = address.address,
                propertyCount = rooms.size,
                occupiedCount = rooms.count { it.tenantInfo != null },
                pendingAmount = MonthlyCollectionCalculator.calculate(rooms, transactions, month).pending
            )
        }.sortedWith(
            compareByDescending<AddressOverview> { it.propertyCount > 0 }
                .thenBy { it.address.lowercase() }
        )
    }.flowOn(Dispatchers.Default)

    fun insertAddress(address: Address) = viewModelScope.launch {
        repository.insertAddress(address)
    }

    fun updateAddressName(id: String, name: String) = viewModelScope.launch {
        repository.updateAddressName(id, name)
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
