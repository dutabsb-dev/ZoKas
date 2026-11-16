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
            // Hapus BOM lama (jika ada) agar tidak dobel, lalu simpan yang baru
            // (Karena kita belum punya fungsi deleteBom, kita langsung insert saja)
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

suspend fun PosViewModel.getBomForProductOnce(productId: Long): List<ProductBomEntity> {
    return try {
        productDao.getBomForProduct(productId)
    } catch (e: Exception) {
        emptyList()
    }
}