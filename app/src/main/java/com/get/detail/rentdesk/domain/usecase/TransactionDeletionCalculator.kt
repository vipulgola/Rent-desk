package com.get.detail.rentdesk.domain.usecase

import com.get.detail.rentdesk.data.local.entity.RecordTransaction

class LegacyTransactionHistoryException : IllegalStateException(
    "The original bill details are missing from an older transaction"
)

data class DeletionAdjustment(
    val balance: Double,
    val reading: Int,
    val updatedTransactions: List<RecordTransaction>
)

object TransactionDeletionCalculator {
    /** Transactions are replayed in the order they were recorded, not their payment dates. */
    fun recalculate(
        activeTransactions: List<RecordTransaction>,
        selectedIds: Set<String>,
        modifiedAtUtc: Long
    ): DeletionAdjustment {
        val ordered = activeTransactions.sortedWith(
            compareBy<RecordTransaction> { it.createdAtUtc }.thenBy { it.transactionId }
        )
        val firstAffected = ordered.indexOfFirst { it.transactionId in selectedIds }
        require(firstAffected >= 0) { "No selected transaction exists" }
        val first = ordered[firstAffected]
        var balance = first.previousBalance ?: throw LegacyTransactionHistoryException()
        var reading = first.previousReading ?: throw LegacyTransactionHistoryException()
        val updates = mutableListOf<RecordTransaction>()

        ordered.drop(firstAffected).forEach { transaction ->
            if (transaction.transactionId in selectedIds) return@forEach
            val rent = transaction.rentCharged ?: throw LegacyTransactionHistoryException()
            val rate = transaction.electricityRateCharged
                ?: throw LegacyTransactionHistoryException()
            if (transaction.reading < reading) throw LegacyTransactionHistoryException()
            updates += transaction.copy(
                previousBalance = balance,
                previousReading = reading,
                modifiedAtUtc = modifiedAtUtc
            )
            balance += rent + (transaction.reading - reading) * rate - transaction.amountPaid
            reading = transaction.reading
        }
        return DeletionAdjustment(balance, reading, updates)
    }
}
