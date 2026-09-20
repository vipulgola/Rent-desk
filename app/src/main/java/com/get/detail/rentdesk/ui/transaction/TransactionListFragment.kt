package com.get.detail.rentdesk.ui.transaction

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.data.local.AppDatabase
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.data.repository.RentRepository
import com.get.detail.rentdesk.databinding.DialogTransactionBinding
import com.get.detail.rentdesk.databinding.FragmentTransactionListBinding
import com.get.detail.rentdesk.domain.usecase.RentBreakdown
import com.get.detail.rentdesk.domain.usecase.RentCalculator
import com.get.detail.rentdesk.utils.PaymentDateUtils
import com.get.detail.rentdesk.viewmodel.TransactionViewModel
import com.get.detail.rentdesk.viewmodel.TransactionViewModelFactory
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class TransactionListFragment : Fragment() {
    private var _binding: FragmentTransactionListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransactionViewModel by viewModels {
        val database = AppDatabase.getDatabase(requireContext())
        val repository = RentRepository(
            requireContext(),
            database.addressDao(),
            database.propertyTenantDao(),
            database.transactionDao()
        )
        TransactionViewModelFactory(repository)
    }

    private var propertyId: String? = null
    private lateinit var adapter: TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        propertyId = arguments?.getString("propertyId")

        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::adapter.isInitialized && adapter.isSelectionMode) {
                    adapter.isSelectionMode = false
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()

        adapter = TransactionAdapter(
            onEditClick = { transaction -> showEditTransactionDialog(transaction) },
            onSelectionChanged = { isSelectionMode ->
                requireActivity().invalidateOptionsMenu()
                binding.fabAddTransaction.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
            }
        )
        binding.rvTransactions.adapter = adapter

        propertyId?.let { id ->
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.getTransactionsForProperty(id).collect { transactions ->
                        adapter.submitList(transactions)
                        binding.tvEmptyStateTransactions.visibility =
                            if (transactions.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.getPropertyFlow(id).collect { property ->
                        val balanceAmount = property?.balanceAmount ?: 0.0
                        val hasBalance = kotlin.math.abs(balanceAmount) > 0.005
                        binding.tvBalanceStatus.visibility =
                            if (hasBalance) View.VISIBLE else View.GONE
                        val isCredit = balanceAmount < -0.005
                        binding.tvBalanceStatus.setBackgroundResource(
                            if (isCredit) R.color.status_paid_container
                            else R.color.status_due_container
                        )
                        binding.tvBalanceStatus.setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                if (isCredit) R.color.status_paid else R.color.status_due
                            )
                        )
                        binding.tvBalanceStatus.text = getString(
                            R.string.balance_status,
                            money(balanceAmount)
                        )
                    }
                }
            }
        }

        binding.fabAddTransaction.setOnClickListener {
            val id = propertyId ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                val property = viewModel.getProperty(id)
                if (property == null) {
                    Toast.makeText(requireContext(), R.string.property_not_found, Toast.LENGTH_SHORT).show()
                } else {
                    showNewTransactionDialog(property)
                }
            }
        }
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.transaction_list_menu, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                super.onPrepareMenu(menu)
                val deleteItem = menu.findItem(R.id.action_delete_selected)
                val editModeItem = menu.findItem(R.id.action_edit_mode)
                if (::adapter.isInitialized) {
                    deleteItem.isVisible = adapter.isSelectionMode && adapter.selectedItems.isNotEmpty()
                    editModeItem.title = if (adapter.isSelectionMode) getString(R.string.done) else getString(R.string.action_edit)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
                R.id.action_delete_selected -> {
                    showDeleteConfirmationDialog()
                    true
                }
                R.id.action_edit_mode -> {
                    adapter.isSelectionMode = !adapter.isSelectionMode
                    true
                }
                R.id.action_edit_property -> {
                    propertyId?.let { id ->
                        findNavController().navigate(
                            R.id.action_transactionListFragment_to_tenantDetailsFragment,
                            Bundle().apply { putString("propertyId", id) }
                        )
                    }
                    true
                }
                else -> false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showDeleteConfirmationDialog() {
        val selectedCount = adapter.selectedItems.size
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Transactions")
            .setMessage("Are you sure you want to delete $selectedCount selected transaction(s)?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteTransactions(adapter.getSelectedTransactions())
                adapter.isSelectionMode = false
                Toast.makeText(
                    requireContext(),
                    "$selectedCount transactions deleted",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showNewTransactionDialog(property: PropertyTenantInfo) {
        val dialogBinding = DialogTransactionBinding.inflate(layoutInflater)
        dialogBinding.etPaymentDate.setText(PaymentDateUtils.format(PaymentDateUtils.today()))
        setupPaymentDatePicker(dialogBinding)
        dialogBinding.etReading.setText(property.meterReading.toString())

        var calculated: RentBreakdown? = null
        var calculatedReading: Int? = null

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_transaction)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.payment_received, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialogBinding.btnCalculateRate.setOnClickListener {
                dialogBinding.tilReading.error = null
                val currentReading = dialogBinding.etReading.text?.toString()?.trim()?.toIntOrNull()
                if (currentReading == null || currentReading < property.meterReading) {
                    dialogBinding.tilReading.error = getString(
                        R.string.reading_less_than_previous,
                        property.meterReading
                    )
                    return@setOnClickListener
                }

                val breakdown = RentCalculator.calculate(
                    previousReading = property.meterReading,
                    currentReading = currentReading,
                    electricityPricePerUnit = property.electricityPricePerUnit,
                    previousBalance = property.balanceAmount,
                    monthlyRent = property.monthlyRent
                )
                calculated = breakdown
                calculatedReading = currentReading
                dialogBinding.tvCalculationBreakdown.text = getString(
                    R.string.bill_breakdown,
                    currentReading,
                    property.meterReading,
                    money(property.electricityPricePerUnit),
                    money(breakdown.electricityCost),
                    money(breakdown.previousBalance),
                    money(breakdown.rentAmount),
                    money(breakdown.totalAmount)
                )
                dialogBinding.tvCalculationBreakdown.visibility = View.VISIBLE
                dialogBinding.tilAmountReceived.visibility = View.VISIBLE
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialogBinding.tilPaymentDate.error = null
                dialogBinding.tilReading.error = null
                dialogBinding.tilAmountReceived.error = null

                val paymentDate = dialogBinding.etPaymentDate.text?.toString()?.trim().orEmpty()
                val paymentDateUtc = PaymentDateUtils.parse(paymentDate)
                if (paymentDateUtc == null) {
                    dialogBinding.tilPaymentDate.error = getString(R.string.invalid_payment_date)
                    return@setOnClickListener
                }

                val currentReading = dialogBinding.etReading.text?.toString()?.trim()?.toIntOrNull()
                val breakdown = calculated
                if (currentReading == null || currentReading != calculatedReading || breakdown == null) {
                    dialogBinding.tilReading.error = getString(R.string.calculate_before_receiving)
                    return@setOnClickListener
                }

                val amountReceived = dialogBinding.etAmountReceived.text?.toString()
                    ?.trim()
                    ?.replace(',', '.')
                    ?.toDoubleOrNull()
                if (amountReceived == null || amountReceived < 0.0 || !amountReceived.isFinite()) {
                    dialogBinding.tilAmountReceived.error = getString(R.string.invalid_amount)
                    return@setOnClickListener
                }

                val transaction = RecordTransaction(
                    transactionId = UUID.randomUUID().toString(),
                    propertyId = property.propertyId,
                    paymentDateUtc = paymentDateUtc,
                    reading = currentReading,
                    amountPaid = amountReceived
                )
                viewModel.recordPayment(
                    transaction = transaction,
                    meterReading = currentReading,
                    balanceAmount = RentCalculator.remainingBalance(
                        breakdown.totalAmount,
                        amountReceived
                    )
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showEditTransactionDialog(transaction: RecordTransaction) {
        viewLifecycleOwner.lifecycleScope.launch {
            val property = viewModel.getProperty(transaction.propertyId)
            if (property == null) {
                Toast.makeText(requireContext(), R.string.property_not_found, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val dialogBinding = DialogTransactionBinding.inflate(layoutInflater)
            dialogBinding.etPaymentDate.setText(PaymentDateUtils.format(transaction.paymentDateUtc))
            setupPaymentDatePicker(dialogBinding)
            dialogBinding.etReading.setText(transaction.reading.toString())
            dialogBinding.etReading.isEnabled = false
            dialogBinding.btnCalculateRate.visibility = View.GONE
            dialogBinding.tilAmountReceived.visibility = View.VISIBLE
            dialogBinding.etAmountReceived.setText(decimalText(transaction.amountPaid))

            val dialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.edit_transaction)
                .setView(dialogBinding.root)
                .setPositiveButton(R.string.update_transaction, null)
                .setNegativeButton(R.string.cancel, null)
                .create()

            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    dialogBinding.tilPaymentDate.error = null
                    dialogBinding.tilAmountReceived.error = null
                    val paymentDate = dialogBinding.etPaymentDate.text?.toString()?.trim().orEmpty()
                    val paymentDateUtc = PaymentDateUtils.parse(paymentDate)
                    if (paymentDateUtc == null) {
                        dialogBinding.tilPaymentDate.error = getString(R.string.invalid_payment_date)
                        return@setOnClickListener
                    }
                    val amountReceived = dialogBinding.etAmountReceived.text?.toString()
                        ?.trim()
                        ?.replace(',', '.')
                        ?.toDoubleOrNull()
                    if (amountReceived == null || amountReceived < 0.0 || !amountReceived.isFinite()) {
                        dialogBinding.tilAmountReceived.error = getString(R.string.invalid_amount)
                        return@setOnClickListener
                    }

                    val updated = transaction.copy(
                        paymentDateUtc = paymentDateUtc,
                        amountPaid = amountReceived
                    )
                    viewModel.updateRecordedPayment(
                        transaction = updated,
                        balanceDelta = transaction.amountPaid - amountReceived
                    )
                    dialog.dismiss()
                }
            }
            dialog.show()
        }
    }

    private fun setupPaymentDatePicker(dialogBinding: DialogTransactionBinding) {
        val openPicker = { showPaymentDatePicker(dialogBinding) }
        dialogBinding.etPaymentDate.setOnClickListener { openPicker() }
        dialogBinding.tilPaymentDate.setEndIconOnClickListener { openPicker() }
    }

    private fun showPaymentDatePicker(dialogBinding: DialogTransactionBinding) {
        val initialDateUtc = PaymentDateUtils.parse(
            dialogBinding.etPaymentDate.text?.toString()?.trim().orEmpty()
        ) ?: PaymentDateUtils.today()
        val initialDate = PaymentDateUtils.toCalendar(initialDateUtc)

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedDateUtc = PaymentDateUtils.fromDateParts(
                    year,
                    month + 1,
                    dayOfMonth
                )
                dialogBinding.etPaymentDate.setText(PaymentDateUtils.format(selectedDateUtc))
                dialogBinding.tilPaymentDate.error = null
            },
            initialDate.get(Calendar.YEAR),
            initialDate.get(Calendar.MONTH),
            initialDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun money(value: Double): String = CURRENCY_FORMAT.format(value)

    private fun decimalText(value: Double): String = if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        value.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    }
}
