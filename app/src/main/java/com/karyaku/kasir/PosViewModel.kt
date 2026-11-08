package com.karyaku.kasir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class CartItem(
    val product: ProductEntity, 
    var quantity: Int = 1, 
    var isSelectedForSplit: Boolean = false,
    var notes: String = "",
    var selectedVariation: String = "" // FITUR BARU: Varian terpilih
)

class PosViewModel(
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao,
    private val userDao: UserDao,
    private val cashFlowDao: CashFlowDao,
    private val customerDao: CustomerDao,
    private val transactionItemDao: TransactionItemDao,
    private val voucherDao: VoucherDao,
    private val holdOrderDao: HoldOrderDao
) : ViewModel() {

    private val _products = MutableStateFlow<List<ProductEntity>>(emptyList())
    val products: StateFlow<List<ProductEntity>> = _products.asStateFlow()

    val inventoryValue: StateFlow<Long> = _products.map { products ->
        products.sumOf { it.stock * it.costPrice }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _bestSellers = MutableStateFlow<List<BestSellerData>>(emptyList())
    val bestSellers: StateFlow<List<BestSellerData>> = _bestSellers.asStateFlow()

    private val _customers = MutableStateFlow<List<CustomerEntity>>(emptyList())
    val customers: StateFlow<List<CustomerEntity>> = _customers.asStateFlow()

    private val _users = MutableStateFlow<List<UserEntity>>(emptyList())
    val users: StateFlow<List<UserEntity>> = _users.asStateFlow()

    private val _holdOrders = MutableStateFlow<List<HoldOrderEntity>>(emptyList())
    val holdOrders: StateFlow<List<HoldOrderEntity>> = _holdOrders.asStateFlow()

    private val _shifts = MutableStateFlow<List<ShiftEntity>>(emptyList())
    val shifts: StateFlow<List<ShiftEntity>> = _shifts.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Semua")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _discount = MutableStateFlow(0L)
    val discount: StateFlow<Long> = _discount.asStateFlow()

    private val _voucherDiscount = MutableStateFlow(0L)
    val voucherDiscount: StateFlow<Long> = _voucherDiscount.asStateFlow()

    private val _appliedVoucher = MutableStateFlow("")
    val appliedVoucher: StateFlow<String> = _appliedVoucher.asStateFlow()

    private val _selectedTable = MutableStateFlow("Take-Away")
    val selectedTable: StateFlow<String> = _selectedTable.asStateFlow()

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

    val netProfit: StateFlow<Long> = combine<List<TransactionEntity>, List<CashFlowEntity>, Long>(
        _transactions, 
        cashFlowDao.getAllCashFlows()
    ) { txList, cashFlows ->
        val labaKotor = txList.filter { it.status == "SUCCESS" }.sumOf { it.totalPrice - it.totalCost }
        val totalKasKeluar = cashFlows.filter { it.type == "OUT" }.sumOf { it.amount }
        labaKotor - totalKasKeluar
    }.catch { emit(0L) }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _taxPercent = MutableStateFlow(0.0)
    val taxPercent: StateFlow<Double> = _taxPercent.asStateFlow()

    val uniqueCategories: StateFlow<List<String>> = _products.map { productList ->
        val categories = productList.map { it.category }.distinct()
        listOf("Semua") + categories
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Semua"))

    val filteredProducts: StateFlow<List<ProductEntity>> = combine(
        _products, _searchQuery, _selectedCategory
    ) { products, query, category ->
        products.filter { product ->
            val matchQuery = query.isBlank() || product.name.contains(query, ignoreCase = true) || product.barcode.contains(query, ignoreCase = true)
            val matchCategory = category == "Semua" || product.category == category
            matchQuery && matchCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val occupiedTables: StateFlow<List<String>> = combine(_holdOrders, _transactions) { holds, txs ->
        val holdTables = holds.map { it.tableName }
        val unpaidTables = txs.filter { it.status == "UNPAID" }.map { it.tableName }
        (holdTables + unpaidTables).distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            if (userDao.getAllUsersOnce().isEmpty()) {
                userDao.insertUser(UserEntity(name = "Owner", pin = "123456", role = "OWNER"))
                userDao.insertUser(UserEntity(name = "Kasir 1", pin = "111111", role = "CASHIER"))
            }
            if (voucherDao.getVoucherByCode("PROMO10") == null) {
                voucherDao.insertVoucher(VoucherEntity("PROMO10", 10000))
            }
            checkActiveShift()
        }
        viewModelScope.launch { productDao.getAllProducts().collectLatest { _products.value = it } }
        viewModelScope.launch { transactionDao.getAllTransactions().collectLatest { _transactions.value = it } }
        viewModelScope.launch { userDao.getAllAuditLogs().collectLatest { _auditLogs.value = it } }
        viewModelScope.launch { customerDao.getAllCustomers().collectLatest { _customers.value = it } }
        viewModelScope.launch { transactionItemDao.getBestSellers().collectLatest { _bestSellers.value = it } }
        viewModelScope.launch { userDao.getAllUsers().collectLatest { _users.value = it } }
        viewModelScope.launch { holdOrderDao.getAllHoldOrders().collectLatest { _holdOrders.value = it } }
        viewModelScope.launch { userDao.getAllShifts().collectLatest { _shifts.value = it } }
    }

    fun setTaxPercent(tax: Double) { _taxPercent.value = tax }
    fun setTable(table: String) { _selectedTable.value = table }

    fun processBarcodeScan(barcode: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val product = productDao.getProductByBarcode(barcode)
            if (product != null) {
                if (product.stock > 0) {
                    addToCart(product)
                    onResult(true, "Berhasil tambah: ${product.name}")
                } else {
                    onResult(false, "Stok ${product.name} habis!")
                }
            } else {
                onResult(false, "Barcode tidak ditemukan!")
            }
        }
    }

    fun updateCartItemNotes(product: ProductEntity, notes: String) {
        val currentCart = _cart.value.toMutableList()
        val idx = currentCart.indexOfFirst { it.product.id == product.id }
        if (idx != -1) {
            currentCart[idx] = currentCart[idx].copy(notes = notes)
            _cart.value = currentCart
        }
    }

    fun updateCartItemVariation(product: ProductEntity, variation: String) {
        val currentCart = _cart.value.toMutableList()
        val idx = currentCart.indexOfFirst { it.product.id == product.id }
        if (idx != -1) {
            currentCart[idx] = currentCart[idx].copy(selectedVariation = variation)
            _cart.value = currentCart
        }
    }

    fun exportProductsToCsv(): String {
        val header = "Nama,HargaJual,HargaModal,Stok,Kategori,Barcode,Varian,Komisi,Expiry\n"
        val rows = _products.value.joinToString("\n") { 
            "${it.name},${it.price},${it.costPrice},${it.stock},${it.category},${it.barcode},${it.variations},${it.commissionPerItem},${it.expiryDate}" 
        }
        return header + rows
    }

    fun importProductsFromCsv(csvText: String, onResult: (Int, Int) -> Unit) {
        viewModelScope.launch {
            var insertCount = 0
            var updateCount = 0
            val lines = csvText.split("\n")
            for (i in 1 until lines.size) { 
                val parts = lines[i].split(",")
                if (parts.size >= 5) {
                    try {
                        val name = parts[0].trim()
                        val price = parts[1].trim().toLongOrNull() ?: 0L
                        val costPrice = parts[2].trim().toLongOrNull() ?: 0L
                        val stock = parts[3].trim().toIntOrNull() ?: 0
                        val category = parts[4].trim()
                        val barcode = if (parts.size > 5) parts[5].trim() else ""
                        val variations = if (parts.size > 6) parts[6].trim() else ""
                        val commission = if (parts.size > 7) parts[7].trim().toLongOrNull() ?: 0L else 0L
                        val expiry = if (parts.size > 8) parts[8].trim().toLongOrNull() ?: 0L else 0L

                        val existingProduct = _products.value.find { 
                            (barcode.isNotEmpty() && it.barcode == barcode) || it.name.equals(name, ignoreCase = true)
                        }

                        if (existingProduct != null) {
                            productDao.insertProduct(existingProduct.copy(
                                name = name, price = price, costPrice = costPrice, stock = stock, 
                                category = category, barcode = barcode, variations = variations, commissionPerItem = commission, expiryDate = expiry
                            ))
                            updateCount++
                        } else {
                            productDao.insertProduct(ProductEntity(
                                name = name, price = price, costPrice = costPrice, stock = stock, 
                                category = category, barcode = barcode, variations = variations, commissionPerItem = commission, expiryDate = expiry
                            ))
                            insertCount++
                        }
                    } catch (e: Exception) {}
                }
            }
            onResult(insertCount, updateCount)
        }
    }

    fun recordWaste(product: ProductEntity, qty: Int, user: UserEntity) {
        viewModelScope.launch {
            val newStock = (product.stock - qty).coerceAtLeast(0)
            productDao.updateStock(product.id, newStock)
            val loss = qty * product.costPrice
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "WASTE: ${product.name} -$qty pcs. Rugi: ${formatRupiah(loss)}"))
        }
    }

    fun logCashDrawerOpen(user: UserEntity) {
        viewModelScope.launch {
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "BUKA LACI MANUAL (No Sale)"))
        }
    }

    fun saveHoldOrder(customerName: String) {
        if (_cart.value.isEmpty()) return
        viewModelScope.launch {
            val jsonArray = JSONArray()
            _cart.value.forEach { item ->
                val jsonObj = JSONObject()
                jsonObj.put("productId", item.product.id)
                jsonObj.put("quantity", item.quantity)
                jsonObj.put("variation", item.selectedVariation)
                jsonObj.put("notes", item.notes)
                jsonArray.put(jsonObj)
            }
            val holdOrder = HoldOrderEntity(tableName = _selectedTable.value, customerName = customerName, cartJson = jsonArray.toString())
            holdOrderDao.insertHoldOrder(holdOrder)
            _cart.value = emptyList()
            _selectedTable.value = "Take-Away"
        }
    }

    fun resumeHoldOrder(order: HoldOrderEntity) {
        viewModelScope.launch {
            val newCart = mutableListOf<CartItem>()
            val jsonArray = JSONArray(order.cartJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val productId = obj.getLong("productId")
                val qty = obj.getInt("quantity")
                val variation = obj.optString("variation", "")
                val notes = obj.optString("notes", "")
                val product = _products.value.find { it.id == productId }
                if (product != null) newCart.add(CartItem(product, qty, selectedVariation = variation, notes = notes))
            }
            _cart.value = newCart
            _selectedTable.value = order.tableName
            holdOrderDao.deleteHoldOrder(order.id)
        }
    }

    fun applyVoucher(code: String, onResult: (Boolean, String) -> Unit) {
        if (code.isBlank()) {
            _voucherDiscount.value = 0
            _appliedVoucher.value = ""
            return
        }
        viewModelScope.launch {
            val voucher = voucherDao.getVoucherByCode(code.uppercase())
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
    }

    fun addStock(product: ProductEntity, qtyToAdd: Int, user: UserEntity) {
        viewModelScope.launch {
            val newStock = product.stock + qtyToAdd
            productDao.updateStock(product.id, newStock)
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "RESTOCK: ${product.name} +$qtyToAdd"))
        }
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
        viewModelScope.launch { customerDao.insertCustomer(CustomerEntity(name = name, phone = phone)) }
    }

    fun getTransactionItems(transactionId: Long): Flow<List<TransactionItemEntity>> {
        return transactionItemDao.getItemsByTransaction(transactionId)
    }

    fun checkActiveShift() { viewModelScope.launch { _activeShift.value = userDao.getActiveShift() } }

    suspend fun login(pin: String): UserEntity? {
        val user = userDao.loginByPin(pin)
        if (user != null) userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "LOGIN_SUCCESS"))
        return user
    }

    fun updatePin(user: UserEntity, newPin: String) {
        viewModelScope.launch {
            userDao.updatePin(user.id, newPin)
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "UBAH PIN"))
        }
    }

    fun addUser(name: String, pin: String, role: String, adminUser: UserEntity) {
        viewModelScope.launch {
            userDao.insertUser(UserEntity(name = name, pin = pin, role = role))
            userDao.insertAuditLog(AuditLogEntity(userId = adminUser.id, userName = adminUser.name, action = "TAMBAH KARYAWAN: $name"))
        }
    }

    fun updateUser(user: UserEntity, adminUser: UserEntity) {
        viewModelScope.launch {
            userDao.updateUser(user)
            userDao.insertAuditLog(AuditLogEntity(userId = adminUser.id, userName = adminUser.name, action = "EDIT KARYAWAN: ${user.name}"))
        }
    }

    fun deleteUser(user: UserEntity, adminUser: UserEntity) {
        viewModelScope.launch {
            userDao.deleteUser(user)
            userDao.insertAuditLog(AuditLogEntity(userId = adminUser.id, userName = adminUser.name, action = "HAPUS KARYAWAN: ${user.name}"))
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

    fun closeShift(user: UserEntity, uangFisik: Long, onSuccess: (ShiftEntity) -> Unit) {
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
                onSuccess(shift.copy(endTime = System.currentTimeMillis(), expectedCash = uangSistem, closingCash = uangFisik, discrepancy = selisih, status = "CLOSED"))
            }
        }
    }

    fun addProduct(name: String, price: Long, stock: Int, category: String, costPrice: Long, barcode: String, variations: String, commission: Long, expiry: Long, userId: Long, userName: String) {
        viewModelScope.launch {
            productDao.insertProduct(ProductEntity(
                name = name, price = price, stock = stock, category = category, costPrice = costPrice, 
                barcode = barcode, variations = variations, commissionPerItem = commission, expiryDate = expiry
            ))
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

    fun toggleSplitSelection(product: ProductEntity) {
        val currentCart = _cart.value.toMutableList()
        val idx = currentCart.indexOfFirst { it.product.id == product.id }
        if (idx != -1) {
            val item = currentCart[idx]
            currentCart[idx] = item.copy(isSelectedForSplit = !item.isSelectedForSplit)
            _cart.value = currentCart
        }
    }

    // FITUR BARU: Perhitungan Total Harga + Tiering + Happy Hour + Promo
    fun getTotalPrice(): Long {
        val subtotal = _cart.value.sumOf { it.product.price * it.quantity }
        val tax = (subtotal - _discount.value - _voucherDiscount.value) * (_taxPercent.value / 100.0)
        return (subtotal + tax - _discount.value - _voucherDiscount.value).coerceAtLeast(0.0).toLong()
    }

    fun processPayment(
        cashReceived: Long,
        userId: Long,
        userName: String,
        isSplitBill: Boolean = false,
        method: String = "CASH",
        onSuccess: (TransactionEntity) -> Unit
    ) {
        val itemsToProcess = if (isSplitBill) _cart.value.filter { it.isSelectedForSplit } else _cart.value
        if (itemsToProcess.isEmpty()) return

        var subtotal = 0L
        var totalCommission = 0L
        var promoDiscount = 0L

        // Hitung Promo & Komisi per Item
        itemsToProcess.forEach { item ->
            val itemSubtotal = item.product.price * item.quantity
            subtotal += itemSubtotal
            totalCommission += (item.product.commissionPerItem * item.quantity)

            // LOGIKA PROMO ENGINE
            if (item.product.promoType == "BOGO" && item.quantity >= 2) {
                val freeItems = item.quantity / 2
                promoDiscount += freeItems * item.product.price
            } else if (item.product.promoType == "TIERED" && item.quantity >= item.product.promoMinQty) {
                val discAmount = (itemSubtotal * item.product.promoValue) / 100
                promoDiscount += discAmount
            }
        }

        // Customer Tiering
        var tierDiscount = 0L
        val customer = _selectedCustomer.value
        if (customer != null) {
            if (customer.points >= 1000) tierDiscount = (subtotal * 0.10).toLong()
            else if (customer.points >= 500) tierDiscount = (subtotal * 0.05).toLong()
            else if (customer.points >= 100) tierDiscount = (subtotal * 0.02).toLong()
        }

        val currentDiscount = if (isSplitBill) promoDiscount else (_discount.value + _voucherDiscount.value + tierDiscount + promoDiscount)
        val taxAmount = (subtotal - currentDiscount) * (_taxPercent.value / 100.0)
        val total = (subtotal + taxAmount - currentDiscount).coerceAtLeast(0.0).toLong()

        val status = if (method == "BON") "UNPAID" else "SUCCESS"
        val change = if (method == "BON") 0L else if (cashReceived >= total) cashReceived - total else 0L
        val customerName = customer?.name ?: "Pelanggan Umum"
        val totalCost = itemsToProcess.sumOf { it.product.costPrice * it.quantity }

        val transaction = TransactionEntity(
            totalPrice = total,
            cashReceived = if (method == "BON") 0L else cashReceived,
            changeAmount = change,
            customerName = customerName,
            totalCost = totalCost,
            tableName = _selectedTable.value,
            voucherCode = if (isSplitBill) "" else _appliedVoucher.value,
            taxAmount = taxAmount.toLong(),
            paymentMethod = method,
            status = status,
            cashierName = userName,
            totalCommission = totalCommission // Simpan komisi
        )

        viewModelScope.launch {
            val newId = transactionDao.insertTransaction(transaction)
            val completedTransaction = transaction.copy(id = newId)

            val itemEntities = itemsToProcess.map { cartItem ->
                TransactionItemEntity(
                    transactionId = newId,
                    productName = cartItem.product.name,
                    price = cartItem.product.price,
                    quantity = cartItem.quantity,
                    subtotal = cartItem.product.price * cartItem.quantity,
                    costPrice = cartItem.product.costPrice,
                    notes = if (cartItem.selectedVariation.isNotEmpty()) "Varian: ${cartItem.selectedVariation} | ${cartItem.notes}" else cartItem.notes
                )
            }
            transactionItemDao.insertTransactionItems(itemEntities)

            itemsToProcess.forEach { item ->
                val product = _products.value.find { it.id == item.product.id }
                if (product != null) {
                    val sisa = (product.stock - item.quantity).coerceAtLeast(0)
                    productDao.updateStock(product.id, sisa)
                }
            }

            if (!isSplitBill && _selectedCustomer.value != null && method != "BON") {
                val earnedPoints = (total / 10000).toInt()
                val finalPoints = (customer!!.points - _pointsUsed.value + earnedPoints).coerceAtLeast(0)
                customerDao.updateCustomerPoints(customer.id, finalPoints)
            }

            // Tambah Komisi ke Kasir
            if (totalCommission > 0) {
                userDao.addCommission(userId, totalCommission)
            }

            userDao.insertAuditLog(
                AuditLogEntity(
                    userId = userId, userName = userName,
                    action = "TRANSAKSI ($method): ${formatRupiah(total)} ($customerName) | Komisi: ${formatRupiah(totalCommission)}"
                )
            )

            if (isSplitBill) {
                _cart.value = _cart.value.filter { !it.isSelectedForSplit }
            } else {
                _cart.value = emptyList()
                _discount.value = 0
                _voucherDiscount.value = 0
                _appliedVoucher.value = ""
                _pointsUsed.value = 0
                _selectedCustomer.value = null
            }
            onSuccess(completedTransaction)
        }
    }

    fun payUnpaidTransaction(transaction: TransactionEntity, cashReceived: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val change = (cashReceived - transaction.totalPrice).coerceAtLeast(0L)
            transactionDao.updateTransactionStatus(transaction.id, cashReceived, change)
            onSuccess()
        }
    }

    fun refundTransaction(transaction: TransactionEntity, pin: String, currentUser: UserEntity, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val supervisor = userDao.loginByPin(pin)
            if (supervisor == null || (supervisor.role != "OWNER" && supervisor.role != "SUPERVISOR")) {
                onResult(false, "PIN Salah atau bukan Supervisor/Owner!")
                return@launch
            }

            val itemsToRestore = transactionItemDao.getItemsByTransaction(transaction.id).first()
            itemsToRestore.forEach { item ->
                val product = _products.value.find { it.name == item.productName }
                if (product != null) {
                    productDao.updateStock(product.id, product.stock + item.quantity)
                }
            }

            transactionDao.refundTransaction(transaction.id)
            userDao.insertAuditLog(AuditLogEntity(userId = currentUser.id, userName = currentUser.name, action = "REFUND TRANSAKSI #${transaction.id} (Oleh: ${supervisor.name})"))
            onResult(true, "Refund berhasil! Stok telah dikembalikan.")
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
            userDao.insertAuditLog(AuditLogEntity(userId = currentUser.id, userName = currentUser.name, action = "VOID TRANSAKSI #${transaction.id} (Oleh Supervisor: ${supervisor.name})"))
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
    private val transactionItemDao: TransactionItemDao,
    private val voucherDao: VoucherDao,
    private val holdOrderDao: HoldOrderDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PosViewModel(productDao, transactionDao, userDao, cashFlowDao, customerDao, transactionItemDao, voucherDao, holdOrderDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}