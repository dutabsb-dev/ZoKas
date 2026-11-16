package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_opnames",
    indices = [Index(value = ["timestamp"]), Index(value = ["branchId"])]
)
data class StockOpnameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val branchId: Long = 1L, // TAMBAHAN
    val timestamp: Long = System.currentTimeMillis(),
    val userId: Long,
    val userName: String,
    val totalItemsChecked: Int,
    val totalLossValue: Long, 
    val totalGainValue: Long  
)