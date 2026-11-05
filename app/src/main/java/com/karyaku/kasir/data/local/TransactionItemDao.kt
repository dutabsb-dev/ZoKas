package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Class untuk menampung data barang terlaris
data class BestSellerData(
    val productName: String,
    val totalQty: Int,
    val totalRevenue: Long
)

@Dao
interface TransactionItemDao {
    @Insert
    suspend fun insertTransactionItems(items: List<TransactionItemEntity>)

    @Query("SELECT * FROM transaction_items WHERE transactionId = :transactionId")
    fun getItemsByTransaction(transactionId: Long): Flow<List<TransactionItemEntity>>

    // Fungsi untuk mengambil 5 produk terlaris (berdasarkan kuantitas terjual)
    @Query("""
        SELECT productName, SUM(quantity) as totalQty, SUM(subtotal) as totalRevenue 
        FROM transaction_items 
        GROUP BY productName 
        ORDER BY totalQty DESC 
        LIMIT 5
    """)
    fun getBestSellers(): Flow<List<BestSellerData>>
}