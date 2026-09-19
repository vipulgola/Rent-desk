package com.get.detail.rentdesk.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.databinding.FragmentSettingsBinding
import com.get.detail.rentdesk.lock.AppLockManager
import com.get.detail.rentdesk.lock.AppLockSession
import com.get.detail.rentdesk.lock.LockType
import com.get.detail.rentdesk.ui.lock.AppLockActivity

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var lockManager: AppLockManager
    private var updatingUi = false
    private var pendingVerifiedAction: (() -> Unit)? = null

    private val verifyCredential = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        AppLockSession.endInternalAuthentication()
        val action = pendingVerifiedAction
        pendingVerifiedAction = null
        if (result.resultCode == Activity.RESULT_OK) action?.invoke()
        refreshUi()
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
        binding.btnChangeLock.setOnClickListener {
            verifyThen(::chooseLockType)
        }
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

    override fun onResume() {
        super.onResume()
        if (::lockManager.isInitialized) refreshUi()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
    }

    private fun chooseLockType() {
        val labels = arrayOf(
            getString(R.string.lock_method_pin),
            getString(R.string.lock_method_pattern)
        )
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

    private fun canUseStrongBiometrics(): Boolean {
        return BiometricManager.from(requireContext()).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun timeoutLabel(timeout: Long): Int = when (timeout) {
        30_000L -> R.string.lock_after_30_seconds
        60_000L -> R.string.lock_after_1_minute
        300_000L -> R.string.lock_after_5_minutes
        else -> R.string.lock_immediately
    }
}
