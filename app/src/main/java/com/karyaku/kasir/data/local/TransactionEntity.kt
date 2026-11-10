package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["timestamp"]), Index(value = ["status"])]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val totalPrice: Long,
    val cashReceived: Long,
    val changeAmount: Long,
    val customerName: String = "Pelanggan Umum",
    val status: String = "SUCCESS", // SUCCESS, UNPAID, VOIDED, REFUNDED
    val totalCost: Long = 0,
    val tableName: String = "Take-Away",
    val voucherCode: String = "",
    val taxAmount: Long = 0,          
    val paymentMethod: String = "CASH", 
    val cashierName: String = "Kasir",
    val totalCommission: Long = 0L
)