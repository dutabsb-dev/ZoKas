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

// ====================================================================
// FILE 3 — PRODUCT, STOCK, BOM, RAW MATERIAL, EXCEL, BARCODE
// ====================================================================
// Extension functions untuk PosViewModel.
// ====================================================================

// ================================================================
// PRODUCT CRUD
// ================================================================
fun PosViewModel.addProduct(
    name: String, price: Long, stock: Int, category: String, costPrice: Long, 
    barcode: String, variations: String, commission: Long, expiry: Long, 
    isService: Boolean, isCombo: Boolean, imagePath: String, 
    wholesalePrice: Long, wholesaleMinQty: Int,
    userId: Long, userName: String
) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            var duplicateWarning: String? = null
            if (barcode.isNotBlank()) {
                val existing = productDao.getProductsByBarcodeList(barcode)
                    .filter { it.branchId == _currentBranchId.value }
                if (existing.isNotEmpty()) {
                    val otherNames = existing.joinToString(", ") { it.name }
                    duplicateWarning = "Barcode '$barcode' juga dipakai di cabang ini oleh: $otherNames"
                }
            }

            val activeBranchId = _currentBranchId.value

            productDao.insertProduct(ProductEntity(
                name = name, price = price, stock = stock, category = category, costPrice = costPrice, 
                barcode = barcode, variations = variations, commissionPerItem = commission, expiryDate = expiry,
                isService = isService, isCombo = isCombo, imagePath = imagePath, 
                wholesalePrice = wholesalePrice, wholesaleMinQty = wholesaleMinQty,
                branchId = activeBranchId,
                isSynced = false, updatedAt = System.currentTimeMillis()
            ))
            
            userDao.insertAuditLog(AuditLogEntity(
                userId = userId, 
                userName = userName, 
                action = "TAMBAH PRODUK (Cabang #$activeBranchId): $name", 
                actionType = "INVENTORY"
            ))
            
            if (duplicateWarning != null) {
                userDao.insertAuditLog(AuditLogEntity(
                    userId = userId, 
                    userName = userName, 
                    action = "PERINGATAN BARCODE DUPLIKAT: Produk '$name' ditambahkan. $duplicateWarning. Scan barcode ini akan ditolak sampai diperbaiki.",
                    actionType = "INVENTORY"
                ))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.updateProduct(product: ProductEntity, userId: Long, userName: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            // Proteksi cross-branch.
            if (product.branchId != _currentBranchId.value) {
                userDao.insertAuditLog(AuditLogEntity(
                    userId = userId,
                    userName = userName,
                    action = "TOLAK EDIT PRODUK LAIN CABANG: '${product.name}' (produk cabang #${product.branchId}, aktif #${_currentBranchId.value})",
                    actionType = "INVENTORY"
                ))
                return@launch
            }

            val oldProduct = _products.value.find { it.id == product.id }

            var duplicateWarning: String? = null
            if (product.barcode.isNotBlank()) {
                val sameBarcode = productDao.getProductsByBarcodeList(product.barcode)
                    .filter { it.id != product.id && it.branchId == _currentBranchId.value }
                if (sameBarcode.isNotEmpty()) {
                    val otherNames = sameBarcode.joinToString(", ") { it.name }
                    duplicateWarning = "Barcode '${product.barcode}' juga dipakai di cabang ini oleh: $otherNames"
                }
            }

            // Tulis ke DB dulu (saveProduct = UPDATE in-place).
            productDao.saveProduct(product.copy(isSynced = false, updatedAt = System.currentTimeMillis()))

            // Audit log baru setelah sukses.
            if (oldProduct != null && oldProduct.price != product.price) {
                userDao.insertAuditLog(AuditLogEntity(
                    userId = userId,
                    userName = userName,
                    action = "UBAH HARGA: ${product.name} dari ${formatRupiah(oldProduct.price)} menjadi ${formatRupiah(product.price)}",
                    actionType = "INVENTORY"
                ))
            } else {
                userDao.insertAuditLog(AuditLogEntity(
                    userId = userId,
                    userName = userName,
                    action = "EDIT PRODUK: ${product.name}",
                    actionType = "INVENTORY"
                ))
            }
            
            if (duplicateWarning != null) {
                userDao.insertAuditLog(AuditLogEntity(
                    userId = userId,
                    userName = userName,
                    action = "PERINGATAN BARCODE DUPLIKAT: Produk '${product.name}' diubah. $duplicateWarning. Scan barcode ini akan ditolak sampai diperbaiki.",
                    actionType = "INVENTORY"
                ))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.deleteProduct(product: ProductEntity, userId: Long, userName: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            if (product.branchId != _currentBranchId.value) {
                userDao.insertAuditLog(AuditLogEntity(
                    userId = userId,
                    userName = userName,
                    action = "TOLAK HAPUS PRODUK LAIN CABANG: '${product.name}'",
                    actionType = "INVENTORY"
                ))
                return@launch
            }
            productDao.deleteProduct(product)
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "HAPUS PRODUK: ${product.name}", actionType = "INVENTORY"))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

// ================================================================
// STOCK OPERATIONS
// ================================================================
fun PosViewModel.addStock(product: ProductEntity, qtyToAdd: Int, user: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val newStock = product.stock + qtyToAdd
            productDao.updateStock(product.id, newStock)
            userDao.insertAuditLog(AuditLogEntity(
                userId = user.id, userName = user.name, 
                action = "RESTOCK: ${product.name} +$qtyToAdd",
                actionType = "INVENTORY"
            ))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.recordWaste(product: ProductEntity, qty: Int, user: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val newStock = (product.stock - qty).coerceAtLeast(0)
            productDao.updateStock(product.id, newStock)
            val loss = qty * product.costPrice
            userDao.insertAuditLog(AuditLogEntity(
                userId = user.id, userName = user.name, 
                action = "WASTE: ${product.name} -$qty pcs. Rugi: ${formatRupiah(loss)}",
                actionType = "INVENTORY"
            ))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.processStockOpname(
    opnameItems: List<StockOpnameItemEntity>,
    user: UserEntity,
    onSuccess: () -> Unit
) {
    if (opnameItems.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            database.withTransaction {
                val timestamp = System.currentTimeMillis()
                var totalLoss = 0L
                var totalGain = 0L

                opnameItems.forEach { item ->
                    val product = _products.value.find { it.id == item.productId }
                    if (product != null) {
                        if (item.difference < 0) {
                            totalLoss += (Math.abs(item.difference) * product.costPrice)
                        } else if (item.difference > 0) {
                            totalGain += (item.difference * product.costPrice)
                        }
                        productDao.updateStock(product.id, item.physicalStock)
                    }
                }

                val opname = StockOpnameEntity(
                    timestamp = timestamp,
                    userId = user.id,
                    userName = user.name,
                    totalItemsChecked = opnameItems.size,
                    totalLossValue = totalLoss,
                    totalGainValue = totalGain
                )
                val opnameId = stockOpnameDao.insertOpname(opname)

                val itemsWithId = opnameItems.map { it.copy(opnameId = opnameId) }
                stockOpnameDao.insertOpnameItems(itemsWithId)

                userDao.insertAuditLog(AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    action = "STOCK OPNAME: ${opnameItems.size} item diaudit. Rugi: ${formatRupiah(totalLoss)}, Untung: ${formatRupiah(totalGain)}",
                    actionType = "INVENTORY"
                ))
            }
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// ================================================================
// BOM & RAW MATERIAL
// ================================================================
fun PosViewModel.addBom(productId: Long, rawMaterialId: Long, qty: Double) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            productDao.insertProductBom(ProductBomEntity(productId = productId, rawMaterialId = rawMaterialId, quantityUsed = qty))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.addRawMaterial(name: String, stock: Double, unit: String, costPerUnit: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            productDao.insertRawMaterial(RawMaterialEntity(name = name, stock = stock, unit = unit, costPerUnit = costPerUnit))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

// ================================================================
// EXCEL EXPORT / IMPORT
// ================================================================
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
                            productDao.saveProduct(existing.copy(
                                name = newProduct.name,
                                price = newProduct.price,
                                costPrice = newProduct.costPrice,
                                stock = newProduct.stock,
                                category = newProduct.category,
                                barcode = newProduct.barcode,
                                variations = newProduct.variations,
                                commissionPerItem = newProduct.commissionPerItem,
                                expiryDate = newProduct.expiryDate,
                                isService = newProduct.isService,
                                wholesalePrice = newProduct.wholesalePrice,
                                wholesaleMinQty = newProduct.wholesaleMinQty,
                                isSynced = false,
                                updatedAt = System.currentTimeMillis()
                            ))
                            updateCount++
                        } else {
                            productDao.saveProduct(newProduct.copy(
                                branchId = activeBranchId,
                                isSynced = false,
                                updatedAt = System.currentTimeMillis()
                            ))
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

// ================================================================
// BARCODE SCAN
// ================================================================
fun PosViewModel.processBarcodeScan(barcode: String, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            var cleanBarcode = barcode
            var parsedExpiry = 0L
            var parsedBatch = ""

            val gs1Regex = Regex("""\(01\)(\d+)\(17\)(\d{6})\(10\)([A-Za-z0-9]+)""")
            val match = gs1Regex.find(barcode)
            
            if (match != null) {
                cleanBarcode = match.groupValues[1]
                val expiryStr = match.groupValues[2]
                parsedBatch = match.groupValues[3]
                
                try {
                    val year = 2000 + expiryStr.substring(0, 2).toInt()
                    val month = expiryStr.substring(2, 4).toInt() - 1
                    val day = expiryStr.substring(4, 6).toInt()
                    val cal = java.util.Calendar.getInstance()
                    cal.set(year, month, day, 23, 59, 59)
                    parsedExpiry = cal.timeInMillis
                } catch (e: Exception) { }
            }

            val matches = productDao.getProductsByBarcodeList(cleanBarcode)

            when {
                matches.isEmpty() -> {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Barcode tidak ditemukan: $cleanBarcode")
                    }
                }
                matches.size == 1 -> {
                    val product = matches.first()
                    if (product.branchId != _currentBranchId.value) {
                        withContext(Dispatchers.Main) {
                            onResult(false, "Produk '${product.name}' milik cabang lain.")
                        }
                        return@launch
                    }
                    if (product.isService || product.stock > 0) {
                        val finalProduct = if (parsedExpiry > 0) {
                            product.copy(expiryDate = parsedExpiry, batchNumber = parsedBatch)
                        } else product
                        
                        withContext(Dispatchers.Main) { addToCart(finalProduct) }
                        withContext(Dispatchers.Main) { 
                            onResult(true, "Berhasil tambah: ${product.name}") 
                        }
                    } else {
                        withContext(Dispatchers.Main) { 
                            onResult(false, "Stok ${product.name} habis!") 
                        }
                    }
                }
                else -> {
                    val names = matches.joinToString(", ") { it.name }
                    val count = matches.size
                    
                    userDao.insertAuditLog(AuditLogEntity(
                        userId = 0,
                        userName = "SYSTEM",
                        action = "BARCODE DUPLIKAT TERDETEKSI: '$cleanBarcode' dipakai oleh $count produk: $names. Scan dibatalkan.",
                        actionType = "INVENTORY"
                    ))
                    
                    withContext(Dispatchers.Main) {
                        onResult(
                            false, 
                            "Barcode duplikat ($count produk: $names). Perbaiki dulu di menu Produk."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { 
                onResult(false, "Gagal scan: ${e.message}") 
            }
        }
    }
}