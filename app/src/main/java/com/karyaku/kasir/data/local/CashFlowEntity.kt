package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cash_flows",
    indices = [Index(value = ["branchId"]), Index(value = ["shiftId"])]
)
data class CashFlowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long,
    val branchId: Long = 1L, // TAMBAHAN: Filter per cabang
    val type: String, 
    val amount: Long,
    val note: String, 
    val timestamp: Long = System.currentTimeMillis(),
    val userId: Long,
    val userName: String
)