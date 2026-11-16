package com.karyaku.kasir

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.karyaku.kasir.data.local.CartItem
import com.karyaku.kasir.data.local.CustomerMembershipEntity
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.TransactionEntity
import com.karyaku.kasir.data.local.UserEntity
import com.karyaku.kasir.data.local.ShiftEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    viewModel: PosViewModel,
    settingsManager: SettingsManager,
    currentUser: UserEntity,
    onLogout: () -> Unit,
    showCfd: Boolean,
    onDismissCfd: () -> Unit,
    actionTrigger: String? = null,
    onActionHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isPhone = configuration.screenWidthDp < 600
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    val searchFocusRequester = remember { FocusRequester() }

    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val discount by viewModel.discount.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val selectedCustomer by viewModel.selectedCustomer.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val filteredProducts by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val categories by viewModel.uniqueCategories.collectAsStateWithLifecycle()
    val pointsUsed by viewModel.pointsUsed.collectAsStateWithLifecycle()
    val holdOrders by viewModel.holdOrders.collectAsStateWithLifecycle()
    val occupiedTables by viewModel.occupiedTables.collectAsStateWithLifecycle()
    val voucherDiscount by viewModel.voucherDiscount.collectAsStateWithLifecycle()
    val appliedVoucher by viewModel.appliedVoucher.collectAsStateWithLifecycle()
    val selectedTable by viewModel.selectedTable.collectAsStateWithLifecycle()
    val selectedOrderType by viewModel.selectedOrderType.collectAsStateWithLifecycle()
    val pickupDate by viewModel.pickupDate.collectAsStateWithLifecycle()
    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val cashFlows by viewModel.cashFlows.collectAsStateWithLifecycle()
    val taxPercent by viewModel.taxPercent.collectAsStateWithLifecycle()
    val serviceChargePercent by viewModel.serviceChargePercent.collectAsStateWithLifecycle()
    val customerMemberships by viewModel.customerMemberships.collectAsStateWithLifecycle()

    val qrisDynamicString by viewModel.qrisDynamicString.collectAsStateWithLifecycle()
    val isQrisPaid by viewModel.isQrisPaid.collectAsStateWithLifecycle()
    val uniqueTransferAmount by viewModel.uniqueTransferAmount.collectAsStateWithLifecycle()
    val isTransferPaid by viewModel.isTransferPaid.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.setTaxPercent(settingsManager.taxPercent)
        viewModel.setServiceChargePercent(settingsManager.serviceChargePercent)
    }

    var showCartOnPhone by rememberSaveable { mutableStateOf(false) }
    var showPaymentSheet by remember { mutableStateOf(false) }
    var showProductForm by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<ProductEntity?>(null) }
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }
    var productToRestock by remember { mutableStateOf<ProductEntity?>(null) }
    var productToWaste by remember { mutableStateOf<ProductEntity?>(null) }
    var showActionDialogFor by remember { mutableStateOf<ProductEntity?>(null) }
    var productToEditNotes by remember { mutableStateOf<ProductEntity?>(null) }
    var productToSelectVariant by remember { mutableStateOf<ProductEntity?>(null) }
    var productToSelectMatrix by remember { mutableStateOf<ProductEntity?>(null) }
    var productToEditSerial by remember { mutableStateOf<ProductEntity?>(null) }
    var productToAssignStaff by remember { mutableStateOf<ProductEntity?>(null) }
    var productToManageBom by remember { mutableStateOf<ProductEntity?>(null) }
    var completedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    var showOpenShiftDialog by remember { mutableStateOf(false) }
    var showCloseShiftDialog by remember { mutableStateOf(false) }
    var showCashFlowDialog by remember { mutableStateOf(false) }
    var showDiscountDialog by remember { mutableStateOf(false) }
    var showCustomerDialog by remember { mutableStateOf(false) }
    var modalAwal by remember { mutableStateOf("") }
    var showDrawerConfirm by remember { mutableStateOf(false) }
    var showPinForDiscount by remember { mutableStateOf(false) }
    var showShiftReport by remember { mutableStateOf<ShiftEntity?>(null) }
    var showMergeOrderDialog by remember { mutableStateOf(false) }
    var showPrinterErrorDialog by remember { mutableStateOf(false) }
    var showHoldDialog by remember { mutableStateOf(false) }
    var showTableSelector by remember { mutableStateOf(false) }
    var showVoucherDialog by remember { mutableStateOf(false) }
    var isSplitBillMode by remember { mutableStateOf(false) }

    LaunchedEffect(actionTrigger) {
        if (actionTrigger != null) {
            when (actionTrigger) {
                "MERGE" -> showMergeOrderDialog = true
                "DRAWER" -> showDrawerConfirm = true
                "HOLD" -> showHoldDialog = true
                "CASHFLOW" -> showCashFlowDialog = true
                "CLOSE_SHIFT" -> showCloseShiftDialog = true
            }
            onActionHandled()
        }
    }

    val subtotal = cart.sumOf {
        if (it.usedMembershipId != null) 0L
        else if (it.product.wholesaleMinQty > 0 && it.quantity >= it.product.wholesaleMinQty && it.product.wholesalePrice > 0) it.product.wholesalePrice * it.quantity
        else viewModel.getPriceForVariant(it.product, it.selectedVariation) * it.quantity
    }
    val happyHourDiscountAmount = 0L
    var tierDiscount = 0L
    var promoDiscountUI = 0L
    var totalDiscount = discount + happyHourDiscountAmount + tierDiscount + promoDiscountUI
    if (settingsManager.maxDiscountCap > 0 && totalDiscount > settingsManager.maxDiscountCap) totalDiscount = settingsManager.maxDiscountCap

    val taxAmount = ((subtotal - totalDiscount - voucherDiscount) * (taxPercent / 100.0)).toLong()
    val serviceChargeAmount = ((subtotal - totalDiscount - voucherDiscount) * (serviceChargePercent / 100.0)).toLong()
    val totalPrice = (subtotal + taxAmount + serviceChargeAmount - totalDiscount - voucherDiscount).coerceAtLeast(0)

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.processBarcodeScan(result.contents) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                if (success) keyboardController?.hide()
            }
        }
    }

    LaunchedEffect(activeShift, showShiftReport) { 
        if (activeShift == null && showShiftReport == null) showOpenShiftDialog = true 
    }

    if (showPrinterErrorDialog) PrinterErrorDialog { showPrinterErrorDialog = false }

    if (showCfd) {
        Dialog(onDismissRequest = onDismissCfd, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            CfdContent(viewModel = viewModel, settingsManager = settingsManager, onDismiss = onDismissCfd)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp) {
                when (event.key) {
                    Key.F1 -> { searchFocusRequester.requestFocus(); true }
                    Key.F2 -> { if (cart.isNotEmpty()) { showPaymentSheet = true; true } else false }
                    Key.F3 -> { if (cart.isNotEmpty()) { viewModel.saveHoldOrder(selectedCustomer?.name ?: "Pelanggan"); true } else false }
                    else -> false
                }
            } else false
        }
    ) {
        if (showPaymentSheet) {
            val totalToPay = if (isSplitBillMode) cart.filter { it.isSelectedForSplit }.sumOf { viewModel.getPriceForVariant(it.product, it.selectedVariation) * it.quantity } else totalPrice
            PaymentBottomSheet(
                totalPrice = totalToPay, totalCommission = cart.sumOf { it.product.commissionPerItem * it.quantity },
                settingsManager = settingsManager, qrisString = qrisDynamicString, isQrisPaid = isQrisPaid,
                uniqueTransferAmount = uniqueTransferAmount, isTransferPaid = isTransferPaid,
                onGenerateQris = {
                    val payload = settingsManager.qrisStaticPayload
                    if (payload.isNullOrBlank()) Toast.makeText(context, "QRIS belum diatur di Pengaturan.", Toast.LENGTH_LONG).show()
                    else viewModel.generateQrisPayment(totalToPay, payload)
                },
                onGenerateTransfer = { viewModel.generateTransferPayment(totalToPay) },
                onDismiss = { showPaymentSheet = false; viewModel.resetQrisPayment(); viewModel.resetTransferPayment() }, 
                onConfirmPayment = { cashReceived, method ->
                    viewModel.processPayment(cashReceived, currentUser.id, currentUser.name, isSplitBillMode, method, settingsManager.maxDiscountCap, if (settingsManager.enableBirthdayDiscount) settingsManager.birthdayDiscountPercent else 0.0) { newTransaction ->
                        showPaymentSheet = false
                        if (!isSplitBillMode) showCartOnPhone = false
                        completedTransaction = newTransaction
                        if (isSplitBillMode) isSplitBillMode = false
                        viewModel.printReceipt(context, newTransaction, settingsManager)
                    }
                }
            )
        }

        if (showProductForm) {
            ProductFormBottomSheet(
                productToEdit = productToEdit, onDismiss = { showProductForm = false; productToEdit = null }, 
                onSave = { name, price, stock, category, costPrice, barcode, variations, commission, expiry, isService, isCombo, imagePath, wholesalePrice, wholesaleMinQty ->
                    if (productToEdit == null) viewModel.addProduct(name, price, stock, category, costPrice, barcode, variations, commission, expiry, isService, isCombo, imagePath, wholesalePrice, wholesaleMinQty, currentUser.id, currentUser.name)
                    else viewModel.updateProduct(productToEdit!!.copy(name = name, price = price, stock = stock, category = category, costPrice = costPrice, barcode = barcode, variations = variations, commissionPerItem = commission, expiryDate = expiry, isService = isService, isCombo = isCombo, imagePath = imagePath, wholesalePrice = wholesalePrice, wholesaleMinQty = wholesaleMinQty), currentUser.id, currentUser.name)
                    showProductForm = false; productToEdit = null
                }
            )
        }

        if (showActionDialogFor != null) {
            PosActionMenuBottomSheet(
                product = showActionDialogFor!!, onDismiss = { showActionDialogFor = null },
                onEdit = { productToEdit = showActionDialogFor; showProductForm = true; showActionDialogFor = null },
                onRestock = { productToRestock = showActionDialogFor; showActionDialogFor = null },
                onWaste = { productToWaste = showActionDialogFor; showActionDialogFor = null },
                onBom = { productToManageBom = showActionDialogFor; showActionDialogFor = null },
                onSaveBarcode = { val product = showActionDialogFor!!; showActionDialogFor = null; saveBarcodeToGallery(context, product) },
                onDelete = { productToDelete = showActionDialogFor; showActionDialogFor = null }
            )
        }

        if (productToSelectMatrix != null) ProductMatrixDialog(productToSelectMatrix!!, { variantName -> viewModel.addToCart(productToSelectMatrix!!, variantName); productToSelectMatrix = null }, { productToSelectMatrix = null })
        if (productToSelectVariant != null) ProductVariantDialog(productToSelectVariant!!, { variant -> viewModel.addToCart(productToSelectVariant!!, variant); productToSelectVariant = null }, { productToSelectVariant = null })
        if (showMergeOrderDialog) MergeTableDialog({ tableA, tableB -> viewModel.mergeOrder(tableA, tableB); showMergeOrderDialog = false }, { showMergeOrderDialog = false })
        
        if (productToEditSerial != null) {
            val currentSerial = cart.find { it.product.id == productToEditSerial!!.id }?.serialNumber ?: ""
            SerialNumberDialog(productToEditSerial!!, currentSerial, { serial -> viewModel.updateCartItemSerial(productToEditSerial!!, cart.find { it.product.id == productToEditSerial!!.id }?.selectedVariation ?: "", serial); productToEditSerial = null }, { productToEditSerial = null })
        }

        if (productToAssignStaff != null) {
            AssignStaffDialog(productToAssignStaff!!, users, { staff -> viewModel.updateCartItemStaff(productToAssignStaff!!, cart.find { it.product.id == productToAssignStaff!!.id }?.selectedVariation ?: "", staff); productToAssignStaff = null }, { productToAssignStaff = null })
        }

        if (productToManageBom != null) BomManagementDialog(productToManageBom!!, rawMaterials, viewModel, { productToManageBom = null })
        if (showShiftReport != null) ShiftReportDialog(showShiftReport!!, { showShiftReport = null; onLogout() })
        
        if (showPinForDiscount) {
            PinDiscountDialog({ pin ->
                val supervisor = users.find { it.pin == hashPin(pin) && (it.role == "OWNER" || it.role == "SUPERVISOR" || it.id == currentUser.id) }
                if (supervisor != null) { showPinForDiscount = false; showDiscountDialog = true } else Toast.makeText(context, "PIN Salah atau Akses Ditolak!", Toast.LENGTH_SHORT).show()
            }, { showPinForDiscount = false })
        }

        if (showDrawerConfirm) {
            CashDrawerConfirmDialog({
                val mac = settingsManager.printerMacAddress
                if (mac != null) {
                    scope.launch {
                        val printerManager = BluetoothPrinterManager(context)
                        val success = printerManager.openCashDrawer(mac)
                        if (success) Toast.makeText(context, "Laci berhasil dibuka!", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(context, "Gagal buka laci. Periksa printer.", Toast.LENGTH_LONG).show()
                        viewModel.logCashDrawerOpen(currentUser)
                        showDrawerConfirm = false 
                    }
                } else {
                    Toast.makeText(context, "Printer belum diatur di Pengaturan.", Toast.LENGTH_LONG).show()
                    viewModel.logCashDrawerOpen(currentUser)
                    showDrawerConfirm = false
                }
            }, { showDrawerConfirm = false })
        }

        if (productToWaste != null) {
            WasteRecordDialog(
                product = productToWaste!!,
                onDismiss = { productToWaste = null },
                onSave = { qty -> if (qty > 0 && qty <= productToWaste!!.stock) { viewModel.recordWaste(productToWaste!!, "", qty, currentUser); productToWaste = null } }
            )
        }

        if (productToEditNotes != null) {
            val currentNotes = cart.find { it.product.id == productToEditNotes!!.id }?.notes ?: ""
            OrderNotesDialog(productToEditNotes!!, currentNotes, { notes -> viewModel.updateCartItemNotes(productToEditNotes!!, cart.find { it.product.id == productToEditNotes!!.id }?.selectedVariation ?: "", notes); productToEditNotes = null }, { productToEditNotes = null })
        }

        if (showOpenShiftDialog) OpenShiftDialog(modalAwal, { if (it.length <= 10) modalAwal = it }, { viewModel.openShift(currentUser, modalAwal.toLongOrNull() ?: 0L); showOpenShiftDialog = false })

        if (showCloseShiftDialog && activeShift != null) { 
            CloseShiftWizardDialog(activeShift!!, transactions, cashFlows, { showCloseShiftDialog = false }, { uangFisik, note -> viewModel.closeShift(currentUser, uangFisik, note) { closedShift -> showCloseShiftDialog = false; showShiftReport = closedShift } })
        }
        
        if (showCashFlowDialog) CashFlowDialog({ type, amount, note -> viewModel.addCashFlow(type, amount, note, currentUser); showCashFlowDialog = false }, { showCashFlowDialog = false })
        if (showDiscountDialog) ManualDiscountDialog(discount, { amount -> viewModel.setDiscount(amount); showDiscountDialog = false }, { showDiscountDialog = false })

        if (showCustomerDialog) {
            CustomerDialogV2(viewModel, customers, selectedCustomer, { customer -> viewModel.setCustomer(customer); showCustomerDialog = false }, { name, phone, birthDate -> viewModel.addCustomer(name, phone, birthDate) }, { points -> viewModel.redeemPoints(points) }, { showCustomerDialog = false })
        }

        if (showTableSelector) TableSelectorDialog(selectedTable, occupiedTables, { table -> viewModel.setTable(table) }, { showTableSelector = false })
        if (showVoucherDialog) VoucherDialog(appliedVoucher, { code -> viewModel.applyVoucher(code) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); if (success) showVoucherDialog = false } }, { viewModel.applyVoucher("") { _, _ -> }; showVoucherDialog = false }, { showVoucherDialog = false })
        if (showHoldDialog) HoldOrderDialog(holdOrders, { order -> viewModel.resumeHoldOrder(order); showHoldDialog = false }, { showHoldDialog = false })

        if (completedTransaction != null) {
            ReceiptDialog(completedTransaction!!, viewModel, settingsManager.storeName ?: "ZoKas", settingsManager.storeAddress ?: "", settingsManager.storePhone ?: "", settingsManager.storeFooter ?: "", settingsManager, { completedTransaction = null })
        }

        if (productToDelete != null) {
            DeleteProductConfirmDialog(productToDelete!!, { viewModel.deleteProduct(productToDelete!!, currentUser.id, currentUser.name); productToDelete = null }, { productToDelete = null })
        }

        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val isWideScreen = maxWidth > 600.dp || isLandscape
                if (isWideScreen) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(0.6f).fillMaxHeight()) { 
                            PosProductSection(
                                products = filteredProducts, categories = categories, selectedCategory = selectedCategory, 
                                onCategorySelect = { category: String -> viewModel.setCategory(category) }, searchQuery = searchQuery, onSearchChange = { query: String -> viewModel.setSearchQuery(query) }, 
                                onProductClick = { prod: ProductEntity -> 
                                    if (prod.variations.contains("|")) productToSelectMatrix = prod
                                    else if (prod.variations.isNotEmpty()) productToSelectVariant = prod 
                                    else viewModel.addToCart(prod) 
                                }, 
                                onProductLongClick = { product: ProductEntity -> if (currentUser.role == "OWNER") showActionDialogFor = product }, 
                                onBarcodeScan = { barcode: String -> viewModel.processBarcodeScan(barcode) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); if (success) keyboardController?.hide() } },
                                onCameraScanClick = {
                                    val options = ScanOptions().apply { setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES); setPrompt("Scan Barcode Produk"); setCameraId(0); setBeepEnabled(false); setBarcodeImageEnabled(true); setOrientationLocked(true) }
                                    barcodeLauncher.launch(options)
                                },
                                searchFocusRequester = searchFocusRequester
                            ) 
                        }
                        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        Box(modifier = Modifier.weight(0.4f).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                            PosCartSection(
                                cart = cart, subtotal = subtotal, discount = totalDiscount, taxAmount = taxAmount, serviceCharge = serviceChargeAmount, totalPrice = totalPrice, 
                                selectedCustomer = selectedCustomer, pointsUsed = pointsUsed, selectedTable = selectedTable, 
                                selectedOrderType = selectedOrderType, pickupDate = pickupDate, appliedVoucher = appliedVoucher, 
                                voucherDiscount = voucherDiscount, isSplitBillMode = isSplitBillMode, isTableModeEnabled = settingsManager.isTableModeEnabled,
                                enableOrderTypes = settingsManager.enableOrderTypes, enablePreOrder = settingsManager.enablePreOrder,
                                customerMemberships = customerMemberships, enableMembership = settingsManager.enableMembership,
                                onOrderTypeChange = { type: String -> viewModel.setOrderType(type) }, onPickupDateChange = { date: Long -> viewModel.setPickupDate(date) },
                                onToggleSplitMode = { isSplitBillMode = !isSplitBillMode }, onToggleSplitSelection = { item: CartItem -> viewModel.toggleSplitSelection(item.product, item.selectedVariation) }, 
                                onTableClick = { showTableSelector = true }, onVoucherClick = { showVoucherDialog = true }, 
                                onHoldClick = { if (cart.isNotEmpty()) { viewModel.saveHoldOrder(selectedCustomer?.name ?: "Pelanggan"); Toast.makeText(context, "Pesanan diparkir.", Toast.LENGTH_SHORT).show() } }, 
                                onViewHoldClick = { showHoldDialog = true }, onCustomerClick = { showCustomerDialog = true }, 
                                onAdd = { item: CartItem -> viewModel.addToCart(item.product, item.selectedVariation) }, onRemove = { item: CartItem -> viewModel.removeFromCart(item.product, item.selectedVariation) }, 
                                onDelete = { item: CartItem -> viewModel.deleteItem(item.product, item.selectedVariation) }, 
                                onDiscountClick = { if (currentUser.role == "CASHIER" && settingsManager.requirePinForDiscount) { showPinForDiscount = true } else { showDiscountDialog = true } }, 
                                onCheckout = { showPaymentSheet = true }, onEditNotes = { product: ProductEntity -> productToEditNotes = product },
                                onEditSerial = { product: ProductEntity -> productToEditSerial = product }, onAssignStaff = { product: ProductEntity -> productToAssignStaff = product },
                                onApplyMembership = { cartItem: CartItem, cm: CustomerMembershipEntity -> viewModel.applyMembershipToCart(cartItem, cm) },
                                onRemoveMembership = { cartItem: CartItem -> viewModel.removeMembershipFromCart(cartItem) },
                                lang = settingsManager.language
                            )
                        }
                    }
                } else {
                    if (showCartOnPhone) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TextButton(onClick = { showCartOnPhone = false }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { 
                                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kembali ke Katalog", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) 
                            }
                            PosCartSection(
                                cart = cart, subtotal = subtotal, discount = totalDiscount, taxAmount = taxAmount, serviceCharge = serviceChargeAmount, totalPrice = totalPrice, 
                                selectedCustomer = selectedCustomer, pointsUsed = pointsUsed, selectedTable = selectedTable, 
                                selectedOrderType = selectedOrderType, pickupDate = pickupDate, appliedVoucher = appliedVoucher, 
                                voucherDiscount = voucherDiscount, isSplitBillMode = isSplitBillMode, isTableModeEnabled = settingsManager.isTableModeEnabled,
                                enableOrderTypes = settingsManager.enableOrderTypes, enablePreOrder = settingsManager.enablePreOrder,
                                customerMemberships = customerMemberships, enableMembership = settingsManager.enableMembership,
                                onOrderTypeChange = { type: String -> viewModel.setOrderType(type) }, onPickupDateChange = { date: Long -> viewModel.setPickupDate(date) },
                                onToggleSplitMode = { isSplitBillMode = !isSplitBillMode }, onToggleSplitSelection = { item: CartItem -> viewModel.toggleSplitSelection(item.product, item.selectedVariation) }, 
                                onTableClick = { showTableSelector = true }, onVoucherClick = { showVoucherDialog = true }, 
                                onHoldClick = { if (cart.isNotEmpty()) { viewModel.saveHoldOrder(selectedCustomer?.name ?: "Pelanggan"); Toast.makeText(context, "Pesanan diparkir.", Toast.LENGTH_SHORT).show() } }, 
                                onViewHoldClick = { showHoldDialog = true }, onCustomerClick = { showCustomerDialog = true }, 
                                onAdd = { item: CartItem -> viewModel.addToCart(item.product, item.selectedVariation) }, onRemove = { item: CartItem -> viewModel.removeFromCart(item.product, item.selectedVariation) }, 
                                onDelete = { item: CartItem -> viewModel.deleteItem(item.product, item.selectedVariation) }, 
                                onDiscountClick = { if (currentUser.role == "CASHIER" && settingsManager.requirePinForDiscount) { showPinForDiscount = true } else { showDiscountDialog = true } }, 
                                onCheckout = { showPaymentSheet = true }, onEditNotes = { product: ProductEntity -> productToEditNotes = product },
                                onEditSerial = { product: ProductEntity -> productToEditSerial = product }, onAssignStaff = { product: ProductEntity -> productToAssignStaff = product },
                                onApplyMembership = { cartItem: CartItem, cm: CustomerMembershipEntity -> viewModel.applyMembershipToCart(cartItem, cm) },
                                onRemoveMembership = { cartItem: CartItem -> viewModel.removeMembershipFromCart(cartItem) },
                                lang = settingsManager.language
                            )
                        }
                    } else {
                        PosProductSection(
                            products = filteredProducts, categories = categories, selectedCategory = selectedCategory, 
                            onCategorySelect = { category: String -> viewModel.setCategory(category) }, searchQuery = searchQuery, onSearchChange = { query: String -> viewModel.setSearchQuery(query) }, 
                            onProductClick = { prod: ProductEntity -> 
                                if (prod.variations.contains("|")) productToSelectMatrix = prod
                                else if (prod.variations.isNotEmpty()) productToSelectVariant = prod 
                                else viewModel.addToCart(prod) 
                            }, 
                            onProductLongClick = { product: ProductEntity -> if (currentUser.role == "OWNER") showActionDialogFor = product }, 
                            onBarcodeScan = { barcode: String -> viewModel.processBarcodeScan(barcode) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); if (success) keyboardController?.hide() } },
                            onCameraScanClick = {
                                val options = ScanOptions().apply { setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES); setPrompt("Scan Barcode Produk"); setCameraId(0); setBeepEnabled(false); setBarcodeImageEnabled(true); setOrientationLocked(true) }
                                barcodeLauncher.launch(options)
                            },
                            searchFocusRequester = searchFocusRequester
                        )
                    }
                }
            }
        }
        
        if (cart.isNotEmpty() && !showCartOnPhone && isPhone && !isLandscape) {
            ExtendedFloatingActionButton(
                onClick = { showCartOnPhone = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = EmeraldGreen,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = "Cart")
                Spacer(modifier = Modifier.width(8.dp))
                Text("${cart.sumOf { it.quantity }} Item | ${formatRupiah(totalPrice)}", fontWeight = FontWeight.Bold)
            }
        }
    }
}