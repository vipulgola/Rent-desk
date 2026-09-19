package com.get.detail.rentmatrix.data.local.converter

import androidx.room.TypeConverter
import com.get.detail.rentmatrix.domain.model.TenantInfo
import com.get.detail.rentmatrix.utils.YearMonth
import com.google.gson.Gson

class Converters {
    private val gson = Gson()

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