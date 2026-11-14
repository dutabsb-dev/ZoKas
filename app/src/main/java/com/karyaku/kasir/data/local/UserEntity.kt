package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pin: String,
    val role: String, // "OWNER", "CASHIER", atau "STAFF"
    val totalCommission: Long = 0L,
    val branchId: Long = 1L,
    val baseSalary: Long = 0L,
    // TAMBAHAN BARU: Untuk enkripsi sidik jari/wajah (Keystore)
    val biometricKey: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)