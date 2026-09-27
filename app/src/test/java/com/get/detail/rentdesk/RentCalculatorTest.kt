package com.get.detail.rentdesk

import com.get.detail.rentdesk.domain.usecase.RentCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class RentCalculatorTest {
    @Test
    fun usageBelowTenUnitsHasAMinimumChargeIncludingZeroUsage() {
        for (units in listOf(0, 1, 9)) {
            val result = RentCalculator.calculate(100, 100 + units, 8.0, 500.0, 2_000)
            assertEquals(100.0, result.electricityCost, 0.0)
            assertEquals(2_600.0, result.totalAmount, 0.0)
        }
    }

    @Test
    fun exactlyTenUnitsUsesTheMeteredCharge() {
        val result = RentCalculator.calculate(100, 110, 8.0, 500.0, 2_000)
        assertEquals(80.0, result.electricityCost, 0.0)
        assertEquals(2_580.0, result.totalAmount, 0.0)
    }

    @Test
    fun usageAboveTenUnitsCanCostLessThanTheMinimum() {
        val result = RentCalculator.calculate(100, 112, 5.0, 0.0, 2_000)
        assertEquals(60.0, result.electricityCost, 0.0)
    }

    @Test
    fun minimumDoesNotReduceALargerMeteredCharge() {
        val result = RentCalculator.calculate(100, 109, 20.0, 0.0, 2_000)
        assertEquals(180.0, result.electricityCost, 0.0)
    }

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
