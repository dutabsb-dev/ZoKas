package com.karyaku.kasir

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.TransactionEntity
import com.karyaku.kasir.data.local.UserEntity
import com.karyaku.kasir.data.local.CustomerEntity
import com.karyaku.kasir.data.local.ShiftEntity
import com.karyaku.kasir.data.local.RawMaterialEntity
import kotlinx.coroutines.launch
import java.io.File

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

    // --- STATE DARI VIEWMODEL ---
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
    val allProducts by viewModel.products.collectAsStateWithLifecycle()
    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val cashFlows by viewModel.cashFlows.collectAsStateWithLifecycle()
    val taxPercent by viewModel.taxPercent.collectAsStateWithLifecycle()
    val serviceChargePercent by viewModel.serviceChargePercent.collectAsStateWithLifecycle()
    val customerMemberships by viewModel.customerMemberships.collectAsStateWithLifecycle()

    // STATE QRIS & TRANSFER
    val qrisDynamicString by viewModel.qrisDynamicString.collectAsStateWithLifecycle()
    val isQrisPaid by viewModel.isQrisPaid.collectAsStateWithLifecycle()
    val uniqueTransferAmount by viewModel.uniqueTransferAmount.collectAsStateWithLifecycle()
    val isTransferPaid by viewModel.isTransferPaid.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.setTaxPercent(settingsManager.taxPercent)
        viewModel.setServiceChargePercent(settingsManager.serviceChargePercent)
    }

    // --- STATE LOKAL ---
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

    // --- TRIGGER DARI MENU TITIK TIGA ---
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

    // --- LOGIKA PERHITUNGAN ---
    val subtotal = cart.sumOf {
        if (it.usedMembershipId != null) {
            0L
        } else if (it.product.wholesaleMinQty > 0 && it.quantity >= it.product.wholesaleMinQty && it.product.wholesalePrice > 0) {
            it.product.wholesalePrice * it.quantity
        } else {
            // GUNAKAN HARGA VARIAN
            getPriceForVariant(it.product, it.selectedVariation) * it.quantity
        }
    }
    val happyHourDiscountAmount = 0L
    var tierDiscount = 0L
    var promoDiscountUI = 0L

    var totalDiscount = discount + happyHourDiscountAmount + tierDiscount + promoDiscountUI
    if (settingsManager.maxDiscountCap > 0 && totalDiscount > settingsManager.maxDiscountCap) {
        totalDiscount = settingsManager.maxDiscountCap
    }

    val taxAmount = ((subtotal - totalDiscount - voucherDiscount) * (taxPercent / 100.0)).toLong()
    val serviceChargeAmount = ((subtotal - totalDiscount - voucherDiscount) * (serviceChargePercent / 100.0)).toLong()
    val totalPrice = (subtotal + taxAmount + serviceChargeAmount - totalDiscount - voucherDiscount).coerceAtLeast(0)

    // --- BARCODE LAUNCHER ---
    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.processBarcodeScan(result.contents) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                if (success) keyboardController?.hide()
            }
        }
    }

    LaunchedEffect(activeShift) { if (activeShift == null) showOpenShiftDialog = true }

    if (showPrinterErrorDialog) {
        PrinterErrorDialog { showPrinterErrorDialog = false }
    }

    // --- FITUR CFD (CUSTOMER DISPLAY) ---
    if (showCfd) {
        CustomerDisplayDialog(
            storeName = settingsManager.storeName ?: "ZoKas",
            storeLogoPath = settingsManager.storeLogoPath, // FIX: Tambahkan Logo
            cart = cart,
            totalPrice = totalPrice,
            onDismiss = onDismissCfd
        )
    }

    // --- ROOT UI ---
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
        // --- PEMANGGILAN DIALOG & BOTTOM SHEETS ---
        if (showPaymentSheet) {
            val totalToPay = if (isSplitBillMode) cart.filter { it.isSelectedForSplit }.sumOf { getPriceForVariant(it.product, it.selectedVariation) * it.quantity } else totalPrice
            PaymentBottomSheet(
                totalPrice = totalToPay,
                totalCommission = cart.sumOf { it.product.commissionPerItem * it.quantity },
                settingsManager = settingsManager,
                qrisString = qrisDynamicString,
                isQrisPaid = isQrisPaid,
                uniqueTransferAmount = uniqueTransferAmount,
                isTransferPaid = isTransferPaid,
                onGenerateQris = {
                    val payload = settingsManager.qrisStaticPayload
                    if (payload.isNullOrBlank()) {
                        Toast.makeText(context, "QRIS belum diatur di Pengaturan.", Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.generateQrisPayment(totalToPay, payload)
                    }
                },
                onGenerateTransfer = {
                    viewModel.generateTransferPayment(totalToPay)
                },
                onDismiss = {
                    showPaymentSheet = false
                    viewModel.resetQrisPayment()
                    viewModel.resetTransferPayment()
                },
                onConfirmPayment = { cashReceived, method ->
                    viewModel.processPayment(
                        cashReceived = cashReceived,
                        userId = currentUser.id,
                        userName = currentUser.name,
                        isSplitBill = isSplitBillMode,
                        method = method,
                        maxDiscountCap = settingsManager.maxDiscountCap,
                        birthdayDiscountPercent = if (settingsManager.enableBirthdayDiscount) settingsManager.birthdayDiscountPercent else 0.0
                    ) { newTransaction ->
                        showPaymentSheet = false
                        if (!isSplitBillMode) showCartOnPhone = false
                        completedTransaction = newTransaction
                        if (isSplitBillMode) isSplitBillMode = false
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
                product = showActionDialogFor!!,
                onDismiss = { showActionDialogFor = null },
                onEdit = { productToEdit = showActionDialogFor; showProductForm = true; showActionDialogFor = null },
                onRestock = { productToRestock = showActionDialogFor; showActionDialogFor = null },
                onWaste = { productToWaste = showActionDialogFor; showActionDialogFor = null },
                onBom = { productToManageBom = showActionDialogFor; showActionDialogFor = null },
                onSaveBarcode = {
                    val product = showActionDialogFor!!
                    showActionDialogFor = null
                    saveBarcodeToGallery(context, product)
                },
                onDelete = { productToDelete = showActionDialogFor; showActionDialogFor = null }
            )
        }

        if (productToSelectMatrix != null) {
            ProductMatrixDialog(
                product = productToSelectMatrix!!,
                onSelect = { variantName -> viewModel.addToCart(productToSelectMatrix!!, variantName); productToSelectMatrix = null },
                onDismiss = { productToSelectMatrix = null }
            )
        }

        if (productToSelectVariant != null) {
            ProductVariantDialog(
                product = productToSelectVariant!!,
                onSelect = { variant -> viewModel.addToCart(productToSelectVariant!!, variant); productToSelectVariant = null },
                onDismiss = { productToSelectVariant = null }
            )
        }

        if (showMergeOrderDialog) {
            MergeTableDialog(
                onMerge = { tableA, tableB -> viewModel.mergeOrder(tableA, tableB); showMergeOrderDialog = false },
                onDismiss = { showMergeOrderDialog = false }
            )
        }

        if (productToEditSerial != null) {
            val currentSerial = cart.find { it.product.id == productToEditSerial!!.id }?.serialNumber ?: ""
            SerialNumberDialog(
                product = productToEditSerial!!, currentSerial = currentSerial,
                onSave = { serial -> viewModel.updateCartItemSerial(productToEditSerial!!, cart.find { it.product.id == productToEditSerial!!.id }?.selectedVariation ?: "", serial); productToEditSerial = null },
                onDismiss = { productToEditSerial = null }
            )
        }

        if (productToAssignStaff != null) {
            AssignStaffDialog(
                product = productToAssignStaff!!, users = users,
                onAssign = { staff -> viewModel.updateCartItemStaff(productToAssignStaff!!, cart.find { it.product.id == productToAssignStaff!!.id }?.selectedVariation ?: "", staff); productToAssignStaff = null },
                onDismiss = { productToAssignStaff = null }
            )
        }

        if (productToManageBom != null) {
            BomManagementDialog(
                product = productToManageBom!!, rawMaterials = rawMaterials, viewModel = viewModel,
                onDismiss = { productToManageBom = null }
            )
        }

        if (showShiftReport != null) {
            ShiftReportDialog(shift = showShiftReport!!, onLogout = { showShiftReport = null; onLogout() })
        }

        if (showPinForDiscount) {
            PinDiscountDialog(
                onVerify = { pin ->
                    val supervisor = users.find { it.pin == hashPin(pin) && (it.role == "OWNER" || it.role == "SUPERVISOR" || it.id == currentUser.id) }
                    if (supervisor != null) { showPinForDiscount = false; showDiscountDialog = true }
                    else { Toast.makeText(context, "PIN Salah atau Akses Ditolak!", Toast.LENGTH_SHORT).show() }
                },
                onDismiss = { showPinForDiscount = false }
            )
        }

        if (showDrawerConfirm) {
            CashDrawerConfirmDialog(
                onConfirm = {
                    val mac = settingsManager.printerMacAddress
                    if (mac != null) {
                        scope.launch {
                            val printerManager = BluetoothPrinterManager(context)
                            val success = printerManager.openCashDrawer(mac)
                            if (success) {
                                Toast.makeText(context, "Laci berhasil dibuka!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Gagal buka laci. Periksa printer.", Toast.LENGTH_LONG).show()
                            }
                            viewModel.logCashDrawerOpen(currentUser)
                            showDrawerConfirm = false
                        }
                    } else {
                        Toast.makeText(context, "Printer belum diatur di Pengaturan.", Toast.LENGTH_LONG).show()
                        viewModel.logCashDrawerOpen(currentUser)
                        showDrawerConfirm = false
                    }
                },
                onDismiss = { showDrawerConfirm = false }
            )
        }

        if (productToWaste != null) {
            WasteRecordDialog(
                product = productToWaste!!,
                onSave = { qty -> if (qty > 0 && qty <= productToWaste!!.stock) { viewModel.recordWaste(productToWaste!!, qty, currentUser); productToWaste = null } },
                onDismiss = { productToWaste = null }
            )
        }

        if (productToEditNotes != null) {
            val currentNotes = cart.find { it.product.id == productToEditNotes!!.id }?.notes ?: ""
            OrderNotesDialog(
                product = productToEditNotes!!, currentNotes = currentNotes,
                onSave = { notes -> viewModel.updateCartItemNotes(productToEditNotes!!, cart.find { it.product.id == productToEditNotes!!.id }?.selectedVariation ?: "", notes); productToEditNotes = null },
                onDismiss = { productToEditNotes = null }
            )
        }

        if (showOpenShiftDialog) {
            OpenShiftDialog(
                modalAwal = modalAwal, onModalChange = { if (it.length <= 10) modalAwal = it },
                onConfirm = { viewModel.openShift(currentUser, modalAwal.toLongOrNull() ?: 0L); showOpenShiftDialog = false }
            )
        }

        if (showCloseShiftDialog && activeShift != null) {
            CloseShiftWizardDialog(
                shift = activeShift!!, transactions = transactions, cashFlows = cashFlows,
                onDismiss = { showCloseShiftDialog = false },
                onConfirm = { uangFisik, note ->
                    viewModel.closeShift(currentUser, uangFisik, note) { closedShift ->
                        showCloseShiftDialog = false
                        showShiftReport = closedShift
                    }
                }
            )
        }

        if (showCashFlowDialog) {
            CashFlowDialog(
                onSave = { type, amount, note -> viewModel.addCashFlow(type, amount, note, currentUser); showCashFlowDialog = false },
                onDismiss = { showCashFlowDialog = false }
            )
        }

        if (showDiscountDialog) {
            ManualDiscountDialog(
                currentDiscount = discount,
                onSave = { amount -> viewModel.setDiscount(amount); showDiscountDialog = false },
                onDismiss = { showDiscountDialog = false }
            )
        }

        if (showCustomerDialog) {
            CustomerDialogV2(
                viewModel = viewModel,
                customers = customers, selectedCustomer = selectedCustomer,
                onSelect = { viewModel.setCustomer(it); showCustomerDialog = false },
                onAddCustomer = { name, phone, birthDate -> viewModel.addCustomer(name, phone, birthDate) },
                onRedeemPoints = { points -> viewModel.redeemPoints(points) },
                onDismiss = { showCustomerDialog = false }
            )
        }

        if (showTableSelector) {
            TableSelectorDialog(
                currentTable = selectedTable, occupiedTables = occupiedTables,
                onSelect = { viewModel.setTable(it) },
                onDismiss = { showTableSelector = false }
            )
        }

        if (showVoucherDialog) {
            VoucherDialog(
                currentCode = appliedVoucher,
                onApply = { code -> viewModel.applyVoucher(code) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); if (success) showVoucherDialog = false } },
                onRemove = { viewModel.applyVoucher("") { _, _ -> }; showVoucherDialog = false },
                onDismiss = { showVoucherDialog = false }
            )
        }

        if (showHoldDialog) {
            HoldOrderDialog(
                holdOrders = holdOrders,
                onResume = { viewModel.resumeHoldOrder(it); showHoldDialog = false },
                onDismiss = { showHoldDialog = false }
            )
        }

        if (completedTransaction != null) {
            ReceiptDialog(
                transaction = completedTransaction!!, viewModel = viewModel,
                storeName = settingsManager.storeName ?: "ZoKas", storeAddress = settingsManager.storeAddress ?: "",
                storePhone = settingsManager.storePhone ?: "", storeFooter = settingsManager.storeFooter ?: "",
                settingsManager = settingsManager, onDismiss = { completedTransaction = null }
            )
        }

        if (productToDelete != null) {
            DeleteProductConfirmDialog(
                product = productToDelete!!,
                onDelete = { viewModel.deleteProduct(productToDelete!!, currentUser.id, currentUser.name); productToDelete = null },
                onDismiss = { productToDelete = null }
            )
        }

        // --- MAIN UI LAYOUT ---
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val isWideScreen = maxWidth > 600.dp || isLandscape

                if (isWideScreen) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // KIRI: Katalog
                        Box(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
                            PosProductSection(
                                products = filteredProducts, categories = categories, selectedCategory = selectedCategory,
                                onCategorySelect = { viewModel.setCategory(it) }, searchQuery = searchQuery, onSearchChange = { viewModel.setSearchQuery(it) },
                                onProductClick = { prod ->
                                    if (prod.variations.contains("|")) productToSelectMatrix = prod
                                    else if (prod.variations.isNotEmpty()) productToSelectVariant = prod
                                    else viewModel.addToCart(prod)
                                },
                                onProductLongClick = { showActionDialogFor = it },
                                onBarcodeScan = { barcode -> viewModel.processBarcodeScan(barcode) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); if (success) keyboardController?.hide() } },
                                onCameraScanClick = {
                                    val options = ScanOptions()
                                    options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                                    options.setPrompt("Scan Barcode Produk")
                                    options.setCameraId(0)
                                    options.setBeepEnabled(false)
                                    options.setBarcodeImageEnabled(true)
                                    options.setOrientationLocked(true)
                                    barcodeLauncher.launch(options)
                                },
                                searchFocusRequester = searchFocusRequester
                            )
                        }
                        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                        // KANAN: Keranjang
                        Box(modifier = Modifier.weight(0.4f).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                            PosCartSection(
                                cart = cart, subtotal = subtotal, discount = totalDiscount, taxAmount = taxAmount, serviceCharge = serviceChargeAmount, totalPrice = totalPrice,
                                selectedCustomer = selectedCustomer, pointsUsed = pointsUsed, selectedTable = selectedTable,
                                selectedOrderType = selectedOrderType, pickupDate = pickupDate,
                                appliedVoucher = appliedVoucher,
                                voucherDiscount = voucherDiscount, isSplitBillMode = isSplitBillMode, isTableModeEnabled = settingsManager.isTableModeEnabled,
                                enableOrderTypes = settingsManager.enableOrderTypes, enablePreOrder = settingsManager.enablePreOrder,
                                customerMemberships = customerMemberships, enableMembership = settingsManager.enableMembership,
                                onOrderTypeChange = { viewModel.setOrderType(it) }, onPickupDateChange = { viewModel.setPickupDate(it) },
                                onToggleSplitMode = { isSplitBillMode = !isSplitBillMode }, onToggleSplitSelection = { viewModel.toggleSplitSelection(it.product, it.selectedVariation) },
                                onTableClick = { showTableSelector = true }, onVoucherClick = { showVoucherDialog = true },
                                onHoldClick = { if (cart.isNotEmpty()) { viewModel.saveHoldOrder(selectedCustomer?.name ?: "Pelanggan"); Toast.makeText(context, "Pesanan diparkir.", Toast.LENGTH_SHORT).show() } },
                                onViewHoldClick = { showHoldDialog = true }, onCustomerClick = { showCustomerDialog = true },
                                onAdd = { viewModel.addToCart(it.product, it.selectedVariation) }, onRemove = { viewModel.removeFromCart(it.product, it.selectedVariation) },
                                onDelete = { viewModel.deleteItem(it.product, it.selectedVariation) },
                                onDiscountClick = { if (currentUser.role == "CASHIER" && settingsManager.requirePinForDiscount) { showPinForDiscount = true } else { showDiscountDialog = true } },
                                onCheckout = { showPaymentSheet = true }, onEditNotes = { productToEditNotes = it },
                                onEditSerial = { productToEditSerial = it }, onAssignStaff = { productToAssignStaff = it },
                                onApplyMembership = { cartItem, cm -> viewModel.applyMembershipToCart(cartItem, cm) },
                                onRemoveMembership = { cartItem -> viewModel.removeMembershipFromCart(cartItem) },
                                lang = settingsManager.language
                            )
                        }
                    }
                } else {
                    // LAYOUT HP (Portrait)
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
                                selectedOrderType = selectedOrderType, pickupDate = pickupDate,
                                appliedVoucher = appliedVoucher,
                                voucherDiscount = voucherDiscount, isSplitBillMode = isSplitBillMode, isTableModeEnabled = settingsManager.isTableModeEnabled,
                                enableOrderTypes = settingsManager.enableOrderTypes, enablePreOrder = settingsManager.enablePreOrder,
                                customerMemberships = customerMemberships, enableMembership = settingsManager.enableMembership,
                                onOrderTypeChange = { viewModel.setOrderType(it) }, onPickupDateChange = { viewModel.setPickupDate(it) },
                                onToggleSplitMode = { isSplitBillMode = !isSplitBillMode }, onToggleSplitSelection = { viewModel.toggleSplitSelection(it.product, it.selectedVariation) },
                                onTableClick = { showTableSelector = true }, onVoucherClick = { showVoucherDialog = true },
                                onHoldClick = { if (cart.isNotEmpty()) { viewModel.saveHoldOrder(selectedCustomer?.name ?: "Pelanggan"); Toast.makeText(context, "Pesanan diparkir.", Toast.LENGTH_SHORT).show() } },
                                onViewHoldClick = { showHoldDialog = true }, onCustomerClick = { showCustomerDialog = true },
                                onAdd = { viewModel.addToCart(it.product, it.selectedVariation) }, onRemove = { viewModel.removeFromCart(it.product, it.selectedVariation) },
                                onDelete = { viewModel.deleteItem(it.product, it.selectedVariation) },
                                onDiscountClick = { if (currentUser.role == "CASHIER" && settingsManager.requirePinForDiscount) { showPinForDiscount = true } else { showDiscountDialog = true } },
                                onCheckout = { showPaymentSheet = true }, onEditNotes = { productToEditNotes = it },
                                onEditSerial = { productToEditSerial = it }, onAssignStaff = { productToAssignStaff = it },
                                onApplyMembership = { cartItem, cm -> viewModel.applyMembershipToCart(cartItem, cm) },
                                onRemoveMembership = { cartItem -> viewModel.removeMembershipFromCart(cartItem) },
                                lang = settingsManager.language
                            )
                        }
                    } else {
                        PosProductSection(
                            products = filteredProducts, categories = categories, selectedCategory = selectedCategory,
                            onCategorySelect = { viewModel.setCategory(it) }, searchQuery = searchQuery, onSearchChange = { viewModel.setSearchQuery(it) },
                            onProductClick = { prod ->
                                if (prod.variations.contains("|")) productToSelectMatrix = prod
                                else if (prod.variations.isNotEmpty()) productToSelectVariant = prod
                                else viewModel.addToCart(prod)
                            },
                            onProductLongClick = { showActionDialogFor = it },
                            onBarcodeScan = { barcode -> viewModel.processBarcodeScan(barcode) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); if (success) keyboardController?.hide() } },
                            onCameraScanClick = {
                                val options = ScanOptions()
                                options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                                options.setPrompt("Scan Barcode Produk")
                                options.setCameraId(0)
                                options.setBeepEnabled(false)
                                options.setBarcodeImageEnabled(true)
                                options.setOrientationLocked(true)
                                barcodeLauncher.launch(options)
                            },
                            searchFocusRequester = searchFocusRequester
                        )
                    }
                }
            }
        }

        // FAB Keranjang (Hanya di HP Portrait)
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

