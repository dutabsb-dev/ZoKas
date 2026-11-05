package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CashFlowDao {
    @Insert
    suspend fun insertCashFlow(cashFlow: CashFlowEntity)

    // Ambil semua kas keluar/masuk berdasarkan Shift ID
    @Query("SELECT * FROM cash_flows WHERE shiftId = :shiftId ORDER BY timestamp DESC")
    fun getCashFlowByShift(shiftId: Long): Flow<List<CashFlowEntity>>

    // Hitung total kas keluar (OUT) pada shift berjalan
    @Query("SELECT SUM(amount) FROM cash_flows WHERE shiftId = :shiftId AND type = 'OUT'")
    suspend fun getTotalCashOut(shiftId: Long): Long?

    // Hitung total kas masuk tambahan (IN) pada shift berjalan
    @Query("SELECT SUM(amount) FROM cash_flows WHERE shiftId = :shiftId AND type = 'IN'")
    suspend fun getTotalCashIn(shiftId: Long): Long?
}