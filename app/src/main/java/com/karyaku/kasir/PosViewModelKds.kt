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
val PosViewModel.activeKitchenTickets: StateFlow<List<KitchenTicketEntity>> =
    this.kitchenTicketDao.getActiveTickets()
        .catch { emit(emptyList<KitchenTicketEntity>()) }
        .stateIn(this.viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

// ================================================================
// UPDATE STATUS
// ================================================================
fun PosViewModel.updateKitchenTicketStatus(ticketId: Long, newStatus: String) {
    this.viewModelScope.launch(Dispatchers.IO) {
        try {
            this@updateKitchenTicketStatus.kitchenTicketDao.updateStatus(ticketId, newStatus)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// ================================================================
// CLEAR COMPLETED
// ================================================================
fun PosViewModel.clearCompletedKitchenTickets() {
    this.viewModelScope.launch(Dispatchers.IO) {
        try {
            this@clearCompletedKitchenTickets.kitchenTicketDao.clearCompletedTickets()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}