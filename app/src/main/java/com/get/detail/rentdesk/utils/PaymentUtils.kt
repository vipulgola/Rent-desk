package com.get.detail.rentdesk.utils

import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.domain.usecase.RentPaymentStatus

object PaymentUtils {

    fun updatePaymentStatusUI(
        textView: TextView,
        status: RentPaymentStatus,
        customText: CharSequence? = null,
        showDot: Boolean = false
    ) {
        val context = textView.context
        val (textColorRes, bgColorRes, statusTextRes) = when (status) {
            RentPaymentStatus.PAID -> Triple(
                R.color.status_paid,
                R.color.status_paid_container,
                R.string.payment_paid
            )
            RentPaymentStatus.DUE -> Triple(
                R.color.status_due,
                R.color.status_due_container,
                R.string.payment_due
            )
            RentPaymentStatus.VACANT -> Triple(
                R.color.status_vacant,
                R.color.status_vacant_container,
                R.string.payment_vacant
            )
        }

        textView.text = customText ?: context.getString(statusTextRes)
        textView.setTextColor(ContextCompat.getColor(context, textColorRes))

        val shape = GradientDrawable().apply {
            cornerRadius = 999f
            setColor(ContextCompat.getColor(context, bgColorRes))
        }
        textView.background = shape

        // Add some padding to make the background look better
        val density = context.resources.displayMetrics.density
        val horizontal = (12 * density).toInt()
        val vertical = (6 * density).toInt()
        textView.setPadding(horizontal, vertical, horizontal, vertical)
        val dot = if (showDot) GradientDrawable().apply {
            this.shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, textColorRes))
            val size = (7 * density).toInt()
            setBounds(0, 0, size, size)
        } else null
        textView.setCompoundDrawablesRelative(dot, null, null, null)
        textView.compoundDrawablePadding = if (showDot) (6 * density).toInt() else 0
    }
}
