package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val totalPrice: Long,
    val cashReceived: Long,
    val changeAmount: Long,
    val customerName: String = "Pelanggan Umum",
    val status: String = "SUCCESS",
    val totalCost: Long = 0,
    val tableName: String = "Take-Away", // <--- TAMBAHAN
    val voucherCode: String = ""        // <--- TAMBAHAN
)