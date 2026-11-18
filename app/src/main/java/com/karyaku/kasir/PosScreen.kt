package com.karyaku.kasir

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import com.karyaku.kasir.data.local.CustomerEntity
import com.karyaku.kasir.data.local.CustomerMembershipEntity
import com.karyaku.kasir.data.local.HoldOrderEntity
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.TransactionEntity
import com.karyaku.kasir.data.local.UserEntity
import com.karyaku.kasir.data.local.ShiftEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

// ====================================================================
// UPDATE PosScreen-v4.kt:
//   1. FIX BUG #1 (Selalu Minta Modal Awal Saat Buka Ulang Aplikasi):
//      - Penyebab 1 (Race Condition Room Flow): Saat aplikasi baru dibuka,
//        activeShift dan shifts masih bernilai awal (null / empty) selama
//        beberapa milidetik selagi Room membaca database di background.
//        LaunchedEffect(activeShift) sebelumnya langsung mendeteksi null
//        di milidetik ke-0 dan memaksa showOpenShiftDialog = true.
//      - Penyebab 2 (Sinkronisasi Shift): Mengecek baik dari activeShift
//        maupun dari daftar shifts di database yang endTime == null.
//      - Solusi: Menambahkan penantian inisialisasi DB (delay 450ms) +
//        auto-close otomatis jika di tengah tunggu ternyata shift aktif
//        ditemukan di database (hasOpenShift).
//   2. FIX BUG #2 (Tombol Back Saat Membuka Keranjang di Layar HP):
//      - Menambahkan BackHandler(enabled = showCartOnPhone) agar saat kasir
//        membuka layar keranjang penuh di HP dan menekan tombol Back Android,
//        layar kembali ke Katalog Produk (bukan keluar aplikasi).
//   3. Mempertahankan seluruh perbaikan Simulasi sebelumnya (Restock di Kasir,
//      Waste per varian, Diskon Happy Hour & Ultah, serta Edit Catatan/SN/Staf
//      per CartItem).
// ====================================================================

private fun isCurrentTimeInHappyHour(startStr: String, endStr: String): Boolean {
    return try {
        val sParts = startStr.split(":")
        val eParts = endStr.split(":")
        if (sParts.size < 2 || eParts.size < 2) return false
        val startMin = (sParts[0].trim().toIntOrNull() ?: 0) * 60 + (sParts[1].trim().toIntOrNull() ?: 0)
        val endMin = (eParts[0].trim().toIntOrNull() ?: 0) * 60 + (eParts[1].trim().toIntOrNull() ?: 0)
        val now = Calendar.getInstance()
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        if (startMin <= endMin) {
            nowMin in startMin..endMin
        } else {
            nowMin >= startMin || nowMin <= endMin
        }
    } catch (e: Exception) {
        false
    }
}

