package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transaction_items")
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long, // Relasi ke tabel transactions
    val productName: String,
    val price: Long,
    val quantity: Int,
    val subtotal: Long
)