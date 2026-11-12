package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Insert
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Insert
    suspend fun insertPurchaseItems(items: List<PurchaseItemEntity>)

    @Query("SELECT * FROM purchases ORDER BY timestamp DESC")
    fun getAllPurchases(): Flow<List<PurchaseEntity>>

    @Query("UPDATE purchases SET status = 'PAID' WHERE id = :purchaseId")
    suspend fun markAsPaid(purchaseId: Long)

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    fun getPurchaseItems(purchaseId: Long): Flow<List<PurchaseItemEntity>>
}