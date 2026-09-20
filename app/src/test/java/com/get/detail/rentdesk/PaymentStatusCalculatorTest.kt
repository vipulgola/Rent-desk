package com.get.detail.rentdesk

import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.domain.model.TenantInfo
import com.get.detail.rentdesk.domain.usecase.PaymentStatusCalculator
import com.get.detail.rentdesk.domain.usecase.RentPaymentStatus
import com.get.detail.rentdesk.utils.PaymentDateUtils
import com.get.detail.rentdesk.utils.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class PaymentStatusCalculatorTest {
    private val property = PropertyTenantInfo(
        propertyId = "property-1",
        entityName = "Flat 1",
        tenantInfo = TenantInfo(
            name = "Tenant",
            mobileNumber = "9999999999",
            joiningMonthYear = YearMonth(2026, 1),
            joiningDayOfMonth = 10,
            aadhaarNumber = "123456789012",
            address = "Address"
        )
    )

    @Test
    fun beforeJoiningDay_withoutPreviousCyclePayment_isDue() {
        assertEquals(
            YearMonth(2026, 8),
            PaymentStatusCalculator.billingMonth(property, date(2026, 9, 9))
        )
        assertEquals(
            RentPaymentStatus.DUE,
            PaymentStatusCalculator.currentStatus(property, emptyList(), date(2026, 9, 9))
        )
    }

    @Test
    fun beforeJoiningDay_withPreviousCyclePayment_isPaid() {
        val transaction = paidTransaction(month = 8)

        assertEquals(
            RentPaymentStatus.PAID,
            PaymentStatusCalculator.currentStatus(property, listOf(transaction), date(2026, 9, 9))
        )
    }

    @Test
    fun onJoiningDay_isDue() {
        assertEquals(
            YearMonth(2026, 9),
            PaymentStatusCalculator.billingMonth(property, date(2026, 9, 10))
        )
        assertEquals(
            RentPaymentStatus.DUE,
            PaymentStatusCalculator.currentStatus(property, emptyList(), date(2026, 9, 10))
        )
    }

    @Test
    fun fullyPaidCurrentMonth_isPaidAfterDueDay() {
        val transaction = paidTransaction(month = 9)

        assertEquals(
            RentPaymentStatus.PAID,
            PaymentStatusCalculator.currentStatus(property, listOf(transaction), date(2026, 9, 15))
        )
    }

    @Test
    fun paymentWithRemainingPropertyBalance_isDue() {
        val transaction = paidTransaction(month = 9)

        assertEquals(
            RentPaymentStatus.DUE,
            PaymentStatusCalculator.currentStatus(
                property.copy(balanceAmount = 250.0),
                listOf(transaction),
                date(2026, 9, 15)
            )
        )
    }

    @Test
    fun propertyWithoutTenant_isVacant() {
        assertEquals(
            RentPaymentStatus.VACANT,
            PaymentStatusCalculator.currentStatus(
                property.copy(tenantInfo = null),
                emptyList(),
                date(2026, 9, 15)
            )
        )
    }

    @Test
    fun deletingPaidTransaction_changesStatusToDue() {
        val transaction = paidTransaction(month = 9)
        assertEquals(
            RentPaymentStatus.PAID,
            PaymentStatusCalculator.currentStatus(property, listOf(transaction), date(2026, 9, 15))
        )
        assertEquals(
            RentPaymentStatus.DUE,
            PaymentStatusCalculator.currentStatus(property, emptyList(), date(2026, 9, 15))
        )
    }

    private fun paidTransaction(month: Int) = RecordTransaction(
        transactionId = "transaction-$month",
        propertyId = property.propertyId,
        paymentDateUtc = PaymentDateUtils.fromDateParts(2026, month, 10),
        reading = 0,
        amountPaid = 1000.0
    )

    private fun date(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day)
        }
}
