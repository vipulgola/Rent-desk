package com.get.detail.rentdesk.ui.property

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.get.detail.rentdesk.databinding.ItemPropertySummaryBinding
import java.text.NumberFormat

class PropertySummaryAdapter : RecyclerView.Adapter<PropertySummaryAdapter.SummaryViewHolder>() {
    private var total = 0
    private var occupied = 0
    private var emptyState: Int? = null
    private val numbers = NumberFormat.getIntegerInstance()

    fun update(total: Int, occupied: Int) {
        this.total = total
        this.occupied = occupied
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
            tvEmptyState.visibility = if (emptyState != null) View.VISIBLE else View.GONE
            emptyState?.let { tvEmptyState.setText(it) }
        }
    }

    class SummaryViewHolder(val binding: ItemPropertySummaryBinding) : RecyclerView.ViewHolder(binding.root)
}
