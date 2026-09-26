package com.get.detail.rentdesk.ui.transaction

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.databinding.ItemTransactionBinding
import com.get.detail.rentdesk.utils.PaymentDateUtils
import java.text.NumberFormat
import java.text.DateFormatSymbols
import java.util.Locale

class TransactionAdapter(
    private val onEditClick: (RecordTransaction) -> Unit,
    private val onSelectionChanged: (Boolean) -> Unit
) : ListAdapter<RecordTransaction, TransactionAdapter.TransactionViewHolder>(TransactionDiffCallback()) {

    var isSelectionMode = false
        set(value) {
            field = value
            if (!value) selectedItems.clear()
            notifyDataSetChanged()
            onSelectionChanged(value)
        }

    val selectedItems = mutableSetOf<RecordTransaction>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun getSelectedTransactions(): List<RecordTransaction> = selectedItems.toList()

    inner class TransactionViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(transaction: RecordTransaction) {
            val dateParts = PaymentDateUtils.format(transaction.paymentDateUtc).split('/')
            binding.tvTransactionDay.text = dateParts.getOrNull(0).orEmpty()
            binding.tvTransactionMonth.text = dateParts.getOrNull(1)?.toIntOrNull()?.let {
                DateFormatSymbols.getInstance(Locale.getDefault()).shortMonths[it - 1]
            }.orEmpty()
            binding.tvTransactionYear.text = dateParts.getOrNull(2).orEmpty()
            binding.tvAmountPaid.text = CURRENCY_FORMAT.format(transaction.amountPaid)
            binding.tvReading.text = binding.root.context.getString(R.string.meter_reading, transaction.reading)
            binding.tvRentFor.text = binding.root.context.getString(R.string.rent_for_value,
                com.get.detail.rentdesk.utils.RentMonthPicker.label(transaction.rentMonth()))

            // Visual feedback for selection
            binding.cbSelected.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            binding.btnEditTransaction.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
            binding.cbSelected.isChecked = selectedItems.contains(transaction)
            binding.root.isSelected = selectedItems.contains(transaction)

            binding.btnEditTransaction.setOnClickListener {
                onEditClick(transaction)
            }

            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(transaction)
                }
            }

            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    isSelectionMode = true
                    toggleSelection(transaction)
                    true
                } else {
                    false
                }
            }
        }

        private fun toggleSelection(transaction: RecordTransaction) {
            val position = adapterPosition
            if (position == RecyclerView.NO_POSITION) return

            if (selectedItems.contains(transaction)) {
                selectedItems.remove(transaction)
            } else {
                selectedItems.add(transaction)
            }
            notifyItemChanged(position)
            if (selectedItems.isEmpty() && isSelectionMode) {
                isSelectionMode = false
            }
            onSelectionChanged(isSelectionMode)
        }
    }

    class TransactionDiffCallback : DiffUtil.ItemCallback<RecordTransaction>() {
        override fun areItemsTheSame(oldItem: RecordTransaction, newItem: RecordTransaction): Boolean {
            return oldItem.transactionId == newItem.transactionId
        }

        override fun areContentsTheSame(oldItem: RecordTransaction, newItem: RecordTransaction): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private val CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    }
}
