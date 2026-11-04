package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val userName: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val openingCash: Long, // Modal awal
    val expectedCash: Long = 0, // Uang yang seharusnya ada (sistem)
    val closingCash: Long? = null, // Uang fisik yang dihitung kasir (blind closing)
    val discrepancy: Long? = null, // Selisih
    val status: String = "OPEN" // "OPEN" atau "CLOSED"
)