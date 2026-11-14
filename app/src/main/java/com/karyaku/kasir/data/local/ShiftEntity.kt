package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shifts",
    indices = [Index(value = ["branchId"]), Index(value = ["status"])]
)
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val userName: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val openingCash: Long,
    val expectedCash: Long = 0,
    val closingCash: Long? = null,
    val discrepancy: Long? = null,
    val status: String = "OPEN",
    val branchId: Long = 1L,
    // TAMBAHAN BARU: Untuk menyimpan catatan selisih
    val note: String = "" 
)