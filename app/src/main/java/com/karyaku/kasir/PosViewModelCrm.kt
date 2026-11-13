package com.karyaku.kasir

import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.CustomerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ====================================================================
// MODUL G — CRM LANJUTAN
// ====================================================================
// Extension functions untuk PosViewModel.
// Berisi: tier pelanggan, birthday check, profil edit, filter.
// ====================================================================

// ================================================================
// TIER PELANGGAN
// ================================================================
// Tier dihitung dari `points` (computed, tidak disimpan di DB).
// Warna di sini hanya referensi; UI bisa override sesuai tema.
// ================================================================
enum class CustomerTier(val label: String, val minPoints: Int) {
    REGULAR("Regular", 0),
    SILVER("Silver", 100),
    GOLD("Gold", 500),
    PLATINUM("Platinum", 1000);

    companion object {
        fun fromPoints(points: Int): CustomerTier = when {
            points >= 1000 -> PLATINUM
            points >= 500 -> GOLD
            points >= 100 -> SILVER
            else -> REGULAR
        }
    }
}

// ================================================================
// BIRTHDAY CHECK
// ================================================================
// Bandingkan MONTH + DAY_OF_MONTH saja (abaikan tahun).
// Return false jika birthDate = 0 (tidak diisi).
// ================================================================
fun isBirthdayToday(customer: CustomerEntity): Boolean {
    if (customer.birthDate <= 0L) return false
    val today = java.util.Calendar.getInstance()
    val birth = java.util.Calendar.getInstance().apply { timeInMillis = customer.birthDate }
    return today.get(java.util.Calendar.MONTH) == birth.get(java.util.Calendar.MONTH) &&
           today.get(java.util.Calendar.DAY_OF_MONTH) == birth.get(java.util.Calendar.DAY_OF_MONTH)
}

// ================================================================
// CRUD PROFIL PELANGGAN
// ================================================================
fun PosViewModel.updateCustomerBirthday(customerId: Long, birthDate: Long, onResult: (Boolean) -> Unit = {}) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val existing = customerDao.getCustomerByIdOnce(customerId)
            if (existing != null) {
                customerDao.updateCustomerProfile(
                    customerId = customerId,
                    name = existing.name,
                    phone = existing.phone,
                    birthDate = birthDate
                )
                withContext(Dispatchers.Main) { onResult(true) }
            } else {
                withContext(Dispatchers.Main) { onResult(false) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) { onResult(false) }
        }
    }
}

fun PosViewModel.updateCustomerProfile(
    customerId: Long,
    name: String,
    phone: String,
    birthDate: Long,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    if (name.isBlank()) {
        onResult(false, "Nama tidak boleh kosong.")
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val existing = customerDao.getCustomerByIdOnce(customerId)
            if (existing == null) {
                withContext(Dispatchers.Main) { onResult(false, "Pelanggan tidak ditemukan.") }
                return@launch
            }
            customerDao.updateCustomerProfile(
                customerId = customerId,
                name = name.trim(),
                phone = phone.trim().ifBlank { "-" },
                birthDate = birthDate
            )
            userDao.insertAuditLog(
                com.karyaku.kasir.data.local.AuditLogEntity(
                    userId = 0L,
                    userName = "SYSTEM",
                    action = "EDIT PROFIL PELANGGAN: ${existing.name} → $name",
                    actionType = "CRM"
                )
            )
            withContext(Dispatchers.Main) { onResult(true, "Profil pelanggan diperbarui.") }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) { onResult(false, "Gagal update: ${e.message}") }
        }
    }
}

// ================================================================
// QUERY HELPER
// ================================================================
suspend fun PosViewModel.getCustomerById(customerId: Long): CustomerEntity? {
    return try {
        customerDao.getCustomerByIdOnce(customerId)
    } catch (e: Exception) {
        null
    }
}

fun PosViewModel.getCustomersByTier(tier: CustomerTier): Flow<List<CustomerEntity>> {
    return customerDao.getCustomersByMinPoints(tier.minPoints)
}

fun PosViewModel.getTopSpenders(limit: Int = 10): Flow<List<CustomerEntity>> {
    return customerDao.getTopSpenders(limit)
}