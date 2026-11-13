package com.karyaku.kasir

import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ====================================================================
// FILE 5 — USER, AUTH, CUSTOMER, MEMBERSHIP, VOUCHER, FILTER
// ====================================================================
// Extension functions untuk PosViewModel.
// ====================================================================

// ================================================================
// AUTH
// ================================================================
suspend fun PosViewModel.login(pin: String): UserEntity? {
    return try {
        val hashedPin = hashPin(pin)
        val user = userDao.loginByPin(hashedPin)
        if (user != null) {
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "LOGIN_SUCCESS", actionType = "AUTH"))
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
// USER CRUD
// ================================================================
fun PosViewModel.addUser(name: String, pin: String, role: String, adminUser: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val hashedPin = hashPin(pin)
            userDao.insertUser(UserEntity(
                name = name, 
                pin = hashedPin, 
                role = role, 
                branchId = _currentBranchId.value
            ))
            userDao.insertAuditLog(AuditLogEntity(userId = adminUser.id, userName = adminUser.name, action = "TAMBAH KARYAWAN: $name (Cabang aktif)", actionType = "AUTH"))
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

// MODUL G-2a: addCustomer sekarang menerima birthDate (default 0 = tidak diisi).
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