package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Long,
    val stock: Int = 0,
    val category: String = "Umum",
    val costPrice: Long = 0 // <--- TAMBAHKAN INI (Harga Beli/Modal)
)