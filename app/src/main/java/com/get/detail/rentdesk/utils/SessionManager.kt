package com.get.detail.rentdesk.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "RentDeskPrefs"
        private const val IS_LOGGED_IN = "isLoggedIn"
        private const val MOBILE_NUMBER = "mobileNumber"
    }

    fun saveLoginSession(mobile: String) {
        prefs.edit().apply {
            putBoolean(IS_LOGGED_IN, true)
            putString(MOBILE_NUMBER, mobile)
            apply()
        }
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(IS_LOGGED_IN, false)
    }

    fun getMobileNumber(): String? {
        return prefs.getString(MOBILE_NUMBER, null)
    }

    fun logout() {
        prefs.edit().clear().apply()
    }
}