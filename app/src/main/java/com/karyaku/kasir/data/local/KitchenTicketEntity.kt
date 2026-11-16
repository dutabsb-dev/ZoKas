package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kitchen_tickets",
    indices = [
        Index(value = ["status"]),
        Index(value = ["timestamp"]),
        Index(value = ["transactionId"]),
        Index(value = ["branchId"]) // TAMBAHAN
    ]
)
data class KitchenTicketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val branchId: Long = 1L, // TAMBAHAN
    val timestamp: Long = System.currentTimeMillis(),
    val tableName: String,
    val orderType: String,
    val customerName: String,
    val status: String = "PENDING", // PENDING, COOKING, READY
    val itemsJson: String
)