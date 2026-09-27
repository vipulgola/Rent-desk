package com.get.detail.rentdesk.domain.model

data class TenantHistoryEntry(
    val tenant: TenantInfo,
    val monthlyRent: Int,
    val vacatedAtUtc: Long,
    val closingBalance: Double
)
