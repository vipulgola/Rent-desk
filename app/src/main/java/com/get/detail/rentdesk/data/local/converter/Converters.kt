package com.get.detail.rentdesk.data.local.converter

import androidx.room.TypeConverter
import com.get.detail.rentdesk.domain.model.TenantInfo
import com.get.detail.rentdesk.utils.YearMonth
import com.google.gson.Gson
import com.get.detail.rentdesk.domain.model.TenantHistoryEntry
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromTenantHistory(history: List<TenantHistoryEntry>?): String? = history?.let { gson.toJson(it) }

    @TypeConverter
    fun toTenantHistory(value: String?): List<TenantHistoryEntry>? = value?.let {
        gson.fromJson(it, object : TypeToken<List<TenantHistoryEntry>>() {}.type)
    }

    @TypeConverter
    fun fromYearMonth(yearMonth: YearMonth?): String? {
        return yearMonth?.toString()
    }

    @TypeConverter
    fun toYearMonth(value: String?): YearMonth? {
        return value?.let { YearMonth.parse(it) }
    }

    @TypeConverter
    fun fromTenantInfo(tenantInfo: TenantInfo?): String? {
        return tenantInfo?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toTenantInfo(value: String?): TenantInfo? {
        return value?.let { gson.fromJson(it, TenantInfo::class.java) }
    }
}
