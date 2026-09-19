package com.get.detail.rentmatrix.domain.model

import com.get.detail.rentmatrix.utils.YearMonth
data class TenantInfo(
    val name: String,
    val mobileNumber: String,
    val joiningMonthYear: YearMonth,
    val aadhaarNumber: String,
    val address: String
)