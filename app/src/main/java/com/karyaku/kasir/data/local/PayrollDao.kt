package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PayrollDao {
    @Insert
    suspend fun insertPayroll(payroll: PayrollEntity): Long

    @Query("UPDATE payrolls SET status = 'PAID' WHERE id = :payrollId")
    suspend fun markAsPaid(payrollId: Long)

    @Query("SELECT * FROM payrolls WHERE branchId = :branchId ORDER BY timestamp DESC")
    fun getAllPayrollsByBranch(branchId: Long): Flow<List<PayrollEntity>>
}