package com.karyaku.kasir

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.biometric.BiometricManager
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

private const val TAG_VM = "ZOAKS_BIOMETRIC_VM"

// ================================================================
// AUTH
// ================================================================
suspend fun PosViewModel.login(pin: String): UserEntity? {
    return try {
        val hashedPin = hashPin(pin)
        val user = userDao.loginByPin(hashedPin)
        if (user != null) {
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "LOGIN_SUCCESS (PIN)", actionType = "AUTH"))
            Log.d(TAG_VM, "Login via PIN Berhasil untuk: ${user.name}")
        } else {
            Log.w(TAG_VM, "Login via PIN Gagal: PIN tidak cocok.")
        }
        user
    } catch (e: Exception) { 
        Log.e(TAG_VM, "Error Login PIN: ${e.message}", e)
        null 
    }
}

fun PosViewModel.updatePin(user: UserEntity, newPin: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val hashedPin = hashPin(newPin)
            userDao.updatePin(user.id, hashedPin)
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "UBAH PIN", actionType = "SYSTEM"))
            Log.d(TAG_VM, "PIN berhasil diubah untuk: ${user.name}")
        } catch (e: Exception) { 
            Log.e(TAG_VM, "Error Ubah PIN: ${e.message}", e)
        }
    }
}

// ================================================================
// BIOMETRIC LOGIN & REGISTRATION (MODUL ABSENSI)
// ================================================================

fun PosViewModel.isUserBiometricEnrolled(user: UserEntity): Boolean {
    return user.biometricKey.isNotBlank()
}

fun PosViewModel.registerBiometric(activity: FragmentActivity, user: UserEntity, onResult: (Boolean, String) -> Unit) {
    Log.d(TAG_VM, "Memulai proses pendaftaran biometrik untuk: ${user.name}")
    
    val status = BiometricHelper.checkSensorStatus(activity)
    Log.d(TAG_VM, "Status Sensor: $status")
    
    if (status != "SUPPORTED") {
        val msg = BiometricHelper.getSensorStatusMessage(status)
        Log.e(TAG_VM, "Pendaftaran gagal, sensor tidak support: $msg")
        onResult(false, msg)
        return
    }

    val alias = "ZOAKS_USER_${user.id}_${System.currentTimeMillis()}"
    val keyCreated = BiometricCryptoHelper.createKey(alias)
    
    if (!keyCreated) {
        Log.e(TAG_VM, "Pendaftaran gagal, tidak bisa membuat kunci.")
        onResult(false, "Gagal membuat kunci enkripsi. HP Anda mungkin hanya mendukung Face Unlock Class 2.")
        return
    }

    val cipher = BiometricCryptoHelper.getCipherForEncryption(alias)
    
    if (cipher == null) {
        Log.e(TAG_VM, "Pendaftaran gagal, Cipher null.")
        onResult(false, "Gagal menyiapkan enkripsi.")
        return
    }

    Log.d(TAG_VM, "Menampilkan prompt biometrik untuk pendaftaran...")
    BiometricCryptoHelper.showBiometricPrompt(
        activity = activity,
        title = "Daftarkan Biometrik",
        subtitle = "Tempelkan sidik jari atau wajah untuk ${user.name}",
        negativeButtonText = "Batal",
        cipher = cipher,
        onSuccess = { result ->
            Log.d(TAG_VM, "Prompt pendaftaran sukses, memproses enkripsi...")
            try {
                val encrypted = result.cryptoObject?.cipher?.doFinal("ValidBiometric".toByteArray(StandardCharsets.UTF_8))
                val iv = result.cryptoObject?.cipher?.iv
                
                if (encrypted != null && iv != null) {
                    val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)
                    val keyData = "$alias|$ivString"
                    
                    viewModelScope.launch(Dispatchers.IO) {
                        userDao.updateBiometricKey(user.id, keyData)
                        userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "DAFTAR BIOMETRIK", actionType = "AUTH"))
                        Log.d(TAG_VM, "Kunci biometrik berhasil disimpan ke database.")
                        withContext(Dispatchers.Main) { onResult(true, "Biometrik berhasil didaftarkan!") }
                    }
                } else {
                    Log.e(TAG_VM, "Enkripsi Gagal: encrypted/iv null.")
                    onResult(false, "Gagal mengenkripsi data.")
                }
            } catch (e: Exception) {
                Log.e(TAG_VM, "Error saat enkripsi: ${e.message}", e)
                onResult(false, "Error enkripsi: ${e.message}")
            }
        },
        onError = { error ->
            Log.e(TAG_VM, "Prompt pendaftaran error: $error")
            onResult(false, "Gagal mendaftarkan: $error")
        }
    )
}

