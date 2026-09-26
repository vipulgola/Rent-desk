package com.get.detail.rentdesk.ui.address

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
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
import com.get.detail.rentdesk.data.local.entity.Address
import com.get.detail.rentdesk.domain.model.AddressOverview
import androidx.recyclerview.widget.ConcatAdapter
import com.get.detail.rentdesk.data.repository.RentRepository
import com.get.detail.rentdesk.databinding.FragmentAddressListBinding
import com.get.detail.rentdesk.databinding.DialogNameBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.get.detail.rentdesk.viewmodel.AddressViewModel
import com.get.detail.rentdesk.viewmodel.AddressViewModelFactory
import kotlinx.coroutines.launch
import java.util.UUID

class AddressListFragment : Fragment() {
    private var _binding: FragmentAddressListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddressViewModel by viewModels {
        val database = AppDatabase.getDatabase(requireContext())
        val repository = RentRepository(requireContext(), database.addressDao(), database.propertyTenantDao(), database.transactionDao())
        AddressViewModelFactory(repository)
    }

    private lateinit var adapter: AddressAdapter
    private lateinit var summaryAdapter: AddressSummaryAdapter
    private var allAddresses: List<AddressOverview> = emptyList()
    private var searchQuery = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddressListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenu()

        adapter = AddressAdapter(
            onClick = { address -> openProperties(address) },
            onEdit = { address ->
                showAddressDialog(address.dataUUID, address.address)
            },
            onAddProperty = { address -> openProperties(address, addProperty = true) }
        )
        summaryAdapter = AddressSummaryAdapter()
        binding.rvAddresses.adapter = ConcatAdapter(summaryAdapter, adapter)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.addressOverviews.collect { addresses ->
                    allAddresses = addresses
                    summaryAdapter.update(addresses.size, addresses.sumOf { it.propertyCount })
                    applySearch()
                }
            }
        }

        binding.fabAddAddress.setOnClickListener {
            showAddressDialog()
        }
    }

    private fun openProperties(address: AddressOverview, addProperty: Boolean = false) {
        findNavController().navigate(
            R.id.action_addressListFragment_to_propertyListFragment,
            Bundle().apply {
                putString("addressId", address.dataUUID)
                putBoolean("addProperty", addProperty)
            }
        )
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.address_list_menu, menu)
                (menu.findItem(R.id.action_search_addresses).actionView as SearchView)
                    .setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                        override fun onQueryTextSubmit(query: String?) = false
                        override fun onQueryTextChange(query: String?): Boolean {
                            searchQuery = query.orEmpty()
                            applySearch()
                            return true
                        }
                    })
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.action_edit_mode)?.title =
                    if (::adapter.isInitialized && adapter.isEditMode) {
                        getString(R.string.done)
                    } else {
                        getString(R.string.action_edit)
                    }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId != R.id.action_edit_mode) return false
                adapter.isEditMode = !adapter.isEditMode
                requireActivity().invalidateOptionsMenu()
                return true
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun applySearch() {
        if (::adapter.isInitialized && ::summaryAdapter.isInitialized) {
            val matches = allAddresses.filter {
                it.address.contains(searchQuery, ignoreCase = true)
            }
            adapter.submitList(matches)
            summaryAdapter.setEmptyState(
                if (matches.isNotEmpty()) null
                else if (allAddresses.isEmpty()) R.string.address_dashboard_empty
                else R.string.no_matching_addresses
            )
        }
    }

    private fun showAddressDialog(addressId: String? = null, currentName: String = "") {
        val sheet = DialogNameBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        sheet.tvDialogTitle.setText(if (addressId == null) R.string.add_address else R.string.edit_address)
        sheet.tilName.hint = getString(R.string.address)
        sheet.ivNameIcon.setImageResource(R.drawable.ic_location_24)
        sheet.etName.setText(currentName)
        sheet.etName.setSelection(sheet.etName.text?.length ?: 0)
        sheet.btnCancelName.setOnClickListener { dialog.dismiss() }
        sheet.btnSaveName.setOnClickListener {
            val name = sheet.etName.text.toString().trim()
            if (name.isBlank()) {
                sheet.tilName.error = getString(R.string.name_required)
                return@setOnClickListener
            }
            if (addressId == null) {
                viewModel.insertAddress(Address(UUID.randomUUID().toString(), name))
            } else {
                viewModel.updateAddressName(addressId, name)
            }
            dialog.dismiss()
        }
        dialog.setContentView(sheet.root)
        dialog.show()
    }

    override fun onDestroyView() {
        binding.rvAddresses.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
