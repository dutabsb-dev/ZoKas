package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE userId = :userId AND date = :date LIMIT 1")
    suspend fun getTodayAttendance(userId: Long, date: String): AttendanceEntity?

    @Insert
    suspend fun insertCheckIn(attendance: AttendanceEntity)

    @Query("UPDATE attendance SET checkOutTime = :checkOutTime WHERE userId = :userId AND date = :date")
    suspend fun updateCheckOut(userId: Long, date: String, checkOutTime: Long)

    @Query("SELECT * FROM attendance WHERE branchId = :branchId ORDER BY checkInTime DESC")
    fun getAllAttendanceByBranch(branchId: Long): Flow<List<AttendanceEntity>>
}