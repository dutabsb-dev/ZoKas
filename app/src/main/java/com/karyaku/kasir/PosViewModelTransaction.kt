package com.karyaku.kasir

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ====================================================================
// FILE FINAL — IMPLEMENTASI TRANSAKSI (KDS, BOM, MEMBERSHIP, POIN, BON)
// ====================================================================

fun PosViewModel.processPayment(
    cashReceived: Long, userId: Long, userName: String, isSplitBill: Boolean, method: String, 
    maxDiscountCap: Long, birthdayDiscountPercent: Double, taxPercent: Double, 
    serviceChargePercent: Double, manualDiscount: Long, voucherDiscount: Long, voucherCode: String,
    onSuccess: (TransactionEntity) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val cartToProcess = if (isSplitBill) _cart.value.filter { it.isSelectedForSplit } else _cart.value
            if (cartToProcess.isEmpty()) return@launch

            // 1. Hitung Subtotal
            val subtotal = cartToProcess.sumOf { 
                if (it.usedMembershipId != null) 0L
                else if (it.product.wholesaleMinQty > 0 && it.quantity >= it.product.wholesaleMinQty && it.product.wholesalePrice > 0) it.product.wholesalePrice * it.quantity
                else getPriceForVariant(it.product, it.selectedVariation) * it.quantity
            }
            
            // 2. Hitung Diskon, Pajak, Service
            var totalDiscount = manualDiscount + voucherDiscount
            if (maxDiscountCap > 0 && totalDiscount > maxDiscountCap) totalDiscount = maxDiscountCap
            
            val taxableAmount = (subtotal - totalDiscount).coerceAtLeast(0)
            val taxAmount = (taxableAmount * (taxPercent / 100.0)).toLong()
            val serviceChargeAmount = (taxableAmount * (serviceChargePercent / 100.0)).toLong()
            val finalTotalPrice = (subtotal + taxAmount + serviceChargeAmount - totalDiscount).coerceAtLeast(0)
            
            val totalCommission = cartToProcess.sumOf { it.product.commissionPerItem * it.quantity }

            // 3. Simpan Transaksi Utama
            val newTransaction = TransactionEntity(
                timestamp = System.currentTimeMillis(), totalPrice = finalTotalPrice,
                totalCost = cartToProcess.sumOf { it.product.costPrice * it.quantity },
                paymentMethod = method, status = "SUCCESS", cashierName = userName, 
                customerName = _selectedCustomer.value?.name ?: "Pelanggan Umum",
                orderType = _selectedOrderType.value, tableName = _selectedTable.value, 
                branchId = _currentBranchId.value, 
                cashReceived = cashReceived, changeAmount = if (cashReceived > finalTotalPrice) cashReceived - finalTotalPrice else 0L,
                taxAmount = taxAmount, serviceCharge = serviceChargeAmount, voucherCode = voucherCode,
                totalCommission = totalCommission
            )

            val transactionId = transactionDao.insertTransaction(newTransaction)
            val savedTransaction = newTransaction.copy(id = transactionId)
            val itemsToInsert = mutableListOf<TransactionItemEntity>()

            // 4. Proses Item & Paket Tambahan
            for (cartItem in cartToProcess) {
                val itemPrice = if (cartItem.usedMembershipId != null) 0L else getPriceForVariant(cartItem.product, cartItem.selectedVariation)
                
                // Simpan Detail Transaksi
                itemsToInsert.add(TransactionItemEntity(
                    transactionId = transactionId, productId = cartItem.product.id, productName = cartItem.product.name,
                    quantity = cartItem.quantity, price = itemPrice, costPrice = cartItem.product.costPrice, 
                    notes = cartItem.notes, serialNumber = cartItem.serialNumber, assignedStaff = cartItem.assignedStaff, 
                    subtotal = itemPrice * cartItem.quantity, isService = cartItem.product.isService
                ))

                // A. Potong Stok Produk (memperhitungkan konversi kemasan varian jika ada, misal Dus isi 24)
                val freshProduct = productDao.getProductById(cartItem.product.id)
                if (freshProduct != null && !freshProduct.isService) {
                    val packMult = getVariantPackMultiplier(freshProduct.variations, cartItem.selectedVariation)
                    val totalSmallestDeducted = cartItem.quantity * packMult
                    val newStock = (freshProduct.stock - totalSmallestDeducted).coerceAtLeast(0)
                    if (cartItem.selectedVariation.isNotBlank()) {
                        val newVariationStr = deductVariantStock(freshProduct.variations, cartItem.selectedVariation, cartItem.quantity)
                        productDao.updateProduct(freshProduct.copy(variations = newVariationStr, stock = newStock))
                    } else {
                        productDao.updateProduct(freshProduct.copy(stock = newStock))
                    }
                }

                // B. PAKET 2: Potong Kuota Membership
                if (cartItem.usedMembershipId != null) {
                    val cm = _customerMemberships.value.find { it.id == cartItem.usedMembershipId }
                    if (cm != null && cm.remainingQuota > 0) {
                        membershipDao.updateRemainingQuota(cm.id, cm.remainingQuota - 1)
                    }
                }

                // C. PAKET 1: Potong Stok Bahan Baku (BOM)
                val bomList = productDao.getBomForProduct(cartItem.product.id)
                if (bomList.isNotEmpty()) {
                    bomList.forEach { bom ->
                        val material = _rawMaterials.value.find { it.id == bom.rawMaterialId }
                        if (material != null) {
                            val qtyUsed = bom.quantityUsed * cartItem.quantity
                            productDao.updateRawMaterialStock(material.id, material.stock - qtyUsed)
                        }
                    }
                }
            }
            
            transactionItemDao.insertTransactionItems(itemsToInsert)

            // 5. PAKET 2: Update Loyalty Points & Profil Pelanggan
            val customer = _selectedCustomer.value
            if (customer != null) {
                val pointsEarned = (finalTotalPrice / 10000).toInt() // 1 Poin per Rp 10.000
                val finalPoints = (customer.points - _pointsUsed.value + pointsEarned).coerceAtLeast(0)
                
                customerDao.updateCustomerStats(
                    customerId = customer.id,
                    visitTime = System.currentTimeMillis(),
                    spent = finalTotalPrice,
                    newPoints = finalPoints
                )
            }

            // 6. PAKET 1: Buat Tiket Dapur (KDS)
            // Hanya buat tiket jika ada produk fisik (bukan jasa)
            val kitchenItems = cartToProcess.filter { !it.product.isService }
            if (kitchenItems.isNotEmpty()) {
                val itemsJson = JSONArray().apply {
                    kitchenItems.forEach { item ->
                        val displayItemName = if (item.selectedVariation.isNotBlank()) {
                            "${item.product.name} (${item.selectedVariation})"
                        } else {
                            item.product.name
                        }
                        put(JSONObject().apply {
                            put("name", displayItemName)
                            put("qty", item.quantity)
                            put("notes", item.notes)
                        })
                    }
                }.toString()
                
                kitchenTicketDao.insertTicket(KitchenTicketEntity(
                    transactionId = transactionId,
                    branchId = _currentBranchId.value,
                    tableName = newTransaction.tableName,
                    orderType = newTransaction.orderType,
                    customerName = newTransaction.customerName,
                    status = "PENDING",
                    itemsJson = itemsJson
                ))
            }

            // 7. Tambah Komisi Kasir
            if (totalCommission > 0) {
                userDao.addCommission(userId, totalCommission)
            }

            // 8. Selesai
            withContext(Dispatchers.Main) {
                if (isSplitBill) _cart.value = _cart.value.filterNot { it.isSelectedForSplit } else _cart.value = emptyList()
                onSuccess(savedTransaction)
            }
        } catch (e: Exception) { 
            e.printStackTrace() 
        }
    }
}

