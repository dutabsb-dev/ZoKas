package com.karyaku.kasir

import android.content.Context
import android.util.Base64
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher

// ====================================================================
// FILE 5 — USER, AUTH, CUSTOMER, MEMBERSHIP, VOUCHER, FILTER
// ====================================================================

// ================================================================
// AUTH
// ================================================================
suspend fun PosViewModel.login(pin: String): UserEntity? {
    return try {
        val hashedPin = hashPin(pin)
        val user = userDao.loginByPin(hashedPin)
        if (user != null) {
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "LOGIN_SUCCESS (PIN)", actionType = "AUTH"))
        }
        user
    } catch (e: Exception) { null }
}

fun PosViewModel.updatePin(user: UserEntity, newPin: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val hashedPin = hashPin(newPin)
            userDao.updatePin(user.id, hashedPin)
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "UBAH PIN", actionType = "SYSTEM"))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

// ================================================================
// BIOMETRIC LOGIN & REGISTRATION (MODUL ABSENSI)
// ================================================================

// Cek apakah user ini sudah punya biometrik terdaftar
fun PosViewModel.isUserBiometricEnrolled(user: UserEntity): Boolean {
    return user.biometricKey.isNotBlank()
}

// Fungsi untuk mendaftarkan biometrik baru
fun PosViewModel.registerBiometric(activity: FragmentActivity, user: UserEntity, onResult: (Boolean, String) -> Unit) {
    val status = BiometricHelper.checkSensorStatus(activity)
    if (status != "SUPPORTED") {
        onResult(false, BiometricHelper.getSensorStatusMessage(status))
        return
    }

    val alias = "ZOAKS_USER_${user.id}_${System.currentTimeMillis()}"
    BiometricCryptoHelper.createKey(alias)
    val cipher = BiometricCryptoHelper.getCipherForEncryption(alias)
    
    if (cipher == null) {
        onResult(false, "Gagal menyiapkan enkripsi.")
        return
    }

    BiometricCryptoHelper.showBiometricPrompt(
        activity = activity,
        title = "Daftarkan Biometrik",
        subtitle = "Tempelkan sidik jari atau wajah untuk ${user.name}",
        negativeButtonText = "Batal",
        cipher = cipher,
        onSuccess = { result ->
            try {
                val encrypted = result.cryptoObject?.cipher?.doFinal("ValidBiometric".toByteArray(StandardCharsets.UTF_8))
                val iv = result.cryptoObject?.cipher?.iv
                
                if (encrypted != null && iv != null) {
                    val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)
                    val encryptedString = Base64.encodeToString(encrypted, Base64.NO_WRAP)
                    
                    // Simpan alias & iv di database (format: "alias|ivBase64")
                    val keyData = "$alias|$ivString"
                    viewModelScope.launch(Dispatchers.IO) {
                        userDao.updateBiometricKey(user.id, keyData)
                        userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "DAFTAR BIOMETRIK", actionType = "AUTH"))
                        withContext(Dispatchers.Main) { onResult(true, "Biometrik berhasil didaftarkan!") }
                    }
                } else {
                    onResult(false, "Gagal mengenkripsi data.")
                }
            } catch (e: Exception) {
                onResult(false, "Error enkripsi: ${e.message}")
            }
        },
        onError = { error ->
            onResult(false, "Gagal mendaftarkan: $error")
        }
    )
}

// Fungsi untuk login menggunakan biometrik
fun PosViewModel.loginWithBiometric(activity: FragmentActivity, user: UserEntity, onResult: (Boolean, String) -> Unit) {
    val status = BiometricHelper.checkSensorStatus(activity)
    if (status != "SUPPORTED") {
        onResult(false, BiometricHelper.getSensorStatusMessage(status))
        return
    }

    if (user.biometricKey.isBlank()) {
        onResult(false, "Anda belum mendaftarkan sidik jari/wajah.")
        return
    }

    val parts = user.biometricKey.split("|")
    if (parts.size != 2) {
        onResult(false, "Data biometrik rusak.")
        return
    }

    val alias = parts[0]
    val iv = Base64.decode(parts[1], Base64.NO_WRAP)
    val cipher = BiometricCryptoHelper.getCipherForDecryption(alias, iv)

    if (cipher == null) {
        onResult(false, "Kunci biometrik kedaluwarsa. Silakan login dengan PIN dan daftarkan ulang.")
        return
    }

    BiometricCryptoHelper.showBiometricPrompt(
        activity = activity,
        title = "Login Biometrik",
        subtitle = "Halo ${user.name}, tempelkan sidik jari/wajah Anda",
        negativeButtonText = "Gunakan PIN",
        cipher = cipher,
        onSuccess = { result ->
            viewModelScope.launch(Dispatchers.IO) {
                userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "LOGIN_SUCCESS (BIOMETRIC)", actionType = "AUTH"))
                withContext(Dispatchers.Main) { onResult(true, "Login Berhasil") }
            }
        },
        onError = { error ->
            onResult(false, "Gagal Login: $error")
        }
    )
}

