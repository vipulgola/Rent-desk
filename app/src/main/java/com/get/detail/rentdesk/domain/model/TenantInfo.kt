package com.get.detail.rentdesk.domain.model

import com.get.detail.rentdesk.utils.YearMonth
data class TenantInfo(
    val name: String,
    val mobileNumber: String,
    val joiningMonthYear: YearMonth,
    val joiningDayOfMonth: Int = 1,
    val aadhaarNumber: String,
    val address: String
)
