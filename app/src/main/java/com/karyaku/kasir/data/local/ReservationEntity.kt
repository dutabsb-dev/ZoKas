package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reservations",
    indices = [Index(value = ["dateTime"]), Index(value = ["branchId"])]
)
data class ReservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val branchId: Long = 1L, // TAMBAHAN
    val customerName: String,
    val phone: String = "",
    val dateTime: Long,
    val resourceName: String, 
    val status: String = "BOOKED", // BOOKED, ARRIVED, CANCELLED
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)