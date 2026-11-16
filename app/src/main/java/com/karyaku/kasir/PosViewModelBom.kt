package com.karyaku.kasir

import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.ProductBomEntity
import com.karyaku.kasir.data.local.RawMaterialEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun PosViewModel.saveBomBatch(
    productId: Long, 
    boms: List<Pair<RawMaterialEntity, Double>>, 
    onSuccess: () -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            // 1. Hapus semua resep lama terlebih dahulu (agar tidak menumpuk)
            productDao.deleteBomForProduct(productId)
            
            // 2. Simpan resep baru secara massal
            boms.forEach { (material, qty) ->
                productDao.insertProductBom(
                    ProductBomEntity(
                        productId = productId, 
                        rawMaterialId = material.id, 
                        quantityUsed = qty
                    )
                )
            }
            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun PosViewModel.deleteBomProduct(productId: Long, onSuccess: () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            productDao.deleteBomForProduct(productId)
            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

suspend fun PosViewModel.getBomForProductOnce(productId: Long): List<ProductBomEntity> {
    return try {
        productDao.getBomForProduct(productId)
    } catch (e: Exception) {
        emptyList()
    }
}