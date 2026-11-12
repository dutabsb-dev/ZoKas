package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_opnames")
data class StockOpnameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val userId: Long,
    val userName: String,
    val totalItemsChecked: Int,
    val totalLossValue: Long, // Nilai kerugian (jika fisik < sistem)
    val totalGainValue: Long  // Nilai keuntungan (jika fisik > sistem)
)