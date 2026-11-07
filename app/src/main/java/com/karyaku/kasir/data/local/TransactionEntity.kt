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
    val status: String = "SUCCESS", // SUCCESS, VOIDED, REFUNDED, UNPAID
    val totalCost: Long = 0,
    val tableName: String = "Take-Away",
    val voucherCode: String = "",
    val taxAmount: Long = 0,          // <--- TAMBAHAN PAJAK
    val paymentMethod: String = "CASH", // CASH, BON, QRIS
    val cashierName: String = "Kasir"   // <--- TAMBAHAN UNTUK LAPORAN KASIR
)