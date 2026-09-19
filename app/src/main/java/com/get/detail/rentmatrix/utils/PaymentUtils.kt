package com.get.detail.rentmatrix.utils

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import com.get.detail.rentmatrix.data.local.dao.TransactionDao
import com.get.detail.rentmatrix.data.local.entity.RecordTransaction

object PaymentUtils {

    /**
     * Checks if the current month's rent is paid for a given property.
     * This method compares the current YearMonth with the transactions.
     */
    fun isPaidForCurrentMonth(propertyId: String, transactions: List<RecordTransaction>): Boolean {
        val currentMonth = YearMonth.now()
        return transactions.any { 
            it.propertyId == propertyId && 
            it.monthYear == currentMonth &&
            it.amountPaid > 0
        }
    }

    /**
     * Updates the Payment Status TextView with appropriate colors and text.
     * Paid: Green text with light green background.
     * Unpaid: Red text with light red background.
     */
    fun updatePaymentStatusUI(textView: TextView, isPaid: Boolean) {
        val context = textView.context
        val (textColor, bgColor, statusText) = if (isPaid) {
            Triple(
                Color.parseColor("#2E7D32"), // Dark Green
                Color.parseColor("#E8F5E9"), // Light Green
                "Paid"
            )
        } else {
            Triple(
                Color.parseColor("#C62828"), // Dark Red
                Color.parseColor("#FFEBEE"), // Light Red
                "Unpaid"
            )
        }

        textView.text = statusText
        textView.setTextColor(textColor)
        
        val shape = GradientDrawable().apply {
            cornerRadius = 12f
            setColor(bgColor)
        }
        textView.background = shape
        
        // Add some padding to make the background look better
        val paddingEnd = 16
        val paddingTop = 8
        textView.setPadding(paddingEnd, paddingTop, paddingEnd, paddingTop)
    }
}
