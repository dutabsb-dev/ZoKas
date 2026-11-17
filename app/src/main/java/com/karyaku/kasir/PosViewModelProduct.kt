package com.karyaku.kasir

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun PosViewModel.deductVariantStock(variationsStr: String, targetVariantName: String, deductQty: Int): String {
    if (variationsStr.isBlank()) return ""
    val parts = variationsStr.split(",")
    val newParts = parts.map { part ->
        val pieces = part.split(":")
        if (pieces.size >= 2 && pieces[0].trim().equals(targetVariantName, ignoreCase = true)) {
            val price = pieces[1].trim(); val cost = pieces.getOrNull(2)?.trim() ?: "0"
            val currentStock = if (pieces.size >= 4) pieces[3].trim().toIntOrNull() ?: 0 else 999999 
            "${pieces[0]}:$price:$cost:${(currentStock - deductQty).coerceAtLeast(0)}"
        } else { part }
    }
    return newParts.joinToString(",")
}

fun PosViewModel.addProduct(
    name: String, price: Long, stock: Int, category: String, costPrice: Long, 
    barcode: String, variations: String, commission: Long, expiry: Long, 
    isService: Boolean, isCombo: Boolean, imagePath: String, 
    wholesalePrice: Long, wholesaleMinQty: Int,
    userId: Long, userName: String
) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val activeBranchId = _currentBranchId.value
            productDao.insertProduct(ProductEntity(
                name = name, price = price, stock = stock, category = category, costPrice = costPrice, 
                barcode = barcode, variations = variations, commissionPerItem = commission, expiryDate = expiry,
                isService = isService, isCombo = isCombo, imagePath = imagePath, 
                wholesalePrice = wholesalePrice, wholesaleMinQty = wholesaleMinQty,
                branchId = activeBranchId, isSynced = false, updatedAt = System.currentTimeMillis()
            ))
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "TAMBAH PRODUK (Cabang #$activeBranchId): $name", actionType = "INVENTORY"))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.updateProduct(product: ProductEntity, userId: Long, userName: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            if (product.branchId != _currentBranchId.value) return@launch
            val oldProduct = _products.value.find { it.id == product.id }
            productDao.saveProduct(product.copy(isSynced = false, updatedAt = System.currentTimeMillis()))
            
            val action = if (oldProduct != null && oldProduct.price != product.price) {
                "UBAH HARGA: ${product.name} dari ${formatRupiah(oldProduct.price)} menjadi ${formatRupiah(product.price)}"
            } else {
                "EDIT PRODUK: ${product.name}"
            }
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = action, actionType = "INVENTORY"))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.deleteProduct(product: ProductEntity, userId: Long, userName: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            if (product.branchId != _currentBranchId.value) return@launch
            productDao.deleteProduct(product)
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "HAPUS PRODUK: ${product.name}", actionType = "INVENTORY"))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.addStock(product: ProductEntity, variation: String, qty: Int, user: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val fresh = productDao.getProductById(product.id) ?: return@launch
            val newStock = fresh.stock + qty
            val newVar = if (variation.isNotBlank()) modifyVariantStock(fresh.variations, variation, qty) else fresh.variations
            productDao.updateProduct(fresh.copy(stock = newStock, variations = newVar, updatedAt = System.currentTimeMillis()))
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "RESTOCK: ${fresh.name} ($variation) +$qty", actionType = "INVENTORY"))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.recordWaste(product: ProductEntity, variation: String, qty: Int, user: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val fresh = productDao.getProductById(product.id) ?: return@launch
            val newStock = (fresh.stock - qty).coerceAtLeast(0)
            val newVar = if (variation.isNotBlank()) modifyVariantStock(fresh.variations, variation, -qty) else fresh.variations
            productDao.updateProduct(fresh.copy(stock = newStock, variations = newVar, updatedAt = System.currentTimeMillis()))
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "WASTE: ${fresh.name} ($variation) -$qty", actionType = "INVENTORY"))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.processStockOpname(opnameItems: List<StockOpnameItemEntity>, user: UserEntity, onSuccess: () -> Unit) {
    if (opnameItems.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            database.withTransaction {
                var totalLoss = 0L
                var totalGain = 0L
                opnameItems.forEach { item ->
                    val product = _products.value.find { it.id == item.productId }
                    if (product != null) {
                        if (item.difference < 0) totalLoss += (Math.abs(item.difference) * product.costPrice)
                        else if (item.difference > 0) totalGain += (item.difference * product.costPrice)
                        productDao.updateStock(product.id, item.physicalStock)
                    }
                }
                val opname = StockOpnameEntity(
                    branchId = _currentBranchId.value, timestamp = System.currentTimeMillis(),
                    userId = user.id, userName = user.name, totalItemsChecked = opnameItems.size,
                    totalLossValue = totalLoss, totalGainValue = totalGain
                )
                val opnameId = stockOpnameDao.insertOpname(opname)
                stockOpnameDao.insertOpnameItems(opnameItems.map { it.copy(opnameId = opnameId) })
                userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "STOCK OPNAME: ${opnameItems.size} item diaudit.", actionType = "INVENTORY"))
            }
            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.processBarcodeScan(barcode: String, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            var cleanBarcode = barcode
            val gs1Regex = Regex("""\(01\)(\d+)\(17\)(\d{6})\(10\)([A-Za-z0-9]+)""")
            val match = gs1Regex.find(barcode)
            if (match != null) cleanBarcode = match.groupValues[1]

            val matches = productDao.getProductsByBarcodeList(cleanBarcode)
            when {
                matches.isEmpty() -> withContext(Dispatchers.Main) { onResult(false, "Barcode tidak ditemukan: $cleanBarcode") }
                matches.size == 1 -> {
                    val product = matches.first()
                    if (product.branchId != _currentBranchId.value) {
                        withContext(Dispatchers.Main) { onResult(false, "Produk '${product.name}' milik cabang lain.") }
                        return@launch
                    }
                    if (product.isService || product.stock > 0) {
                        withContext(Dispatchers.Main) { addToCart(product) }
                        withContext(Dispatchers.Main) { onResult(true, "Berhasil tambah: ${product.name}") }
                    } else {
                        withContext(Dispatchers.Main) { onResult(false, "Stok ${product.name} habis!") }
                    }
                }
                else -> withContext(Dispatchers.Main) { onResult(false, "Barcode duplikat terdeteksi.") }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal scan: ${e.message}") }
        }
    }
}

