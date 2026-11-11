package com.karyaku.kasir.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProductEntity::class, 
        TransactionEntity::class, 
        UserEntity::class, 
        AuditLogEntity::class, 
        ShiftEntity::class,
        CashFlowEntity::class,
        CustomerEntity::class,
        TransactionItemEntity::class,
        VoucherEntity::class,    
        HoldOrderEntity::class,
        RawMaterialEntity::class,
        ProductBomEntity::class,
        BranchEntity::class // <-- ENTITAS BARU DITAMBAHKAN
    ], 
    version = 20, // <-- UPDATE VERSI
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun userDao(): UserDao
    abstract fun cashFlowDao(): CashFlowDao
    abstract fun customerDao(): CustomerDao
    abstract fun transactionItemDao(): TransactionItemDao
    abstract fun voucherDao(): VoucherDao       
    abstract fun holdOrderDao(): HoldOrderDao   
    abstract fun branchDao(): BranchDao // <-- DAO BARU

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // MIGRASI AMAN DARI VERSI 19 KE 20
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Buat Tabel Cabang (Branches)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `branches` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `address` TEXT NOT NULL, 
                        `phone` TEXT NOT NULL, 
                        `isMainBranch` INTEGER NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `isSynced` INTEGER NOT NULL
                    )
                """.trimIndent())

                // 2. Tambah kolom di Users
                database.execSQL("ALTER TABLE users ADD COLUMN branchId INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE users ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE users ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")

                // 3. Tambah kolom di Products
                database.execSQL("ALTER TABLE products ADD COLUMN branchId INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE products ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE products ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")

                // 4. Tambah kolom di Transactions
                database.execSQL("ALTER TABLE transactions ADD COLUMN branchId INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE transactions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE transactions ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_database"
                )
                .addMigrations(MIGRATION_19_20) // Terapkan migrasi
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeAndResetDatabase() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}