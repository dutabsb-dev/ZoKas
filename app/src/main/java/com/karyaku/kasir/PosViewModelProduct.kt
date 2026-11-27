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

val AVAILABLE_STOCK_UNITS = listOf(
    "pcs", "dus", "zak", "bal", "pack", "box", "lusin", "kodi",
    "kg", "g", "l", "ml", "meter", "roll", "botol", "kaleng"
)

fun extractProductBaseUnit(product: ProductEntity): String {
    val raw = product.barcode
    if (raw.contains("@@UNIT:")) {
        val u = raw.substringAfter("@@UNIT:").trim()
        if (u.isNotEmpty()) return u
    }
    return "pcs"
}

private fun stripUnitTag(rawBarcodeField: String): String {
    return if (rawBarcodeField.contains("@@UNIT:")) {
        rawBarcodeField.substringBefore("@@UNIT:").trim()
    } else {
        rawBarcodeField.trim()
    }
}

fun extractProductSku(rawBarcodeField: String): String {
    val clean = stripUnitTag(rawBarcodeField)
    if (!clean.contains("||")) return ""
    return clean.substringBefore("||").trim()
}

fun extractActualBarcode(rawBarcodeField: String): String {
    val clean = stripUnitTag(rawBarcodeField)
    if (!clean.contains("||")) return clean
    return clean.substringAfter("||").trim()
}

fun packSkuAndBarcode(sku: String, barcode: String, baseUnit: String = "pcs"): String {
    val cleanSku = sku.trim().replace("|", "").replace("@", "")
    val cleanBarcode = barcode.trim().replace("|", "").replace("@", "")
    val cleanUnit = baseUnit.trim().ifBlank { "pcs" }
    val core = if (cleanSku.isEmpty()) cleanBarcode else "$cleanSku||$cleanBarcode"
    return if (cleanUnit.equals("pcs", ignoreCase = true)) {
        core
    } else {
        "$core@@UNIT:$cleanUnit"
    }
}

fun matchesProductSearch(product: ProductEntity, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    val sku = extractProductSku(product.barcode)
    val rawBc = extractActualBarcode(product.barcode)
    return sku.contains(q, ignoreCase = true) ||
        rawBc.contains(q, ignoreCase = true) ||
        product.name.contains(q, ignoreCase = true) ||
        product.category.contains(q, ignoreCase = true)
}

fun resolveVariantDisplayNameForReceipt(
    product: ProductEntity?,
    itemProductName: String,
    unitPrice: Long
): String {
    if (product == null || product.variations.isBlank()) return itemProductName
    if (itemProductName.contains("(") && itemProductName.endsWith(")")) return itemProductName

    val parts = product.variations.replace("|", ",").split(",")
    for (part in parts) {
        val pieces = part.split(":")
        if (pieces.size >= 2) {
            val vName = pieces[0].trim()
            val vPrice = pieces[1].trim().toLongOrNull() ?: 0L
            if (vName.isNotEmpty() && vPrice == unitPrice) {
                return "$itemProductName ($vName)"
            }
        }
    }
    return itemProductName
}

fun getVariantPackMultiplier(variationsStr: String, targetVariantName: String): Int {
    if (variationsStr.isBlank() || targetVariantName.isBlank()) return 1
    val parts = variationsStr.replace("|", ",").split(",")
    for (part in parts) {
        val pieces = part.split(":")
        if (pieces.size >= 5 && pieces[0].trim().equals(targetVariantName, ignoreCase = true)) {
            return (pieces[4].trim().toIntOrNull() ?: 1).coerceAtLeast(1)
        }
    }
    return 1
}

