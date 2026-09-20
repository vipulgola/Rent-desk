package com.get.detail.rentdesk.backup

import android.content.Context
import android.os.Build
import androidx.room.withTransaction
import com.get.detail.rentdesk.BuildConfig
import com.get.detail.rentdesk.data.local.AppDatabase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
                transactions = database.transactionDao().getAllTransactions()
            )
        }
        return BackupEnvelope(
            checksumSha256 = sha256(gson.toJson(data).toByteArray(Charsets.UTF_8)),
            data = data
        )
    }

    fun toJsonBytes(backup: BackupEnvelope): ByteArray =
        gson.toJson(backup).toByteArray(Charsets.UTF_8)

    fun parseAndValidate(bytes: ByteArray): BackupEnvelope {
        val backup = gson.fromJson(bytes.toString(Charsets.UTF_8), BackupEnvelope::class.java)
            ?: error("Backup file is empty")
        require(backup.data.schemaVersion == SCHEMA_VERSION) {
            "Unsupported backup version ${backup.data.schemaVersion}"
        }
        val actualChecksum = sha256(gson.toJson(backup.data).toByteArray(Charsets.UTF_8))
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
        return backup
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
        appendLine("address_id,address")
        backup.data.addresses.forEach { appendLine(csvRow(it.dataUUID, it.address)) }
    }

    private fun propertiesCsv(backup: BackupEnvelope): String = buildString {
        appendLine("property_id,property_name,address_id")
        backup.data.properties.forEach {
            appendLine(csvRow(it.propertyId, it.entityName, it.addressId.orEmpty()))
        }
    }

    private fun tenantsCsv(backup: BackupEnvelope): String = buildString {
        appendLine("property_id,property_name,tenant_name,mobile,joining_date,aadhaar,address")
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
                        tenant.address
                    )
                )
            }
        }
    }

    private fun transactionsCsv(backup: BackupEnvelope): String = buildString {
        appendLine("transaction_id,property_id,month,year,reading,balance,amount_paid")
        backup.data.transactions.forEach {
            appendLine(
                csvRow(
                    it.transactionId,
                    it.propertyId,
                    it.monthYear.month.toString(),
                    it.monthYear.year.toString(),
                    it.reading.toString(),
                    it.balanceAmount.toString(),
                    it.amountPaid.toString()
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
        const val SCHEMA_VERSION = 1
    }
}
