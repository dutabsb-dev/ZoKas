package com.karyaku.kasir

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.TransactionEntity
import com.karyaku.kasir.data.local.UserEntity
import com.karyaku.kasir.data.local.CustomerEntity
import com.karyaku.kasir.data.local.ShiftEntity
import com.karyaku.kasir.data.local.RawMaterialEntity
import com.karyaku.kasir.data.local.ProductBomEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
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
fun PosScreen(viewModel: PosViewModel, settingsManager: SettingsManager, currentUser: UserEntity, onLogout: () -> Unit) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val printerManager = remember { BluetoothPrinterManager(context) }
    
    // KEYBOARD FOCUS UNTUK SHORTCUT
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

    val taxPercent by viewModel.taxPercent.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.setTaxPercent(settingsManager.taxPercent) }

    // NETWORK STATUS
    var isOnline by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { isOnline = true }
            override fun onLost(network: android.net.Network) { isOnline = false }
        }
        cm.registerDefaultNetworkCallback(callback)
        isOnline = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        onDispose { cm.unregisterNetworkCallback(callback) }
    }

    // LOW STOCK BADGE
    val lowStockCount = allProducts.count { it.stock <= 5 && it.stock >= 0 }

    // HAPPY HOUR LOGIC
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
    
    // CUSTOMER TIERING CALCULATION
    var tierDiscount = 0L
    if (selectedCustomer != null) {
        if (selectedCustomer!!.points >= 1000) tierDiscount = (subtotal * 0.10).toLong()
        else if (selectedCustomer!!.points >= 500) tierDiscount = (subtotal * 0.05).toLong()
        else if (selectedCustomer!!.points >= 100) tierDiscount = (subtotal * 0.02).toLong()
    }

    // PROMO ENGINE CALCULATION (UI ONLY)
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
    
    // MAX DISCOUNT CAP UI
    if (settingsManager.maxDiscountCap > 0 && totalDiscount > settingsManager.maxDiscountCap) {
        totalDiscount = settingsManager.maxDiscountCap
    }

    val taxAmount = ((subtotal - totalDiscount - voucherDiscount) * (taxPercent / 100.0)).toLong()
    val totalPrice = (subtotal + taxAmount - totalDiscount - voucherDiscount).coerceAtLeast(0)
    
    // UI STATE
    var showCartOnPhone by remember { mutableStateOf(false) }
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
    var productToManageBom by remember { mutableStateOf<ProductEntity?>(null) } // FITUR BARU
    var completedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    
    // DIALOG STATE
    var showOpenShiftDialog by remember { mutableStateOf(false) }
    var showCloseShiftDialog by remember { mutableStateOf(false) }
    var showCashFlowDialog by remember { mutableStateOf(false) }
    var showDiscountDialog by remember { mutableStateOf(false) } 
    var showCustomerDialog by remember { mutableStateOf(false) }
    var modalAwal by remember { mutableStateOf("") }
    var showDrawerConfirm by remember { mutableStateOf(false) }
    var showPinForDiscount by remember { mutableStateOf(false) }
    var showCfd by remember { mutableStateOf(false) }
    var showShiftReport by remember { mutableStateOf<ShiftEntity?>(null) }
    var showMergeOrderDialog by remember { mutableStateOf(false) }

    var showHoldDialog by remember { mutableStateOf(false) }
    var showTableSelector by remember { mutableStateOf(false) }
    var showVoucherDialog by remember { mutableStateOf(false) }
    var isSplitBillMode by remember { mutableStateOf(false) }

    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(activeShift) { if (activeShift == null) showOpenShiftDialog = true }

    LaunchedEffect(completedTransaction) {
        if (completedTransaction != null) {
            val kitchenMac = settingsManager.kitchenPrinterMacAddress
            if (kitchenMac != null) {
                scope.launch {
                    val kitchenData = generateKitchenTicket(completedTransaction!!, viewModel)
                    printerManager.printData(kitchenMac, kitchenData)
                }
            }
            if (completedTransaction!!.paymentMethod == "CASH") {
                val printerMac = settingsManager.printerMacAddress
                if (printerMac != null) {
                    scope.launch { printerManager.openCashDrawer(printerMac) }
                }
            }
        }
    }

    // --- KEYBOARD SHORTCUTS WRAPPER ---
    Box(
        modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp) {
                when (event.key) {
                    Key.F1 -> { searchFocusRequester.requestFocus(); true }
                    Key.F2 -> { if (cart.isNotEmpty()) { showPaymentSheet = true; true } else false }
                    Key.F3 -> { if (cart.isNotEmpty()) { viewModel.saveHoldOrder(selectedCustomer?.name ?: "Pelanggan"); true } else false }
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
                icon = { Icon(Icons.Default.List, contentDescription = null, tint = ZokasGreen) },
                title = { Text("Pilih Varian (${product.name})") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
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
                                                viewModel.addToCart(product)
                                                viewModel.updateCartItemVariation(product, variantName)
                                                productToSelectMatrix = null
                                            },
                                            modifier = Modifier.fillMaxWidth().height(40.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen.copy(alpha = 0.1f))
                                        ) {
                                            Text("Pilih", fontSize = 10.sp, color = ZokasGreen)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { productToSelectMatrix = null }) { Text("Batal") } }
            )
        }

        // --- DIALOG PILIH VARIAN (TEKS BIASA) ---
        if (productToSelectVariant != null) {
            val product = productToSelectVariant!!
            val variants = product.variations.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            AlertDialog(
                onDismissRequest = { productToSelectVariant = null },
                icon = { Icon(Icons.Default.List, contentDescription = null, tint = ZokasGreen) },
                title = { Text("Pilih Varian") },
                text = {
                    Column {
                        Text("Silakan pilih varian untuk ${product.name}:", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        variants.forEach { variant ->
                            OutlinedButton(
                                onClick = {
                                    viewModel.addToCart(product)
                                    viewModel.updateCartItemVariation(product, variant)
                                    productToSelectVariant = null
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(variant, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { productToSelectVariant = null }) { Text("Batal") } }
            )
        }

        // --- DIALOG MERGE ORDER (GABUNG MEJA) ---
        if (showMergeOrderDialog) {
            var tableA by remember { mutableStateOf("Meja 1") }
            var tableB by remember { mutableStateOf("Meja 2") }
            val tables = listOf("Meja 1", "Meja 2", "Meja 3", "Meja 4", "Meja 5")
            
            AlertDialog(
                onDismissRequest = { showMergeOrderDialog = false },
                icon = { Icon(Icons.Default.Merge, contentDescription = null, tint = ZokasGreen) },
                title = { Text("Gabung Meja (Merge)") },
                text = {
                    Column {
                        Text("Pilih Meja Utama (Tujuan):")
                        tables.forEach { t -> 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = tableA == t, onClick = { tableA = t })
                                Text(t)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Pilih Meja yang Digabung:")
                        tables.forEach { t -> 
                            if (t != tableA) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = tableB == t, onClick = { tableB = t })
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
                    }, colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)) { Text("GABUNG") } 
                },
                dismissButton = { TextButton(onClick = { showMergeOrderDialog = false }) { Text("Batal") } }
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
                        Text("Masukkan nomor seri untuk ${productToEditSerial!!.name}:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = serial, onValueChange = { serial = it }, label = { Text("Serial Number") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                },
                confirmButton = { Button(onClick = { viewModel.updateCartItemSerial(productToEditSerial!!, serial); productToEditSerial = null }) { Text("SIMPAN") } },
                dismissButton = { TextButton(onClick = { productToEditSerial = null }) { Text("Batal") } }
            )
        }

        // --- DIALOG ASSIGN STAFF ---
        if (productToAssignStaff != null) {
            var selectedStaff by remember { mutableStateOf(cart.find { it.product.id == productToAssignStaff!!.id }?.assignedStaff ?: "") }
            val staffList = users.filter { it.role != "OWNER" }
            
            AlertDialog(
                onDismissRequest = { productToAssignStaff = null },
                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                title = { Text("Tugaskan Staf / Terapis") },
                text = {
                    Column {
                        Text("Pilih staf untuk ${productToAssignStaff!!.name}:")
                        Spacer(modifier = Modifier.height(8.dp))
                        staffList.forEach { user ->
                            OutlinedButton(
                                onClick = { selectedStaff = user.name; viewModel.updateCartItemStaff(productToAssignStaff!!, user.name); productToAssignStaff = null },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) { Text(user.name) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { productToAssignStaff = null }) { Text("Batal") } }
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
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ZokasGreen) },
                title = { Text("Laporan Tutup Kasir") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Kasir: ${shift.userName}", fontWeight = FontWeight.Bold)
                        Text("Waktu Buka: ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(shift.startTime))}")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Modal Awal:"); Text(formatRupiah(shift.openingCash))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Kas Sistem:"); Text(formatRupiah(shift.expectedCash), color = ZokasGreen, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Kas Fisik:"); Text(formatRupiah(shift.closingCash ?: 0L), fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Selisih:", fontWeight = FontWeight.Bold)
                            val selisih = shift.discrepancy ?: 0L
                            Text(formatRupiah(selisih), color = if (selisih < 0) Color.Red else if (selisih > 0) Color.Blue else ZokasGreen, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                },
                confirmButton = { Button(onClick = { showShiftReport = null; onLogout() }) { Text("LOGOUT") } }
            )
        }

        // --- CFD MODE ---
        if (showCfd) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.White).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showCfd = false },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("SELAMAT DATANG DI", fontSize = 24.sp, color = Color.Gray, letterSpacing = 2.sp)
                    Text(settingsManager.storeName ?: "ZoKas Store", fontSize = 42.sp, fontWeight = FontWeight.Black, color = ZokasGreen, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(48.dp))
                    Text("Total Belanja Anda:", fontSize = 28.sp, color = Color.Gray)
                    Text(formatRupiah(totalPrice), fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = ZokasOrange)
                    Spacer(modifier = Modifier.height(48.dp))
                    Text("Terima kasih atas kunjungan Anda", fontSize = 20.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("(Ketuk di mana saja untuk kembali ke Kasir)", fontSize = 12.sp, color = Color.LightGray)
                }
            }
            return
        }

        // --- DIALOG PIN DISKON ---
        if (showPinForDiscount) {
            var pin by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showPinForDiscount = false },
                icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = ZokasOrange) },
                title = { Text("Otorisasi Diskon") },
                text = {
                    Column {
                        Text("Masukkan PIN Supervisor/Owner untuk memberikan diskon:")
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
                        if (pin == "123456" || pin == "111111" || pin == currentUser.pin) {
                            showPinForDiscount = false; showDiscountDialog = true
                        } else {
                            Toast.makeText(context, "PIN Salah!", Toast.LENGTH_SHORT).show()
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)) { Text("VERIFIKASI") }
                },
                dismissButton = { TextButton(onClick = { showPinForDiscount = false }) { Text("Batal") } }
            )
        }

        // --- DIALOG AKSI PRODUK ---
        if (showActionDialogFor != null) {
            ModalBottomSheet(
                onDismissRequest = { showActionDialogFor = null },
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(modifier = Modifier.padding(bottom = 32.dp)) {
                    Text(text = showActionDialogFor!!.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                    ActionMenuItem(icon = Icons.Default.Edit, title = "Edit Produk", subtitle = "Ubah detail", color = ZokasGreen, onClick = { productToEdit = showActionDialogFor; showProductForm = true; showActionDialogFor = null })
                    ActionMenuItem(icon = Icons.Default.Add, title = "Tambah Stok", subtitle = "Restock barang", color = ZokasOrange, onClick = { productToRestock = showActionDialogFor; showActionDialogFor = null })
                    ActionMenuItem(icon = Icons.Default.Delete, title = "Barang Rusak", subtitle = "Catat waste/expired", color = Color(0xFFF57C00), onClick = { productToWaste = showActionDialogFor; showActionDialogFor = null })
                    ActionMenuItem(icon = Icons.Default.List, title = "Atur Resep (BOM)", subtitle = "Bahan baku & komposisi", color = Color.Magenta, onClick = { productToManageBom = showActionDialogFor; showActionDialogFor = null })
                    ActionMenuItem(icon = Icons.Default.List, title = "Simpan Barcode (JPG)", subtitle = "Simpan label ke galeri", color = Color.Blue, onClick = { 
                        val product = showActionDialogFor!!
                        showActionDialogFor = null
                        saveBarcodeToGallery(context, product)
                    })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    ActionMenuItem(icon = Icons.Default.Delete, title = "Hapus Produk", subtitle = "Hapus permanen", color = Color.Red, onClick = { productToDelete = showActionDialogFor; showActionDialogFor = null })
                }
            }
        }

        // --- DIALOG LAINNYA ---
        if (showDrawerConfirm) {
            AlertDialog(
                onDismissRequest = { showDrawerConfirm = false },
                icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                title = { Text("Buka Laci Kasir") },
                text = { Text("Buka laci tanpa transaksi? Aktivitas ini akan dicatat.") },
                confirmButton = { Button(onClick = { viewModel.logCashDrawerOpen(currentUser); Toast.makeText(context, "Laci dibuka.", Toast.LENGTH_SHORT).show(); showDrawerConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)) { Text("BUKA") } },
                dismissButton = { TextButton(onClick = { showDrawerConfirm = false }) { Text("Batal") } }
            )
        }

        if (productToWaste != null) {
            var qty by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { productToWaste = null },
                icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                title = { Text("Catat Barang Rusak") },
                text = {
                    Column {
                        Text("Produk: ${productToWaste!!.name}", fontWeight = FontWeight.Bold)
                        Text("Stok: ${productToWaste!!.stock} pcs", color = Color.Gray)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(value = qty, onValueChange = { if (it.length <= 5) qty = it }, label = { Text("Jumlah Rusak") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                },
                confirmButton = {
                    Button(onClick = { 
                        val q = qty.toIntOrNull() ?: 0
                        if (q > 0 && q <= productToWaste!!.stock) { viewModel.recordWaste(productToWaste!!, q, currentUser); productToWaste = null }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("SIMPAN") }
                },
                dismissButton = { TextButton(onClick = { productToWaste = null }) { Text("Batal") } }
            )
        }

        if (productToEditNotes != null) {
            var notes by remember { mutableStateOf(cart.find { it.product.id == productToEditNotes!!.id }?.notes ?: "") }
            AlertDialog(
                onDismissRequest = { productToEditNotes = null },
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                title = { Text("Catatan Pesanan") },
                text = {
                    Column {
                        Text("Catatan untuk ${productToEditNotes!!.name}:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Cth: Less Ice") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                },
                confirmButton = { Button(onClick = { viewModel.updateCartItemNotes(productToEditNotes!!, notes); productToEditNotes = null }) { Text("SIMPAN") } },
                dismissButton = { TextButton(onClick = { productToEditNotes = null }) { Text("Batal") } }
            )
        }

        if (showOpenShiftDialog) {
            AlertDialog(
                onDismissRequest = { },
                icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = ZokasGreen) },
                title = { Text("Buka Kasir") },
                text = {
                    Column {
                        Text("Masukkan modal awal:")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(value = modalAwal, onValueChange = { if (it.length <= 10) modalAwal = it }, label = { Text("Modal Awal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                },
                confirmButton = { Button(onClick = { viewModel.openShift(currentUser, modalAwal.toLongOrNull() ?: 0L); showOpenShiftDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("MULAI") } }
            )
        }

        if (showCloseShiftDialog) { CloseShiftDialog(onDismiss = { showCloseShiftDialog = false }, onConfirm = { uangFisik -> viewModel.closeShift(currentUser, uangFisik) { closedShift -> showCloseShiftDialog = false; showShiftReport = closedShift } }) }
        if (showCashFlowDialog) { CashFlowDialog(onDismiss = { showCashFlowDialog = false }, onSave = { type, amount, note -> viewModel.addCashFlow(type, amount, note, currentUser); showCashFlowDialog = false }) }
        if (showDiscountDialog) { DiscountDialog(currentDiscount = discount, onDismiss = { showDiscountDialog = false }, onSave = { amount -> viewModel.setDiscount(amount); showDiscountDialog = false }) }
        if (showCustomerDialog) { CustomerDialog(customers = customers, selectedCustomer = selectedCustomer, onDismiss = { showCustomerDialog = false }, onSelect = { viewModel.setCustomer(it); showCustomerDialog = false }, onAddCustomer = { name, phone -> viewModel.addCustomer(name, phone) }, onRedeemPoints = { points -> viewModel.redeemPoints(points) }) }
        if (showTableSelector) { TableSelectorDialog(currentTable = selectedTable, occupiedTables = occupiedTables, onDismiss = { showTableSelector = false }, onSelect = { viewModel.setTable(it) }) }
        if (showVoucherDialog) { VoucherDialog(currentCode = appliedVoucher, onDismiss = { showVoucherDialog = false }, onApply = { code -> viewModel.applyVoucher(code) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); if (success) showVoucherDialog = false } }, onRemove = { viewModel.applyVoucher("") { _, _ -> }; showVoucherDialog = false }) }
        if (showHoldDialog) { HoldOrderDialog(holdOrders = holdOrders, onDismiss = { showHoldDialog = false }, onResume = { viewModel.resumeHoldOrder(it); showHoldDialog = false }) }

        if (completedTransaction != null) {
            ReceiptDialog(transaction = completedTransaction!!, viewModel = viewModel, storeName = if (settingsManager.isPro) settingsManager.storeName ?: "" else "ZoKas", storeAddress = if (settingsManager.isPro) settingsManager.storeAddress ?: "" else "", storePhone = if (settingsManager.isPro) settingsManager.storePhone ?: "" else "", storeFooter = if (settingsManager.isPro) settingsManager.storeFooter ?: "" else "", settingsManager = settingsManager, onDismiss = { completedTransaction = null })
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
                productToEdit = productToEdit, onDismiss = { showProductForm = false; productToEdit = null }, 
                onSave = { name, price, stock, category, costPrice, barcode, variations, commission, expiry, isService, isCombo ->
                    if (productToEdit == null) viewModel.addProduct(name, price, stock, category, costPrice, barcode, variations, commission, expiry, currentUser.id, currentUser.name)
                    else viewModel.updateProduct(productToEdit!!.copy(name = name, price = price, stock = stock, category = category, costPrice = costPrice, barcode = barcode, variations = variations, commissionPerItem = commission, expiryDate = expiry, isService = isService, isCombo = isCombo), currentUser.id, currentUser.name)
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
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red) },
                title = { Text("Hapus Produk?") },
                text = { Text("Hapus '${productToDelete!!.name}' secara permanen?") },
                confirmButton = { Button(onClick = { viewModel.deleteProduct(productToDelete!!, currentUser.id, currentUser.name); productToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("HAPUS") } },
                dismissButton = { TextButton(onClick = { productToDelete = null }) { Text("Batal") } }
            )
        }

        // --- MAIN UI (SCAFFOLD) ---
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Image(painter = painterResource(id = R.drawable.logo_zokas), contentDescription = "Logo", modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) { 
                                Text(
                                    text = if (settingsManager.isPro) settingsManager.storeName ?: "ZoKas" else "ZoKas",
                                    fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = ZokasOrange,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).background(if (isOnline) ZokasGreen else Color.Red, CircleShape))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = if (isOnline) "Online" else "Offline", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f), maxLines = 1)
                                }
                            }
                        }
                    },
                    actions = {
                        if (lowStockCount > 0) {
                            BadgedBox(badge = { Badge(containerColor = Color.Red) { Text(lowStockCount.toString(), color = Color.White) } }, modifier = Modifier.padding(end = 8.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = "Stok Menipis", tint = ZokasOrange)
                            }
                        }
                        IconButton(onClick = { showCfd = true }) { Icon(Icons.Default.Person, contentDescription = "Layar Pelanggan", tint = ZokasOrange) }
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Gabung Meja (Merge)") }, leadingIcon = { Icon(Icons.Default.Merge, contentDescription = null, tint = ZokasGreen) }, onClick = { showMergeOrderDialog = true; showMenu = false })
                            DropdownMenuItem(text = { Text("Buka Laci (No Sale)") }, leadingIcon = { Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = ZokasGreen) }, onClick = { showDrawerConfirm = true; showMenu = false })
                            DropdownMenuItem(text = { Text("Pesanan Parkir") }, leadingIcon = { Icon(Icons.Default.List, contentDescription = null, tint = ZokasGreen) }, onClick = { showHoldDialog = true; showMenu = false })
                            DropdownMenuItem(text = { Text("Kas Keluar / Masuk") }, leadingIcon = { Icon(Icons.Default.AccountBox, contentDescription = null, tint = ZokasOrange) }, onClick = { showCashFlowDialog = true; showMenu = false })
                            DropdownMenuItem(text = { Text("Tutup Kasir") }, leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = ZokasOrange) }, onClick = { showCloseShiftDialog = true; showMenu = false })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Logout", color = Color.Red) }, leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.Red) }, onClick = { onLogout(); showMenu = false })
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                )
            },
            floatingActionButton = {
                Box(
                    modifier = Modifier.size(64.dp).shadow(12.dp, CircleShape, spotColor = ZokasOrangeDark).background(brush = Brush.linearGradient(colors = listOf(ZokasOrange, ZokasOrangeDark)), shape = CircleShape).clickable { productToEdit = null; showProductForm = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Add, contentDescription = "Tambah", tint = Color.White, modifier = Modifier.size(32.dp)) }
            },
            bottomBar = {
                if (cart.isNotEmpty() && !showCartOnPhone) {
                    BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.height(72.dp)) {
                        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("${cart.sumOf { it.quantity }} item", fontSize = 12.sp, color = Color.Gray)
                                Text(formatRupiah(totalPrice), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = ZokasOrange)
                            }
                            Button(onClick = { showCartOnPhone = true }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange), shape = RoundedCornerShape(12.dp), modifier = Modifier.height(48.dp)) { 
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("Keranjang", fontWeight = FontWeight.Bold) 
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                AnimatedVisibility(visible = isHappyHour, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Box(modifier = Modifier.fillMaxWidth().background(Brush.horizontalGradient(colors = listOf(Color.Red, Color(0xFFFF5722)))).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("HAPPY HOUR! Diskon ${settingsManager.happyHourDiscount}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isTablet = maxWidth > 600.dp
                    if (isTablet) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(0.6f).fillMaxHeight()) { 
                                ProductCatalog(
                                    products = filteredProducts, categories = categories, selectedCategory = selectedCategory, 
                                    onCategorySelect = { viewModel.setCategory(it) }, searchQuery = searchQuery, onSearchChange = { viewModel.setSearchQuery(it) }, 
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
                                    onBarcodeScan = { barcode -> viewModel.processBarcodeScan(barcode) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); if (success) keyboardController?.hide() } },
                                    searchFocusRequester = searchFocusRequester
                                ) 
                            }
                            VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            Box(modifier = Modifier.weight(0.4f).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                                CartList(
                                    cart = cart, subtotal = subtotal, discount = totalDiscount, taxAmount = taxAmount, totalPrice = totalPrice, selectedCustomer = selectedCustomer, pointsUsed = pointsUsed, selectedTable = selectedTable, appliedVoucher = appliedVoucher, voucherDiscount = voucherDiscount, isSplitBillMode = isSplitBillMode, 
                                    onToggleSplitMode = { isSplitBillMode = !isSplitBillMode }, onToggleSplitSelection = { viewModel.toggleSplitSelection(it) }, onTableClick = { showTableSelector = true }, onVoucherClick = { showVoucherDialog = true }, 
                                    onHoldClick = { if (cart.isNotEmpty()) { viewModel.saveHoldOrder(selectedCustomer?.name ?: "Pelanggan"); Toast.makeText(context, "Pesanan diparkir.", Toast.LENGTH_SHORT).show() } }, onViewHoldClick = { showHoldDialog = true }, onCustomerClick = { showCustomerDialog = true }, 
                                    onAdd = { viewModel.addToCart(it) }, onRemove = { viewModel.removeFromCart(it) }, onDelete = { viewModel.deleteItem(it) }, 
                                    onDiscountClick = { if (currentUser.role == "CASHIER" && settingsManager.requirePinForDiscount) { showPinForDiscount = true } else { showDiscountDialog = true } }, 
                                    onCheckout = { showPaymentSheet = true }, onEditNotes = { productToEditNotes = it },
                                    onEditSerial = { productToEditSerial = it }, onAssignStaff = { productToAssignStaff = it }
                                )
                            }
                        }
                    } else {
                        if (showCartOnPhone) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                TextButton(onClick = { showCartOnPhone = false }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Kembali ke Katalog", color = ZokasOrange, fontWeight = FontWeight.Bold) }
                                CartList(
                                    cart = cart, subtotal = subtotal, discount = totalDiscount, taxAmount = taxAmount, totalPrice = totalPrice, selectedCustomer = selectedCustomer, pointsUsed = pointsUsed, selectedTable = selectedTable, appliedVoucher = appliedVoucher, voucherDiscount = voucherDiscount, isSplitBillMode = isSplitBillMode, 
                                    onToggleSplitMode = { isSplitBillMode = !isSplitBillMode }, onToggleSplitSelection = { viewModel.toggleSplitSelection(it) }, onTableClick = { showTableSelector = true }, onVoucherClick = { showVoucherDialog = true }, 
                                    onHoldClick = { if (cart.isNotEmpty()) { viewModel.saveHoldOrder(selectedCustomer?.name ?: "Pelanggan"); Toast.makeText(context, "Pesanan diparkir.", Toast.LENGTH_SHORT).show() } }, onViewHoldClick = { showHoldDialog = true }, onCustomerClick = { showCustomerDialog = true }, 
                                    onAdd = { viewModel.addToCart(it) }, onRemove = { viewModel.removeFromCart(it) }, onDelete = { viewModel.deleteItem(it) }, 
                                    onDiscountClick = { if (currentUser.role == "CASHIER" && settingsManager.requirePinForDiscount) { showPinForDiscount = true } else { showDiscountDialog = true } }, 
                                    onCheckout = { showPaymentSheet = true }, onEditNotes = { productToEditNotes = it },
                                    onEditSerial = { productToEditSerial = it }, onAssignStaff = { productToAssignStaff = it }
                                )
                            }
                        } else {
                            ProductCatalog(
                                products = filteredProducts, categories = categories, selectedCategory = selectedCategory, 
                                onCategorySelect = { viewModel.setCategory(it) }, searchQuery = searchQuery, onSearchChange = { viewModel.setSearchQuery(it) }, 
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
                                onBarcodeScan = { barcode -> viewModel.processBarcodeScan(barcode) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); if (success) keyboardController?.hide() } },
                                searchFocusRequester = searchFocusRequester
                            )
                        }
                    }
                }
            }
        }
    }
}

