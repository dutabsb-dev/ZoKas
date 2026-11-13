package com.karyaku.kasir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    internal val database: AppDatabase
) : ViewModel() {

    // DAO REFERENCES
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
    internal val kitchenTicketDao = database.kitchenTicketDao() // MODUL B (KDS)

    // STATE MULTI-CABANG
    internal val _currentBranchId = MutableStateFlow(1L)
    val currentBranchId: StateFlow<Long> = _currentBranchId.asStateFlow()

    internal val _isBranchSwitchLocked = MutableStateFlow(false)
    val isBranchSwitchLocked: StateFlow<Boolean> = _isBranchSwitchLocked.asStateFlow()

    internal val _allBranches = MutableStateFlow<List<BranchEntity>>(emptyList())
    val allBranches: StateFlow<List<BranchEntity>> = _allBranches.asStateFlow()

    val currentBranch: StateFlow<BranchEntity?> = combine(_currentBranchId, _allBranches) { id, branches ->
        branches.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // STATE TRANSFER STOK
    internal val _stockTransfers = MutableStateFlow<List<StockTransferEntity>>(emptyList())
    val stockTransfers: StateFlow<List<StockTransferEntity>> = _stockTransfers.asStateFlow()

    internal val _transfersForCurrentBranch = MutableStateFlow<List<StockTransferEntity>>(emptyList())
    val transfersForCurrentBranch: StateFlow<List<StockTransferEntity>> = _transfersForCurrentBranch.asStateFlow()

    // STATE PRODUK & BAHAN BAKU
    internal val _products = MutableStateFlow<List<ProductEntity>>(emptyList())
    val products: StateFlow<List<ProductEntity>> = _products.asStateFlow()

    internal val _rawMaterials = MutableStateFlow<List<RawMaterialEntity>>(emptyList())
    val rawMaterials: StateFlow<List<RawMaterialEntity>> = _rawMaterials.asStateFlow()

    // STATE KAS & KEUANGAN
    val cashFlows: StateFlow<List<CashFlowEntity>> = cashFlowDao.getAllCashFlows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    internal val _expenses = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    val expenses: StateFlow<List<ExpenseEntity>> = _expenses.asStateFlow()

    internal val _stockOpnames = MutableStateFlow<List<StockOpnameEntity>>(emptyList())
    val stockOpnames: StateFlow<List<StockOpnameEntity>> = _stockOpnames.asStateFlow()

    val inventoryValue: StateFlow<Long> = _products.map { products ->
        products.sumOf { it.stock * it.costPrice }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // STATE BEST SELLER
    internal val _bestSellers = MutableStateFlow<List<BestSellerData>>(emptyList())
    val bestSellers: StateFlow<List<BestSellerData>> = _bestSellers.asStateFlow()

    // STATE PELANGGAN & MEMBERSHIP
    internal val _customers = MutableStateFlow<List<CustomerEntity>>(emptyList())
    val customers: StateFlow<List<CustomerEntity>> = _customers.asStateFlow()

    internal val _memberships = MutableStateFlow<List<MembershipEntity>>(emptyList())
    val memberships: StateFlow<List<MembershipEntity>> = _memberships.asStateFlow()

    internal val _customerMemberships = MutableStateFlow<List<CustomerMembershipEntity>>(emptyList())
    val customerMemberships: StateFlow<List<CustomerMembershipEntity>> = _customerMemberships.asStateFlow()

    // STATE USER & SHIFT
    internal val _users = MutableStateFlow<List<UserEntity>>(emptyList())
    val users: StateFlow<List<UserEntity>> = _users.asStateFlow()

    internal val _shifts = MutableStateFlow<List<ShiftEntity>>(emptyList())
    val shifts: StateFlow<List<ShiftEntity>> = _shifts.asStateFlow()

    // STATE HOLD ORDER (PARKIR MEJA)
    internal val _holdOrders = MutableStateFlow<List<HoldOrderEntity>>(emptyList())
    val holdOrders: StateFlow<List<HoldOrderEntity>> = _holdOrders.asStateFlow()

    // STATE SUPPLIER & PURCHASE
    internal val _suppliers = MutableStateFlow<List<SupplierEntity>>(emptyList())
    val suppliers: StateFlow<List<SupplierEntity>> = _suppliers.asStateFlow()

    internal val _purchases = MutableStateFlow<List<PurchaseEntity>>(emptyList())
    val purchases: StateFlow<List<PurchaseEntity>> = _purchases.asStateFlow()

    val outstandingPurchases: StateFlow<List<PurchaseEntity>> = purchaseDao.getOutstandingPurchases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalOutstandingDebt: StateFlow<Long> = purchaseDao.getTotalOutstandingDebt()
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // STATE RESERVASI
    internal val _reservations = MutableStateFlow<List<ReservationEntity>>(emptyList())
    val reservations: StateFlow<List<ReservationEntity>> = _reservations.asStateFlow()

    // STATE FILTER PRODUK
    internal val _selectedCategory = MutableStateFlow("Semua")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    internal val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // STATE DISKON & VOUCHER
    internal val _discount = MutableStateFlow(0L)
    val discount: StateFlow<Long> = _discount.asStateFlow()

    internal val _voucherDiscount = MutableStateFlow(0L)
    val voucherDiscount: StateFlow<Long> = _voucherDiscount.asStateFlow()

    internal val _appliedVoucher = MutableStateFlow("")
    val appliedVoucher: StateFlow<String> = _appliedVoucher.asStateFlow()

    // STATE MEJA & TIPE PESANAN
    internal val _selectedTable = MutableStateFlow("Take-Away")
    val selectedTable: StateFlow<String> = _selectedTable.asStateFlow()

    internal val _selectedOrderType = MutableStateFlow("Dine-In")
    val selectedOrderType: StateFlow<String> = _selectedOrderType.asStateFlow()

    internal val _pickupDate = MutableStateFlow(0L)
    val pickupDate: StateFlow<Long> = _pickupDate.asStateFlow()

    // STATE KERANJANG
    internal val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    internal val _selectedCustomer = MutableStateFlow<CustomerEntity?>(null)
    val selectedCustomer: StateFlow<CustomerEntity?> = _selectedCustomer.asStateFlow()

    internal val _pointsUsed = MutableStateFlow(0)
    val pointsUsed: StateFlow<Int> = _pointsUsed.asStateFlow()

    // STATE TRANSAKSI & SHIFT AKTIF
    internal val _transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val transactions: StateFlow<List<TransactionEntity>> = _transactions.asStateFlow()

    internal val _activeShift = MutableStateFlow<ShiftEntity?>(null)
    val activeShift: StateFlow<ShiftEntity?> = _activeShift.asStateFlow()

    internal val _auditLogs = MutableStateFlow<List<AuditLogEntity>>(emptyList())
    val auditLogs: StateFlow<List<AuditLogEntity>> = _auditLogs.asStateFlow()

    // STATE INIT
    internal val _isInitializing = MutableStateFlow(true)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    // STATE QRIS & TRANSFER MANUAL
    internal val _qrisDynamicString = MutableStateFlow("")
    val qrisDynamicString: StateFlow<String> = _qrisDynamicString.asStateFlow()

    internal val _isQrisPaid = MutableStateFlow(false)
    val isQrisPaid: StateFlow<Boolean> = _isQrisPaid.asStateFlow()

    internal val _uniqueTransferAmount = MutableStateFlow(0L)
    val uniqueTransferAmount: StateFlow<Long> = _uniqueTransferAmount.asStateFlow()

    internal val _isTransferPaid = MutableStateFlow(false)
    val isTransferPaid: StateFlow<Boolean> = _isTransferPaid.asStateFlow()

    // STATE PAJAK & SERVICE CHARGE
    internal val _taxPercent = MutableStateFlow(0.0)
    val taxPercent: StateFlow<Double> = _taxPercent.asStateFlow()

    internal val _serviceChargePercent = MutableStateFlow(0.0)
    val serviceChargePercent: StateFlow<Double> = _serviceChargePercent.asStateFlow()

    // COMPUTED FLOWS
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

    // INIT BLOCK
    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                checkActiveShift()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) { _isInitializing.value = false }
            }
        }

        // Cabang global
        viewModelScope.launch {
            branchDao.getAllBranches().catch { _allBranches.value = emptyList() }.collectLatest {
                _allBranches.value = it
            }
        }

        // Produk per cabang
        viewModelScope.launch {
            _currentBranchId.collectLatest { branchId ->
                productDao.getProductsByBranch(branchId)
                    .catch { _products.value = emptyList() }
                    .collectLatest { _products.value = it }
            }
        }

        // Transaksi per cabang
        viewModelScope.launch {
            _currentBranchId.collectLatest { branchId ->
                transactionDao.getTransactionsByBranch(branchId)
                    .catch { _transactions.value = emptyList() }
                    .collectLatest { _transactions.value = it }
            }
        }

        // User per cabang
        viewModelScope.launch {
            _currentBranchId.collectLatest { branchId ->
                userDao.getUsersByBranch(branchId)
                    .catch { _users.value = emptyList() }
                    .collectLatest { _users.value = it }
            }
        }

        // Shift per cabang
        viewModelScope.launch {
            _currentBranchId.collectLatest { branchId ->
                userDao.getShiftsByBranch(branchId)
                    .catch { _shifts.value = emptyList() }
                    .collectLatest { _shifts.value = it }
            }
        }

        // Transfer stok — semua (global)
        viewModelScope.launch {
            stockTransferDao.getAllTransfers()
                .catch { _stockTransfers.value = emptyList() }
                .collectLatest { _stockTransfers.value = it }
        }

        // Transfer stok — per cabang aktif
        viewModelScope.launch {
            _currentBranchId.collectLatest { branchId ->
                stockTransferDao.getTransfersForBranch(branchId)
                    .catch { _transfersForCurrentBranch.value = emptyList() }
                    .collectLatest { _transfersForCurrentBranch.value = it }
            }
        }

        // Global: raw materials, audit logs, customers, hold orders, best sellers, memberships, suppliers, purchases, reservations, expenses, stock opnames.
        viewModelScope.launch { productDao.getAllRawMaterials().catch { _rawMaterials.value = emptyList() }.collectLatest { _rawMaterials.value = it } }
        viewModelScope.launch { userDao.getAllAuditLogs().catch { _auditLogs.value = emptyList() }.collectLatest { _auditLogs.value = it } }
        viewModelScope.launch { customerDao.getAllCustomers().catch { _customers.value = emptyList() }.collectLatest { _customers.value = it } }
        viewModelScope.launch { transactionItemDao.getBestSellers().catch { _bestSellers.value = emptyList() }.collectLatest { _bestSellers.value = it } }
        viewModelScope.launch { holdOrderDao.getAllHoldOrders().catch { _holdOrders.value = emptyList() }.collectLatest { _holdOrders.value = it } }
        viewModelScope.launch { membershipDao.getAllMemberships().catch { _memberships.value = emptyList() }.collectLatest { _memberships.value = it } }
        viewModelScope.launch { supplierDao.getAllSuppliers().catch { _suppliers.value = emptyList() }.collectLatest { _suppliers.value = it } }
        viewModelScope.launch { purchaseDao.getAllPurchases().catch { _purchases.value = emptyList() }.collectLatest { _purchases.value = it } }
        viewModelScope.launch { reservationDao.getAllReservations().catch { _reservations.value = emptyList() }.collectLatest { _reservations.value = it } }
        viewModelScope.launch { expenseDao.getAllExpenses().catch { _expenses.value = emptyList() }.collectLatest { _expenses.value = it } }
        viewModelScope.launch { stockOpnameDao.getAllOpnames().catch { _stockOpnames.value = emptyList() }.collectLatest { _stockOpnames.value = it } }

        // Payment notification listener (QRIS & Transfer auto-detect)
        viewModelScope.launch {
            PaymentNotificationListener.paymentEvents.collect { notificationText ->
                if (_qrisDynamicString.value.isNotEmpty() && !_isQrisPaid.value) {
                    _isQrisPaid.value = true
                    userDao.insertAuditLog(AuditLogEntity(
                        userId = 0, userName = "SYSTEM",
                        action = "QRIS AUTO-DETECT: Pembayaran masuk terdeteksi.",
                        actionType = "TRANSACTION"
                    ))
                }
                if (_uniqueTransferAmount.value > 0 && !_isTransferPaid.value) {
                    val nominalStr = _uniqueTransferAmount.value.toString()
                    if (notificationText.contains(nominalStr)) {
                        _isTransferPaid.value = true
                        userDao.insertAuditLog(AuditLogEntity(
                            userId = 0, userName = "SYSTEM",
                            action = "TRANSFER AUTO-DETECT: Dana masuk Rp $nominalStr terdeteksi.",
                            actionType = "TRANSACTION"
                        ))
                    }
                }
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