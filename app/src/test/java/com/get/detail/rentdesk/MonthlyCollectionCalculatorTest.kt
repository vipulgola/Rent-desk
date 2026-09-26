package com.get.detail.rentdesk

import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.domain.model.TenantInfo
import com.get.detail.rentdesk.domain.usecase.MonthlyCollectionCalculator
import com.get.detail.rentdesk.utils.PaymentDateUtils
import com.get.detail.rentdesk.utils.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlyCollectionCalculatorTest {
    private val month = YearMonth(2026, 9)
    private val tenant = TenantInfo("Sunil", "9999999999", YearMonth(2026, 1), 10,
        "123456789012", "Address", tenancyId = "current")
    private val property = PropertyTenantInfo("room", "Room 1", tenantInfo = tenant,
        monthlyRent = 3000, electricityPricePerUnit = 8.0, balanceAmount = 500.0)

    private fun payment(received: Double = 2000.0) = RecordTransaction("payment", "room",
        PaymentDateUtils.fromDateParts(2026, 10, 2), 110, received,
        previousBalance = 500.0, previousReading = 100, rentCharged = 3000.0,
        electricityRateCharged = 8.0, billingMonth = month, tenancyId = "current")

    @Test fun latePaymentIsCountedInItsRentMonthWithElectricityAndOpeningBalance() {
        val result = MonthlyCollectionCalculator.calculate(listOf(property), listOf(payment()), month, month)
        assertEquals(500.0, result.openingBalance, 0.001)
        assertEquals(3000.0, result.rent, 0.001)
        assertEquals(80.0, result.electricity, 0.001)
        assertEquals(3580.0, result.expected, 0.001)
        assertEquals(2000.0, result.collected, 0.001)
        assertEquals(1580.0, result.pending, 0.001)
        val october = MonthlyCollectionCalculator.calculate(listOf(property), listOf(payment()),
            YearMonth(2026, 10), month)
        assertEquals(0.0, october.collected, 0.001)
    }

    @Test fun unbilledRoomIncludesSavedRentAndExistingBalanceButNotVacantRoom() {
        val vacant = property.copy(propertyId = "empty", tenantInfo = null)
        val result = MonthlyCollectionCalculator.calculate(listOf(property, vacant), emptyList(), month, month)
        assertEquals(3500.0, result.expected, 0.001)
        assertEquals(3500.0, result.pending, 0.001)
    }

    @Test fun overpaymentBecomesCreditWithoutNegativePending() {
        val result = MonthlyCollectionCalculator.calculate(listOf(property), listOf(payment(4000.0)), month, month)
        assertEquals(0.0, result.pending, 0.001)
        assertEquals(420.0, result.credit, 0.001)
    }

    @Test fun depositDoesNotIncreaseCollectionsAndDeletedPaymentDoesNotCount() {
        val withDeposit = property.copy(tenantInfo = tenant.copy(depositReceived = 10000.0))
        val result = MonthlyCollectionCalculator.calculate(listOf(withDeposit),
            listOf(payment().copy(isDeleted = true)), month, month)
        assertEquals(0.0, result.collected, 0.001)
        assertEquals(3500.0, result.expected, 0.001)
    }

    @Test fun futureTenantDoesNotCreateExpectedRentBeforeJoining() {
        val future = property.copy(tenantInfo = tenant.copy(joiningMonthYear = YearMonth(2026, 10)))
        val result = MonthlyCollectionCalculator.calculate(listOf(future), emptyList(), month, month)
        assertEquals(0.0, result.expected, 0.001)
    }

    @Test fun minimumIsIncludedInTotalsAndNextMonthsBalance() {
        val bill = payment(received = 3500.0).copy(reading = 109)
        val result = MonthlyCollectionCalculator.calculate(listOf(property), listOf(bill), month, month)
        assertEquals(100.0, result.electricity, 0.001)
        assertEquals(3600.0, result.expected, 0.001)
        assertEquals(100.0, result.pending, 0.001)
        val nextMonth = MonthlyCollectionCalculator.calculate(
            listOf(property), listOf(bill), YearMonth(2026, 10), month
        )
        assertEquals(100.0, nextMonth.openingBalance, 0.001)
    }
}