// ================================================================
// USER CRUD
// ================================================================
fun PosViewModel.addUser(name: String, pin: String, role: String, baseSalary: Long = 0L, adminUser: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val hashedPin = hashPin(pin)
            userDao.insertUser(UserEntity(
                name = name, 
                pin = hashedPin, 
                role = role, 
                baseSalary = baseSalary,
                branchId = _currentBranchId.value
            ))
            userDao.insertAuditLog(AuditLogEntity(userId = adminUser.id, userName = adminUser.name, action = "TAMBAH KARYAWAN: $name (Gaji: ${formatRupiah(baseSalary)})", actionType = "AUTH"))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.updateUser(user: UserEntity, adminUser: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val hashedPin = hashPin(user.pin)
            userDao.updateUser(user.copy(pin = hashedPin))
            userDao.insertAuditLog(AuditLogEntity(userId = adminUser.id, userName = adminUser.name, action = "EDIT KARYAWAN: ${user.name}", actionType = "AUTH"))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.deleteUser(user: UserEntity, adminUser: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            // Hapus juga data biometrik di Keystore HP jika ada
            if (user.biometricKey.isNotBlank()) {
                val alias = user.biometricKey.split("|").firstOrNull()
                if (alias != null) BiometricCryptoHelper.deleteKey(alias)
            }
            userDao.deleteUser(user)
            userDao.insertAuditLog(AuditLogEntity(userId = adminUser.id, userName = adminUser.name, action = "HAPUS KARYAWAN: ${user.name}", actionType = "AUTH"))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

// ================================================================
// CUSTOMER
// ================================================================
fun PosViewModel.setCustomer(customer: CustomerEntity?) {
    _selectedCustomer.value = customer
    _pointsUsed.value = 0
    _discount.value = 0
    
    if (customer != null) {
        viewModelScope.launch(Dispatchers.IO) {
            membershipDao.getActiveMembershipsForCustomer(customer.id).collectLatest {
                _customerMemberships.value = it
            }
        }
    } else {
        _customerMemberships.value = emptyList()
    }
}

fun PosViewModel.redeemPoints(pointsToRedeem: Int) {
    val customer = _selectedCustomer.value ?: return
    if (pointsToRedeem > 0 && pointsToRedeem <= customer.points) {
        _pointsUsed.value = pointsToRedeem
        _discount.value = (pointsToRedeem * 1000).toLong()
    }
}

fun PosViewModel.addCustomer(name: String, phone: String, birthDate: Long = 0L) {
    viewModelScope.launch(Dispatchers.IO) {
        try { 
            customerDao.insertCustomer(
                CustomerEntity(
                    name = name, 
                    phone = phone,
                    birthDate = birthDate
                )
            ) 
        }
        catch (e: Exception) { e.printStackTrace() }
    }
}

// ================================================================
// MEMBERSHIP
// ================================================================
fun PosViewModel.addMembership(name: String, price: Long, quota: Int, days: Int) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            membershipDao.insertMembership(MembershipEntity(name = name, price = price, quotaCount = quota, validDays = days))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.buyMembership(customerId: Long, membership: MembershipEntity, onSuccess: () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val expiryDate = System.currentTimeMillis() + (membership.validDays * 24 * 60 * 60 * 1000L)
            val customerMembership = CustomerMembershipEntity(
                customerId = customerId,
                membershipId = membership.id,
                membershipName = membership.name,
                remainingQuota = membership.quotaCount,
                expiryDate = expiryDate
            )
            membershipDao.insertCustomerMembership(customerMembership)
            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.applyMembershipToCart(cartItem: CartItem, customerMembership: CustomerMembershipEntity) {
    val currentCart = _cart.value.toMutableList()
    val idx = currentCart.indexOfFirst { it.product.id == cartItem.product.id && it.selectedVariation == cartItem.selectedVariation }
    if (idx != -1) {
        currentCart[idx] = currentCart[idx].copy(
            usedMembershipId = customerMembership.id,
            usedMembershipName = customerMembership.membershipName
        )
        _cart.value = currentCart
    }
}

fun PosViewModel.removeMembershipFromCart(cartItem: CartItem) {
    val currentCart = _cart.value.toMutableList()
    val idx = currentCart.indexOfFirst { it.product.id == cartItem.product.id && it.selectedVariation == cartItem.selectedVariation }
    if (idx != -1) {
        currentCart[idx] = currentCart[idx].copy(usedMembershipId = null, usedMembershipName = null)
        _cart.value = currentCart
    }
}

// ================================================================
// VOUCHER
// ================================================================
fun PosViewModel.applyVoucher(code: String, onResult: (Boolean, String) -> Unit) {
    if (code.isBlank()) {
        _voucherDiscount.value = 0
        _appliedVoucher.value = ""
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val voucher = voucherDao.getVoucherByCode(code.uppercase())
            withContext(Dispatchers.Main) {
                if (voucher != null) {
                    _voucherDiscount.value = voucher.discountAmount
                    _appliedVoucher.value = voucher.code
                    onResult(true, "Voucher berhasil digunakan!")
                } else {
                    _voucherDiscount.value = 0
                    _appliedVoucher.value = ""
                    onResult(false, "Kode voucher tidak valid.")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Terjadi kesalahan sistem.") }
        }
    }
}

// ================================================================
// FILTER SETTERS
// ================================================================
fun PosViewModel.setSearchQuery(query: String) { _searchQuery.value = query }
fun PosViewModel.setCategory(category: String) { _selectedCategory.value = category }
fun PosViewModel.setDiscount(amount: Long) { _discount.value = amount }