package com.karyaku.kasir.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Tambahkan TransactionEntity dan naikkan version menjadi 2
@Database(entities = [ProductEntity::class, TransactionEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao // Tambahkan ini

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_database"
                )
                .fallbackToDestructiveMigration() // Agar tidak crash saat versi DB naik
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}