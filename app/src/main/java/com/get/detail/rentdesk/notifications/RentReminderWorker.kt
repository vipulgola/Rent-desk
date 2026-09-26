package com.get.detail.rentdesk.notifications

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.get.detail.rentdesk.data.local.AppDatabase
import com.get.detail.rentdesk.domain.usecase.PaymentStatusCalculator
import com.get.detail.rentdesk.domain.usecase.RentPaymentStatus
import com.get.detail.rentdesk.utils.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RentReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val preferences = RentReminderPreferences(applicationContext)
        if (!preferences.enabled || !SessionManager(applicationContext).isLoggedIn()) {
            RentReminderNotifications.cancel(applicationContext)
            return@withContext Result.success()
        }
        val now = Calendar.getInstance()
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
        if (now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) <
            preferences.hour * 60 + preferences.minute ||
            preferences.lastNotifiedDay == day) return@withContext Result.success()
        RentReminderNotifications.createChannel(applicationContext)
        if (!RentReminderNotifications.canNotify(applicationContext)) return@withContext Result.success()

        try {
            val database = AppDatabase.getDatabase(applicationContext)
            val labels = database.withTransaction {
                val transactions = database.transactionDao().getAllTransactions().groupBy { it.propertyId }
                val addresses = database.addressDao().getAllAddressesList().associate { it.dataUUID to it.address }
                database.propertyTenantDao().getAllPropertiesList().filter { property ->
                    val tenant = property.tenantInfo ?: return@filter false
                    // A future tenant must not generate a reminder before their first joining date.
                    val joining = Calendar.getInstance().apply {
                        clear()
                        set(tenant.joiningMonthYear.year, tenant.joiningMonthYear.month - 1, 1)
                        set(Calendar.DAY_OF_MONTH, minOf(
                            tenant.joiningDayOfMonth.coerceIn(1, 31), getActualMaximum(Calendar.DAY_OF_MONTH)
                        ))
                    }
                    !now.before(joining) && PaymentStatusCalculator.currentStatus(
                        property, transactions[property.propertyId].orEmpty(), now
                    ) == RentPaymentStatus.DUE
                }.sortedBy { it.entityName.lowercase() }.map { property ->
                    listOfNotNull(property.entityName, addresses[property.addressId])
                        .joinToString(" · ")
                }
            }
            if (isStopped) return@withContext Result.success()
            if (labels.isEmpty()) {
                RentReminderNotifications.cancel(applicationContext)
            } else {
                RentReminderNotifications.show(applicationContext, labels, day)
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A later periodic run also retries if the database is temporarily unavailable.
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
