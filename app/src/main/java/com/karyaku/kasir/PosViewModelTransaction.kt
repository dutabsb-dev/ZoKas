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
                    put(JSONObject().apply {
                        put("productId", item.product.id)
                        put("name", item.product.name)
                        put("price", item.product.price)
                        put("quantity", item.quantity)
                        put("variation", item.selectedVariation)
                        put("notes", item.notes)
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
                    newCart.add(CartItem(product = product, quantity = obj.getInt("quantity"), selectedVariation = obj.optString("variation", ""), notes = obj.optString("notes", "")))
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

fun PosViewModel.payUnpaidTransaction(tx: TransactionEntity, cashReceived: Long, paidMethod: String, user: UserEntity, onSuccess: () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val changeAmount = if (cashReceived > tx.totalPrice) cashReceived - tx.totalPrice else 0L
            transactionDao.updateTransactionStatus(tx.id, cashReceived, changeAmount, paidMethod, System.currentTimeMillis())
            
            cashFlowDao.insertCashFlow(CashFlowEntity(
                shiftId = _activeShift.value?.id ?: 0L, branchId = _currentBranchId.value, type = "IN", 
                amount = tx.totalPrice, note = "Pelunasan BON #${tx.id} (${tx.customerName})", userId = user.id, userName = user.name
            ))
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "PELUNASAN BON #${tx.id}: ${formatRupiah(tx.totalPrice)}", actionType = "TRANSACTION"))
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
            transactionItemDao.getItemsByTransaction(transaction.id).first().forEach { item ->
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
            transactionItemDao.getItemsByTransaction(transaction.id).first().forEach { item ->
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