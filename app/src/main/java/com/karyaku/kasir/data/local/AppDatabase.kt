package com.karyaku.kasir.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        RawMaterialEntity::class, // TAMBAHAN
        ProductBomEntity::class   // TAMBAHAN
    ], 
    version = 17, // UPDATE VERSI
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_database"
                ).fallbackToDestructiveMigration().build()
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