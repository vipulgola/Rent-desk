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

    fun toCsv(properties: List<PropertyTenantInfo>, transactions: List<RecordTransaction>): String {
        val builder = StringBuilder()
        // Header
        builder.append("Property ID,Property Name,Tenant Name,Mobile,Joining Date,Address,Monthly Rent,Electricity Price Per Unit,Property Meter Reading,Property Balance,Property Created UTC,Property Modified UTC,Transaction ID,Payment Date,Reading,Amount Received,Transaction Created UTC,Transaction Modified UTC\n")

        val propertyMap = properties.associateBy { it.propertyId }

        transactions.forEach { trans ->
            val prop = propertyMap[trans.propertyId]
            builder.append("${escapeCsv(trans.propertyId)},")
            builder.append("${escapeCsv(prop?.entityName ?: "N/A")},")
            builder.append("${escapeCsv(prop?.tenantInfo?.name ?: "N/A")},")
            builder.append("${escapeCsv(prop?.tenantInfo?.mobileNumber ?: "N/A")},")
            builder.append("${escapeCsv(formatJoiningDate(prop?.tenantInfo))},")
            builder.append("${escapeCsv(prop?.tenantInfo?.address ?: "N/A")},")
            builder.append("${prop?.monthlyRent ?: 0},")
            builder.append("${prop?.electricityPricePerUnit ?: 0.0},")
            builder.append("${prop?.meterReading ?: 0},")
            builder.append("${prop?.balanceAmount ?: 0.0},")
            builder.append("${escapeCsv(formatUtc(prop?.createdAtUtc))},")
            builder.append("${escapeCsv(formatUtc(prop?.modifiedAtUtc))},")
            builder.append("${escapeCsv(trans.transactionId)},")
            builder.append("${escapeCsv(PaymentDateUtils.format(trans.paymentDateUtc))},")
            builder.append("${trans.reading},")
            builder.append("${trans.amountPaid},")
            builder.append("${escapeCsv(formatUtc(trans.createdAtUtc))},")
            builder.append("${escapeCsv(formatUtc(trans.modifiedAtUtc))}\n")
        }

        // Add properties without transactions
        val transPropIds = transactions.map { it.propertyId }.toSet()
        properties.filter { it.propertyId !in transPropIds }.forEach { prop ->
            builder.append("${escapeCsv(prop.propertyId)},")
            builder.append("${escapeCsv(prop.entityName)},")
            builder.append("${escapeCsv(prop.tenantInfo?.name ?: "N/A")},")
            builder.append("${escapeCsv(prop.tenantInfo?.mobileNumber ?: "N/A")},")
            builder.append("${escapeCsv(formatJoiningDate(prop.tenantInfo))},")
            builder.append("${escapeCsv(prop.tenantInfo?.address ?: "N/A")},")
            builder.append("${prop.monthlyRent},")
            builder.append("${prop.electricityPricePerUnit},")
            builder.append("${prop.meterReading},")
            builder.append("${prop.balanceAmount},")
            builder.append("${escapeCsv(formatUtc(prop.createdAtUtc))},")
            builder.append("${escapeCsv(formatUtc(prop.modifiedAtUtc))},")
            builder.append(",,,,,\n")
        }

        return builder.toString()
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
