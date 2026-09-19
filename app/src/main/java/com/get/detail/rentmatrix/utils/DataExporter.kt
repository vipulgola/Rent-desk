package com.get.detail.rentmatrix.utils

import com.get.detail.rentmatrix.data.local.entity.PropertyTenantInfo
import com.get.detail.rentmatrix.data.local.entity.RecordTransaction
import com.google.gson.GsonBuilder

object DataExporter {

    fun toCsv(properties: List<PropertyTenantInfo>, transactions: List<RecordTransaction>): String {
        val builder = StringBuilder()
        // Header
        builder.append("Property ID,Property Name,Tenant Name,Mobile,Joining Date,Address,Transaction ID,Month Year,Reading,Balance,Amount Paid\n")

        val propertyMap = properties.associateBy { it.propertyId }

        transactions.forEach { trans ->
            val prop = propertyMap[trans.propertyId]
            builder.append("${escapeCsv(trans.propertyId)},")
            builder.append("${escapeCsv(prop?.entityName ?: "N/A")},")
            builder.append("${escapeCsv(prop?.tenantInfo?.name ?: "N/A")},")
            builder.append("${escapeCsv(prop?.tenantInfo?.mobileNumber ?: "N/A")},")
            builder.append("${escapeCsv(prop?.tenantInfo?.joiningMonthYear?.toString() ?: "N/A")},")
            builder.append("${escapeCsv(prop?.tenantInfo?.address ?: "N/A")},")
            builder.append("${escapeCsv(trans.transactionId)},")
            builder.append("${escapeCsv(trans.monthYear.toString())},")
            builder.append("${trans.reading},")
            builder.append("${trans.balanceAmount},")
            builder.append("${trans.amountPaid}\n")
        }

        // Add properties without transactions
        val transPropIds = transactions.map { it.propertyId }.toSet()
        properties.filter { it.propertyId !in transPropIds }.forEach { prop ->
            builder.append("${escapeCsv(prop.propertyId)},")
            builder.append("${escapeCsv(prop.entityName)},")
            builder.append("${escapeCsv(prop.tenantInfo?.name ?: "N/A")},")
            builder.append("${escapeCsv(prop.tenantInfo?.mobileNumber ?: "N/A")},")
            builder.append("${escapeCsv(prop.tenantInfo?.joiningMonthYear?.toString() ?: "N/A")},")
            builder.append("${escapeCsv(prop.tenantInfo?.address ?: "N/A")},")
            builder.append(",,,,, \n")
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

    fun toJson(properties: List<PropertyTenantInfo>, transactions: List<RecordTransaction>): String {
        val data = mapOf(
            "properties" to properties,
            "transactions" to transactions
        )
        return GsonBuilder().setPrettyPrinting().create().toJson(data)
    }
}