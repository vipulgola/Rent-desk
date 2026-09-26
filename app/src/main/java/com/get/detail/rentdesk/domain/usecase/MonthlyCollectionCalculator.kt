package com.get.detail.rentdesk.domain.usecase

import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.domain.model.TenantInfo
import com.get.detail.rentdesk.utils.PaymentDateUtils
import com.get.detail.rentdesk.utils.YearMonth
import java.util.Calendar

data class MonthlyCollection(
    val openingBalance: Double,
    val rent: Double,
    val electricity: Double,
    val expected: Double,
    val collected: Double,
    val pending: Double,
    val credit: Double,
    val estimated: Boolean
)

object MonthlyCollectionCalculator {
    fun calculate(properties: List<PropertyTenantInfo>, transactions: List<RecordTransaction>,
        month: YearMonth, currentMonth: YearMonth = YearMonth.now()): MonthlyCollection {
        var opening = 0.0
        var rent = 0.0
        var electricity = 0.0
        var expected = 0.0
        var collected = 0.0
        var pending = 0.0
        var credit = 0.0
        var estimated = false
        val paymentsByProperty = transactions.filterNot { it.isDeleted }.groupBy { it.propertyId }
        properties.forEach { property ->
            val payments = paymentsByProperty[property.propertyId].orEmpty()
            val occupants = buildList {
                property.tenantInfo?.let { add(Occupant(it, property.monthlyRent, null)) }
                property.tenantHistory.orEmpty().forEach { add(Occupant(it.tenant, it.monthlyRent, it.vacatedAtUtc)) }
            }
            val keys = (occupants.map { it.tenant.tenancyId } +
                payments.filter { it.rentMonth() == month }.map { it.tenancyId }).distinct()
            keys.forEach tenantLoop@ { key ->
                val occupant = occupants.firstOrNull { it.tenant.tenancyId == key }
                val history = payments.filter { it.tenancyId == key }.sortedBy { it.createdAtUtc }
                val bills = history.filter { it.rentMonth() == month }
                val scheduled = occupant?.let { isDueInMonth(it, month) } == true
                if (bills.isEmpty() && !scheduled) return@tenantLoop
                val prior = history.filter { score(it.rentMonth()) < score(month) }.lastOrNull()
                val openingForTenant = bills.firstOrNull()?.previousBalance
                    ?: prior?.let(::closingBalance)
                    ?: if (bills.isEmpty() && month == currentMonth &&
                        property.tenantInfo?.tenancyId == key &&
                        history.none { score(it.rentMonth()) > score(month) }) property.balanceAmount else 0.0
                val savedRent = occupant?.rent ?: property.monthlyRent
                val rentForTenant = if (bills.isEmpty()) savedRent.toDouble()
                    else bills.sumOf { it.rentCharged ?: savedRent.toDouble() }
                val electricityForTenant = bills.sumOf(::electricityCharge)
                val received = bills.sumOf { it.amountPaid }
                val charges = openingForTenant + rentForTenant + electricityForTenant
                opening += openingForTenant
                rent += rentForTenant
                electricity += electricityForTenant
                expected += charges.coerceAtLeast(0.0)
                collected += received
                pending += (charges - received).coerceAtLeast(0.0)
                credit += (received - charges).coerceAtLeast(0.0)
                if (bills.any { it.rentCharged == null || it.previousReading == null ||
                    it.electricityRateCharged == null || it.previousBalance == null } ||
                    (bills.isEmpty() && month != currentMonth)) estimated = true
            }
        }
        return MonthlyCollection(opening, rent, electricity, expected, collected, pending, credit, estimated)
    }

    private data class Occupant(val tenant: TenantInfo, val rent: Int, val vacated: Long?)
    private fun score(month: YearMonth) = month.year * 12 + month.month
    private fun electricityCharge(payment: RecordTransaction): Double =
        if (payment.previousReading != null && payment.electricityRateCharged != null)
            (payment.reading - payment.previousReading).coerceAtLeast(0) * payment.electricityRateCharged else 0.0
    private fun closingBalance(payment: RecordTransaction): Double? {
        val previous = payment.previousBalance ?: return null
        val rent = payment.rentCharged ?: return null
        return previous + rent + electricityCharge(payment) - payment.amountPaid
    }
    private fun isDueInMonth(occupant: Occupant, month: YearMonth): Boolean {
        val tenant = occupant.tenant
        val calendar = PaymentDateUtils.toCalendar(PaymentDateUtils.fromDateParts(month.year, month.month, 1))
        val day = minOf(tenant.joiningDayOfMonth.coerceIn(1, 31), calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        val dueDate = PaymentDateUtils.fromDateParts(month.year, month.month, day)
        val joining = PaymentDateUtils.fromDateParts(tenant.joiningMonthYear.year,
            tenant.joiningMonthYear.month, tenant.joiningDayOfMonth.coerceIn(1, 31))
        return dueDate >= joining && (occupant.vacated == null || dueDate <= occupant.vacated)
    }
}
