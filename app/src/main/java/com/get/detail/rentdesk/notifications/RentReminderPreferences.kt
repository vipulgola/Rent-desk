package com.get.detail.rentdesk.notifications

import android.content.Context

class RentReminderPreferences(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("RentDeskRentReminders", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) { preferences.edit().putBoolean("enabled", value).apply() }

    val hour: Int get() = preferences.getInt("hour", 9).coerceIn(0, 23)
    val minute: Int get() = preferences.getInt("minute", 0).coerceIn(0, 59)

    fun setTime(hour: Int, minute: Int) {
        require(hour in 0..23 && minute in 0..59)
        preferences.edit().putInt("hour", hour).putInt("minute", minute).apply()
    }

    var lastNotifiedDay: String?
        get() = preferences.getString("lastNotifiedDay", null)
        set(value) { preferences.edit().putString("lastNotifiedDay", value).apply() }
}
