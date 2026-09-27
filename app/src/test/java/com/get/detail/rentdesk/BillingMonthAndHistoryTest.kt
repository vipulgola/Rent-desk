package com.get.detail.rentdesk

import com.get.detail.rentdesk.data.local.converter.Converters
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.domain.model.TenantHistoryEntry
import com.get.detail.rentdesk.domain.model.TenantInfo
import com.get.detail.rentdesk.domain.usecase.PaymentStatusCalculator
import com.get.detail.rentdesk.domain.usecase.RentPaymentStatus
import com.get.detail.rentdesk.utils.PaymentDateUtils
import com.get.detail.rentdesk.utils.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class BillingMonthAndHistoryTest {
    private val tenant = TenantInfo("Tenant", "9999999999", YearMonth(2026, 1), 10,
        "123456789012", "Address", tenancyId = "current")
    private val property = PropertyTenantInfo("room", "Room", tenantInfo = tenant)
    private val september = Calendar.getInstance().apply { clear(); set(2026, Calendar.SEPTEMBER, 25) }
    private val payment = RecordTransaction("payment", "room",
        PaymentDateUtils.fromDateParts(2026, 10, 2), 100, 3000.0,
        billingMonth = YearMonth(2026, 9), tenancyId = "current")

    @Test fun rentMonthControlsStatusIndependentlyOfReceiptDate() {
        assertEquals(RentPaymentStatus.PAID, PaymentStatusCalculator.currentStatus(property, listOf(payment), september))
    }

    @Test fun oldTenantsPaymentCannotPayTheNewTenantRent() {
        assertEquals(RentPaymentStatus.DUE, PaymentStatusCalculator.currentStatus(property,
            listOf(payment.copy(tenancyId = "previous")), september))
    }

    @Test fun historicPaymentsWithoutRentMonthUsePaymentMonth() {
        assertEquals(YearMonth(2026, 10), payment.copy(billingMonth = null).rentMonth())
    }

    @Test fun tenantHistoryRoundTripPreservesDepositAndClosingBalance() {
        val entry = TenantHistoryEntry(tenant.copy(depositReceived = 10000.0,
            depositDeductions = 1000.0, depositRefunded = 9000.0, depositNotes = "Repairs"),
            3000, PaymentDateUtils.fromDateParts(2026, 9, 25), 1680.0)
        val converters = Converters()
        assertEquals(listOf(entry), converters.toTenantHistory(converters.fromTenantHistory(listOf(entry))))
    }
}
