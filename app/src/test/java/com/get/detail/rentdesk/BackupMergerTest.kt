package com.get.detail.rentdesk

import com.get.detail.rentdesk.backup.BackupData
import com.get.detail.rentdesk.backup.BackupMerger
import com.get.detail.rentdesk.data.local.entity.Address
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.local.entity.RecordTransaction
import com.get.detail.rentdesk.utils.PaymentDateUtils
import com.get.detail.rentdesk.utils.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupMergerTest {
    @Test
    fun merge_keepsNewestMatchingEntriesAndRetainsUniqueEntries() {
        val local = backupData(
            addresses = listOf(
                Address("shared", "Local newer", createdAtUtc = 10, modifiedAtUtc = 50),
                Address("local-only", "Local only", createdAtUtc = 20, modifiedAtUtc = 20)
            ),
            properties = listOf(
                PropertyTenantInfo(
                    "property",
                    "Local older",
                    addressId = "shared",
                    monthlyRent = 10_000,
                    electricityPricePerUnit = 7.0,
                    meterReading = 100,
                    balanceAmount = 200.0,
                    createdAtUtc = 10,
                    modifiedAtUtc = 30
                )
            ),
            transactions = listOf(transaction("transaction", amountPaid = 100.0, modifiedAt = 70))
        )
        val remote = backupData(
            addresses = listOf(
                Address("shared", "Remote older", createdAtUtc = 5, modifiedAtUtc = 40),
                Address("remote-only", "Remote only", createdAtUtc = 25, modifiedAtUtc = 25)
            ),
            properties = listOf(
                PropertyTenantInfo(
                    "property",
                    "Remote newer",
                    addressId = "shared",
                    monthlyRent = 12_000,
                    electricityPricePerUnit = 8.5,
                    meterReading = 125,
                    balanceAmount = 300.0,
                    createdAtUtc = 5,
                    modifiedAtUtc = 60
                )
            ),
            transactions = listOf(transaction("transaction", amountPaid = 90.0, modifiedAt = 65))
        )

        val merged = BackupMerger.merge(local, remote)

        assertEquals(listOf("local-only", "remote-only", "shared"), merged.addresses.map { it.dataUUID })
        assertEquals("Local newer", merged.addresses.first { it.dataUUID == "shared" }.address)
        assertEquals(5L, merged.addresses.first { it.dataUUID == "shared" }.createdAtUtc)
        assertEquals("Remote newer", merged.properties.single().entityName)
        assertEquals(12_000, merged.properties.single().monthlyRent)
        assertEquals(8.5, merged.properties.single().electricityPricePerUnit, 0.0)
        assertEquals(125, merged.properties.single().meterReading)
        assertEquals(300.0, merged.properties.single().balanceAmount, 0.0)
        assertEquals(5L, merged.properties.single().createdAtUtc)
        assertEquals(100.0, merged.transactions.single().amountPaid, 0.0)
    }

    private fun backupData(
        addresses: List<Address>,
        properties: List<PropertyTenantInfo>,
        transactions: List<RecordTransaction>
    ) = BackupData(
        schemaVersion = 4,
        createdAtUtc = "2026-09-20T00:00:00Z",
        updatedByMobile = "9999999999",
        sourceDevice = "Test",
        appVersion = "1.0",
        addresses = addresses,
        properties = properties,
        transactions = transactions
    )

    private fun transaction(id: String, amountPaid: Double, modifiedAt: Long) = RecordTransaction(
        transactionId = id,
        propertyId = "property",
        paymentDateUtc = PaymentDateUtils.fromDateParts(2026, 9, 20),
        reading = 1,
        amountPaid = amountPaid,
        createdAtUtc = 10,
        modifiedAtUtc = modifiedAt
    )
}
