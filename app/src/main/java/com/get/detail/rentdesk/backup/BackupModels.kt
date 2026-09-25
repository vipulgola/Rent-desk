package com.get.detail.rentdesk.backup

import com.get.detail.rentdesk.data.local.entity.Address
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.local.entity.RecordTransaction

data class BackupEnvelope(
    val checksumSha256: String,
    val data: BackupData
)

data class BackupData(
    val schemaVersion: Int,
    val createdAtUtc: String,
    val updatedByMobile: String,
    val sourceDevice: String,
    val appVersion: String,
    val addresses: List<Address>,
    val properties: List<PropertyTenantInfo>,
    val transactions: List<RecordTransaction>
) {
    val recordCounts: BackupRecordCounts
        get() = BackupRecordCounts(
            addresses.size,
            properties.size,
            transactions.count { !it.isDeleted }
        )
}

data class BackupRecordCounts(
    val addresses: Int,
    val properties: Int,
    val transactions: Int
)

data class DriveBackupFile(
    val id: String,
    val name: String,
    val modifiedTime: String,
    val size: Long
)

data class DriveAccountInfo(
    val emailAddress: String,
    val displayName: String?
)
