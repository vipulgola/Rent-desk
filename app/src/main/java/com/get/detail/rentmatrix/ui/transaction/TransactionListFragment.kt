package com.get.detail.rentmatrix.ui.transaction

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
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
import com.get.detail.rentmatrix.R
import com.get.detail.rentmatrix.data.local.AppDatabase
import com.get.detail.rentmatrix.data.local.entity.RecordTransaction
import com.get.detail.rentmatrix.data.repository.RentRepository
import com.get.detail.rentmatrix.databinding.FragmentTransactionListBinding
import com.get.detail.rentmatrix.domain.usecase.RentCalculator
import com.get.detail.rentmatrix.utils.YearMonth
import com.get.detail.rentmatrix.viewmodel.TransactionViewModel
import com.get.detail.rentmatrix.viewmodel.TransactionViewModelFactory
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

        adapter = TransactionAdapter { isSelectionMode ->
            requireActivity().invalidateOptionsMenu()
            binding.fabAddTransaction.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
        }
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
            showAddTransactionDialog()
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

    private fun showAddTransactionDialog() {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val etMonth = EditText(context).apply { hint = "Month (1-12)" }
        val etYear = EditText(context).apply { hint = "Year (YYYY)" }
        val etRentAmount = EditText(context).apply { hint = "Rent Amount" }
        val etAmountPaid = EditText(context).apply { hint = "Amount Paid" }
        val etReading = EditText(context).apply { hint = "Reading" }

        layout.addView(etMonth)
        layout.addView(etYear)
        layout.addView(etRentAmount)
        layout.addView(etAmountPaid)
        layout.addView(etReading)

        AlertDialog.Builder(context)
            .setTitle("Add Transaction")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val month = etMonth.text.toString().toIntOrNull() ?: 0
                val year = etYear.text.toString().toIntOrNull() ?: 0
                val rentAmount = etRentAmount.text.toString().toIntOrNull() ?: 0
                val amountPaid = etAmountPaid.text.toString().toIntOrNull() ?: 0
                val reading = etReading.text.toString().toIntOrNull() ?: 0

                if (month in 1..12 && year > 2000) {
                    val yearMonth = YearMonth(year, month)
                    val calculator = RentCalculator()
                    val balance = calculator.calculateBalance(rentAmount, amountPaid)
                    
                    propertyId?.let { id ->
                        val transaction = RecordTransaction(
                            transactionId = UUID.randomUUID().toString(),
                            propertyId = id,
                            monthYear = yearMonth,
                            reading = reading,
                            balanceAmount = balance,
                            amountPaid = amountPaid
                        )
                        viewModel.insertTransaction(transaction)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}