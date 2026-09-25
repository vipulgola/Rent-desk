package com.get.detail.rentdesk

import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.domain.usecase.LegacyTransactionHistoryException
import com.get.detail.rentdesk.domain.usecase.TransactionDeletionCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionDeletionCalculatorTest {
    @Test
    fun deletingOnlyPaymentRestoresOpeningBalanceAndReading() {
        val payment = transaction("first", 1, 500, 100.0, 50, 2_000.0, 10.0, 1_000.0)

        val result = TransactionDeletionCalculator.recalculate(
            listOf(payment), setOf("first"), 100
        )

        assertEquals(100.0, result.balance, 0.0)
        assertEquals(50, result.reading)
        assertEquals(emptyList<RecordTransaction>(), result.updatedTransactions)
    }

    @Test
    fun deletingOlderPaymentReplaysLaterElectricityFromLastRetainedReading() {
        val first = transaction("first", 1, 100, 0.0, 0, 2_000.0, 10.0, 1_000.0)
        val second = transaction("second", 2, 150, 2_000.0, 100, 3_000.0, 8.0, 500.0)

        val result = TransactionDeletionCalculator.recalculate(
            listOf(second, first), setOf("first"), 100
        )

        assertEquals(3_700.0, result.balance, 0.0)
        assertEquals(150, result.reading)
        assertEquals(0.0, result.updatedTransactions.single().previousBalance!!, 0.0)
        assertEquals(0, result.updatedTransactions.single().previousReading)
    }

    @Test
    fun deletingSeveralPaymentsRestoresStateBeforeEarliestSelected() {
        val first = transaction("first", 1, 100, 250.0, 20, 2_000.0, 10.0, 1_000.0)
        val second = transaction("second", 2, 150, 1_850.0, 100, 2_000.0, 10.0, 500.0)

        val result = TransactionDeletionCalculator.recalculate(
            listOf(first, second), setOf("first", "second"), 100
        )

        assertEquals(250.0, result.balance, 0.0)
        assertEquals(20, result.reading)
    }

    @Test(expected = LegacyTransactionHistoryException::class)
    fun legacyPaymentNeedsManualCorrection() {
        val legacy = RecordTransaction("legacy", "property", 0, 100, 500.0)
        TransactionDeletionCalculator.recalculate(listOf(legacy), setOf("legacy"), 100)
    }

    private fun transaction(
        id: String,
        createdAt: Long,
        reading: Int,
        previousBalance: Double,
        previousReading: Int,
        rent: Double,
        rate: Double,
        amountPaid: Double
    ) = RecordTransaction(
        transactionId = id,
        propertyId = "property",
        paymentDateUtc = 0,
        reading = reading,
        amountPaid = amountPaid,
        previousBalance = previousBalance,
        previousReading = previousReading,
        rentCharged = rent,
        electricityRateCharged = rate,
        createdAtUtc = createdAt,
        modifiedAtUtc = createdAt
    )
}
