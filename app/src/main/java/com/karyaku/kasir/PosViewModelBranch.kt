package com.karyaku.kasir

import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ====================================================================
// MODUL D-1: MANAJEMEN CABANG (MULTI-BRANCH)
// ====================================================================

fun PosViewModel.initCurrentBranchForUser(user: UserEntity) {
    _currentBranchId.value = user.branchId
    _isBranchSwitchLocked.value = (user.role == "CASHIER")
    checkActiveShift(user.id) // FIX: Cek shift berdasarkan ID user yang login
}

fun PosViewModel.switchBranch(branchId: Long, onResult: (Boolean, String) -> Unit) {
    if (_activeShift.value != null) {
        onResult(false, "Tutup kasir (shift) terlebih dahulu sebelum pindah cabang.")
        return
    }
    _currentBranchId.value = branchId
    // Saat pindah cabang, shift otomatis null, UI akan memunculkan pop-up OpenShiftDialog
    _activeShift.value = null 
    onResult(true, "Berhasil pindah cabang.")
}

fun PosViewModel.addBranch(name: String, address: String, phone: String, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            branchDao.insertBranch(BranchEntity(name = name, address = address, phone = phone, isMainBranch = false))
            withContext(Dispatchers.Main) { onResult(true, "Cabang '$name' berhasil ditambahkan.") }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal tambah cabang: ${e.message}") }
        }
    }
}

fun PosViewModel.updateBranch(branch: BranchEntity, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val existing = branchDao.getBranchById(branch.id)
            if (existing != null && existing.isMainBranch) {
                branchDao.updateBranch(branch.copy(isMainBranch = true))
            } else {
                branchDao.updateBranch(branch)
            }
            withContext(Dispatchers.Main) { onResult(true, "Cabang berhasil diperbarui.") }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal update cabang: ${e.message}") }
        }
    }
}

fun PosViewModel.deleteBranch(branch: BranchEntity, onResult: (Boolean, String) -> Unit) {
    if (branch.isMainBranch) {
        onResult(false, "Cabang utama tidak bisa dihapus.")
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val txCount = branchDao.getTransactionCountForBranch(branch.id)
            val prodCount = branchDao.getProductCountForBranch(branch.id)
            if (txCount > 0 || prodCount > 0) {
                withContext(Dispatchers.Main) { 
                    onResult(false, "Tidak bisa dihapus. Cabang ini masih memiliki $txCount transaksi dan $prodCount produk.") 
                }
                return@launch
            }
            branchDao.deleteBranch(branch.id)
            withContext(Dispatchers.Main) { onResult(true, "Cabang berhasil dihapus.") }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal hapus cabang: ${e.message}") }
        }
    }
}