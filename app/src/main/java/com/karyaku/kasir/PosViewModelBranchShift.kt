package com.karyaku.kasir

import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ====================================================================
// FILE 2 — BRANCH, SHIFT, CASH FLOW, SETUP
// ====================================================================
// Extension functions untuk PosViewModel.
// Berisi: Modul D-1 (multi-cabang) + Manajemen Shift Kasir.
// ====================================================================

// ================================================================
// MODUL D-1: INIT CABANG UNTUK USER YANG LOGIN
// ================================================================
fun PosViewModel.initCurrentBranchForUser(user: UserEntity) {
    _currentBranchId.value = user.branchId
    _isBranchSwitchLocked.value = (user.role != "OWNER")
    viewModelScope.launch(Dispatchers.IO) {
        try { checkActiveShift() } catch (e: Exception) { e.printStackTrace() }
    }
}

// ================================================================
// MODUL D-1: SWITCH CABANG
// ================================================================
fun PosViewModel.switchBranch(newBranchId: Long, onResult: (Boolean, String) -> Unit) {
    if (_isBranchSwitchLocked.value) {
        onResult(false, "Hanya OWNER yang bisa pindah cabang.")
        return
    }
    if (newBranchId == _currentBranchId.value) {
        onResult(true, "Sudah di cabang ini.")
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val targetBranch = branchDao.getBranchById(newBranchId)
            if (targetBranch == null) {
                withContext(Dispatchers.Main) { onResult(false, "Cabang tidak ditemukan.") }
                return@launch
            }

            val activeShift = userDao.getActiveShiftByBranch(_currentBranchId.value)
            if (activeShift != null) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Masih ada shift aktif di cabang ini. Tutup kasir dulu sebelum pindah cabang.")
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                _currentBranchId.value = newBranchId
                _activeShift.value = null
                _cart.value = emptyList()
                _discount.value = 0
                _voucherDiscount.value = 0
                _appliedVoucher.value = ""
                _selectedCustomer.value = null
                _pointsUsed.value = 0
                _customerMemberships.value = emptyList()
                _pickupDate.value = 0
                onResult(true, "Pindah ke ${targetBranch.name}")
            }
            checkActiveShift()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal pindah cabang: ${e.message}") }
        }
    }
}

// ================================================================
// MODUL D-1: CRUD CABANG
// ================================================================
fun PosViewModel.addBranch(name: String, address: String, phone: String, onResult: (Boolean, String) -> Unit) {
    if (_isBranchSwitchLocked.value) {
        onResult(false, "Hanya OWNER yang bisa menambah cabang.")
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val branch = BranchEntity(
                name = name,
                address = address,
                phone = phone,
                isMainBranch = false
            )
            val newId = branchDao.insertBranch(branch)
            withContext(Dispatchers.Main) {
                onResult(true, "Cabang '$name' ditambahkan (ID: $newId).")
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal tambah cabang: ${e.message}") }
        }
    }
}

fun PosViewModel.updateBranch(branch: BranchEntity, onResult: (Boolean, String) -> Unit) {
    if (_isBranchSwitchLocked.value) {
        onResult(false, "Hanya OWNER yang bisa mengedit cabang.")
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        try {
            branchDao.updateBranch(branch.copy(updatedAt = System.currentTimeMillis()))
            withContext(Dispatchers.Main) { onResult(true, "Cabang diperbarui.") }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal update cabang: ${e.message}") }
        }
    }
}

fun PosViewModel.deleteBranch(branch: BranchEntity, onResult: (Boolean, String) -> Unit) {
    if (_isBranchSwitchLocked.value) {
        onResult(false, "Hanya OWNER yang bisa menghapus cabang.")
        return
    }
    if (branch.isMainBranch) {
        onResult(false, "Cabang utama tidak bisa dihapus.")
        return
    }
    if (branch.id == _currentBranchId.value) {
        onResult(false, "Tidak bisa hapus cabang yang sedang aktif. Pindah cabang dulu.")
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val txCount = branchDao.getTransactionCountForBranch(branch.id)
            val prodCount = branchDao.getProductCountForBranch(branch.id)
            if (txCount > 0 || prodCount > 0) {
                withContext(Dispatchers.Main) {
                    onResult(
                        false,
                        "Cabang punya $txCount transaksi & $prodCount produk. Pindahkan datanya dulu."
                    )
                }
                return@launch
            }
            branchDao.deleteBranch(branch.id)
            withContext(Dispatchers.Main) { onResult(true, "Cabang '${branch.name}' dihapus.") }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal hapus cabang: ${e.message}") }
        }
    }
}

