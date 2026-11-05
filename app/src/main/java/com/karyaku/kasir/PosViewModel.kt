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
    private val userDao: UserDao,
    private val cashFlowDao: CashFlowDao // Parameter Baru
) : ViewModel() {

    private val _products = MutableStateFlow<List<ProductEntity>>(emptyList())
    val products: StateFlow<List<ProductEntity>> = _products.asStateFlow()

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val transactions: StateFlow<List<TransactionEntity>> = _transactions.asStateFlow()

    private val _activeShift = MutableStateFlow<ShiftEntity?>(null)
    val activeShift: StateFlow<ShiftEntity?> = _activeShift.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLogEntity>>(emptyList())
    val auditLogs: StateFlow<List<AuditLogEntity>> = _auditLogs.asStateFlow()

    init {
        viewModelScope.launch {
            if (userDao.getAllUsersOnce().isEmpty()) {
                userDao.insertUser(UserEntity(name = "Owner", pin = "123456", role = "OWNER"))
                userDao.insertUser(UserEntity(name = "Kasir 1", pin = "111111", role = "CASHIER"))
            }
            checkActiveShift()
        }
        viewModelScope.launch { productDao.getAllProducts().collectLatest { _products.value = it } }
        viewModelScope.launch { transactionDao.getAllTransactions().collectLatest { _transactions.value = it } }
        viewModelScope.launch { userDao.getAllAuditLogs().collectLatest { _auditLogs.value = it } }
    }

    fun checkActiveShift() {
        viewModelScope.launch { _activeShift.value = userDao.getActiveShift() }
    }

    suspend fun login(pin: String): UserEntity? {
        val user = userDao.loginByPin(pin)
        if (user != null) userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "LOGIN_SUCCESS"))
        return user
    }

    fun openShift(user: UserEntity, modalAwal: Long) {
        viewModelScope.launch {
            val shift = ShiftEntity(userId = user.id, userName = user.name, openingCash = modalAwal)
            userDao.insertShift(shift)
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "BUKA KASIR: Modal ${formatRupiah(modalAwal)}"))
            checkActiveShift()
        }
    }

    // FUNGSI BARU: Catat Kas Keluar/Masuk
    fun addCashFlow(type: String, amount: Long, note: String, user: UserEntity) {
        viewModelScope.launch {
            val shift = _activeShift.value ?: return@launch
            val cashFlow = CashFlowEntity(
                shiftId = shift.id,
                type = type,
                amount = amount,
                note = note,
                userId = user.id,
                userName = user.name
            )
            cashFlowDao.insertCashFlow(cashFlow)
            val actionText = if (type == "OUT") "KAS KELUAR: $note (${formatRupiah(amount)})" else "KAS MASUK: $note (${formatRupiah(amount)})"
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = actionText))
        }
    }

    // LOGIKA TUTUP KASIR YANG DIPERBARUI (Menghitung Kas Keluar/Masuk)
    fun closeShift(user: UserEntity, uangFisik: Long, onSuccess: (Long) -> Unit) {
        viewModelScope.launch {
            val shift = _activeShift.value
            if (shift != null) {
                // 1. Total Penjualan Tunai
                val totalPenjualan = _transactions.value.filter { it.timestamp >= shift.startTime }.sumOf { it.totalPrice }
                
                // 2. Total Kas Keluar (Pengeluaran)
                val totalKasKeluar = cashFlowDao.getTotalCashOut(shift.id) ?: 0L
                
                // 3. Total Kas Masuk Tambahan (Misal: tambah modal tengah shift)
                val totalKasMasuk = cashFlowDao.getTotalCashIn(shift.id) ?: 0L

                // 4. Uang Sistem = Modal Awal + Penjualan + Kas Masuk - Kas Keluar
                val uangSistem = shift.openingCash + totalPenjualan + totalKasMasuk - totalKasKeluar
                
                // 5. Selisih
                val selisih = uangFisik - uangSistem

                userDao.closeShift(shift.id, System.currentTimeMillis(), uangSistem, uangFisik, selisih)
                userDao.insertAuditLog(AuditLogEntity(
                    userId = user.id, 
                    userName = user.name, 
                    action = "TUTUP KASIR: Fisik ${formatRupiah(uangFisik)}, Sistem ${formatRupiah(uangSistem)}, Selisih ${formatRupiah(selisih)}"
                ))
                
                checkActiveShift()
                onSuccess(selisih)
            }
        }
    }

    fun addProduct(name: String, price: Long, stock: Int, userId: Long, userName: String) {
        viewModelScope.launch {
            productDao.insertProduct(ProductEntity(name = name, price = price, stock = stock))
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "TAMBAH PRODUK: $name"))
        }
    }

    fun updateProduct(product: ProductEntity, userId: Long, userName: String) {
        viewModelScope.launch {
            productDao.insertProduct(product)
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "EDIT PRODUK: ${product.name}"))
        }
    }

    fun deleteProduct(product: ProductEntity, userId: Long, userName: String) {
        viewModelScope.launch {
            productDao.deleteProduct(product)
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "HAPUS PRODUK: ${product.name}"))
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
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "TRANSAKSI: ${formatRupiah(total)}"))
            _cart.value = emptyList()
            onSuccess(newId)
        }
    }
}

class PosViewModelFactory(
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao,
    private val userDao: UserDao,
    private val cashFlowDao: CashFlowDao // Parameter Baru
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PosViewModel(productDao, transactionDao, userDao, cashFlowDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}