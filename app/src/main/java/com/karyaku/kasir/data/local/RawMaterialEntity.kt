package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "raw_materials")
data class RawMaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val stock: Double, // Gunakan Double untuk gram/ml
    val unit: String, // "gram", "ml", "pcs"
    val costPerUnit: Long
)