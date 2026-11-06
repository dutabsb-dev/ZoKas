package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hold_orders")
data class HoldOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableName: String,
    val customerName: String,
    val cartJson: String, // Menyimpan list keranjang dalam bentuk JSON
    val timestamp: Long = System.currentTimeMillis()
)