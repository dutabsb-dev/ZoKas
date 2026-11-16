package com.karyaku.kasir

import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// ====================================================================
// FILE 7 — TRANSAKSI ITEM, RESERVASI, EXPENSE
// ====================================================================

fun PosViewModel.getTransactionItems(transactionId: Long): Flow<List<TransactionItemEntity>> {
    return transactionItemDao.getItemsByTransaction(transactionId)
}

fun PosViewModel.addReservation(customerName: String, phone: String, dateTime: Long, resourceName: String, notes: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            reservationDao.insertReservation(ReservationEntity(
                branchId = _currentBranchId.value, // FIX
                customerName = customerName,
                phone = phone,
                dateTime = dateTime,
                resourceName = resourceName,
                notes = notes
            ))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.updateReservationStatus(id: Long, status: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try { reservationDao.updateStatus(id, status) } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.deleteReservation(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        try { reservationDao.deleteReservation(id) } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.addExpense(category: String, amount: Long, note: String, user: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val expense = ExpenseEntity(
                branchId = _currentBranchId.value, // FIX
                category = category,
                amount = amount,
                note = note,
                userId = user.id,
                userName = user.name
            )
            expenseDao.insertExpense(expense)
            userDao.insertAuditLog(AuditLogEntity(
                userId = user.id, userName = user.name,
                action = "PENGELUARAN: $category - ${formatRupiah(amount)} ($note)",
                actionType = "FINANCE"
            ))
        } catch (e: Exception) { e.printStackTrace() }
    }
}