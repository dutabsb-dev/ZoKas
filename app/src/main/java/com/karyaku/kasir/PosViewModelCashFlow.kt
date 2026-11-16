package com.karyaku.kasir

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ====================================================================
// FILE PERBAIKAN: CASH FLOW, TRANSAKSI, & NOTIFIKASI
// ====================================================================

fun PosViewModel.listenToPaymentNotifications() {
    viewModelScope.launch(Dispatchers.IO) {
        PaymentNotificationListener.paymentEvents.collect { event ->
            if (_isQrisPaid.value == false && _qrisDynamicString.value.isNotEmpty()) {
                withContext(Dispatchers.Main) { _isQrisPaid.value = true }
            }
            if (_isTransferPaid.value == false && _uniqueTransferAmount.value > 0) {
                if (event.extractedAmount == _uniqueTransferAmount.value) {
                    withContext(Dispatchers.Main) { _isTransferPaid.value = true }
                }
            }
        }
    }
}

fun PosViewModel.addCashFlow(type: String, amount: Long, note: String, user: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val activeShiftId = _activeShift.value?.id ?: 0L
            val cashFlow = CashFlowEntity(
                shiftId = activeShiftId,
                branchId = _currentBranchId.value,
                type = type,
                amount = amount,
                note = note,
                timestamp = System.currentTimeMillis(),
                userId = user.id,
                userName = user.name
            )
            cashFlowDao.insertCashFlow(cashFlow)
            userDao.insertAuditLog(AuditLogEntity(
                userId = user.id, userName = user.name, 
                action = "KAS ${if (type == "IN") "MASUK" else "KELUAR"}: ${formatRupiah(amount)} ($note)", 
                actionType = "CASHFLOW"
            ))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.closeShift(user: UserEntity, physicalCash: Long, note: String, onDone: (ShiftEntity) -> Unit) {
    val currentShift = _activeShift.value ?: return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val endTime = System.currentTimeMillis()
            val allTransactions = transactionDao.getTransactionsByBranch(_currentBranchId.value).first()
            val allCashFlows = cashFlowDao.getCashFlowsByBranch(_currentBranchId.value).first()
            
            val cashSales = allTransactions
                .filter { it.timestamp >= currentShift.startTime && it.status == "SUCCESS" && it.paymentMethod == "CASH" }
                .sumOf { it.totalPrice }
            
            val cashIn = allCashFlows.filter { it.shiftId == currentShift.id && it.type == "IN" }.sumOf { it.amount }
            val cashOut = allCashFlows.filter { it.shiftId == currentShift.id && it.type == "OUT" }.sumOf { it.amount }
            
            val expectedCash = currentShift.openingCash + cashSales + cashIn - cashOut
            val discrepancy = physicalCash - expectedCash

            userDao.closeShift(currentShift.id, endTime, expectedCash, physicalCash, discrepancy, note)
            
            val closedShift = currentShift.copy(
                endTime = endTime, expectedCash = expectedCash, closingCash = physicalCash,
                discrepancy = discrepancy, status = "CLOSED", note = note
            )
            
            _activeShift.value = null
            withContext(Dispatchers.Main) { onDone(closedShift) }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

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

            val subtotal = cartToProcess.sumOf { 
                if (it.usedMembershipId != null) 0L
                else if (it.product.wholesaleMinQty > 0 && it.quantity >= it.product.wholesaleMinQty && it.product.wholesalePrice > 0) it.product.wholesalePrice * it.quantity
                else getPriceForVariant(it.product, it.selectedVariation) * it.quantity
            }
            
            var totalDiscount = manualDiscount + voucherDiscount
            if (maxDiscountCap > 0 && totalDiscount > maxDiscountCap) totalDiscount = maxDiscountCap
            
            val taxableAmount = (subtotal - totalDiscount).coerceAtLeast(0)
            val taxAmount = (taxableAmount * (taxPercent / 100.0)).toLong()
            val serviceChargeAmount = (taxableAmount * (serviceChargePercent / 100.0)).toLong()
            val finalTotalPrice = (subtotal + taxAmount + serviceChargeAmount - totalDiscount).coerceAtLeast(0)
            
            val totalCommission = cartToProcess.sumOf { it.product.commissionPerItem * it.quantity }

            val newTransaction = TransactionEntity(
                timestamp = System.currentTimeMillis(), totalPrice = finalTotalPrice,
                totalCost = cartToProcess.sumOf { it.product.costPrice * it.quantity },
                paymentMethod = method, status = "SUCCESS", cashierName = userName, 
                customerName = _selectedCustomer.value?.name ?: "Pelanggan Umum",
                orderType = _selectedOrderType.value, tableName = _selectedTable.value, 
                branchId = _currentBranchId.value, 
                cashReceived = cashReceived, changeAmount = if (cashReceived > finalTotalPrice) cashReceived - finalTotalPrice else 0L,
                taxAmount = taxAmount, serviceCharge = serviceChargeAmount, voucherCode = voucherCode,
                totalCommission = totalCommission // Disimpan di struk
            )

            val transactionId = transactionDao.insertTransaction(newTransaction)
            val savedTransaction = newTransaction.copy(id = transactionId)
            val itemsToInsert = mutableListOf<TransactionItemEntity>()

            for (cartItem in cartToProcess) {
                val itemPrice = if (cartItem.usedMembershipId != null) 0L else getPriceForVariant(cartItem.product, cartItem.selectedVariation)
                itemsToInsert.add(TransactionItemEntity(
                    transactionId = transactionId, productId = cartItem.product.id, productName = cartItem.product.name,
                    quantity = cartItem.quantity, price = itemPrice, costPrice = cartItem.product.costPrice, 
                    notes = cartItem.notes, serialNumber = cartItem.serialNumber, assignedStaff = cartItem.assignedStaff, 
                    subtotal = itemPrice * cartItem.quantity, isService = cartItem.product.isService
                ))

                val freshProduct = productDao.getProductById(cartItem.product.id)
                if (freshProduct != null) {
                    val newStock = (freshProduct.stock - cartItem.quantity).coerceAtLeast(0)
                    if (cartItem.selectedVariation.isNotBlank()) {
                        val newVariationStr = deductVariantStock(freshProduct.variations, cartItem.selectedVariation, cartItem.quantity)
                        productDao.updateProduct(freshProduct.copy(variations = newVariationStr, stock = newStock))
                    } else {
                        productDao.updateProduct(freshProduct.copy(stock = newStock))
                    }
                }
            }
            transactionItemDao.insertTransactionItems(itemsToInsert)

            // FIX SESI 7: Tambahkan komisi ke profil kasir di database
            if (totalCommission > 0) {
                userDao.addCommission(userId, totalCommission)
            }

            withContext(Dispatchers.Main) {
                if (isSplitBill) _cart.value = _cart.value.filterNot { it.isSelectedForSplit } else _cart.value = emptyList()
                onSuccess(savedTransaction)
            }
        } catch (e: Exception) { e.printStackTrace() }
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
        val outputStream = java.io.ByteArrayOutputStream()
        outputStream.write(EscPosCommands.INIT)
        outputStream.write(strukText.toByteArray(charset("GBK")))
        outputStream.write(EscPosCommands.FEED_3)
        outputStream.write(EscPosCommands.CUT_PAPER)
        printerManager.printData(macAddress, outputStream.toByteArray())
    }
}