package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Long, // Harga dalam Rupiah (contoh: 15000)
    val stock: Int = 0,
    val category: String = "Umum"
)