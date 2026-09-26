package com.get.detail.rentdesk.ui.collection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.data.local.AppDatabase
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.data.repository.RentRepository
import com.get.detail.rentdesk.databinding.FragmentMonthlyCollectionBinding
import com.get.detail.rentdesk.domain.usecase.MonthlyCollectionCalculator
import com.get.detail.rentdesk.utils.RentMonthPicker
import com.get.detail.rentdesk.utils.YearMonth
import com.get.detail.rentdesk.viewmodel.PropertyViewModel
import com.get.detail.rentdesk.viewmodel.PropertyViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class MonthlyCollectionFragment : Fragment() {
    private var _binding: FragmentMonthlyCollectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PropertyViewModel by viewModels {
        val database = AppDatabase.getDatabase(requireContext())
        val repository = RentRepository(
            requireContext(), database.addressDao(), database.propertyTenantDao(), database.transactionDao()
        )
        PropertyViewModelFactory(repository)
    }

    private var selectedMonth = YearMonth.now()
    private var properties: List<PropertyTenantInfo> = emptyList()
    private var transactions: List<RecordTransaction> = emptyList()
    private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            val year = savedInstanceState.getInt(STATE_YEAR, selectedMonth.year)
            val month = savedInstanceState.getInt(STATE_MONTH, selectedMonth.month)
            if (year in 1900..2200 && month in 1..12) selectedMonth = YearMonth(year, month)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMonthlyCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnSummaryMonth.setOnClickListener {
            RentMonthPicker.show(requireContext(), selectedMonth, ::selectMonth)
        }
        binding.btnPreviousMonth.setOnClickListener { changeMonth(-1) }
        binding.btnNextMonth.setOnClickListener { changeMonth(1) }
        binding.btnCollectionHelp.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.collection_help_title)
                .setMessage(R.string.collection_help_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        renderCollection()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.allProperties,
                    viewModel.allTransactions,
                    AppDatabase.getDatabase(requireContext()).addressDao().getAllAddresses()
                ) { allProperties, allTransactions, addresses ->
                    Triple(allProperties, allTransactions, addresses)
                }.collect { (allProperties, allTransactions, addresses) ->
                    val addressId = arguments?.getString("addressId")
                    properties = if (addressId == null) allProperties else
                        allProperties.filter { it.addressId == addressId }
                    transactions = allTransactions
                    (requireActivity() as AppCompatActivity).supportActionBar?.subtitle =
                        addresses.firstOrNull { it.dataUUID == addressId }?.address
                    renderCollection()
                }
            }
        }
    }

    private fun selectMonth(month: YearMonth) {
        selectedMonth = month
        renderCollection()
    }

    private fun changeMonth(delta: Int) {
        val index = selectedMonth.year * 12 + selectedMonth.month - 1 + delta
        val next = YearMonth(index / 12, index % 12 + 1)
        if (next.year in 1900..2200) selectMonth(next)
    }

    private fun renderCollection() = with(binding) {
        val summary = MonthlyCollectionCalculator.calculate(properties, transactions, selectedMonth)
        btnSummaryMonth.text = RentMonthPicker.label(selectedMonth)
        btnPreviousMonth.isEnabled = selectedMonth != YearMonth(1900, 1)
        btnNextMonth.isEnabled = selectedMonth != YearMonth(2200, 12)
        tvMonthlyExpected.text = money.format(summary.expected)
        tvMonthlyCollected.text = money.format(summary.collected)
        tvMonthlyPending.text = money.format(summary.pending)
        tvOpeningBalance.text = money.format(summary.openingBalance)
        tvRentTotal.text = money.format(summary.rent)
        tvElectricityTotal.text = money.format(summary.electricity)
        tvCreditTotal.text = money.format(summary.credit)
        rowCredit.visibility = if (summary.credit > 0.0) View.VISIBLE else View.GONE
        tvMonthlyEstimate.visibility = if (summary.estimated) View.VISIBLE else View.GONE

        // Use dues covered, so one tenant's advance cannot hide another tenant's unpaid rent.
        val coveredFraction = if (summary.expected > 0.0) {
            ((summary.expected - summary.pending) / summary.expected).coerceIn(0.0, 1.0)
        } else 0.0
        collectionProgress.progress = (coveredFraction * 1000).toInt()
        tvCollectionProgress.text = NumberFormat.getPercentInstance().apply {
            maximumFractionDigits = if (coveredFraction > 0.0 && coveredFraction < 1.0) 1 else 0
            roundingMode = java.math.RoundingMode.DOWN
        }.format(coveredFraction)
        collectionProgress.contentDescription = getString(
            R.string.collection_progress_description, tvCollectionProgress.text
        )
        tvCollectionStatus.setText(when {
            summary.expected <= 0.0 -> R.string.collection_nothing_due
            summary.pending <= 0.0 -> R.string.collection_all_received
            summary.collected <= 0.0 -> R.string.collection_not_started
            else -> R.string.collection_in_progress
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_YEAR, selectedMonth.year)
        outState.putInt(STATE_MONTH, selectedMonth.month)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val STATE_YEAR = "collectionYear"
        private const val STATE_MONTH = "collectionMonth"
    }
}
