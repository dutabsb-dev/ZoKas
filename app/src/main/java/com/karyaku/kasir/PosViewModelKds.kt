package com.karyaku.kasir

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ====================================================================
// MODUL B — KDS FUNCTIONS
// ====================================================================

fun PosViewModel.updateKitchenTicketStatus(ticketId: Long, newStatus: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            kitchenTicketDao.updateStatus(ticketId, newStatus)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun PosViewModel.clearCompletedKitchenTickets() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            kitchenTicketDao.clearCompletedTickets()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}