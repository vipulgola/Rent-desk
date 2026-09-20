package com.get.detail.rentdesk.backup

import android.content.Context

class DriveBackupPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var connectedEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    var lastBackupAt: String?
        get() = prefs.getString(KEY_LAST_BACKUP_AT, null)
        set(value) = prefs.edit().putString(KEY_LAST_BACKUP_AT, value).apply()

    var lastSeenBackupId: String?
        get() = prefs.getString(KEY_LAST_SEEN_BACKUP_ID, null)
        set(value) = prefs.edit().putString(KEY_LAST_SEEN_BACKUP_ID, value).apply()

    fun clearConnection() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREF_NAME = "RentDeskDriveBackup"
        private const val KEY_EMAIL = "connectedEmail"
        private const val KEY_LAST_BACKUP_AT = "lastBackupAt"
        private const val KEY_LAST_SEEN_BACKUP_ID = "lastSeenBackupId"
    }
}
