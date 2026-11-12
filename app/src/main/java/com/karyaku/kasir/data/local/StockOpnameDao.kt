package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StockOpnameDao {
    @Insert
    suspend fun insertOpname(opname: StockOpnameEntity): Long

    @Insert
    suspend fun insertOpnameItems(items: List<StockOpnameItemEntity>)

    @Query("SELECT * FROM stock_opnames ORDER BY timestamp DESC")
    fun getAllOpnames(): Flow<List<StockOpnameEntity>>

    @Query("SELECT * FROM stock_opname_items WHERE opnameId = :opnameId")
    fun getOpnameItems(opnameId: Long): Flow<List<StockOpnameItemEntity>>
}