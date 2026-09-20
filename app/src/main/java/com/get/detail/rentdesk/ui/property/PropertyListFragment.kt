package com.get.detail.rentdesk.ui.property

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
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
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.repository.RentRepository
import com.get.detail.rentdesk.databinding.FragmentPropertyListBinding
import com.get.detail.rentdesk.domain.usecase.PaymentStatusCalculator
import com.get.detail.rentdesk.viewmodel.PropertyViewModel
import com.get.detail.rentdesk.viewmodel.PropertyViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class PropertyListFragment : Fragment() {
    private var _binding: FragmentPropertyListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PropertyViewModel by viewModels {
        val database = AppDatabase.getDatabase(requireContext())
        val repository = RentRepository(requireContext(), database.addressDao(), database.propertyTenantDao(), database.transactionDao())
        PropertyViewModelFactory(repository)
    }

    private lateinit var adapter: PropertyAdapter
    private var pendingExportData: String? = null

    private val createCsvFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { saveFile(it, pendingExportData ?: "") }
    }

    private val createJsonFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { saveFile(it, pendingExportData ?: "") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
        _binding = FragmentPropertyListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenu()

        adapter = PropertyAdapter(
            onClick = { property ->
                val bundle = Bundle().apply {
                    putString("propertyId", property.propertyId)
                }
                if (property.tenantInfo == null) {
                    findNavController().navigate(R.id.action_propertyListFragment_to_tenantDetailsFragment, bundle)
                } else {
                    findNavController().navigate(R.id.action_propertyListFragment_to_transactionListFragment, bundle)
                }
            },
            onEdit = { property -> showPropertyDialog(property) },
            onSelectionChanged = { isSelectionMode ->
                requireActivity().invalidateOptionsMenu()
                binding.fabAddProperty.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
            }
        )
        binding.rvProperties.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allProperties.combine(viewModel.allTransactions) { properties, transactions ->
                    properties to transactions
                }.collect { (properties, transactions) ->
                    val addressId = arguments?.getString("addressId")
                    val filteredProperties = if (addressId != null) {
                        properties.filter { it.addressId == addressId }
                    } else {
                        properties
                    }
                    val sortedProperties = filteredProperties.sortedWith(
                        compareBy<PropertyTenantInfo> { property ->
                            PaymentStatusCalculator.sortRank(
                                PaymentStatusCalculator.currentStatus(property, transactions)
                            )
                        }.thenBy { property -> property.entityName.lowercase() }
                    )
                    adapter.updateData(sortedProperties, transactions)
                    binding.tvEmptyState.visibility = if (filteredProperties.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        binding.fabAddProperty.setOnClickListener {
            showPropertyDialog()
        }
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.property_list_menu, menu)
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
                        showBulkDeleteConfirmationDialog()
                        true
                    }
                    R.id.action_edit_mode -> {
                        adapter.isSelectionMode = !adapter.isSelectionMode
                        true
                    }
                    R.id.action_export_excel -> {
                        lifecycleScope.launch {
                            pendingExportData = viewModel.getExportDataCsv()
                            createCsvFile.launch("rent_desk_export.csv")
                        }
                        true
                    }
                    R.id.action_export_json -> {
                        lifecycleScope.launch {
                            pendingExportData = viewModel.getExportDataJson()
                            createJsonFile.launch("rent_desk_export.json")
                        }
                        true
                    }
                    R.id.action_delete_property -> {
                        adapter.isSelectionMode = true
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showBulkDeleteConfirmationDialog() {
        val selectedCount = adapter.selectedItems.size
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Properties")
            .setMessage("Are you sure you want to delete $selectedCount selected property(ies)?")
            .setPositiveButton("Delete") { _, _ ->
                val toDelete = adapter.getSelectedProperties()
                viewModel.deleteProperties(toDelete)
                adapter.isSelectionMode = false
                Toast.makeText(requireContext(), "$selectedCount properties deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPropertyDialog(existing: PropertyTenantInfo? = null) {
        val context = requireContext()
        val editText = EditText(context)
        editText.hint = getString(R.string.property_name)
        editText.setText(existing?.entityName.orEmpty())
        editText.setSelection(editText.text.length)
        
        val container = FrameLayout(context)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val margin = (20 * resources.displayMetrics.density).toInt()
        params.marginStart = margin
        params.marginEnd = margin
        editText.layoutParams = params
        container.addView(editText)
        
        AlertDialog.Builder(context)
            .setTitle(if (existing == null) R.string.add_property else R.string.edit_property_name)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotBlank()) {
                    if (existing == null) {
                        val addressId = arguments?.getString("addressId")
                        val property = PropertyTenantInfo(
                            propertyId = UUID.randomUUID().toString(),
                            entityName = name,
                            addressId = addressId
                        )
                        viewModel.insertProperty(property)
                    } else {
                        viewModel.updateProperty(existing.copy(entityName = name))
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveFile(uri: android.net.Uri, content: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                context?.contentResolver?.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    context?.let {
                        Toast.makeText(it, "File saved successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    context?.let {
                        Toast.makeText(it, "Failed to save file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