// ================= KOMPONEN UI BAWAH =================

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
        icon = { Icon(Icons.Default.List, contentDescription = null, tint = Color.Magenta) },
        title = { Text("Resep (BOM): ${product.name}") },
        text = {
            Column {
                Text("Tambahkan bahan baku yang digunakan untuk membuat produk ini.")
                Spacer(modifier = Modifier.height(8.dp))
                rawMaterials.forEach { material ->
                    OutlinedButton(
                        onClick = { selectedMaterial = material },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedMaterial?.id == material.id) ZokasGreen.copy(alpha = 0.2f) else Color.Transparent
                        )
                    ) {
                        Text("${material.name} (${material.stock} ${material.unit})")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = qtyUsed,
                    onValueChange = { if (it.length <= 5) qtyUsed = it },
                    label = { Text("Jumlah Digunakan (${selectedMaterial?.unit ?: "unit"})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
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
                colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)
            ) { Text("SIMPAN RESEP") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
fun ActionMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp)) }
        Spacer(modifier = Modifier.width(16.dp))
        Column { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(subtitle, fontSize = 12.sp, color = Color.Gray) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductCatalog(
    products: List<ProductEntity>, categories: List<String>, selectedCategory: String, 
    onCategorySelect: (String) -> Unit, searchQuery: String, onSearchChange: (String) -> Unit, 
    onProductClick: (ProductEntity) -> Unit, onProductLongClick: (ProductEntity) -> Unit, onBarcodeScan: (String) -> Unit,
    searchFocusRequester: FocusRequester
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery, onValueChange = onSearchChange, 
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp).focusRequester(searchFocusRequester), 
            placeholder = { Text("Cari produk / scan barcode... (F1)") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ZokasGreen) }, 
            trailingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ZokasGreen) }, singleLine = true, shape = RoundedCornerShape(16.dp), 
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZokasGreen, unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant), 
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { if (searchQuery.isNotBlank()) { onBarcodeScan(searchQuery); onSearchChange("") } })
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                FilterChip(selected = category == selectedCategory, onClick = { onCategorySelect(category) }, label = { Text(category, fontWeight = if(category == selectedCategory) FontWeight.Bold else FontWeight.Normal) }, shape = RoundedCornerShape(12.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZokasGreen, selectedLabelColor = Color.White, containerColor = MaterialTheme.colorScheme.surface))
            }
        }
        if (products.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Produk tidak ditemukan.", color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) }
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 160.dp), contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(items = products, key = { it.id }) { product ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f), label = "scale")
                    
                    val isExpiringSoon = product.expiryDate > 0 && (product.expiryDate - System.currentTimeMillis()) < (7L * 24 * 60 * 60 * 1000)

                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).scale(scale)
                            .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = ZokasGreenDark)
                            .clip(RoundedCornerShape(24.dp))
                            .background(brush = Brush.linearGradient(colors = if (isExpiringSoon) listOf(Color.Red, Color(0xFFB71C1C)) else listOf(ZokasGreen, ZokasGreenDark)))
                            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = { onProductClick(product) }, onLongClick = { onProductLongClick(product) })
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) { 
                                    Text(text = product.category, fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) 
                                }
                                if (product.promoType != "NONE") {
                                    Surface(color = Color.Yellow, shape = RoundedCornerShape(8.dp)) { 
                                        Text(text = if (product.promoType == "BOGO") "BOGO" else "DISC ${product.promoValue}%", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) 
                                    }
                                }
                            }
                            Text(text = product.name, fontWeight = FontWeight.ExtraBold, maxLines = 2, style = MaterialTheme.typography.titleLarge, color = Color.White, lineHeight = 24.sp)
                            
                            if (isExpiringSoon) { Text("EXPIRED SEGERA", fontSize = 10.sp, color = Color.Yellow, fontWeight = FontWeight.Bold) }

                            Spacer(modifier = Modifier.weight(1f))
                            Column {
                                Text(text = formatRupiah(product.price), style = MaterialTheme.typography.titleLarge, color = ZokasOrange, fontWeight = FontWeight.Black)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.List, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(12.dp)); Spacer(modifier = Modifier.width(4.dp)); Text(text = "Stok: ${product.stock}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
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
    onToggleSplitMode: () -> Unit, onToggleSplitSelection: (ProductEntity) -> Unit, onTableClick: () -> Unit, onVoucherClick: () -> Unit,
    onHoldClick: () -> Unit, onViewHoldClick: () -> Unit, onCustomerClick: () -> Unit, onAdd: (ProductEntity) -> Unit, 
    onRemove: (ProductEntity) -> Unit, onDelete: (ProductEntity) -> Unit, onDiscountClick: () -> Unit, onCheckout: () -> Unit,
    onEditNotes: (ProductEntity) -> Unit, onEditSerial: (ProductEntity) -> Unit, onAssignStaff: (ProductEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onTableClick, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, ZokasOrange)) { Text(selectedTable, fontWeight = FontWeight.Bold, color = ZokasOrange, fontSize = 11.sp, maxLines = 1) }
            OutlinedButton(onClick = onHoldClick, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(8.dp)) { Text("Parkir (F3)", fontWeight = FontWeight.Bold, color = ZokasGreen, fontSize = 11.sp) }
            OutlinedButton(onClick = onViewHoldClick, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(8.dp)) { Text("Lihat", fontWeight = FontWeight.Bold, color = ZokasGreen, fontSize = 11.sp) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCustomerClick, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.Person, contentDescription = null, tint = ZokasOrange, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = if (selectedCustomer != null) "${selectedCustomer.name} (${selectedCustomer.points} Poin)" else "Pelanggan Umum", fontWeight = FontWeight.Bold, color = ZokasOrange, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Split Bill", fontSize = 12.sp); Spacer(modifier = Modifier.width(4.dp)); Switch(checked = isSplitBillMode, onCheckedChange = { onToggleSplitMode() }, modifier = Modifier.scale(0.8f))
            }
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(cart) { item ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        if (isSplitBillMode) { Checkbox(checked = item.isSelectedForSplit, onCheckedChange = { onToggleSplitSelection(item.product) }, modifier = Modifier.scale(0.8f)) }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (item.selectedVariation.isNotEmpty()) {
                                Text("Varian: ${item.selectedVariation}", fontSize = 10.sp, color = ZokasGreen, fontWeight = FontWeight.Bold)
                            }
                            if (item.serialNumber.isNotEmpty()) {
                                Text("SN: ${item.serialNumber}", fontSize = 10.sp, color = Color.Blue, fontWeight = FontWeight.Bold)
                            }
                            if (item.assignedStaff.isNotEmpty()) {
                                Text("Staf: ${item.assignedStaff}", fontSize = 10.sp, color = Color.Magenta, fontWeight = FontWeight.Bold)
                            }
                            Text(formatRupiah(item.product.price), fontSize = 11.sp, color = Color.Gray)
                            if (item.notes.isNotEmpty()) { Text("Cat: ${item.notes}", fontSize = 10.sp, color = Color.Red, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onEditNotes(item.product) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Edit, contentDescription = "Edit Catatan", tint = ZokasGreen, modifier = Modifier.size(16.dp)) }
                            if (item.product.isService) {
                                IconButton(onClick = { onAssignStaff(item.product) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Person, contentDescription = "Assign Staf", tint = Color.Magenta, modifier = Modifier.size(16.dp)) }
                            }
                            if (item.product.category == "Elektronik" || item.product.isService) {
                                IconButton(onClick = { onEditSerial(item.product) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.List, contentDescription = "Input SN", tint = Color.Blue, modifier = Modifier.size(16.dp)) }
                            }
                            IconButton(onClick = { onRemove(item.product) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Remove, contentDescription = "Kurang", modifier = Modifier.size(16.dp)) }
                            Text(text = "${item.quantity}", modifier = Modifier.padding(horizontal = 4.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            IconButton(onClick = { onAdd(item.product) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, contentDescription = "Tambah", modifier = Modifier.size(16.dp)) }
                            IconButton(onClick = { onDelete(item.product) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Subtotal:", fontSize = 12.sp, color = Color.Gray)
            Text(formatRupiah(subtotal), fontSize = 12.sp, color = Color.Gray)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Diskon:", fontSize = 12.sp, color = Color.Gray)
                TextButton(onClick = onDiscountClick, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Ubah", color = ZokasOrange, fontSize = 11.sp) }
            }
            Text("- ${formatRupiah(discount)}", fontSize = 12.sp, color = Color.Red)
        }
        if (taxAmount > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pajak:", fontSize = 12.sp, color = Color.Gray)
                Text("+ ${formatRupiah(taxAmount)}", fontSize = 12.sp, color = ZokasOrange)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Total:", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(formatRupiah(totalPrice), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = ZokasOrange)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onCheckout, modifier = Modifier.fillMaxWidth().height(48.dp), enabled = cart.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange), shape = RoundedCornerShape(8.dp)) {
            Text("BAYAR (F2)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormBottomSheet(
    productToEdit: ProductEntity? = null, 
    onDismiss: () -> Unit, 
    onSave: (String, Long, Int, String, Long, String, String, Long, Long, Boolean, Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    var name by remember { mutableStateOf(productToEdit?.name ?: "") }
    var price by remember { mutableStateOf(productToEdit?.price?.toString() ?: "") }
    var costPrice by remember { mutableStateOf(productToEdit?.costPrice?.toString() ?: "") } 
    var stock by remember { mutableStateOf(productToEdit?.stock?.toString() ?: "") }
    var category by remember { mutableStateOf(productToEdit?.category ?: "Umum") } 
    var barcode by remember { mutableStateOf(productToEdit?.barcode ?: "") }
    var variations by remember { mutableStateOf(productToEdit?.variations ?: "") }
    var commission by remember { mutableStateOf(productToEdit?.commissionPerItem?.toString() ?: "") }
    var expiryDate by remember { mutableStateOf(productToEdit?.expiryDate ?: 0L) }
    var isService by remember { mutableStateOf(productToEdit?.isService ?: false) }
    var isCombo by remember { mutableStateOf(productToEdit?.isCombo ?: false) }
    
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val expiryText = if (expiryDate > 0) dateFormat.format(Date(expiryDate)) else "Pilih Tanggal"

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
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (productToEdit == null) "Tambah Produk Baru" else "Edit Produk", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = isService, onCheckedChange = { isService = it })
                Text("Produk Jasa (Tanpa Stok)", modifier = Modifier.clickable { isService = !isService })
                Spacer(modifier = Modifier.width(16.dp))
                Checkbox(checked = isCombo, onCheckedChange = { isCombo = it })
                Text("Produk Combo/Paket", modifier = Modifier.clickable { isCombo = !isCombo })
            }
            
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Produk") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = variations, onValueChange = { variations = it }, label = { Text("Varian (Pisah koma / | untuk Matrix)") }, placeholder = { Text("Cth: S,M,L | Merah,Biru") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("Barcode (Opsional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Kategori") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = costPrice, onValueChange = { if (it.length <= 10) costPrice = it }, label = { Text("Harga Beli / Modal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = price, onValueChange = { if (it.length <= 10) price = it }, label = { Text("Harga Jual (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            if (!isService) {
                OutlinedTextField(value = stock, onValueChange = { if (it.length <= 5) stock = it }, label = { Text("Stok") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedTextField(value = commission, onValueChange = { if (it.length <= 10) commission = it }, label = { Text("Komisi Kasir / Item (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.Gray)
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null, tint = ZokasOrange)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Expiry: $expiryText", color = Color.Black)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { 
                val p = price.toLongOrNull() ?: 0L
                val cp = costPrice.toLongOrNull() ?: 0L
                val s = if (isService) 0 else stock.toIntOrNull() ?: 0
                val c = commission.toLongOrNull() ?: 0L
                if (name.isNotBlank() && p > 0) onSave(name, p, s, category.ifBlank { "Umum" }, cp, barcode, variations, c, expiryDate, isService, isCombo) 
            }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = name.isNotBlank() && (price.toLongOrNull() ?: 0L) > 0, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange), shape = RoundedCornerShape(12.dp)) {
                Text(if (productToEdit == null) "SIMPAN PRODUK" else "UPDATE PRODUK", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Pembayaran", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Total Tagihan:", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            Text(formatRupiah(totalPrice), style = MaterialTheme.typography.headlineLarge, color = ZokasOrange, fontWeight = FontWeight.Black)
            
            // FITUR BARU: MULTI CURRENCY
            if (settingsManager.enableMultiCurrency && settingsManager.exchangeRateUsd > 0) {
                val usdAmount = totalPrice / settingsManager.exchangeRateUsd
                val formattedUsd = String.format(Locale.US, "$%.2f", usdAmount)
                Text("≈ $formattedUsd USD", fontSize = 16.sp, color = Color.Gray)
            }

            if (totalCommission > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(color = ZokasGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                    Text("Komisi Kasir: +${formatRupiah(totalCommission)}", color = ZokasGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp), fontSize = 12.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { paymentMethod = "CASH" }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = if (paymentMethod == "CASH") ZokasGreen else MaterialTheme.colorScheme.surfaceVariant)) { Text("TUNAI", color = if (paymentMethod == "CASH") Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Button(onClick = { paymentMethod = "QRIS" }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = if (paymentMethod == "QRIS") Color.Blue else MaterialTheme.colorScheme.surfaceVariant)) { Text("QRIS", color = if (paymentMethod == "QRIS") Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Button(onClick = { paymentMethod = "BON" }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = if (paymentMethod == "BON") ZokasOrange else MaterialTheme.colorScheme.surfaceVariant)) { Text("BON", color = if (paymentMethod == "BON") Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (paymentMethod == "CASH") {
                OutlinedTextField(value = cashInput, onValueChange = { if (it.length <= 10) cashInput = it }, label = { Text("Uang Diterima (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kembalian:", style = MaterialTheme.typography.titleMedium)
                    Text(formatRupiah(change), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (change > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface)
                }
            } else if (paymentMethod == "QRIS") {
                Box(modifier = Modifier.size(200.dp).background(Color.LightGray, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text("QRIS CODE\n(Mock)", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Color.Gray)
                }
            } else {
                Surface(color = Color.Red.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Transaksi akan dicatat sebagai BON (Belum Lunas).", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { onConfirmPayment(if (paymentMethod == "BON" || paymentMethod == "QRIS") totalPrice else cashReceived, paymentMethod) }, 
                modifier = Modifier.fillMaxWidth().height(56.dp), 
                enabled = (paymentMethod == "BON" || paymentMethod == "QRIS") || (cashReceived >= totalPrice && totalPrice > 0), 
                colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange),
                shape = RoundedCornerShape(12.dp)
            ) { 
                Text("SELESAIKAN PEMBAYARAN", fontSize = 16.sp, fontWeight = FontWeight.Bold) 
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// Fungsi-fungsi dialog lainnya tetap sama
@Composable
fun TableSelectorDialog(currentTable: String, occupiedTables: List<String>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val tables = listOf("Take-Away", "Meja 1", "Meja 2", "Meja 3", "Meja 4", "Meja 5")
    AlertDialog(
        onDismissRequest = onDismiss, icon = { Icon(Icons.Default.List, contentDescription = null, tint = ZokasOrange) },
        title = { Text("Pilih Meja") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                for (table in tables) {
                    val isOccupied = occupiedTables.contains(table)
                    OutlinedButton(onClick = { onSelect(table); onDismiss() }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (table == currentTable) ZokasGreen.copy(alpha = 0.2f) else if (isOccupied) Color.Red.copy(alpha = 0.1f) else Color.Transparent), border = if (isOccupied) BorderStroke(1.dp, Color.Red) else null, shape = RoundedCornerShape(12.dp)) {
                        Text(text = if (isOccupied) "$table (Terisi)" else table, color = if (isOccupied) Color.Red else if (table == currentTable) ZokasGreen else MaterialTheme.colorScheme.onSurface, fontWeight = if (isOccupied) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
fun VoucherDialog(currentCode: String, onDismiss: () -> Unit, onApply: (String) -> Unit, onRemove: () -> Unit) {
    var code by remember { mutableStateOf(currentCode) }
    AlertDialog(
        onDismissRequest = onDismiss, icon = { Icon(Icons.Default.Star, contentDescription = null, tint = ZokasGreen) },
        title = { Text("Gunakan Voucher") },
        text = { Column { OutlinedTextField(value = code, onValueChange = { code = it.uppercase() }, label = { Text("Kode Voucher") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)) } },
        confirmButton = { Button(onClick = { onApply(code) }, enabled = code.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)) { Text("TERAPKAN") } },
        dismissButton = { if (currentCode.isNotEmpty()) TextButton(onClick = onRemove) { Text("Hapus", color = Color.Red) } else TextButton(onClick = onDismiss) { Text("Tutup") } }
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
        onDismissRequest = onDismiss, icon = { Icon(Icons.Default.Person, contentDescription = null, tint = ZokasOrange) },
        title = { Text(when { showAddForm -> "Tambah Pelanggan"; showRedeemForm -> "Tukar Poin"; else -> "Pilih Pelanggan" }) },
        text = {
            when {
                showAddForm -> { Column { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Nama") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)); Spacer(modifier = Modifier.height(8.dp)); OutlinedTextField(value = newPhone, onValueChange = { newPhone = it }, label = { Text("No HP") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) } }
                showRedeemForm && selectedCustomer != null -> { Column { Text("Poin: ${selectedCustomer.points}"); Spacer(modifier = Modifier.height(8.dp)); OutlinedTextField(value = pointsToRedeem, onValueChange = { if (it.length <= 5) pointsToRedeem = it }, label = { Text("Jumlah Poin") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) } }
                else -> {
                    Column {
                        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), placeholder = { Text("Cari...") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, shape = RoundedCornerShape(12.dp))
                        TextButton(onClick = { onSelect(null); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Pelanggan Umum", fontWeight = FontWeight.Bold, color = ZokasOrange) }
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) { 
                            items(filteredCustomers) { customer -> 
                                TextButton(onClick = { onSelect(customer); onDismiss() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { 
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { 
                                        Column {
                                            Text("${customer.name} (${customer.points} Poin)", fontWeight = FontWeight.Bold)
                                            val tier = if (customer.points >= 1000) "Platinum (10%)" else if (customer.points >= 500) "Gold (5%)" else if (customer.points >= 100) "Silver (2%)" else "Member"
                                            Text(tier, fontSize = 11.sp, color = ZokasGreen, fontWeight = FontWeight.Bold)
                                        }
                                        Text(if (customer.phone == "-") "" else customer.phone, fontSize = 12.sp, color = Color.Gray) 
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
                showAddForm -> { Button(onClick = { if (newName.isNotBlank()) { onAddCustomer(newName, if(newPhone.isBlank()) "-" else newPhone); showAddForm = false } }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("SIMPAN") } }
                showRedeemForm && selectedCustomer != null -> { Button(onClick = { val pts = pointsToRedeem.toIntOrNull() ?: 0; if (pts > 0 && pts <= selectedCustomer.points) { onRedeemPoints(pts); onDismiss() } }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("TUKAR") } }
                else -> { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (selectedCustomer != null && selectedCustomer.points > 0) Button(onClick = { showRedeemForm = true }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("Tukar Poin") }; Button(onClick = { showAddForm = true }, colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)) { Text("+ Pelanggan") } } }
            }
        },
        dismissButton = { TextButton(onClick = { if (showAddForm || showRedeemForm) { showAddForm = false; showRedeemForm = false } else onDismiss() }) { Text("Tutup") } }
    )
}

@Composable
fun CashFlowDialog(onDismiss: () -> Unit, onSave: (String, Long, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("OUT") }
    AlertDialog(
        onDismissRequest = onDismiss, icon = { Icon(Icons.Default.AccountBox, contentDescription = null, tint = ZokasOrange) }, title = { Text("Kas Keluar / Masuk") },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { type = "OUT" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (type == "OUT") ZokasOrange else Color.Gray)) { Text("Keluar") }; Button(onClick = { type = "IN" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (type == "IN") ZokasGreen else Color.Gray)) { Text("Masuk") } }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = amount, onValueChange = { if (it.length <= 10) amount = it }, label = { Text("Nominal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Keterangan") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = { Button(onClick = { val amt = amount.toLongOrNull() ?: 0L; if (amt > 0 && note.isNotBlank()) onSave(type, amt, note) }, colors = ButtonDefaults.buttonColors(containerColor = if (type == "OUT") ZokasOrange else ZokasGreen)) { Text("SIMPAN") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun DiscountDialog(currentDiscount: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var amount by remember { mutableStateOf(if (currentDiscount > 0) currentDiscount.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss, icon = { Icon(Icons.Default.Star, contentDescription = null, tint = ZokasGreen) }, title = { Text("Diskon Manual") },
        text = { Column { Text("Masukkan nominal:"); Spacer(modifier = Modifier.height(8.dp)); OutlinedTextField(value = amount, onValueChange = { if (it.length <= 10) amount = it }, label = { Text("Nominal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) } },
        confirmButton = { Button(onClick = { onSave(amount.toLongOrNull() ?: 0L) }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("SIMPAN") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun CloseShiftDialog(onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var uangFisik by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = ZokasOrange) }, title = { Text("Tutup Kasir") },
        text = { Column { Text("Masukkan total uang fisik di laci:"); Spacer(modifier = Modifier.height(8.dp)); OutlinedTextField(value = uangFisik, onValueChange = { if (it.length <= 10) uangFisik = it }, label = { Text("Uang Fisik (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) } },
        confirmButton = { Button(onClick = { onConfirm(uangFisik.toLongOrNull() ?: 0L) }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("TUTUP") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// FUNGSI BANTUAN: Simpan Barcode Code 39 ke Galeri
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
                Toast.makeText(context, "Barcode disimpan ke Galeri", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal simpan barcode: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// FUNGSI BANTUAN: Cetak Tiket Dapur
private suspend fun generateKitchenTicket(transaction: TransactionEntity, viewModel: PosViewModel): ByteArray {
    val items = viewModel.getTransactionItems(transaction.id).first()
    val output = ByteArrayOutputStream()
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    output.write(EscPosCommands.INIT)
    output.write(EscPosCommands.ALIGN_CENTER)
    output.write(EscPosCommands.BOLD_ON)
    output.write("TIKET DAPUR\n".toByteArray())
    output.write(EscPosCommands.BOLD_OFF)
    output.write("================\n".toByteArray())
    output.write("ID: #${transaction.id} | ${transaction.tableName}\n".toByteArray())
    output.write("Kasir: ${transaction.cashierName}\n".toByteArray())
    output.write("Waktu: ${dateFormat.format(Date(transaction.timestamp))}\n".toByteArray())
    output.write("================\n".toByteArray())
    output.write(EscPosCommands.ALIGN_LEFT)
    
    items.forEach {
        output.write("${it.quantity}x ${it.productName}\n".toByteArray())
        if (it.notes.isNotEmpty()) { output.write("  *Cat: ${it.notes}\n".toByteArray()) }
        if (it.assignedStaff.isNotEmpty()) { output.write("  *Staf: ${it.assignedStaff}\n".toByteArray()) }
    }
    output.write("\n\n\n".toByteArray())
    output.write(EscPosCommands.FEED_3)
    output.write(EscPosCommands.CUT_PAPER)
    return output.toByteArray()
}