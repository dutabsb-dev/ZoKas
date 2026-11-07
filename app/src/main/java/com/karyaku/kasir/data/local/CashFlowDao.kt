package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CashFlowDao {
    @Insert
    suspend fun insertCashFlow(cashFlow: CashFlowEntity)

    // TAMBAHKAN FUNGSI INI UNTUK MEMPERBAIKI ERROR BUILD
    @Query("SELECT * FROM cash_flows ORDER BY timestamp DESC")
    fun getAllCashFlows(): Flow<List<CashFlowEntity>>

    @Query("SELECT SUM(amount) FROM cash_flows WHERE type = 'OUT' AND shiftId = :shiftId")
    suspend fun getTotalCashOut(shiftId: Long): Long?

    @Query("SELECT SUM(amount) FROM cash_flows WHERE type = 'IN' AND shiftId = :shiftId")
    suspend fun getTotalCashIn(shiftId: Long): Long?
}