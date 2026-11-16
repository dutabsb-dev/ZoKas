package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hold_orders",
    indices = [Index(value = ["timestamp"]), Index(value = ["branchId"])]
)
data class HoldOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val branchId: Long = 1L, // TAMBAHAN
    val tableName: String,
    val customerName: String,
    val cartJson: String, 
    val timestamp: Long = System.currentTimeMillis()
)