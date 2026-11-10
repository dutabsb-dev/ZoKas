package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transaction_items",
    indices = [Index(value = ["transactionId"]), Index(value = ["productId"])]
)
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val productId: Long,
    val productName: String,
    val price: Long,
    val costPrice: Long = 0,
    val quantity: Int,
    val subtotal: Long,
    val notes: String = "",
    val serialNumber: String = "",
    val assignedStaff: String = "",
    val isService: Boolean = false 
)