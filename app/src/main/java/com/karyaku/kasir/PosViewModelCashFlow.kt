package com.karyaku.kasir

import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ====================================================================
// FILE CASH FLOW & SHIFT (TANPA TRANSAKSI)
// processPayment & printReceipt sudah dipindah ke PosViewModelTransaction.kt
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
            // Ambil data fresh HANYA untuk cabang ini
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