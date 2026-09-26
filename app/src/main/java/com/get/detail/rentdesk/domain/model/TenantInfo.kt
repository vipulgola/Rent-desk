package com.get.detail.rentdesk.domain.model

import com.get.detail.rentdesk.utils.YearMonth
data class TenantInfo(
    val name: String,
    val mobileNumber: String,
    val joiningMonthYear: YearMonth,
    val joiningDayOfMonth: Int = 1,
    val aadhaarNumber: String,
    val address: String,
    val tenancyId: String? = null,
    val depositReceived: Double = 0.0,
    val depositDeductions: Double = 0.0,
    val depositRefunded: Double = 0.0,
    val depositNotes: String? = null
)
