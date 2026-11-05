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
    private val cashFlowDao: CashFlowDao,
    private val customerDao: CustomerDao // Parameter Baru
) : ViewModel() {

    private val _products = MutableStateFlow<List<ProductEntity>>(emptyList())
    val products: StateFlow<List<ProductEntity>> = _products.asStateFlow()

    private val _customers = MutableStateFlow<List<CustomerEntity>>(emptyList())
    val customers: StateFlow<List<CustomerEntity>> = _customers.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _discount = MutableStateFlow(0L)
    val discount: StateFlow<Long> = _discount.asStateFlow()

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    // State untuk Pelanggan yang dipilih
    private val _selectedCustomer = MutableStateFlow<CustomerEntity?>(null)
    val selectedCustomer: StateFlow<CustomerEntity?> = _selectedCustomer.asStateFlow()

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
        viewModelScope.launch { customerDao.getAllCustomers().collectLatest { _customers.value = it } }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setDiscount(amount: Long) { _discount.value = amount }
    
    // Fungsi Pelanggan
    fun setCustomer(customer: CustomerEntity?) { _selectedCustomer.value = customer }
    fun addCustomer(name: String, phone: String) {
        viewModelScope.launch {
            customerDao.insertCustomer(CustomerEntity(name = name, phone = phone))
        }
    }

    fun getFilteredProducts(): List<ProductEntity> {
        val query = _searchQuery.value
        return if (query.isBlank()) _products.value 
        else _products.value.filter { it.name.contains(query, ignoreCase = true) }
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

    fun addCashFlow(type: String, amount: Long, note: String, user: UserEntity) {
        viewModelScope.launch {
            val shift = _activeShift.value ?: return@launch
            val cashFlow = CashFlowEntity(shiftId = shift.id, type = type, amount = amount, note = note, userId = user.id, userName = user.name)
            cashFlowDao.insertCashFlow(cashFlow)
            val actionText = if (type == "OUT") "KAS KELUAR: $note (${formatRupiah(amount)})" else "KAS MASUK: $note (${formatRupiah(amount)})"
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = actionText))
        }
    }

    fun closeShift(user: UserEntity, uangFisik: Long, onSuccess: (Long) -> Unit) {
        viewModelScope.launch {
            val shift = _activeShift.value
            if (shift != null) {
                val totalPenjualan = _transactions.value.filter { it.timestamp >= shift.startTime }.sumOf { it.totalPrice }
                val totalKasKeluar = cashFlowDao.getTotalCashOut(shift.id) ?: 0L
                val totalKasMasuk = cashFlowDao.getTotalCashIn(shift.id) ?: 0L
                val uangSistem = shift.openingCash + totalPenjualan + totalKasMasuk - totalKasKeluar
                val selisih = uangFisik - uangSistem

                userDao.closeShift(shift.id, System.currentTimeMillis(), uangSistem, uangFisik, selisih)
                userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "TUTUP KASIR: Fisik ${formatRupiah(uangFisik)}, Sistem ${formatRupiah(uangSistem)}, Selisih ${formatRupiah(selisih)}"))
                
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

    fun getTotalPrice(): Long {
        val subtotal = _cart.value.sumOf { it.product.price * it.quantity }
        return (subtotal - _discount.value).coerceAtLeast(0)
    }

    fun processPayment(cashReceived: Long, userId: Long, userName: String, onSuccess: (Long) -> Unit) {
        val total = getTotalPrice()
        val change = if (cashReceived >= total) cashReceived - total else 0
        val customerName = _selectedCustomer.value?.name ?: "Pelanggan Umum"
        
        val transaction = TransactionEntity(
            totalPrice = total, 
            cashReceived = cashReceived, 
            changeAmount = change,
            customerName = customerName
        )

        viewModelScope.launch {
            _cart.value.forEach { item ->
                val sisa = (item.product.stock - item.quantity).coerceAtLeast(0)
                productDao.updateStock(item.product.id, sisa)
            }
            val newId = transactionDao.insertTransaction(transaction)
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "TRANSAKSI: ${formatRupiah(total)} ($customerName)"))
            
            _cart.value = emptyList()
            _discount.value = 0
            _selectedCustomer.value = null // Reset pelanggan
            onSuccess(newId)
        }
    }
}

class PosViewModelFactory(
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao,
    private val userDao: UserDao,
    private val cashFlowDao: CashFlowDao,
    private val customerDao: CustomerDao // Parameter Baru
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PosViewModel(productDao, transactionDao, userDao, cashFlowDao, customerDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}