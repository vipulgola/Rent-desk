package com.get.detail.rentdesk.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.animation.ObjectAnimator
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.databinding.FragmentAppLockSetupBinding
import com.get.detail.rentdesk.lock.AppLockManager
import com.get.detail.rentdesk.lock.AppLockSession
import com.get.detail.rentdesk.lock.LockType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppLockSetupFragment : Fragment() {
    private var _binding: FragmentAppLockSetupBinding? = null
    private val binding get() = _binding!!
    private lateinit var lockManager: AppLockManager
    private lateinit var lockType: LockType
    private var firstPattern: List<Int>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppLockSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lockManager = AppLockManager(requireContext())
        lockType = arguments?.getString("lockType")
            ?.let { runCatching { LockType.valueOf(it) }.getOrNull() }
            ?: LockType.PIN

        val usesPin = lockType == LockType.PIN
        binding.pinSetupContainer.visibility = if (usesPin) View.VISIBLE else View.GONE
        binding.patternSetupContainer.visibility = if (usesPin) View.GONE else View.VISIBLE
        binding.tvSetupInstruction.setText(if (usesPin) R.string.enter_pin else R.string.draw_pattern)

        binding.btnSavePin.setOnClickListener { savePin() }
        binding.etConfirmPin.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                savePin()
                true
            } else false
        }
        binding.patternSetupView.onPatternComplete = ::handlePattern
        binding.btnResetPattern.setOnClickListener {
            firstPattern = null
            binding.patternSetupView.clearPattern()
            binding.tvSetupInstruction.setText(R.string.draw_pattern)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun savePin() {
        val pin = binding.etPin.text?.toString().orEmpty()
        val confirmation = binding.etConfirmPin.text?.toString().orEmpty()
        binding.tilPin.error = null
        binding.tilConfirmPin.error = null
        if (pin.length !in 4..6) {
            binding.tilPin.error = getString(R.string.pin_length_error)
            return
        }
        if (pin != confirmation) {
            binding.tilConfirmPin.error = getString(R.string.pin_must_match)
            return
        }
        persistCredential(pin)
    }

    private fun handlePattern(pattern: List<Int>) {
        if (pattern.size < 4) {
            binding.tvSetupInstruction.setText(R.string.pattern_too_short)
            return
        }
        val original = firstPattern
        if (original == null) {
            firstPattern = pattern
            binding.patternSetupView.showSuccess()
            binding.patternSetupView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            binding.tvSetupInstruction.setText(R.string.confirm_pattern)
            binding.patternSetupView.isEnabled = false
            binding.patternSetupView.postDelayed({
                binding.patternSetupView.clearPattern()
                binding.patternSetupView.isEnabled = true
            }, 350)
        } else if (original == pattern) {
            binding.patternSetupView.showSuccess()
            binding.patternSetupView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            persistCredential(pattern.joinToString("-"))
        } else {
            firstPattern = null
            binding.patternSetupView.showError()
            binding.patternSetupView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            shake(binding.patternSetupView)
            binding.tvSetupInstruction.setText(R.string.pattern_does_not_match)
            binding.patternSetupView.isEnabled = false
            binding.patternSetupView.postDelayed({
                binding.patternSetupView.clearPattern()
                binding.patternSetupView.isEnabled = true
            }, 650)
        }
    }

    private fun persistCredential(credential: String) {
        setSetupEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                lockManager.saveCredential(lockType, credential)
            }
            if (lockType == LockType.PATTERN) delay(250)
            AppLockSession.markUnlocked()
            requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            Toast.makeText(requireContext(), R.string.app_lock_enabled, Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    private fun setSetupEnabled(enabled: Boolean) {
        binding.etPin.isEnabled = enabled
        binding.etConfirmPin.isEnabled = enabled
        binding.btnSavePin.isEnabled = enabled
        binding.patternSetupView.isEnabled = enabled
        binding.btnResetPattern.isEnabled = enabled
    }

    private fun shake(view: View) {
        ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, -18f, 18f, -12f, 12f, 0f).apply {
            duration = 360
            start()
        }
    }
}
