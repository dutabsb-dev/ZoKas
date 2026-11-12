package com.karyaku.kasir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class CartItem(
    val product: ProductEntity, 
    var quantity: Int = 1, 
    var isSelectedForSplit: Boolean = false,
    var notes: String = "",
    var selectedVariation: String = "",
    var serialNumber: String = "",
    var assignedStaff: String = "",
    var usedMembershipId: Long? = null,
    var usedMembershipName: String? = null
)

class PosViewModel(
    private val database: AppDatabase
) : ViewModel() {

    private val productDao = database.productDao()
    private val transactionDao = database.transactionDao()
    private val userDao = database.userDao()
    private val cashFlowDao = database.cashFlowDao()
    private val customerDao = database.customerDao()
    private val transactionItemDao = database.transactionItemDao()
    private val voucherDao = database.voucherDao()
    private val holdOrderDao = database.holdOrderDao()
    private val branchDao = database.branchDao()
    private val membershipDao = database.membershipDao()
    private val supplierDao = database.supplierDao()
    private val purchaseDao = database.purchaseDao()
    private val reservationDao = database.reservationDao() 
    private val expenseDao = database.expenseDao()
    private val stockOpnameDao = database.stockOpnameDao()

    private val _products = MutableStateFlow<List<ProductEntity>>(emptyList())
    val products: StateFlow<List<ProductEntity>> = _products.asStateFlow()

    private val _rawMaterials = MutableStateFlow<List<RawMaterialEntity>>(emptyList())
    val rawMaterials: StateFlow<List<RawMaterialEntity>> = _rawMaterials.asStateFlow()

    val cashFlows: StateFlow<List<CashFlowEntity>> = cashFlowDao.getAllCashFlows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _expenses = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    val expenses: StateFlow<List<ExpenseEntity>> = _expenses.asStateFlow()

    private val _stockOpnames = MutableStateFlow<List<StockOpnameEntity>>(emptyList())
    val stockOpnames: StateFlow<List<StockOpnameEntity>> = _stockOpnames.asStateFlow()

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

    private val _memberships = MutableStateFlow<List<MembershipEntity>>(emptyList())
    val memberships: StateFlow<List<MembershipEntity>> = _memberships.asStateFlow()

    private val _customerMemberships = MutableStateFlow<List<CustomerMembershipEntity>>(emptyList())
    val customerMemberships: StateFlow<List<CustomerMembershipEntity>> = _customerMemberships.asStateFlow()

    private val _suppliers = MutableStateFlow<List<SupplierEntity>>(emptyList())
    val suppliers: StateFlow<List<SupplierEntity>> = _suppliers.asStateFlow()

    private val _purchases = MutableStateFlow<List<PurchaseEntity>>(emptyList())
    val purchases: StateFlow<List<PurchaseEntity>> = _purchases.asStateFlow()

    private val _reservations = MutableStateFlow<List<ReservationEntity>>(emptyList())
    val reservations: StateFlow<List<ReservationEntity>> = _reservations.asStateFlow()

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
    
    private val _selectedOrderType = MutableStateFlow("Dine-In")
    val selectedOrderType: StateFlow<String> = _selectedOrderType.asStateFlow()
    
    private val _pickupDate = MutableStateFlow(0L)
    val pickupDate: StateFlow<Long> = _pickupDate.asStateFlow()

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

    private val _isInitializing = MutableStateFlow(true)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    // STATE QRIS BARU
    private val _qrisDynamicString = MutableStateFlow("")
    val qrisDynamicString: StateFlow<String> = _qrisDynamicString.asStateFlow()
    
    private val _isQrisPaid = MutableStateFlow(false)
    val isQrisPaid: StateFlow<Boolean> = _isQrisPaid.asStateFlow()

    val netProfit: StateFlow<Long> = combine<List<TransactionEntity>, List<CashFlowEntity>, List<ExpenseEntity>, Long>(
        _transactions, 
        cashFlowDao.getAllCashFlows(),
        _expenses
    ) { txList, cashFlows, expenseList ->
        val labaKotor = txList.filter { it.status == "SUCCESS" }.sumOf { it.totalPrice - it.totalCost }
        val totalKasKeluar = cashFlows.filter { it.type == "OUT" }.sumOf { it.amount }
        val totalExpenses = expenseList.sumOf { it.amount }
        labaKotor - totalKasKeluar - totalExpenses
    }.catch { emit(0L) }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _taxPercent = MutableStateFlow(0.0)
    val taxPercent: StateFlow<Double> = _taxPercent.asStateFlow()

    private val _serviceChargePercent = MutableStateFlow(0.0)
    val serviceChargePercent: StateFlow<Double> = _serviceChargePercent.asStateFlow()

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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (voucherDao.getVoucherByCode("PROMO10") == null) {
                    voucherDao.insertVoucher(VoucherEntity("PROMO10", 10000))
                }
                checkActiveShift()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) { _isInitializing.value = false }
            }
        }
        
        viewModelScope.launch { productDao.getAllProducts().catch { _products.value = emptyList() }.collectLatest { _products.value = it } }
        viewModelScope.launch { productDao.getAllRawMaterials().catch { _rawMaterials.value = emptyList() }.collectLatest { _rawMaterials.value = it } }
        viewModelScope.launch { transactionDao.getAllTransactions().catch { _transactions.value = emptyList() }.collectLatest { _transactions.value = it } }
        viewModelScope.launch { userDao.getAllAuditLogs().catch { _auditLogs.value = emptyList() }.collectLatest { _auditLogs.value = it } }
        viewModelScope.launch { customerDao.getAllCustomers().catch { _customers.value = emptyList() }.collectLatest { _customers.value = it } }
        viewModelScope.launch { transactionItemDao.getBestSellers().catch { _bestSellers.value = emptyList() }.collectLatest { _bestSellers.value = it } }
        viewModelScope.launch { userDao.getAllUsers().catch { _users.value = emptyList() }.collectLatest { _users.value = it } }
        viewModelScope.launch { holdOrderDao.getAllHoldOrders().catch { _holdOrders.value = emptyList() }.collectLatest { _holdOrders.value = it } }
        viewModelScope.launch { userDao.getAllShifts().catch { _shifts.value = emptyList() }.collectLatest { _shifts.value = it } }
        viewModelScope.launch { membershipDao.getAllMemberships().catch { _memberships.value = emptyList() }.collectLatest { _memberships.value = it } }
        viewModelScope.launch { supplierDao.getAllSuppliers().catch { _suppliers.value = emptyList() }.collectLatest { _suppliers.value = it } }
        viewModelScope.launch { purchaseDao.getAllPurchases().catch { _purchases.value = emptyList() }.collectLatest { _purchases.value = it } }
        viewModelScope.launch { reservationDao.getAllReservations().catch { _reservations.value = emptyList() }.collectLatest { _reservations.value = it } }
        viewModelScope.launch { expenseDao.getAllExpenses().catch { _expenses.value = emptyList() }.collectLatest { _expenses.value = it } }
        viewModelScope.launch { stockOpnameDao.getAllOpnames().catch { _stockOpnames.value = emptyList() }.collectLatest { _stockOpnames.value = it } }
        
        // LISTENER UNTUK NOTIFIKASI QRIS
        viewModelScope.launch {
            PaymentNotificationListener.paymentEvents.collect { notificationText ->
                // Jika sedang menunggu pembayaran QRIS, tandai sebagai lunas
                if (_qrisDynamicString.value.isNotEmpty() && !_isQrisPaid.value) {
                    // Validasi sederhana: cek apakah ada nominal total di teks notifikasi (jika format bank menyertakan nominal)
                    // Karena format tiap bank beda, kita asumsikan jika ada notif masuk dari bank, itu valid.
                    _isQrisPaid.value = true
                    userDao.insertAuditLog(AuditLogEntity(
                        userId = 0, userName = "SYSTEM",
                        action = "QRIS AUTO-DETECT: Pembayaran masuk terdeteksi dari notifikasi.",
                        actionType = "TRANSACTION"
                    ))
                }
            }
        }
    }

    // --- FUNGSI QRIS BARU ---
    fun generateQrisPayment(amount: Long, staticPayload: String) {
        if (staticPayload.isBlank()) return
        val dynamicQris = QrisHelper.generateDynamicQris(staticPayload, amount)
        _qrisDynamicString.value = dynamicQris
        _isQrisPaid.value = false // Reset status
    }

    fun resetQrisPayment() {
        _qrisDynamicString.value = ""
        _isQrisPaid.value = false
    }

    fun setTaxPercent(tax: Double) { _taxPercent.value = tax }
    fun setServiceChargePercent(charge: Double) { _serviceChargePercent.value = charge }
    fun setTable(table: String) { _selectedTable.value = table }
    fun setOrderType(type: String) { _selectedOrderType.value = type }
    fun setPickupDate(date: Long) { _pickupDate.value = date }

    fun registerFullSetup(
        businessType: String,
        storeName: String,
        storeAddress: String,
        storePhone: String,
        adminName: String,
        adminPin: String,
        cashierName: String,
        cashierPin: String,
        onSuccess: (UserEntity) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                database.withTransaction {
                    val mainBranch = BranchEntity(
                        name = "$storeName (Pusat)",
                        address = storeAddress,
                        phone = storePhone,
                        isMainBranch = true
                    )
                    val branchId = branchDao.insertBranch(mainBranch)

                    val admin = UserEntity(
                        name = adminName,
                        pin = hashPin(adminPin), 
                        role = "OWNER",
                        branchId = branchId
                    )
                    val adminId = userDao.insertUser(admin)

                    val cashier = UserEntity(
                        name = cashierName,
                        pin = hashPin(cashierPin),
                        role = "CASHIER",
                        branchId = branchId
                    )
                    userDao.insertUser(cashier)

                    withContext(Dispatchers.Main) {
                        onSuccess(admin.copy(id = adminId))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getFirstOwner(): UserEntity? {
        return try {
            userDao.getAllUsersOnce().firstOrNull { it.role == "OWNER" }
        } catch (e: Exception) {
            null
        }
    }

    fun processBarcodeScan(barcode: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var cleanBarcode = barcode
                var parsedExpiry = 0L
                var parsedBatch = ""

                val gs1Regex = Regex("""\(01\)(\d+)\(17\)(\d{6})\(10\)([A-Za-z0-9]+)""")
                val match = gs1Regex.find(barcode)
                
                if (match != null) {
                    cleanBarcode = match.groupValues[1]
                    val expiryStr = match.groupValues[2]
                    parsedBatch = match.groupValues[3]
                    
                    try {
                        val year = 2000 + expiryStr.substring(0, 2).toInt()
                        val month = expiryStr.substring(2, 4).toInt() - 1
                        val day = expiryStr.substring(4, 6).toInt()
                        val cal = java.util.Calendar.getInstance()
                        cal.set(year, month, day, 23, 59, 59)
                        parsedExpiry = cal.timeInMillis
                    } catch (e: Exception) { }
                }

                val product = productDao.getProductByBarcode(cleanBarcode)
                if (product != null) {
                    if (product.isService || product.stock > 0) {
                        val finalProduct = if (parsedExpiry > 0) {
                            product.copy(expiryDate = parsedExpiry, batchNumber = parsedBatch)
                        } else product
                        
                        withContext(Dispatchers.Main) { addToCart(finalProduct) }
                        withContext(Dispatchers.Main) { onResult(true, "Berhasil tambah: ${product.name}") }
                    } else {
                        withContext(Dispatchers.Main) { onResult(false, "Stok ${product.name} habis!") }
                    }
                } else {
                    withContext(Dispatchers.Main) { onResult(false, "Barcode tidak ditemukan!") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, "Gagal scan: ${e.message}") }
            }
        }
    }

    fun updateCartItemNotes(product: ProductEntity, variation: String, notes: String) {
        val currentCart = _cart.value.toMutableList()
        val idx = currentCart.indexOfFirst { it.product.id == product.id && it.selectedVariation == variation }
        if (idx != -1) { currentCart[idx] = currentCart[idx].copy(notes = notes); _cart.value = currentCart }
    }

    fun updateCartItemSerial(product: ProductEntity, variation: String, serial: String) {
        val currentCart = _cart.value.toMutableList()
        val idx = currentCart.indexOfFirst { it.product.id == product.id && it.selectedVariation == variation }
        if (idx != -1) { currentCart[idx] = currentCart[idx].copy(serialNumber = serial); _cart.value = currentCart }
    }

    fun updateCartItemStaff(product: ProductEntity, variation: String, staff: String) {
        val currentCart = _cart.value.toMutableList()
        val idx = currentCart.indexOfFirst { it.product.id == product.id && it.selectedVariation == variation }
        if (idx != -1) { currentCart[idx] = currentCart[idx].copy(assignedStaff = staff); _cart.value = currentCart }
    }

    fun mergeOrder(tableA: String, tableB: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val orderA = _holdOrders.value.find { it.tableName == tableA }
                val orderB = _holdOrders.value.find { it.tableName == tableB }
                
                if (orderA != null && orderB != null) {
                    val jsonA = JSONArray(orderA.cartJson)
                    val jsonB = JSONArray(orderB.cartJson)
                    for (i in 0 until jsonB.length()) { jsonA.put(jsonB.getJSONObject(i)) }
                    
                    holdOrderDao.deleteHoldOrder(orderB.id)
                    holdOrderDao.insertHoldOrder(orderA.copy(tableName = tableA, cartJson = jsonA.toString()))
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun addBom(productId: Long, rawMaterialId: Long, qty: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                productDao.insertProductBom(ProductBomEntity(productId = productId, rawMaterialId = rawMaterialId, quantityUsed = qty))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun addRawMaterial(name: String, stock: Double, unit: String, costPerUnit: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                productDao.insertRawMaterial(RawMaterialEntity(name = name, stock = stock, unit = unit, costPerUnit = costPerUnit))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun addMembership(name: String, price: Long, quota: Int, days: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                membershipDao.insertMembership(MembershipEntity(name = name, price = price, quotaCount = quota, validDays = days))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun buyMembership(customerId: Long, membership: MembershipEntity, onSuccess: () -> Unit) {
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

    fun applyMembershipToCart(cartItem: CartItem, customerMembership: CustomerMembershipEntity) {
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

    fun removeMembershipFromCart(cartItem: CartItem) {
        val currentCart = _cart.value.toMutableList()
        val idx = currentCart.indexOfFirst { it.product.id == cartItem.product.id && it.selectedVariation == cartItem.selectedVariation }
        if (idx != -1) {
            currentCart[idx] = currentCart[idx].copy(usedMembershipId = null, usedMembershipName = null)
            _cart.value = currentCart
        }
    }

    fun addSupplier(name: String, contact: String, phone: String, address: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                supplierDao.insertSupplier(SupplierEntity(name = name, contactPerson = contact, phone = phone, address = address))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun createPurchaseOrder(
        supplier: SupplierEntity,
        invoiceNumber: String,
        items: List<PurchaseItemEntity>,
        isPaid: Boolean,
        updateCostPrice: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                database.withTransaction {
                    val total = items.sumOf { it.subtotal }
                    val purchase = PurchaseEntity(
                        supplierId = supplier.id,
                        supplierName = supplier.name,
                        invoiceNumber = invoiceNumber,
                        totalAmount = total,
                        status = if (isPaid) "PAID" else "UNPAID"
                    )
                    val purchaseId = purchaseDao.insertPurchase(purchase)
                    
                    val itemsWithId = items.map { it.copy(purchaseId = purchaseId) }
                    purchaseDao.insertPurchaseItems(itemsWithId)
                    
                    items.forEach { item ->
                        val product = _products.value.find { it.id == item.productId }
                        if (product != null) {
                            val newStock = product.stock + item.quantity
                            val newCost = if (updateCostPrice) item.costPrice else product.costPrice
                            productDao.insertProduct(product.copy(stock = newStock, costPrice = newCost, isSynced = false, updatedAt = System.currentTimeMillis()))
                        }
                    }
                }
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun payPurchase(purchaseId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                purchaseDao.markAsPaid(purchaseId)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun addReservation(customerName: String, phone: String, dateTime: Long, resourceName: String, notes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                reservationDao.insertReservation(ReservationEntity(
                    customerName = customerName,
                    phone = phone,
                    dateTime = dateTime,
                    resourceName = resourceName,
                    notes = notes
                ))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun updateReservationStatus(id: Long, status: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                reservationDao.updateStatus(id, status)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deleteReservation(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                reservationDao.deleteReservation(id)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun addExpense(category: String, amount: Long, note: String, user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val expense = ExpenseEntity(
                    category = category,
                    amount = amount,
                    note = note,
                    userId = user.id,
                    userName = user.name
                )
                expenseDao.insertExpense(expense)
                userDao.insertAuditLog(AuditLogEntity(
                    userId = user.id, userName = user.name,
                    action = "PENGELUARAN: $category - ${formatRupiah(amount)} ($note)",
                    actionType = "FINANCE"
                ))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun processStockOpname(
        opnameItems: List<StockOpnameItemEntity>,
        user: UserEntity,
        onSuccess: () -> Unit
    ) {
        if (opnameItems.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                database.withTransaction {
                    val timestamp = System.currentTimeMillis()
                    var totalLoss = 0L
                    var totalGain = 0L

                    opnameItems.forEach { item ->
                        val product = _products.value.find { it.id == item.productId }
                        if (product != null) {
                            if (item.difference < 0) {
                                totalLoss += (Math.abs(item.difference) * product.costPrice)
                            } else if (item.difference > 0) {
                                totalGain += (item.difference * product.costPrice)
                            }
                            productDao.updateStock(product.id, item.physicalStock)
                        }
                    }

                    val opname = StockOpnameEntity(
                        timestamp = timestamp,
                        userId = user.id,
                        userName = user.name,
                        totalItemsChecked = opnameItems.size,
                        totalLossValue = totalLoss,
                        totalGainValue = totalGain
                    )
                    val opnameId = stockOpnameDao.insertOpname(opname)

                    val itemsWithId = opnameItems.map { it.copy(opnameId = opnameId) }
                    stockOpnameDao.insertOpnameItems(itemsWithId)

                    userDao.insertAuditLog(AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        action = "STOCK OPNAME: ${opnameItems.size} item diaudit. Rugi: ${formatRupiah(totalLoss)}, Untung: ${formatRupiah(totalGain)}",
                        actionType = "INVENTORY"
                    ))
                }
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun exportProductsToCsv(): String {
        val header = "Nama,HargaJual,HargaModal,Stok,Kategori,Barcode,Varian,Komisi,Expiry,IsService,HargaGrosir,MinGrosir\n"
        val rows = _products.value.joinToString("\n") { 
            "${it.name},${it.price},${it.costPrice},${it.stock},${it.category},${it.barcode},${it.variations},${it.commissionPerItem},${it.expiryDate},${it.isService},${it.wholesalePrice},${it.wholesaleMinQty}" 
        }
        return header + rows
    }

    fun importProductsFromCsv(csvText: String, onResult: (Int, Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var insertCount = 0
            var updateCount = 0
            try {
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
                            val isService = if (parts.size > 9) parts[9].trim().toBoolean() else false
                            val wholesalePrice = if (parts.size > 10) parts[10].trim().toLongOrNull() ?: 0L else 0L
                            val wholesaleMinQty = if (parts.size > 11) parts[11].trim().toIntOrNull() ?: 0 else 0

                            val existingProduct = _products.value.find { 
                                (barcode.isNotEmpty() && it.barcode == barcode) || it.name.equals(name, ignoreCase = true)
                            }

                            if (existingProduct != null) {
                                productDao.insertProduct(existingProduct.copy(
                                    name = name, price = price, costPrice = costPrice, stock = stock, 
                                    category = category, barcode = barcode, variations = variations, commissionPerItem = commission, expiryDate = expiry, isService = isService,
                                    wholesalePrice = wholesalePrice, wholesaleMinQty = wholesaleMinQty,
                                    isSynced = false, updatedAt = System.currentTimeMillis()
                                ))
                                updateCount++
                            } else {
                                productDao.insertProduct(ProductEntity(
                                    name = name, price = price, costPrice = costPrice, stock = stock, 
                                    category = category, barcode = barcode, variations = variations, commissionPerItem = commission, expiryDate = expiry, isService = isService,
                                    wholesalePrice = wholesalePrice, wholesaleMinQty = wholesaleMinQty,
                                    isSynced = false, updatedAt = System.currentTimeMillis()
                                ))
                                insertCount++
                            }
                        } catch (e: Exception) { }
                    }
                }
                withContext(Dispatchers.Main) { onResult(insertCount, updateCount) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(0, 0) }
            }
        }
    }

    fun recordWaste(product: ProductEntity, qty: Int, user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newStock = (product.stock - qty).coerceAtLeast(0)
                productDao.updateStock(product.id, newStock)
                val loss = qty * product.costPrice
                userDao.insertAuditLog(AuditLogEntity(
                    userId = user.id, userName = user.name, 
                    action = "WASTE: ${product.name} -$qty pcs. Rugi: ${formatRupiah(loss)}",
                    actionType = "INVENTORY"
                ))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun logCashDrawerOpen(user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) { 
            try {
                userDao.insertAuditLog(AuditLogEntity(
                    userId = user.id, userName = user.name, 
                    action = "BUKA LACI MANUAL (No Sale)",
                    actionType = "SYSTEM"
                )) 
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun saveHoldOrder(customerName: String) {
        if (_cart.value.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonArray = JSONArray()
                _cart.value.forEach { item ->
                    val jsonObj = JSONObject()
                    jsonObj.put("productId", item.product.id)
                    jsonObj.put("quantity", item.quantity)
                    jsonObj.put("variation", item.selectedVariation)
                    jsonObj.put("notes", item.notes)
                    jsonObj.put("serial", item.serialNumber)
                    jsonObj.put("staff", item.assignedStaff)
                    jsonObj.put("membershipId", item.usedMembershipId ?: 0L)
                    jsonObj.put("membershipName", item.usedMembershipName ?: "")
                    jsonArray.put(jsonObj)
                }
                val holdOrder = HoldOrderEntity(tableName = _selectedTable.value, customerName = customerName, cartJson = jsonArray.toString())
                holdOrderDao.insertHoldOrder(holdOrder)
                withContext(Dispatchers.Main) {
                    _cart.value = emptyList()
                    _selectedTable.value = "Take-Away"
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun resumeHoldOrder(order: HoldOrderEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newCart = mutableListOf<CartItem>()
                val jsonArray = JSONArray(order.cartJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val productId = obj.getLong("productId")
                    val qty = obj.getInt("quantity")
                    val variation = obj.optString("variation", "")
                    val notes = obj.optString("notes", "")
                    val serial = obj.optString("serial", "")
                    val staff = obj.optString("staff", "")
                    val memId = obj.optLong("membershipId", 0L).takeIf { it > 0 }
                    val memName = obj.optString("membershipName", "").takeIf { it.isNotEmpty() }
                    val product = _products.value.find { it.id == productId }
                    if (product != null) newCart.add(CartItem(product, qty, selectedVariation = variation, notes = notes, serialNumber = serial, assignedStaff = staff, usedMembershipId = memId, usedMembershipName = memName))
                }
                holdOrderDao.deleteHoldOrder(order.id)
                withContext(Dispatchers.Main) {
                    _cart.value = newCart
                    _selectedTable.value = order.tableName
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun applyVoucher(code: String, onResult: (Boolean, String) -> Unit) {
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

    fun addStock(product: ProductEntity, qtyToAdd: Int, user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newStock = product.stock + qtyToAdd
                productDao.updateStock(product.id, newStock)
                userDao.insertAuditLog(AuditLogEntity(
                    userId = user.id, userName = user.name, 
                    action = "RESTOCK: ${product.name} +$qtyToAdd",
                    actionType = "INVENTORY"
                ))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setCategory(category: String) { _selectedCategory.value = category }
    fun setDiscount(amount: Long) { _discount.value = amount }
    
    fun setCustomer(customer: CustomerEntity?) {
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
    
    fun redeemPoints(pointsToRedeem: Int) {
        val customer = _selectedCustomer.value ?: return
        if (pointsToRedeem > 0 && pointsToRedeem <= customer.points) {
            _pointsUsed.value = pointsToRedeem
            _discount.value = (pointsToRedeem * 1000).toLong()
        }
    }
    
    fun addCustomer(name: String, phone: String) { 
        viewModelScope.launch(Dispatchers.IO) { 
            try { customerDao.insertCustomer(CustomerEntity(name = name, phone = phone)) } 
            catch (e: Exception) { e.printStackTrace() }
        } 
    }

    fun getTransactionItems(transactionId: Long): Flow<List<TransactionItemEntity>> { 
        return transactionItemDao.getItemsByTransaction(transactionId) 
    }
    
    fun checkActiveShift() { 
        viewModelScope.launch(Dispatchers.IO) { 
            try { _activeShift.value = userDao.getActiveShift() } 
            catch (e: Exception) { e.printStackTrace() }
        } 
    }

    suspend fun login(pin: String): UserEntity? {
        return try {
            val hashedPin = hashPin(pin)
            val user = userDao.loginByPin(hashedPin)
            if (user != null) {
                userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "LOGIN_SUCCESS", actionType = "AUTH"))
            }
            user
        } catch (e: Exception) { null }
    }

    fun updatePin(user: UserEntity, newPin: String) { 
        viewModelScope.launch(Dispatchers.IO) { 
            try {
                val hashedPin = hashPin(newPin)
                userDao.updatePin(user.id, hashedPin)
                userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "UBAH PIN", actionType = "SYSTEM")) 
            } catch (e: Exception) { e.printStackTrace() }
        } 
    }
    
    fun addUser(name: String, pin: String, role: String, adminUser: UserEntity) { 
        viewModelScope.launch(Dispatchers.IO) { 
            try {
                val hashedPin = hashPin(pin)
                userDao.insertUser(UserEntity(name = name, pin = hashedPin, role = role, branchId = adminUser.branchId))
                userDao.insertAuditLog(AuditLogEntity(userId = adminUser.id, userName = adminUser.name, action = "TAMBAH KARYAWAN: $name", actionType = "AUTH")) 
            } catch (e: Exception) { e.printStackTrace() }
        } 
    }
    
    fun updateUser(user: UserEntity, adminUser: UserEntity) { 
        viewModelScope.launch(Dispatchers.IO) { 
            try {
                val hashedPin = hashPin(user.pin)
                userDao.updateUser(user.copy(pin = hashedPin))
                userDao.insertAuditLog(AuditLogEntity(userId = adminUser.id, userName = adminUser.name, action = "EDIT KARYAWAN: ${user.name}", actionType = "AUTH")) 
            } catch (e: Exception) { e.printStackTrace() }
        } 
    }
    
    fun deleteUser(user: UserEntity, adminUser: UserEntity) { 
        viewModelScope.launch(Dispatchers.IO) { 
            try {
                userDao.deleteUser(user)
                userDao.insertAuditLog(AuditLogEntity(userId = adminUser.id, userName = adminUser.name, action = "HAPUS KARYAWAN: ${user.name}", actionType = "AUTH")) 
            } catch (e: Exception) { e.printStackTrace() }
        } 
    }

    fun openShift(user: UserEntity, modalAwal: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val shift = ShiftEntity(userId = user.id, userName = user.name, openingCash = modalAwal)
                userDao.insertShift(shift)
                userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "BUKA KASIR: Modal ${formatRupiah(modalAwal)}", actionType = "SYSTEM"))
                checkActiveShift()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun addCashFlow(type: String, amount: Long, note: String, user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val shift = _activeShift.value ?: return@launch
                val cashFlow = CashFlowEntity(shiftId = shift.id, type = type, amount = amount, note = note, userId = user.id, userName = user.name)
                cashFlowDao.insertCashFlow(cashFlow)
                val actionText = if (type == "OUT") "KAS KELUAR: $note (${formatRupiah(amount)})" else "KAS MASUK: $note (${formatRupiah(amount)})"
                userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = actionText, actionType = "FINANCE"))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun closeShift(user: UserEntity, uangFisik: Long, note: String, onSuccess: (ShiftEntity) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val shift = _activeShift.value
                if (shift != null) {
                    val totalPenjualan = _transactions.value.filter { it.timestamp >= shift.startTime && it.status == "SUCCESS" }.sumOf { it.totalPrice }
                    val totalKasKeluar = cashFlowDao.getTotalCashOut(shift.id) ?: 0L
                    val totalKasMasuk = cashFlowDao.getTotalCashIn(shift.id) ?: 0L
                    val uangSistem = shift.openingCash + totalPenjualan + totalKasMasuk - totalKasKeluar
                    val selisih = uangFisik - uangSistem

                    userDao.closeShift(shift.id, System.currentTimeMillis(), uangSistem, uangFisik, selisih)
                    userDao.insertAuditLog(AuditLogEntity(
                        userId = user.id, userName = user.name, 
                        action = "TUTUP KASIR: Fisik ${formatRupiah(uangFisik)}, Sistem ${formatRupiah(uangSistem)}, Selisih ${formatRupiah(selisih)} | Catatan: $note", 
                        actionType = "SYSTEM"
                    ))
                    checkActiveShift()
                    withContext(Dispatchers.Main) {
                        onSuccess(shift.copy(endTime = System.currentTimeMillis(), expectedCash = uangSistem, closingCash = uangFisik, discrepancy = selisih, status = "CLOSED"))
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun addProduct(
        name: String, price: Long, stock: Int, category: String, costPrice: Long, 
        barcode: String, variations: String, commission: Long, expiry: Long, 
        isService: Boolean, isCombo: Boolean, imagePath: String, 
        wholesalePrice: Long, wholesaleMinQty: Int,
        userId: Long, userName: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                productDao.insertProduct(ProductEntity(
                    name = name, price = price, stock = stock, category = category, costPrice = costPrice, 
                    barcode = barcode, variations = variations, commissionPerItem = commission, expiryDate = expiry,
                    isService = isService, isCombo = isCombo, imagePath = imagePath, 
                    wholesalePrice = wholesalePrice, wholesaleMinQty = wholesaleMinQty,
                    isSynced = false, updatedAt = System.currentTimeMillis()
                ))
                userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "TAMBAH PRODUK: $name", actionType = "INVENTORY"))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun updateProduct(product: ProductEntity, userId: Long, userName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val oldProduct = _products.value.find { it.id == product.id }
                if (oldProduct != null && oldProduct.price != product.price) {
                    userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "UBAH HARGA: ${product.name} dari ${formatRupiah(oldProduct.price)} menjadi ${formatRupiah(product.price)}", actionType = "INVENTORY"))
                } else {
                    userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "EDIT PRODUK: ${product.name}", actionType = "INVENTORY"))
                }
                productDao.insertProduct(product.copy(isSynced = false, updatedAt = System.currentTimeMillis()))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deleteProduct(product: ProductEntity, userId: Long, userName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                productDao.deleteProduct(product)
                userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "HAPUS PRODUK: ${product.name}", actionType = "INVENTORY"))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun addToCart(product: ProductEntity, variation: String = "") {
        val currentCart = _cart.value.toMutableList()
        val idx = currentCart.indexOfFirst { it.product.id == product.id && it.selectedVariation == variation }
        
        if (idx != -1) {
            val item = currentCart[idx]
            if (product.isService || item.quantity < item.product.stock) {
                currentCart[idx] = item.copy(quantity = item.quantity + 1)
            }
        } else {
            if (product.isService || product.stock > 0) {
                currentCart.add(CartItem(product, selectedVariation = variation))
            }
        }
        _cart.value = currentCart
    }

    fun removeFromCart(product: ProductEntity, variation: String) {
        val currentCart = _cart.value.toMutableList()
        val idx = currentCart.indexOfFirst { it.product.id == product.id && it.selectedVariation == variation }
        if (idx != -1) {
            val item = currentCart[idx]
            if (item.quantity > 1) currentCart[idx] = item.copy(quantity = item.quantity - 1)
            else currentCart.removeAt(idx)
            _cart.value = currentCart
        }
    }

    fun deleteItem(product: ProductEntity, variation: String) {
        _cart.value = _cart.value.toMutableList().also { it.removeAll { item -> item.product.id == product.id && item.selectedVariation == variation } }
    }

    fun toggleSplitSelection(product: ProductEntity, variation: String) {
        val currentCart = _cart.value.toMutableList()
        val idx = currentCart.indexOfFirst { it.product.id == product.id && it.selectedVariation == variation }
        if (idx != -1) {
            val item = currentCart[idx]
            currentCart[idx] = item.copy(isSelectedForSplit = !item.isSelectedForSplit)
            _cart.value = currentCart
        }
    }

    fun getTotalPrice(): Long {
        val subtotal = _cart.value.sumOf { item ->
            if (item.usedMembershipId != null) {
                0L
            } else if (item.product.wholesaleMinQty > 0 && item.quantity >= item.product.wholesaleMinQty && item.product.wholesalePrice > 0) {
                item.product.wholesalePrice * item.quantity
            } else {
                item.product.price * item.quantity
            }
        }
        val tax = (subtotal - _discount.value - _voucherDiscount.value) * (_taxPercent.value / 100.0)
        val service = (subtotal - _discount.value - _voucherDiscount.value) * (_serviceChargePercent.value / 100.0)
        return (subtotal + tax + service - _discount.value - _voucherDiscount.value).coerceAtLeast(0.0).toLong()
    }

    fun processPayment(
        cashReceived: Long,
        userId: Long,
        userName: String,
        isSplitBill: Boolean = false,
        method: String = "CASH",
        maxDiscountCap: Long = 0L,
        onSuccess: (TransactionEntity) -> Unit
    ) {
        val itemsToProcess = if (isSplitBill) _cart.value.filter { it.isSelectedForSplit } else _cart.value
        if (itemsToProcess.isEmpty()) return

        var subtotal = 0L
        var totalCommission = 0L
        var promoDiscount = 0L

        itemsToProcess.forEach { item ->
            val itemPrice = if (item.usedMembershipId != null) {
                0L
            } else if (item.product.wholesaleMinQty > 0 && item.quantity >= item.product.wholesaleMinQty && item.product.wholesalePrice > 0) {
                item.product.wholesalePrice
            } else {
                item.product.price
            }
            val itemSubtotal = itemPrice * item.quantity
            subtotal += itemSubtotal
            totalCommission += (item.product.commissionPerItem * item.quantity)

            if (item.product.promoType == "BOGO" && item.quantity >= 2) {
                val freeItems = item.quantity / 2
                promoDiscount += freeItems * itemPrice
            } else if (item.product.promoType == "TIERED" && item.quantity >= item.product.promoMinQty) {
                promoDiscount += (itemSubtotal * item.product.promoValue) / 100
            }
        }

        var tierDiscount = 0L
        val customer = _selectedCustomer.value
        if (customer != null) {
            if (customer.points >= 1000) tierDiscount = (subtotal * 0.10).toLong()
            else if (customer.points >= 500) tierDiscount = (subtotal * 0.05).toLong()
            else if (customer.points >= 100) tierDiscount = (subtotal * 0.02).toLong()
        }

        var currentDiscount = if (isSplitBill) promoDiscount else (_discount.value + _voucherDiscount.value + tierDiscount + promoDiscount)
        
        if (maxDiscountCap > 0 && currentDiscount > maxDiscountCap) {
            currentDiscount = maxDiscountCap
        }

        val taxAmount = (subtotal - currentDiscount) * (_taxPercent.value / 100.0)
        val serviceChargeAmount = (subtotal - currentDiscount) * (_serviceChargePercent.value / 100.0)
        val total = (subtotal + taxAmount + serviceChargeAmount - currentDiscount).coerceAtLeast(0.0).toLong()

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
            orderType = _selectedOrderType.value,
            serviceCharge = serviceChargeAmount.toLong(),
            pickupDate = _pickupDate.value,
            voucherCode = if (isSplitBill) "" else _appliedVoucher.value,
            taxAmount = taxAmount.toLong(),
            paymentMethod = method,
            status = status,
            cashierName = userName,
            totalCommission = totalCommission,
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                database.withTransaction {
                    val newId = transactionDao.insertTransaction(transaction)
                    val completedTransaction = transaction.copy(id = newId)

                    val itemEntities = itemsToProcess.map { cartItem ->
                        val finalPrice = if (cartItem.usedMembershipId != null) {
                            0L
                        } else if (cartItem.product.wholesaleMinQty > 0 && cartItem.quantity >= cartItem.product.wholesaleMinQty && cartItem.product.wholesalePrice > 0) {
                            cartItem.product.wholesalePrice
                        } else {
                            cartItem.product.price
                        }
                        TransactionItemEntity(
                            transactionId = newId,
                            productId = cartItem.product.id,
                            productName = cartItem.product.name,
                            price = finalPrice,
                            quantity = cartItem.quantity,
                            subtotal = finalPrice * cartItem.quantity,
                            costPrice = cartItem.product.costPrice,
                            notes = if (cartItem.selectedVariation.isNotEmpty()) "Varian: ${cartItem.selectedVariation} | ${cartItem.notes}" else cartItem.notes,
                            serialNumber = cartItem.serialNumber,
                            assignedStaff = cartItem.assignedStaff,
                            isService = cartItem.product.isService
                        )
                    }
                    transactionItemDao.insertTransactionItems(itemEntities)

                    itemsToProcess.forEach { item ->
                        if (!item.product.isService && !item.product.isCombo) {
                            val product = _products.value.find { it.id == item.product.id }
                            if (product != null) {
                                val sisa = (product.stock - item.quantity).coerceAtLeast(0)
                                productDao.updateStock(product.id, sisa)
                            }
                            
                            val boms = productDao.getBomForProduct(item.product.id)
                            boms.forEach { bom ->
                                val material = _rawMaterials.value.find { it.id == bom.rawMaterialId }
                                if (material != null) {
                                    val newStock = (material.stock - (bom.quantityUsed * item.quantity)).coerceAtLeast(0.0)
                                    productDao.updateRawMaterialStock(material.id, newStock)
                                }
                            }
                        }
                        
                        if (item.usedMembershipId != null) {
                            val cm = _customerMemberships.value.find { it.id == item.usedMembershipId }
                            if (cm != null) {
                                val newQuota = (cm.remainingQuota - item.quantity).coerceAtLeast(0)
                                membershipDao.updateRemainingQuota(cm.id, newQuota)
                            }
                        }
                    }

                    if (!isSplitBill && _selectedCustomer.value != null && method != "BON") {
                        val earnedPoints = (total / 10000).toInt()
                        val finalPoints = (customer!!.points - _pointsUsed.value + earnedPoints).coerceAtLeast(0)
                        customerDao.updateCustomerPoints(customer.id, finalPoints)
                    }

                    if (totalCommission > 0) { userDao.addCommission(userId, totalCommission) }

                    userDao.insertAuditLog(
                        AuditLogEntity(
                            userId = userId, userName = userName, 
                            action = "TRANSAKSI ($method): ${formatRupiah(total)} ($customerName)",
                            actionType = "TRANSACTION"
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    if (isSplitBill) {
                        _cart.value = _cart.value.filter { !it.isSelectedForSplit }
                    } else {
                        _cart.value = emptyList()
                        _discount.value = 0
                        _voucherDiscount.value = 0
                        _appliedVoucher.value = ""
                        _pointsUsed.value = 0
                        _selectedCustomer.value = null
                        _pickupDate.value = 0 
                        _customerMemberships.value = emptyList()
                    }
                    resetQrisPayment() // Reset QRIS setelah sukses
                    onSuccess(transaction.copy(id = transaction.id)) 
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun payUnpaidTransaction(transaction: TransactionEntity, cashReceived: Long, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val change = (cashReceived - transaction.totalPrice).coerceAtLeast(0L)
                transactionDao.updateTransactionStatus(transaction.id, cashReceived, change)
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun refundTransaction(transaction: TransactionEntity, pin: String, currentUser: UserEntity, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val hashedPin = hashPin(pin)
                val supervisor = userDao.loginByPin(hashedPin)
                if (supervisor == null || (supervisor.role != "OWNER" && supervisor.role != "SUPERVISOR")) {
                    withContext(Dispatchers.Main) { onResult(false, "PIN Salah atau bukan Supervisor/Owner!") }
                    return@launch
                }

                val itemsToRestore = transactionItemDao.getItemsByTransaction(transaction.id).first()
                
                database.withTransaction {
                    itemsToRestore.forEach { item ->
                        if (!item.isService) {
                            val product = _products.value.find { it.id == item.productId }
                            if (product != null) { 
                                productDao.updateStock(product.id, product.stock + item.quantity) 
                            }
                        }
                    }

                    transactionDao.refundTransaction(transaction.id)
                    userDao.insertAuditLog(AuditLogEntity(
                        userId = currentUser.id, userName = currentUser.name, 
                        action = "REFUND TRANSAKSI #${transaction.id} (Oleh: ${supervisor.name})",
                        actionType = "TRANSACTION"
                    ))
                }
                
                withContext(Dispatchers.Main) { onResult(true, "Refund berhasil!") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, "Gagal refund: ${e.message}") }
            }
        }
    }

    fun voidTransaction(transaction: TransactionEntity, pinSupervisor: String, currentUser: UserEntity, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val hashedPin = hashPin(pinSupervisor)
                val supervisor = userDao.loginByPin(hashedPin)
                if (supervisor == null || (supervisor.role != "OWNER" && supervisor.role != "SUPERVISOR")) {
                    withContext(Dispatchers.Main) { onResult(false, "PIN Salah atau bukan Supervisor!") }
                    return@launch
                }

                val itemsToRestore = transactionItemDao.getItemsByTransaction(transaction.id).first()
                
                database.withTransaction {
                    itemsToRestore.forEach { item ->
                        if (!item.isService) {
                            val product = _products.value.find { it.id == item.productId }
                            if (product != null) { 
                                productDao.updateStock(product.id, product.stock + item.quantity) 
                            }
                        }
                    }

                    transactionDao.voidTransaction(transaction.id)
                    userDao.insertAuditLog(AuditLogEntity(
                        userId = currentUser.id, userName = currentUser.name, 
                        action = "VOID TRANSAKSI #${transaction.id} (Oleh Supervisor: ${supervisor.name})",
                        actionType = "TRANSACTION"
                    ))
                }
                
                withContext(Dispatchers.Main) { onResult(true, "Transaksi berhasil di-void.") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, "Gagal void: ${e.message}") }
            }
        }
    }
}

class PosViewModelFactory(private val database: AppDatabase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PosViewModel(database) as T 
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}