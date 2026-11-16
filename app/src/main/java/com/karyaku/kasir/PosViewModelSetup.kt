package com.karyaku.kasir

import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.AuditLogEntity
import com.karyaku.kasir.data.local.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ====================================================================
// FILE 8 — SETUP WIZARD REGISTRATION
// ====================================================================

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
            // 1. Simpan Akun Admin (Owner)
            val admin = UserEntity(
                name = adminName,
                pin = hashPin(adminPin),
                role = "OWNER",
                branchId = 1L
            )
            val adminId = userDao.insertUser(admin)
            
            // 2. Simpan Akun Kasir
            val cashier = UserEntity(
                name = cashierName,
                pin = hashPin(cashierPin),
                role = "CASHIER",
                branchId = 1L
            )
            userDao.insertUser(cashier)
            
            // 3. Catat Log Audit
            userDao.insertAuditLog(AuditLogEntity(
                userId = adminId,
                userName = adminName,
                action = "SETUP TOKO SELESAI: $storeName ($businessType)",
                actionType = "SYSTEM"
            ))

            // 4. Kembalikan data Admin yang baru dibuat ke UI
            withContext(Dispatchers.Main) {
                onSuccess(admin.copy(id = adminId))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}