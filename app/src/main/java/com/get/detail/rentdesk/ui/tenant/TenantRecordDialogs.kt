package com.get.detail.rentdesk.ui.tenant

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.get.detail.rentdesk.R
import com.get.detail.rentdesk.data.local.AppDatabase
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.data.repository.RentRepository
import com.get.detail.rentdesk.databinding.DialogDepositBinding
import com.get.detail.rentdesk.domain.model.TenantHistoryEntry
import com.get.detail.rentdesk.utils.PaymentDateUtils
import com.get.detail.rentdesk.utils.RentMonthPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TenantRecordDialogs {
    private fun money(value: Double) = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
    private fun date(timestamp: Long) = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
    private fun repository(fragment: Fragment): RentRepository {
        val database = AppDatabase.getDatabase(fragment.requireContext())
        return RentRepository(fragment.requireContext(), database.addressDao(), database.propertyTenantDao(), database.transactionDao())
    }

    fun showHistory(fragment: Fragment, propertyId: String) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val database = AppDatabase.getDatabase(fragment.requireContext())
            val property = database.propertyTenantDao().getPropertyById(propertyId) ?: return@launch
            val history = property.tenantHistory.orEmpty().sortedByDescending { it.vacatedAtUtc }
            val payments = database.transactionDao().getAllTransactions().filter { it.propertyId == propertyId }
            val unassigned = payments.filter { it.tenancyId == null }
            val unassignedBalance = property.unassignedBalance
                ?: property.balanceAmount.takeIf { property.tenantInfo == null && it != 0.0 }
            val hasUnassigned = unassigned.isNotEmpty() || unassignedBalance != null
            val labels = history.map { "${it.tenant.name}\n${fragment.getString(R.string.tenant_moved_out, date(it.vacatedAtUtc))}" } +
                if (hasUnassigned) listOf(fragment.getString(R.string.tenant_legacy_payments)) else emptyList()
            val dialog = AlertDialog.Builder(fragment.requireContext()).setTitle(R.string.tenant_history)
                .setNegativeButton(R.string.cancel, null)
            if (labels.isEmpty()) dialog.setMessage(R.string.no_tenant_history) else {
                dialog.setItems(labels.toTypedArray()) { _, index ->
                    if (index == history.size) {
                        AlertDialog.Builder(fragment.requireContext()).setTitle(R.string.tenant_legacy_payments)
                            .setMessage(fragment.getString(R.string.legacy_tenant_balance,
                                unassignedBalance?.let(::money) ?: fragment.getString(R.string.not_recorded)))
                            .setPositiveButton(R.string.view_transactions) { _, _ -> showPayments(fragment, unassigned) }
                            .setNegativeButton(R.string.cancel, null).show()
                    }
                    else showHistoryEntry(fragment, propertyId, history[index], payments)
                }
            }
            dialog.show()
        }
    }

    private fun showHistoryEntry(fragment: Fragment, propertyId: String, entry: TenantHistoryEntry,
        payments: List<RecordTransaction>) {
        val tenant = entry.tenant
        val joining = PaymentDateUtils.fromDateParts(tenant.joiningMonthYear.year,
            tenant.joiningMonthYear.month, tenant.joiningDayOfMonth)
        val text = fragment.getString(R.string.tenant_history_details,
            tenant.name, tenant.mobileNumber, PaymentDateUtils.format(joining), date(entry.vacatedAtUtc),
            money(entry.monthlyRent.toDouble()), money(entry.closingBalance),
            money(tenant.depositReceived), money(tenant.depositDeductions), money(tenant.depositRefunded),
            money((tenant.depositReceived - tenant.depositDeductions - tenant.depositRefunded).coerceAtLeast(0.0))) +
            "\n\n" + tenant.address + if (tenant.depositNotes.isNullOrBlank()) "" else "\n\n" + tenant.depositNotes
        AlertDialog.Builder(fragment.requireContext()).setTitle(R.string.tenant_history).setMessage(text)
            .setPositiveButton(R.string.security_deposit) { _, _ ->
                tenant.tenancyId?.let { showDeposit(fragment, propertyId, it) { showHistory(fragment, propertyId) } }
            }
            .setNeutralButton(R.string.view_transactions) { _, _ ->
                showPayments(fragment, payments.filter { it.tenancyId == tenant.tenancyId })
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun showPayments(fragment: Fragment, payments: List<RecordTransaction>) {
        val lines = payments.sortedByDescending { it.paymentDateUtc }.map {
            "${PaymentDateUtils.format(it.paymentDateUtc)} · ${money(it.amountPaid)}\n" +
                fragment.getString(R.string.rent_for_value, RentMonthPicker.label(it.rentMonth()))
        }
        val dialog = AlertDialog.Builder(fragment.requireContext()).setTitle(R.string.past_tenant_payments)
            .setPositiveButton(android.R.string.ok, null)
        if (lines.isEmpty()) dialog.setMessage(R.string.no_history_payments) else dialog.setItems(lines.toTypedArray(), null)
        dialog.show()
    }

    fun showDeposit(fragment: Fragment, propertyId: String, tenancyId: String, onSaved: () -> Unit = {}) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val repo = repository(fragment)
            val property = repo.getPropertyById(propertyId) ?: return@launch
            val tenant = property.tenantInfo?.takeIf { it.tenancyId == tenancyId }
                ?: property.tenantHistory.orEmpty().firstOrNull { it.tenant.tenancyId == tenancyId }?.tenant ?: return@launch
            val fields = DialogDepositBinding.inflate(fragment.layoutInflater)
            fields.etDepositReceived.setText(tenant.depositReceived.toString())
            fields.etDepositDeductions.setText(tenant.depositDeductions.toString())
            fields.etDepositRefunded.setText(tenant.depositRefunded.toString())
            fields.etDepositNotes.setText(tenant.depositNotes.orEmpty())
            fun amount(text: CharSequence?) = text?.toString()?.trim()?.toDoubleOrNull()
            fun updateRemaining() {
                val remaining = (amount(fields.etDepositReceived.text) ?: 0.0) -
                    (amount(fields.etDepositDeductions.text) ?: 0.0) - (amount(fields.etDepositRefunded.text) ?: 0.0)
                fields.tvDepositRemaining.text = fragment.getString(R.string.deposit_remaining, money(remaining))
            }
            listOf(fields.etDepositReceived, fields.etDepositDeductions, fields.etDepositRefunded)
                .forEach { it.doAfterTextChanged { updateRemaining() } }
            updateRemaining()
            val dialog = AlertDialog.Builder(fragment.requireContext()).setTitle(R.string.security_deposit)
                .setView(fields.root).setPositiveButton(R.string.save, null).setNegativeButton(R.string.cancel, null).create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val received = amount(fields.etDepositReceived.text)
                    val deductions = amount(fields.etDepositDeductions.text)
                    val refunded = amount(fields.etDepositRefunded.text)
                    if (received == null || deductions == null || refunded == null ||
                        listOf(received, deductions, refunded).any { !it.isFinite() || it < 0 } ||
                        deductions + refunded > received + 0.005) {
                        fields.tilDepositReceived.error = fragment.getString(R.string.invalid_deposit)
                        return@setOnClickListener
                    }
                    fields.tilDepositReceived.error = null
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    fragment.viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            repo.updateDeposit(propertyId, tenancyId, received, deductions, refunded,
                                fields.etDepositNotes.text?.toString()?.trim().orEmpty())
                            dialog.dismiss()
                            onSaved()
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            Toast.makeText(fragment.context, error.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            dialog.show()
        }
    }
}
