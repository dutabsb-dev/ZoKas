package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * MODUL D-2 — Transfer Stok Antar Cabang
 *
 * Header dokumen transfer. Satu dokumen bisa berisi banyak item
 * (lihat StockTransferItemEntity).
 *
 * Instant transfer: begitu disimpan, stok source sudah berkurang dan
 * stok destination sudah bertambah. Tidak ada workflow pending/approve.
 *
 * Index:
 *   - fromBranchId untuk history per cabang asal
 *   - toBranchId untuk history per cabang tujuan
 *   - timestamp untuk sorting kronologis
 */
@Entity(
    tableName = "stock_transfers",
    indices = [
        Index(value = ["fromBranchId"]),
        Index(value = ["toBranchId"]),
        Index(value = ["timestamp"])
    ]
)
data class StockTransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromBranchId: Long,
    val fromBranchName: String,
    val toBranchId: Long,
    val toBranchName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val userId: Long,
    val userName: String,
    val totalItems: Int,      // jumlah baris item (bukan total qty)
    val totalQuantity: Int    // total qty gabungan semua item
)