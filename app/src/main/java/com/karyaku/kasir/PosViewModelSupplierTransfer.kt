package com.karyaku.kasir

import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ====================================================================
// FILE 6 — SUPPLIER (MODUL E) + TRANSFER STOK (MODUL D-2)
// ====================================================================
// Extension functions untuk PosViewModel.
// ====================================================================

// ================================================================
// MODUL E — SUPPLIER & PURCHASE ORDER
// ================================================================
fun PosViewModel.addSupplier(name: String, contact: String, phone: String, address: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            supplierDao.insertSupplier(SupplierEntity(name = name, contactPerson = contact, phone = phone, address = address))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.createPurchaseOrder(
    supplier: SupplierEntity,
    invoiceNumber: String,
    items: List<PurchaseItemEntity>,
    isPaid: Boolean,
    updateCostPrice: Boolean,
    onSuccess: () -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            database.withTransaction {
                val total = items.sumOf { it.subtotal }
                val purchase = PurchaseEntity(
                    supplierId = supplier.id,
                    supplierName = supplier.name,
                    invoiceNumber = invoiceNumber,
                    totalAmount = total,
                    status = if (isPaid) "PAID" else "UNPAID",
                    paidAmount = if (isPaid) total else 0L,
                    paidAt = if (isPaid) System.currentTimeMillis() else 0L
                )
                val purchaseId = purchaseDao.insertPurchase(purchase)
                
                val itemsWithId = items.map { it.copy(purchaseId = purchaseId) }
                purchaseDao.insertPurchaseItems(itemsWithId)
                
                items.forEach { item ->
                    val product = _products.value.find { it.id == item.productId }
                    if (product != null) {
                        val newStock = product.stock + item.quantity
                        val newCost = if (updateCostPrice) item.costPrice else product.costPrice
                        productDao.saveProduct(product.copy(stock = newStock, costPrice = newCost, isSynced = false, updatedAt = System.currentTimeMillis()))
                    }
                }
            }
            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.getPaymentsForPurchase(purchaseId: Long): Flow<List<PurchasePaymentEntity>> {
    return purchasePaymentDao.getPaymentsByPurchase(purchaseId)
}

fun PosViewModel.getAllPurchasePayments(): Flow<List<PurchasePaymentEntity>> {
    return purchasePaymentDao.getAllPayments()
}

fun PosViewModel.recordSupplierPayment(
    purchase: PurchaseEntity,
    amount: Long,
    paymentMethod: String,
    note: String,
    user: UserEntity,
    onResult: (Boolean, String) -> Unit
) {
    val outstanding = (purchase.totalAmount - purchase.paidAmount).coerceAtLeast(0L)
    if (amount <= 0) {
        onResult(false, "Nominal pembayaran harus lebih dari 0.")
        return
    }
    if (amount > outstanding) {
        onResult(false, "Nominal melebihi sisa hutang (${formatRupiah(outstanding)}).")
        return
    }

    viewModelScope.launch(Dispatchers.IO) {
        try {
            var newPaidAmount = 0L
            var newStatus = "UNPAID"

            database.withTransaction {
                newPaidAmount = purchase.paidAmount + amount
                newStatus = when {
                    newPaidAmount <= 0L -> "UNPAID"
                    newPaidAmount < purchase.totalAmount -> "PARTIAL"
                    else -> "PAID"
                }
                val paidAt = if (newStatus == "PAID") System.currentTimeMillis() else 0L

                purchasePaymentDao.insertPayment(
                    PurchasePaymentEntity(
                        purchaseId = purchase.id,
                        amount = amount,
                        paymentMethod = paymentMethod,
                        note = note,
                        userId = user.id,
                        userName = user.name
                    )
                )

                purchaseDao.updatePayment(
                    purchaseId = purchase.id,
                    paidAmount = newPaidAmount,
                    status = newStatus,
                    paidAt = paidAt
                )

                userDao.insertAuditLog(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        action = "BAYAR HUTANG SUPPLIER: ${purchase.supplierName} (PO #${purchase.id} / ${purchase.invoiceNumber}) sebesar ${formatRupiah(amount)} via $paymentMethod. Status: $newStatus",
                        actionType = "FINANCE"
                    )
                )
            }

            withContext(Dispatchers.Main) {
                val msg = when (newStatus) {
                    "PAID" -> "Hutang LUNAS! Total terbayar ${formatRupiah(newPaidAmount)}."
                    "PARTIAL" -> "Cicilan tercatat. Sisa hutang ${formatRupiah(purchase.totalAmount - newPaidAmount)}."
                    else -> "Pembayaran tercatat."
                }
                onResult(true, msg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onResult(false, "Gagal mencatat pembayaran: ${e.message}")
            }
        }
    }
}

@Deprecated("Gunakan recordSupplierPayment() agar riwayat cicilan tercatat.")
fun PosViewModel.payPurchase(purchaseId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val purchase = _purchases.value.find { it.id == purchaseId }
            if (purchase != null) {
                val outstanding = (purchase.totalAmount - purchase.paidAmount).coerceAtLeast(0L)
                if (outstanding > 0L) {
                    val systemUser = UserEntity(
                        id = 0L,
                        name = "SYSTEM",
                        pin = "",
                        role = "SYSTEM"
                    )
                    recordSupplierPayment(
                        purchase = purchase,
                        amount = outstanding,
                        paymentMethod = "CASH",
                        note = "Pelunasan legacy via payPurchase()",
                        user = systemUser,
                        onResult = { _, _ -> }
                    )
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

// ================================================================
// MODUL D-2 — TRANSFER STOK ANTAR CABANG
// ================================================================
fun PosViewModel.getTransferItems(transferId: Long): Flow<List<StockTransferItemEntity>> {
    return stockTransferDao.getItemsForTransfer(transferId)
}

fun PosViewModel.transferStock(
    toBranchId: Long,
    items: List<Pair<ProductEntity, Int>>,
    note: String,
    user: UserEntity,
    onResult: (Boolean, String) -> Unit
) {
    val fromBranchId = _currentBranchId.value

    if (fromBranchId == toBranchId) {
        onResult(false, "Cabang asal dan tujuan tidak boleh sama.")
        return
    }
    if (items.isEmpty()) {
        onResult(false, "Minimal 1 item harus ditransfer.")
        return
    }
    val invalidQty = items.firstOrNull { it.second <= 0 }
    if (invalidQty != null) {
        onResult(false, "Qty harus lebih dari 0.")
        return
    }

    viewModelScope.launch(Dispatchers.IO) {
        try {
            var totalItems = 0
            var totalQty = 0
            var fromBranchName = ""
            var toBranchName = ""

            database.withTransaction {
                val fromBranch = branchDao.getBranchById(fromBranchId)
                    ?: throw IllegalStateException("Cabang asal tidak ditemukan.")
                val toBranch = branchDao.getBranchById(toBranchId)
                    ?: throw IllegalStateException("Cabang tujuan tidak ditemukan.")

                fromBranchName = fromBranch.name
                toBranchName = toBranch.name

                // Snapshot produk destination (suspend, aman di transaction).
                val destProducts = productDao.getProductsByBranchOnce(toBranchId).toMutableList()

                val transferItemsToInsert = mutableListOf<StockTransferItemEntity>()

                items.forEach { (requestProduct, qty) ->
                    // 1. Fresh read source
                    val source = productDao.getProductById(requestProduct.id)
                        ?: throw IllegalStateException("Produk '${requestProduct.name}' tidak ditemukan.")

                    if (source.branchId != fromBranchId) {
                        throw IllegalStateException("Produk '${source.name}' bukan milik cabang asal.")
                    }
                    if (source.isService) {
                        throw IllegalStateException("Produk '${source.name}' adalah jasa, tidak bisa ditransfer.")
                    }
                    if (source.stock < qty) {
                        throw IllegalStateException("Stok '${source.name}' tidak cukup (tersedia: ${source.stock}, diminta: $qty).")
                    }

                    // 2. Kurangi stok source
                    productDao.updateStock(source.id, source.stock - qty)

                    // 3. Cari / buat produk di destination
                    val destExisting = destProducts.find { dest ->
                        (source.barcode.isNotBlank() && dest.barcode == source.barcode) ||
                        dest.name.equals(source.name, ignoreCase = true)
                    }

                    val finalDestId: Long
                    if (destExisting != null) {
                        productDao.updateStock(destExisting.id, destExisting.stock + qty)
                        finalDestId = destExisting.id
                        // Update cache lokal agar item berikutnya akurat
                        val idx = destProducts.indexOfFirst { it.id == destExisting.id }
                        if (idx != -1) {
                            destProducts[idx] = destExisting.copy(stock = destExisting.stock + qty)
                        }
                    } else {
                        // Auto-create produk baru di destination.
                        val newProduct = source.copy(
                            id = 0L,
                            stock = qty,
                            branchId = toBranchId,
                            isSynced = false,
                            updatedAt = System.currentTimeMillis()
                        )
                        val newId = productDao.insertProduct(newProduct)
                        finalDestId = newId
                        destProducts.add(newProduct.copy(id = newId))
                    }

                    transferItemsToInsert.add(
                        StockTransferItemEntity(
                            transferId = 0L,
                            sourceProductId = source.id,
                            destinationProductId = finalDestId,
                            productName = source.name,
                            barcode = source.barcode,
                            quantity = qty
                        )
                    )
                    totalQty += qty
                }

                totalItems = transferItemsToInsert.size

                // 4. Insert header
                val transfer = StockTransferEntity(
                    fromBranchId = fromBranchId,
                    fromBranchName = fromBranch.name,
                    toBranchId = toBranchId,
                    toBranchName = toBranch.name,
                    note = note,
                    userId = user.id,
                    userName = user.name,
                    totalItems = totalItems,
                    totalQuantity = totalQty
                )
                val transferId = stockTransferDao.insertTransfer(transfer)

                // 5. Insert items dengan transferId
                stockTransferDao.insertTransferItems(
                    transferItemsToInsert.map { it.copy(transferId = transferId) }
                )

                // 6. Audit log
                userDao.insertAuditLog(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        action = "TRANSFER STOK: $fromBranchName → $toBranchName, $totalItems item (${totalQty} pcs). Catatan: ${note.ifBlank { "-" }}",
                        actionType = "INVENTORY"
                    )
                )
            }

            withContext(Dispatchers.Main) {
                onResult(
                    true,
                    "Transfer berhasil: $totalItems item ($totalQty pcs) dari $fromBranchName ke $toBranchName."
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onResult(false, "Transfer gagal: ${e.message}")
            }
        }
    }
}