package com.get.detail.rentdesk.ui.tenant

import android.os.Bundle
import android.app.DatePickerDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.data.local.AppDatabase
import com.get.detail.rentdesk.data.repository.RentRepository
import com.get.detail.rentdesk.databinding.FragmentTenantDetailsBinding
import com.get.detail.rentdesk.domain.model.TenantInfo
import com.get.detail.rentdesk.utils.YearMonth
import com.get.detail.rentdesk.viewmodel.TenantViewModel
import com.get.detail.rentdesk.viewmodel.TenantViewModelFactory
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class TenantDetailsFragment : Fragment() {
    private var _binding: FragmentTenantDetailsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TenantViewModel by viewModels {
        val database = AppDatabase.getDatabase(requireContext())
        val repository = RentRepository(requireContext(), database.addressDao(), database.propertyTenantDao(), database.transactionDao())
        TenantViewModelFactory(repository)
    }

    private var propertyId: String? = null
    private var joiningYear: Int? = null
    private var joiningMonth: Int? = null
    private var joiningDay: Int? = null

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

        val today = Calendar.getInstance()
        setJoiningDate(
            year = today.get(Calendar.YEAR),
            month = today.get(Calendar.MONTH) + 1,
            day = today.get(Calendar.DAY_OF_MONTH)
        )

        binding.etJoiningDate.setOnClickListener { showJoiningDatePicker() }
        binding.tilJoiningDate.setEndIconOnClickListener { showJoiningDatePicker() }

        propertyId?.let { id ->
            viewModel.getProperty(id)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.property.collect { property ->
                    binding.btnVacateProperty.visibility =
                        if (property?.tenantInfo != null) View.VISIBLE else View.GONE
                    property?.let {
                        binding.etMonthlyRent.setText(
                            it.monthlyRent.takeIf { value -> value > 0 }?.toString().orEmpty()
                        )
                        binding.etElectricityPrice.setText(
                            formatElectricityPrice(it.electricityPricePerUnit)
                        )
                        binding.etMeterReading.setText(it.meterReading.toString())
                    }
                    property?.tenantInfo?.let { info ->
                        binding.etTenantName.setText(info.name)
                        binding.etMobileNumber.setText(info.mobileNumber)
                        setJoiningDate(
                            year = info.joiningMonthYear.year,
                            month = info.joiningMonthYear.month,
                            day = info.joiningDayOfMonth.takeIf { it in 1..31 } ?: 1
                        )
                        binding.etAadhaarNumber.setText(info.aadhaarNumber)
                        binding.etAddress.setText(info.address)
                        binding.btnSaveTenant.setText(R.string.update_tenant)
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.propertyVacated.collect {
                    findNavController().navigateUp()
                }
            }
        }

        binding.btnSaveTenant.setOnClickListener {
            saveTenant()
        }
        binding.btnVacateProperty.setOnClickListener {
            showVacateConfirmation()
        }
    }

    private fun saveTenant() {
        val name = binding.etTenantName.text.toString()
        val mobile = binding.etMobileNumber.text.toString()
        val aadhaar = binding.etAadhaarNumber.text.toString()
        val address = binding.etAddress.text.toString()
        val monthlyRent = binding.etMonthlyRent.text?.toString()?.trim()?.toIntOrNull()
        val electricityPrice = binding.etElectricityPrice.text?.toString()
            ?.trim()
            ?.replace(',', '.')
            ?.toDoubleOrNull()
        val meterReading = binding.etMeterReading.text?.toString()?.trim()?.toIntOrNull()

        val selectedYear = joiningYear
        val selectedMonth = joiningMonth
        val selectedDay = joiningDay

        if (name.isBlank() || mobile.isBlank() || aadhaar.isBlank() || address.isBlank()) {
            Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedYear == null || selectedMonth == null || selectedDay == null) {
            binding.tilJoiningDate.error = getString(R.string.joining_date_required)
            return
        }
        binding.tilJoiningDate.error = null
        binding.tilMonthlyRent.error = null
        binding.tilElectricityPrice.error = null
        binding.tilMeterReading.error = null
        if (monthlyRent == null || monthlyRent <= 0) {
            binding.tilMonthlyRent.error = getString(R.string.invalid_monthly_rent)
            return
        }
        if (electricityPrice == null || electricityPrice < 0.0 || !electricityPrice.isFinite()) {
            binding.tilElectricityPrice.error = getString(R.string.invalid_electricity_price)
            return
        }
        if (meterReading == null || meterReading < 0) {
            binding.tilMeterReading.error = getString(R.string.invalid_property_meter_reading)
            return
        }

        val tenantInfo = TenantInfo(
            name = name,
            mobileNumber = mobile,
            joiningMonthYear = YearMonth(selectedYear, selectedMonth),
            joiningDayOfMonth = selectedDay,
            aadhaarNumber = aadhaar,
            address = address
        )

        propertyId?.let { id ->
            viewModel.updatePropertyWithTenant(
                id,
                tenantInfo,
                monthlyRent,
                electricityPrice,
                meterReading
            )
        }
    }

    private fun formatElectricityPrice(value: Double): String = when {
        value <= 0.0 -> ""
        value % 1.0 == 0.0 -> value.toLong().toString()
        else -> value.toString()
    }

    private fun showJoiningDatePicker() {
        val today = Calendar.getInstance()
        val initialYear = joiningYear ?: today.get(Calendar.YEAR)
        val initialMonth = (joiningMonth ?: today.get(Calendar.MONTH) + 1) - 1
        val initialDay = joiningDay ?: today.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            requireContext(),
            { _, year, zeroBasedMonth, dayOfMonth ->
                setJoiningDate(year, zeroBasedMonth + 1, dayOfMonth)
            },
            initialYear,
            initialMonth,
            initialDay
        ).apply {
            datePicker.maxDate = today.timeInMillis
            show()
        }
    }

    private fun setJoiningDate(year: Int, month: Int, day: Int) {
        joiningYear = year
        joiningMonth = month
        joiningDay = day
        binding.etJoiningDate.setText(
            String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month, year)
        )
        binding.tilJoiningDate.error = null
    }

    private fun showVacateConfirmation() {
        val id = propertyId ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.vacate_property_title)
            .setMessage(R.string.vacate_property_message)
            .setPositiveButton(R.string.confirm_vacate) { _, _ ->
                viewModel.vacateProperty(id)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
