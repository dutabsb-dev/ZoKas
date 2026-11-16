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

    // TAMBAHAN: Filter per cabang
    @Query("SELECT * FROM purchases WHERE branchId = :branchId ORDER BY timestamp DESC")
    fun getPurchasesByBranch(branchId: Long): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    fun getPurchaseItems(purchaseId: Long): Flow<List<PurchaseItemEntity>>

    @Query("SELECT * FROM purchases WHERE supplierId = :supplierId ORDER BY timestamp DESC")
    fun getPurchasesBySupplier(supplierId: Long): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE status != 'PAID' ORDER BY timestamp ASC")
    fun getOutstandingPurchases(): Flow<List<PurchaseEntity>>

    // TAMBAHAN: Outstanding per cabang
    @Query("SELECT * FROM purchases WHERE status != 'PAID' AND branchId = :branchId ORDER BY timestamp ASC")
    fun getOutstandingPurchasesByBranch(branchId: Long): Flow<List<PurchaseEntity>>

    @Query("SELECT SUM(totalAmount - paidAmount) FROM purchases WHERE status != 'PAID'")
    fun getTotalOutstandingDebt(): Flow<Long?>

    // TAMBAHAN: Total Hutang per cabang
    @Query("SELECT SUM(totalAmount - paidAmount) FROM purchases WHERE status != 'PAID' AND branchId = :branchId")
    fun getTotalOutstandingDebtByBranch(branchId: Long): Flow<Long?>

    @Query("UPDATE purchases SET paidAmount = :paidAmount, status = :status, paidAt = :paidAt WHERE id = :purchaseId")
    suspend fun updatePayment(purchaseId: Long, paidAmount: Long, status: String, paidAt: Long)

    @Deprecated("Gunakan updatePayment() dengan parameter lengkap agar paidAmount tersinkron.")
    @Query("UPDATE purchases SET status = 'PAID', paidAmount = totalAmount, paidAt = :paidAt WHERE id = :purchaseId")
    suspend fun markAsPaid(purchaseId: Long, paidAt: Long = System.currentTimeMillis())
}