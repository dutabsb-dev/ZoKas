package com.karyaku.kasir

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.karyaku.kasir.data.local.ProductBomEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatRupiah(amount: Long): String {
    val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    return format.format(amount).split(",")[0].replace("Rp", "Rp ")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PosScreen(
    viewModel: PosViewModel, 
    settingsManager: SettingsManager, 
    currentUser: UserEntity, 
    onLogout: () -> Unit,
    showCfd: Boolean,
    onDismissCfd: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isPhone = configuration.screenWidthDp < 600
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val lang = settingsManager.language
    
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val printerManager = remember { BluetoothPrinterManager(context) }
    
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
    val allProducts by viewModel.products.collectAsStateWithLifecycle()
    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val cashFlows by viewModel.cashFlows.collectAsStateWithLifecycle()

    val taxPercent by viewModel.taxPercent.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.setTaxPercent(settingsManager.taxPercent) }

    val lowStockCount = allProducts.count { !it.isService && it.stock <= 5 && it.stock >= 0 }

    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)
    val currentTimeString = String.format(Locale.getDefault(), "%02d:%02d", currentHour, currentMinute)
    
    val isHappyHour = remember(currentTimeString, settingsManager.happyHourDiscount) {
        if (settingsManager.happyHourDiscount > 0) {
            currentTimeString >= settingsManager.happyHourStart && currentTimeString <= settingsManager.happyHourEnd
        } else false
    }

    val subtotal = cart.sumOf { it.product.price * it.quantity }
    val happyHourDiscountAmount = if (isHappyHour) ((subtotal * settingsManager.happyHourDiscount) / 100).toLong() else 0L
    
    var tierDiscount = 0L
    if (selectedCustomer != null) {
        if (selectedCustomer!!.points >= 1000) tierDiscount = (subtotal * 0.10).toLong()
        else if (selectedCustomer!!.points >= 500) tierDiscount = (subtotal * 0.05).toLong()
        else if (selectedCustomer!!.points >= 100) tierDiscount = (subtotal * 0.02).toLong()
    }

    var promoDiscountUI = 0L
    cart.forEach { item ->
        val itemSubtotal = item.product.price * item.quantity
        if (item.product.promoType == "BOGO" && item.quantity >= 2) {
            val freeItems = item.quantity / 2
            promoDiscountUI += freeItems * item.product.price
        } else if (item.product.promoType == "TIERED" && item.quantity >= item.product.promoMinQty) {
            promoDiscountUI += (itemSubtotal * item.product.promoValue) / 100
        }
    }

    var totalDiscount = discount + happyHourDiscountAmount + tierDiscount + promoDiscountUI
    
    if (settingsManager.maxDiscountCap > 0 && totalDiscount > settingsManager.maxDiscountCap) {
        totalDiscount = settingsManager.maxDiscountCap
    }

    val taxAmount = ((subtotal - totalDiscount - voucherDiscount) * (taxPercent / 100.0)).toLong()
    val totalPrice = (subtotal + taxAmount - totalDiscount - voucherDiscount).coerceAtLeast(0)
    
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

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.processBarcodeScan(result.contents) { success, msg -> 
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                if (success) keyboardController?.hide()
            }
        }
    }

    LaunchedEffect(activeShift) { if (activeShift == null) showOpenShiftDialog = true }

    LaunchedEffect(completedTransaction) {
        if (completedTransaction != null) {
            val kitchenMac = settingsManager.kitchenPrinterMacAddress
            if (kitchenMac != null) {
                scope.launch {
                    val kitchenData = generateKitchenTicket(completedTransaction!!, viewModel)
                    val success = printerManager.printData(kitchenMac, kitchenData)
                    if (!success) {
                        withContext(Dispatchers.Main) { showPrinterErrorDialog = true }
                    }
                }
            }
            if (completedTransaction!!.paymentMethod == "CASH") {
                val printerMac = settingsManager.printerMacAddress
                if (printerMac != null) {
                    scope.launch { 
                        val success = printerManager.openCashDrawer(printerMac)
                        if (!success) {
                            withContext(Dispatchers.Main) { showPrinterErrorDialog = true }
                        }
                    }
                }
            }
        }
    }

    if (showPrinterErrorDialog) {
        PrinterErrorDialog { showPrinterErrorDialog = false }
    }

    Box(
        modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp) {
                when (event.key) {
                    Key.F1 -> { searchFocusRequester.requestFocus(); true }
                    Key.F2 -> { if (cart.isNotEmpty()) { showPaymentSheet = true; true } else false }
                    Key.F3 -> { if (cart.isNotEmpty()) { viewModel.saveHoldOrder(selectedCustomer?.name ?: "Customer"); true } else false }
                    Key.Escape -> { 
                        showPaymentSheet = false; showProductForm = false; showDiscountDialog = false; 
                        showCustomerDialog = false; showVoucherDialog = false; showHoldDialog = false; true 
                    }
                    else -> false
                }
            } else false
        }
    ) {
        // --- DIALOG MATRIX 2D (FASHION) ---
        if (productToSelectMatrix != null) {
            val product = productToSelectMatrix!!
            val parts = product.variations.split("|")
            val rows = parts[0].split(",").map { it.trim() }
            val cols = if (parts.size > 1) parts[1].split(",").map { it.trim() } else listOf("")

            AlertDialog(
                onDismissRequest = { productToSelectMatrix = null },
                icon = { Icon(Icons.Default.List, contentDescription = null, tint = EmeraldGreen) },
                title = { Text("${LanguageManager.tr("pilih_varian", lang)} (${product.name})") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Spacer(modifier = Modifier.width(60.dp))
                            cols.forEach { col -> Text(col, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) }
                        }
                        rows.forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(row, modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold)
                                cols.forEach { col ->
                                    val variantName = if (col.isBlank()) row else "$row - $col"
                                    Box(modifier = Modifier.weight(1f).padding(horizontal = 2.dp)) {
                                        Button(
                                            onClick = {
                                                viewModel.addToCart(product, variantName)
                                                productToSelectMatrix = null
                                            },
                                            modifier = Modifier.fillMaxWidth().height(40.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen.copy(alpha = 0.1f)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(LanguageManager.tr("pilih", lang), fontSize = 10.sp, color = EmeraldGreen)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { productToSelectMatrix = null }) { Text(LanguageManager.tr("batal", lang)) } }
            )
        }

        // --- DIALOG PILIH VARIAN (TEKS BIASA) ---
        if (productToSelectVariant != null) {
            val product = productToSelectVariant!!
            val variants = product.variations.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            AlertDialog(
                onDismissRequest = { productToSelectVariant = null },
                icon = { Icon(Icons.Default.List, contentDescription = null, tint = EmeraldGreen) },
                title = { Text(LanguageManager.tr("pilih_varian", lang)) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Please select a variant for ${product.name}:", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        variants.forEach { variant ->
                            OutlinedButton(
                                onClick = {
                                    viewModel.addToCart(product, variant)
                                    productToSelectVariant = null
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(variant, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { productToSelectVariant = null }) { Text(LanguageManager.tr("batal", lang)) } }
            )
        }

        // --- DIALOG MERGE ORDER (GABUNG MEJA) ---
        if (showMergeOrderDialog) {
            var tableA by remember { mutableStateOf("Table 1") }
            var tableB by remember { mutableStateOf("Table 2") }
            val tables = listOf("Table 1", "Table 2", "Table 3", "Table 4", "Table 5")
            
            AlertDialog(
                onDismissRequest = { showMergeOrderDialog = false },
                icon = { Icon(Icons.Default.Merge, contentDescription = null, tint = EmeraldGreen) },
                title = { Text("Merge Tables") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Select Main Table (Target):")
                        tables.forEach { t -> 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = tableA == t, onClick = { tableA = t }, colors = RadioButtonDefaults.colors(selectedColor = EmeraldGreen))
                                Text(t)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Select Table to Merge:")
                        tables.forEach { t -> 
                            if (t != tableA) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = tableB == t, onClick = { tableB = t }, colors = RadioButtonDefaults.colors(selectedColor = EmeraldGreen))
                                    Text(t)
                                }
                            }
                        }
                    }
                },
                confirmButton = { 
                    Button(onClick = { 
                        viewModel.mergeOrder(tableA, tableB)
                        showMergeOrderDialog = false 
                    }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text("MERGE") } 
                },
                dismissButton = { TextButton(onClick = { showMergeOrderDialog = false }) { Text(LanguageManager.tr("batal", lang)) } }
            )
        }

        // --- DIALOG SERIAL NUMBER ---
        if (productToEditSerial != null) {
            var serial by remember { mutableStateOf(cart.find { it.product.id == productToEditSerial!!.id }?.serialNumber ?: "") }
            AlertDialog(
                onDismissRequest = { productToEditSerial = null },
                icon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                title = { Text("Input IMEI / Serial Number") },
                text = {
                    Column {
                        Text("Enter serial number for ${productToEditSerial!!.name}:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = serial, onValueChange = { serial = it }, label = { Text("Serial Number") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                },
                confirmButton = { Button(onClick = { viewModel.updateCartItemSerial(productToEditSerial!!, cart.find { it.product.id == productToEditSerial!!.id }?.selectedVariation ?: "", serial); productToEditSerial = null }, shape = RoundedCornerShape(12.dp)) { Text("SAVE") } },
                dismissButton = { TextButton(onClick = { productToEditSerial = null }) { Text(LanguageManager.tr("batal", lang)) } }
            )
        }

        // --- DIALOG ASSIGN STAFF ---
        if (productToAssignStaff != null) {
            var selectedStaff by remember { mutableStateOf(cart.find { it.product.id == productToAssignStaff!!.id }?.assignedStaff ?: "") }
            val staffList = users.filter { it.role != "OWNER" }
            
            AlertDialog(
                onDismissRequest = { productToAssignStaff = null },
                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                title = { Text("Assign Staff / Therapist") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Select staff for ${productToAssignStaff!!.name}:")
                        Spacer(modifier = Modifier.height(8.dp))
                        staffList.forEach { user ->
                            OutlinedButton(
                                onClick = { selectedStaff = user.name; viewModel.updateCartItemStaff(productToAssignStaff!!, cart.find { it.product.id == productToAssignStaff!!.id }?.selectedVariation ?: "", user.name); productToAssignStaff = null },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text(user.name) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { productToAssignStaff = null }) { Text(LanguageManager.tr("batal", lang)) } }
            )
        }

        // --- DIALOG BOM (RESEP) ---
        if (productToManageBom != null) {
            BomManagementDialog(
                product = productToManageBom!!,
                rawMaterials = rawMaterials,
                viewModel = viewModel,
                onDismiss = { productToManageBom = null }
            )
        }

        // --- SHIFT REPORT DIALOG ---
        if (showShiftReport != null) {
            val shift = showShiftReport!!
            AlertDialog(
                onDismissRequest = { showShiftReport = null; onLogout() },
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen) },
                title = { Text("Cashier Closing Report") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Text("Cashier: ${shift.userName}", fontWeight = FontWeight.Bold)
                        Text("Open Time: ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(shift.startTime))}")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Opening Cash:"); Text(formatRupiah(shift.openingCash))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("System Cash:"); Text(formatRupiah(shift.expectedCash), color = EmeraldGreen, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Physical Cash:"); Text(formatRupiah(shift.closingCash ?: 0L), fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Difference:", fontWeight = FontWeight.Bold)
                            val selisih = shift.discrepancy ?: 0L
                            Text(formatRupiah(selisih), color = if (selisih < 0) ErrorRed else if (selisih > 0) SlateGrey else EmeraldGreen, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                },
                confirmButton = { Button(onClick = { showShiftReport = null; onLogout() }, shape = RoundedCornerShape(12.dp)) { Text("LOGOUT") } }
            )
        }

        // --- CFD MODE ---
        if (showCfd) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.White).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismissCfd() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("WELCOME TO", fontSize = 24.sp, color = SlateGrey, letterSpacing = 2.sp)
                    Text(settingsManager.storeName ?: "ZoKas Store", fontSize = 42.sp, fontWeight = FontWeight.Black, color = EmeraldGreen, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(48.dp))
                    Text("Your Total Bill:", fontSize = 28.sp, color = SlateGrey)
                    Text(formatRupiah(totalPrice), fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = AmberAccent)
                    Spacer(modifier = Modifier.height(48.dp))
                    Text("Thank you for your visit", fontSize = 20.sp, color = SlateGrey)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("(Tap anywhere to return)", fontSize = 12.sp, color = SlateGreyLight)
                }
            }
            return
        }

        // --- DIALOG PIN DISKON ---
        if (showPinForDiscount) {
            var pin by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showPinForDiscount = false },
                icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = AmberAccent) },
                title = { Text("Discount Authorization") },
                text = {
                    Column {
                        Text("Enter Supervisor/Owner PIN to apply discount:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pin, onValueChange = { pin = it }, label = { Text("PIN") }, 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), 
                            visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            val supervisor = viewModel.login(pin)
                            if (supervisor != null && (supervisor.role == "OWNER" || supervisor.role == "SUPERVISOR" || supervisor.id == currentUser.id)) {
                                showPinForDiscount = false; showDiscountDialog = true
                            } else {
                                Toast.makeText(context, "Wrong PIN or Access Denied!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text("VERIFY") }
                },
                dismissButton = { TextButton(onClick = { showPinForDiscount = false }) { Text(LanguageManager.tr("batal", lang)) } }
            )
        }

        // --- DIALOG AKSI PRODUK ---
        if (showActionDialogFor != null) {
            ModalBottomSheet(
                onDismissRequest = { showActionDialogFor = null },
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(modifier = Modifier.padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
                    Text(text = showActionDialogFor!!.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                    ActionMenuItem(icon = Icons.Default.Edit, title = "Edit Product", subtitle = "Change details", color = EmeraldGreen, onClick = { productToEdit = showActionDialogFor; showProductForm = true; showActionDialogFor = null })
                    ActionMenuItem(icon = Icons.Default.Add, title = "Add Stock", subtitle = "Restock item", color = AmberAccent, onClick = { productToRestock = showActionDialogFor; showActionDialogFor = null })
                    ActionMenuItem(icon = Icons.Default.Delete, title = "Damaged Item", subtitle = "Record waste/expired", color = AmberAccentDark, onClick = { productToWaste = showActionDialogFor; showActionDialogFor = null })
                    ActionMenuItem(icon = Icons.Default.List, title = "Manage Recipe (BOM)", subtitle = "Raw materials & composition", color = EmeraldGreenLight, onClick = { productToManageBom = showActionDialogFor; showActionDialogFor = null })
                    ActionMenuItem(icon = Icons.Default.List, title = "Save Barcode (JPG)", subtitle = "Save label to gallery", color = SlateGrey, onClick = { 
                        val product = showActionDialogFor!!
                        showActionDialogFor = null
                        saveBarcodeToGallery(context, product)
                    })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    ActionMenuItem(icon = Icons.Default.Delete, title = "Delete Product", subtitle = "Delete permanently", color = ErrorRed, onClick = { productToDelete = showActionDialogFor; showActionDialogFor = null })
                }
            }
        }

        // --- DIALOG LAINNYA ---
        if (showDrawerConfirm) {
            AlertDialog(
                onDismissRequest = { showDrawerConfirm = false },
                icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                title = { Text("Open Cash Drawer") },
                text = { Text("Open drawer without transaction? This activity will be logged.") },
                confirmButton = { Button(onClick = { viewModel.logCashDrawerOpen(currentUser); Toast.makeText(context, "Drawer opened.", Toast.LENGTH_SHORT).show(); showDrawerConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text("OPEN") } },
                dismissButton = { TextButton(onClick = { showDrawerConfirm = false }) { Text(LanguageManager.tr("batal", lang)) } }
            )
        }

        if (productToWaste != null) {
            var qty by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { productToWaste = null },
                icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed) },
                title = { Text("Record Damaged Item") },
                text = {
                    Column {
                        Text("Product: ${productToWaste!!.name}", fontWeight = FontWeight.Bold)
                        Text("Stock: ${productToWaste!!.stock} pcs", color = SlateGrey)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(value = qty, onValueChange = { if (it.length <= 5) qty = it }, label = { Text("Damaged Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                },
                confirmButton = {
                    Button(onClick = { 
                        val q = qty.toIntOrNull() ?: 0
                        if (q > 0 && q <= productToWaste!!.stock) { viewModel.recordWaste(productToWaste!!, q, currentUser); productToWaste = null }
                    }, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed), shape = RoundedCornerShape(12.dp)) { Text("SAVE") }
                },
                dismissButton = { TextButton(onClick = { productToWaste = null }) { Text(LanguageManager.tr("batal", lang)) } }
            )
        }

        if (productToEditNotes != null) {
            var notes by remember { mutableStateOf(cart.find { it.product.id == productToEditNotes!!.id }?.notes ?: "") }
            AlertDialog(
                onDismissRequest = { productToEditNotes = null },
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                title = { Text("Order Notes") },
                text = {
                    Column {
                        Text("Notes for ${productToEditNotes!!.name}:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("E.g., Less Ice") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                },
                confirmButton = { Button(onClick = { viewModel.updateCartItemNotes(productToEditNotes!!, cart.find { it.product.id == productToEditNotes!!.id }?.selectedVariation ?: "", notes); productToEditNotes = null }, shape = RoundedCornerShape(12.dp)) { Text("SAVE") } },
                dismissButton = { TextButton(onClick = { productToEditNotes = null }) { Text(LanguageManager.tr("batal", lang)) } }
            )
        }

        if (showOpenShiftDialog) {
            AlertDialog(
                onDismissRequest = { },
                icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = EmeraldGreen) },
                title = { Text("Open Cashier") },
                text = {
                    Column {
                        Text("Enter opening cash:")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(value = modalAwal, onValueChange = { if (it.length <= 10) modalAwal = it }, label = { Text("Opening Cash (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                },
                confirmButton = { Button(onClick = { viewModel.openShift(currentUser, modalAwal.toLongOrNull() ?: 0L); showOpenShiftDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = AmberAccent), shape = RoundedCornerShape(12.dp)) { Text("START") } }
            )
        }

        // WIZARD TUTUP KASIR DENGAN 3 TAHAP
        if (showCloseShiftDialog && activeShift != null) { 
            CloseShiftWizardDialog(
                shift = activeShift!!,
                transactions = transactions,
                cashFlows = cashFlows,
                onDismiss = { showCloseShiftDialog = false },
                onConfirm = { uangFisik, note -> 
                    viewModel.closeShift(currentUser, uangFisik, note) { closedShift -> 
                        showCloseShiftDialog = false
                        showShiftReport = closedShift 
                    } 
                }
            ) 
        }
        
        if (showCashFlowDialog) { CashFlowDialog(onDismiss = { showCashFlowDialog = false }, onSave = { type, amount, note -> viewModel.addCashFlow(type, amount, note, currentUser); showCashFlowDialog = false }) }
        if (showDiscountDialog) { DiscountDialog(currentDiscount = discount, onDismiss = { showDiscountDialog = false }, onSave = { amount -> viewModel.setDiscount(amount); showDiscountDialog = false }) }
        if (showCustomerDialog) { CustomerDialog(customers = customers, selectedCustomer = selectedCustomer, onDismiss = { showCustomerDialog = false }, onSelect = { viewModel.setCustomer(it); showCustomerDialog = false }, onAddCustomer = { name, phone -> viewModel.addCustomer(name, phone) }, onRedeemPoints = { points -> viewModel.redeemPoints(points) }) }
        if (showTableSelector) { TableSelectorDialog(currentTable = selectedTable, occupiedTables = occupiedTables, onDismiss = { showTableSelector = false }, onSelect = { viewModel.setTable(it) }) }
        if (showVoucherDialog) { VoucherDialog(currentCode = appliedVoucher, onDismiss = { showVoucherDialog = false }, onApply = { code -> viewModel.applyVoucher(code) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); if (success) showVoucherDialog = false } }, onRemove = { viewModel.applyVoucher("") { _, _ -> }; showVoucherDialog = false }) }
        if (showHoldDialog) { HoldOrderDialog(holdOrders = holdOrders, onDismiss = { showHoldDialog = false }, onResume = { viewModel.resumeHoldOrder(it); showHoldDialog = false }) }

        if (completedTransaction != null) {
            ReceiptDialog(transaction = completedTransaction!!, viewModel = viewModel, storeName = settingsManager.storeName ?: "ZoKas", storeAddress = settingsManager.storeAddress ?: "", storePhone = settingsManager.storePhone ?: "", storeFooter = settingsManager.storeFooter ?: "", settingsManager = settingsManager, onDismiss = { completedTransaction = null })
        }

        if (showPaymentSheet) {
            val totalToPay = if (isSplitBillMode) cart.filter { it.isSelectedForSplit }.sumOf { it.product.price * it.quantity } else totalPrice
            PaymentBottomSheet(
                totalPrice = totalToPay, 
                totalCommission = cart.sumOf { it.product.commissionPerItem * it.quantity },
                settingsManager = settingsManager,
                onDismiss = { showPaymentSheet = false }, 
                onConfirmPayment = { cashReceived, method ->
                    viewModel.processPayment(cashReceived, currentUser.id, currentUser.name, isSplitBillMode, method, settingsManager.maxDiscountCap) { newTransaction ->
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
                productToEdit = productToEdit, 
                onDismiss = { showProductForm = false; productToEdit = null }, 
                onSave = { name, price, stock, category, costPrice, barcode, variations, commission, expiry, isService, isCombo, imagePath ->
                    if (productToEdit == null) viewModel.addProduct(name, price, stock, category, costPrice, barcode, variations, commission, expiry, isService, isCombo, imagePath, currentUser.id, currentUser.name)
                    else viewModel.updateProduct(productToEdit!!.copy(name = name, price = price, stock = stock, category = category, costPrice = costPrice, barcode = barcode, variations = variations, commissionPerItem = commission, expiryDate = expiry, isService = isService, isCombo = isCombo, imagePath = imagePath), currentUser.id, currentUser.name)
                    showProductForm = false; productToEdit = null
                }
            )
        }

        if (productToRestock != null) {
            StockOpnameDialog(product = productToRestock!!, onDismiss = { productToRestock = null }, onSave = { qty -> viewModel.addStock(productToRestock!!, qty, currentUser); productToRestock = null })
        }

        if (productToDelete != null) {
            AlertDialog(
                onDismissRequest = { productToDelete = null },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed) },
                title = { Text("Delete Product?") },
                text = { Text("Delete '${productToDelete!!.name}' permanently?") },
                confirmButton = { Button(onClick = { viewModel.deleteProduct(productToDelete!!, currentUser.id, currentUser.name); productToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed), shape = RoundedCornerShape(12.dp)) { Text("DELETE") } },
                dismissButton = { TextButton(onClick = { productToDelete = null }) { Text(LanguageManager.tr("batal", lang)) } }
            )
        }

        // --- MAIN UI (TANPA SCAFFOLD GANDA) ---
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            
            // KONTEN UTAMA
            AnimatedVisibility(visible = isHappyHour, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Box(modifier = Modifier.fillMaxWidth().background(Brush.horizontalGradient(colors = listOf(ErrorRed, AmberAccent))).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("HAPPY HOUR! ${settingsManager.happyHourDiscount}% OFF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            // BoxWithConstraints yang lebih adaptif
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Deteksi jika layar lebar (Tablet ATAU Landscape HP)
                val isWideScreen = maxWidth > 600.dp || isLandscape
                
                if (isWideScreen) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(0.6f).fillMaxHeight()) { 
                            ProductCatalog(
                                products = filteredProducts, categories = categories, selectedCategory = selectedCategory, 
                                onCategorySelect = { viewModel.setCategory(it) }, 
                                onProductClick = { prod -> 
                                    if (prod.variations.contains("|")) {
                                        productToSelectMatrix = prod
                                    } else if (prod.variations.isNotEmpty()) { 
                                        productToSelectVariant = prod 
                                    } else { 
                                        viewModel.addToCart(prod) 
                                    }
                                }, 
                                onProductLongClick = { showActionDialogFor = it }, 
                                searchFocusRequester = searchFocusRequester,
                                lang = lang
                            ) 
                        }
                        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        Box(modifier = Modifier.weight(0.4f).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                            CartList(
                                cart = cart, subtotal = subtotal, discount = totalDiscount, taxAmount = taxAmount, totalPrice = totalPrice, selectedCustomer = selectedCustomer, pointsUsed = pointsUsed, selectedTable = selectedTable, appliedVoucher = appliedVoucher, voucherDiscount = voucherDiscount, isSplitBillMode = isSplitBillMode, 
                                isTableModeEnabled = settingsManager.isTableModeEnabled,
                                onToggleSplitMode = { isSplitBillMode = !isSplitBillMode }, onToggleSplitSelection = { viewModel.toggleSplitSelection(it.product, it.selectedVariation) }, onTableClick = { showTableSelector = true }, onVoucherClick = { showVoucherDialog = true }, 
                                onHoldClick = { if (cart.isNotEmpty()) { viewModel.saveHoldOrder(selectedCustomer?.name ?: "Customer"); Toast.makeText(context, "Order held.", Toast.LENGTH_SHORT).show() } }, onViewHoldClick = { showHoldDialog = true }, onCustomerClick = { showCustomerDialog = true }, 
                                onAdd = { viewModel.addToCart(it.product, it.selectedVariation) }, onRemove = { viewModel.removeFromCart(it.product, it.selectedVariation) }, onDelete = { viewModel.deleteItem(it.product, it.selectedVariation) }, 
                                onDiscountClick = { if (currentUser.role == "CASHIER" && settingsManager.requirePinForDiscount) { showPinForDiscount = true } else { showDiscountDialog = true } }, 
                                onCheckout = { showPaymentSheet = true }, onEditNotes = { productToEditNotes = it },
                                onEditSerial = { productToEditSerial = it }, onAssignStaff = { productToAssignStaff = it },
                                lang = lang
                            )
                        }
                    }
                } else {
                    if (showCartOnPhone) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TextButton(onClick = { showCartOnPhone = false }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text(LanguageManager.tr("kembali_katalog", lang), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                            CartList(
                                cart = cart, subtotal = subtotal, discount = totalDiscount, taxAmount = taxAmount, totalPrice = totalPrice, selectedCustomer = selectedCustomer, pointsUsed = pointsUsed, selectedTable = selectedTable, appliedVoucher = appliedVoucher, voucherDiscount = voucherDiscount, isSplitBillMode = isSplitBillMode, 
                                isTableModeEnabled = settingsManager.isTableModeEnabled,
                                onToggleSplitMode = { isSplitBillMode = !isSplitBillMode }, onToggleSplitSelection = { viewModel.toggleSplitSelection(it.product, it.selectedVariation) }, onTableClick = { showTableSelector = true }, onVoucherClick = { showVoucherDialog = true }, 
                                onHoldClick = { if (cart.isNotEmpty()) { viewModel.saveHoldOrder(selectedCustomer?.name ?: "Customer"); Toast.makeText(context, "Order held.", Toast.LENGTH_SHORT).show() } }, onViewHoldClick = { showHoldDialog = true }, onCustomerClick = { showCustomerDialog = true }, 
                                onAdd = { viewModel.addToCart(it.product, it.selectedVariation) }, onRemove = { viewModel.removeFromCart(it.product, it.selectedVariation) }, onDelete = { viewModel.deleteItem(it.product, it.selectedVariation) }, 
                                onDiscountClick = { if (currentUser.role == "CASHIER" && settingsManager.requirePinForDiscount) { showPinForDiscount = true } else { showDiscountDialog = true } }, 
                                onCheckout = { showPaymentSheet = true }, onEditNotes = { productToEditNotes = it },
                                onEditSerial = { productToEditSerial = it }, onAssignStaff = { productToAssignStaff = it },
                                lang = lang
                            )
                        }
                    } else {
                        ProductCatalog(
                            products = filteredProducts, categories = categories, selectedCategory = selectedCategory, 
                            onCategorySelect = { viewModel.setCategory(it) }, 
                            onProductClick = { prod -> 
                                if (prod.variations.contains("|")) {
                                    productToSelectMatrix = prod
                                } else if (prod.variations.isNotEmpty()) { 
                                    productToSelectVariant = prod 
                                } else { 
                                    viewModel.addToCart(prod) 
                                }
                            }, 
                            onProductLongClick = { showActionDialogFor = it }, 
                            searchFocusRequester = searchFocusRequester,
                            lang = lang
                        )
                    }
                }
            }
        }
        
        // FLOATING ACTION BUTTON UNTUK KERANJANG (Hanya di HP Portrait)
        if (cart.isNotEmpty() && !showCartOnPhone && isPhone && !isLandscape) {
            ExtendedFloatingActionButton(
                onClick = { showCartOnPhone = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = EmeraldGreen,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = "Cart")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${cart.sumOf { it.quantity }} Item | ${formatRupiah(totalPrice)}",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BomManagementDialog(
    product: ProductEntity,
    rawMaterials: List<RawMaterialEntity>,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    var selectedMaterial by remember { mutableStateOf<RawMaterialEntity?>(null) }
    var qtyUsed by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.List, contentDescription = null, tint = EmeraldGreenLight) },
        title = { Text("Recipe (BOM): ${product.name}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Add raw materials used to make this product.", fontSize = 12.sp, color = SlateGrey)
                Spacer(modifier = Modifier.height(12.dp))
                
                if (rawMaterials.isEmpty()) {
                    Surface(color = ErrorRed.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("No Raw Material data. Add it in Settings.", fontSize = 12.sp, color = ErrorRed, modifier = Modifier.padding(12.dp))
                    }
                } else {
                    Text("Select Raw Material:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    rawMaterials.forEach { material ->
                        OutlinedButton(
                            onClick = { selectedMaterial = material },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selectedMaterial?.id == material.id) EmeraldGreen.copy(alpha = 0.2f) else Color.Transparent
                            )
                        ) {
                            Text("${material.name} (Remaining: ${material.stock} ${material.unit})")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = qtyUsed,
                        onValueChange = { if (it.length <= 5) qtyUsed = it },
                        label = { Text("Quantity Used (${selectedMaterial?.unit ?: "Select material first"})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedMaterial != null,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = qtyUsed.toDoubleOrNull() ?: 0.0
                    if (selectedMaterial != null && qty > 0) {
                        viewModel.addBom(product.id, selectedMaterial!!.id, qty)
                        onDismiss()
                    }
                },
                enabled = selectedMaterial != null && qtyUsed.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(12.dp)
            ) { Text("SAVE RECIPE") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun ActionMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp)) }
        Spacer(modifier = Modifier.width(16.dp))
        Column { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(subtitle, fontSize = 12.sp, color = SlateGrey) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductCatalog(
    products: List<ProductEntity>, categories: List<String>, selectedCategory: String, 
    onCategorySelect: (String) -> Unit, 
    onProductClick: (ProductEntity) -> Unit, onProductLongClick: (ProductEntity) -> Unit, 
    searchFocusRequester: FocusRequester,
    lang: String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // SEARCH BAR DIHAPUS DARI SINI
        
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                FilterChip(
                    selected = category == selectedCategory, 
                    onClick = { onCategorySelect(category) }, 
                    label = { Text(category, fontWeight = if(category == selectedCategory) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp) }, 
                    shape = RoundedCornerShape(12.dp), 
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldGreen, selectedLabelColor = Color.White, containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
        if (products.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Product not found.", color = SlateGrey, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp), 
                horizontalArrangement = Arrangement.spacedBy(12.dp), 
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = products, key = { it.id }) { product ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.95f else 1f, 
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f), 
                        label = "scale"
                    )
                    
                    val isExpiringSoon = product.expiryDate > 0 && (product.expiryDate - System.currentTimeMillis()) < (7L * 24 * 60 * 60 * 1000)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.85f) // Sedikit lebih tinggi untuk gambar
                            .scale(scale)
                            .shadow(4.dp, RoundedCornerShape(12.dp), spotColor = EmeraldGreenDark)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                interactionSource = interactionSource, 
                                indication = null, 
                                onClick = { onProductClick(product) }, 
                                onLongClick = { onProductLongClick(product) }
                            ),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // TAMPILKAN GAMBAR PRODUK
                            if (product.imagePath.isNotEmpty()) {
                                AsyncImage(
                                    model = java.io.File(product.imagePath),
                                    contentDescription = product.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // GANTI OVERLAY GELAP DENGAN GRADIENT AGAR GAMBAR TERANG
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                                startY = 150f
                                            )
                                        )
                                )
                            } else {
                                // Fallback jika tidak ada gambar
                                Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(colors = if (isExpiringSoon) listOf(ErrorRed, Color(0xFFB71C1C)) else listOf(EmeraldGreen, EmeraldGreenDark))))
                            }

                            Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Surface(color = Color.White.copy(alpha = 0.3f), shape = RoundedCornerShape(6.dp)) { 
                                        Text(text = product.category, fontSize = 9.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) 
                                    }
                                    if (product.promoType != "NONE") {
                                        Surface(color = AmberAccent, shape = RoundedCornerShape(6.dp)) { 
                                            Text(text = if (product.promoType == "BOGO") "BOGO" else "DISC ${product.promoValue}%", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) 
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.weight(1f))
                                
                                Column {
                                    Text(text = product.name, fontWeight = FontWeight.ExtraBold, maxLines = 2, style = MaterialTheme.typography.titleSmall, color = Color.White, lineHeight = 16.sp)
                                    if (isExpiringSoon) { Text("EXPIRED SOON", fontSize = 9.sp, color = Color.Yellow, fontWeight = FontWeight.Bold) }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = formatRupiah(product.price), style = MaterialTheme.typography.titleMedium, color = AmberAccent, fontWeight = FontWeight.Black)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (product.isService) {
                                            Icon(Icons.Default.Build, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(10.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(text = "Service", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                                        } else {
                                            Icon(Icons.Default.Inventory, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(10.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(text = "Stock: ${product.stock}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CartList(
    cart: List<CartItem>, subtotal: Long, discount: Long, taxAmount: Long, totalPrice: Long, 
    selectedCustomer: CustomerEntity?, pointsUsed: Int,
    selectedTable: String, appliedVoucher: String, voucherDiscount: Long, isSplitBillMode: Boolean,
    isTableModeEnabled: Boolean,
    onToggleSplitMode: () -> Unit, onToggleSplitSelection: (CartItem) -> Unit, onTableClick: () -> Unit, onVoucherClick: () -> Unit,
    onHoldClick: () -> Unit, onViewHoldClick: () -> Unit, onCustomerClick: () -> Unit, onAdd: (CartItem) -> Unit, 
    onRemove: (CartItem) -> Unit, onDelete: (CartItem) -> Unit, onDiscountClick: () -> Unit, onCheckout: () -> Unit,
    onEditNotes: (ProductEntity) -> Unit, onEditSerial: (ProductEntity) -> Unit, onAssignStaff: (ProductEntity) -> Unit,
    lang: String
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).imePadding()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isTableModeEnabled) {
                OutlinedButton(onClick = onTableClick, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, AmberAccent)) { Text(selectedTable, fontWeight = FontWeight.Bold, color = AmberAccent, fontSize = 11.sp, maxLines = 1) }
            }
            OutlinedButton(onClick = onHoldClick, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(12.dp)) { Text("Hold (F3)", fontWeight = FontWeight.Bold, color = EmeraldGreen, fontSize = 11.sp) }
            OutlinedButton(onClick = onViewHoldClick, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(12.dp)) { Text("View", fontWeight = FontWeight.Bold, color = EmeraldGreen, fontSize = 11.sp) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCustomerClick, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.Person, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = if (selectedCustomer != null) "${selectedCustomer.name} (${selectedCustomer.points} Pts)" else "General Customer", fontWeight = FontWeight.Bold, color = AmberAccent, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Split Bill", fontSize = 12.sp); Spacer(modifier = Modifier.width(4.dp)); Switch(checked = isSplitBillMode, onCheckedChange = { onToggleSplitMode() }, modifier = Modifier.scale(0.8f), colors = SwitchDefaults.colors(checkedTrackColor = EmeraldGreen))
            }
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(cart) { item ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        if (isSplitBillMode) { Checkbox(checked = item.isSelectedForSplit, onCheckedChange = { onToggleSplitSelection(item) }, modifier = Modifier.scale(0.8f), colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen)) }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (item.selectedVariation.isNotEmpty()) {
                                Text("Variant: ${item.selectedVariation}", fontSize = 10.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                            }
                            if (item.serialNumber.isNotEmpty()) {
                                Text("SN: ${item.serialNumber}", fontSize = 10.sp, color = SlateGrey, fontWeight = FontWeight.Bold)
                            }
                            if (item.assignedStaff.isNotEmpty()) {
                                Text("Staff: ${item.assignedStaff}", fontSize = 10.sp, color = AmberAccent, fontWeight = FontWeight.Bold)
                            }
                            Text(formatRupiah(item.product.price), fontSize = 11.sp, color = SlateGrey)
                            if (item.notes.isNotEmpty()) { Text("Note: ${item.notes}", fontSize = 10.sp, color = ErrorRed, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onEditNotes(item.product) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Edit, contentDescription = "Edit Notes", tint = EmeraldGreen, modifier = Modifier.size(16.dp)) }
                            if (item.product.isService) {
                                IconButton(onClick = { onAssignStaff(item.product) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Person, contentDescription = "Assign Staff", tint = AmberAccent, modifier = Modifier.size(16.dp)) }
                            }
                            if (item.product.category == "Elektronik" || item.product.isService) {
                                IconButton(onClick = { onEditSerial(item.product) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.List, contentDescription = "Input SN", tint = SlateGrey, modifier = Modifier.size(16.dp)) }
                            }
                            IconButton(onClick = { onRemove(item) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Remove, contentDescription = "Reduce", modifier = Modifier.size(16.dp)) }
                            Text(text = "${item.quantity}", modifier = Modifier.padding(horizontal = 4.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            IconButton(onClick = { onAdd(item) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp)) }
                            IconButton(onClick = { onDelete(item) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Subtotal:", fontSize = 12.sp, color = SlateGrey)
            Text(formatRupiah(subtotal), fontSize = 12.sp, color = SlateGrey)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Discount:", fontSize = 12.sp, color = SlateGrey)
                TextButton(onClick = onDiscountClick, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Change", color = AmberAccent, fontSize = 11.sp) }
            }
            Text("- ${formatRupiah(discount)}", fontSize = 12.sp, color = ErrorRed)
        }
        if (taxAmount > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Tax:", fontSize = 12.sp, color = SlateGrey)
                Text("+ ${formatRupiah(taxAmount)}", fontSize = 12.sp, color = AmberAccent)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Total:", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(formatRupiah(totalPrice), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onCheckout, 
            modifier = Modifier.fillMaxWidth().height(48.dp), 
            enabled = cart.isNotEmpty(), 
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), 
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("PAY (F2)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormBottomSheet(
    productToEdit: ProductEntity? = null, 
    onDismiss: () -> Unit, 
    onSave: (String, Long, Int, String, Long, String, String, Long, Long, Boolean, Boolean, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    var name by remember { mutableStateOf(productToEdit?.name ?: "") }
    var price by remember { mutableStateOf(productToEdit?.price?.toString() ?: "") }
    var costPrice by remember { mutableStateOf(productToEdit?.costPrice?.toString() ?: "") } 
    var stock by remember { mutableStateOf(productToEdit?.stock?.toString() ?: "") }
    var category by remember { mutableStateOf(productToEdit?.category ?: "General") } 
    var barcode by remember { mutableStateOf(productToEdit?.barcode ?: "") }
    var variations by remember { mutableStateOf(productToEdit?.variations ?: "") }
    var commission by remember { mutableStateOf(productToEdit?.commissionPerItem?.toString() ?: "") }
    var expiryDate by remember { mutableStateOf(productToEdit?.expiryDate ?: 0L) }
    var isService by remember { mutableStateOf(productToEdit?.isService ?: false) }
    var isCombo by remember { mutableStateOf(productToEdit?.isCombo ?: false) }
    
    // STATE UNTUK GAMBAR
    var imagePath by remember { mutableStateOf(productToEdit?.imagePath ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val expiryText = if (expiryDate > 0) dateFormat.format(Date(expiryDate)) else "Select Date"

    // Launcher untuk memilih gambar
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            // Simpan gambar ke penyimpanan internal aplikasi agar permanen
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = File(context.filesDir, "img_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                imagePath = file.absolutePath
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = if (expiryDate > 0) expiryDate else System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    expiryDate = datePickerState.selectedDateMillis ?: 0L
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()), 
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (productToEdit == null) "Add New Product" else "Edit Product", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(16.dp))
            
            // --- FITUR UPLOAD GAMBAR ---
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, SlateGrey, RoundedCornerShape(12.dp))
                    .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (imagePath.isNotEmpty()) {
                    AsyncImage(
                        model = File(imagePath),
                        contentDescription = "Product Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "Upload", tint = SlateGrey)
                        Text("Upload Image", fontSize = 10.sp, color = SlateGrey)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { isService = !isService }) {
                    Checkbox(checked = isService, onCheckedChange = { isService = it }, colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen))
                    Text("Service Product (No Stock)", fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { isCombo = !isCombo }) {
                    Checkbox(checked = isCombo, onCheckedChange = { isCombo = it }, colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen))
                    Text("Combo/Package Product", fontSize = 14.sp)
                }
            }
            
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = variations, onValueChange = { variations = it }, label = { Text("Variations (Comma / | for Matrix)") }, placeholder = { Text("E.g., S,M,L | Red,Blue") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("Barcode (Optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = costPrice, onValueChange = { if (it.length <= 10) costPrice = it }, label = { Text("Cost Price (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = price, onValueChange = { if (it.length <= 10) price = it }, label = { Text("Selling Price (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            if (!isService) {
                OutlinedTextField(value = stock, onValueChange = { if (it.length <= 5) stock = it }, label = { Text("Stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedTextField(value = commission, onValueChange = { if (it.length <= 10) commission = it }, label = { Text("Cashier Commission / Item (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SlateGrey)
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null, tint = AmberAccent)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Expiry: $expiryText", color = Color.Black)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { 
                val p = price.toLongOrNull() ?: 0L
                val cp = costPrice.toLongOrNull() ?: 0L
                val s = if (isService) 0 else stock.toIntOrNull() ?: 0
                val c = commission.toLongOrNull() ?: 0L
                if (name.isNotBlank() && p > 0) onSave(name, p, s, category.ifBlank { "General" }, cp, barcode, variations, c, expiryDate, isService, isCombo, imagePath) 
            }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = name.isNotBlank() && (price.toLongOrNull() ?: 0L) > 0, colors = ButtonDefaults.buttonColors(containerColor = AmberAccent), shape = RoundedCornerShape(12.dp)) {
                Text(if (productToEdit == null) "SAVE PRODUCT" else "UPDATE PRODUCT", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentBottomSheet(
    totalPrice: Long, 
    totalCommission: Long,
    settingsManager: SettingsManager,
    onDismiss: () -> Unit, 
    onConfirmPayment: (Long, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var cashInput by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("CASH") }
    val cashReceived = cashInput.toLongOrNull() ?: 0L
    val change = if (cashReceived >= totalPrice) cashReceived - totalPrice else 0L

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()), 
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Payment", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Total Bill:", style = MaterialTheme.typography.titleMedium, color = SlateGrey)
            Text(formatRupiah(totalPrice), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            
            if (settingsManager.enableMultiCurrency && settingsManager.exchangeRateUsd > 0) {
                val usdAmount = totalPrice / settingsManager.exchangeRateUsd
                val formattedUsd = String.format(Locale.US, "$%.2f", usdAmount)
                Text("≈ $formattedUsd USD", fontSize = 16.sp, color = SlateGrey)
            }

            if (totalCommission > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(color = EmeraldGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                    Text("Cashier Commission: +${formatRupiah(totalCommission)}", color = EmeraldGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp), fontSize = 12.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { paymentMethod = "CASH" }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = if (paymentMethod == "CASH") EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant)) { Text("CASH", color = if (paymentMethod == "CASH") Color.White else SlateGrey, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Button(onClick = { paymentMethod = "QRIS" }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = if (paymentMethod == "QRIS") SlateGrey else MaterialTheme.colorScheme.surfaceVariant)) { Text("QRIS", color = if (paymentMethod == "QRIS") Color.White else SlateGrey, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Button(onClick = { paymentMethod = "BON" }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = if (paymentMethod == "BON") AmberAccent else MaterialTheme.colorScheme.surfaceVariant)) { Text("BON", color = if (paymentMethod == "BON") Color.White else SlateGrey, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (paymentMethod == "CASH") {
                OutlinedTextField(value = cashInput, onValueChange = { if (it.length <= 10) cashInput = it }, label = { Text("Cash Received (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Change:", style = MaterialTheme.typography.titleMedium)
                    Text(formatRupiah(change), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (change > 0) EmeraldGreen else MaterialTheme.colorScheme.onSurface)
                }
            } else if (paymentMethod == "QRIS") {
                Box(modifier = Modifier.size(200.dp).background(SlateGreyLight, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text("QRIS CODE\n(Mock)", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = SlateGrey)
                }
            } else {
                Surface(color = ErrorRed.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Transaction will be recorded as BON (Unpaid).", color = ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { onConfirmPayment(if (paymentMethod == "BON" || paymentMethod == "QRIS") totalPrice else cashReceived, paymentMethod) }, 
                modifier = Modifier.fillMaxWidth().height(56.dp), 
                enabled = (paymentMethod == "BON" || paymentMethod == "QRIS") || (cashReceived >= totalPrice && totalPrice > 0), 
                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                shape = RoundedCornerShape(12.dp)
            ) { 
                Text("COMPLETE PAYMENT", fontSize = 16.sp, fontWeight = FontWeight.Bold) 
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun TableSelectorDialog(currentTable: String, occupiedTables: List<String>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val tables = listOf("Take-Away", "Table 1", "Table 2", "Table 3", "Table 4", "Table 5")
    AlertDialog(
        onDismissRequest = onDismiss, icon = { Icon(Icons.Default.List, contentDescription = null, tint = AmberAccent) },
        title = { Text("Select Table") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                for (table in tables) {
                    val isOccupied = occupiedTables.contains(table)
                    OutlinedButton(
                        onClick = { onSelect(table); onDismiss() }, 
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), 
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (table == currentTable) EmeraldGreen.copy(alpha = 0.2f) else if (isOccupied) ErrorRed.copy(alpha = 0.1f) else Color.Transparent), 
                        border = if (isOccupied) BorderStroke(1.dp, ErrorRed) else null, 
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = if (isOccupied) "$table (Occupied)" else table, color = if (isOccupied) ErrorRed else if (table == currentTable) EmeraldGreen else MaterialTheme.colorScheme.onSurface, fontWeight = if (isOccupied) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun VoucherDialog(currentCode: String, onDismiss: () -> Unit, onApply: (String) -> Unit, onRemove: () -> Unit) {
    var code by remember { mutableStateOf(currentCode) }
    AlertDialog(
        onDismissRequest = onDismiss, icon = { Icon(Icons.Default.Star, contentDescription = null, tint = EmeraldGreen) },
        title = { Text("Use Voucher") },
        text = { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { OutlinedTextField(value = code, onValueChange = { code = it.uppercase() }, label = { Text("Voucher Code") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)) } },
        confirmButton = { Button(onClick = { onApply(code) }, enabled = code.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text("APPLY") } },
        dismissButton = { if (currentCode.isNotEmpty()) TextButton(onClick = onRemove) { Text("Remove", color = ErrorRed) } else TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun CustomerDialog(customers: List<CustomerEntity>, selectedCustomer: CustomerEntity?, onDismiss: () -> Unit, onSelect: (CustomerEntity?) -> Unit, onAddCustomer: (String, String) -> Unit, onRedeemPoints: (Int) -> Unit) {
    var showAddForm by remember { mutableStateOf(false) }
    var showRedeemForm by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var pointsToRedeem by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    val filteredCustomers = customers.filter { it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss, icon = { Icon(Icons.Default.Person, contentDescription = null, tint = AmberAccent) },
        title = { Text(when { showAddForm -> "Add Customer"; showRedeemForm -> "Redeem Points"; else -> "Select Customer" }) },
        text = {
            when {
                showAddForm -> { Column { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)); Spacer(modifier = Modifier.height(8.dp)); OutlinedTextField(value = newPhone, onValueChange = { newPhone = it }, label = { Text("Phone No") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) } }
                showRedeemForm && selectedCustomer != null -> { Column { Text("Points: ${selectedCustomer.points}"); Spacer(modifier = Modifier.height(8.dp)); OutlinedTextField(value = pointsToRedeem, onValueChange = { if (it.length <= 5) pointsToRedeem = it }, label = { Text("Points Amount") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) } }
                else -> {
                    Column {
                        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), placeholder = { Text("Search...") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, shape = RoundedCornerShape(12.dp))
                        TextButton(onClick = { onSelect(null); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("General Customer", fontWeight = FontWeight.Bold, color = AmberAccent) }
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) { 
                            items(filteredCustomers) { customer -> 
                                TextButton(onClick = { onSelect(customer); onDismiss() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { 
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
                                        Column {
                                            Text("${customer.name} (${customer.points} Pts)", fontWeight = FontWeight.Bold)
                                            val tier = if (customer.points >= 1000) "Platinum (10%)" else if (customer.points >= 500) "Gold (5%)" else if (customer.points >= 100) "Silver (2%)" else "Member"
                                            Text(tier, fontSize = 11.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                                        }
                                        Text(if (customer.phone == "-") "" else customer.phone, fontSize = 12.sp, color = SlateGrey) 
                                    } 
                                } 
                            } 
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                showAddForm -> { Button(onClick = { if (newName.isNotBlank()) { onAddCustomer(newName, if(newPhone.isBlank()) "-" else newPhone); showAddForm = false } }, colors = ButtonDefaults.buttonColors(containerColor = AmberAccent), shape = RoundedCornerShape(12.dp)) { Text("SAVE") } }
                showRedeemForm && selectedCustomer != null -> { Button(onClick = { val pts = pointsToRedeem.toIntOrNull() ?: 0; if (pts > 0 && pts <= selectedCustomer.points) { onRedeemPoints(pts); onDismiss() } }, colors = ButtonDefaults.buttonColors(containerColor = AmberAccent), shape = RoundedCornerShape(12.dp)) { Text("REDEEM") } }
                else -> { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (selectedCustomer != null && selectedCustomer.points > 0) Button(onClick = { showRedeemForm = true }, colors = ButtonDefaults.buttonColors(containerColor = AmberAccent), shape = RoundedCornerShape(12.dp)) { Text("Redeem Points") }; Button(onClick = { showAddForm = true }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text("+ Customer") } } }
            }
        },
        dismissButton = { TextButton(onClick = { if (showAddForm || showRedeemForm) { showAddForm = false; showRedeemForm = false } else onDismiss() }) { Text("Close") } }
    )
}

@Composable
fun CashFlowDialog(onDismiss: () -> Unit, onSave: (String, Long, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("OUT") }
    AlertDialog(
        onDismissRequest = onDismiss, icon = { Icon(Icons.Default.AccountBox, contentDescription = null, tint = AmberAccent) }, title = { Text("Cash In / Out") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { type = "OUT" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (type == "OUT") AmberAccent else SlateGrey), shape = RoundedCornerShape(12.dp)) { Text("Out") }; Button(onClick = { type = "IN" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (type == "IN") EmeraldGreen else SlateGrey), shape = RoundedCornerShape(12.dp)) { Text("In") } }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = amount, onValueChange = { if (it.length <= 10) amount = it }, label = { Text("Amount (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = { Button(onClick = { val amt = amount.toLongOrNull() ?: 0L; if (amt > 0 && note.isNotBlank()) onSave(type, amt, note) }, colors = ButtonDefaults.buttonColors(containerColor = if (type == "OUT") AmberAccent else EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text("SAVE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun DiscountDialog(currentDiscount: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var amount by remember { mutableStateOf(if (currentDiscount > 0) currentDiscount.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss, icon = { Icon(Icons.Default.Star, contentDescription = null, tint = EmeraldGreen) }, title = { Text("Manual Discount") },
        text = { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { Text("Enter amount:"); Spacer(modifier = Modifier.height(8.dp)); OutlinedTextField(value = amount, onValueChange = { if (it.length <= 10) amount = it }, label = { Text("Amount (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) } },
        confirmButton = { Button(onClick = { onSave(amount.toLongOrNull() ?: 0L) }, colors = ButtonDefaults.buttonColors(containerColor = AmberAccent), shape = RoundedCornerShape(12.dp)) { Text("SAVE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

fun saveBarcodeToGallery(context: Context, product: ProductEntity) {
    try {
        val width = 500
        val height = 250
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val textPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 24f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        canvas.drawText(product.name.lowercase(), width / 2f, 40f, textPaint)
        canvas.drawText("Rp ${product.price}", width / 2f, height - 20f, textPaint)

        val barcodePaint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.FILL
        }
        
        val code39Map = mapOf(
            '0' to "101001101101", '1' to "110100101011", '2' to "101100101011", '3' to "110110010101", '4' to "101001101011", '5' to "110100110101", '6' to "101100110101", '7' to "101001011011", '8' to "110100101101", '9' to "101100101101",
            'A' to "110101001011", 'B' to "101101001011", 'C' to "110110100101", 'D' to "101011001011", 'E' to "110101100101", 'F' to "101101100101", 'G' to "101010011011", 'H' to "110101001101", 'I' to "101101001101", 'J' to "101011001101", 'K' to "110101010011", 'L' to "101101010011", 'M' to "110110101001", 'N' to "101011010011", 'O' to "110101101001", 'P' to "101101101001", 'Q' to "101010110011", 'R' to "110101011001", 'S' to "101101011001", 'T' to "101011011001", 'U' to "110010101011", 'V' to "100110101011", 'W' to "110011010101", 'X' to "100101101011", 'Y' to "110010110101", 'Z' to "100110110101",
            '-' to "100101011011", '.' to "110010101101", ' ' to "100110101101", '$' to "100100100101", '/' to "100100101001", '+' to "100101001001", '%' to "101001001001", '*' to "100101101101"
        )

        val barcodeText = "*${product.barcode.ifBlank { product.name.take(10).uppercase() }}*"
        var x = 50f
        val barWidth = 3f
        val barHeight = 100f
        
        for (char in barcodeText) {
            val pattern = code39Map[char] ?: code39Map['-']!!
            for (bit in pattern) {
                if (bit == '1') {
                    canvas.drawRect(x, 70f, x + barWidth, 70f + barHeight, barcodePaint)
                }
                x += barWidth
            }
            x += barWidth 
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "barcode_${product.name.replace(" ", "_")}_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                Toast.makeText(context, "Barcode saved to Gallery", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to save barcode: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private suspend fun generateKitchenTicket(transaction: TransactionEntity, viewModel: PosViewModel): ByteArray {
    val items = viewModel.getTransactionItems(transaction.id).first()
    val output = ByteArrayOutputStream()
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    output.write(EscPosCommands.INIT)
    output.write(EscPosCommands.ALIGN_CENTER)
    output.write(EscPosCommands.BOLD_ON)
    output.write("KITCHEN TICKET\n".toByteArray())
    output.write(EscPosCommands.BOLD_OFF)
    output.write("================\n".toByteArray())
    output.write("ID: #${transaction.id} | ${transaction.tableName}\n".toByteArray())
    output.write("Cashier: ${transaction.cashierName}\n".toByteArray())
    output.write("Time: ${dateFormat.format(Date(transaction.timestamp))}\n".toByteArray())
    output.write("================\n".toByteArray())
    output.write(EscPosCommands.ALIGN_LEFT)
    
    items.forEach {
        output.write("${it.quantity}x ${it.productName}\n".toByteArray())
        if (it.notes.isNotEmpty()) { output.write("  *Note: ${it.notes}\n".toByteArray()) }
        if (it.assignedStaff.isNotEmpty()) { output.write("  *Staff: ${it.assignedStaff}\n".toByteArray()) }
    }
    output.write("\n\n\n".toByteArray())
    output.write(EscPosCommands.FEED_3)
    output.write(EscPosCommands.CUT_PAPER)
    return output.toByteArray()
}