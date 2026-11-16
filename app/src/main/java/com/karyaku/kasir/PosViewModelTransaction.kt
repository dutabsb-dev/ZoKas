package com.karyaku.kasir

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// ====================================================================
// FILE 7 — IMPLEMENTASI FITUR TRANSAKSI (HOLD, BON, VOID, QRIS)
// ====================================================================

fun PosViewModel.saveHoldOrder(customerName: String) {
    val currentCart = _cart.value
    if (currentCart.isEmpty()) return
    
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val cartJson = JSONArray().apply {
                currentCart.forEach { item ->
                    val obj = JSONObject().apply {
                        put("productId", item.product.id)
                        put("name", item.product.name)
                        put("price", item.product.price)
                        put("quantity", item.quantity)
                        put("variation", item.selectedVariation)
                        put("notes", item.notes)
                    }
                    put(obj)
                }
            }.toString()

            val holdOrder = HoldOrderEntity(
                branchId = _currentBranchId.value, // FIX: Simpan ID Cabang
                tableName = _selectedTable.value,
                customerName = customerName,
                cartJson = cartJson,
                timestamp = System.currentTimeMillis()
            )
            holdOrderDao.insertHoldOrder(holdOrder)
            
            withContext(Dispatchers.Main) {
                _cart.value = emptyList()
                _selectedCustomer.value = null
                _selectedTable.value = "Take-Away"
            }
        } catch (e: Exception) {
            Log.e("HoldOrder", "Gagal simpan pesanan parkir: ${e.message}")
        }
    }
}

fun PosViewModel.resumeHoldOrder(order: HoldOrderEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray(order.cartJson)
            val newCart = mutableListOf<CartItem>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val productId = obj.getLong("productId")
                val product = productDao.getProductById(productId)
                
                if (product != null) {
                    newCart.add(
                        CartItem(
                            product = product,
                            quantity = obj.getInt("quantity"),
                            selectedVariation = obj.optString("variation", ""),
                            notes = obj.optString("notes", "")
                        )
                    )
                }
            }
            
            withContext(Dispatchers.Main) {
                _cart.value = newCart
                _selectedTable.value = order.tableName
                _selectedCustomer.value = if (order.customerName != "Pelanggan Umum") {
                    _customers.value.find { it.name == order.customerName }
                } else null
            }
            
            holdOrderDao.deleteHoldOrder(order.id)
        } catch (e: Exception) {
            Log.e("HoldOrder", "Gagal melanjutkan pesanan: ${e.message}")
        }
    }
}

fun PosViewModel.payUnpaidTransaction(tx: TransactionEntity, cashReceived: Long, paidMethod: String, user: UserEntity, onSuccess: () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val changeAmount = if (cashReceived > tx.totalPrice) cashReceived - tx.totalPrice else 0L
            
            transactionDao.updateTransactionStatus(
                transactionId = tx.id,
                cashReceived = cashReceived,
                changeAmount = changeAmount,
                paidMethod = paidMethod,
                paidAt = System.currentTimeMillis()
            )
            
            val activeShiftId = _activeShift.value?.id ?: 0L
            cashFlowDao.insertCashFlow(CashFlowEntity(
                shiftId = activeShiftId,
                branchId = _currentBranchId.value, // FIX: Simpan ID Cabang
                type = "IN",
                amount = tx.totalPrice,
                note = "Pelunasan BON #${tx.id} (${tx.customerName})",
                timestamp = System.currentTimeMillis(),
                userId = user.id,
                userName = user.name
            ))
            
            userDao.insertAuditLog(AuditLogEntity(
                userId = user.id, userName = user.name,
                action = "PELUNASAN BON #${tx.id}: ${formatRupiah(tx.totalPrice)} via $paidMethod",
                actionType = "TRANSACTION"
            ))
            
            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) { 
            e.printStackTrace() 
        }
    }
}

fun PosViewModel.voidTransaction(tx: TransactionEntity, pin: String, user: UserEntity, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            if (hashPin(pin) != user.pin && user.role != "OWNER") {
                withContext(Dispatchers.Main) { onResult(false, "PIN salah atau akses ditolak.") }
                return@launch
            }
            
            transactionDao.voidTransaction(tx.id)
            
            val items = transactionItemDao.getItemsByTransaction(tx.id).first()
            items.forEach { item ->
                val prod = productDao.getProductById(item.productId)
                if (prod != null) {
                    productDao.updateStock(prod.id, prod.stock + item.quantity)
                }
            }
            
            userDao.insertAuditLog(AuditLogEntity(
                userId = user.id, userName = user.name,
                action = "VOID TRANSAKSI #${tx.id}: ${formatRupiah(tx.totalPrice)}",
                actionType = "TRANSACTION"
            ))
            
            withContext(Dispatchers.Main) { onResult(true, "Transaksi berhasil di-void.") }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal void: ${e.message}") }
        }
    }
}

fun PosViewModel.refundTransaction(tx: TransactionEntity, pin: String, user: UserEntity, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            if (hashPin(pin) != user.pin && user.role != "OWNER") {
                withContext(Dispatchers.Main) { onResult(false, "PIN salah atau akses ditolak.") }
                return@launch
            }
            
            transactionDao.refundTransaction(tx.id)
            
            val items = transactionItemDao.getItemsByTransaction(tx.id).first()
            items.forEach { item ->
                val prod = productDao.getProductById(item.productId)
                if (prod != null) {
                    productDao.updateStock(prod.id, prod.stock + item.quantity)
                }
            }
            
            userDao.insertAuditLog(AuditLogEntity(
                userId = user.id, userName = user.name,
                action = "REFUND TRANSAKSI #${tx.id}: ${formatRupiah(tx.totalPrice)}",
                actionType = "TRANSACTION"
            ))
            
            withContext(Dispatchers.Main) { onResult(true, "Transaksi berhasil di-refund.") }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal refund: ${e.message}") }
        }
    }
}

fun PosViewModel.generateQrisPayment(amount: Long, payload: String) {
    _qrisDynamicString.value = QrisHelper.generateDynamicQris(payload, amount)
    _isQrisPaid.value = false
}

fun PosViewModel.generateTransferPayment(amount: Long) {
    val uniqueCents = (1..999).random()
    _uniqueTransferAmount.value = amount + uniqueCents
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
    val holdsA = _holdOrders.value.filter { it.tableName == tableA }
    val holdsB = _holdOrders.value.filter { it.tableName == tableB }
    
    if (holdsA.isEmpty() && holdsB.isEmpty()) return
    
    viewModelScope.launch(Dispatchers.IO) {
        try {
            holdsB.forEach { orderB ->
                val mergedOrder = orderB.copy(tableName = tableA, id = 0, branchId = _currentBranchId.value)
                holdOrderDao.insertHoldOrder(mergedOrder)
                holdOrderDao.deleteHoldOrder(orderB.id)
            }
            withContext(Dispatchers.Main) { _selectedTable.value = tableA }
        } catch (e: Exception) {
            Log.e("MergeOrder", "Gagal gabung meja: ${e.message}")
        }
    }
}