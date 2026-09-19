package com.get.detail.rentdesk.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.databinding.FragmentLoginBinding
import com.get.detail.rentdesk.utils.SessionManager

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var sessionManager: SessionManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        if (sessionManager.isLoggedIn()) {
            findNavController().navigate(R.id.action_loginFragment_to_addressListFragment)
            return
        }

        binding.btnLogin.setOnClickListener {
            val mobile = binding.etMobile.text.toString()
            if (mobile.length == 10) {
                // Save login session
                sessionManager.saveLoginSession(mobile)
                // Navigate to property list
                findNavController().navigate(R.id.action_loginFragment_to_addressListFragment)
            } else {
                binding.tilMobile.error = "Enter valid 10-digit mobile number"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}