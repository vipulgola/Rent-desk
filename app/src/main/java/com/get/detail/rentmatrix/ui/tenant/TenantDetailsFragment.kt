package com.get.detail.rentmatrix.ui.tenant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.get.detail.rentmatrix.R
import com.get.detail.rentmatrix.data.local.AppDatabase
import com.get.detail.rentmatrix.data.repository.RentRepository
import com.get.detail.rentmatrix.databinding.FragmentTenantDetailsBinding
import com.get.detail.rentmatrix.domain.model.TenantInfo
import com.get.detail.rentmatrix.utils.YearMonth
import com.get.detail.rentmatrix.viewmodel.TenantViewModel
import com.get.detail.rentmatrix.viewmodel.TenantViewModelFactory
import kotlinx.coroutines.launch

class TenantDetailsFragment : Fragment() {
    private var _binding: FragmentTenantDetailsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TenantViewModel by viewModels {
        val database = AppDatabase.getDatabase(requireContext())
        val repository = RentRepository(database.addressDao(), database.propertyTenantDao(), database.transactionDao())
        TenantViewModelFactory(repository)
    }

    private var propertyId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        propertyId = arguments?.getString("propertyId")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTenantDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        propertyId?.let { id ->
            viewModel.getProperty(id)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.property.collect { property ->
                    property?.tenantInfo?.let { info ->
                        binding.etTenantName.setText(info.name)
                        binding.etMobileNumber.setText(info.mobileNumber)
                        binding.etAadhaarNumber.setText(info.aadhaarNumber)
                        binding.etAddress.setText(info.address)
                        binding.btnSaveTenant.text = "Update Tenant"
                    }
                }
            }
        }

        viewModel.navigateToTransactions.observe(viewLifecycleOwner) { id ->
            if (id.isNotEmpty()) {
                val bundle = Bundle().apply {
                    putString("propertyId", id)
                }
                findNavController().navigate(
                    R.id.action_tenantDetailsFragment_to_transactionListFragment,
                    bundle
                )
            }
        }

        binding.btnSaveTenant.setOnClickListener {
            saveTenant()
        }
    }

    private fun saveTenant() {
        val name = binding.etTenantName.text.toString()
        val mobile = binding.etMobileNumber.text.toString()
        val aadhaar = binding.etAadhaarNumber.text.toString()
        val address = binding.etAddress.text.toString()

        if (name.isBlank() || mobile.isBlank() || aadhaar.isBlank() || address.isBlank()) {
            Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val tenantInfo = TenantInfo(
            name = name,
            mobileNumber = mobile,
            joiningMonthYear = YearMonth.now(),
            aadhaarNumber = aadhaar,
            address = address
        )

        propertyId?.let { id ->
            viewModel.updatePropertyWithTenant(id, tenantInfo, YearMonth.now())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}