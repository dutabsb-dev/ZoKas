package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierId: Long,
    val supplierName: String,
    val invoiceNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val totalAmount: Long,
    val status: String = "UNPAID", // UNPAID / PARTIAL / PAID
    // ================================================================
    // MODUL E — Hutang Supplier Cicilan
    // ================================================================
    // paidAmount : total yang sudah dibayar (0 = UNPAID,
    //              antara 0..totalAmount = PARTIAL,
    //              >= totalAmount = PAID)
    // paidAt     : timestamp saat PO lunas penuh (0 = belum lunas)
    // Riwayat tiap cicilan ada di tabel purchase_payments.
    // ================================================================
    val paidAmount: Long = 0L,
    val paidAt: Long = 0L
)