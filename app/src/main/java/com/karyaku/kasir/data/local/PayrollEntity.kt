package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payrolls")
data class PayrollEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val userName: String,
    val period: String, // Cth: "November 2026"
    val baseSalary: Long,
    val commission: Long,
    val totalSalary: Long,
    val status: String = "UNPAID", // UNPAID / PAID
    val timestamp: Long = System.currentTimeMillis(),
    val branchId: Long = 1L
)