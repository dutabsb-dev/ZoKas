package com.karyaku.kasir

import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.KitchenTicketEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ====================================================================
// MODUL B — KDS (KITCHEN DISPLAY SYSTEM)
// ====================================================================
// Extension functions untuk PosViewModel.
// ====================================================================

// ================================================================
// STATE: ACTIVE TICKETS
// ================================================================
// Mengambil tiket yang statusnya belum READY (PENDING atau COOKING).
// ================================================================
val PosViewModel.activeKitchenTickets: StateFlow<List<KitchenTicketEntity>> =
    kitchenTicketDao.getActiveTickets()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

// ================================================================
// UPDATE STATUS
// ================================================================
// Mengubah status tiket: PENDING -> COOKING -> READY
// ================================================================
fun PosViewModel.updateKitchenTicketStatus(ticketId: Long, newStatus: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            kitchenTicketDao.updateStatus(ticketId, newStatus)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// ================================================================
// CLEAR COMPLETED
// ================================================================
// Menghapus tiket yang sudah READY (selesai dimasak) dari database.
// ================================================================
fun PosViewModel.clearCompletedKitchenTickets() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            kitchenTicketDao.clearCompletedTickets()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}