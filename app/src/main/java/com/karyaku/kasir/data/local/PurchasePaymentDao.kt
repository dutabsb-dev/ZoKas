package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchasePaymentDao {

    @Insert
    suspend fun insertPayment(payment: PurchasePaymentEntity): Long

    @Query("SELECT * FROM purchase_payments WHERE purchaseId = :purchaseId ORDER BY timestamp DESC")
    fun getPaymentsByPurchase(purchaseId: Long): Flow<List<PurchasePaymentEntity>>

    @Query("SELECT * FROM purchase_payments ORDER BY timestamp DESC")
    fun getAllPayments(): Flow<List<PurchasePaymentEntity>>
}