fun PosViewModel.deductVariantStock(variationsStr: String, targetVariantName: String, deductQty: Int): String {
    if (variationsStr.isBlank()) return ""
    val parts = variationsStr.split(",")
    val newParts = parts.map { part ->
        val pieces = part.split(":")
        if (pieces.size >= 2 && pieces[0].trim().equals(targetVariantName, ignoreCase = true)) {
            val price = pieces[1].trim()
            val cost = pieces.getOrNull(2)?.trim() ?: "0"
            val currentStock = if (pieces.size >= 4) pieces[3].trim().toIntOrNull() ?: 0 else 999999
            val mult = pieces.getOrNull(4)?.trim()
            val newStock = (currentStock - deductQty).coerceAtLeast(0)
            if (!mult.isNullOrEmpty()) {
                "${pieces[0]}:$price:$cost:$newStock:$mult"
            } else {
                "${pieces[0]}:$price:$cost:$newStock"
            }
        } else {
            part
        }
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
            val skuInfo = extractProductSku(barcode).let { if (it.isNotEmpty()) " [Kode: $it]" else "" }
            productDao.insertProduct(
                ProductEntity(
                    name = name, price = price, stock = stock, category = category, costPrice = costPrice,
                    barcode = barcode, variations = variations, commissionPerItem = commission, expiryDate = expiry,
                    isService = isService, isCombo = isCombo, imagePath = imagePath,
                    wholesalePrice = wholesalePrice, wholesaleMinQty = wholesaleMinQty,
                    branchId = activeBranchId, isSynced = false, updatedAt = System.currentTimeMillis()
                )
            )
            userDao.insertAuditLog(
                AuditLogEntity(
                    userId = userId,
                    userName = userName,
                    action = "TAMBAH PRODUK (Cabang #$activeBranchId): $name$skuInfo",
                    actionType = "INVENTORY"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private fun PosViewModel.resolveAppContextForProduct(): Context? {
    return runCatching {
        val field = PosViewModel::class.java.declaredFields.firstOrNull {
            Context::class.java.isAssignableFrom(it.type)
        }?.apply { isAccessible = true }
        field?.get(this) as? Context
    }.getOrNull() ?: runCatching {
        val appClass = Class.forName("android.app.ActivityThread")
        appClass.getMethod("currentApplication").invoke(null) as? Context
    }.getOrNull()
}

fun PosViewModel.updateProduct(product: ProductEntity, userId: Long, userName: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            if (product.branchId != _currentBranchId.value) return@launch
            val oldProduct = _products.value.find { it.id == product.id }
            val appCtx = resolveAppContextForProduct()
            val legacyBoms = runCatching { productDao.getBomForProduct(product.id) }.getOrDefault(emptyList())
            val syncedProduct = if (appCtx != null) {
                VariantBomManager.syncProductAndVariantCostsFromRecipes(
                    context = appCtx,
                    product = product,
                    rawMaterials = _rawMaterials.value,
                    legacyProductBoms = legacyBoms
                )
            } else {
                product
            }
            productDao.saveProduct(syncedProduct.copy(isSynced = false, updatedAt = System.currentTimeMillis()))

            val skuInfo = extractProductSku(product.barcode).let { if (it.isNotEmpty()) " [Kode: $it]" else "" }
            val action = if (oldProduct != null && oldProduct.price != product.price) {
                "UBAH HARGA: ${product.name}$skuInfo dari ${formatRupiah(oldProduct.price)} menjadi ${formatRupiah(product.price)}"
            } else {
                "EDIT PRODUK: ${product.name}$skuInfo"
            }
            userDao.insertAuditLog(
                AuditLogEntity(
                    userId = userId,
                    userName = userName,
                    action = action,
                    actionType = "INVENTORY"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun PosViewModel.deleteProduct(product: ProductEntity, userId: Long, userName: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val targetId = product.id
            val targetName = product.name
            resolveAppContextForProduct()?.let { appCtx ->
                VariantBomManager.clearAllRecipesForProduct(appCtx, product)
            }
            database.withTransaction {
                productDao.deleteBomForProduct(targetId)
                productDao.unlinkProductFromTransactionItems(targetId)
                val deletedRows = productDao.deleteProductById(targetId)
                if (deletedRows == 0) {
                    val fresh = productDao.getProductById(targetId)
                    if (fresh != null) {
                        productDao.deleteProduct(fresh)
                    }
                }
            }
            userDao.insertAuditLog(
                AuditLogEntity(
                    userId = userId,
                    userName = userName,
                    action = "HAPUS PRODUK: $targetName",
                    actionType = "INVENTORY"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun PosViewModel.addStock(product: ProductEntity, variation: String, qty: Int, user: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val fresh = productDao.getProductById(product.id) ?: return@launch
            val packMult = getVariantPackMultiplier(fresh.variations, variation)
            val variantDelta = if (packMult > 1) (qty / packMult).coerceAtLeast(1) else qty
            val newStock = fresh.stock + qty
            val newVar = if (variation.isNotBlank()) modifyVariantStock(fresh.variations, variation, variantDelta) else fresh.variations
            productDao.updateProduct(fresh.copy(stock = newStock, variations = newVar, updatedAt = System.currentTimeMillis()))
            userDao.insertAuditLog(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    action = "RESTOCK: ${fresh.name} ($variation) +$qty",
                    actionType = "INVENTORY"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun PosViewModel.recordWaste(product: ProductEntity, variation: String, qty: Int, user: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val fresh = productDao.getProductById(product.id) ?: return@launch
            val packMult = getVariantPackMultiplier(fresh.variations, variation)
            val variantDelta = if (packMult > 1) (qty / packMult).coerceAtLeast(1) else qty
            val newStock = (fresh.stock - qty).coerceAtLeast(0)
            val newVar = if (variation.isNotBlank()) modifyVariantStock(fresh.variations, variation, -variantDelta) else fresh.variations
            productDao.updateProduct(fresh.copy(stock = newStock, variations = newVar, updatedAt = System.currentTimeMillis()))
            userDao.insertAuditLog(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    action = "WASTE: ${fresh.name} ($variation) -$qty",
                    actionType = "INVENTORY"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
                userDao.insertAuditLog(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        action = "AUDIT STOK: ${opnameItems.size} item diaudit.",
                        actionType = "INVENTORY"
                    )
                )
            }
            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun PosViewModel.processBarcodeScan(barcode: String, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            var cleanCode = barcode.trim()
            val gs1Regex = Regex("""\(01\)(\d+)\(17\)(\d{6})\(10\)([A-Za-z0-9]+)""")
            val match = gs1Regex.find(cleanCode)
            if (match != null) cleanCode = match.groupValues[1]

            val activeBranchId = _currentBranchId.value
            val allBranchProducts = _products.value.filter { it.branchId == activeBranchId }

            val matches = allBranchProducts.filter { prod ->
                val sku = extractProductSku(prod.barcode)
                val actualBc = extractActualBarcode(prod.barcode)
                stripUnitTag(prod.barcode).equals(cleanCode, ignoreCase = true) ||
                    (actualBc.isNotEmpty() && actualBc.equals(cleanCode, ignoreCase = true)) ||
                    (sku.isNotEmpty() && sku.equals(cleanCode, ignoreCase = true))
            }.ifEmpty {
                productDao.getProductsByBarcodeList(cleanCode)
            }

            when {
                matches.isEmpty() -> withContext(Dispatchers.Main) {
                    onResult(false, "Kode/Barcode tidak ditemukan: $cleanCode")
                }
                matches.size == 1 -> {
                    val product = matches.first()
                    if (product.branchId != activeBranchId) {
                        withContext(Dispatchers.Main) {
                            onResult(false, "Produk '${product.name}' milik cabang lain.")
                        }
                        return@launch
                    }
                    if (product.isService || product.stock > 0) {
                        withContext(Dispatchers.Main) { addToCart(product) }
                        withContext(Dispatchers.Main) {
                            onResult(true, "Berhasil tambah: ${product.name}")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onResult(false, "Stok ${product.name} habis!")
                        }
                    }
                }
                else -> withContext(Dispatchers.Main) {
                    onResult(false, "Kode/Barcode duplikat terdeteksi ($cleanCode).")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal memproses kode: ${e.message}") }
        }
    }
}

fun PosViewModel.addBom(productId: Long, rawMaterialId: Long, qty: Double) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            productDao.insertProductBom(ProductBomEntity(productId = productId, rawMaterialId = rawMaterialId, quantityUsed = qty))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun PosViewModel.addRawMaterial(name: String, stock: Double, unit: String, costPerUnit: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            productDao.insertRawMaterial(RawMaterialEntity(name = name, stock = stock, unit = unit, costPerUnit = costPerUnit))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun PosViewModel.updateRawMaterial(id: Long, name: String, stock: Double, unit: String, costPerUnit: Long, onSuccess: () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val material = _rawMaterials.value.find { it.id == id }
            if (material != null) {
                val updatedMaterial = material.copy(name = name, stock = stock, unit = unit, costPerUnit = costPerUnit)
                productDao.insertRawMaterial(updatedMaterial)
                val appCtx = resolveAppContextForProduct()
                if (appCtx != null && material.costPerUnit != costPerUnit) {
                    val refreshedMaterials = _rawMaterials.value.map { if (it.id == id) updatedMaterial else it }
                    val allBranchProds = productDao.getAllProducts().first()
                    allBranchProds.forEach { prod ->
                        val legacyBoms = runCatching { productDao.getBomForProduct(prod.id) }.getOrDefault(emptyList())
                        val synced = VariantBomManager.syncProductAndVariantCostsFromRecipes(
                            context = appCtx,
                            product = prod,
                            rawMaterials = refreshedMaterials,
                            legacyProductBoms = legacyBoms
                        )
                        if (synced.costPrice != prod.costPrice || synced.variations != prod.variations) {
                            productDao.saveProduct(synced.copy(isSynced = false, updatedAt = System.currentTimeMillis()))
                        }
                    }
                }
                withContext(Dispatchers.Main) { onSuccess() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun PosViewModel.deleteRawMaterial(id: Long, onSuccess: () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            productDao.deleteRawMaterial(id)
            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun PosViewModel.restockRawMaterial(id: Long, qty: Double, note: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val material = _rawMaterials.value.find { it.id == id }
            if (material != null) {
                productDao.updateRawMaterialStock(id, material.stock + qty)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
                        val existing = freshProducts.find {
                            (newProduct.barcode.isNotEmpty() && it.barcode == newProduct.barcode) ||
                                it.name.equals(newProduct.name, ignoreCase = true)
                        }
                        if (existing != null) {
                            productDao.saveProduct(
                                existing.copy(
                                    name = newProduct.name, price = newProduct.price, costPrice = newProduct.costPrice,
                                    stock = newProduct.stock, category = newProduct.category, barcode = newProduct.barcode,
                                    variations = newProduct.variations, commissionPerItem = newProduct.commissionPerItem,
                                    expiryDate = newProduct.expiryDate, isService = newProduct.isService,
                                    wholesalePrice = newProduct.wholesalePrice, wholesaleMinQty = newProduct.wholesaleMinQty,
                                    isSynced = false, updatedAt = System.currentTimeMillis()
                                )
                            )
                            updateCount++
                        } else {
                            productDao.saveProduct(newProduct.copy(branchId = activeBranchId, isSynced = false, updatedAt = System.currentTimeMillis()))
                            insertCount++
                        }
                    } catch (itemEx: Exception) {
                        itemEx.printStackTrace()
                    }
                }
            }
            withContext(Dispatchers.Main) { onResult(insertCount, updateCount) }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) { onResult(0, 0) }
        }
    }
}
/**
 * Kalkulasi susut timbang (misal kulakan ayam potong/daging/ikan dalam kg)
 * sehingga HPP per kg bersih siap jual tetap akurat di lapangan.
 */
fun calculateNetWeightAndCostPerKg(
    grossWeightKg: Double,
    shrinkagePercent: Double,
    totalPurchaseCost: Long
): Pair<Double, Long> {
    val safeGross = grossWeightKg.coerceAtLeast(0.01)
    val safeShrink = shrinkagePercent.coerceIn(0.0, 80.0)
    val netWeightKg = (safeGross * (1.0 - (safeShrink / 100.0))).coerceAtLeast(0.01)
    val netCostPerKg = kotlin.math.round(totalPurchaseCost.toDouble() / netWeightKg).toLong()
    return Pair(netWeightKg, netCostPerKg)
}
