package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchases",
    indices = [Index(value = ["branchId"]), Index(value = ["supplierId"])]
)
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val branchId: Long = 1L, // TAMBAHAN: Filter per cabang
    val supplierId: Long,
    val supplierName: String,
    val invoiceNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val totalAmount: Long,
    val status: String = "UNPAID", // UNPAID / PARTIAL / PAID
    val paidAmount: Long = 0L,
    val paidAt: Long = 0L
)