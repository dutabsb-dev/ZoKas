package com.karyaku.kasir

import android.util.Base64
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

// ================================================================
// AUTH
// ================================================================
suspend fun PosViewModel.login(pin: String): UserEntity? {
    return try {
        val user = userDao.loginByPin(hashPin(pin))
        if (user != null) userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "LOGIN_SUCCESS (PIN)", actionType = "AUTH"))
        user
    } catch (e: Exception) { null }
}

fun PosViewModel.updatePin(user: UserEntity, newPin: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            userDao.updatePin(user.id, hashPin(newPin))
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "UBAH PIN", actionType = "SYSTEM"))
        } catch (e: Exception) {}
    }
}

// ================================================================
// BIOMETRIC
// ================================================================
fun PosViewModel.isUserBiometricEnrolled(user: UserEntity): Boolean = user.biometricKey.isNotBlank()

fun PosViewModel.registerBiometric(activity: FragmentActivity, user: UserEntity, onResult: (Boolean, String) -> Unit) {
    if (user.role != "OWNER") { onResult(false, "Hanya OWNER yang diizinkan."); return }
    if (!BiometricCryptoHelper.isBiometricSupported(activity)) { onResult(false, "Perangkat tidak mendukung Biometrik Strong."); return }

    val alias = "ZOAKS_USER_${user.id}_${System.currentTimeMillis()}"
    if (!BiometricCryptoHelper.createKey(alias)) { onResult(false, "Gagal membuat kunci enkripsi."); return }

    val cipher = BiometricCryptoHelper.getCipherForEncryption(alias) ?: return onResult(false, "Gagal menyiapkan enkripsi.")

    BiometricCryptoHelper.showBiometricPrompt(
        activity, "Daftarkan Biometrik", "Tempelkan sidik jari/wajah untuk ${user.name}", "Batal", cipher,
        onSuccess = { result ->
            try {
                val encrypted = result.cryptoObject?.cipher?.doFinal("ValidBiometric".toByteArray(StandardCharsets.UTF_8))
                val iv = result.cryptoObject?.cipher?.iv
                if (encrypted != null && iv != null) {
                    val keyData = "$alias|${Base64.encodeToString(iv, Base64.NO_WRAP)}"
                    viewModelScope.launch(Dispatchers.IO) {
                        userDao.updateBiometricKey(user.id, keyData)
                        userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "DAFTAR BIOMETRIK", actionType = "AUTH"))
                        withContext(Dispatchers.Main) { onResult(true, "Biometrik berhasil didaftarkan!") }
                    }
                } else onResult(false, "Gagal mengenkripsi data.")
            } catch (e: Exception) { onResult(false, "Error enkripsi: ${e.message}") }
        }, onError = { onResult(false, "Gagal: $it") }
    )
}

