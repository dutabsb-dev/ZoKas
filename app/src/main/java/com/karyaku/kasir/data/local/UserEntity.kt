package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pin: String,
    val role: String,
    val totalCommission: Long = 0L,
    val branchId: Long = 1L,
    // TAMBAHAN BARU: Gaji Pokok
    val baseSalary: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)