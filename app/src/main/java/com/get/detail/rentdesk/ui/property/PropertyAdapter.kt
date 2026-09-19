package com.get.detail.rentdesk.ui.property

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.databinding.ItemPropertyBinding
import com.get.detail.rentdesk.domain.usecase.PaymentStatusCalculator
import com.get.detail.rentdesk.utils.PaymentUtils
import android.view.View

class PropertyAdapter(
    private val onClick: (PropertyTenantInfo) -> Unit,
    private val onSelectionChanged: (Boolean) -> Unit,
    private var propertyList: List<PropertyTenantInfo> = listOf(),
    private var transactionList: List<RecordTransaction> = listOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var isSelectionMode = false
        set(value) {
            field = value
            if (!value) selectedItems.clear()
            notifyDataSetChanged()
            onSelectionChanged(value)
        }

    val selectedItems = mutableSetOf<PropertyTenantInfo>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PropertyViewHolder {
        val binding = ItemPropertyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PropertyViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        (holder as PropertyViewHolder).bind(propertyList[position])
    }

    override fun getItemCount(): Int {
        return propertyList.size
    }

    fun getSelectedProperties(): List<PropertyTenantInfo> = selectedItems.toList()

    inner class PropertyViewHolder(private val binding: ItemPropertyBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(property: PropertyTenantInfo) {
            binding.tvPropertyName.text = property.entityName
            binding.tvTenantName.text = property.tenantInfo?.name
                ?: binding.root.context.getString(R.string.no_tenant)
            
            val paymentStatus = PaymentStatusCalculator.currentStatus(property, transactionList)
            PaymentUtils.updatePaymentStatusUI(binding.tvPaymentStatus, paymentStatus)

            binding.cbPropertySelected.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            binding.cbPropertySelected.isChecked = selectedItems.contains(property)
            
            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(property)
                } else {
                    onClick(property)
                }
            }

            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    isSelectionMode = true
                    toggleSelection(property)
                    true
                } else {
                    false
                }
            }
        }

        private fun toggleSelection(property: PropertyTenantInfo) {
            val position = adapterPosition
            if (position == RecyclerView.NO_POSITION) return

            if (selectedItems.contains(property)) {
                selectedItems.remove(property)
            } else {
                selectedItems.add(property)
            }
            notifyItemChanged(position)
            if (selectedItems.isEmpty() && isSelectionMode) {
                isSelectionMode = false
            }
            onSelectionChanged(isSelectionMode)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updatePropertyList(list: List<PropertyTenantInfo>) {
        propertyList = list
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(properties: List<PropertyTenantInfo>, transactions: List<RecordTransaction>) {
        propertyList = properties
        transactionList = transactions
        notifyDataSetChanged()
    }

}
