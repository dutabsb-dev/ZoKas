package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [Index(value = ["barcode"], unique = false), Index(value = ["name"])]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Long,
    val stock: Int = 0,
    val category: String = "Umum",
    val costPrice: Long = 0,
    val barcode: String = "",
    val variations: String = "", 
    val commissionPerItem: Long = 0L,
    val promoType: String = "NONE",
    val promoMinQty: Int = 0,
    val promoValue: Long = 0L,
    val batchNumber: String = "",
    val expiryDate: Long = 0L,
    val isService: Boolean = false,
    val isCombo: Boolean = false,
    val comboPrice: Long = 0L,
    val imagePath: String = "",
    // FITUR BARU: Harga Grosir
    val wholesalePrice: Long = 0L,
    val wholesaleMinQty: Int = 0,
    val branchId: Long = 1L,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)