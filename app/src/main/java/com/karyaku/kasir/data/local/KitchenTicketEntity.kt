package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * MODUL B — KDS (Kitchen Display System)
 *
 * Menyimpan antrean pesanan untuk koki.
 * Status:
 *   - PENDING : Baru masuk, belum dimasak.
 *   - COOKING : Sedang dimasak.
 *   - READY   : Selesai dimasak, siap disajikan.
 *
 * `itemsJson` menyimpan snapshot detail pesanan (nama, qty, notes)
 * dalam format JSON agar KDS tidak perlu join tabel transaksi.
 */
@Entity(
    tableName = "kitchen_tickets",
    indices = [
        Index(value = ["status"]),
        Index(value = ["timestamp"]),
        Index(value = ["transactionId"])
    ]
)
data class KitchenTicketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val tableName: String,
    val orderType: String,
    val customerName: String,
    val status: String = "PENDING", // PENDING, COOKING, READY
    val itemsJson: String
)