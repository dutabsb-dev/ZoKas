package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Query("SELECT * FROM products ORDER BY id DESC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    // FITUR BARU: Filter Berdasarkan Cabang
    @Query("SELECT * FROM products WHERE branchId = :branchId ORDER BY id DESC")
    fun getProductsByBranch(branchId: Long): Flow<List<ProductEntity>>

    @Query("UPDATE products SET stock = :newStock, isSynced = 0, updatedAt = :updatedAt WHERE id = :productId")
    suspend fun updateStock(productId: Long, newStock: Int, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

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