private fun isTodayCustomerBirthday(birthDateMillis: Long): Boolean {
    if (birthDateMillis <= 0L) return false
    val today = Calendar.getInstance()
    val birth = Calendar.getInstance().apply { timeInMillis = birthDateMillis }
    return today.get(Calendar.MONTH) == birth.get(Calendar.MONTH) &&
        today.get(Calendar.DAY_OF_MONTH) == birth.get(Calendar.DAY_OF_MONTH)
}

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
    val shifts by viewModel.shifts.collectAsStateWithLifecycle()
    val currentBranchId by viewModel.currentBranchId.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val discount by viewModel.discount.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val selectedCustomer by viewModel.selectedCustomer.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val allProducts by viewModel.products.collectAsStateWithLifecycle()
    val vmFilteredProducts by viewModel.filteredProducts.collectAsStateWithLifecycle()
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

    val isCrmEnabled = settingsManager.enableCrmModule

    val filteredProducts = remember(allProducts, vmFilteredProducts, currentBranchId, selectedCategory, searchQuery) {
        if (searchQuery.isBlank()) {
            vmFilteredProducts
        } else {
            val q = searchQuery.trim()
            allProducts.filter { prod ->
                prod.branchId == currentBranchId &&
                    (selectedCategory == "Semua" || prod.category.equals(selectedCategory, ignoreCase = true)) &&
                    matchesProductSearch(prod, q)
            }.sortedWith(
                compareByDescending<ProductEntity> {
                    extractProductSku(it.barcode).equals(q, ignoreCase = true)
                }.thenByDescending {
                    extractProductSku(it.barcode).startsWith(q, ignoreCase = true)
                }.thenBy { it.name }
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.setTaxPercent(settingsManager.taxPercent)
        viewModel.setServiceChargePercent(settingsManager.serviceChargePercent)
        if (!isCrmEnabled && selectedCustomer != null) {
            viewModel.setCustomer(null)
        }
    }

    var showCartOnPhone by rememberSaveable { mutableStateOf(false) }

    // FIX BUG #2: Jika sedang membuka tampilan Keranjang penuh di layar HP, tombol Back kembali ke Katalog Produk
    BackHandler(enabled = showCartOnPhone && !isLandscape && isPhone) {
        showCartOnPhone = false
    }

    var showPaymentSheet by remember { mutableStateOf(false) }
    var showProductForm by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<ProductEntity?>(null) }
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }
    var productToRestock by remember { mutableStateOf<ProductEntity?>(null) }
    var productToWaste by remember { mutableStateOf<ProductEntity?>(null) }
    var productForVariantAction by remember { mutableStateOf<ProductEntity?>(null) }
    var variantActionType by remember { mutableStateOf("") }
    var variantToProcess by remember { mutableStateOf("") }

    var showActionDialogFor by remember { mutableStateOf<ProductEntity?>(null) }
    var cartItemToEditNotes by remember { mutableStateOf<CartItem?>(null) }
    var productToSelectVariant by remember { mutableStateOf<ProductEntity?>(null) }
    var productToSelectMatrix by remember { mutableStateOf<ProductEntity?>(null) }
    var cartItemToEditSerial by remember { mutableStateOf<CartItem?>(null) }
    var cartItemToAssignStaff by remember { mutableStateOf<CartItem?>(null) }
    var productToManageBom by remember { mutableStateOf<ProductEntity?>(null) }
    var showRawMaterialDialog by remember { mutableStateOf(false) }
    var completedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    var showOpenShiftDialog by remember { mutableStateOf(false) }
    var showProactiveGuide by remember { mutableStateOf(false) }
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

    // FIX BUG #1: Deteksi Shift Aktif yang Tahan Race Condition saat Aplikasi Baru Dibuka
    // Cari apakah ada shift yang belum ditutup (endTime == null) baik dari activeShift maupun tabel shifts
    val currentOpenShift = remember(activeShift, shifts, currentBranchId) {
        activeShift ?: shifts.firstOrNull {
            it.endTime == null && (it.branchId == currentBranchId || it.branchId == 0L)
        } ?: shifts.firstOrNull { it.endTime == null }
    }

    LaunchedEffect(currentOpenShift, showShiftReport, currentBranchId) {
        if (currentOpenShift != null) {
            showOpenShiftDialog = false
            if (!ProactiveGuideManager.hasSeenGuide(context, ProactiveGuideManager.KEY_POS_SCREEN)) {
                showProactiveGuide = true
            }
        } else if (showShiftReport == null) {
            delay(450)
            val stillOpen = viewModel.activeShift.value ?: viewModel.shifts.value.firstOrNull { it.endTime == null }
            if (stillOpen == null && showShiftReport == null) {
                showOpenShiftDialog = true
            }
        }
    }

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

    val subtotal = cart.sumOf { item ->
        if (item.usedMembershipId != null) 0L
        else if (item.product.wholesaleMinQty > 0 && item.quantity >= item.product.wholesaleMinQty && item.product.wholesalePrice > 0) item.product.wholesalePrice * item.quantity
        else viewModel.getPriceForVariant(item.product, item.selectedVariation) * item.quantity
    }

    // Kalkulasi aktif untuk Happy Hour & Diskon Ulang Tahun di UI Keranjang
    val happyHourDiscountAmount = if (settingsManager.happyHourDiscount > 0.0 &&
        isCurrentTimeInHappyHour(settingsManager.happyHourStart, settingsManager.happyHourEnd)
    ) {
        (subtotal * (settingsManager.happyHourDiscount / 100.0)).toLong()
    } else 0L

    val birthdayDiscountAmount = if (isCrmEnabled &&
        settingsManager.enableBirthdayDiscount &&
        settingsManager.birthdayDiscountPercent > 0.0 &&
        selectedCustomer != null &&
        isTodayCustomerBirthday(selectedCustomer!!.birthDate)
    ) {
        (subtotal * (settingsManager.birthdayDiscountPercent / 100.0)).toLong()
    } else 0L

    val pointsDiscountAmount = if (isCrmEnabled) (pointsUsed * 100L) else 0L

    var totalDiscount = discount + happyHourDiscountAmount + birthdayDiscountAmount + pointsDiscountAmount
    if (settingsManager.maxDiscountCap > 0 && totalDiscount > settingsManager.maxDiscountCap) {
        totalDiscount = settingsManager.maxDiscountCap
    }

    val taxPrefs = remember { context.getSharedPreferences("zokas_tax_ext", Context.MODE_PRIVATE) }
    val isTaxInclusive = taxPrefs.getBoolean("is_tax_inclusive", false)

    val netAfterDiscount = (subtotal - totalDiscount - voucherDiscount).coerceAtLeast(0L)
    val serviceChargeAmount = (netAfterDiscount * (serviceChargePercent / 100.0)).toLong()
    val taxAmount = if (taxPercent <= 0.0) {
        0L
    } else if (isTaxInclusive) {
        // Pajak Inklusif: Pajak sudah terkandung di dalam harga jual menu (DPP + Pajak = Harga Menu)
        (netAfterDiscount - (netAfterDiscount / (1.0 + (taxPercent / 100.0)))).toLong()
    } else {
        // Pajak Eksklusif: Pajak ditambahkan di atas subtotal bersih
        (netAfterDiscount * (taxPercent / 100.0)).toLong()
    }
    val totalPrice = if (isTaxInclusive) {
        (netAfterDiscount + serviceChargeAmount).coerceAtLeast(0L)
    } else {
        (netAfterDiscount + taxAmount + serviceChargeAmount).coerceAtLeast(0L)
    }

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.processBarcodeScan(result.contents) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                if (success) keyboardController?.hide()
            }
        }
    }

    if (showPrinterErrorDialog) PrinterErrorDialog { showPrinterErrorDialog = false }

    if (showCfd) {
        Dialog(onDismissRequest = onDismissCfd, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            CfdContent(viewModel = viewModel, settingsManager = settingsManager, onDismiss = onDismissCfd)
        }
    }

    // Dialog Pemilih Varian untuk Restock / Waste / Simpan Barcode di Layar Kasir
    if (productForVariantAction != null) {
        AlertDialog(
            onDismissRequest = { productForVariantAction = null },
            title = { Text("Pilih Varian", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Produk ${productForVariantAction!!.name} memiliki beberapa varian. Pilih varian untuk ${variantActionType.lowercase()}:",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val variants = productForVariantAction!!.variations
                        .replace("|", ",")
                        .split(",")
                        .map { it.split(":")[0].trim() }
                        .filter { it.isNotEmpty() }
                    variants.forEach { vName ->
                        Button(
                            onClick = {
                                variantToProcess = vName
                                val targetProd = productForVariantAction
                                productForVariantAction = null
                                if (variantActionType == "RESTOCK") productToRestock = targetProd
                                if (variantActionType == "WASTE") productToWaste = targetProd
                                if (variantActionType == "BARCODE" && targetProd != null) {
                                    saveBarcodeToGallery(context, targetProd)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text(vName) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { productForVariantAction = null }) {
                    Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp) {
                when (event.key) {
                    Key.F1 -> { searchFocusRequester.requestFocus(); true }
                    Key.F2 -> { if (cart.isNotEmpty()) { showPaymentSheet = true; true } else false }
                    Key.F3 -> { if (cart.isNotEmpty()) { viewModel.saveHoldOrder(if (isCrmEnabled) (selectedCustomer?.name ?: "Pelanggan") else "Pelanggan"); true } else false }
                    else -> false
                }
            } else false
        }
    ) {
        if (showPaymentSheet) {
            val totalToPay = if (isSplitBillMode) cart.filter { item -> item.isSelectedForSplit }.sumOf { item -> viewModel.getPriceForVariant(item.product, item.selectedVariation) * item.quantity } else totalPrice
            PaymentBottomSheet(
                totalPrice = totalToPay, totalCommission = cart.sumOf { item -> item.product.commissionPerItem * item.quantity },
                settingsManager = settingsManager, qrisString = qrisDynamicString, isQrisPaid = isQrisPaid,
                uniqueTransferAmount = uniqueTransferAmount, isTransferPaid = isTransferPaid,
                onGenerateQris = {
                    val payload = settingsManager.qrisStaticPayload
                    if (payload.isNullOrBlank()) Toast.makeText(context, "QRIS belum diatur di Pengaturan.", Toast.LENGTH_LONG).show()
                    else viewModel.generateQrisPayment(totalToPay, payload)
                },
                onGenerateTransfer = { viewModel.generateTransferPayment(totalToPay) },
                onDismiss = { showPaymentSheet = false; viewModel.resetQrisPayment(); viewModel.resetTransferPayment() },
                onConfirmPayment = { cashReceived: Long, method: String ->
                    val currentCartSnapshot = if (isSplitBillMode) cart.filter { it.isSelectedForSplit } else cart

                    viewModel.processPayment(
                        cashReceived = cashReceived, userId = currentUser.id, userName = currentUser.name,
                        isSplitBill = isSplitBillMode, method = method, maxDiscountCap = settingsManager.maxDiscountCap,
                        birthdayDiscountPercent = if (isCrmEnabled && settingsManager.enableBirthdayDiscount) settingsManager.birthdayDiscountPercent else 0.0,
                        taxPercent = if (isTaxInclusive) 0.0 else taxPercent, serviceChargePercent = serviceChargePercent,
                        manualDiscount = discount + happyHourDiscountAmount, voucherDiscount = voucherDiscount, voucherCode = appliedVoucher
                    ) { newTransaction ->
                        showPaymentSheet = false
                        if (!isSplitBillMode) showCartOnPhone = false
                        completedTransaction = newTransaction
                        if (isSplitBillMode) isSplitBillMode = false

                        viewModel.printReceipt(context, newTransaction, settingsManager, currentCartSnapshot)
                    }
                }
            )
        }

        if (showProductForm) {
            ProductFormBottomSheet(
                productToEdit = productToEdit,
                onDismiss = { showProductForm = false; productToEdit = null },
                onManageBom = { prod ->
                    productToManageBom = prod
                },
                onManageRawMaterial = {
                    showRawMaterialDialog = true
                },
                onSave = { name, price, stock, category, costPrice, barcode, variations, commission, expiry, isService, isCombo, imagePath, wholesalePrice, wholesaleMinQty ->
                    if (productToEdit == null) viewModel.addProduct(name, price, stock, category, costPrice, barcode, variations, commission, expiry, isService, isCombo, imagePath, wholesalePrice, wholesaleMinQty, currentUser.id, currentUser.name)
                    else viewModel.updateProduct(productToEdit!!.copy(name = name, price = price, stock = stock, category = category, costPrice = costPrice, barcode = barcode, variations = variations, commissionPerItem = commission, expiryDate = expiry, isService = isService, isCombo = isCombo, imagePath = imagePath, wholesalePrice = wholesalePrice, wholesaleMinQty = wholesaleMinQty), currentUser.id, currentUser.name)
                    showProductForm = false; productToEdit = null
                }
            )
        }

        if (showProactiveGuide && !showOpenShiftDialog) {
            ProactiveGuideDialog(
                featureKey = ProactiveGuideManager.KEY_POS_SCREEN,
                onDismiss = { showProactiveGuide = false }
            )
        }

        if (showActionDialogFor != null) {
            val isOwner = currentUser.role == "OWNER"
            PosActionMenuBottomSheet(
                product = showActionDialogFor!!,
                onDismiss = { showActionDialogFor = null },
                onEdit = {
                    if (isOwner) {
                        productToEdit = showActionDialogFor
                        showProductForm = true
                    } else {
                        Toast.makeText(context, "Hanya OWNER yang dapat mengubah detail/harga produk.", Toast.LENGTH_SHORT).show()
                    }
                    showActionDialogFor = null
                },
                onRestock = {
                    val target = showActionDialogFor!!
                    showActionDialogFor = null
                    if (target.variations.isNotBlank() && !target.variations.contains("|")) {
                        productForVariantAction = target
                        variantActionType = "RESTOCK"
                    } else {
                        variantToProcess = ""
                        productToRestock = target
                    }
                },
                onWaste = {
                    val target = showActionDialogFor!!
                    showActionDialogFor = null
                    if (target.variations.isNotBlank() && !target.variations.contains("|")) {
                        productForVariantAction = target
                        variantActionType = "WASTE"
                    } else {
                        variantToProcess = ""
                        productToWaste = target
                    }
                },
                onBom = { productToManageBom = showActionDialogFor; showActionDialogFor = null },
                onRawMaterial = { showRawMaterialDialog = true; showActionDialogFor = null },
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
                onSelect = { variantName: String -> viewModel.addToCart(productToSelectMatrix!!, variantName); productToSelectMatrix = null },
                onDismiss = { productToSelectMatrix = null }
            )
        }

        if (productToSelectVariant != null) {
            ProductVariantDialog(
                product = productToSelectVariant!!,
                onSelect = { variant: String -> viewModel.addToCart(productToSelectVariant!!, variant); productToSelectVariant = null },
                onDismiss = { productToSelectVariant = null }
            )
        }

        if (showMergeOrderDialog) {
            MergeTableDialog(
                onMerge = { tableA: String, tableB: String -> viewModel.mergeOrder(tableA, tableB); showMergeOrderDialog = false },
                onDismiss = { showMergeOrderDialog = false }
            )
        }

        // Edit Serial Number berdasarkan CartItem spesifik (aman untuk multi-varian)
        if (cartItemToEditSerial != null) {
            val targetItem = cartItemToEditSerial!!
            SerialNumberDialog(
                product = targetItem.product,
                currentSerial = targetItem.serialNumber,
                onSave = { serial: String ->
                    viewModel.updateCartItemSerial(targetItem.product, targetItem.selectedVariation, serial)
                    cartItemToEditSerial = null
                },
                onDismiss = { cartItemToEditSerial = null }
            )
        }

        // Assign Staff berdasarkan CartItem spesifik (aman untuk multi-varian)
        if (cartItemToAssignStaff != null) {
            val targetItem = cartItemToAssignStaff!!
            AssignStaffDialog(
                product = targetItem.product,
                users = users,
                onAssign = { staff: String ->
                    viewModel.updateCartItemStaff(targetItem.product, targetItem.selectedVariation, staff)
                    cartItemToAssignStaff = null
                },
                onDismiss = { cartItemToAssignStaff = null }
            )
        }

        if (productToManageBom != null) {
            BomManagementDialog(
                product = productToManageBom!!,
                rawMaterials = rawMaterials,
                viewModel = viewModel,
                onDismiss = { productToManageBom = null }
            )
        }

        if (showRawMaterialDialog) {
            RawMaterialManagementDialog(
                rawMaterials = rawMaterials,
                viewModel = viewModel,
                onDismiss = { showRawMaterialDialog = false }
            )
        }

        if (showShiftReport != null) {
            ShiftReportDialog(shift = showShiftReport!!, onLogout = { showShiftReport = null; onLogout() })
        }

        if (showPinForDiscount) {
            PinDiscountDialog(
                onVerify = { pin: String ->
                    val hashedInput = hashPin(pin)
                    val supervisor = users.find { user ->
                        user.pin == hashedInput && (user.role == "OWNER" || user.role == "SUPERVISOR")
                    }
                    if (supervisor != null) {
                        showPinForDiscount = false
                        showDiscountDialog = true
                        Toast.makeText(context, "Diskon diotorisasi oleh ${supervisor.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "PIN Salah! Wajib menggunakan PIN Owner atau Supervisor.", Toast.LENGTH_SHORT).show()
                    }
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
                },
                onDismiss = { showDrawerConfirm = false }
            )
        }

        // Render StockOpnameDialog saat productToRestock dipilih di Layar Kasir
        if (productToRestock != null) {
            StockOpnameDialog(
                product = productToRestock!!,
                onDismiss = { productToRestock = null; variantToProcess = "" },
                onSave = { qty: Int ->
                    if (qty > 0) {
                        viewModel.addStock(productToRestock!!, variantToProcess, qty, currentUser)
                        Toast.makeText(context, "Stok berhasil ditambahkan (+$qty)", Toast.LENGTH_SHORT).show()
                    }
                    productToRestock = null
                    variantToProcess = ""
                }
            )
        }

        // WasteRecordDialog mendukung validasi & pengurangan stok per varian spesifik
        if (productToWaste != null) {
            val targetProd = productToWaste!!
            val maxAvailableStock = remember(targetProd, variantToProcess) {
                if (variantToProcess.isNotBlank() && targetProd.variations.isNotBlank() && !targetProd.variations.contains("|")) {
                    val part = targetProd.variations.split(",").find {
                        it.split(":").firstOrNull()?.trim().equals(variantToProcess, ignoreCase = true)
                    }
                    val pieces = part?.split(":")
                    val vStockStr = pieces?.getOrNull(3)?.trim()
                    if (!vStockStr.isNullOrEmpty()) vStockStr.toIntOrNull() ?: targetProd.stock else targetProd.stock
                } else {
                    targetProd.stock
                }
            }
            WasteRecordDialog(
                product = if (variantToProcess.isNotBlank()) targetProd.copy(name = "${targetProd.name} ($variantToProcess)", stock = maxAvailableStock) else targetProd,
                onDismiss = { productToWaste = null; variantToProcess = "" },
                onSave = { qty: Int ->
                    if (qty > 0 && qty <= maxAvailableStock) {
                        viewModel.recordWaste(targetProd, variantToProcess, qty, currentUser)
                        Toast.makeText(context, "Barang rusak tercatat (-$qty)", Toast.LENGTH_SHORT).show()
                        productToWaste = null
                        variantToProcess = ""
                    } else {
                        Toast.makeText(context, "Jumlah melebihi stok varian tersedia ($maxAvailableStock)!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // Edit Notes berdasarkan CartItem spesifik (aman untuk multi-varian)
        if (cartItemToEditNotes != null) {
            val targetItem = cartItemToEditNotes!!
            OrderNotesDialog(
                product = targetItem.product,
                currentNotes = targetItem.notes,
                onSave = { notes: String ->
                    viewModel.updateCartItemNotes(targetItem.product, targetItem.selectedVariation, notes)
                    cartItemToEditNotes = null
                },
                onDismiss = { cartItemToEditNotes = null }
            )
        }

        if (showOpenShiftDialog && currentOpenShift == null) {
            OpenShiftDialog(
                modalAwal = modalAwal,
                onModalChange = { input: String -> if (input.length <= 10) modalAwal = input },
                onConfirm = { viewModel.openShift(currentUser, modalAwal.toLongOrNull() ?: 0L); showOpenShiftDialog = false }
            )
        }

        if (showCloseShiftDialog) {
            val shiftToClose = currentOpenShift
            if (shiftToClose != null) {
                CloseShiftWizardDialog(
                    shift = shiftToClose, transactions = transactions, cashFlows = cashFlows,
                    onDismiss = { showCloseShiftDialog = false },
                    onConfirm = { uangFisik: Long, note: String ->
                        viewModel.closeShift(currentUser, uangFisik, note) { closedShift ->
                            showCloseShiftDialog = false
                            showShiftReport = closedShift
                        }
                    }
                )
            } else {
                Toast.makeText(context, "Tidak ada shift kasir yang sedang aktif.", Toast.LENGTH_SHORT).show()
                showCloseShiftDialog = false
            }
        }

        if (showCashFlowDialog) {
            CashFlowDialog(
                onSave = { type: String, amount: Long, note: String -> viewModel.addCashFlow(type, amount, note, currentUser); showCashFlowDialog = false },
                onDismiss = { showCashFlowDialog = false }
            )
        }

        if (showDiscountDialog) {
            ManualDiscountDialog(
                currentDiscount = discount,
                onSave = { amount: Long -> viewModel.setDiscount(amount); showDiscountDialog = false },
                onDismiss = { showDiscountDialog = false }
            )
        }

        if (showCustomerDialog && isCrmEnabled) {
            CustomerDialogV2(
                viewModel = viewModel,
                customers = customers, selectedCustomer = selectedCustomer,
                onSelect = { customer: CustomerEntity? ->
                    viewModel.setCustomer(customer)
                    showCustomerDialog = false
                },
                onAddCustomer = { name: String, phone: String, birthDate: Long -> viewModel.addCustomer(name, phone, birthDate) },
                onRedeemPoints = { points: Int -> viewModel.redeemPoints(points) },
                onDismiss = { showCustomerDialog = false }
            )
        }

        if (showTableSelector) {
            TableSelectorDialog(
                currentTable = selectedTable, occupiedTables = occupiedTables,
                onSelect = { table: String -> viewModel.setTable(table) },
                onDismiss = { showTableSelector = false }
            )
        }

        if (showVoucherDialog) {
            VoucherDialog(
                currentCode = appliedVoucher,
                onApply = { code: String -> viewModel.applyVoucher(code) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); if (success) showVoucherDialog = false } },
                onRemove = { viewModel.applyVoucher("") { _, _ -> }; showVoucherDialog = false },
                onDismiss = { showVoucherDialog = false }
            )
        }

        if (showHoldDialog) {
            HoldOrderDialog(
                holdOrders = holdOrders,
                onResume = { order: HoldOrderEntity -> viewModel.resumeHoldOrder(order); showHoldDialog = false },
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

        val canOpenProductMenu = currentUser.role == "OWNER" || currentUser.role == "SUPERVISOR"

        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            if (currentOpenShift != null && currentOpenShift.userId != currentUser.id) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Shift aktif dibuka oleh: ${currentOpenShift.userName} (Login: ${currentUser.name})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { showCloseShiftDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Ganti Shift", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
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
                                onProductLongClick = { prod: ProductEntity -> if (canOpenProductMenu) showActionDialogFor = prod },
                                onBarcodeScan = { barcode: String -> viewModel.processBarcodeScan(barcode) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); if (success) keyboardController?.hide() } },
                                onCameraScanClick = {
                                    val options = ScanOptions().apply { setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES); setPrompt("Scan Barcode Produk"); setCameraId(0); setBeepEnabled(false); setBarcodeImageEnabled(true); setOrientationLocked(true) }
                                    barcodeLauncher.launch(options)
                                },
                                searchFocusRequester = searchFocusRequester
                            )
                        }
                        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Box(modifier = Modifier.weight(0.4f).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                            PosCartSection(
                                cart = cart, subtotal = subtotal, discount = totalDiscount, taxAmount = taxAmount, serviceCharge = serviceChargeAmount, totalPrice = totalPrice,
                                selectedCustomer = if (isCrmEnabled) selectedCustomer else null, pointsUsed = pointsUsed, selectedTable = selectedTable,
                                selectedOrderType = selectedOrderType, pickupDate = pickupDate, appliedVoucher = appliedVoucher,
                                voucherDiscount = voucherDiscount, isSplitBillMode = isSplitBillMode, isTableModeEnabled = settingsManager.isTableModeEnabled,
                                enableOrderTypes = settingsManager.enableOrderTypes, enablePreOrder = settingsManager.enablePreOrder,
                                customerMemberships = customerMemberships, enableMembership = settingsManager.enableMembership,
                                enableCrm = isCrmEnabled,
                                onOrderTypeChange = { type: String -> viewModel.setOrderType(type) }, onPickupDateChange = { date: Long -> viewModel.setPickupDate(date) },
                                onToggleSplitMode = { isSplitBillMode = !isSplitBillMode }, onToggleSplitSelection = { item: CartItem -> viewModel.toggleSplitSelection(item.product, item.selectedVariation) },
                                onTableClick = { showTableSelector = true }, onVoucherClick = { showVoucherDialog = true },
                                onHoldClick = { if (cart.isNotEmpty()) { viewModel.saveHoldOrder(if (isCrmEnabled) (selectedCustomer?.name ?: "Pelanggan") else "Pelanggan"); Toast.makeText(context, "Pesanan diparkir.", Toast.LENGTH_SHORT).show() } },
                                onViewHoldClick = { showHoldDialog = true }, onCustomerClick = { showCustomerDialog = true },
                                onAdd = { item: CartItem -> viewModel.addToCart(item.product, item.selectedVariation) }, onRemove = { item: CartItem -> viewModel.removeFromCart(item.product, item.selectedVariation) },
                                onDelete = { item: CartItem -> viewModel.deleteItem(item.product, item.selectedVariation) },
                                onDiscountClick = { if (currentUser.role == "CASHIER" && settingsManager.requirePinForDiscount) { showPinForDiscount = true } else { showDiscountDialog = true } },
                                onCheckout = { showPaymentSheet = true },
                                onEditNotes = { item: CartItem -> cartItemToEditNotes = item },
                                onEditSerial = { item: CartItem -> cartItemToEditSerial = item },
                                onAssignStaff = { item: CartItem -> cartItemToAssignStaff = item },
                                onApplyMembership = { cartItem: CartItem, cm: CustomerMembershipEntity -> viewModel.applyMembershipToCart(cartItem, cm) },
                                onRemoveMembership = { cartItem: CartItem -> viewModel.removeMembershipFromCart(cartItem) },
                                lang = settingsManager.language,
                                isTaxInclusive = isTaxInclusive
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
                                selectedCustomer = if (isCrmEnabled) selectedCustomer else null, pointsUsed = pointsUsed, selectedTable = selectedTable,
                                selectedOrderType = selectedOrderType, pickupDate = pickupDate, appliedVoucher = appliedVoucher,
                                voucherDiscount = voucherDiscount, isSplitBillMode = isSplitBillMode, isTableModeEnabled = settingsManager.isTableModeEnabled,
                                enableOrderTypes = settingsManager.enableOrderTypes, enablePreOrder = settingsManager.enablePreOrder,
                                customerMemberships = customerMemberships, enableMembership = settingsManager.enableMembership,
                                enableCrm = isCrmEnabled,
                                onOrderTypeChange = { type: String -> viewModel.setOrderType(type) }, onPickupDateChange = { date: Long -> viewModel.setPickupDate(date) },
                                onToggleSplitMode = { isSplitBillMode = !isSplitBillMode }, onToggleSplitSelection = { item: CartItem -> viewModel.toggleSplitSelection(item.product, item.selectedVariation) },
                                onTableClick = { showTableSelector = true }, onVoucherClick = { showVoucherDialog = true },
                                onHoldClick = { if (cart.isNotEmpty()) { viewModel.saveHoldOrder(if (isCrmEnabled) (selectedCustomer?.name ?: "Pelanggan") else "Pelanggan"); Toast.makeText(context, "Pesanan diparkir.", Toast.LENGTH_SHORT).show() } },
                                onViewHoldClick = { showHoldDialog = true }, onCustomerClick = { showCustomerDialog = true },
                                onAdd = { item: CartItem -> viewModel.addToCart(item.product, item.selectedVariation) }, onRemove = { item: CartItem -> viewModel.removeFromCart(item.product, item.selectedVariation) },
                                onDelete = { item: CartItem -> viewModel.deleteItem(item.product, item.selectedVariation) },
                                onDiscountClick = { if (currentUser.role == "CASHIER" && settingsManager.requirePinForDiscount) { showPinForDiscount = true } else { showDiscountDialog = true } },
                                onCheckout = { showPaymentSheet = true },
                                onEditNotes = { item: CartItem -> cartItemToEditNotes = item },
                                onEditSerial = { item: CartItem -> cartItemToEditSerial = item },
                                onAssignStaff = { item: CartItem -> cartItemToAssignStaff = item },
                                onApplyMembership = { cartItem: CartItem, cm: CustomerMembershipEntity -> viewModel.applyMembershipToCart(cartItem, cm) },
                                onRemoveMembership = { cartItem: CartItem -> viewModel.removeMembershipFromCart(cartItem) },
                                lang = settingsManager.language,
                                isTaxInclusive = isTaxInclusive
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
                            onProductLongClick = { prod: ProductEntity -> if (canOpenProductMenu) showActionDialogFor = prod },
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
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = "Cart")
                Spacer(modifier = Modifier.width(8.dp))
                Text("${cart.sumOf { item -> item.quantity }} Item | ${formatRupiah(totalPrice)}", fontWeight = FontWeight.Bold)
            }
        }
    }
}
