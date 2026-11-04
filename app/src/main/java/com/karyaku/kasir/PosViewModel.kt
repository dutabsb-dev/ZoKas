package com.karyaku.kasir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class CartItem(val product: ProductEntity, var quantity: Int = 1)

class PosViewModel(
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao,
    private val userDao: UserDao
) : ViewModel() {

    private val _products = MutableStateFlow<List<ProductEntity>>(emptyList())
    val products: StateFlow<List<ProductEntity>> = _products.asStateFlow()

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val transactions: StateFlow<List<TransactionEntity>> = _transactions.asStateFlow()

    // State untuk Shift Kasir
    private val _activeShift = MutableStateFlow<ShiftEntity?>(null)
    val activeShift: StateFlow<ShiftEntity?> = _activeShift.asStateFlow()

    init {
        viewModelScope.launch {
            if (userDao.getAllUsersOnce().isEmpty()) {
                userDao.insertUser(UserEntity(name = "Owner", pin = "123456", role = "OWNER"))
                userDao.insertUser(UserEntity(name = "Kasir 1", pin = "111111", role = "CASHIER"))
            }
            checkActiveShift()
        }
        viewModelScope.launch {
            productDao.getAllProducts().collectLatest { _products.value = it }
        }
        viewModelScope.launch {
            transactionDao.getAllTransactions().collectLatest { _transactions.value = it }
        }
    }

    fun checkActiveShift() {
        viewModelScope.launch {
            _activeShift.value = userDao.getActiveShift()
        }
    }

    suspend fun login(pin: String): UserEntity? {
        val user = userDao.loginByPin(pin)
        if (user != null) {
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "LOGIN_SUCCESS"))
        }
        return user
    }

    // --- FUNGSI SHIFT KASIR ---
    fun openShift(user: UserEntity, modalAwal: Long) {
        viewModelScope.launch {
            val shift = ShiftEntity(userId = user.id, userName = user.name, openingCash = modalAwal)
            userDao.insertShift(shift)
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "OPEN_SHIFT: Modal Rp $modalAwal"))
            checkActiveShift()
        }
    }

    fun closeShift(user: UserEntity, uangFisik: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val shift = _activeShift.value
            if (shift != null) {
                // Hitung uang sistem: Modal Awal + Total Penjualan Tunai selama shift ini
                // Untuk simplifikasi MVP, kita asumsikan semua transaksi di tabel adalah milik shift ini
                val totalPenjualan = _transactions.value.filter { it.timestamp >= shift.startTime }.sumOf { it.totalPrice }
                val uangSistem = shift.openingCash + totalPenjualan
                val selisih = uangFisik - uangSistem

                userDao.closeShift(shift.id, System.currentTimeMillis(), uangSistem, uangFisik, selisih)
                userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "CLOSE_SHIFT: Fisik Rp $uangFisik, Sistem Rp $uangSistem, Selisih Rp $selisih"))
                
                checkActiveShift()
                onSuccess()
            }
        }
    }

    // --- FUNGSI PRODUK & TRANSAKSI ---
    fun addProduct(name: String, price: Long, stock: Int, userId: Long, userName: String) {
        viewModelScope.launch {
            productDao.insertProduct(ProductEntity(name = name, price = price, stock = stock))
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "ADD_PRODUCT: $name"))
        }
    }

    fun updateProduct(product: ProductEntity, userId: Long, userName: String) {
        viewModelScope.launch {
            productDao.insertProduct(product)
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "EDIT_PRODUCT: ${product.name}"))
        }
    }

    fun deleteProduct(product: ProductEntity, userId: Long, userName: String) {
        viewModelScope.launch {
            productDao.deleteProduct(product)
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "DELETE_PRODUCT: ${product.name}"))
        }
    }

    fun addToCart(product: ProductEntity) {
        val currentCart = _cart.value.toMutableList()
        val idx = currentCart.indexOfFirst { it.product.id == product.id }
        if (idx != -1) {
            val item = currentCart[idx]
            if (item.quantity < item.product.stock) currentCart[idx] = item.copy(quantity = item.quantity + 1)
        } else {
            if (product.stock > 0) currentCart.add(CartItem(product))
        }
        _cart.value = currentCart
    }

    fun removeFromCart(product: ProductEntity) {
        val currentCart = _cart.value.toMutableList()
        val idx = currentCart.indexOfFirst { it.product.id == product.id }
        if (idx != -1) {
            val item = currentCart[idx]
            if (item.quantity > 1) currentCart[idx] = item.copy(quantity = item.quantity - 1)
            else currentCart.removeAt(idx)
            _cart.value = currentCart
        }
    }

    fun deleteItem(product: ProductEntity) {
        _cart.value = _cart.value.toMutableList().also { it.removeAll { item -> item.product.id == product.id } }
    }

    fun getTotalPrice(): Long = _cart.value.sumOf { it.product.price * it.quantity }

    fun processPayment(cashReceived: Long, userId: Long, userName: String, onSuccess: (Long) -> Unit) {
        val total = getTotalPrice()
        val change = if (cashReceived >= total) cashReceived - total else 0
        val transaction = TransactionEntity(totalPrice = total, cashReceived = cashReceived, changeAmount = change)

        viewModelScope.launch {
            _cart.value.forEach { item ->
                val sisa = (item.product.stock - item.quantity).coerceAtLeast(0)
                productDao.updateStock(item.product.id, sisa)
            }
            val newId = transactionDao.insertTransaction(transaction)
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "CHECKOUT: Rp $total"))
            _cart.value = emptyList()
            onSuccess(newId)
        }
    }
}

class PosViewModelFactory(
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao,
    private val userDao: UserDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PosViewModel(productDao, transactionDao, userDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}