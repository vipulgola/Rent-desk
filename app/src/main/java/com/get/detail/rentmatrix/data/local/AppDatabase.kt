package com.get.detail.rentmatrix.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.get.detail.rentmatrix.data.local.converter.Converters
import com.get.detail.rentmatrix.data.local.dao.AddressDao
import com.get.detail.rentmatrix.data.local.dao.PropertyTenantDao
import com.get.detail.rentmatrix.data.local.dao.TransactionDao
import com.get.detail.rentmatrix.data.local.entity.Address
import com.get.detail.rentmatrix.data.local.entity.PropertyTenantInfo
import com.get.detail.rentmatrix.data.local.entity.RecordTransaction

@Database(
    entities = [PropertyTenantInfo::class, RecordTransaction::class, Address::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun addressDao(): AddressDao
    abstract fun propertyTenantDao(): PropertyTenantDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rent_matrix_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}