fun PosViewModel.printReceipt(
    context: Context, 
    transaction: TransactionEntity, 
    settingsManager: SettingsManager, 
    cartSnapshot: List<CartItem>
) {
    val macAddress = settingsManager.printerMacAddress ?: return
    
    val receiptItems = cartSnapshot.map { cartItem ->
        ReceiptItem(
            name = cartItem.product.name, 
            qty = cartItem.quantity, 
            price = getPriceForVariant(cartItem.product, cartItem.selectedVariation).toDouble(), 
            variation = cartItem.selectedVariation + (if (cartItem.notes.isNotBlank()) " | ${cartItem.notes}" else "")
        )
    }

    val strukText = ReceiptFormatter.buildReceipt(
        storeName = settingsManager.storeName ?: "ZoKas", 
        storeAddress = settingsManager.storeAddress ?: "", 
        storePhone = settingsManager.storePhone ?: "", 
        storeFooter = settingsManager.storeFooter ?: "Terima kasih!",
        transactionId = transaction.id, 
        dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(transaction.timestamp)),
        orderType = transaction.orderType ?: "Dine-In", 
        cashierName = transaction.cashierName ?: "Kasir", 
        customerName = transaction.customerName ?: "Pelanggan Umum",
        items = receiptItems, 
        subtotal = (transaction.totalPrice - transaction.taxAmount - transaction.serviceCharge).toDouble(), 
        total = transaction.totalPrice.toDouble(), 
        payment = transaction.cashReceived.toDouble(), 
        change = transaction.changeAmount.toDouble()
    )

    viewModelScope.launch(Dispatchers.IO) {
        val printerManager = BluetoothPrinterManager(context)
        val cfg = PrinterUpgradePrefs.load(context)
        val outputStream = java.io.ByteArrayOutputStream()
        outputStream.write(EscPosCommands.INIT)

        if (cfg.printLogo) {
            val logoBytes = EscPosCommands.printDefaultOrCustomLogo(
                context = context,
                logoPath = settingsManager.storeLogoPath,
                targetWidthPx = cfg.paperSize.logoWidthPx
            )
            if (logoBytes.isNotEmpty()) {
                outputStream.write(logoBytes)
            }
        }

        val adaptedText = if (cfg.paperSize == PrinterPaperSize.MM_80) {
            strukText.replace("=".repeat(32), "=".repeat(48)).replace("-".repeat(32), "-".repeat(48))
        } else {
            strukText
        }

        outputStream.write(EscPosCommands.ALIGN_LEFT)
        outputStream.write(adaptedText.toByteArray(charset("GBK")))
        outputStream.write(byteArrayOf(0x0A))
        outputStream.write(byteArrayOf(0x1B, 0x64, cfg.feedLines.coerceIn(1, 5).toByte()))
        if (cfg.autoCutPaper) {
            outputStream.write(EscPosCommands.CUT_PAPER)
        }

        if (cfg.autoOpenDrawerOnCash && transaction.paymentMethod.equals("CASH", ignoreCase = true)) {
            printerManager.openCashDrawer(macAddress)
        }
        printerManager.printData(macAddress, outputStream.toByteArray())
    }
}

