package com.get.detail.rentdesk.utils


data class YearMonth(val year: Int, val month: Int) {
    override fun toString(): String {
        return "%d-%02d".format(year, month)
    }
    companion object {
        fun parse(value: String): YearMonth {
            val parts = value.split("-")
            return YearMonth(parts[0].toInt(), parts[1].toInt())
        }
        
        fun now(): YearMonth {
            val calendar = java.util.Calendar.getInstance()
            return YearMonth(
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH) + 1
            )
        }
    }
}