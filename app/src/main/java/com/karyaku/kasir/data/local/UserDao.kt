package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Delete
    suspend fun deleteUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE pin = :pin LIMIT 1")
    suspend fun loginByPin(pin: String): UserEntity?

    @Query("SELECT * FROM users")
    suspend fun getAllUsersOnce(): List<UserEntity>

    @Query("SELECT * FROM users ORDER BY role DESC, name ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("UPDATE users SET pin = :newPin WHERE id = :userId")
    suspend fun updatePin(userId: Long, newPin: String)

    // FITUR BARU: Update Komisi Karyawan
    @Query("UPDATE users SET totalCommission = totalCommission + :commission WHERE id = :userId")
    suspend fun addCommission(userId: Long, commission: Long)

    @Insert
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<AuditLogEntity>>

    @Insert
    suspend fun insertShift(shift: ShiftEntity): Long

    @Query("SELECT * FROM shifts WHERE status = 'OPEN' LIMIT 1")
    suspend fun getActiveShift(): ShiftEntity?

    @Query("UPDATE shifts SET endTime = :endTime, expectedCash = :expectedCash, closingCash = :closingCash, discrepancy = :discrepancy, status = 'CLOSED' WHERE id = :shiftId")
    suspend fun closeShift(shiftId: Long, endTime: Long, expectedCash: Long, closingCash: Long, discrepancy: Long)
    
    @Query("SELECT * FROM shifts ORDER BY startTime DESC")
    fun getAllShifts(): Flow<List<ShiftEntity>>
}