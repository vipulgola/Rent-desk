package com.get.detail.rentdesk.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.ui.lock.AppLockActivity

object RentReminderNotifications {
    const val CHANNEL_ID = "due_rent_reminders"
    private const val NOTIFICATION_ID = 2401
    internal val deliveryLock = Any()

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, context.getString(R.string.rent_reminders_title),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.rent_reminder_channel_description)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun show(context: Context, propertyLabels: List<String>, day: String) {
        synchronized(deliveryLock) {
            val preferences = RentReminderPreferences(context)
            if (!preferences.enabled || preferences.lastNotifiedDay == day ||
                propertyLabels.isEmpty() || !canNotify(context)) return
            val intent = Intent(context, AppLockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentIntent = PendingIntent.getActivity(
                context, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val title = context.resources.getQuantityString(
                R.plurals.rent_reminder_due_count, propertyLabels.size, propertyLabels.size
            )
            val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
            propertyLabels.take(6).forEach { style.addLine(it) }
            if (propertyLabels.size > 6) {
                style.setSummaryText(context.getString(R.string.rent_reminder_more, propertyLabels.size - 6))
            }
            val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_rent_notification)
                .setContentTitle(context.getString(R.string.rent_reminders_title))
                .setContentText(context.getString(R.string.rent_reminder_open_app))
                .build()
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_rent_notification)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.rent_reminder_open_app))
                .setStyle(style)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setNumber(propertyLabels.size)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
                preferences.lastNotifiedDay = day
            } catch (_: SecurityException) {
                // Permission can be revoked between checking it and publishing.
            }
        }
    }
}
