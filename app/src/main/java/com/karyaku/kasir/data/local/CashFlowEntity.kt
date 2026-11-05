package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cash_flows")
data class CashFlowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long, // Terhubung ke shift yang sedang aktif
    val type: String, // "IN" (Kas Masuk / Tambah Modal) atau "OUT" (Kas Keluar)
    val amount: Long,
    val note: String, // Contoh: "Beli Galon", "Bayar Kurir"
    val timestamp: Long = System.currentTimeMillis(),
    val userId: Long,
    val userName: String
)