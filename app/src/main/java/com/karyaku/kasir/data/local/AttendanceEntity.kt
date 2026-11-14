package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance",
    indices = [Index(value = ["userId"]), Index(value = ["date"])]
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val userName: String,
    val date: String, // Format: "dd-MM-yyyy"
    val checkInTime: Long = System.currentTimeMillis(),
    val checkOutTime: Long? = null,
    val branchId: Long = 1L
)