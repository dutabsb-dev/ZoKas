package com.karyaku.kasir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun parseFractionOrDecimalQty(raw: String): Double {
    val clean = raw.trim().replace(',', '.')
    if (clean.isEmpty()) return 0.0
    // Tangani pecahan campuran seperti "1 1/2"
    val partsSpace = clean.split(Regex("\\s+"))
    if (partsSpace.size == 2 && partsSpace[1].contains("/")) {
        val whole = partsSpace[0].toDoubleOrNull() ?: 0.0
        val frac = parseFractionOrDecimalQty(partsSpace[1])
        return (whole + frac).coerceAtLeast(0.0)
    }
    // Tangani pecahan biasa seperti "1/4", "1/2", "3/4"
    if (clean.contains("/")) {
        val fracParts = clean.split("/")
        if (fracParts.size == 2) {
            val num = fracParts[0].toDoubleOrNull() ?: return 0.0
            val den = fracParts[1].toDoubleOrNull() ?: return 0.0
            if (den != 0.0) return (num / den).coerceAtLeast(0.0)
        }
    }
    return clean.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
}

fun formatQtyDisplay(qty: Double): String {
    val rounded = Math.round(qty * 1000.0) / 1000.0
    if (Math.abs(rounded - Math.rint(rounded)) < 1e-6) {
        return rounded.toInt().toString()
    }
    val str = String.format(java.util.Locale.US, "%.3f", rounded).trimEnd('0').trimEnd('.')
    return str
}

data class CartItem(
    val product: ProductEntity, 
    var quantity: Int = 1,
    var qtyFraction: Double = 1.0,
    var isSelectedForSplit: Boolean = false,
    var notes: String = "",
    var selectedVariation: String = "",
    var serialNumber: String = "",
    var assignedStaff: String = "",
    var usedMembershipId: Long? = null,
    var usedMembershipName: String? = null
) {
    val effectiveQty: Double
        get() = if (qtyFraction > 0.0) qtyFraction else quantity.toDouble()
}

