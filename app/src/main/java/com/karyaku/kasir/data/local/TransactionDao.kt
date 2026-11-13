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

    @Query("UPDATE transactions SET status = 'REFUNDED' WHERE id = :transactionId")
    suspend fun refundTransaction(transactionId: Long)

    // ================================================================
    // PHASE 0 — SESI 2: Perbaikan pelunasan BON
    // ================================================================
    // Masalah lama:
    //   - paymentMethod ditimpa jadi 'CASH' → history berubah mundur,
    //     laporan shift lama jadi salah (seolah ada cash masuk kemarin).
    //   - Tidak ada record kapan BON dilunasi.
    //
    // Perbaikan:
    //   - status       → 'SUCCESS' (transaksi lunas)
    //   - paymentMethod TIDAK diubah (tetap 'BON' sebagai penanda history)
    //   - paidAt       diisi (kapan lunas)
    //   - paidMethod   diisi (metode pelunasan)
    //   - cashReceived & changeAmount diisi untuk struk pelunasan
    //
    // Caller (PosViewModel.payUnpaidTransaction) WAJIB pakai signature ini.
    // ================================================================
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