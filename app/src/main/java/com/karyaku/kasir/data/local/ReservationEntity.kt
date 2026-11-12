package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reservations")
data class ReservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerName: String,
    val phone: String = "",
    val dateTime: Long,
    val resourceName: String, // Nama Meja, Nama Terapis, atau Ruangan
    val status: String = "BOOKED", // BOOKED, ARRIVED, CANCELLED
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)