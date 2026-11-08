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
    val costPrice: Long = 0,
    val barcode: String = "",
    // FITUR BARU: Varian (cth: "S,M,L" atau "Merah,Biru")
    val variations: String = "",
    // FITUR BARU: Komisi Kasir (Nominal Rp per item)
    val commissionPerItem: Long = 0L,
    // FITUR BARU: Promo Engine ("NONE", "BOGO", "TIERED")
    val promoType: String = "NONE",
    val promoMinQty: Int = 0,
    val promoValue: Long = 0L, // Jika TIERED, isi persen diskon (cth: 10 = 10%)
    // FITUR BARU: Batch & Expiry (FEFO)
    val batchNumber: String = "",
    val expiryDate: Long = 0L
)