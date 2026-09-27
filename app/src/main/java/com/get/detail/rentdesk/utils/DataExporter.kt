package com.get.detail.rentdesk.utils

import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.domain.model.TenantInfo
import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DataExporter {

    fun toCsv(properties: List<PropertyTenantInfo>, transactions: List<RecordTransaction>): String = buildString {
        val headers = listOf("Property ID", "Property Name", "Tenant Name", "Mobile", "Joining Date", "Address",
            "Monthly Rent", "Electricity Price Per Unit", "Property Meter Reading", "Property Balance",
            "Property Created UTC", "Property Modified UTC", "Transaction ID", "Payment Date", "Reading",
            "Amount Received", "Transaction Created UTC", "Transaction Modified UTC", "Rent For", "Tenancy ID",
            "Tenant Status", "Deposit Received", "Deposit Deductions", "Deposit Refunded", "Deposit Notes")
        appendLine(headers.joinToString(","))
        fun row(property: PropertyTenantInfo, tenant: TenantInfo?, transaction: RecordTransaction?,
            rent: Int, balance: Double, status: String) {
            val values = listOf(property.propertyId, property.entityName, tenant?.name ?: "N/A",
                tenant?.mobileNumber ?: "N/A", formatJoiningDate(tenant), tenant?.address ?: "N/A",
                rent.toString(), property.electricityPricePerUnit.toString(), property.meterReading.toString(),
                balance.toString(), formatUtc(property.createdAtUtc), formatUtc(property.modifiedAtUtc),
                transaction?.transactionId.orEmpty(), transaction?.let { PaymentDateUtils.format(it.paymentDateUtc) }.orEmpty(),
                transaction?.reading?.toString().orEmpty(), transaction?.amountPaid?.toString().orEmpty(),
                transaction?.let { formatUtc(it.createdAtUtc) }.orEmpty(),
                transaction?.let { formatUtc(it.modifiedAtUtc) }.orEmpty(), transaction?.rentMonth()?.toString().orEmpty(),
                (tenant?.tenancyId ?: transaction?.tenancyId).orEmpty(), status, tenant?.depositReceived?.toString().orEmpty(),
                tenant?.depositDeductions?.toString().orEmpty(), tenant?.depositRefunded?.toString().orEmpty(),
                tenant?.depositNotes.orEmpty())
            appendLine(values.joinToString(",") { escapeCsv(it) })
        }
        properties.forEach { property ->
            val payments = transactions.filter { it.propertyId == property.propertyId && !it.isDeleted }
            payments.forEach { payment ->
                val archived = property.tenantHistory.orEmpty().firstOrNull { it.tenant.tenancyId == payment.tenancyId }
                val active = property.tenantInfo?.takeIf { it.tenancyId == payment.tenancyId }
                row(property, archived?.tenant ?: active, payment, archived?.monthlyRent ?: property.monthlyRent,
                    archived?.closingBalance ?: if (active != null) property.balanceAmount
                        else property.unassignedBalance ?: property.balanceAmount,
                    if (archived != null) "Previous" else if (active != null) "Current" else "Unknown")
            }
            property.tenantInfo?.let { tenant ->
                if (payments.none { it.tenancyId == tenant.tenancyId })
                    row(property, tenant, null, property.monthlyRent, property.balanceAmount, "Current")
            }
            property.tenantHistory.orEmpty().forEach { entry ->
                if (payments.none { it.tenancyId == entry.tenant.tenancyId })
                    row(property, entry.tenant, null, entry.monthlyRent, entry.closingBalance, "Previous")
            }
            if (payments.isEmpty() && property.tenantInfo == null && property.tenantHistory.isNullOrEmpty())
                row(property, null, null, property.monthlyRent, property.balanceAmount, "Vacant")
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun formatJoiningDate(tenantInfo: TenantInfo?): String {
        tenantInfo ?: return "N/A"
        val day = tenantInfo.joiningDayOfMonth.takeIf { it in 1..31 } ?: 1
        return "%02d-%02d-%04d".format(
            day,
            tenantInfo.joiningMonthYear.month,
            tenantInfo.joiningMonthYear.year
        )
    }

    private fun formatUtc(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0L) return "N/A"
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestamp))
    }

    fun toJson(properties: List<PropertyTenantInfo>, transactions: List<RecordTransaction>): String {
        val data = mapOf(
            "properties" to properties,
            "transactions" to transactions
        )
        return GsonBuilder().setPrettyPrinting().create().toJson(data)
    }
}
