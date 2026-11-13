package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * MODUL D-2 — Item per Transfer
 *
 * Satu baris = satu produk yang ditransfer dengan qty tertentu.
 *
 * productName disimpan sebagai snapshot agar history tetap valid
 * meski produk di-rename di kemudian hari.
 *
 * sourceProductId : produk di cabang sumber (untuk audit).
 * destinationProductId : produk di cabang tujuan (bisa berbeda ID,
 *   karena tiap cabang punya baris produk sendiri di tabel products).
 *
 * FK ke stock_transfers dengan CASCADE: hapus header → hapus items.
 */
@Entity(
    tableName = "stock_transfer_items",
    foreignKeys = [
        ForeignKey(
            entity = StockTransferEntity::class,
            parentColumns = ["id"],
            childColumns = ["transferId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["transferId"])]
)
data class StockTransferItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transferId: Long,
    val sourceProductId: Long,
    val destinationProductId: Long,
    val productName: String,
    val barcode: String = "",
    val quantity: Int
)