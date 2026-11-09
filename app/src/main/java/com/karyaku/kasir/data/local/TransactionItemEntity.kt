package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transaction_items")
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val productName: String,
    val price: Long,
    val costPrice: Long = 0,
    val quantity: Int,
    val subtotal: Long,
    val notes: String = "",
    // FITUR BARU
    val serialNumber: String = "",
    val assignedStaff: String = ""
)