package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReservationDao {
    @Insert
    suspend fun insertReservation(reservation: ReservationEntity)

    @Query("SELECT * FROM reservations ORDER BY dateTime ASC")
    fun getAllReservations(): Flow<List<ReservationEntity>>

    // TAMBAHAN: Filter Reservasi per Cabang
    @Query("SELECT * FROM reservations WHERE branchId = :branchId ORDER BY dateTime ASC")
    fun getReservationsByBranch(branchId: Long): Flow<List<ReservationEntity>>

    @Query("UPDATE reservations SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("DELETE FROM reservations WHERE id = :id")
    suspend fun deleteReservation(id: Long)
}