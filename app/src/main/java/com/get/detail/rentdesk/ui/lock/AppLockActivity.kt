package com.get.detail.rentdesk.ui.lock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.animation.ObjectAnimator
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.databinding.ActivityAppLockBinding
import com.get.detail.rentdesk.lock.AppLockManager
import com.get.detail.rentdesk.lock.AppLockSession
import com.get.detail.rentdesk.lock.LockType
import com.get.detail.rentdesk.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppLockActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppLockBinding
    private lateinit var lockManager: AppLockManager
    private var lockoutTimer: CountDownTimer? = null
    private var verificationInProgress = false
    private val verificationOnly by lazy { intent.getBooleanExtra(EXTRA_VERIFICATION_ONLY, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityAppLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lockManager = AppLockManager(this)

        if (!lockManager.isEnabled) {
            if (verificationOnly) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            } else {
                continueToApp()
            }
            return
        }

        configureCredentialUi()
        binding.btnBiometric.setOnClickListener { showBiometricPrompt() }
        binding.etUnlockPin.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                verifyPin()
                true
            } else {
                false
            }
        }
        binding.btnUnlock.setOnClickListener { verifyPin() }
        binding.patternUnlockView.onPatternComplete = { pattern ->
            verify(pattern.joinToString("-"))
        }

        showLockoutIfNeeded()
        if (savedInstanceState == null && canUseBiometrics()) {
            binding.root.post { showBiometricPrompt() }
        }
    }

    override fun onDestroy() {
        lockoutTimer?.cancel()
        super.onDestroy()
    }

    private fun configureCredentialUi() {
        val usesPin = lockManager.lockType == LockType.PIN
        binding.tilUnlockPin.visibility = if (usesPin) View.VISIBLE else View.GONE
        binding.btnUnlock.visibility = if (usesPin) View.VISIBLE else View.GONE
        binding.patternUnlockView.visibility = if (usesPin) View.GONE else View.VISIBLE
        binding.tvLockMessage.setText(if (usesPin) R.string.enter_your_pin else R.string.draw_your_pattern)
        binding.btnBiometric.visibility = if (canUseBiometrics()) View.VISIBLE else View.GONE
    }

    private fun verifyPin() {
        val pin = binding.etUnlockPin.text?.toString().orEmpty()
        if (pin.isNotEmpty()) verify(pin)
    }

    private fun verify(credential: String) {
        if (verificationInProgress) return
        val remaining = lockManager.remainingLockoutSeconds()
        if (remaining > 0) {
            showLockout(remaining)
            return
        }
        verificationInProgress = true
        setCredentialInputEnabled(false)
        binding.progressUnlock.visibility = View.VISIBLE
        binding.tvLockMessage.setText(R.string.checking_lock)

        lifecycleScope.launch {
            val verified = withContext(Dispatchers.Default) {
                lockManager.verifyCredential(credential)
            }
            binding.progressUnlock.visibility = View.GONE
            if (verified) {
                lockManager.clearFailedAttempts()
                binding.patternUnlockView.showSuccess()
                binding.patternUnlockView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                delay(250)
                authenticationSucceeded()
            } else {
                binding.patternUnlockView.showError()
                binding.patternUnlockView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                shake(
                    if (lockManager.lockType == LockType.PIN) binding.tilUnlockPin
                    else binding.patternUnlockView
                )
                val lockoutSeconds = lockManager.recordFailedAttempt()
                if (lockoutSeconds > 0) {
                    showLockout(lockoutSeconds)
                } else {
                    binding.tvLockMessage.setText(
                        if (lockManager.lockType == LockType.PIN) R.string.incorrect_pin
                        else R.string.incorrect_pattern
                    )
                    delay(550)
                    binding.patternUnlockView.clearPattern()
                    setCredentialInputEnabled(true)
                }
                binding.etUnlockPin.text?.clear()
            }
            verificationInProgress = false
        }
    }

    private fun shake(view: View) {
        ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, -18f, 18f, -12f, 12f, 0f).apply {
            duration = 360
            start()
        }
    }

    private fun canUseBiometrics(): Boolean {
        if (!lockManager.biometricsEnabled) return false
        return BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showBiometricPrompt() {
        if (!canUseBiometrics()) return
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    lockManager.clearFailedAttempts()
                    authenticationSucceeded()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    binding.tvLockMessage.setText(R.string.verify_to_continue)
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(getString(R.string.use_pin_or_pattern))
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun authenticationSucceeded() {
        if (verificationOnly) {
            setResult(Activity.RESULT_OK)
            finish()
        } else {
            AppLockSession.markUnlocked()
            continueToApp()
        }
    }

    private fun continueToApp() {
        if (intent.getBooleanExtra(EXTRA_RETURN_TO_CALLER, false)) {
            finish()
        } else {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
            finish()
        }
    }

    private fun showLockoutIfNeeded() {
        lockManager.remainingLockoutSeconds().takeIf { it > 0 }?.let(::showLockout)
    }

    private fun showLockout(seconds: Long) {
        lockoutTimer?.cancel()
        setCredentialInputEnabled(false)
        lockoutTimer = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = (millisUntilFinished + 999) / 1000
                binding.tvLockMessage.text = getString(R.string.too_many_attempts, remaining)
            }

            override fun onFinish() {
                binding.patternUnlockView.clearPattern()
                setCredentialInputEnabled(true)
                configureCredentialUi()
            }
        }.start()
    }

    private fun setCredentialInputEnabled(enabled: Boolean) {
        binding.etUnlockPin.isEnabled = enabled
        binding.btnUnlock.isEnabled = enabled
        binding.patternUnlockView.isEnabled = enabled
        binding.btnBiometric.isEnabled = enabled
    }

    companion object {
        const val EXTRA_VERIFICATION_ONLY = "verificationOnly"
        const val EXTRA_RETURN_TO_CALLER = "returnToCaller"
    }
}
