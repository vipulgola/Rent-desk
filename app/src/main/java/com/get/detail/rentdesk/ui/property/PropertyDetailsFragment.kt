package com.get.detail.rentdesk.ui.property

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.data.local.AppDatabase
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.data.repository.RentRepository
import com.get.detail.rentdesk.databinding.DialogEditPropertyBinding
import com.get.detail.rentdesk.databinding.FragmentPropertyDetailsBinding
import com.get.detail.rentdesk.domain.usecase.PaymentStatusCalculator
import com.get.detail.rentdesk.domain.usecase.RentPaymentStatus
import com.get.detail.rentdesk.utils.PaymentUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.text.NumberFormat
import java.util.Locale

class PropertyDetailsFragment : Fragment() {
    private var _binding: FragmentPropertyDetailsBinding? = null
    private val binding get() = _binding!!
    private val propertyId get() = requireArguments().getString("propertyId")!!
    private var currentProperty: PropertyTenantInfo? = null
    private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentPropertyDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val database = AppDatabase.getDatabase(requireContext())
        val id = propertyId
        val details = Bundle().apply { putString("propertyId", id) }
        binding.btnTenantHistory.setOnClickListener {
            com.get.detail.rentdesk.ui.tenant.TenantRecordDialogs.showHistory(this, id)
        }
        binding.cardTenant.setOnClickListener {
            findNavController().navigate(R.id.action_propertyDetailsFragment_to_tenantDetailsFragment, details)
        }
        binding.cardEditProperty.setOnClickListener {
            currentProperty?.let(::showEditProperty)
        }
        binding.cardViewTransactions.setOnClickListener {
            findNavController().navigate(R.id.action_propertyDetailsFragment_to_transactionListFragment, details)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    database.propertyTenantDao().getAllProperties(),
                    database.transactionDao().getAllTransactionsFlow(),
                    database.addressDao().getAllAddresses()
                ) { properties, transactions, addresses ->
                    Triple(properties.firstOrNull { it.propertyId == id }, transactions, addresses)
                }.collect { (property, transactions, addresses) ->
                    if (property == null) {
                        findNavController().navigateUp()
                        return@collect
                    }
                    val address = addresses.firstOrNull { it.dataUUID == property.addressId }?.address
                    render(property, transactions.filter { it.propertyId == id }, address)
                }
            }
        }
    }

    private fun render(
        property: PropertyTenantInfo,
        transactions: List<RecordTransaction>,
        address: String?
    ) {
        currentProperty = property
        (requireActivity() as AppCompatActivity).supportActionBar?.apply {
            title = property.entityName
            subtitle = address
        }
        binding.tvOverviewPropertyName.text = property.entityName
        binding.tvOverviewRent.text = getString(R.string.rent_per_month, money.format(property.monthlyRent))
        val status = PaymentStatusCalculator.currentStatus(property, transactions)
        PaymentUtils.updatePaymentStatusUI(binding.tvOverviewStatus, status)
        val month = PaymentStatusCalculator.billingMonth(property)
        binding.tvOverviewDue.text = if (status == RentPaymentStatus.DUE && month != null) {
            val name = DateFormatSymbols.getInstance(Locale.getDefault()).shortMonths[month.month - 1]
            getString(R.string.due_on_date, property.tenantInfo?.joiningDayOfMonth ?: 1, name, month.year)
        } else ""
        val tenant = property.tenantInfo
        binding.tvTenantName.text = tenant?.name ?: getString(R.string.no_tenant)
        binding.tvTenantPhone.text = tenant?.mobileNumber.orEmpty()
        binding.tvTenantInitial.text = tenant?.name?.firstOrNull()?.uppercase() ?: "?"
        binding.cardViewTransactions.visibility = if (tenant == null) View.GONE else View.VISIBLE
    }

    private fun showEditProperty(property: PropertyTenantInfo) {
        val sheet = DialogEditPropertyBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        sheet.etPropertyName.setText(property.entityName)
        sheet.etMonthlyRent.setText(property.monthlyRent.toString())
        sheet.etElectricityPrice.setText(property.electricityPricePerUnit.toString())
        sheet.etMeterReading.setText(property.meterReading.toString())
        sheet.btnCancelProperty.setOnClickListener { dialog.dismiss() }
        sheet.btnSaveProperty.setOnClickListener {
            val name = sheet.etPropertyName.text?.toString()?.trim().orEmpty()
            val rent = sheet.etMonthlyRent.text?.toString()?.toIntOrNull()
            val rate = sheet.etElectricityPrice.text?.toString()?.toDoubleOrNull()
            val reading = sheet.etMeterReading.text?.toString()?.toIntOrNull()
            when {
                name.isBlank() -> sheet.tilPropertyName.error = getString(R.string.name_required)
                rent == null || rent < 0 -> sheet.tilMonthlyRent.error = getString(R.string.invalid_property_rent)
                rate == null || !rate.isFinite() || rate < 0 ->
                    sheet.tilElectricityPrice.error = getString(R.string.invalid_electricity_price)
                reading == null || reading < 0 ->
                    sheet.tilMeterReading.error = getString(R.string.invalid_property_meter_reading)
                else -> {
                    val database = AppDatabase.getDatabase(requireContext())
                    val repository = RentRepository(requireContext(), database.addressDao(),
                        database.propertyTenantDao(), database.transactionDao())
                    viewLifecycleOwner.lifecycleScope.launch {
                        val latest = repository.getPropertyById(property.propertyId) ?: return@launch
                        repository.updateProperty(latest.copy(
                            entityName = name,
                            monthlyRent = rent,
                            electricityPricePerUnit = rate,
                            meterReading = reading
                        ))
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.setContentView(sheet.root)
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        currentProperty = null
    }
}