// ================================================================
// HOLD ORDER (PARKIR PESANAN)
// ================================================================
fun PosViewModel.saveHoldOrder(customerName: String) {
    val currentCart = _cart.value
    if (currentCart.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val cartJson = JSONArray().apply {
                currentCart.forEach { item ->
                    put(JSONObject().apply {
                        put("productId", item.product.id)
                        put("name", item.product.name)
                        put("price", item.product.price)
                        put("quantity", item.quantity)
                        put("variation", item.selectedVariation)
                        put("notes", item.notes)
                        put("serialNumber", item.serialNumber)
                        put("assignedStaff", item.assignedStaff)
                    })
                }
            }.toString()

            holdOrderDao.insertHoldOrder(HoldOrderEntity(branchId = _currentBranchId.value, tableName = _selectedTable.value, customerName = customerName, cartJson = cartJson))
            withContext(Dispatchers.Main) {
                _cart.value = emptyList()
                _selectedCustomer.value = null
                _selectedTable.value = "Take-Away"
            }
        } catch (e: Exception) { Log.e("HoldOrder", "Gagal simpan: ${e.message}") }
    }
}

fun PosViewModel.resumeHoldOrder(order: HoldOrderEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray(order.cartJson)
            val newCart = mutableListOf<CartItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val product = productDao.getProductById(obj.getLong("productId"))
                if (product != null) {
                    newCart.add(
                        CartItem(
                            product = product,
                            quantity = obj.getInt("quantity"),
                            selectedVariation = obj.optString("variation", ""),
                            notes = obj.optString("notes", ""),
                            serialNumber = obj.optString("serialNumber", ""),
                            assignedStaff = obj.optString("assignedStaff", "")
                        )
                    )
                }
            }
            withContext(Dispatchers.Main) {
                _cart.value = newCart
                _selectedTable.value = order.tableName
                _selectedCustomer.value = if (order.customerName != "Pelanggan Umum") _customers.value.find { it.name == order.customerName } else null
            }
            holdOrderDao.deleteHoldOrder(order.id)
        } catch (e: Exception) { Log.e("HoldOrder", "Gagal resume: ${e.message}") }
    }
}

