package com.get.detail.rentdesk.domain.model

data class AddressOverview(
    val dataUUID: String,
    val address: String,
    val propertyCount: Int,
    val occupiedCount: Int,
    val pendingAmount: Double
) {
    val vacantCount: Int get() = propertyCount - occupiedCount
}
