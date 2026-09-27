package com.get.detail.rentdesk.ui.address

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.databinding.ItemAddressBinding
import com.get.detail.rentdesk.domain.model.AddressOverview
import com.get.detail.rentdesk.utils.RentMonthPicker
import com.get.detail.rentdesk.utils.YearMonth
import java.text.NumberFormat
import java.util.Locale

class AddressAdapter(
    private val onClick: (AddressOverview) -> Unit,
    private val onEdit: (AddressOverview) -> Unit,
    private val onAddProperty: (AddressOverview) -> Unit
) : ListAdapter<AddressOverview, AddressAdapter.AddressViewHolder>(AddressDiffCallback()) {

    private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }

    var isEditMode = false
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddressViewHolder {
        return AddressViewHolder(ItemAddressBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: AddressViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AddressViewHolder(private val binding: ItemAddressBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(address: AddressOverview) = with(binding) {
            val context = root.context
            val hasProperties = address.propertyCount > 0
            val hasPending = address.pendingAmount > 0.0
            root.strokeColor = ContextCompat.getColor(context,
                if (hasProperties) R.color.address_active_outline else R.color.address_card_outline)
            ivAddressIcon.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context,
                if (hasProperties) R.color.address_occupied_background else R.color.address_empty_icon_background))
            ivAddressIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context,
                if (hasProperties) R.color.address_occupied else R.color.address_empty_icon))
            tvAddressTitle.text = address.address
            tvPropertyCount.text = if (hasProperties) root.resources.getQuantityString(
                R.plurals.property_count, address.propertyCount, address.propertyCount
            ) else context.getString(R.string.address_no_properties)

            val occupancyVisibility = if (hasProperties) View.VISIBLE else View.GONE
            occupancyFlow.visibility = occupancyVisibility
            tvOccupied.visibility = occupancyVisibility
            tvVacant.visibility = occupancyVisibility
            tvOccupied.text = context.getString(R.string.address_occupied_count, address.occupiedCount)
            tvVacant.text = context.getString(R.string.address_vacant_count, address.vacantCount)
            rowPending.visibility = if (hasProperties) View.VISIBLE else View.GONE
            val pendingColor = ContextCompat.getColor(context,
                if (hasPending) R.color.address_pending else R.color.address_occupied)
            ivPendingIcon.imageTintList = ColorStateList.valueOf(pendingColor)
            ivPendingIcon.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context,
                if (hasPending) R.color.address_pending_background else R.color.address_occupied_background))
            tvPendingAmount.setTextColor(pendingColor)
            tvPendingAmount.text = if (hasPending) {
                context.getString(R.string.address_pending_amount, money.format(address.pendingAmount))
            } else context.getString(R.string.address_no_pending)
            rowPending.contentDescription = context.getString(
                R.string.address_pending_description,
                money.format(address.pendingAmount),
                RentMonthPicker.label(YearMonth.now())
            )

            btnAddFirstProperty.visibility = if (!hasProperties && !isEditMode) View.VISIBLE else View.GONE
            // An empty address still needs its title and edit action, without an unused footer.
            footerDivider.visibility = if (hasProperties || !isEditMode) View.VISIBLE else View.GONE
            btnAddFirstProperty.setOnClickListener { onAddProperty(address) }
            root.setOnClickListener { onClick(address) }
            btnEditAddress.visibility = if (isEditMode) View.VISIBLE else View.GONE
            ivChevron.visibility = if (isEditMode) View.GONE else View.VISIBLE
            btnEditAddress.setOnClickListener { onEdit(address) }
        }
    }

    class AddressDiffCallback : DiffUtil.ItemCallback<AddressOverview>() {
        override fun areItemsTheSame(oldItem: AddressOverview, newItem: AddressOverview): Boolean =
            oldItem.dataUUID == newItem.dataUUID

        override fun areContentsTheSame(oldItem: AddressOverview, newItem: AddressOverview): Boolean =
            oldItem == newItem
    }
}