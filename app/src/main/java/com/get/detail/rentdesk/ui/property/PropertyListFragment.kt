package com.get.detail.rentdesk.ui.property

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.data.local.AppDatabase
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.repository.RentRepository
import com.get.detail.rentdesk.databinding.FragmentPropertyListBinding
import com.get.detail.rentdesk.databinding.DialogNameBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
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
    private lateinit var summaryAdapter: PropertySummaryAdapter
    private var visibleProperties: List<PropertyTenantInfo> = emptyList()
    private var visibleTransactions: List<com.get.detail.rentdesk.data.local.entity.RecordTransaction> = emptyList()
    private var searchQuery = ""
    private var propertyFilter = PropertyFilter.ALL
    private var pendingExportData: String? = null

    private val createCsvFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { saveFile(it, pendingExportData ?: "") }
    }

    private val createJsonFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { saveFile(it, pendingExportData ?: "") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        propertyFilter = PropertyFilter.values().firstOrNull {
            it.name == savedInstanceState?.getString("propertyFilter")
        } ?: PropertyFilter.ALL
        
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
                findNavController().navigate(R.id.action_propertyListFragment_to_propertyDetailsFragment, bundle)
            },
            onEdit = { property -> showPropertyDialog(property) },
            onCollectRent = { property ->
                findNavController().navigate(R.id.action_propertyListFragment_to_transactionListFragment,
                    Bundle().apply {
                        putString("propertyId", property.propertyId)
                        putBoolean("collectRent", true)
                    })
            },
            onSelectionChanged = { isSelectionMode ->
                requireActivity().invalidateOptionsMenu()
                binding.fabAddProperty.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
            }
        )
        summaryAdapter = PropertySummaryAdapter { filter ->
            if (propertyFilter != filter) {
                propertyFilter = filter
                if (adapter.isSelectionMode) adapter.isSelectionMode = false
                applyFilters()
                binding.rvProperties.scrollToPosition(0)
            }
        }
        binding.rvProperties.adapter = ConcatAdapter(summaryAdapter, adapter)
        summaryAdapter.update(visibleProperties.size, visibleProperties.count { it.tenantInfo != null }, propertyFilter)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.allProperties,
                    viewModel.allTransactions,
                    AppDatabase.getDatabase(requireContext()).addressDao().getAllAddresses()
                ) { properties, transactions, addresses -> Triple(properties, transactions, addresses) }
                    .collect { (properties, transactions, addresses) ->
                    val addressId = arguments?.getString("addressId")
                    (requireActivity() as AppCompatActivity).supportActionBar?.subtitle =
                        addresses.firstOrNull { it.dataUUID == addressId }?.address
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
                    visibleProperties = sortedProperties
                    visibleTransactions = transactions
                    applyFilters()
                }
            }
        }

        binding.fabAddProperty.setOnClickListener {
            showPropertyDialog()
        }

        if (arguments?.getBoolean("addProperty") == true) {
            arguments?.remove("addProperty")
            showPropertyDialog()
        }
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.property_list_menu, menu)
                (menu.findItem(R.id.action_search_properties).actionView as SearchView)
                    .setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                        override fun onQueryTextSubmit(query: String?) = false
                        override fun onQueryTextChange(query: String?): Boolean {
                            searchQuery = query.orEmpty()
                            applyFilters()
                            return true
                        }
                    })
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
                    R.id.action_monthly_collection -> {
                        findNavController().navigate(
                            R.id.action_propertyListFragment_to_monthlyCollectionFragment,
                            Bundle().apply { putString("addressId", arguments?.getString("addressId")) }
                        )
                        true
                    }
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

    private fun applyFilters() {
        if (::adapter.isInitialized && ::summaryAdapter.isInitialized && _binding != null) {
            val filteredProperties = visibleProperties.filter { property ->
                when (propertyFilter) {
                    PropertyFilter.ALL -> true
                    PropertyFilter.OCCUPIED -> property.tenantInfo != null
                    PropertyFilter.VACANT -> property.tenantInfo == null
                }
            }
            val matches = filteredProperties.filter {
                it.entityName.contains(searchQuery, ignoreCase = true) ||
                    it.tenantInfo?.name?.contains(searchQuery, ignoreCase = true) == true
            }
            adapter.updateData(matches, visibleTransactions)
            summaryAdapter.update(visibleProperties.size, visibleProperties.count { it.tenantInfo != null }, propertyFilter)
            summaryAdapter.setEmptyState(
                if (matches.isNotEmpty()) null
                else if (visibleProperties.isEmpty()) R.string.no_properties
                else if (filteredProperties.isEmpty() && propertyFilter == PropertyFilter.OCCUPIED)
                    R.string.no_occupied_properties
                else if (filteredProperties.isEmpty() && propertyFilter == PropertyFilter.VACANT)
                    R.string.no_vacant_properties
                else R.string.no_matching_properties
            )
        }
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
        val sheet = DialogNameBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        sheet.tvDialogTitle.setText(if (existing == null) R.string.add_property else R.string.edit_property_name)
        sheet.etName.setText(existing?.entityName.orEmpty())
        sheet.etName.setSelection(sheet.etName.text?.length ?: 0)
        sheet.btnCancelName.setOnClickListener { dialog.dismiss() }
        sheet.btnSaveName.setOnClickListener {
            val name = sheet.etName.text.toString().trim()
            if (name.isBlank()) {
                sheet.tilName.error = getString(R.string.name_required)
                return@setOnClickListener
            }
            if (existing == null) {
                viewModel.insertProperty(PropertyTenantInfo(
                    propertyId = UUID.randomUUID().toString(),
                    entityName = name,
                    addressId = arguments?.getString("addressId")
                ))
            } else {
                viewModel.updateProperty(existing.copy(entityName = name))
            }
            dialog.dismiss()
        }
        dialog.setContentView(sheet.root)
        dialog.show()
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("propertyFilter", propertyFilter.name)
    }

    override fun onDestroyView() {
        binding.rvProperties.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
