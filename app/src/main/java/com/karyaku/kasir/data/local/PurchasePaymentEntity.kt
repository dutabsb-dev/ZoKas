package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * MODUL E — Hutang Supplier Cicilan
 *
 * Mencatat setiap pembayaran (cicilan atau pelunasan penuh) ke supplier.
 * Satu PO bisa punya banyak baris di sini.
 *
 * FK ke purchases dengan CASCADE: hapus PO → hapus semua riwayat bayarnya.
 * Index purchaseId untuk query riwayat per PO.
 */
@Entity(
    tableName = "purchase_payments",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["purchaseId"])]
)
data class PurchasePaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val amount: Long,
    val paymentMethod: String = "CASH", // CASH / Bank Transfer / QRIS
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val userId: Long,
    val userName: String
)