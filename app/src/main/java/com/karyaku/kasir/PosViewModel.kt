package com.karyaku.kasir

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    internal val kitchenTicketDao = database.kitchenTicketDao()
    internal val attendanceDao = database.attendanceDao()
    internal val payrollDao = database.payrollDao()

    // STATE KDS (KITCHEN DISPLAY SYSTEM)
    val activeKitchenTickets: StateFlow<List<KitchenTicketEntity>> = kitchenTicketDao.getActiveTickets()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // STATE QRIS & TRANSFER
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

    // INIT BLOCK (Sama seperti aslinya)
    init {
        viewModelScope.launch(Dispatchers.IO) {
            try { checkActiveShift() } catch (e: Exception) { e.printStackTrace() } 
            finally { withContext(Dispatchers.Main) { _isInitializing.value = false } }
        }
        viewModelScope.launch { branchDao.getAllBranches().catch { _allBranches.value = emptyList() }.collectLatest { _allBranches.value = it } }
        viewModelScope.launch { _currentBranchId.collectLatest { id -> productDao.getProductsByBranch(id).catch { _products.value = emptyList() }.collectLatest { _products.value = it } } }
        viewModelScope.launch { _currentBranchId.collectLatest { id -> transactionDao.getTransactionsByBranch(id).catch { _transactions.value = emptyList() }.collectLatest { _transactions.value = it } } }
        viewModelScope.launch { _currentBranchId.collectLatest { id -> userDao.getUsersByBranch(id).catch { _users.value = emptyList() }.collectLatest { _users.value = it } } }
        viewModelScope.launch { _currentBranchId.collectLatest { id -> userDao.getShiftsByBranch(id).catch { _shifts.value = emptyList() }.collectLatest { _shifts.value = it } } }
        
        viewModelScope.launch { productDao.getAllRawMaterials().catch { _rawMaterials.value = emptyList() }.collectLatest { _rawMaterials.value = it } }
        viewModelScope.launch { userDao.getAllAuditLogs().catch { _auditLogs.value = emptyList() }.collectLatest { _auditLogs.value = it } }
        viewModelScope.launch { customerDao.getAllCustomers().catch { _customers.value = emptyList() }.collectLatest { _customers.value = it } }
        viewModelScope.launch { holdOrderDao.getAllHoldOrders().catch { _holdOrders.value = emptyList() }.collectLatest { _holdOrders.value = it } }
        viewModelScope.launch { membershipDao.getAllMemberships().catch { _memberships.value = emptyList() }.collectLatest { _memberships.value = it } }
        viewModelScope.launch { supplierDao.getAllSuppliers().catch { _suppliers.value = emptyList() }.collectLatest { _suppliers.value = it } }
        viewModelScope.launch { purchaseDao.getAllPurchases().catch { _purchases.value = emptyList() }.collectLatest { _purchases.value = it } }
        viewModelScope.launch { reservationDao.getAllReservations().catch { _reservations.value = emptyList() }.collectLatest { _reservations.value = it } }
        viewModelScope.launch { expenseDao.getAllExpenses().catch { _expenses.value = emptyList() }.collectLatest { _expenses.value = it } }
    }

    // ========================================================================
    // LOGIKA VARIAN & KERANJANG (YANG BARU)
    // ========================================================================

    // Mengambil harga sesuai varian
    fun getPriceForVariant(product: ProductEntity, variation: String): Long {
        if (variation.isBlank() || product.variations.isBlank()) return product.price
        val parts = product.variations.split(",")
        for (part in parts) {
            val pieces = part.split(":")
            if (pieces.size >= 2 && pieces[0].trim().equals(variation, ignoreCase = true)) {
                return pieces[1].trim().toLongOrNull() ?: product.price
            }
        }
        return product.price
    }

    // Mengambil stok sesuai varian
    fun getStockForVariant(product: ProductEntity, variation: String): Int {
        if (variation.isBlank() || product.variations.isBlank()) return product.stock
        val parts = product.variations.split(",")
        for (part in parts) {
            val pieces = part.split(":")
            // Format baru: Nama:HargaJual:HargaBeli:Stok
            if (pieces.size >= 4 && pieces[0].trim().equals(variation, ignoreCase = true)) {
                return pieces[3].trim().toIntOrNull() ?: 0
            }
        }
        return 0
    }

    // Tambah ke keranjang
    fun addToCart(product: ProductEntity, variation: String = "") {
        val currentCart = _cart.value
        val existingIndex = currentCart.indexOfFirst { it.product.id == product.id && it.selectedVariation == variation }
        
        val stockAvailable = getStockForVariant(product, variation)
        val currentQtyInCart = if (existingIndex != -1) currentCart[existingIndex].quantity else 0

        if (currentQtyInCart + 1 > stockAvailable) {
            // Opsional: Bisa diganti dengan Toast jika ada context, tapi di sini kita diamkan saja
            return 
        }

        if (existingIndex != -1) {
            val newCart = currentCart.toMutableList()
            newCart[existingIndex] = newCart[existingIndex].copy(quantity = newCart[existingIndex].quantity + 1)
            _cart.value = newCart
        } else {
            _cart.value = currentCart + CartItem(product = product, selectedVariation = variation)
        }
    }

    // Kurangi dari keranjang
    fun removeFromCart(product: ProductEntity, variation: String) {
        val currentCart = _cart.value
        val existingIndex = currentCart.indexOfFirst { it.product.id == product.id && it.selectedVariation == variation }
        if (existingIndex != -1) {
            val newCart = currentCart.toMutableList()
            val item = newCart[existingIndex]
            if (item.quantity > 1) {
                newCart[existingIndex] = item.copy(quantity = item.quantity - 1)
                _cart.value = newCart
            } else {
                newCart.removeAt(existingIndex)
                _cart.value = newCart
            }
        }
    }

    // Hapus dari keranjang
    fun deleteItem(product: ProductEntity, variation: String) {
        _cart.value = _cart.value.filterNot { it.product.id == product.id && it.selectedVariation == variation }
    }

    fun toggleSplitSelection(product: ProductEntity, variation: String) {
        _cart.value = _cart.value.map { 
            if (it.product.id == product.id && it.selectedVariation == variation) {
                it.copy(isSelectedForSplit = !it.isSelectedForSplit)
            } else it
        }
    }

    // ========================================================================
    // PROSES PEMBAYARAN (PENGURANGAN STOK VARIAN)
    // ========================================================================
    fun processPayment(
        cashReceived: Long,
        userId: Long,
        userName: String,
        isSplitBill: Boolean,
        method: String,
        maxDiscountCap: Long,
        birthdayDiscountPercent: Double,
        onSuccess: (TransactionEntity) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cartToProcess = if (isSplitBill) _cart.value.filter { it.isSelectedForSplit } else _cart.value
                if (cartToProcess.isEmpty()) return@launch

                val subtotal = cartToProcess.sumOf { getPriceForVariant(it.product, it.selectedVariation) * it.quantity }
                val totalPrice = subtotal // Bisa disesuaikan dengan pajak/diskon jika perlu

                val newTransaction = TransactionEntity(
                    timestamp = System.currentTimeMillis(),
                    subtotal = subtotal,
                    discount = 0L,
                    taxAmount = 0L,
                    serviceCharge = 0L,
                    totalPrice = totalPrice,
                    totalCost = cartToProcess.sumOf { it.product.costPrice * it.quantity },
                    cashReceived = cashReceived,
                    changeAmount = if (cashReceived > totalPrice) cashReceived - totalPrice else 0L,
                    paymentMethod = method,
                    status = "SUCCESS",
                    cashierName = userName,
                    customerName = _selectedCustomer.value?.name ?: "Pelanggan Umum",
                    orderType = _selectedOrderType.value,
                    tableName = _selectedTable.value,
                    branchId = _currentBranchId.value
                )

                // 1. Simpan Transaksi
                val transactionId = transactionDao.insertTransaction(newTransaction)
                val savedTransaction = newTransaction.copy(id = transactionId)

                // 2. Simpan Item & POTONG STOK
                for (cartItem in cartToProcess) {
                    val itemEntity = TransactionItemEntity(
                        transactionId = transactionId,
                        productId = cartItem.product.id,
                        productName = cartItem.product.name,
                        quantity = cartItem.quantity,
                        price = getPriceForVariant(cartItem.product, cartItem.selectedVariation),
                        costPrice = cartItem.product.costPrice,
                        subtotal = getPriceForVariant(cartItem.product, cartItem.selectedVariation) * cartItem.quantity,
                        notes = cartItem.notes,
                        variation = cartItem.selectedVariation,
                        serialNumber = cartItem.serialNumber,
                        assignedStaff = cartItem.assignedStaff
                    )
                    transactionItemDao.insertTransactionItem(itemEntity)

                    // --- LOGIKA POTONG STOK (VARIAN ATAU BIASA) ---
                    val freshProduct = productDao.getProductById(cartItem.product.id)
                    if (freshProduct != null) {
                        if (cartItem.selectedVariation.isNotBlank()) {
                            // Jika ada varian, potong stok di dalam string varian
                            val newVariationStr = deductVariantStock(freshProduct.variations, cartItem.selectedVariation, cartItem.quantity)
                            productDao.updateProduct(freshProduct.copy(variations = newVariationStr))
                        } else {
                            // Jika tidak ada varian, potong stok utama
                            val newStock = (freshProduct.stock - cartItem.quantity).coerceAtLeast(0)
                            productDao.updateProduct(freshProduct.copy(stock = newStock))
                        }
                    }
                }

                // 3. Bersihkan Keranjang
                withContext(Dispatchers.Main) {
                    if (isSplitBill) {
                        _cart.value = _cart.value.filterNot { it.isSelectedForSplit }
                    } else {
                        _cart.value = emptyList()
                    }
                    onSuccess(savedTransaction)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Fungsi pembantu untuk memotong stok di dalam string varian
    private fun deductVariantStock(variationsStr: String, targetVariantName: String, deductQty: Int): String {
        if (variationsStr.isBlank()) return ""
        val parts = variationsStr.split(",")
        val newParts = parts.map { part ->
            val pieces = part.split(":")
            if (pieces.size >= 4 && pieces[0].trim().equals(targetVariantName, ignoreCase = true)) {
                val currentStock = pieces[3].trim().toIntOrNull() ?: 0
                val newStock = (currentStock - deductQty).coerceAtLeast(0)
                // Format: Nama:HargaJual:HargaBeli:Stok
                "${pieces[0]}:${pieces[1]}:${pieces[2]}:$newStock"
            } else {
                part
            }
        }
        return newParts.joinToString(",")
    }

    // ========================================================================
    // FUNGSI CETAK STRUK (Sama seperti sebelumnya)
    // ========================================================================
    fun printReceipt(context: Context, transaction: TransactionEntity, settingsManager: SettingsManager) {
        val macAddress = settingsManager.printerMacAddress
        if (macAddress.isNullOrBlank()) {
            Toast.makeText(context, "Printer belum diatur di Pengaturan", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Karena ini auto-print, kita ambil data keranjang yang terakhir
        val receiptItems = _cart.value.map { cartItem ->
            ReceiptItem(
                name = cartItem.product.name,
                qty = cartItem.quantity,
                price = getPriceForVariant(cartItem.product, cartItem.selectedVariation).toDouble(),
                variation = cartItem.selectedVariation + (if (cartItem.notes.isNotBlank()) " | ${cartItem.notes}" else "")
            )
        }

        val strukText = ReceiptFormatter.buildReceipt(
            storeName = settingsManager.storeName ?: "ZoKas",
            storeAddress = settingsManager.storeAddress ?: "",
            storePhone = settingsManager.storePhone ?: "",
            storeFooter = settingsManager.storeFooter ?: "Terima kasih!",
            transactionId = transaction.id,
            dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(transaction.timestamp)),
            orderType = transaction.orderType ?: "Dine-In",
            cashierName = transaction.cashierName ?: "Kasir",
            customerName = transaction.customerName ?: "Pelanggan Umum",
            items = receiptItems,
            subtotal = transaction.totalPrice.toDouble(),
            total = transaction.totalPrice.toDouble(),
            payment = transaction.totalPrice.toDouble(),
            change = 0.0
        )

        viewModelScope.launch(Dispatchers.IO) {
            val printerManager = BluetoothPrinterManager(context)
            val outputStream = java.io.ByteArrayOutputStream()
            outputStream.write(EscPosCommands.INIT)
            outputStream.write(strukText.toByteArray(charset("GBK")))
            outputStream.write(EscPosCommands.FEED_3)
            outputStream.write(EscPosCommands.CUT_PAPER)

            val success = printerManager.printData(macAddress, outputStream.toByteArray())
            withContext(Dispatchers.Main) {
                if (success) Toast.makeText(context, "Struk dicetak!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ========================================================================
    // FUNGSI-FUNGSI PENDUKUNG LAINNYA
    // ========================================================================
    fun setCategory(category: String) { _selectedCategory.value = category }
    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setOrderType(type: String) { _selectedOrderType.value = type }
    fun setTable(table: String) { _selectedTable.value = table }
    fun setPickupDate(date: Long) { _pickupDate.value = date }
    fun setDiscount(amount: Long) { _discount.value = amount }
    fun setCustomer(customer: CustomerEntity?) { _selectedCustomer.value = customer }
    fun redeemPoints(points: Int) { _pointsUsed.value = points }
    fun setTaxPercent(tax: Double) { _taxPercent.value = tax }
    fun setServiceChargePercent(sc: Double) { _serviceChargePercent.value = sc }
    
    fun updateCartItemNotes(product: ProductEntity, variation: String, notes: String) {
        _cart.value = _cart.value.map {
            if (it.product.id == product.id && it.selectedVariation == variation) it.copy(notes = notes) else it
        }
    }
    fun updateCartItemSerial(product: ProductEntity, variation: String, serial: String) {
        _cart.value = _cart.value.map {
            if (it.product.id == product.id && it.selectedVariation == variation) it.copy(serialNumber = serial) else it
        }
    }
    fun updateCartItemStaff(product: ProductEntity, variation: String, staff: String) {
        _cart.value = _cart.value.map {
            if (it.product.id == product.id && it.selectedVariation == variation) it.copy(assignedStaff = staff) else it
        }
    }

    // Placeholder untuk fungsi yang mungkin dipanggil di PosScreen tapi tidak ada implementasi detailnya
    fun saveHoldOrder(name: String) {}
    fun resumeHoldOrder(order: HoldOrderEntity) {}
    fun mergeOrder(tableA: String, tableB: String) {}
    fun logCashDrawerOpen(user: UserEntity) {}
    fun openShift(user: UserEntity, modal: Long) {}
    fun closeShift(user: UserEntity, physical: Long, note: String, onDone: (ShiftEntity) -> Unit) {}
    fun addCashFlow(type: String, amount: Long, note: String, user: UserEntity) {}
    fun addCustomer(name: String, phone: String, birthDate: Long) {}
    fun applyVoucher(code: String, callback: (Boolean, String) -> Unit) {}
    fun generateQrisPayment(amount: Long, payload: String) {}
    fun generateTransferPayment(amount: Long) {}
    fun resetQrisPayment() { _isQrisPaid.value = false; _qrisDynamicString.value = "" }
    fun resetTransferPayment() { _isTransferPaid.value = false; _uniqueTransferAmount.value = 0L }
    fun applyMembershipToCart(cartItem: CartItem, cm: CustomerMembershipEntity) {}
    fun removeMembershipFromCart(cartItem: CartItem) {}
    fun processBarcodeScan(barcode: String, callback: (Boolean, String) -> Unit) {}
    
    // Untuk ReceiptDialog
    fun getTransactionItems(transactionId: Long): Flow<List<TransactionItemEntity>> {
        return transactionItemDao.getItemsByTransaction(transactionId)
    }

    private fun checkActiveShift() {}
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