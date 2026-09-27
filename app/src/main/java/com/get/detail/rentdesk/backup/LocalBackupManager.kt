package com.get.detail.rentdesk.backup

import android.content.Context
import android.os.Build
import androidx.room.withTransaction
import com.get.detail.rentdesk.BuildConfig
import com.get.detail.rentdesk.data.local.AppDatabase
import com.get.detail.rentdesk.utils.PaymentDateUtils
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LocalBackupManager(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun createBackup(mobileNumber: String): BackupEnvelope {
        val data = database.withTransaction {
            BackupData(
                schemaVersion = SCHEMA_VERSION,
                createdAtUtc = utcTimestamp(),
                updatedByMobile = mobileNumber,
                sourceDevice = deviceName(),
                appVersion = BuildConfig.VERSION_NAME,
                addresses = database.addressDao().getAllAddressesList(),
                properties = database.propertyTenantDao().getAllPropertiesList(),
                transactions = database.transactionDao().getAllTransactionRecords()
            )
        }
        return createEnvelope(data)
    }

    fun mergeBackups(local: BackupEnvelope, remote: BackupEnvelope): BackupEnvelope =
        createEnvelope(BackupMerger.merge(local.data, remote.data))

    fun createEnvelope(data: BackupData): BackupEnvelope = BackupEnvelope(
        checksumSha256 = sha256(gson.toJson(data).toByteArray(Charsets.UTF_8)),
        data = data
    )

    fun toJsonBytes(backup: BackupEnvelope): ByteArray =
        gson.toJson(backup).toByteArray(Charsets.UTF_8)

    fun parseAndValidate(bytes: ByteArray): BackupEnvelope {
        val json = bytes.toString(Charsets.UTF_8)
        val root = JsonParser.parseString(json).asJsonObject
        val dataJson = root.get("data") ?: error("Backup data is missing")
        val backup = gson.fromJson(json, BackupEnvelope::class.java)
            ?: error("Backup file is empty")
        require(backup.data.schemaVersion in 1..SCHEMA_VERSION) {
            "Unsupported backup version ${backup.data.schemaVersion}"
        }
        val actualChecksum = sha256(gson.toJson(dataJson).toByteArray(Charsets.UTF_8))
        require(actualChecksum.equals(backup.checksumSha256, ignoreCase = true)) {
            "Backup checksum does not match"
        }
        val propertyIds = backup.data.properties.map { it.propertyId }.toSet()
        require(backup.data.transactions.all { it.propertyId in propertyIds }) {
            "Backup contains transactions without a property"
        }
        val addressIds = backup.data.addresses.map { it.dataUUID }.toSet()
        require(backup.data.properties.all { it.addressId == null || it.addressId in addressIds }) {
            "Backup contains properties without an address"
        }
        val fallbackTime = parseUtcTimestamp(backup.data.createdAtUtc)
        val dataObject = dataJson.asJsonObject
        val propertiesWithStoredBalance = dataObject.getAsJsonArray("properties")
            ?.mapNotNull { element ->
                val property = element.asJsonObject
                property.get("propertyId")?.asString
                    ?.takeIf { property.has("balanceAmount") }
            }
            ?.toSet()
            .orEmpty()
        val legacyBalances = legacyTransactionBalances(dataObject)
        val legacyPaymentDates = legacyTransactionPaymentDates(dataObject)
        val migratedData = backup.data.copy(
            schemaVersion = SCHEMA_VERSION,
            addresses = backup.data.addresses.map {
                it.copy(
                    createdAtUtc = it.createdAtUtc.takeIf { value -> value > 0L } ?: fallbackTime,
                    modifiedAtUtc = it.modifiedAtUtc.takeIf { value -> value > 0L } ?: fallbackTime
                )
            },
            properties = backup.data.properties.map {
                it.copy(
                    tenantInfo = it.tenantInfo?.let { tenant ->
                        tenant.copy(tenancyId = tenant.tenancyId ?: "legacy-${it.propertyId}")
                    },
                    balanceAmount = if (it.propertyId in propertiesWithStoredBalance) {
                        it.balanceAmount
                    } else {
                        legacyBalances[it.propertyId] ?: 0.0
                    },
                    createdAtUtc = it.createdAtUtc.takeIf { value -> value > 0L } ?: fallbackTime,
                    modifiedAtUtc = it.modifiedAtUtc.takeIf { value -> value > 0L } ?: fallbackTime
                )
            },
            transactions = backup.data.transactions.map {
                val legacyTenant = backup.data.properties.firstOrNull { property -> property.propertyId == it.propertyId }?.tenantInfo
                it.copy(
                    tenancyId = it.tenancyId ?: if (backup.data.schemaVersion < 6 && legacyTenant != null)
                        legacyTenant.tenancyId ?: "legacy-${it.propertyId}" else null,
                    paymentDateUtc = it.paymentDateUtc.takeIf { value -> value > 0L }
                        ?: legacyPaymentDates[it.transactionId]
                        ?: fallbackTime,
                    createdAtUtc = it.createdAtUtc.takeIf { value -> value > 0L } ?: fallbackTime,
                    modifiedAtUtc = it.modifiedAtUtc.takeIf { value -> value > 0L } ?: fallbackTime
                )
            }
        )
        require(migratedData.transactions.all { it.billingMonth == null ||
            (it.billingMonth.year in 1900..2200 && it.billingMonth.month in 1..12) }) { "Invalid rent month in backup" }
        migratedData.properties.forEach { property ->
            val tenants = listOfNotNull(property.tenantInfo) + property.tenantHistory.orEmpty().map { it.tenant }
            require(tenants.mapNotNull { it.tenancyId }.distinct().size == tenants.size) { "Invalid tenant history in backup" }
            tenants.forEach { tenant ->
                require(listOf(tenant.depositReceived, tenant.depositDeductions, tenant.depositRefunded)
                    .all { it.isFinite() && it >= 0 } &&
                    tenant.depositDeductions + tenant.depositRefunded <= tenant.depositReceived + 0.005) {
                    "Invalid deposit in backup"
                }
            }
        }
        return createEnvelope(migratedData)
    }

    private fun legacyTransactionPaymentDates(
        data: com.google.gson.JsonObject
    ): Map<String, Long> = buildMap {
        data.getAsJsonArray("transactions")?.forEach { element ->
            val transaction = element.asJsonObject
            val transactionId = transaction.get("transactionId")?.asString ?: return@forEach
            val timestamp = transaction.get("paymentDateUtc")
                ?.takeUnless { it.isJsonNull }
                ?.asLong
                ?.takeIf { it > 0L }
                ?: transaction.get("paymentDate")
                    ?.takeUnless { it.isJsonNull }
                    ?.asString
                    ?.let {
                        PaymentDateUtils.parse(it)
                            ?: PaymentDateUtils.parse(it, "ddMMyyyy")
                    }
                ?: legacyMonthYearDate(transaction.get("monthYear"))
            if (timestamp != null) put(transactionId, timestamp)
        }
    }

    private fun legacyMonthYearDate(monthYear: com.google.gson.JsonElement?): Long? {
        monthYear ?: return null
        val parts = runCatching {
            val value = monthYear.asJsonObject
            value.get("year").asInt to value.get("month").asInt
        }.getOrElse {
            val text = monthYear.asString.split("-")
            if (text.size != 2) return null
            (text[0].toIntOrNull() ?: return null) to (text[1].toIntOrNull() ?: return null)
        }
        return PaymentDateUtils.fromDateParts(parts.first, parts.second, 1)
    }

    private fun legacyTransactionBalances(
        data: com.google.gson.JsonObject
    ): Map<String, Double> {
        val latest = mutableMapOf<String, Pair<Long, Double>>()
        data.getAsJsonArray("transactions")?.forEach { element ->
            val transaction = element.asJsonObject
            if (!transaction.has("balanceAmount")) return@forEach
            val propertyId = transaction.get("propertyId")?.asString ?: return@forEach
            val modifiedAt = transaction.get("modifiedAtUtc")
                ?.takeUnless { it.isJsonNull }
                ?.asLong
                ?: 0L
            val monthScore = transaction.get("monthYear")?.let { monthYear ->
                runCatching {
                    val value = monthYear.asJsonObject
                    value.get("year").asLong * 100 + value.get("month").asLong
                }.getOrElse {
                    monthYear.asString.replace("-", "").toLongOrNull() ?: 0L
                }
            } ?: 0L
            val score = modifiedAt.takeIf { it > 0L } ?: monthScore
            val balance = transaction.get("balanceAmount").asDouble
            if (latest[propertyId]?.first?.let { score >= it } != false) {
                latest[propertyId] = score to balance
            }
        }
        return latest.mapValues { it.value.second }
    }

    suspend fun restore(backup: BackupEnvelope) {
        database.withTransaction {
            database.transactionDao().deleteAllTransactions()
            database.propertyTenantDao().deleteAllProperties()
            database.addressDao().deleteAllAddresses()
            if (backup.data.addresses.isNotEmpty()) {
                database.addressDao().insertAddresses(backup.data.addresses)
            }
            if (backup.data.properties.isNotEmpty()) {
                database.propertyTenantDao().insertProperties(backup.data.properties)
            }
            if (backup.data.transactions.isNotEmpty()) {
                database.transactionDao().insertTransactions(backup.data.transactions)
            }
        }
    }

    fun createReadableZip(backup: BackupEnvelope): ByteArray {
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                addEntry(zip, "addresses.csv", addressesCsv(backup))
                addEntry(zip, "properties.csv", propertiesCsv(backup))
                addEntry(zip, "tenants.csv", tenantsCsv(backup))
                addEntry(zip, "tenant_history.csv", tenantHistoryCsv(backup))
                addEntry(zip, "transactions.csv", transactionsCsv(backup))
                addEntry(zip, "complete_backup.json", gson.toJson(backup))
                addEntry(zip, "metadata.json", gson.toJson(metadata(backup)))
            }
            output.toByteArray()
        }
    }

    fun backupFileName(backup: BackupEnvelope): String =
        "rentdesk_backup_${fileTimestamp()}_${maskedMobile(backup.data.updatedByMobile)}.json"

    fun exportFileName(backup: BackupEnvelope): String =
        "RentDesk_${fileTimestamp()}_${maskedMobile(backup.data.updatedByMobile)}.zip"

    private fun metadata(backup: BackupEnvelope): Map<String, Any> = linkedMapOf(
        "schemaVersion" to backup.data.schemaVersion,
        "createdAtUtc" to backup.data.createdAtUtc,
        "updatedByMobile" to backup.data.updatedByMobile,
        "sourceDevice" to backup.data.sourceDevice,
        "appVersion" to backup.data.appVersion,
        "recordCounts" to backup.data.recordCounts,
        "checksumSha256" to backup.checksumSha256
    )

    private fun addressesCsv(backup: BackupEnvelope): String = buildString {
        appendLine("address_id,address,created_at_utc,modified_at_utc")
        backup.data.addresses.forEach {
            appendLine(
                csvRow(
                    it.dataUUID,
                    it.address,
                    formatUtcTimestamp(it.createdAtUtc),
                    formatUtcTimestamp(it.modifiedAtUtc)
                )
            )
        }
    }

    private fun propertiesCsv(backup: BackupEnvelope): String = buildString {
        appendLine("property_id,property_name,address_id,monthly_rent,electricity_price_per_unit,meter_reading,balance_amount,created_at_utc,modified_at_utc")
        backup.data.properties.forEach {
            appendLine(
                csvRow(
                    it.propertyId,
                    it.entityName,
                    it.addressId.orEmpty(),
                    it.monthlyRent.toString(),
                    it.electricityPricePerUnit.toString(),
                    it.meterReading.toString(),
                    it.balanceAmount.toString(),
                    formatUtcTimestamp(it.createdAtUtc),
                    formatUtcTimestamp(it.modifiedAtUtc)
                )
            )
        }
    }

    private fun tenantsCsv(backup: BackupEnvelope): String = buildString {
        appendLine("property_id,property_name,tenant_name,mobile,joining_date,aadhaar,address,monthly_rent,electricity_price_per_unit,meter_reading,balance_amount,tenancy_id,deposit_received,deposit_deductions,deposit_refunded,deposit_notes")
        backup.data.properties.forEach { property ->
            property.tenantInfo?.let { tenant ->
                appendLine(
                    csvRow(
                        property.propertyId,
                        property.entityName,
                        tenant.name,
                        tenant.mobileNumber,
                        "%02d-%02d-%04d".format(
                            tenant.joiningDayOfMonth,
                            tenant.joiningMonthYear.month,
                            tenant.joiningMonthYear.year
                        ),
                        tenant.aadhaarNumber,
                        tenant.address,
                        property.monthlyRent.toString(),
                        property.electricityPricePerUnit.toString(),
                        property.meterReading.toString(),
                        property.balanceAmount.toString(),
                        tenant.tenancyId.orEmpty(),
                        tenant.depositReceived.toString(),
                        tenant.depositDeductions.toString(),
                        tenant.depositRefunded.toString(),
                        tenant.depositNotes.orEmpty()
                    )
                )
            }
        }
    }

    private fun tenantHistoryCsv(backup: BackupEnvelope): String = buildString {
        appendLine("property_id,tenancy_id,tenant_name,mobile,joining_date,moved_out_date,monthly_rent,closing_balance,deposit_received,deposit_deductions,deposit_refunded,deposit_held,notes")
        backup.data.properties.forEach { property ->
            val records = property.tenantHistory.orEmpty()
            records.forEach { entry ->
                val tenant = entry.tenant
                appendLine(csvRow(property.propertyId, tenant.tenancyId.orEmpty(), tenant.name, tenant.mobileNumber,
                    "%02d-%02d-%04d".format(tenant.joiningDayOfMonth, tenant.joiningMonthYear.month, tenant.joiningMonthYear.year),
                    formatUtcTimestamp(entry.vacatedAtUtc), entry.monthlyRent.toString(), entry.closingBalance.toString(),
                    tenant.depositReceived.toString(), tenant.depositDeductions.toString(), tenant.depositRefunded.toString(),
                    (tenant.depositReceived - tenant.depositDeductions - tenant.depositRefunded).toString(),
                    tenant.depositNotes.orEmpty()))
            }
        }
    }

    private fun transactionsCsv(backup: BackupEnvelope): String = buildString {
        appendLine("transaction_id,property_id,payment_date,reading,amount_received,previous_balance,previous_reading,rent_charged,electricity_rate,created_at_utc,modified_at_utc,rent_for,tenancy_id")
        backup.data.transactions.filterNot { it.isDeleted }.forEach {
            appendLine(
                csvRow(
                    it.transactionId,
                    it.propertyId,
                    PaymentDateUtils.format(it.paymentDateUtc),
                    it.reading.toString(),
                    it.amountPaid.toString(),
                    it.previousBalance?.toString().orEmpty(),
                    it.previousReading?.toString().orEmpty(),
                    it.rentCharged?.toString().orEmpty(),
                    it.electricityRateCharged?.toString().orEmpty(),
                    formatUtcTimestamp(it.createdAtUtc),
                    formatUtcTimestamp(it.modifiedAtUtc),
                    it.rentMonth().toString(),
                    it.tenancyId.orEmpty()
                )
            )
        }
    }

    private fun csvRow(vararg values: String): String = values.joinToString(",") { csvCell(it) }

    private fun csvCell(value: String): String {
        val safe = if (value.firstOrNull() in setOf('=', '+', '-', '@')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }

    private fun addEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun utcTimestamp(): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    private fun formatUtcTimestamp(timestamp: Long): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(timestamp))

    private fun parseUtcTimestamp(value: String): Long = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(value)?.time
    }.getOrNull() ?: System.currentTimeMillis()

    private fun fileTimestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .trim()

    private fun maskedMobile(mobile: String): String {
        val digits = mobile.filter(Char::isDigit)
        return when {
            digits.length >= 4 -> digits.take(2) + "xxxxxx" + digits.takeLast(2)
            digits.isNotEmpty() -> "xxxx$digits"
            else -> "unknown"
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val SCHEMA_VERSION = 6
    }
}