fun PosViewModel.addBom(productId: Long, rawMaterialId: Long, qty: Double) {
    viewModelScope.launch(Dispatchers.IO) {
        try { productDao.insertProductBom(ProductBomEntity(productId = productId, rawMaterialId = rawMaterialId, quantityUsed = qty)) } catch (e: Exception) { e.printStackTrace() }
    }
}

// ================================================================
// FUNGSI BAHAN BAKU (RAW MATERIAL) BARU
// ================================================================
fun PosViewModel.addRawMaterial(name: String, stock: Double, unit: String, costPerUnit: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        try { 
            productDao.insertRawMaterial(RawMaterialEntity(name = name, stock = stock, unit = unit, costPerUnit = costPerUnit)) 
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.updateRawMaterial(id: Long, name: String, stock: Double, unit: String, costPerUnit: Long, onSuccess: () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val material = _rawMaterials.value.find { it.id == id }
            if (material != null) {
                productDao.insertRawMaterial(material.copy(name = name, stock = stock, unit = unit, costPerUnit = costPerUnit))
                withContext(Dispatchers.Main) { onSuccess() }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.deleteRawMaterial(id: Long, onSuccess: () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            productDao.deleteRawMaterial(id)
            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.restockRawMaterial(id: Long, qty: Double, note: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val material = _rawMaterials.value.find { it.id == id }
            if (material != null) {
                productDao.updateRawMaterialStock(id, material.stock + qty)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.wasteRawMaterial(id: Long, qty: Double, note: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val material = _rawMaterials.value.find { it.id == id }
            if (material != null) {
                val newStock = (material.stock - qty).coerceAtLeast(0.0)
                productDao.updateRawMaterialStock(id, newStock)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.exportProductsToExcel(context: Context, uri: Uri, onResult: (Boolean) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        val success = ExcelExporter.exportProductsToExcel(context, uri, _products.value)
        withContext(Dispatchers.Main) { onResult(success) }
    }
}

fun PosViewModel.importProductsFromExcel(context: Context, uri: Uri, onResult: (Int, Int) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        var insertCount = 0
        var updateCount = 0
        try {
            val importedProducts = ExcelExporter.importProductsFromExcel(context, uri)
            if (importedProducts != null) {
                val freshProducts = productDao.getAllProducts().first()
                val activeBranchId = _currentBranchId.value
                importedProducts.forEach { newProduct ->
                    try {
                        val existing = freshProducts.find { (newProduct.barcode.isNotEmpty() && it.barcode == newProduct.barcode) || it.name.equals(newProduct.name, ignoreCase = true) }
                        if (existing != null) {
                            productDao.saveProduct(existing.copy(name = newProduct.name, price = newProduct.price, costPrice = newProduct.costPrice, stock = newProduct.stock, category = newProduct.category, barcode = newProduct.barcode, variations = newProduct.variations, commissionPerItem = newProduct.commissionPerItem, expiryDate = newProduct.expiryDate, isService = newProduct.isService, wholesalePrice = newProduct.wholesalePrice, wholesaleMinQty = newProduct.wholesaleMinQty, isSynced = false, updatedAt = System.currentTimeMillis()))
                            updateCount++
                        } else {
                            productDao.saveProduct(newProduct.copy(branchId = activeBranchId, isSynced = false, updatedAt = System.currentTimeMillis()))
                            insertCount++
                        }
                    } catch (itemEx: Exception) { itemEx.printStackTrace() }
                }
            }
            withContext(Dispatchers.Main) { onResult(insertCount, updateCount) }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) { onResult(0, 0) }
        }
    }
}