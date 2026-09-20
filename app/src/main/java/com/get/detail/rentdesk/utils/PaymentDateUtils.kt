package com.get.detail.rentdesk.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object PaymentDateUtils {
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    fun fromDateParts(year: Int, month: Int, dayOfMonth: Int): Long =
        Calendar.getInstance(utc, Locale.US).apply {
            clear()
            set(year, month - 1, dayOfMonth, 0, 0, 0)
        }.timeInMillis

    fun today(): Long {
        val localToday = Calendar.getInstance()
        return fromDateParts(
            localToday.get(Calendar.YEAR),
            localToday.get(Calendar.MONTH) + 1,
            localToday.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun toCalendar(paymentDateUtc: Long): Calendar =
        Calendar.getInstance(utc, Locale.US).apply { timeInMillis = paymentDateUtc }

    fun toYearMonth(paymentDateUtc: Long): YearMonth {
        val calendar = toCalendar(paymentDateUtc)
        return YearMonth(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1
        )
    }

    fun format(paymentDateUtc: Long, pattern: String = "dd/MM/yyyy"): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = utc }
            .format(Date(paymentDateUtc))

    fun parse(value: String, pattern: String = "dd/MM/yyyy"): Long? {
        val parser = SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = utc
            isLenient = false
        }
        val parsed = runCatching { parser.parse(value) }.getOrNull() ?: return null
        return parsed.time.takeIf { parser.format(parsed) == value }
    }
}
