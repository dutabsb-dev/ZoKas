package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    // ================================================================
    // PHASE 0 — SESI 1: FIX SILENT FAILURE
    // insertProduct ABORT (fail loud), updateProduct UPDATE in-place,
    // saveProduct @Transaction wrapper. (Dipertahankan di Sesi 3)
    // ================================================================

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity): Int

    @Transaction
    suspend fun saveProduct(product: ProductEntity): Long {
        if (product.id == 0L) {
            return insertProduct(product)
        }
        val affected = updateProduct(product)
        return if (affected > 0) product.id else insertProduct(product)
    }

    // ================================================================
    // Query umum — tidak diubah.
    // ================================================================

    @Query("SELECT * FROM products ORDER BY id DESC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE branchId = :branchId ORDER BY id DESC")
    fun getProductsByBranch(branchId: Long): Flow<List<ProductEntity>>

    @Query("UPDATE products SET stock = :newStock, isSynced = 0, updatedAt = :updatedAt WHERE id = :productId")
    suspend fun updateStock(productId: Long, newStock: Int, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    // ================================================================
    // PHASE 0 — SESI 3: Barcode lookup aman
    // ================================================================
    // Masalah lama:
    //   getProductByBarcode() pakai LIMIT 1 → jika ada 2+ produk
    //   dengan barcode sama (bisa terjadi via import Excel), query
    //   mengembalikan produk arbitrer → SALAH TAMBAH KE CART.
    //
    // Perbaikan:
    //   - getProductsByBarcodeList(): kembalikan SEMUA match,
    //     filter out barcode kosong (karena "" bukan barcode valid).
    //   - Caller (processBarcodeScan) wajib deteksi duplikat.
    //
    // Method lama getProductByBarcode dipertahankan untuk kompatibilitas
    // (tidak dipakai lagi oleh processBarcodeScan, tapi jaga-jaga jika
    // ada kode lain yang memanggil).
    // ================================================================

    @Query("SELECT * FROM products WHERE barcode = :barcode AND barcode != ''")
    suspend fun getProductsByBarcodeList(barcode: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE barcode = :barcode AND barcode != '' LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    // Bahan Baku & Resep — tidak diubah.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawMaterial(material: RawMaterialEntity)

    @Query("SELECT * FROM raw_materials")
    fun getAllRawMaterials(): Flow<List<RawMaterialEntity>>

    @Query("UPDATE raw_materials SET stock = :newStock WHERE id = :id")
    suspend fun updateRawMaterialStock(id: Long, newStock: Double)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductBom(bom: ProductBomEntity)

    @Query("SELECT * FROM product_boms WHERE productId = :productId")
    suspend fun getBomForProduct(productId: Long): List<ProductBomEntity>
}