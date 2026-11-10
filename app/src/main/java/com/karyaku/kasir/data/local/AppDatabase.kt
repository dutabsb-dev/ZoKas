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
        ProductBomEntity::class
    ], 
    version = 19, // UPDATE VERSI
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // MIGRASI AMAN: Menambahkan Index untuk performa query
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Index untuk Product (Barcode sering di-scan)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_products_barcode ON products(barcode)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_products_name ON products(name)")
                
                // Index untuk Transaction (Sering difilter berdasarkan tanggal/status)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_timestamp ON transactions(timestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_status ON transactions(status)")
                
                // Index untuk TransactionItem
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_items_transactionId ON transaction_items(transactionId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_items_productId ON transaction_items(productId)")
                
                // Index untuk AuditLog
                database.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_timestamp ON audit_logs(timestamp)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_database"
                )
                .addMigrations(MIGRATION_18_19) // Terapkan migrasi
                // .fallbackToDestructiveMigration() // DIHAPUS: Demi keamanan data user
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