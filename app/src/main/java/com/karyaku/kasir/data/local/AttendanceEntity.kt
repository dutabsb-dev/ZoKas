package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance",
    indices = [Index(value = ["userId"]), Index(value = ["date"]), Index(value = ["branchId"])]
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val userName: String,
    val date: String,
    val checkInTime: Long,
    val checkOutTime: Long? = null,
    val branchId: Long = 1L
)