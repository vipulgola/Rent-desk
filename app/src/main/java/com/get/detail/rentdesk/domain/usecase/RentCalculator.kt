package com.get.detail.rentdesk.domain.usecase

import com.get.detail.rentdesk.data.local.entity.RecordTransaction

class RentCalculator {
    /**
     * Calculates the balance amount for a new transaction.
     * This is a placeholder for the exact rent calculation rules to be provided later.
     */
    fun calculateBalance(
        rentAmount: Int,
        amountPaid: Int,
        previousBalance: Int = 0
    ): Int {
        // Simple logic for now: (Rent + Previous Balance) - Amount Paid
        return (rentAmount + previousBalance) - amountPaid
    }
}