// ================================================================
// PELUNASAN BON, VOID, REFUND, QRIS, MERGE
// ================================================================
fun PosViewModel.payUnpaidTransaction(tx: TransactionEntity, cashReceived: Long, paidMethod: String = "CASH", user: UserEntity? = null, onSuccess: () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val changeAmount = if (cashReceived > tx.totalPrice) cashReceived - tx.totalPrice else 0L
            transactionDao.updateTransactionStatus(tx.id, cashReceived, changeAmount, paidMethod, System.currentTimeMillis())
            
            cashFlowDao.insertCashFlow(CashFlowEntity(
                shiftId = _activeShift.value?.id ?: 0L, branchId = _currentBranchId.value, type = "IN", 
                amount = tx.totalPrice, note = "Pelunasan BON #${tx.id} (${tx.customerName})", 
                userId = user?.id ?: 0L, userName = user?.name ?: "SYSTEM"
            ))
            
            if (user != null) {
                userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "PELUNASAN BON #${tx.id}: ${formatRupiah(tx.totalPrice)} via $paidMethod", actionType = "TRANSACTION"))
            }
            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.voidTransaction(transaction: TransactionEntity, pin: String, currentUser: UserEntity, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            if (hashPin(pin) != currentUser.pin && currentUser.role != "OWNER") {
                withContext(Dispatchers.Main) { onResult(false, "PIN salah atau akses ditolak.") }
                return@launch
            }
            transactionDao.voidTransaction(transaction.id)
            
            val items = transactionItemDao.getItemsByTransaction(transaction.id).first()
            items.forEach { item ->
                val prod = productDao.getProductById(item.productId)
                if (prod != null) productDao.updateStock(prod.id, prod.stock + item.quantity)
            }
            
            userDao.insertAuditLog(AuditLogEntity(userId = currentUser.id, userName = currentUser.name, action = "VOID TRANSAKSI #${transaction.id}", actionType = "TRANSACTION"))
            withContext(Dispatchers.Main) { onResult(true, "Transaksi berhasil di-void.") }
        } catch (e: Exception) { withContext(Dispatchers.Main) { onResult(false, "Gagal: ${e.message}") } }
    }
}

fun PosViewModel.refundTransaction(transaction: TransactionEntity, pin: String, currentUser: UserEntity, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            if (hashPin(pin) != currentUser.pin && currentUser.role != "OWNER") {
                withContext(Dispatchers.Main) { onResult(false, "PIN salah.") }
                return@launch
            }
            transactionDao.refundTransaction(transaction.id)
            
            val items = transactionItemDao.getItemsByTransaction(transaction.id).first()
            items.forEach { item ->
                val prod = productDao.getProductById(item.productId)
                if (prod != null) productDao.updateStock(prod.id, prod.stock + item.quantity)
            }
            
            userDao.insertAuditLog(AuditLogEntity(userId = currentUser.id, userName = currentUser.name, action = "REFUND TRANSAKSI #${transaction.id}", actionType = "TRANSACTION"))
            withContext(Dispatchers.Main) { onResult(true, "Transaksi berhasil di-refund.") }
        } catch (e: Exception) { withContext(Dispatchers.Main) { onResult(false, "Gagal: ${e.message}") } }
    }
}

fun PosViewModel.generateQrisPayment(amount: Long, staticPayload: String) {
    _qrisDynamicString.value = QrisHelper.generateDynamicQris(staticPayload, amount)
    _isQrisPaid.value = false
}

fun PosViewModel.generateTransferPayment(baseAmount: Long) {
    _uniqueTransferAmount.value = baseAmount + (1..999).random()
    _isTransferPaid.value = false
}

fun PosViewModel.resetQrisPayment() {
    _isQrisPaid.value = false
    _qrisDynamicString.value = ""
}

fun PosViewModel.resetTransferPayment() {
    _isTransferPaid.value = false
    _uniqueTransferAmount.value = 0L
}

fun PosViewModel.mergeOrder(tableA: String, tableB: String) {
    val holdsB = _holdOrders.value.filter { it.tableName == tableB }
    if (holdsB.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            holdsB.forEach { orderB ->
                holdOrderDao.insertHoldOrder(orderB.copy(tableName = tableA, id = 0, branchId = _currentBranchId.value))
                holdOrderDao.deleteHoldOrder(orderB.id)
            }
            withContext(Dispatchers.Main) { _selectedTable.value = tableA }
        } catch (e: Exception) { Log.e("MergeOrder", "Gagal: ${e.message}") }
    }
}