package com.get.detail.rentdesk.ui.address

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.data.local.AppDatabase
import com.get.detail.rentdesk.data.local.entity.Address
import com.get.detail.rentdesk.data.repository.RentRepository
import com.get.detail.rentdesk.databinding.FragmentAddressListBinding
import com.get.detail.rentdesk.viewmodel.AddressViewModel
import com.get.detail.rentdesk.viewmodel.AddressViewModelFactory
import kotlinx.coroutines.launch
import java.util.UUID

class AddressListFragment : Fragment() {
    private var _binding: FragmentAddressListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddressViewModel by viewModels {
        val database = AppDatabase.getDatabase(requireContext())
        val repository = RentRepository(database.addressDao(), database.propertyTenantDao(), database.transactionDao())
        AddressViewModelFactory(repository)
    }

    private lateinit var adapter: AddressAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddressListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AddressAdapter { addressWithCount ->
            val bundle = Bundle().apply {
                putString("addressId", addressWithCount.dataUUID)
            }
            findNavController().navigate(R.id.action_addressListFragment_to_propertyListFragment, bundle)
        }
        binding.rvAddresses.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.addressesWithCount.collect { addresses ->
                    adapter.submitList(addresses)
                    binding.tvEmptyState.visibility = if (addresses.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        binding.fabAddAddress.setOnClickListener {
            showAddAddressDialog()
        }
    }

    private fun showAddAddressDialog() {
        val context = requireContext()
        val editText = EditText(context)
        editText.hint = "Address"
        
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
            .setTitle("Add Address")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val addressName = editText.text.toString()
                if (addressName.isNotBlank()) {
                    val address = Address(
                        dataUUID = UUID.randomUUID().toString(),
                        address = addressName
                    )
                    viewModel.insertAddress(address)
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