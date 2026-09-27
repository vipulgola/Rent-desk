package com.get.detail.rentdesk.ui.address

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.text.scale
import androidx.recyclerview.widget.RecyclerView
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.databinding.ItemAddressSummaryBinding
import java.text.NumberFormat

class AddressSummaryAdapter : RecyclerView.Adapter<AddressSummaryAdapter.SummaryViewHolder>() {
    private var locations = 0
    private var properties = 0
    private var emptyState: Int? = null
    private val numbers = NumberFormat.getIntegerInstance()

    fun update(locations: Int, properties: Int) {
        this.locations = locations
        this.properties = properties
        notifyItemChanged(0)
    }

    fun setEmptyState(message: Int?) {
        if (emptyState == message) return
        emptyState = message
        notifyItemChanged(0)
    }

    override fun getItemCount(): Int = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SummaryViewHolder {
        return SummaryViewHolder(
            ItemAddressSummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: SummaryViewHolder, position: Int) {
        with(holder.binding) {
            val headingColor = ContextCompat.getColor(root.context, R.color.address_heading)
            tvLocationTotal.text = buildSpannedString {
                color(headingColor) { bold { scale(1.2f) { append(numbers.format(locations)) } } }
                append(" ")
                append(root.resources.getQuantityString(R.plurals.address_locations_label, locations))
            }
            tvPropertyTotal.text = buildSpannedString {
                color(headingColor) { bold { scale(1.2f) { append(numbers.format(properties)) } } }
                append(" ")
                append(root.resources.getQuantityString(R.plurals.address_properties_label, properties))
            }
            tvEmptyState.visibility = if (emptyState != null) View.VISIBLE else View.GONE
            emptyState?.let { tvEmptyState.setText(it) }
        }
    }

    class SummaryViewHolder(val binding: ItemAddressSummaryBinding) : RecyclerView.ViewHolder(binding.root)
}