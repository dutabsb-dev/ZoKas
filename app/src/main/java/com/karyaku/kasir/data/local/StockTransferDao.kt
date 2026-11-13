package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StockTransferDao {

    @Insert
    suspend fun insertTransfer(transfer: StockTransferEntity): Long

    @Insert
    suspend fun insertTransferItems(items: List<StockTransferItemEntity>)

    /**
     * Semua history transfer (lintas cabang) — untuk OWNER melihat
     * semua aktivitas transfer.
     */
    @Query("SELECT * FROM stock_transfers ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<StockTransferEntity>>

    /**
     * History transfer yang melibatkan cabang tertentu, baik sebagai
     * asal (kirim) maupun tujuan (terima).
     * Berguna untuk tab "Riwayat" di dialog transfer per cabang.
     */
    @Query("""
        SELECT * FROM stock_transfers 
        WHERE fromBranchId = :branchId OR toBranchId = :branchId 
        ORDER BY timestamp DESC
    """)
    fun getTransfersForBranch(branchId: Long): Flow<List<StockTransferEntity>>

    /**
     * Item per transfer — dipakai saat user membuka detail history.
     */
    @Query("SELECT * FROM stock_transfer_items WHERE transferId = :transferId")
    fun getItemsForTransfer(transferId: Long): Flow<List<StockTransferItemEntity>>
}