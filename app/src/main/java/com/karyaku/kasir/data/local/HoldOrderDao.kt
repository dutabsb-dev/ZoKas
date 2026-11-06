package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HoldOrderDao {
    @Insert
    suspend fun insertHoldOrder(order: HoldOrderEntity): Long

    @Query("SELECT * FROM hold_orders ORDER BY timestamp DESC")
    fun getAllHoldOrders(): Flow<List<HoldOrderEntity>>

    @Query("DELETE FROM hold_orders WHERE id = :id")
    suspend fun deleteHoldOrder(id: Long)
}