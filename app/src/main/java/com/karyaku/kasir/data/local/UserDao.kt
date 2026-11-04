package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert
    suspend fun insertUser(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE pin = :pin LIMIT 1")
    suspend fun loginByPin(pin: String): UserEntity?

    @Query("SELECT * FROM users")
    suspend fun getAllUsersOnce(): List<UserEntity>

    // Audit Logs
    @Insert
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<AuditLogEntity>>
}