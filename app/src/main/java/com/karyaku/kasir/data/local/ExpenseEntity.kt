package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // Gaji, Listrik, Sewa, Operasional, dll
    val amount: Long,
    val note: String,
    val timestamp: Long = System.currentTimeMillis(),
    val userId: Long,
    val userName: String
)