package com.get.detail.rentdesk.ui.settings

import android.app.Activity
import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.get.detail.rentdesk.BuildConfig
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.backup.AutoBackupScheduler
import com.get.detail.rentdesk.backup.BackupEnvelope
import com.get.detail.rentdesk.backup.DriveBackupFile
import com.get.detail.rentdesk.backup.DriveBackupPreferences
import com.get.detail.rentdesk.backup.GoogleDriveClient
import com.get.detail.rentdesk.backup.LocalBackupManager
import com.get.detail.rentdesk.databinding.FragmentSettingsBinding
import com.get.detail.rentdesk.lock.AppLockManager
import com.get.detail.rentdesk.lock.AppLockSession
import com.get.detail.rentdesk.lock.LockType
import com.get.detail.rentdesk.ui.lock.AppLockActivity
import com.get.detail.rentdesk.utils.SessionManager
import com.get.detail.rentdesk.notifications.RentReminderNotifications
import com.get.detail.rentdesk.notifications.RentReminderPreferences
import com.get.detail.rentdesk.notifications.RentReminderScheduler
import java.util.Calendar
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var lockManager: AppLockManager
    private lateinit var drivePreferences: DriveBackupPreferences
    private lateinit var localBackupManager: LocalBackupManager
    private lateinit var autoBackupScheduler: AutoBackupScheduler
    private lateinit var sessionManager: SessionManager
    private var updatingUi = false
    private var updatingDriveUi = false
    private var updatingReminderUi = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val context = context ?: return@registerForActivityResult
        if (granted) {
            RentReminderScheduler(context).enable()
        } else {
            RentReminderScheduler(context).disable()
            Toast.makeText(context, R.string.rent_reminder_permission_needed, Toast.LENGTH_LONG).show()
        }
        refreshReminderUi()
    }
    private var pendingVerifiedAction: (() -> Unit)? = null
    private var pendingDriveAction: DriveAction? = null

    private val verifyCredential = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        AppLockSession.endInternalAuthentication()
        val action = pendingVerifiedAction
        pendingVerifiedAction = null
        if (result.resultCode == Activity.RESULT_OK) action?.invoke()
        refreshUi()
    }

    private val driveAuthorization = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        AppLockSession.endInternalAuthentication()
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            pendingDriveAction = null
            setDriveBusy(false)
            return@registerForActivityResult
        }
        runCatching {
            Identity.getAuthorizationClient(requireActivity())
                .getAuthorizationResultFromIntent(result.data!!)
        }.onSuccess(::handleAuthorizationResult)
            .onFailure(::showDriveError)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDriveAction = savedInstanceState?.getString(STATE_DRIVE_ACTION)
            ?.let { runCatching { DriveAction.valueOf(it) }.getOrNull() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingDriveAction?.let { outState.putString(STATE_DRIVE_ACTION, it.name) }
        super.onSaveInstanceState(outState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lockManager = AppLockManager(requireContext())
        drivePreferences = DriveBackupPreferences(requireContext())
        localBackupManager = LocalBackupManager(requireContext())
        autoBackupScheduler = AutoBackupScheduler(requireContext())
        sessionManager = SessionManager(requireContext())
        setupAppLockSettings()
        setupDriveSettings()
        setupReminderSettings()
    }

    override fun onResume() {
        super.onResume()
        if (::lockManager.isInitialized) refreshUi()
        refreshReminderUi()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupReminderSettings() {
        RentReminderNotifications.createChannel(requireContext())
        binding.switchRentReminders.setOnCheckedChangeListener { _, checked ->
            if (updatingReminderUi) return@setOnCheckedChangeListener
            if (!checked) {
                RentReminderScheduler(requireContext()).disable()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                RentReminderScheduler(requireContext()).enable()
                if (!RentReminderNotifications.canNotify(requireContext())) openNotificationSettings()
            }
            refreshReminderUi()
        }
        binding.rowRentReminderTime.setOnClickListener {
            val preferences = RentReminderPreferences(requireContext())
            TimePickerDialog(requireContext(), { _, hour, minute ->
                preferences.setTime(hour, minute)
                refreshReminderUi()
            }, preferences.hour, preferences.minute, DateFormat.is24HourFormat(requireContext())).show()
        }
        binding.btnRentNotificationSettings.setOnClickListener { openNotificationSettings() }
        refreshReminderUi()
    }

    private fun refreshReminderUi() {
        if (_binding == null) return
        val preferences = RentReminderPreferences(requireContext())
        val allowed = RentReminderNotifications.canNotify(requireContext())
        updatingReminderUi = true
        binding.switchRentReminders.isChecked = preferences.enabled
        updatingReminderUi = false
        val time = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, preferences.hour)
            set(Calendar.MINUTE, preferences.minute)
        }
        val formattedTime = DateFormat.getTimeFormat(requireContext()).format(time.time)
        binding.tvRentReminderTime.text = getString(R.string.rent_reminder_daily_time, formattedTime)
        binding.tvRentReminderStatus.setText(when {
            !allowed -> R.string.rent_reminder_permission_needed
            preferences.enabled -> R.string.rent_reminder_enabled_summary
            else -> R.string.rent_reminder_disabled_summary
        })
        binding.btnRentNotificationSettings.visibility = if (allowed) View.GONE else View.VISIBLE
    }

    private fun openNotificationSettings() {
        val context = requireContext()
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val action = if (androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
            } else {
                Settings.ACTION_APP_NOTIFICATION_SETTINGS
            }
            Intent(action)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, RentReminderNotifications.CHANNEL_ID)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}"))
        }
        startActivity(intent)
    }

    private fun setupAppLockSettings() {
        binding.switchAppLock.setOnCheckedChangeListener { _, checked ->
            if (updatingUi) return@setOnCheckedChangeListener
            if (checked && !lockManager.isEnabled) {
                chooseLockType()
            } else if (!checked && lockManager.isEnabled) {
                verifyThen {
                    lockManager.disable()
                    refreshUi()
                }
            }
            refreshUi()
        }
        binding.rowLockMethod.setOnClickListener {
            if (lockManager.isEnabled) verifyThen(::chooseLockType) else chooseLockType()
        }
        binding.btnChangeLock.setOnClickListener { verifyThen(::chooseLockType) }
        binding.switchBiometrics.setOnCheckedChangeListener { _, checked ->
            if (updatingUi || !lockManager.isEnabled) return@setOnCheckedChangeListener
            verifyThen {
                lockManager.biometricsEnabled = checked
                refreshUi()
            }
            refreshUi()
        }
        binding.rowLockTimeout.setOnClickListener {
            if (lockManager.isEnabled) verifyThen(::chooseTimeout)
        }
    }

    private fun setupDriveSettings() {
        binding.btnBackupMore.setOnClickListener {
            val expanded = binding.backupMoreOptions.visibility != View.VISIBLE
            binding.backupMoreOptions.visibility = if (expanded) View.VISIBLE else View.GONE
            binding.btnBackupMore.setText(if (expanded) R.string.backup_less else R.string.backup_more)
        }
        binding.btnConnectDrive.setOnClickListener { authorizeDrive(DriveAction.CONNECT) }
        binding.btnBackupNow.setOnClickListener { authorizeDrive(DriveAction.BACKUP) }
        binding.btnAutomaticBackup.setOnClickListener { toggleAutomaticBackup() }
        binding.switchDriveBackup.setOnCheckedChangeListener { _, _ ->
            if (!updatingDriveUi) toggleAutomaticBackup()
        }
        binding.btnRestoreBackup.setOnClickListener { authorizeDrive(DriveAction.RESTORE) }
        binding.btnExportReadable.setOnClickListener { authorizeDrive(DriveAction.EXPORT) }
        binding.btnDisconnectDrive.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.disconnect_drive_confirmation)
                .setPositiveButton(R.string.disconnect) { _, _ ->
                    autoBackupScheduler.disable()
                    drivePreferences.clearConnection()
                    refreshDriveUi()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        androidx.work.WorkManager.getInstance(requireContext())
            .getWorkInfosForUniqueWorkLiveData(AutoBackupScheduler.UNIQUE_WORK_NAME)
            .observe(viewLifecycleOwner) { refreshDriveUi() }
    }

    private fun toggleAutomaticBackup() {
        val connected = drivePreferences.connectedEmail.equals(
            BuildConfig.DRIVE_BACKUP_ACCOUNT, ignoreCase = true
        )
        if (!connected) {
            authorizeDrive(DriveAction.ENABLE_AUTOMATIC)
        } else if (drivePreferences.automaticBackupEnabled) {
            autoBackupScheduler.disable()
        } else {
            autoBackupScheduler.enable()
        }
        refreshDriveUi()
    }

    private fun refreshUi() {
        if (_binding == null) return
        updatingUi = true
        val enabled = lockManager.isEnabled
        binding.switchAppLock.isChecked = enabled
        binding.tvLockMethodValue.setText(
            when (lockManager.lockType) {
                LockType.PIN -> R.string.lock_method_pin
                LockType.PATTERN -> R.string.lock_method_pattern
                null -> R.string.lock_method_none
            }
        )
        val biometricAvailable = canUseStrongBiometrics()
        binding.switchBiometrics.isEnabled = enabled && biometricAvailable
        binding.switchBiometrics.isChecked = enabled && biometricAvailable && lockManager.biometricsEnabled
        binding.tvBiometricSummary.visibility = if (biometricAvailable) View.GONE else View.VISIBLE
        binding.rowLockMethod.isEnabled = true
        binding.rowLockTimeout.isEnabled = enabled
        binding.btnChangeLock.isEnabled = enabled
        binding.tvLockTimeoutValue.setText(timeoutLabel(lockManager.timeoutMillis))
        updatingUi = false
        refreshDriveUi()
    }

    private fun refreshDriveUi() {
        if (_binding == null) return
        val connectedEmail = drivePreferences.connectedEmail
        val connected = connectedEmail.equals(BuildConfig.DRIVE_BACKUP_ACCOUNT, ignoreCase = true)
        updatingDriveUi = true
        binding.switchDriveBackup.isChecked = connected && drivePreferences.automaticBackupEnabled
        updatingDriveUi = false
        binding.tvDriveAccount.text = if (connected) {
            getString(R.string.connected_drive_account, connectedEmail)
        } else {
            getString(R.string.drive_not_connected, BuildConfig.DRIVE_BACKUP_ACCOUNT)
        }
        binding.tvBackupMobile.text = getString(
            R.string.backup_mobile,
            sessionManager.getMobileNumber().orEmpty()
        )
        binding.tvLastDriveBackup.text = drivePreferences.lastBackupAt?.let {
            getString(R.string.last_backup, it)
        } ?: getString(R.string.no_backup_yet)
        binding.btnConnectDrive.visibility = if (connected) View.GONE else View.VISIBLE
        binding.btnBackupNow.isEnabled = true
        binding.btnAutomaticBackup.isEnabled = connected
        binding.btnAutomaticBackup.setText(
            if (drivePreferences.automaticBackupEnabled) {
                R.string.disable_automatic_backup
            } else {
                R.string.enable_automatic_backup
            }
        )
        binding.tvAutomaticBackupStatus.text = automaticBackupStatusText()
        binding.btnRestoreBackup.isEnabled = true
        binding.btnExportReadable.isEnabled = connected
        binding.btnDisconnectDrive.visibility = if (connected) View.VISIBLE else View.GONE
    }

    private fun authorizeDrive(action: DriveAction) {
        pendingDriveAction = action
        setDriveBusy(true)
        val requestBuilder = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(Scopes.DRIVE_APPFOLDER),
                    Scope(Scopes.DRIVE_FILE)
                )
            )
        if (action == DriveAction.CONNECT || action == DriveAction.ENABLE_AUTOMATIC) {
            requestBuilder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
        }
        val request = requestBuilder.build()
        Identity.getAuthorizationClient(requireActivity())
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        showDriveError(IllegalStateException("Google authorization could not be opened"))
                    } else {
                        setDriveBusy(false)
                        AppLockSession.beginInternalAuthentication()
                        driveAuthorization.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    }
                } else {
                    handleAuthorizationResult(result)
                }
            }
            .addOnFailureListener(::showDriveError)
    }

    private fun handleAuthorizationResult(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank()) {
            showDriveError(IllegalStateException("Google did not return a Drive access token"))
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                setDriveBusy(true)
                val client = GoogleDriveClient(token)
                val account = withContext(Dispatchers.IO) { client.getAccountInfo() }
                if (!account.emailAddress.equals(BuildConfig.DRIVE_BACKUP_ACCOUNT, ignoreCase = true)) {
                    pendingDriveAction = null
                    setDriveBusy(false)
                    Toast.makeText(
                        requireContext(),
                        getString(
                            R.string.wrong_drive_account,
                            BuildConfig.DRIVE_BACKUP_ACCOUNT,
                            account.emailAddress
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                drivePreferences.connectedEmail = account.emailAddress
                if (
                    pendingDriveAction == DriveAction.CONNECT &&
                    drivePreferences.automaticBackupEnabled
                ) {
                    autoBackupScheduler.enqueuePending(immediate = true)
                }
                when (pendingDriveAction) {
                    DriveAction.CONNECT -> {
                        setDriveBusy(false)
                        Toast.makeText(requireContext(), R.string.drive_connected, Toast.LENGTH_SHORT).show()
                    }
                    DriveAction.ENABLE_AUTOMATIC -> {
                        autoBackupScheduler.enable()
                        setDriveBusy(false)
                    }
                    DriveAction.BACKUP -> checkBeforeBackup(client)
                    DriveAction.RESTORE -> loadBackupChoices(client)
                    DriveAction.EXPORT -> exportReadableCopy(client)
                    null -> setDriveBusy(false)
                }
                pendingDriveAction = null
                refreshDriveUi()
            } catch (error: Exception) {
                showDriveError(error)
            }
        }
    }

    private suspend fun checkBeforeBackup(client: GoogleDriveClient) {
        val existing = withContext(Dispatchers.IO) { client.listBackups().firstOrNull() }
        setDriveBusy(false)
        if (existing != null && existing.id != drivePreferences.lastSeenBackupId) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.newer_backup_warning_title)
                .setMessage(R.string.newer_backup_warning_message)
                .setPositiveButton(R.string.upload_anyway) { _, _ -> performBackup(client) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            performBackup(client)
        }
    }

    private fun performBackup(client: GoogleDriveClient) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                setDriveBusy(true)
                val mobile = sessionManager.getMobileNumber().orEmpty()
                val result = withContext(Dispatchers.IO) {
                    val backup = localBackupManager.createBackup(mobile)
                    client.uploadBackup(
                        localBackupManager.backupFileName(backup),
                        localBackupManager.toJsonBytes(backup)
                    )
                }
                drivePreferences.lastSeenBackupId = result.id
                drivePreferences.lastBackupAt = result.modifiedTime
                drivePreferences.uploadedChangeVersion = drivePreferences.localChangeVersion
                if (drivePreferences.automaticBackupEnabled) {
                    drivePreferences.automaticBackupStatus = DriveBackupPreferences.STATUS_IDLE
                }
                Toast.makeText(requireContext(), R.string.backup_complete, Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                showDriveError(error)
            } finally {
                setDriveBusy(false)
                refreshDriveUi()
            }
        }
    }

    private suspend fun loadBackupChoices(client: GoogleDriveClient) {
        val backups = withContext(Dispatchers.IO) { client.listBackups() }
        setDriveBusy(false)
        if (backups.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_drive_backups, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.available_backups)
            .setItems(backups.map(::backupLabel).toTypedArray()) { _, which ->
                prepareRestore(client, backups[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun prepareRestore(client: GoogleDriveClient, file: DriveBackupFile) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                setDriveBusy(true)
                val backup = withContext(Dispatchers.IO) {
                    localBackupManager.parseAndValidate(client.download(file.id))
                }
                setDriveBusy(false)
                val current = withContext(Dispatchers.IO) {
                    localBackupManager.createBackup(sessionManager.getMobileNumber().orEmpty())
                }
                val preview = com.get.detail.rentdesk.databinding.DialogRestorePreviewBinding.inflate(layoutInflater)
                preview.tvRestoreSource.text = getString(R.string.restore_preview_source,
                    backup.data.createdAtUtc, backup.data.sourceDevice, backup.data.updatedByMobile)
                fun totals(data: com.get.detail.rentdesk.backup.BackupData): String {
                    val tenants = data.properties.mapNotNull { it.tenantInfo } +
                        data.properties.flatMap { it.tenantHistory.orEmpty().map { entry -> entry.tenant } }
                    val held = tenants.sumOf { it.depositReceived - it.depositDeductions - it.depositRefunded }
                    return getString(R.string.restore_preview_counts, data.recordCounts.addresses,
                        data.recordCounts.properties, data.recordCounts.transactions,
                        data.properties.count { it.tenantInfo != null },
                        data.properties.sumOf { it.tenantHistory.orEmpty().size },
                        java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "IN")).format(held))
                }
                preview.tvRestoreCurrent.text = totals(current.data)
                preview.tvRestoreBackup.text = totals(backup.data)
                val payments = backup.data.transactions.filterNot { it.isDeleted }
                preview.tvRestoreDates.text = if (payments.isEmpty()) getString(R.string.no_history_payments)
                    else getString(R.string.restore_preview_dates,
                        com.get.detail.rentdesk.utils.PaymentDateUtils.format(payments.minOf { it.paymentDateUtc }),
                        com.get.detail.rentdesk.utils.PaymentDateUtils.format(payments.maxOf { it.paymentDateUtc }))
                AlertDialog.Builder(requireContext()).setTitle(R.string.restore_preview_title)
                    .setView(preview.root)
                    .setPositiveButton(R.string.restore_this_backup) { _, _ -> restoreBackup(file, backup) }
                    .setNegativeButton(R.string.cancel, null).show()
            } catch (error: Exception) {
                showDriveError(error)
            } finally {
                if (_binding != null) setDriveBusy(false)
            }
        }
    }

    private fun restoreBackup(file: DriveBackupFile, backup: BackupEnvelope) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                setDriveBusy(true)
                withContext(Dispatchers.IO) { localBackupManager.restore(backup) }
                drivePreferences.lastSeenBackupId = file.id
                drivePreferences.uploadedChangeVersion = drivePreferences.localChangeVersion
                if (drivePreferences.automaticBackupEnabled) {
                    drivePreferences.automaticBackupStatus = DriveBackupPreferences.STATUS_IDLE
                }
                Toast.makeText(requireContext(), R.string.restore_complete, Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                showDriveError(error)
            } finally {
                setDriveBusy(false)
            }
        }
    }

    private suspend fun exportReadableCopy(client: GoogleDriveClient) {
        val mobile = sessionManager.getMobileNumber().orEmpty()
        withContext(Dispatchers.IO) {
            val backup = localBackupManager.createBackup(mobile)
            client.uploadReadableExport(
                localBackupManager.exportFileName(backup),
                localBackupManager.createReadableZip(backup)
            )
        }
        setDriveBusy(false)
        Toast.makeText(requireContext(), R.string.readable_export_complete, Toast.LENGTH_LONG).show()
    }

    private fun backupLabel(file: DriveBackupFile): String = buildString {
        append(file.name.removePrefix("rentdesk_backup_").removeSuffix(".json"))
        if (file.modifiedTime.isNotBlank()) append("\n${file.modifiedTime}")
    }

    private fun showDriveError(error: Throwable) {
        pendingDriveAction = null
        if (_binding != null) setDriveBusy(false)
        context?.let {
            Toast.makeText(
                it,
                getString(R.string.drive_operation_failed, error.message ?: error.javaClass.simpleName),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setDriveBusy(busy: Boolean) {
        if (_binding == null) return
        binding.progressDrive.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnConnectDrive.isEnabled = !busy
        binding.btnBackupNow.isEnabled = !busy
        binding.btnBackupMore.isEnabled = !busy
        binding.btnAutomaticBackup.isEnabled = !busy && drivePreferences.connectedEmail != null
        binding.btnRestoreBackup.isEnabled = !busy
        binding.btnExportReadable.isEnabled = !busy && drivePreferences.connectedEmail != null
        binding.btnDisconnectDrive.isEnabled = !busy
    }

    private fun automaticBackupStatusText(): String {
        if (!drivePreferences.automaticBackupEnabled) {
            return getString(R.string.automatic_backup_disabled)
        }
        return when (drivePreferences.automaticBackupStatus) {
            DriveBackupPreferences.STATUS_PENDING ->
                getString(R.string.automatic_backup_pending)
            DriveBackupPreferences.STATUS_RUNNING ->
                getString(R.string.automatic_backup_running)
            DriveBackupPreferences.STATUS_AUTHORIZATION_REQUIRED ->
                getString(R.string.automatic_backup_authorization_required)
            DriveBackupPreferences.STATUS_CONFLICT ->
                getString(R.string.automatic_backup_conflict)
            DriveBackupPreferences.STATUS_FAILED ->
                getString(R.string.automatic_backup_failed)
            else -> drivePreferences.lastAutomaticBackupAt?.let {
                getString(R.string.last_automatic_backup, it)
            } ?: getString(R.string.automatic_backup_idle)
        }
    }

    private fun chooseLockType() {
        val labels = arrayOf(getString(R.string.lock_method_pin), getString(R.string.lock_method_pattern))
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.choose_lock_method)
            .setItems(labels) { _, which ->
                val type = if (which == 0) LockType.PIN else LockType.PATTERN
                findNavController().navigate(
                    R.id.action_settingsFragment_to_appLockSetupFragment,
                    Bundle().apply { putString("lockType", type.name) }
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun chooseTimeout() {
        val values = longArrayOf(0L, 30_000L, 60_000L, 300_000L)
        val labels = values.map { getString(timeoutLabel(it)) }.toTypedArray()
        val selected = values.indexOf(lockManager.timeoutMillis).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.lock_timeout)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                lockManager.timeoutMillis = values[which]
                dialog.dismiss()
                refreshUi()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun verifyThen(action: () -> Unit) {
        if (!lockManager.isEnabled) {
            action()
            return
        }
        pendingVerifiedAction = action
        AppLockSession.beginInternalAuthentication()
        verifyCredential.launch(
            Intent(requireContext(), AppLockActivity::class.java)
                .putExtra(AppLockActivity.EXTRA_VERIFICATION_ONLY, true)
        )
    }

    private fun canUseStrongBiometrics(): Boolean = BiometricManager.from(requireContext())
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        BiometricManager.BIOMETRIC_SUCCESS

    private fun timeoutLabel(timeout: Long): Int = when (timeout) {
        30_000L -> R.string.lock_after_30_seconds
        60_000L -> R.string.lock_after_1_minute
        300_000L -> R.string.lock_after_5_minutes
        else -> R.string.lock_immediately
    }

    private enum class DriveAction {
        CONNECT,
        ENABLE_AUTOMATIC,
        BACKUP,
        RESTORE,
        EXPORT
    }

    companion object {
        private const val STATE_DRIVE_ACTION = "driveAction"
    }
}
