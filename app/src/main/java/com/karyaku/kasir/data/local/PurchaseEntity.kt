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
    val status: String = "UNPAID" // PAID / UNPAID
)