fun PosViewModel.loginWithBiometric(activity: FragmentActivity, user: UserEntity, onResult: (Boolean, String) -> Unit) {
    Log.d(TAG_VM, "Memulai login biometrik untuk: ${user.name}")
    
    val status = BiometricHelper.checkSensorStatus(activity)
    if (status != "SUPPORTED") {
        val msg = BiometricHelper.getSensorStatusMessage(status)
        Log.e(TAG_VM, "Login gagal, sensor tidak support: $msg")
        onResult(false, msg)
        return
    }

    if (user.biometricKey.isBlank()) {
        Log.e(TAG_VM, "Login gagal, user belum mendaftar biometrik.")
        onResult(false, "Anda belum mendaftarkan sidik jari/wajah.")
        return
    }

    val parts = user.biometricKey.split("|")
    if (parts.size != 2) {
        Log.e(TAG_VM, "Login gagal, format data biometrik rusak.")
        onResult(false, "Data biometrik rusak.")
        return
    }

    val alias = parts[0]
    val iv = Base64.decode(parts[1], Base64.NO_WRAP)
    val cipher = BiometricCryptoHelper.getCipherForDecryption(alias, iv)

    if (cipher == null) {
        Log.e(TAG_VM, "Login gagal, Cipher dekripsi null.")
        onResult(false, "Kunci biometrik kedaluwarsa. Silakan login dengan PIN dan daftarkan ulang.")
        return
    }

    Log.d(TAG_VM, "Menampilkan prompt biometrik untuk login...")
    BiometricCryptoHelper.showBiometricPrompt(
        activity = activity,
        title = "Login Biometrik",
        subtitle = "Halo ${user.name}, tempelkan sidik jari/wajah Anda",
        negativeButtonText = "Gunakan PIN",
        cipher = cipher,
        onSuccess = { result ->
            Log.d(TAG_VM, "Login biometrik sukses.")
            viewModelScope.launch(Dispatchers.IO) {
                userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "LOGIN_SUCCESS (BIOMETRIC)", actionType = "AUTH"))
                withContext(Dispatchers.Main) { onResult(true, "Login Berhasil") }
            }
        },
        onError = { error ->
            Log.e(TAG_VM, "Prompt login error: $error")
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
                name = name, pin = hashedPin, role = role, baseSalary = baseSalary, branchId = _currentBranchId.value
            ))
            userDao.insertAuditLog(AuditLogEntity(userId = adminUser.id, userName = adminUser.name, action = "TAMBAH KARYAWAN: $name (Gaji: ${formatRupiah(baseSalary)})", actionType = "AUTH"))
            Log.d(TAG_VM, "Karyawan baru ditambahkan: $name")
        } catch (e: Exception) { 
            Log.e(TAG_VM, "Gagal tambah karyawan: ${e.message}", e)
        }
    }
}

fun PosViewModel.updateUser(user: UserEntity, adminUser: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val hashedPin = hashPin(user.pin)
            userDao.updateUser(user.copy(pin = hashedPin))
            userDao.insertAuditLog(AuditLogEntity(userId = adminUser.id, userName = adminUser.name, action = "EDIT KARYAWAN: ${user.name}", actionType = "AUTH"))
            Log.d(TAG_VM, "Karyawan diupdate: ${user.name}")
        } catch (e: Exception) { 
            Log.e(TAG_VM, "Gagal update karyawan: ${e.message}", e)
        }
    }
}

fun PosViewModel.deleteUser(user: UserEntity, adminUser: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            if (user.biometricKey.isNotBlank()) {
                val alias = user.biometricKey.split("|").firstOrNull()
                if (alias != null) BiometricCryptoHelper.deleteKey(alias)
            }
            userDao.deleteUser(user)
            userDao.insertAuditLog(AuditLogEntity(userId = adminUser.id, userName = adminUser.name, action = "HAPUS KARYAWAN: ${user.name}", actionType = "AUTH"))
            Log.d(TAG_VM, "Karyawan dihapus: ${user.name}")
        } catch (e: Exception) { 
            Log.e(TAG_VM, "Gagal hapus karyawan: ${e.message}", e)
        }
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
            customerDao.insertCustomer(CustomerEntity(name = name, phone = phone, birthDate = birthDate)) 
        }
        catch (e: Exception) { 
            Log.e(TAG_VM, "Gagal tambah customer: ${e.message}", e)
        }
    }
}

// ================================================================
// MEMBERSHIP
// ================================================================
fun PosViewModel.addMembership(name: String, price: Long, quota: Int, days: Int) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            membershipDao.insertMembership(MembershipEntity(name = name, price = price, quotaCount = quota, validDays = days))
            Log.d(TAG_VM, "Membership baru ditambahkan: $name")
        } catch (e: Exception) { 
            Log.e(TAG_VM, "Gagal tambah membership: ${e.message}", e)
        }
    }
}

fun PosViewModel.buyMembership(customerId: Long, membership: MembershipEntity, onSuccess: () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val expiryDate = System.currentTimeMillis() + (membership.validDays * 24 * 60 * 60 * 1000L)
            val customerMembership = CustomerMembershipEntity(
                customerId = customerId, membershipId = membership.id, membershipName = membership.name,
                remainingQuota = membership.quotaCount, expiryDate = expiryDate
            )
            membershipDao.insertCustomerMembership(customerMembership)
            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) { 
            Log.e(TAG_VM, "Gagal beli membership: ${e.message}", e)
        }
    }
}

fun PosViewModel.applyMembershipToCart(cartItem: CartItem, customerMembership: CustomerMembershipEntity) {
    val currentCart = _cart.value.toMutableList()
    val idx = currentCart.indexOfFirst { it.product.id == cartItem.product.id && it.selectedVariation == cartItem.selectedVariation }
    if (idx != -1) {
        currentCart[idx] = currentCart[idx].copy(
            usedMembershipId = customerMembership.id, usedMembershipName = customerMembership.membershipName
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
            Log.e(TAG_VM, "Error voucher: ${e.message}", e)
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