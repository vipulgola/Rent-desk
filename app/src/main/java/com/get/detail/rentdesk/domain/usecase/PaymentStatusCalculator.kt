package com.get.detail.rentdesk.domain.usecase

import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.utils.YearMonth
import java.util.Calendar

enum class RentPaymentStatus {
    DUE,
    PAID,
    VACANT
}

object PaymentStatusCalculator {
    fun currentStatus(
        property: PropertyTenantInfo,
        transactions: List<RecordTransaction>,
        calendar: Calendar = Calendar.getInstance()
    ): RentPaymentStatus {
        val tenant = property.tenantInfo ?: return RentPaymentStatus.VACANT
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonthNumber = calendar.get(Calendar.MONTH) + 1
        val currentMonth = YearMonth(currentYear, currentMonthNumber)
        val joiningMonth = tenant.joiningMonthYear
        if (currentMonth.year < joiningMonth.year ||
            (currentMonth.year == joiningMonth.year && currentMonth.month < joiningMonth.month)
        ) {
            return RentPaymentStatus.PAID
        }

        val storedJoiningDay = tenant.joiningDayOfMonth.takeIf { it in 1..31 } ?: 1
        val dueDay = minOf(storedJoiningDay, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        val billingMonth = if (calendar.get(Calendar.DAY_OF_MONTH) >= dueDay) {
            currentMonth
        } else {
            previousMonth(currentYear, currentMonthNumber)
        }

        val isFullyPaid = transactions.any { transaction ->
            transaction.propertyId == property.propertyId &&
                transaction.monthYear == billingMonth &&
                transaction.amountPaid > 0 &&
                transaction.balanceAmount <= 0
        }
        return if (isFullyPaid) RentPaymentStatus.PAID else RentPaymentStatus.DUE
    }

    private fun previousMonth(year: Int, month: Int): YearMonth = if (month == 1) {
        YearMonth(year - 1, 12)
    } else {
        YearMonth(year, month - 1)
    }

    fun sortRank(status: RentPaymentStatus): Int = when (status) {
        RentPaymentStatus.DUE -> 0
        RentPaymentStatus.PAID -> 1
        RentPaymentStatus.VACANT -> 2
    }
}
