package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transaction_items")
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val productName: String,
    val price: Long, // Harga Jual
    val costPrice: Long = 0, // <--- TAMBAHKAN INI (Modal saat transaksi terjadi)
    val quantity: Int,
    val subtotal: Long
)