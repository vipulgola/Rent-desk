package com.get.detail.rentmatrix.ui.address

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.get.detail.rentmatrix.data.local.dao.AddressWithPropertyCount
import com.get.detail.rentmatrix.databinding.ItemAddressBinding

class AddressAdapter(
    private val onClick: (AddressWithPropertyCount) -> Unit
) : ListAdapter<AddressWithPropertyCount, AddressAdapter.AddressViewHolder>(AddressDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddressViewHolder {
        val binding = ItemAddressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AddressViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: AddressViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AddressViewHolder(
        private val binding: ItemAddressBinding,
        private val onClick: (AddressWithPropertyCount) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(addressWithCount: AddressWithPropertyCount) {
            binding.tvAddressTitle.text = addressWithCount.address
            binding.tvPropertyCount.text = "${addressWithCount.propertyCount} Properties"
            binding.root.setOnClickListener { onClick(addressWithCount) }
        }
    }

    class AddressDiffCallback : DiffUtil.ItemCallback<AddressWithPropertyCount>() {
        override fun areItemsTheSame(oldItem: AddressWithPropertyCount, newItem: AddressWithPropertyCount): Boolean {
            return oldItem.dataUUID == newItem.dataUUID
        }

        override fun areContentsTheSame(oldItem: AddressWithPropertyCount, newItem: AddressWithPropertyCount): Boolean {
            return oldItem == newItem
        }
    }
}