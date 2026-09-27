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
import com.get.detail.rentdesk.domain.usecase.RentPaymentStatus
import com.get.detail.rentdesk.utils.PaymentUtils
import android.view.View
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PropertyAdapter(
    private val onClick: (PropertyTenantInfo) -> Unit,
    private val onEdit: (PropertyTenantInfo) -> Unit,
    private val onCollectRent: (PropertyTenantInfo) -> Unit,
    private val onSelectionChanged: (Boolean) -> Unit,
    private var propertyList: List<PropertyTenantInfo> = listOf(),
    private var transactionList: List<RecordTransaction> = listOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }
    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

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
            val context = binding.root.context
            val billingDate = billingDateText(property, paymentStatus)
            val vacant = paymentStatus == RentPaymentStatus.VACANT
            binding.btnCollectRent.visibility =
                if (paymentStatus == RentPaymentStatus.DUE && !isSelectionMode) View.VISIBLE else View.GONE
            binding.btnCollectRent.setOnClickListener { onCollectRent(property) }
            PaymentUtils.updatePaymentStatusUI(binding.tvPaymentStatus, paymentStatus, showDot = true)
            binding.tvRentLabel.setText(if (vacant) R.string.property_expected_rent else R.string.property_monthly_rent)
            binding.tvMonthlyRent.text = money.format(property.monthlyRent)
            binding.tvBillingDateLabel.setText(when (paymentStatus) {
                RentPaymentStatus.DUE -> R.string.property_pending_since
                RentPaymentStatus.PAID -> R.string.property_next_due_date
                RentPaymentStatus.VACANT -> R.string.property_available_from
            })
            binding.tvBillingDate.text = billingDate ?: context.getString(R.string.property_available_immediately)
            binding.ivPropertyIcon.setBackgroundResource(if (vacant)
                R.drawable.bg_property_icon_vacant else R.drawable.bg_property_icon)
            binding.ivPropertyIcon.setColorFilter(ContextCompat.getColor(context, if (vacant)
                R.color.status_vacant else R.color.brand_primary))

            binding.cbPropertySelected.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            binding.cbPropertySelected.isChecked = selectedItems.contains(property)
            binding.btnEditProperty.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            binding.ivChevron.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
            binding.btnEditProperty.setOnClickListener { onEdit(property) }
            
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
            // Use the property adapter's position; the summary occupies a separate adapter.
            val position = propertyList.indexOfFirst { it.propertyId == property.propertyId }
            if (position < 0) return

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

    private fun billingDateText(property: PropertyTenantInfo, status: RentPaymentStatus): String? {
        val tenant = property.tenantInfo ?: return null
        val month = PaymentStatusCalculator.billingMonth(property)
        val joining = tenant.joiningMonthYear
        val beforeJoining = month == null || month.year < joining.year ||
            (month.year == joining.year && month.month < joining.month)
        val baseMonth = if (beforeJoining) joining else requireNotNull(month)
        val date = Calendar.getInstance().apply {
            clear()
            set(baseMonth.year, baseMonth.month - 1, 1)
            if (status == RentPaymentStatus.PAID && !beforeJoining) add(Calendar.MONTH, 1)
            val joiningDay = tenant.joiningDayOfMonth.takeIf { it in 1..31 } ?: 1
            set(Calendar.DAY_OF_MONTH, minOf(joiningDay, getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        return dateFormat.format(date.time)
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
