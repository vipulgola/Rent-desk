package com.get.detail.rentdesk.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class AutoBackupScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = DriveBackupPreferences(appContext)
    private val workManager = WorkManager.getInstance(appContext)

    fun databaseChanged() {
        preferences.markDatabaseChanged()
        if (preferences.automaticBackupEnabled) enqueuePending()
    }

    fun enable() {
        preferences.automaticBackupEnabled = true
        preferences.markDatabaseChanged()
        enqueuePending(immediate = true)
    }

    fun disable() {
        preferences.automaticBackupEnabled = false
        preferences.automaticBackupStatus = DriveBackupPreferences.STATUS_DISABLED
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun enqueuePending(immediate: Boolean = false) {
        if (!preferences.automaticBackupEnabled) return
        preferences.automaticBackupStatus = DriveBackupPreferences.STATUS_PENDING
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(if (immediate) 0L else DEBOUNCE_SECONDS, TimeUnit.SECONDS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                MIN_BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "rentdesk-automatic-drive-backup"
        const val WORK_TAG = "rentdesk-auto-backup"
        private const val DEBOUNCE_SECONDS = 45L
        private const val MIN_BACKOFF_SECONDS = 30L
    }
}
