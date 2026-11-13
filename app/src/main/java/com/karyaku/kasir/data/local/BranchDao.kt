package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BranchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBranch(branch: BranchEntity): Long

    // ================================================================
    // MODUL D-1: CRUD cabang.
    // ================================================================
    @Update
    suspend fun updateBranch(branch: BranchEntity)

    @Query("DELETE FROM branches WHERE id = :branchId AND isMainBranch = 0")
    suspend fun deleteBranch(branchId: Long)

    @Query("SELECT * FROM branches ORDER BY isMainBranch DESC, name ASC")
    fun getAllBranches(): Flow<List<BranchEntity>>

    @Query("SELECT * FROM branches WHERE id = :id LIMIT 1")
    suspend fun getBranchById(id: Long): BranchEntity?

    @Query("SELECT COUNT(*) FROM branches")
    suspend fun getBranchCount(): Int

    /**
     * Hitung jumlah transaksi di cabang tertentu.
     * Dipakai di ViewModel untuk menolak hapus cabang yang masih punya
     * transaksi (menghindari data orphan tanpa branch).
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE branchId = :branchId")
    suspend fun getTransactionCountForBranch(branchId: Long): Int

    /**
     * Cek apakah cabang punya produk.
     * Jika ya, hapus cabang akan orphan produk (karena produk punya
     * branchId tapi tidak ada FK constraint).
     */
    @Query("SELECT COUNT(*) FROM products WHERE branchId = :branchId")
    suspend fun getProductCountForBranch(branchId: Long): Int
}