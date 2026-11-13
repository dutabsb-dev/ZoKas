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
    val status: String = "SUCCESS", 
    val totalCost: Long = 0,
    val tableName: String = "Take-Away",
    val voucherCode: String = "",
    val taxAmount: Long = 0,          
    val paymentMethod: String = "CASH", 
    val cashierName: String = "Kasir",
    val totalCommission: Long = 0L,
    val orderType: String = "Dine-In",
    val serviceCharge: Long = 0L,
    val pickupDate: Long = 0L,
    val branchId: Long = 1L,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    // ================================================================
    // PHASE 0 — SESI 2: Tracking pelunasan BON
    // ================================================================
    // paidAt     : timestamp saat BON dilunasi (0L = belum lunas / bukan BON)
    // paidMethod : metode pelunasan (CASH / QRIS / Bank Transfer)
    // paymentMethod asli ("BON") TIDAK diubah agar history tidak mundur.
    // ================================================================
    val paidAt: Long = 0L,
    val paidMethod: String = ""
)