package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pin: String,
    val role: String // "OWNER" atau "CASHIER"
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val userName: String,
    val action: String,
    val timestamp: Long = System.currentTimeMillis()
)