fun PosViewModel.loginWithBiometric(activity: FragmentActivity, user: UserEntity, onResult: (Boolean, String) -> Unit) {
    if (user.role != "OWNER") { onResult(false, "Hanya OWNER."); return }
    if (!BiometricCryptoHelper.isBiometricSupported(activity)) { onResult(false, "Tidak mendukung Biometrik Strong."); return }
    if (user.biometricKey.isBlank()) { onResult(false, "Belum mendaftarkan biometrik."); return }

    val parts = user.biometricKey.split("|")
    if (parts.size != 2) {
        viewModelScope.launch(Dispatchers.IO) {
            userDao.updateBiometricKey(user.id, "")
        }
        registerBiometric(activity, user, onResult)
        return
    }

    val ivBytes = try {
        Base64.decode(parts[1], Base64.NO_WRAP)
    } catch (_: Exception) {
        ByteArray(0)
    }

    val cipher = if (ivBytes.isNotEmpty()) {
        BiometricCryptoHelper.getCipherForDecryption(parts[0], ivBytes)
    } else null

    if (cipher == null) {
        BiometricCryptoHelper.deleteKey(parts[0])
        viewModelScope.launch(Dispatchers.IO) {
            userDao.updateBiometricKey(user.id, "")
            withContext(Dispatchers.Main) {
                registerBiometric(activity, user) { regOk, regMsg ->
                    if (regOk) {
                        onResult(true, "Login Berhasil")
                    } else {
                        onResult(false, if (regMsg.isNotBlank()) regMsg else "Kunci kedaluwarsa. Silakan daftarkan ulang sidik jari.")
                    }
                }
            }
        }
        return
    }

    BiometricCryptoHelper.showBiometricPrompt(
        activity, "Login Biometrik", "Halo ${user.name}", "Gunakan PIN", cipher,
        onSuccess = { 
            viewModelScope.launch(Dispatchers.IO) {
                userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "LOGIN (BIOMETRIC)", actionType = "AUTH"))
                withContext(Dispatchers.Main) { onResult(true, "Login Berhasil") }
            }
        }, onError = { err ->
            if (err.contains("key", ignoreCase = true) || err.contains("invalidat", ignoreCase = true)) {
                BiometricCryptoHelper.deleteKey(parts[0])
                viewModelScope.launch(Dispatchers.IO) {
                    userDao.updateBiometricKey(user.id, "")
                }
            }
            onResult(false, "Gagal Login: $err")
        }
    )
}

// ================================================================
// USER CRUD
// ================================================================
fun PosViewModel.addUser(name: String, pin: String, role: String, baseSalary: Long = 0L, adminUser: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            userDao.insertUser(UserEntity(name = name, pin = hashPin(pin), role = role, baseSalary = baseSalary, branchId = _currentBranchId.value))
            userDao.insertAuditLog(AuditLogEntity(userId = adminUser.id, userName = adminUser.name, action = "TAMBAH KARYAWAN: $name", actionType = "AUTH"))
        } catch (e: Exception) {}
    }
}

fun PosViewModel.updateUser(user: UserEntity, adminUser: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            userDao.updateUser(user.copy(pin = hashPin(user.pin)))
            userDao.insertAuditLog(AuditLogEntity(userId = adminUser.id, userName = adminUser.name, action = "EDIT KARYAWAN: ${user.name}", actionType = "AUTH"))
        } catch (e: Exception) {}
    }
}

fun PosViewModel.deleteUser(user: UserEntity, adminUser: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            if (user.biometricKey.isNotBlank()) BiometricCryptoHelper.deleteKey(user.biometricKey.split("|").firstOrNull() ?: "")
            userDao.deleteUser(user)
            userDao.insertAuditLog(AuditLogEntity(userId = adminUser.id, userName = adminUser.name, action = "HAPUS KARYAWAN: ${user.name}", actionType = "AUTH"))
        } catch (e: Exception) {}
    }
}

// ================================================================
// CUSTOMER & MEMBERSHIP
// ================================================================
fun PosViewModel.setCustomer(customer: CustomerEntity?) {
    _selectedCustomer.value = customer
    _pointsUsed.value = 0
    _discount.value = 0
    if (customer != null) {
        viewModelScope.launch(Dispatchers.IO) {
            membershipDao.getActiveMembershipsForCustomer(customer.id).collectLatest { _customerMemberships.value = it }
        }
    } else _customerMemberships.value = emptyList()
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
        try { customerDao.insertCustomer(CustomerEntity(name = name, phone = phone, birthDate = birthDate)) } catch (e: Exception) {}
    }
}

fun PosViewModel.applyMembershipToCart(cartItem: CartItem, customerMembership: CustomerMembershipEntity) {
    val currentCart = _cart.value.toMutableList()
    val idx = currentCart.indexOfFirst { it.product.id == cartItem.product.id && it.selectedVariation == cartItem.selectedVariation }
    if (idx != -1) {
        currentCart[idx] = currentCart[idx].copy(usedMembershipId = customerMembership.id, usedMembershipName = customerMembership.membershipName)
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
        } catch (e: Exception) { withContext(Dispatchers.Main) { onResult(false, "Terjadi kesalahan sistem.") } }
    }
}