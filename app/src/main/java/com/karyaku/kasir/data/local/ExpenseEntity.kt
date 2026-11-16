package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["branchId"])]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val branchId: Long = 1L, // TAMBAHAN: Filter per cabang
    val category: String, 
    val amount: Long,
    val note: String,
    val timestamp: Long = System.currentTimeMillis(),
    val userId: Long,
    val userName: String
)