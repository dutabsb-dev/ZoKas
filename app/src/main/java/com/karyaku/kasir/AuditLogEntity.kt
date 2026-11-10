package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_logs",
    indices = [Index(value = ["timestamp"])]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val userName: String,
    val action: String,
    val actionType: String = "SYSTEM", // AUTH, TRANSACTION, INVENTORY, SYSTEM
    val timestamp: Long = System.currentTimeMillis()
)