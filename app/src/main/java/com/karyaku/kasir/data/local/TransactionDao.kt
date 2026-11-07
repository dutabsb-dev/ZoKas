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

    @Query("UPDATE transactions SET status = 'VOIDED' WHERE id = :transactionId")
    suspend fun voidTransaction(transactionId: Long)

    // FUNGSI BARU: REFUND & BAYAR BON
    @Query("UPDATE transactions SET status = 'REFUNDED' WHERE id = :transactionId")
    suspend fun refundTransaction(transactionId: Long)

    @Query("UPDATE transactions SET status = 'SUCCESS', paymentMethod = 'CASH', cashReceived = :amount, changeAmount = :change WHERE id = :transactionId")
    suspend fun payUnpaidTransaction(transactionId: Long, amount: Long, change: Long)
}