package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    // TAMBAHKAN FUNGSI INI UNTUK MENGUBAH STATUS JADI VOID
    @Query("UPDATE transactions SET status = 'VOIDED' WHERE id = :transactionId")
    suspend fun voidTransaction(transactionId: Long)
}