package com.get.detail.rentdesk.ui.transaction

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import com.get.detail.rentdesk.domain.usecase.LegacyTransactionHistoryException
import com.get.detail.rentdesk.utils.PaymentDateUtils
import com.get.detail.rentdesk.utils.RentMonthPicker
import com.get.detail.rentdesk.utils.YearMonth
import com.get.detail.rentdesk.domain.usecase.PaymentStatusCalculator
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
    private var pendingQuickCollect = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        propertyId = arguments?.getString("propertyId")
        pendingQuickCollect = savedInstanceState == null && arguments?.getBoolean("collectRent", false) == true

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
                        binding.fabAddTransaction.visibility =
                            if (property?.tenantInfo != null && !adapter.isSelectionMode) View.VISIBLE else View.GONE
                        if (pendingQuickCollect && property?.tenantInfo != null) {
                            pendingQuickCollect = false
                            showNewTransactionDialog(property)
                        }
                        val balanceAmount = property?.balanceAmount ?: 0.0
                        val hasBalance = kotlin.math.abs(balanceAmount) > 0.005
                        binding.balanceCard.visibility =
                            if (hasBalance) View.VISIBLE else View.GONE
                        val isCredit = balanceAmount < -0.005
                        binding.balanceCard.setCardBackgroundColor(
                            ContextCompat.getColor(requireContext(),
                                if (isCredit) R.color.status_paid_container else R.color.status_due_container)
                        )
                        binding.tvBalanceStatus.setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                if (isCredit) R.color.status_paid else R.color.status_due
                            )
                        )
                        binding.tvBalanceLabel.text = getString(
                            if (isCredit) R.string.credit_balance else R.string.outstanding_balance
                        )
                        binding.tvBalanceLabel.setTextColor(ContextCompat.getColor(
                            requireContext(), if (isCredit) R.color.status_paid else R.color.status_due
                        ))
                        binding.tvBalanceStatus.text = money(kotlin.math.abs(balanceAmount))
                        property?.let {
                            (requireActivity() as AppCompatActivity).supportActionBar?.apply {
                                title = getString(R.string.transactions)
                                subtitle = listOfNotNull(it.entityName, it.tenantInfo?.name)
                                    .joinToString(" · ")
                            }
                        }
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
        val selected = adapter.getSelectedTransactions()
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Transactions")
            .setMessage("Are you sure you want to delete $selectedCount selected transaction(s)?")
            .setPositiveButton("Delete") { _, _ ->
                deleteSelectedTransactions(selected)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteSelectedTransactions(
        selected: List<RecordTransaction>,
        manualBalance: Double? = null,
        manualReading: Int? = null
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.deleteTransactions(selected, manualBalance, manualReading)
                adapter.isSelectionMode = false
                Toast.makeText(
                    requireContext(),
                    "${selected.size} transactions deleted",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (_: LegacyTransactionHistoryException) {
                showLegacyDeletionDialog(selected)
            } catch (error: Exception) {
                Toast.makeText(
                    requireContext(),
                    error.message ?: "Could not delete transactions",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showLegacyDeletionDialog(selected: List<RecordTransaction>) {
        val balanceInput = EditText(requireContext()).apply {
            hint = "Correct balance after deletion"
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val readingInput = EditText(requireContext()).apply {
            hint = "Correct meter reading after deletion"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val fields = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
            addView(balanceInput)
            addView(readingInput)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Older transaction")
            .setMessage(
                "This payment was saved before bill details were recorded. " +
                    "Enter the balance and meter reading that should remain after deletion."
            )
            .setView(fields)
            .setPositiveButton("Delete and update", null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val balance = balanceInput.text.toString().trim()
                    .replace(',', '.').toDoubleOrNull()
                val reading = readingInput.text.toString().trim().toIntOrNull()
                if (balance == null || !balance.isFinite()) {
                    balanceInput.error = "Enter a valid balance"
                } else if (reading == null || reading < 0) {
                    readingInput.error = "Enter a valid meter reading"
                } else {
                    dialog.dismiss()
                    deleteSelectedTransactions(selected, balance, reading)
                }
            }
        }
        dialog.show()
    }

    private fun showNewTransactionDialog(property: PropertyTenantInfo) {
        val dialogBinding = DialogTransactionBinding.inflate(layoutInflater)
        dialogBinding.tvDialogTitle.setText(R.string.add_transaction)
        dialogBinding.etRentAmount.setText(
            NumberFormat.getIntegerInstance(Locale("en", "IN")).format(property.monthlyRent)
        )
        dialogBinding.etPaymentDate.setText(PaymentDateUtils.format(PaymentDateUtils.today()))
        setupPaymentDatePicker(dialogBinding)
        val selectedBillingMonth = setupBillingMonthPicker(dialogBinding,
            PaymentStatusCalculator.billingMonth(property) ?: YearMonth.now())
        dialogBinding.etReading.setText(property.meterReading.toString())

        var calculated: RentBreakdown? = null
        var calculatedReading: Int? = null

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialogBinding.btnCloseTransaction.setOnClickListener { dialog.dismiss() }
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
                    if (breakdown.electricityCost > breakdown.consumedUnits * property.electricityPricePerUnit)
                        R.string.bill_breakdown_minimum else R.string.bill_breakdown,
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
                dialogBinding.etAmountReceived.setText(decimalText(breakdown.totalAmount.coerceAtLeast(0.0)))
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
                    amountPaid = amountReceived,
                    billingMonth = selectedBillingMonth(),
                    tenancyId = property.tenantInfo?.tenancyId
                )
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        viewModel.recordPayment(transaction)
                        dialog.dismiss()
                    } catch (error: Exception) {
                        Toast.makeText(
                            requireContext(),
                            error.message ?: "Could not save payment",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
        dialog.show()
        dialogBinding.btnCalculateRate.performClick()
    }

    private fun showEditTransactionDialog(transaction: RecordTransaction) {
        viewLifecycleOwner.lifecycleScope.launch {
            val property = viewModel.getProperty(transaction.propertyId)
            if (property == null) {
                Toast.makeText(requireContext(), R.string.property_not_found, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val dialogBinding = DialogTransactionBinding.inflate(layoutInflater)
            dialogBinding.tvDialogTitle.setText(R.string.edit_transaction)
            dialogBinding.etRentAmount.setText(
                NumberFormat.getIntegerInstance(Locale("en", "IN"))
                    .format(transaction.rentCharged ?: property.monthlyRent.toDouble())
            )
            dialogBinding.etPaymentDate.setText(PaymentDateUtils.format(transaction.paymentDateUtc))
            setupPaymentDatePicker(dialogBinding)
            val selectedBillingMonth = setupBillingMonthPicker(dialogBinding, transaction.rentMonth())
            dialogBinding.etReading.setText(transaction.reading.toString())
            dialogBinding.etReading.isEnabled = false
            dialogBinding.btnCalculateRate.visibility = View.GONE
            dialogBinding.tilAmountReceived.visibility = View.VISIBLE
            dialogBinding.etAmountReceived.setText(decimalText(transaction.amountPaid))

            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogBinding.root)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create()

            dialog.setOnShowListener {
                dialogBinding.btnCloseTransaction.setOnClickListener { dialog.dismiss() }
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
                        billingMonth = selectedBillingMonth(),
                        amountPaid = amountReceived
                    )
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            viewModel.updateRecordedPayment(updated)
                            dialog.dismiss()
                        } catch (error: Exception) {
                            Toast.makeText(
                                requireContext(),
                                error.message ?: "Could not update payment",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            dialog.show()
        }
    }

    private fun setupBillingMonthPicker(dialogBinding: DialogTransactionBinding, initial: YearMonth): () -> YearMonth {
        var selected = initial
        dialogBinding.etBillingMonth.setText(RentMonthPicker.label(selected))
        val openPicker = {
            RentMonthPicker.show(requireContext(), selected) {
                selected = it
                dialogBinding.etBillingMonth.setText(RentMonthPicker.label(it))
            }
        }
        dialogBinding.etBillingMonth.setOnClickListener { openPicker() }
        dialogBinding.tilBillingMonth.setEndIconOnClickListener { openPicker() }
        return { selected }
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
