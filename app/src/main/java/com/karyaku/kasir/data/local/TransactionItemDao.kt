package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionItemDao {
    @Insert
    suspend fun insertTransactionItems(items: List<TransactionItemEntity>)

    // Ambil rincian barang berdasarkan ID Transaksi
    @Query("SELECT * FROM transaction_items WHERE transactionId = :transactionId")
    fun getItemsByTransaction(transactionId: Long): Flow<List<TransactionItemEntity>>
}