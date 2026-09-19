package com.get.detail.rentdesk.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.get.detail.rentdesk.data.local.converter.Converters
import com.get.detail.rentdesk.data.local.dao.AddressDao
import com.get.detail.rentdesk.data.local.dao.PropertyTenantDao
import com.get.detail.rentdesk.data.local.dao.TransactionDao
import com.get.detail.rentdesk.data.local.entity.Address
import com.get.detail.rentdesk.data.local.entity.PropertyTenantInfo
import com.get.detail.rentdesk.data.local.entity.RecordTransaction

@Database(
    entities = [PropertyTenantInfo::class, RecordTransaction::class, Address::class],
    version = 3,
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
                    "rent_desk_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Removes the unused startMonthYear column while preserving every property and
 * transaction. The transaction table is temporarily copied because it has a
 * foreign key to property_tenant_info.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val database = db
        database.execSQL(
            """
            CREATE TABLE `record_transaction_backup` (
                `transactionId` TEXT NOT NULL,
                `propertyId` TEXT NOT NULL,
                `monthYear` TEXT NOT NULL,
                `reading` INTEGER NOT NULL,
                `balanceAmount` INTEGER NOT NULL,
                `amountPaid` INTEGER NOT NULL,
                PRIMARY KEY(`transactionId`)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO `record_transaction_backup`
                (`transactionId`, `propertyId`, `monthYear`, `reading`, `balanceAmount`, `amountPaid`)
            SELECT `transactionId`, `propertyId`, `monthYear`, `reading`, `balanceAmount`, `amountPaid`
            FROM `record_transaction`
            """.trimIndent()
        )
        database.execSQL("DROP TABLE `record_transaction`")

        database.execSQL(
            """
            CREATE TABLE `property_tenant_info_new` (
                `propertyId` TEXT NOT NULL,
                `entityName` TEXT NOT NULL,
                `addressId` TEXT,
                `tenantInfo` TEXT,
                PRIMARY KEY(`propertyId`)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO `property_tenant_info_new`
                (`propertyId`, `entityName`, `addressId`, `tenantInfo`)
            SELECT `propertyId`, `entityName`, `addressId`, `tenantInfo`
            FROM `property_tenant_info`
            """.trimIndent()
        )
        database.execSQL("DROP TABLE `property_tenant_info`")
        database.execSQL(
            "ALTER TABLE `property_tenant_info_new` RENAME TO `property_tenant_info`"
        )

        database.execSQL(
            """
            CREATE TABLE `record_transaction` (
                `transactionId` TEXT NOT NULL,
                `propertyId` TEXT NOT NULL,
                `monthYear` TEXT NOT NULL,
                `reading` INTEGER NOT NULL,
                `balanceAmount` INTEGER NOT NULL,
                `amountPaid` INTEGER NOT NULL,
                PRIMARY KEY(`transactionId`),
                FOREIGN KEY(`propertyId`) REFERENCES `property_tenant_info`(`propertyId`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO `record_transaction`
                (`transactionId`, `propertyId`, `monthYear`, `reading`, `balanceAmount`, `amountPaid`)
            SELECT `transactionId`, `propertyId`, `monthYear`, `reading`, `balanceAmount`, `amountPaid`
            FROM `record_transaction_backup`
            """.trimIndent()
        )
        database.execSQL("DROP TABLE `record_transaction_backup`")
        database.execSQL(
            "CREATE INDEX `index_record_transaction_propertyId` ON `record_transaction` (`propertyId`)"
        )
        database.execSQL(
            "CREATE INDEX `index_record_transaction_monthYear` ON `record_transaction` (`monthYear`)"
        )
    }
}
