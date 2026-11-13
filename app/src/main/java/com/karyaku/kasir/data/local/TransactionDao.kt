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

    // ================================================================
    // MODUL D-1: Filter transaksi per cabang.
    // ================================================================
    @Query("SELECT * FROM transactions WHERE branchId = :branchId ORDER BY timestamp DESC")
    fun getTransactionsByBranch(branchId: Long): Flow<List<TransactionEntity>>

    @Query("UPDATE transactions SET status = 'VOIDED' WHERE id = :transactionId")
    suspend fun voidTransaction(transactionId: Long)

    @Query("UPDATE transactions SET status = 'REFUNDED' WHERE id = :transactionId")
    suspend fun refundTransaction(transactionId: Long)

    @Query("""
        UPDATE transactions 
        SET status = 'SUCCESS', 
            paidAt = :paidAt, 
            paidMethod = :paidMethod, 
            cashReceived = :cashReceived, 
            changeAmount = :changeAmount 
        WHERE id = :transactionId
    """)
    suspend fun updateTransactionStatus(
        transactionId: Long, 
        cashReceived: Long, 
        changeAmount: Long, 
        paidMethod: String, 
        paidAt: Long = System.currentTimeMillis()
    )
}