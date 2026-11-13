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
        BranchEntity::class, 
        MembershipEntity::class, 
        CustomerMembershipEntity::class,
        SupplierEntity::class, 
        PurchaseEntity::class, 
        PurchaseItemEntity::class,
        PurchasePaymentEntity::class,
        ReservationEntity::class, 
        ExpenseEntity::class,
        StockOpnameEntity::class,
        StockOpnameItemEntity::class
    ], 
    version = 30, 
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
    abstract fun branchDao(): BranchDao
    abstract fun membershipDao(): MembershipDao
    abstract fun supplierDao(): SupplierDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun purchasePaymentDao(): PurchasePaymentDao
    abstract fun reservationDao(): ReservationDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun stockOpnameDao(): StockOpnameDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 1. MIGRASI MULTI-CABANG (19 -> 20)
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `branches` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `address` TEXT NOT NULL, `phone` TEXT NOT NULL, `isMainBranch` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO `branches` (id, name, address, phone, isMainBranch, createdAt, updatedAt, isSynced) VALUES (1, 'Cabang Pusat', '-', '-', 1, ${System.currentTimeMillis()}, ${System.currentTimeMillis()}, 0)")
                
                database.execSQL("ALTER TABLE users ADD COLUMN branchId INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE users ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE users ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
                
                database.execSQL("ALTER TABLE products ADD COLUMN branchId INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE products ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE products ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
                
                database.execSQL("ALTER TABLE transactions ADD COLUMN branchId INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE transactions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE transactions ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
            }
        }

        // 2. MIGRASI PLACEHOLDER (20 -> 21)
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Placeholder
            }
        }

        // 3. MIGRASI FITUR GROSIR, ORDER TYPE, SERVICE CHARGE (21 -> 22)
        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE products ADD COLUMN wholesalePrice INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE products ADD COLUMN wholesaleMinQty INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE transactions ADD COLUMN orderType TEXT NOT NULL DEFAULT 'Dine-In'")
                database.execSQL("ALTER TABLE transactions ADD COLUMN serviceCharge INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE transactions ADD COLUMN pickupDate INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        // 4. MIGRASI MODUL MEMBERSHIP (22 -> 23)
        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `memberships` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `price` INTEGER NOT NULL, `quotaCount` INTEGER NOT NULL, `validDays` INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `customer_memberships` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `customerId` INTEGER NOT NULL, `membershipId` INTEGER NOT NULL, `membershipName` TEXT NOT NULL, `remainingQuota` INTEGER NOT NULL, `expiryDate` INTEGER NOT NULL)")
            }
        }

        // 5. MIGRASI MODUL SUPPLIER & PO (23 -> 24)
        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `suppliers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `contactPerson` TEXT NOT NULL, `phone` TEXT NOT NULL, `address` TEXT NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `purchases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `supplierId` INTEGER NOT NULL, `supplierName` TEXT NOT NULL, `invoiceNumber` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `totalAmount` INTEGER NOT NULL, `status` TEXT NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `purchase_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `purchaseId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `productName` TEXT NOT NULL, `costPrice` INTEGER NOT NULL, `quantity` INTEGER NOT NULL, `subtotal` INTEGER NOT NULL, FOREIGN KEY(`purchaseId`) REFERENCES `purchases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_items_purchaseId` ON `purchase_items` (`purchaseId`)")
            }
        }

        // 6. MIGRASI MODUL RESERVASI (24 -> 25)
        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `reservations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `customerName` TEXT NOT NULL, `phone` TEXT NOT NULL, `dateTime` INTEGER NOT NULL, `resourceName` TEXT NOT NULL, `status` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
            }
        }

        // 7. MIGRASI MODUL FINANCE / PENGELUARAN (25 -> 26)
        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT NOT NULL, `amount` INTEGER NOT NULL, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `userId` INTEGER NOT NULL, `userName` TEXT NOT NULL)")
            }
        }

        // 8. MIGRASI MODUL STOCK OPNAME (26 -> 27)
        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `stock_opnames` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `userId` INTEGER NOT NULL, `userName` TEXT NOT NULL, `totalItemsChecked` INTEGER NOT NULL, `totalLossValue` INTEGER NOT NULL, `totalGainValue` INTEGER NOT NULL)")
                
                database.execSQL("CREATE TABLE IF NOT EXISTS `stock_opname_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `opnameId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `productName` TEXT NOT NULL, `systemStock` INTEGER NOT NULL, `physicalStock` INTEGER NOT NULL, `difference` INTEGER NOT NULL, FOREIGN KEY(`opnameId`) REFERENCES `stock_opnames`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_opname_items_opnameId` ON `stock_opname_items` (`opnameId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_opname_items_productId` ON `stock_opname_items` (`productId`)")
            }
        }

        // 9. MIGRASI PHASE 0 SESI 2: BON pelunasan tracking (27 -> 28)
        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transactions ADD COLUMN paidAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE transactions ADD COLUMN paidMethod TEXT NOT NULL DEFAULT ''")
            }
        }

        // 10. MIGRASI MODUL E — Hutang Supplier Cicilan (28 -> 29)
        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE purchases ADD COLUMN paidAmount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE purchases ADD COLUMN paidAt INTEGER NOT NULL DEFAULT 0")
                
                database.execSQL(
                    "UPDATE purchases SET paidAmount = totalAmount, paidAt = timestamp WHERE status = 'PAID'"
                )
                
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `purchase_payments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `purchaseId` INTEGER NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `paymentMethod` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `userId` INTEGER NOT NULL,
                        `userName` TEXT NOT NULL,
                        FOREIGN KEY(`purchaseId`) REFERENCES `purchases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_payments_purchaseId` ON `purchase_payments` (`purchaseId`)")
            }
        }

        // ================================================================
        // 11. MIGRASI MODUL D-1 — Multi-Cabang Shift (29 -> 30)
        // ================================================================
        // Shift per cabang agar buka/tutup kasir terisolasi per cabang.
        // Backfill: shift lama dianggap milik cabang user-nya. Kalau user
        // tidak ditemukan (data corrupt), default ke cabang 1.
        // ================================================================
        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE shifts ADD COLUMN branchId INTEGER NOT NULL DEFAULT 1")
                
                // Backfill dari user.branchId (kalau user masih ada).
                database.execSQL(
                    """
                    UPDATE shifts 
                    SET branchId = COALESCE(
                        (SELECT branchId FROM users WHERE users.id = shifts.userId),
                        1
                    )
                    """.trimIndent()
                )
                
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_shifts_branchId` ON `shifts` (`branchId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_shifts_status` ON `shifts` (`status`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_database"
                )
                .addMigrations(
                    MIGRATION_19_20, 
                    MIGRATION_20_21, 
                    MIGRATION_21_22, 
                    MIGRATION_22_23, 
                    MIGRATION_23_24, 
                    MIGRATION_24_25,
                    MIGRATION_25_26,
                    MIGRATION_26_27,
                    MIGRATION_27_28,
                    MIGRATION_28_29,
                    MIGRATION_29_30
                )
                .fallbackToDestructiveMigrationFrom(
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18
                )
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