// ================================================================
// SETUP & AUTH INITIALIZATION
// ================================================================
fun PosViewModel.registerFullSetup(
    businessType: String,
    storeName: String,
    storeAddress: String,
    storePhone: String,
    adminName: String,
    adminPin: String,
    cashierName: String,
    cashierPin: String,
    onSuccess: (UserEntity) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            database.withTransaction {
                val mainBranch = BranchEntity(
                    name = "$storeName (Pusat)",
                    address = storeAddress,
                    phone = storePhone,
                    isMainBranch = true
                )
                val branchId = branchDao.insertBranch(mainBranch)

                val admin = UserEntity(
                    name = adminName,
                    pin = hashPin(adminPin), 
                    role = "OWNER",
                    branchId = branchId
                )
                val adminId = userDao.insertUser(admin)

                val cashier = UserEntity(
                    name = cashierName,
                    pin = hashPin(cashierPin),
                    role = "CASHIER",
                    branchId = branchId
                )
                userDao.insertUser(cashier)

                withContext(Dispatchers.Main) {
                    _currentBranchId.value = branchId
                    _isBranchSwitchLocked.value = false
                    onSuccess(admin.copy(id = adminId))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

suspend fun PosViewModel.getFirstOwner(): UserEntity? {
    return try {
        userDao.getAllUsersOnce().firstOrNull { it.role == "OWNER" }
    } catch (e: Exception) {
        null
    }
}

// ================================================================
// SHIFT MANAGEMENT
// ================================================================
fun PosViewModel.checkActiveShift() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            _activeShift.value = userDao.getActiveShiftByBranch(_currentBranchId.value)
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.openShift(user: UserEntity, modalAwal: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val activeBranchId = _currentBranchId.value
            val shift = ShiftEntity(
                userId = user.id, 
                userName = user.name, 
                openingCash = modalAwal,
                branchId = activeBranchId
            )
            userDao.insertShift(shift)
            userDao.insertAuditLog(AuditLogEntity(
                userId = user.id, 
                userName = user.name, 
                action = "BUKA KASIR (Cabang #$activeBranchId): Modal ${formatRupiah(modalAwal)}", 
                actionType = "SYSTEM"
            ))
            checkActiveShift()
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.closeShift(user: UserEntity, uangFisik: Long, note: String, onSuccess: (ShiftEntity) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val shift = _activeShift.value
            if (shift != null) {
                val totalPenjualanTunai = _transactions.value.filter { 
                    it.timestamp >= shift.startTime 
                    && it.status == "SUCCESS" 
                    && it.paymentMethod == "CASH"
                    && it.branchId == shift.branchId
                }.sumOf { it.totalPrice }
                
                val totalKasKeluar = cashFlowDao.getTotalCashOut(shift.id) ?: 0L
                val totalKasMasuk = cashFlowDao.getTotalCashIn(shift.id) ?: 0L
                
                val uangSistem = shift.openingCash + totalPenjualanTunai + totalKasMasuk - totalKasKeluar
                val selisih = uangFisik - uangSistem

                userDao.closeShift(shift.id, System.currentTimeMillis(), uangSistem, uangFisik, selisih)
                userDao.insertAuditLog(AuditLogEntity(
                    userId = user.id, userName = user.name, 
                    action = "TUTUP KASIR (Cabang #${shift.branchId}): Fisik ${formatRupiah(uangFisik)}, Sistem ${formatRupiah(uangSistem)}, Selisih ${formatRupiah(selisih)} | Catatan: $note", 
                    actionType = "SYSTEM"
                ))
                checkActiveShift()
                withContext(Dispatchers.Main) {
                    onSuccess(shift.copy(
                        endTime = System.currentTimeMillis(), 
                        expectedCash = uangSistem, 
                        closingCash = uangFisik, 
                        discrepancy = selisih, 
                        status = "CLOSED"
                    ))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.addCashFlow(type: String, amount: Long, note: String, user: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val shift = _activeShift.value ?: return@launch
            val cashFlow = CashFlowEntity(shiftId = shift.id, type = type, amount = amount, note = note, userId = user.id, userName = user.name)
            cashFlowDao.insertCashFlow(cashFlow)
            val actionText = if (type == "OUT") "KAS KELUAR: $note (${formatRupiah(amount)})" else "KAS MASUK: $note (${formatRupiah(amount)})"
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = actionText, actionType = "FINANCE"))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

// ================================================================
// STATE SETTERS
// ================================================================
fun PosViewModel.setTaxPercent(tax: Double) { _taxPercent.value = tax }
fun PosViewModel.setServiceChargePercent(charge: Double) { _serviceChargePercent.value = charge }
fun PosViewModel.setTable(table: String) { _selectedTable.value = table }
fun PosViewModel.setOrderType(type: String) { _selectedOrderType.value = type }
fun PosViewModel.setPickupDate(date: Long) { _pickupDate.value = date }