// ====================================================================
// CUSTOMER DISPLAY DIALOG (CFD)
// ====================================================================
@Composable
fun CustomerDisplayDialog(
    storeName: String,
    storeLogoPath: String?,
    cart: List<CartItem>,
    totalPrice: Long,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val listState = rememberLazyListState()
            val totalQty = cart.sumOf { it.quantity }

            LaunchedEffect(cart.size, totalQty) {
                if (cart.isNotEmpty()) {
                    kotlinx.coroutines.delay(50)
                    listState.animateScrollToItem(cart.size - 1)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ---------------- HEADER ----------------
                val logoFile = remember(storeLogoPath) {
                    if (!storeLogoPath.isNullOrBlank()) File(storeLogoPath) else null
                }

                if (logoFile != null && logoFile.exists()) {
                    AsyncImage(
                        model = logoFile,
                        contentDescription = "Store Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(72.dp).padding(bottom = 8.dp)
                    )
                }

                Text(
                    text = storeName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Total Belanja Anda",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatRupiah(totalPrice),
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 42.sp),
                    fontWeight = FontWeight.Black,
                    color = EmeraldGreen,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // ---------------- LIST ITEM (scrollable) ----------------
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (cart.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Belum ada item",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Silakan tambahkan produk di kasir.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(cart, key = { it.product.id to it.selectedVariation }) { item ->
                                CfdItemRow(item = item)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // ---------------- FOOTER ----------------
                Text(
                    text = "Terima Kasih",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Silakan lakukan pembayaran di kasir",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        "Tutup Layar Pelanggan",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CfdItemRow(item: CartItem) {
    val unitPrice = when {
        item.usedMembershipId != null -> 0L
        item.product.wholesaleMinQty > 0
            && item.quantity >= item.product.wholesaleMinQty
            && item.product.wholesalePrice > 0 -> item.product.wholesalePrice
        else -> getPriceForVariant(item.product, item.selectedVariation) // FIX VARIAN HARGA
    }
    val lineTotal = unitPrice * item.quantity

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (item.product.imagePath.isNotEmpty()) {
                    AsyncImage(
                        model = File(item.product.imagePath),
                        contentDescription = item.product.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.selectedVariation.isNotEmpty()) {
                    Text(
                        text = "Varian: ${item.selectedVariation}",
                        fontSize = 11.sp,
                        color = EmeraldGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "${item.quantity} × ${formatRupiah(unitPrice)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                color = EmeraldGreen.copy(alpha = 0.12f),
                shape = CircleShape
            ) {
                Text(
                    text = "${item.quantity}x",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = EmeraldGreen,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatRupiah(lineTotal),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}