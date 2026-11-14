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
        StockOpnameItemEntity::class,
        StockTransferEntity::class,
        StockTransferItemEntity::class,
        KitchenTicketEntity::class // MODUL B
    ], 
    version = 34, // FIX: Naikkan versi database
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
    abstract fun stockTransferDao(): StockTransferDao
    abstract fun kitchenTicketDao(): KitchenTicketDao // MODUL B

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

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

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) { }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE products ADD COLUMN wholesalePrice INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE products ADD COLUMN wholesaleMinQty INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE transactions ADD COLUMN orderType TEXT NOT NULL DEFAULT 'Dine-In'")
                database.execSQL("ALTER TABLE transactions ADD COLUMN serviceCharge INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE transactions ADD COLUMN pickupDate INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `memberships` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `price` INTEGER NOT NULL, `quotaCount` INTEGER NOT NULL, `validDays` INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `customer_memberships` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `customerId` INTEGER NOT NULL, `membershipId` INTEGER NOT NULL, `membershipName` TEXT NOT NULL, `remainingQuota` INTEGER NOT NULL, `expiryDate` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `suppliers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `contactPerson` TEXT NOT NULL, `phone` TEXT NOT NULL, `address` TEXT NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `purchases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `supplierId` INTEGER NOT NULL, `supplierName` TEXT NOT NULL, `invoiceNumber` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `totalAmount` INTEGER NOT NULL, `status` TEXT NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `purchase_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `purchaseId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `productName` TEXT NOT NULL, `costPrice` INTEGER NOT NULL, `quantity` INTEGER NOT NULL, `subtotal` INTEGER NOT NULL, FOREIGN KEY(`purchaseId`) REFERENCES `purchases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_items_purchaseId` ON `purchase_items` (`purchaseId`)")
            }
        }

        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `reservations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `customerName` TEXT NOT NULL, `phone` TEXT NOT NULL, `dateTime` INTEGER NOT NULL, `resourceName` TEXT NOT NULL, `status` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT NOT NULL, `amount` INTEGER NOT NULL, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `userId` INTEGER NOT NULL, `userName` TEXT NOT NULL)")
            }
        }

        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `stock_opnames` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `userId` INTEGER NOT NULL, `userName` TEXT NOT NULL, `totalItemsChecked` INTEGER NOT NULL, `totalLossValue` INTEGER NOT NULL, `totalGainValue` INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `stock_opname_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `opnameId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `productName` TEXT NOT NULL, `systemStock` INTEGER NOT NULL, `physicalStock` INTEGER NOT NULL, `difference` INTEGER NOT NULL, FOREIGN KEY(`opnameId`) REFERENCES `stock_opnames`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_opname_items_opnameId` ON `stock_opname_items` (`opnameId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_opname_items_productId` ON `stock_opname_items` (`productId`)")
            }
        }

        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transactions ADD COLUMN paidAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE transactions ADD COLUMN paidMethod TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE purchases ADD COLUMN paidAmount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE purchases ADD COLUMN paidAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE purchases SET paidAmount = totalAmount, paidAt = timestamp WHERE status = 'PAID'")
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

        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE shifts ADD COLUMN branchId INTEGER NOT NULL DEFAULT 1")
                database.execSQL("UPDATE shifts SET branchId = COALESCE((SELECT branchId FROM users WHERE users.id = shifts.userId), 1)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_shifts_branchId` ON `shifts` (`branchId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_shifts_status` ON `shifts` (`status`)")
            }
        }

        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `stock_transfers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fromBranchId` INTEGER NOT NULL,
                        `fromBranchName` TEXT NOT NULL,
                        `toBranchId` INTEGER NOT NULL,
                        `toBranchName` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `userId` INTEGER NOT NULL,
                        `userName` TEXT NOT NULL,
                        `totalItems` INTEGER NOT NULL,
                        `totalQuantity` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `stock_transfer_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transferId` INTEGER NOT NULL,
                        `sourceProductId` INTEGER NOT NULL,
                        `destinationProductId` INTEGER NOT NULL,
                        `productName` TEXT NOT NULL,
                        `barcode` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        FOREIGN KEY(`transferId`) REFERENCES `stock_transfers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_transfers_fromBranchId` ON `stock_transfers` (`fromBranchId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_transfers_toBranchId` ON `stock_transfers` (`toBranchId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_transfers_timestamp` ON `stock_transfers` (`timestamp`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_transfer_items_transferId` ON `stock_transfer_items` (`transferId`)")
            }
        }

        private val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE customers ADD COLUMN birthDate INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE customers ADD COLUMN lastVisitDate INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE customers ADD COLUMN totalSpending INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_name` ON `customers` (`name`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_points` ON `customers` (`points`)")
            }
        }

        private val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `kitchen_tickets` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transactionId` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `tableName` TEXT NOT NULL,
                        `orderType` TEXT NOT NULL,
                        `customerName` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `itemsJson` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_kitchen_tickets_status` ON `kitchen_tickets` (`status`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_kitchen_tickets_timestamp` ON `kitchen_tickets` (`timestamp`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_kitchen_tickets_transactionId` ON `kitchen_tickets` (`transactionId`)")
            }
        }

        // FIX: MIGRATION UNTUK MENAMBAHKAN KOLOM CATATAN DI SHIFT
        private val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE shifts ADD COLUMN note TEXT NOT NULL DEFAULT ''")
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
                    MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, 
                    MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27,
                    MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31,
                    MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34 // FIX: Daftarkan migrasi baru
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