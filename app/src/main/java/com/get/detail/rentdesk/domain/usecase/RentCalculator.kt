package com.get.detail.rentdesk.domain.usecase

data class RentBreakdown(
    val consumedUnits: Int,
    val electricityCost: Double,
    val previousBalance: Double,
    val rentAmount: Double,
    val totalAmount: Double
)

object RentCalculator {
    fun calculate(
        previousReading: Int,
        currentReading: Int,
        electricityPricePerUnit: Double,
        previousBalance: Double,
        monthlyRent: Int
    ): RentBreakdown {
        require(currentReading >= previousReading) {
            "Current reading cannot be lower than the previous reading"
        }
        val consumedUnits = currentReading - previousReading
        val electricityCost = consumedUnits * electricityPricePerUnit
        val rentAmount = monthlyRent.toDouble()
        return RentBreakdown(
            consumedUnits = consumedUnits,
            electricityCost = electricityCost,
            previousBalance = previousBalance,
            rentAmount = rentAmount,
            totalAmount = electricityCost + previousBalance + rentAmount
        )
    }

    fun remainingBalance(totalAmount: Double, amountReceived: Double): Double =
        totalAmount - amountReceived
}
