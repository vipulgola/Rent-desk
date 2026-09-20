package com.get.detail.rentdesk.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.get.detail.rentdesk.BuildConfig
import com.get.detail.rentdesk.utils.SessionManager
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class AutoBackupWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    private val preferences = DriveBackupPreferences(appContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!preferences.automaticBackupEnabled) return@withContext Result.success()

        val connectedEmail = preferences.connectedEmail
        if (!connectedEmail.equals(BuildConfig.DRIVE_BACKUP_ACCOUNT, ignoreCase = true)) {
            preferences.automaticBackupStatus =
                DriveBackupPreferences.STATUS_AUTHORIZATION_REQUIRED
            return@withContext Result.failure()
        }

        val changeVersion = preferences.localChangeVersion
        if (changeVersion <= preferences.uploadedChangeVersion) {
            preferences.automaticBackupStatus = DriveBackupPreferences.STATUS_IDLE
            return@withContext Result.success()
        }

        preferences.automaticBackupStatus = DriveBackupPreferences.STATUS_RUNNING
        try {
            val authorization = Tasks.await(
                Identity.getAuthorizationClient(applicationContext).authorize(
                    AuthorizationRequest.builder()
                        .setRequestedScopes(
                            listOf(
                                Scope(Scopes.DRIVE_APPFOLDER),
                                Scope(Scopes.DRIVE_FILE)
                            )
                        )
                        .build()
                )
            )
            if (authorization.hasResolution() || authorization.accessToken.isNullOrBlank()) {
                preferences.automaticBackupStatus =
                    DriveBackupPreferences.STATUS_AUTHORIZATION_REQUIRED
                return@withContext Result.failure()
            }

            val client = GoogleDriveClient(authorization.accessToken!!)
            val account = client.getAccountInfo()
            if (!account.emailAddress.equals(BuildConfig.DRIVE_BACKUP_ACCOUNT, ignoreCase = true)) {
                preferences.automaticBackupStatus =
                    DriveBackupPreferences.STATUS_AUTHORIZATION_REQUIRED
                return@withContext Result.failure()
            }

            val backupManager = LocalBackupManager(applicationContext)
            val localBackup = backupManager.createBackup(
                SessionManager(applicationContext).getMobileNumber().orEmpty()
            )
            val latest = client.listBackups().firstOrNull()
            val backup = if (latest == null || latest.id == preferences.lastSeenBackupId) {
                localBackup
            } else {
                val remoteBackup = backupManager.parseAndValidate(client.download(latest.id))
                backupManager.mergeBackups(localBackup, remoteBackup)
            }
            val uploaded = client.uploadBackup(
                backupManager.backupFileName(backup),
                backupManager.toJsonBytes(backup)
            )
            preferences.lastSeenBackupId = uploaded.id
            preferences.lastBackupAt = uploaded.modifiedTime
            preferences.lastAutomaticBackupAt = uploaded.modifiedTime
            preferences.uploadedChangeVersion = changeVersion

            if (preferences.localChangeVersion > changeVersion) {
                preferences.automaticBackupStatus = DriveBackupPreferences.STATUS_PENDING
                Result.retry()
            } else {
                if (backup.data != localBackup.data) {
                    backupManager.restore(backup)
                }
                preferences.automaticBackupStatus = DriveBackupPreferences.STATUS_IDLE
                Result.success()
            }
        } catch (error: DriveRequestException) {
            when {
                error.statusCode == 401 || error.statusCode == 403 -> {
                    preferences.automaticBackupStatus =
                        DriveBackupPreferences.STATUS_AUTHORIZATION_REQUIRED
                    Result.failure()
                }
                error.statusCode == 408 || error.statusCode == 429 || error.statusCode >= 500 -> {
                    preferences.automaticBackupStatus = DriveBackupPreferences.STATUS_PENDING
                    Result.retry()
                }
                else -> {
                    preferences.automaticBackupStatus = DriveBackupPreferences.STATUS_FAILED
                    Result.failure()
                }
            }
        } catch (error: ApiException) {
            if (
                error.statusCode == CommonStatusCodes.NETWORK_ERROR ||
                error.statusCode == CommonStatusCodes.TIMEOUT
            ) {
                preferences.automaticBackupStatus = DriveBackupPreferences.STATUS_PENDING
                Result.retry()
            } else {
                preferences.automaticBackupStatus =
                    DriveBackupPreferences.STATUS_AUTHORIZATION_REQUIRED
                Result.failure()
            }
        } catch (error: IOException) {
            preferences.automaticBackupStatus = DriveBackupPreferences.STATUS_PENDING
            Result.retry()
        } catch (error: Exception) {
            preferences.automaticBackupStatus = DriveBackupPreferences.STATUS_FAILED
            Result.failure()
        }
    }
}
