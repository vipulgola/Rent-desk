package com.get.detail.rentdesk.backup

import com.get.detail.rentdesk.data.local.entity.Address
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.local.entity.RecordTransaction

object BackupMerger {
    fun merge(local: BackupData, remote: BackupData): BackupData = local.copy(
        addresses = mergeAddresses(local.addresses, remote.addresses),
        properties = mergeProperties(local.properties, remote.properties),
        transactions = mergeTransactions(local.transactions, remote.transactions)
    )

    private fun mergeAddresses(
        local: List<Address>,
        remote: List<Address>
    ): List<Address> = mergeById(local, remote, Address::dataUUID) { localItem, remoteItem ->
        newer(localItem.modifiedAtUtc, remoteItem.modifiedAtUtc, localItem, remoteItem).copy(
            createdAtUtc = earliest(localItem.createdAtUtc, remoteItem.createdAtUtc)
        )
    }

    private fun mergeProperties(
        local: List<PropertyTenantInfo>,
        remote: List<PropertyTenantInfo>
    ): List<PropertyTenantInfo> = mergeById(
        local,
        remote,
        PropertyTenantInfo::propertyId
    ) { localItem, remoteItem ->
        val winner = newer(localItem.modifiedAtUtc, remoteItem.modifiedAtUtc, localItem, remoteItem)
        val other = if (winner === localItem) remoteItem else localItem
        val history = (other.tenantHistory.orEmpty() + winner.tenantHistory.orEmpty())
            .filter { it.tenant.tenancyId != winner.tenantInfo?.tenancyId }
            .associateBy { it.tenant.tenancyId }.values.sortedBy { it.vacatedAtUtc }
        winner.copy(
            createdAtUtc = earliest(localItem.createdAtUtc, remoteItem.createdAtUtc),
            tenantHistory = history
        )
    }

    private fun mergeTransactions(
        local: List<RecordTransaction>,
        remote: List<RecordTransaction>
    ): List<RecordTransaction> = mergeById(
        local,
        remote,
        RecordTransaction::transactionId
    ) { localItem, remoteItem ->
        val winner = when {
            localItem.modifiedAtUtc > remoteItem.modifiedAtUtc -> localItem
            remoteItem.modifiedAtUtc > localItem.modifiedAtUtc -> remoteItem
            localItem.isDeleted -> localItem
            remoteItem.isDeleted -> remoteItem
            else -> localItem
        }
        winner.copy(
            createdAtUtc = earliest(localItem.createdAtUtc, remoteItem.createdAtUtc)
        )
    }

    private fun <T> mergeById(
        local: List<T>,
        remote: List<T>,
        id: (T) -> String,
        resolve: (T, T) -> T
    ): List<T> {
        val merged = remote.associateBy(id).toMutableMap()
        local.forEach { localItem ->
            val itemId = id(localItem)
            val remoteItem = merged[itemId]
            merged[itemId] = if (remoteItem == null) localItem else resolve(localItem, remoteItem)
        }
        return merged.entries.sortedBy { it.key }.map { it.value }
    }

    private fun <T> newer(
        localModifiedAt: Long,
        remoteModifiedAt: Long,
        local: T,
        remote: T
    ): T = if (localModifiedAt >= remoteModifiedAt) local else remote

    private fun earliest(first: Long, second: Long): Long = when {
        first <= 0L -> second
        second <= 0L -> first
        else -> minOf(first, second)
    }
}
