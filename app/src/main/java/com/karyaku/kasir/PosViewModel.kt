package com.karyaku.kasir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CartItem(val product: ProductEntity, var quantity: Int = 1)

class PosViewModel(
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao,
    private val userDao: UserDao,
    private val cashFlowDao: CashFlowDao,
    private val customerDao: CustomerDao,
    private val transactionItemDao: TransactionItemDao
) : ViewModel() {

    private val _products = MutableStateFlow<List<ProductEntity>>(emptyList())
    val products: StateFlow<List<ProductEntity>> = _products.asStateFlow()

    private val _bestSellers = MutableStateFlow<List<BestSellerData>>(emptyList())
    val bestSellers: StateFlow<List<BestSellerData>> = _bestSellers.asStateFlow()

    private val _customers = MutableStateFlow<List<CustomerEntity>>(emptyList())
    val customers: StateFlow<List<CustomerEntity>> = _customers.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Semua")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _discount = MutableStateFlow(0L)
    val discount: StateFlow<Long> = _discount.asStateFlow()

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _selectedCustomer = MutableStateFlow<CustomerEntity?>(null)
    val selectedCustomer: StateFlow<CustomerEntity?> = _selectedCustomer.asStateFlow()

    private val _pointsUsed = MutableStateFlow(0)
    val pointsUsed: StateFlow<Int> = _pointsUsed.asStateFlow()

    private val _transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val transactions: StateFlow<List<TransactionEntity>> = _transactions.asStateFlow()

    private val _activeShift = MutableStateFlow<ShiftEntity?>(null)
    val activeShift: StateFlow<ShiftEntity?> = _activeShift.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLogEntity>>(emptyList())
    val auditLogs: StateFlow<List<AuditLogEntity>> = _auditLogs.asStateFlow()

    val uniqueCategories: StateFlow<List<String>> = _products.map { productList ->
        val categories = productList.map { it.category }.distinct()
        listOf("Semua") + categories
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Semua"))

    val filteredProducts: StateFlow<List<ProductEntity>> = combine(
        _products, _searchQuery, _selectedCategory
    ) { products, query, category ->
        products.filter { product ->
            val matchQuery = query.isBlank() || product.name.contains(query, ignoreCase = true)
            val matchCategory = category == "Semua" || product.category == category
            matchQuery && matchCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        viewModelScope.launch { transactionItemDao.getBestSellers().collectLatest { _bestSellers.value = it } }
    }

    fun getLowStockProducts(): List<ProductEntity> {
        return _products.value.filter { it.stock <= 5 && it.stock >= 0 }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setCategory(category: String) { _selectedCategory.value = category }
    fun setDiscount(amount: Long) { _discount.value = amount }
    fun setCustomer(customer: CustomerEntity?) { 
        _selectedCustomer.value = customer 
        _pointsUsed.value = 0
        _discount.value = 0   
    }
    fun redeemPoints(pointsToRedeem: Int) {
        val customer = _selectedCustomer.value ?: return
        if (pointsToRedeem > 0 && pointsToRedeem <= customer.points) {
            _pointsUsed.value = pointsToRedeem
            _discount.value = (pointsToRedeem * 1000).toLong()
        }
    }
    fun addCustomer(name: String, phone: String) {
        viewModelScope.launch {
            customerDao.insertCustomer(CustomerEntity(name = name, phone = phone))
        }
    }

    fun getTransactionItems(transactionId: Long): StateFlow<List<TransactionItemEntity>> {
        val itemsFlow = MutableStateFlow<List<TransactionItemEntity>>(emptyList())
        viewModelScope.launch {
            transactionItemDao.getItemsByTransaction(transactionId).collectLatest {
                itemsFlow.value = it
            }
        }
        return itemsFlow.asStateFlow()
    }

    fun checkActiveShift() {
        viewModelScope.launch { _activeShift.value = userDao.getActiveShift() }
    }

    suspend fun login(pin: String): UserEntity? {
        val user = userDao.loginByPin(pin)
        if (user != null) userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "LOGIN_SUCCESS"))
        return user
    }

    fun updatePin(user: UserEntity, newPin: String) {
        viewModelScope.launch {
            userDao.updatePin(user.id, newPin)
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "UBAH PIN: PIN berhasil diubah"))
        }
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
                val totalPenjualan = _transactions.value.filter { it.timestamp >= shift.startTime && it.status == "SUCCESS" }.sumOf { it.totalPrice }
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

    fun addProduct(name: String, price: Long, stock: Int, category: String, costPrice: Long, userId: Long, userName: String) {
        viewModelScope.launch {
            productDao.insertProduct(ProductEntity(name = name, price = price, stock = stock, category = category, costPrice = costPrice))
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "TAMBAH PRODUK: $name ($category)"))
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
        val customer = _selectedCustomer.value
        val customerName = customer?.name ?: "Pelanggan Umum"
        
        // HITUNG TOTAL MODAL (HPP) UNTUK DISIMPAN DI TRANSAKSI
        val totalCost = _cart.value.sumOf { it.product.costPrice * it.quantity }

        val transaction = TransactionEntity(
            totalPrice = total, 
            cashReceived = cashReceived, 
            changeAmount = change, 
            customerName = customerName,
            totalCost = totalCost // <--- SIMPAN HPP
        )

        viewModelScope.launch {
            val newId = transactionDao.insertTransaction(transaction)
            val itemEntities = _cart.value.map { cartItem ->
                TransactionItemEntity(transactionId = newId, productName = cartItem.product.name, price = cartItem.product.price, quantity = cartItem.quantity, subtotal = cartItem.product.price * cartItem.quantity, costPrice = cartItem.product.costPrice)
            }
            transactionItemDao.insertTransactionItems(itemEntities)

            _cart.value.forEach { item ->
                val sisa = (item.product.stock - item.quantity).coerceAtLeast(0)
                productDao.updateStock(item.product.id, sisa)
            }
            
            if (customer != null) {
                val earnedPoints = (total / 10000).toInt()
                val finalPoints = (customer.points - _pointsUsed.value + earnedPoints).coerceAtLeast(0)
                customerDao.updateCustomerPoints(customer.id, finalPoints)
            }
            
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "TRANSAKSI: ${formatRupiah(total)} ($customerName)"))
            
            _cart.value = emptyList()
            _discount.value = 0
            _pointsUsed.value = 0
            _selectedCustomer.value = null
            onSuccess(newId)
        }
    }

    fun voidTransaction(transaction: TransactionEntity, pinSupervisor: String, currentUser: UserEntity, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val supervisor = userDao.loginByPin(pinSupervisor)
            if (supervisor == null || (supervisor.role != "OWNER" && supervisor.role != "SUPERVISOR")) {
                onResult(false, "PIN Salah atau bukan Supervisor!")
                return@launch
            }

            val itemsToRestore = transactionItemDao.getItemsByTransaction(transaction.id).first()
            
            itemsToRestore.forEach { item ->
                val product = _products.value.find { it.name == item.productName }
                if (product != null) {
                    productDao.updateStock(product.id, product.stock + item.quantity)
                }
            }

            transactionDao.voidTransaction(transaction.id)
            userDao.insertAuditLog(
                AuditLogEntity(
                    userId = currentUser.id, 
                    userName = currentUser.name, 
                    action = "VOID TRANSAKSI #${transaction.id} (Oleh Supervisor: ${supervisor.name})"
                )
            )
            onResult(true, "Transaksi berhasil di-void dan stok dikembalikan.")
        }
    }
}

class PosViewModelFactory(
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao,
    private val userDao: UserDao,
    private val cashFlowDao: CashFlowDao,
    private val customerDao: CustomerDao,
    private val transactionItemDao: TransactionItemDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PosViewModel(productDao, transactionDao, userDao, cashFlowDao, customerDao, transactionItemDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}