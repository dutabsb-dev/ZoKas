package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_opname_items",
    foreignKeys = [
        ForeignKey(
            entity = StockOpnameEntity::class,
            parentColumns = ["id"],
            childColumns = ["opnameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["opnameId"]), Index(value = ["productId"])]
)
data class StockOpnameItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val opnameId: Long,
    val productId: Long,
    val productName: String,
    val systemStock: Int,
    val physicalStock: Int,
    val difference: Int
)