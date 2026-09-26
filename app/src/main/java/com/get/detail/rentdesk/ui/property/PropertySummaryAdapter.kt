package com.get.detail.rentdesk.ui.property

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.databinding.ItemPropertySummaryBinding
import java.text.NumberFormat

enum class PropertyFilter { ALL, OCCUPIED, VACANT }

class PropertySummaryAdapter(
    private val onFilterSelected: (PropertyFilter) -> Unit
) : RecyclerView.Adapter<PropertySummaryAdapter.SummaryViewHolder>() {
    private var total = 0
    private var occupied = 0
    private var selectedFilter = PropertyFilter.ALL
    private var emptyState: Int? = null
    private val numbers = NumberFormat.getIntegerInstance()

    fun update(total: Int, occupied: Int, selectedFilter: PropertyFilter) {
        if (this.total == total && this.occupied == occupied && this.selectedFilter == selectedFilter) return
        this.total = total
        this.occupied = occupied
        this.selectedFilter = selectedFilter
        notifyItemChanged(0)
    }

    fun setEmptyState(message: Int?) {
        if (emptyState == message) return
        emptyState = message
        notifyItemChanged(0)
    }

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = SummaryViewHolder(
        ItemPropertySummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: SummaryViewHolder, position: Int) {
        with(holder.binding) {
            tvTotalProperties.text = numbers.format(total)
            tvOccupiedProperties.text = numbers.format(occupied)
            tvVacantProperties.text = numbers.format(total - occupied)
            bindFilter(filterAll, PropertyFilter.ALL, total, R.string.property_summary_properties)
            bindFilter(filterOccupied, PropertyFilter.OCCUPIED, occupied, R.string.property_summary_occupied)
            bindFilter(filterVacant, PropertyFilter.VACANT, total - occupied, R.string.payment_vacant)
            tvEmptyState.visibility = if (emptyState != null) View.VISIBLE else View.GONE
            emptyState?.let { tvEmptyState.setText(it) }
        }
    }

    private fun bindFilter(view: View, filter: PropertyFilter, count: Int, label: Int) {
        view.isSelected = filter == selectedFilter
        view.contentDescription = view.context.getString(
            R.string.property_filter_description, numbers.format(count), view.context.getString(label)
        )
        view.setOnClickListener { onFilterSelected(filter) }
    }

    class SummaryViewHolder(val binding: ItemPropertySummaryBinding) : RecyclerView.ViewHolder(binding.root)
}
