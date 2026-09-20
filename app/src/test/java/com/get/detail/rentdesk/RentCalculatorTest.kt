package com.get.detail.rentdesk

import com.get.detail.rentdesk.domain.usecase.RentCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class RentCalculatorTest {
    @Test
    fun calculate_includesElectricityBalanceAndRent() {
        val result = RentCalculator.calculate(
            previousReading = 100,
            currentReading = 125,
            electricityPricePerUnit = 8.5,
            previousBalance = 500.0,
            monthlyRent = 10_000
        )

        assertEquals(25, result.consumedUnits)
        assertEquals(212.5, result.electricityCost, 0.0)
        assertEquals(10_712.5, result.totalAmount, 0.0)
        assertEquals(712.5, RentCalculator.remainingBalance(result.totalAmount, 10_000.0), 0.0)
    }
}
