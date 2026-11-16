package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KitchenTicketDao {
    @Insert
    suspend fun insertTicket(ticket: KitchenTicketEntity): Long

    @Query("SELECT * FROM kitchen_tickets WHERE status != 'READY' ORDER BY timestamp ASC")
    fun getActiveTickets(): Flow<List<KitchenTicketEntity>>

    // TAMBAHAN: Filter Dapur per Cabang
    @Query("SELECT * FROM kitchen_tickets WHERE status != 'READY' AND branchId = :branchId ORDER BY timestamp ASC")
    fun getActiveTicketsByBranch(branchId: Long): Flow<List<KitchenTicketEntity>>

    @Query("UPDATE kitchen_tickets SET status = :newStatus WHERE id = :ticketId")
    suspend fun updateStatus(ticketId: Long, newStatus: String)

    @Query("DELETE FROM kitchen_tickets WHERE status = 'READY'")
    suspend fun clearCompletedTickets()
}