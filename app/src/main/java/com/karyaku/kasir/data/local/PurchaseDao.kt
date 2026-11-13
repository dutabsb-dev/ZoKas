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

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    fun getPurchaseItems(purchaseId: Long): Flow<List<PurchaseItemEntity>>

    // ================================================================
    // MODUL E — Hutang Supplier Cicilan
    // ================================================================

    @Query("SELECT * FROM purchases WHERE supplierId = :supplierId ORDER BY timestamp DESC")
    fun getPurchasesBySupplier(supplierId: Long): Flow<List<PurchaseEntity>>

    /**
     * PO yang belum lunas penuh (UNPAID atau PARTIAL).
     * Urut dari yang paling lama (FIFO) — cocok untuk prinsip pembayaran
     * hutang terlama dulu.
     */
    @Query("SELECT * FROM purchases WHERE status != 'PAID' ORDER BY timestamp ASC")
    fun getOutstandingPurchases(): Flow<List<PurchaseEntity>>

    /**
     * Total sisa hutang gabungan semua supplier (UNPAID + PARTIAL).
     * SUM otomatis mengabaikan NULL; kalau tidak ada PO outstanding,
     * hasilnya null (di-handle di ViewModel).
     */
    @Query("SELECT SUM(totalAmount - paidAmount) FROM purchases WHERE status != 'PAID'")
    fun getTotalOutstandingDebt(): Flow<Long?>

    /**
     * Update status + nominal pembayaran pada satu PO.
     * Caller WAJIB pakai pola @Transaction:
     *   1. insertPayment() untuk mencatat cicilan.
     *   2. updatePayment() untuk sinkron paidAmount + status.
     */
    @Query("UPDATE purchases SET paidAmount = :paidAmount, status = :status, paidAt = :paidAt WHERE id = :purchaseId")
    suspend fun updatePayment(purchaseId: Long, paidAmount: Long, status: String, paidAt: Long)

    // ================================================================
    // LEGACY — markAsPaid lama dipertahankan untuk kompatibilitas API,
    // tapi sudah tidak dipakai oleh SupplierDebtDialog maupun payPurchase
    // versi baru. Akan dihapus pada cleanup berikutnya.
    // ================================================================
    @Deprecated("Gunakan updatePayment() dengan parameter lengkap agar paidAmount tersinkron.")
    @Query("UPDATE purchases SET status = 'PAID', paidAmount = totalAmount, paidAt = :paidAt WHERE id = :purchaseId")
    suspend fun markAsPaid(purchaseId: Long, paidAt: Long = System.currentTimeMillis())
}