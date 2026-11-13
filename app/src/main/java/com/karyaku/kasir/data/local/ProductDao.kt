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
    // Query umum
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
    @Query("SELECT * FROM products WHERE barcode = :barcode AND barcode != ''")
    suspend fun getProductsByBarcodeList(barcode: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE barcode = :barcode AND barcode != '' LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    // ================================================================
    // MODUL D-2: Query suspend untuk transfer stok
    // ================================================================
    // Kenapa perlu method ini?
    //   - transferStock() jalan di dalam withTransaction.
    //   - Query Flow (getProductsByBranch, getAllProducts) TIDAK BOLEH
    //     dipanggil .first() di dalam transaction — Room akan deadlock.
    //   - Jadi kita butuh query suspend (bukan Flow) untuk snapshot
    //     produk fresh di dalam transaction.
    // ================================================================
    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE branchId = :branchId")
    suspend fun getProductsByBranchOnce(branchId: Long): List<ProductEntity>

    // Bahan Baku & Resep
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