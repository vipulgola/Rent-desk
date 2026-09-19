package com.get.detail.rentdesk.ui.transaction

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
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.data.repository.RentRepository
import com.get.detail.rentdesk.databinding.DialogTransactionBinding
import com.get.detail.rentdesk.databinding.FragmentTransactionListBinding
import com.get.detail.rentdesk.domain.usecase.RentCalculator
import com.get.detail.rentdesk.utils.YearMonth
import com.get.detail.rentdesk.viewmodel.TransactionViewModel
import com.get.detail.rentdesk.viewmodel.TransactionViewModelFactory
import kotlinx.coroutines.launch
import java.util.UUID

class TransactionListFragment : Fragment() {
    private var _binding: FragmentTransactionListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransactionViewModel by viewModels {
        val database = AppDatabase.getDatabase(requireContext())
        val repository = RentRepository(database.addressDao(), database.propertyTenantDao(), database.transactionDao())
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
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenu()

        adapter = TransactionAdapter(
            onEditClick = { transaction -> showTransactionDialog(transaction) },
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
                        binding.tvEmptyStateTransactions.visibility = if (transactions.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }

        binding.fabAddTransaction.setOnClickListener {
            showTransactionDialog()
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
                    editModeItem.title = if (adapter.isSelectionMode) "Done" else "Edit"
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_delete_selected -> {
                        showDeleteConfirmationDialog()
                        true
                    }
                    R.id.action_edit_mode -> {
                        adapter.isSelectionMode = !adapter.isSelectionMode
                        true
                    }
                    R.id.action_edit_property -> {
                        val id = propertyId
                        if (id != null) {
                            val bundle = Bundle().apply {
                                putString("propertyId", id)
                            }
                            findNavController().navigate(R.id.action_transactionListFragment_to_tenantDetailsFragment, bundle)
                        }
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showDeleteConfirmationDialog() {
        val selectedCount = adapter.selectedItems.size
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Transactions")
            .setMessage("Are you sure you want to delete $selectedCount selected transaction(s)?")
            .setPositiveButton("Delete") { _, _ ->
                val toDelete = adapter.getSelectedTransactions()
                viewModel.deleteTransactions(toDelete)
                adapter.isSelectionMode = false
                Toast.makeText(requireContext(), "$selectedCount transactions deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTransactionDialog(existing: RecordTransaction? = null) {
        val dialogBinding = DialogTransactionBinding.inflate(layoutInflater)

        existing?.let { transaction ->
            dialogBinding.etMonth.setText(transaction.monthYear.month.toString())
            dialogBinding.etYear.setText(transaction.monthYear.year.toString())
            dialogBinding.etRentAmount.setText(
                (transaction.amountPaid + transaction.balanceAmount).toString()
            )
            dialogBinding.etAmountPaid.setText(transaction.amountPaid.toString())
            dialogBinding.etReading.setText(transaction.reading.toString())
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) R.string.add_transaction else R.string.edit_transaction)
            .setView(dialogBinding.root)
            .setPositiveButton(
                if (existing == null) R.string.add_transaction else R.string.update_transaction,
                null
            )
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialogBinding.tilMonth.error = null
                dialogBinding.tilYear.error = null
                dialogBinding.tilRentAmount.error = null
                dialogBinding.tilAmountPaid.error = null
                dialogBinding.tilReading.error = null

                val month = dialogBinding.etMonth.text?.toString()?.toIntOrNull()
                val year = dialogBinding.etYear.text?.toString()?.toIntOrNull()
                val rentAmount = dialogBinding.etRentAmount.text?.toString()?.toIntOrNull()
                val amountPaid = dialogBinding.etAmountPaid.text?.toString()?.toIntOrNull()
                val reading = dialogBinding.etReading.text?.toString()?.toIntOrNull()

                var isValid = true
                if (month == null || month !in 1..12) {
                    dialogBinding.tilMonth.error = getString(R.string.invalid_month)
                    isValid = false
                }
                if (year == null || year !in 2000..2100) {
                    dialogBinding.tilYear.error = getString(R.string.invalid_year)
                    isValid = false
                }
                if (rentAmount == null || rentAmount <= 0) {
                    dialogBinding.tilRentAmount.error = getString(R.string.invalid_amount)
                    isValid = false
                }
                if (amountPaid == null || amountPaid < 0) {
                    dialogBinding.tilAmountPaid.error = getString(R.string.invalid_amount)
                    isValid = false
                }
                if (reading == null || reading < 0) {
                    dialogBinding.tilReading.error = getString(R.string.invalid_reading)
                    isValid = false
                }
                if (!isValid) return@setOnClickListener

                val id = propertyId ?: return@setOnClickListener
                val validMonth = month ?: return@setOnClickListener
                val validYear = year ?: return@setOnClickListener
                val validRentAmount = rentAmount ?: return@setOnClickListener
                val validAmountPaid = amountPaid ?: return@setOnClickListener
                val validReading = reading ?: return@setOnClickListener
                val balance = RentCalculator().calculateBalance(validRentAmount, validAmountPaid)
                val transaction = RecordTransaction(
                    transactionId = existing?.transactionId ?: UUID.randomUUID().toString(),
                    propertyId = existing?.propertyId ?: id,
                    monthYear = YearMonth(validYear, validMonth),
                    reading = validReading,
                    balanceAmount = balance,
                    amountPaid = validAmountPaid
                )

                if (existing == null) {
                    viewModel.insertTransaction(transaction)
                } else {
                    viewModel.updateTransaction(transaction)
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
