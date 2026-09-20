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

    var automaticBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOMATIC_BACKUP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTOMATIC_BACKUP_ENABLED, value).apply()

    var localChangeVersion: Long
        get() = prefs.getLong(KEY_LOCAL_CHANGE_VERSION, 0L)
        set(value) = prefs.edit().putLong(KEY_LOCAL_CHANGE_VERSION, value).apply()

    var uploadedChangeVersion: Long
        get() = prefs.getLong(KEY_UPLOADED_CHANGE_VERSION, 0L)
        set(value) = prefs.edit().putLong(KEY_UPLOADED_CHANGE_VERSION, value).apply()

    var automaticBackupStatus: String
        get() = prefs.getString(KEY_AUTOMATIC_BACKUP_STATUS, STATUS_DISABLED) ?: STATUS_DISABLED
        set(value) = prefs.edit().putString(KEY_AUTOMATIC_BACKUP_STATUS, value).apply()

    var lastAutomaticBackupAt: String?
        get() = prefs.getString(KEY_LAST_AUTOMATIC_BACKUP_AT, null)
        set(value) = prefs.edit().putString(KEY_LAST_AUTOMATIC_BACKUP_AT, value).apply()

    fun markDatabaseChanged(): Long = synchronized(CHANGE_LOCK) {
        val next = localChangeVersion + 1L
        localChangeVersion = next
        next
    }

    fun clearConnection() {
        prefs.edit().clear().apply()
    }

    companion object {
        private val CHANGE_LOCK = Any()
        private const val PREF_NAME = "RentDeskDriveBackup"
        private const val KEY_EMAIL = "connectedEmail"
        private const val KEY_LAST_BACKUP_AT = "lastBackupAt"
        private const val KEY_LAST_SEEN_BACKUP_ID = "lastSeenBackupId"
        private const val KEY_AUTOMATIC_BACKUP_ENABLED = "automaticBackupEnabled"
        private const val KEY_LOCAL_CHANGE_VERSION = "localChangeVersion"
        private const val KEY_UPLOADED_CHANGE_VERSION = "uploadedChangeVersion"
        private const val KEY_AUTOMATIC_BACKUP_STATUS = "automaticBackupStatus"
        private const val KEY_LAST_AUTOMATIC_BACKUP_AT = "lastAutomaticBackupAt"

        const val STATUS_DISABLED = "disabled"
        const val STATUS_IDLE = "idle"
        const val STATUS_PENDING = "pending"
        const val STATUS_RUNNING = "running"
        const val STATUS_AUTHORIZATION_REQUIRED = "authorization_required"
        const val STATUS_CONFLICT = "conflict"
        const val STATUS_FAILED = "failed"
    }
}