class PosViewModel(
    internal val database: AppDatabase
) : ViewModel() {

    internal val productDao = database.productDao()
    internal val transactionDao = database.transactionDao()
    internal val userDao = database.userDao()
    internal val cashFlowDao = database.cashFlowDao()
    internal val customerDao = database.customerDao()
    internal val transactionItemDao = database.transactionItemDao()
    internal val voucherDao = database.voucherDao()
    internal val holdOrderDao = database.holdOrderDao()
    internal val branchDao = database.branchDao()
    internal val membershipDao = database.membershipDao()
    internal val supplierDao = database.supplierDao()
    internal val purchaseDao = database.purchaseDao()
    internal val purchasePaymentDao = database.purchasePaymentDao()
    internal val reservationDao = database.reservationDao()
    internal val expenseDao = database.expenseDao()
    internal val stockOpnameDao = database.stockOpnameDao()
    internal val stockTransferDao = database.stockTransferDao()
    internal val kitchenTicketDao = database.kitchenTicketDao()
    internal val attendanceDao = database.attendanceDao()
    internal val payrollDao = database.payrollDao()

    // STATE FLOWS
    internal val _currentBranchId = MutableStateFlow(1L)
    val currentBranchId: StateFlow<Long> = _currentBranchId.asStateFlow()

    internal val _isBranchSwitchLocked = MutableStateFlow(false)
    val isBranchSwitchLocked: StateFlow<Boolean> = _isBranchSwitchLocked.asStateFlow()

    internal val _allBranches = MutableStateFlow<List<BranchEntity>>(emptyList())
    val allBranches: StateFlow<List<BranchEntity>> = _allBranches.asStateFlow()

    val currentBranch: StateFlow<BranchEntity?> = combine(_currentBranchId, _allBranches) { id, branches ->
        branches.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    internal val _transfersForCurrentBranch = MutableStateFlow<List<StockTransferEntity>>(emptyList())
    val transfersForCurrentBranch: StateFlow<List<StockTransferEntity>> = _transfersForCurrentBranch.asStateFlow()

    internal val _products = MutableStateFlow<List<ProductEntity>>(emptyList())
    val products: StateFlow<List<ProductEntity>> = _products.asStateFlow()

    internal val _rawMaterials = MutableStateFlow<List<RawMaterialEntity>>(emptyList())
    val rawMaterials: StateFlow<List<RawMaterialEntity>> = _rawMaterials.asStateFlow()

    internal val _expenses = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    val expenses: StateFlow<List<ExpenseEntity>> = _expenses.asStateFlow()

    internal val _stockOpnames = MutableStateFlow<List<StockOpnameEntity>>(emptyList())
    val stockOpnames: StateFlow<List<StockOpnameEntity>> = _stockOpnames.asStateFlow()

    internal val _bestSellers = MutableStateFlow<List<BestSellerData>>(emptyList())
    val bestSellers: StateFlow<List<BestSellerData>> = _bestSellers.asStateFlow()

    internal val _customers = MutableStateFlow<List<CustomerEntity>>(emptyList())
    val customers: StateFlow<List<CustomerEntity>> = _customers.asStateFlow()

    internal val _memberships = MutableStateFlow<List<MembershipEntity>>(emptyList())
    val memberships: StateFlow<List<MembershipEntity>> = _memberships.asStateFlow()

    internal val _customerMemberships = MutableStateFlow<List<CustomerMembershipEntity>>(emptyList())
    val customerMemberships: StateFlow<List<CustomerMembershipEntity>> = _customerMemberships.asStateFlow()

    internal val _users = MutableStateFlow<List<UserEntity>>(emptyList())
    val users: StateFlow<List<UserEntity>> = _users.asStateFlow()

    internal val _shifts = MutableStateFlow<List<ShiftEntity>>(emptyList())
    val shifts: StateFlow<List<ShiftEntity>> = _shifts.asStateFlow()

    internal val _attendanceList = MutableStateFlow<List<AttendanceEntity>>(emptyList())
    val attendanceList: StateFlow<List<AttendanceEntity>> = _attendanceList.asStateFlow()

    internal val _payrollList = MutableStateFlow<List<PayrollEntity>>(emptyList())
    val payrollList: StateFlow<List<PayrollEntity>> = _payrollList.asStateFlow()

    internal val _holdOrders = MutableStateFlow<List<HoldOrderEntity>>(emptyList())
    val holdOrders: StateFlow<List<HoldOrderEntity>> = _holdOrders.asStateFlow()

    internal val _suppliers = MutableStateFlow<List<SupplierEntity>>(emptyList())
    val suppliers: StateFlow<List<SupplierEntity>> = _suppliers.asStateFlow()

    internal val _purchases = MutableStateFlow<List<PurchaseEntity>>(emptyList())
    val purchases: StateFlow<List<PurchaseEntity>> = _purchases.asStateFlow()

    internal val _reservations = MutableStateFlow<List<ReservationEntity>>(emptyList())
    val reservations: StateFlow<List<ReservationEntity>> = _reservations.asStateFlow()

    internal val _selectedCategory = MutableStateFlow("Semua")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    internal val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    internal val _discount = MutableStateFlow(0L)
    val discount: StateFlow<Long> = _discount.asStateFlow()

    internal val _voucherDiscount = MutableStateFlow(0L)
    val voucherDiscount: StateFlow<Long> = _voucherDiscount.asStateFlow()

    internal val _appliedVoucher = MutableStateFlow("")
    val appliedVoucher: StateFlow<String> = _appliedVoucher.asStateFlow()

    internal val _selectedTable = MutableStateFlow("Take-Away")
    val selectedTable: StateFlow<String> = _selectedTable.asStateFlow()

    internal val _selectedOrderType = MutableStateFlow("Dine-In")
    val selectedOrderType: StateFlow<String> = _selectedOrderType.asStateFlow()

    internal val _pickupDate = MutableStateFlow(0L)
    val pickupDate: StateFlow<Long> = _pickupDate.asStateFlow()

    internal val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    internal val _selectedCustomer = MutableStateFlow<CustomerEntity?>(null)
    val selectedCustomer: StateFlow<CustomerEntity?> = _selectedCustomer.asStateFlow()

    internal val _pointsUsed = MutableStateFlow(0)
    val pointsUsed: StateFlow<Int> = _pointsUsed.asStateFlow()

    internal val _transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val transactions: StateFlow<List<TransactionEntity>> = _transactions.asStateFlow()

    internal val _activeShift = MutableStateFlow<ShiftEntity?>(null)
    val activeShift: StateFlow<ShiftEntity?> = _activeShift.asStateFlow()

    internal val _auditLogs = MutableStateFlow<List<AuditLogEntity>>(emptyList())
    val auditLogs: StateFlow<List<AuditLogEntity>> = _auditLogs.asStateFlow()

    internal val _isInitializing = MutableStateFlow(true)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    internal val _qrisDynamicString = MutableStateFlow("")
    val qrisDynamicString: StateFlow<String> = _qrisDynamicString.asStateFlow()

    internal val _isQrisPaid = MutableStateFlow(false)
    val isQrisPaid: StateFlow<Boolean> = _isQrisPaid.asStateFlow()

    internal val _uniqueTransferAmount = MutableStateFlow(0L)
    val uniqueTransferAmount: StateFlow<Long> = _uniqueTransferAmount.asStateFlow()

    internal val _isTransferPaid = MutableStateFlow(false)
    val isTransferPaid: StateFlow<Boolean> = _isTransferPaid.asStateFlow()

    internal val _taxPercent = MutableStateFlow(0.0)
    val taxPercent: StateFlow<Double> = _taxPercent.asStateFlow()

    internal val _serviceChargePercent = MutableStateFlow(0.0)
    val serviceChargePercent: StateFlow<Double> = _serviceChargePercent.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeKitchenTickets: StateFlow<List<KitchenTicketEntity>> = _currentBranchId.flatMapLatest { id ->
        kitchenTicketDao.getActiveTicketsByBranch(id).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val cashFlows: StateFlow<List<CashFlowEntity>> = _currentBranchId.flatMapLatest { id ->
        cashFlowDao.getCashFlowsByBranch(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val outstandingPurchases: StateFlow<List<PurchaseEntity>> = _currentBranchId.flatMapLatest { id ->
        purchaseDao.getOutstandingPurchasesByBranch(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val totalOutstandingDebt: StateFlow<Long> = _currentBranchId.flatMapLatest { id ->
        purchaseDao.getTotalOutstandingDebtByBranch(id).map { it ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val inventoryValue: StateFlow<Long> = _products.map { products ->
        products.sumOf { it.stock * it.costPrice }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val netProfit: StateFlow<Long> = combine<List<TransactionEntity>, List<CashFlowEntity>, List<ExpenseEntity>, Long>(
        _transactions, cashFlows, _expenses
    ) { txList, cashFlows, expenseList ->
        val labaKotor = txList.filter { it.status == "SUCCESS" }.sumOf { it.totalPrice - it.totalCost }
        val totalKasKeluar = cashFlows.filter { it.type == "OUT" }.sumOf { it.amount }
        val totalExpenses = expenseList.sumOf { it.amount }
        labaKotor - totalKasKeluar - totalExpenses
    }.catch { emit(0L) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val uniqueCategories: StateFlow<List<String>> = _products.map { productList ->
        val categories = productList.map { it.category }.distinct()
        listOf("Semua") + categories
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Semua"))

    val filteredProducts: StateFlow<List<ProductEntity>> = combine(_products, _searchQuery, _selectedCategory) { products, query, category ->
        products.filter { product ->
            val matchQuery = query.isBlank() || matchesProductSearch(product, query)
            val matchCategory = category == "Semua" || product.category.equals(category, ignoreCase = true)
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
            // Hapus checkActiveShift() dari sini karena saat init belum ada user yang login
            withContext(Dispatchers.Main) { _isInitializing.value = false }
        }
        
        viewModelScope.launch { branchDao.getAllBranches().catch { _allBranches.value = emptyList() }.collectLatest { _allBranches.value = it } }
        viewModelScope.launch { _currentBranchId.collectLatest { id -> productDao.getProductsByBranch(id).catch { _products.value = emptyList() }.collectLatest { _products.value = it } } }
        viewModelScope.launch { _currentBranchId.collectLatest { id -> transactionDao.getTransactionsByBranch(id).catch { _transactions.value = emptyList() }.collectLatest { _transactions.value = it } } }
        viewModelScope.launch { _currentBranchId.collectLatest { id -> userDao.getUsersByBranch(id).catch { _users.value = emptyList() }.collectLatest { _users.value = it } } }
        viewModelScope.launch { _currentBranchId.collectLatest { id -> userDao.getShiftsByBranch(id).catch { _shifts.value = emptyList() }.collectLatest { _shifts.value = it } } }
        viewModelScope.launch { _currentBranchId.collectLatest { id -> attendanceDao.getAllAttendanceByBranch(id).catch { _attendanceList.value = emptyList() }.collectLatest { _attendanceList.value = it } } }
        viewModelScope.launch { _currentBranchId.collectLatest { id -> payrollDao.getAllPayrollsByBranch(id).catch { _payrollList.value = emptyList() }.collectLatest { _payrollList.value = it } } }
        viewModelScope.launch { _currentBranchId.collectLatest { id -> stockTransferDao.getTransfersForBranch(id).catch { _transfersForCurrentBranch.value = emptyList() }.collectLatest { _transfersForCurrentBranch.value = it } } }
        viewModelScope.launch { _currentBranchId.collectLatest { id -> expenseDao.getExpensesByBranch(id).catch { _expenses.value = emptyList() }.collectLatest { _expenses.value = it } } }
        viewModelScope.launch { _currentBranchId.collectLatest { id -> stockOpnameDao.getAllOpnamesByBranch(id).catch { _stockOpnames.value = emptyList() }.collectLatest { _stockOpnames.value = it } } }
        viewModelScope.launch { _currentBranchId.collectLatest { id -> holdOrderDao.getHoldOrdersByBranch(id).catch { _holdOrders.value = emptyList() }.collectLatest { _holdOrders.value = it } } }
        viewModelScope.launch { _currentBranchId.collectLatest { id -> purchaseDao.getPurchasesByBranch(id).catch { _purchases.value = emptyList() }.collectLatest { _purchases.value = it } } }
        viewModelScope.launch { _currentBranchId.collectLatest { id -> reservationDao.getReservationsByBranch(id).catch { _reservations.value = emptyList() }.collectLatest { _reservations.value = it } } }

        viewModelScope.launch { productDao.getAllRawMaterials().catch { _rawMaterials.value = emptyList() }.collectLatest { _rawMaterials.value = it } }
        viewModelScope.launch { userDao.getAllAuditLogs().catch { _auditLogs.value = emptyList() }.collectLatest { _auditLogs.value = it } }
        viewModelScope.launch { customerDao.getAllCustomers().catch { _customers.value = emptyList() }.collectLatest { _customers.value = it } }
        viewModelScope.launch { transactionItemDao.getBestSellers().catch { _bestSellers.value = emptyList() }.collectLatest { _bestSellers.value = it } }
        viewModelScope.launch { membershipDao.getAllMemberships().catch { _memberships.value = emptyList() }.collectLatest { _memberships.value = it } }
        viewModelScope.launch { supplierDao.getAllSuppliers().catch { _suppliers.value = emptyList() }.collectLatest { _suppliers.value = it } }
        
        listenToPaymentNotifications()
    }

    fun addToCart(product: ProductEntity, variation: String = "", addQty: Double = 1.0) {
        val delta = if (addQty > 0.0) addQty else 1.0
        val currentCart = _cart.value
        val existingIndex = currentCart.indexOfFirst { it.product.id == product.id && it.selectedVariation == variation }
        val stockAvailable = getStockForVariant(product, variation).toDouble()
        val currentQtyInCart = if (existingIndex != -1) currentCart[existingIndex].effectiveQty else 0.0
        if (currentQtyInCart + delta > stockAvailable + 1e-6 && !product.isService) return
        if (existingIndex != -1) {
            val newCart = currentCart.toMutableList()
            val nextQty = currentQtyInCart + delta
            newCart[existingIndex] = newCart[existingIndex].copy(
                qtyFraction = nextQty,
                quantity = Math.ceil(nextQty - 1e-6).toInt().coerceAtLeast(1)
            )
            _cart.value = newCart
        } else {
            _cart.value = currentCart + CartItem(
                product = product,
                quantity = Math.ceil(delta - 1e-6).toInt().coerceAtLeast(1),
                qtyFraction = delta,
                selectedVariation = variation
            )
        }
    }

    fun setCartItemQuantity(product: ProductEntity, variation: String, newQty: Double) {
        if (newQty <= 0.0) {
            deleteItem(product, variation)
            return
        }
        val currentCart = _cart.value
        val existingIndex = currentCart.indexOfFirst { it.product.id == product.id && it.selectedVariation == variation }
        val stockAvailable = getStockForVariant(product, variation).toDouble()
        val clampedQty = if (!product.isService && stockAvailable > 0.0) minOf(newQty, stockAvailable) else newQty
        if (existingIndex != -1) {
            val newCart = currentCart.toMutableList()
            newCart[existingIndex] = newCart[existingIndex].copy(
                qtyFraction = clampedQty,
                quantity = Math.ceil(clampedQty - 1e-6).toInt().coerceAtLeast(1)
            )
            _cart.value = newCart
        }
    }

    fun removeFromCart(product: ProductEntity, variation: String, subQty: Double = 1.0) {
        val delta = if (subQty > 0.0) subQty else 1.0
        val currentCart = _cart.value
        val existingIndex = currentCart.indexOfFirst { it.product.id == product.id && it.selectedVariation == variation }
        if (existingIndex != -1) {
            val newCart = currentCart.toMutableList()
            val item = newCart[existingIndex]
            val remaining = item.effectiveQty - delta
            if (remaining > 1e-6) {
                newCart[existingIndex] = item.copy(
                    qtyFraction = remaining,
                    quantity = Math.ceil(remaining - 1e-6).toInt().coerceAtLeast(1)
                )
                _cart.value = newCart
            } else {
                newCart.removeAt(existingIndex)
                _cart.value = newCart
            }
        }
    }

    fun deleteItem(product: ProductEntity, variation: String) {
        _cart.value = _cart.value.filterNot { it.product.id == product.id && it.selectedVariation == variation }
    }

    fun toggleSplitSelection(product: ProductEntity, variation: String) {
        _cart.value = _cart.value.map { if (it.product.id == product.id && it.selectedVariation == variation) it.copy(isSelectedForSplit = !it.isSelectedForSplit) else it }
    }

    internal fun modifyVariantStock(variationsStr: String, target: String, delta: Int): String {
        val parts = variationsStr.split(",")
        val newParts = parts.map { part ->
            val p = part.split(":")
            if (p.size >= 2 && p[0].trim().equals(target, ignoreCase = true)) {
                val price = p[1].trim()
                val cost = p.getOrNull(2)?.trim() ?: "0"
                val currentStock = p.getOrNull(3)?.toIntOrNull() ?: 0
                val mult = p.getOrNull(4)?.trim()
                val updatedStock = (currentStock + delta).coerceAtLeast(0)
                if (!mult.isNullOrEmpty()) {
                    "${p[0]}:$price:$cost:$updatedStock:$mult"
                } else {
                    "${p[0]}:$price:$cost:$updatedStock"
                }
            } else { part }
        }
        return newParts.joinToString(",")
    }

    fun getPriceForVariant(product: ProductEntity, variation: String): Long {
        if (variation.isBlank() || product.variations.isBlank()) return product.price
        val parts = product.variations.split(",")
        for (part in parts) {
            val pieces = part.split(":")
            if (pieces.size >= 2 && pieces[0].trim().equals(variation, ignoreCase = true)) return pieces[1].trim().toLongOrNull() ?: product.price
        }
        return product.price
    }

    fun getStockForVariant(product: ProductEntity, variation: String): Int {
        if (variation.isBlank() || product.variations.isBlank()) return product.stock
        val parts = product.variations.split(",")
        for (part in parts) {
            val pieces = part.split(":")
            if (pieces.size >= 2 && pieces[0].trim().equals(variation, ignoreCase = true)) {
                if (pieces.size >= 4) return pieces[3].trim().toIntOrNull() ?: 0 else return product.stock
            }
        }
        return product.stock
    }

    fun updateCartItemNotes(product: ProductEntity, variation: String, notes: String) { _cart.value = _cart.value.map { if (it.product.id == product.id && it.selectedVariation == variation) it.copy(notes = notes) else it } }
    fun updateCartItemSerial(product: ProductEntity, variation: String, serial: String) { _cart.value = _cart.value.map { if (it.product.id == product.id && it.selectedVariation == variation) it.copy(serialNumber = serial) else it } }
    fun updateCartItemStaff(product: ProductEntity, variation: String, staff: String) { _cart.value = _cart.value.map { if (it.product.id == product.id && it.selectedVariation == variation) it.copy(assignedStaff = staff) else it } }

    fun setCategory(category: String) { _selectedCategory.value = category }
    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setOrderType(type: String) { _selectedOrderType.value = type }
    fun setTable(table: String) { _selectedTable.value = table }
    fun setPickupDate(date: Long) { _pickupDate.value = date }
    fun setDiscount(amount: Long) { _discount.value = amount }
    fun setTaxPercent(tax: Double) { _taxPercent.value = tax }
    fun setServiceChargePercent(sc: Double) { _serviceChargePercent.value = sc }

    fun openShift(user: UserEntity, modalAwal: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newShift = ShiftEntity(
                    userId = user.id, userName = user.name, startTime = System.currentTimeMillis(),
                    openingCash = modalAwal, status = "OPEN", branchId = _currentBranchId.value
                )
                userDao.insertShift(newShift)
                checkActiveShift(user.id) // FIX: Passing userId
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun logCashDrawerOpen(user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "BUKA LACI KASIR", actionType = "SYSTEM"))
        }
    }

    internal fun checkActiveShift(userId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // FIX: Menggunakan getActiveShiftForUser agar tidak menumpang shift orang lain
                val active = userDao.getActiveShiftForUser(_currentBranchId.value, userId)
                withContext(Dispatchers.Main) { _activeShift.value = active }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun processBarcodeScan(rawCode: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val code = rawCode.trim()
        if (code.isEmpty()) {
            onResult(false, "Barcode kosong.")
            return
        }
        val currentList = _products.value
        var scannedVariantName = ""
        val matched = currentList.firstOrNull { prod ->
            val rawBarcode = prod.barcode.trim()
            val actualBarcode = if (rawBarcode.contains("|")) rawBarcode.substringAfter("|").trim() else rawBarcode
            val skuPart = if (rawBarcode.contains("|")) rawBarcode.substringBefore("|").trim() else rawBarcode
            val matchesProductLevel = rawBarcode.equals(code, ignoreCase = true) ||
                actualBarcode.equals(code, ignoreCase = true) ||
                skuPart.equals(code, ignoreCase = true)
            if (matchesProductLevel) {
                true
            } else if (prod.variations.isNotBlank() && !prod.variations.contains("|")) {
                val matchedPart = prod.variations.split(",").firstOrNull { part ->
                    val vPieces = part.split(":")
                    val vCode = vPieces.getOrNull(5)?.trim().orEmpty()
                    vCode.isNotEmpty() && vCode.equals(code, ignoreCase = true)
                }
                if (matchedPart != null) {
                    scannedVariantName = matchedPart.split(":").firstOrNull()?.trim().orEmpty()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        if (matched == null) {
            onResult(false, "Produk dengan barcode \"$code\" tidak ditemukan.")
            return
        }

        val defaultVariation = if (scannedVariantName.isNotBlank()) {
            scannedVariantName
        } else if (matched.variations.isNotBlank() && !matched.variations.contains("|")) {
            matched.variations.split(",").firstOrNull()?.split(":")?.firstOrNull()?.trim().orEmpty()
        } else {
            ""
        }

        val currentCart = _cart.value
        val existingIdx = currentCart.indexOfFirst {
            it.product.id == matched.id && it.selectedVariation == defaultVariation
        }
        val currentQtyInCart = if (existingIdx != -1) currentCart[existingIdx].quantity else 0
        val availableStock = getStockForVariant(matched, defaultVariation)

        if (!matched.isService && currentQtyInCart + 1 > availableStock) {
            onResult(false, "Stok ${matched.name} tidak mencukupi (tersisa $availableStock).")
            return
        }

        addToCart(matched, defaultVariation)
        _searchQuery.value = ""
        val updatedQty = currentQtyInCart + 1
        onResult(true, "${matched.name} masuk keranjang (Qty: $updatedQty)")
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