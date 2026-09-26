package com.get.detail.rentdesk.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class RentReminderScheduler(context: Context) {
    private val appContext = context.applicationContext

    fun enable() {
        RentReminderPreferences(appContext).enabled = true
        ensureScheduled()
    }

    fun disable() {
        // Serialize disabling with publishing so an in-flight worker cannot leave an alert behind.
        synchronized(RentReminderNotifications.deliveryLock) {
            RentReminderPreferences(appContext).enabled = false
            RentReminderNotifications.cancel(appContext)
        }
        WorkManager.getInstance(appContext).cancelUniqueWork(WORK_NAME)
    }

    fun ensureScheduled() {
        if (!RentReminderPreferences(appContext).enabled) return
        RentReminderNotifications.createChannel(appContext)
        // Check local clock/preferences frequently; delivery is limited to once per local day.
        // WorkManager persists this schedule across process death and device restarts.
        val request = PeriodicWorkRequestBuilder<RentReminderWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    companion object {
        private const val WORK_NAME = "rentdesk-due-rent-reminder"
    }
}
