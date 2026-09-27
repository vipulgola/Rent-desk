package com.get.detail.rentdesk.utils

import android.content.Context
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import com.get.detail.rentdesk.R
import java.text.DateFormatSymbols
import java.util.Locale

object RentMonthPicker {
    fun label(month: YearMonth): String =
        "${DateFormatSymbols.getInstance(Locale.getDefault()).months[month.month - 1]} ${month.year}"

    fun show(context: Context, initial: YearMonth, onSelected: (YearMonth) -> Unit) {
        val month = NumberPicker(context).apply {
            minValue = 1
            maxValue = 12
            displayedValues = DateFormatSymbols.getInstance(Locale.getDefault()).shortMonths.take(12).toTypedArray()
            value = initial.month
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            contentDescription = context.getString(R.string.collection_month)
        }
        val year = NumberPicker(context).apply {
            minValue = 1900
            maxValue = 2200
            value = initial.year.coerceIn(minValue, maxValue)
            wrapSelectorWheel = false
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            contentDescription = context.getString(R.string.collection_year)
        }
        val fields = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            addView(month, LinearLayout.LayoutParams(0, -2, 1f))
            addView(year, LinearLayout.LayoutParams(0, -2, 1f))
        }
        AlertDialog.Builder(context).setTitle(R.string.select_rent_month).setView(fields)
            .setPositiveButton(R.string.save) { _, _ -> onSelected(YearMonth(year.value, month.value)) }
            .setNegativeButton(R.string.cancel, null).show()
    }
}
