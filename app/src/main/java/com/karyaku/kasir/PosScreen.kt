package com.karyaku.kasir

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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.TransactionEntity
import com.karyaku.kasir.data.local.UserEntity
import com.karyaku.kasir.data.local.CustomerEntity
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
    
    val taxPercent by viewModel.taxPercent.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.setTaxPercent(settingsManager.taxPercent) }

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
    val totalDiscount = discount + happyHourDiscountAmount
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
    var completedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    
    // DIALOG STATE
    var showOpenShiftDialog by remember { mutableStateOf(false) }
    var showCloseShiftDialog by remember { mutableStateOf(false) }
    var showCashFlowDialog by remember { mutableStateOf(false) }
    var showDiscountDialog by remember { mutableStateOf(false) } 
    var showCustomerDialog by remember { mutableStateOf(false) }
    var modalAwal by remember { mutableStateOf("") }
    var showDrawerConfirm by remember { mutableStateOf(false) }

    var showHoldDialog by remember { mutableStateOf(false) }
    var showTableSelector by remember { mutableStateOf(false) }
    var showVoucherDialog by remember { mutableStateOf(false) }
    var isSplitBillMode by remember { mutableStateOf(false) }

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
        }
    }

    // --- DIALOGS ---

    // DIALOG AKSI PRODUK (REDESIGN TOTAL)
    if (showActionDialogFor != null) {
        ModalBottomSheet(
            onDismissRequest = { showActionDialogFor = null },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = showActionDialogFor!!.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                
                ActionMenuItem(
                    icon = Icons.Outlined.Edit,
                    title = "Edit Produk",
                    subtitle = "Ubah nama, harga, dan detail",
                    color = ZokasGreen,
                    onClick = { productToEdit = showActionDialogFor; showProductForm = true; showActionDialogFor = null }
                )
                ActionMenuItem(
                    icon = Icons.Outlined.AddBox,
                    title = "Tambah Stok (Restock)",
                    subtitle = "Perbarui jumlah persediaan barang",
                    color = ZokasOrange,
                    onClick = { productToRestock = showActionDialogFor; showActionDialogFor = null }
                )
                ActionMenuItem(
                    icon = Icons.Outlined.DeleteForever,
                    title = "Catat Barang Rusak",
                    subtitle = "Kurangi stok karena rusak/expired",
                    color = Color(0xFFF57C00),
                    onClick = { productToWaste = showActionDialogFor; showActionDialogFor = null }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                ActionMenuItem(
                    icon = Icons.Outlined.Delete,
                    title = "Hapus Produk",
                    subtitle = "Hapus permanen dari database",
                    color = Color.Red,
                    onClick = { productToDelete = showActionDialogFor; showActionDialogFor = null }
                )
            }
        }
    }

    if (showDrawerConfirm) {
        AlertDialog(
            onDismissRequest = { showDrawerConfirm = false },
            icon = { Icon(Icons.Default.PointOfSale, contentDescription = null) },
            title = { Text("Buka Laci Kasir") },
            text = { Text("Apakah Anda yakin ingin membuka laci tanpa transaksi? Aktivitas ini akan dicatat di Audit Log.") },
            confirmButton = {
                Button(onClick = { 
                    viewModel.logCashDrawerOpen(currentUser)
                    Toast.makeText(context, "Laci dibuka. Log tercatat.", Toast.LENGTH_SHORT).show()
                    showDrawerConfirm = false 
                }, colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)) { Text("BUKA LACI") }
            },
            dismissButton = { TextButton(onClick = { showDrawerConfirm = false }) { Text("Batal") } }
        )
    }

    if (productToWaste != null) {
        var qty by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { productToWaste = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color.Red) },
            title = { Text("Catat Barang Rusak") },
            text = {
                Column {
                    Text("Produk: ${productToWaste!!.name}", fontWeight = FontWeight.Bold)
                    Text("Stok saat ini: ${productToWaste!!.stock} pcs", color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = qty, 
                        onValueChange = { if (it.length <= 5) qty = it }, 
                        label = { Text("Jumlah Rusak (pcs)") }, 
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { 
                    val q = qty.toIntOrNull() ?: 0
                    if (q > 0 && q <= productToWaste!!.stock) {
                        viewModel.recordWaste(productToWaste!!, q, currentUser)
                        Toast.makeText(context, "Barang rusak dicatat.", Toast.LENGTH_SHORT).show()
                        productToWaste = null 
                    } else {
                        Toast.makeText(context, "Jumlah tidak valid!", Toast.LENGTH_SHORT).show()
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("CATAT RUSAK") }
            },
            dismissButton = { TextButton(onClick = { productToWaste = null }) { Text("Batal") } }
        )
    }

    if (productToEditNotes != null) {
        var notes by remember { mutableStateOf(cart.find { it.product.id == productToEditNotes!!.id }?.notes ?: "") }
        AlertDialog(
            onDismissRequest = { productToEditNotes = null },
            icon = { Icon(Icons.Default.EditNote, contentDescription = null) },
            title = { Text("Catatan Pesanan") },
            text = {
                Column {
                    Text("Tambahkan catatan untuk ${productToEditNotes!!.name}:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes, 
                        onValueChange = { notes = it }, 
                        label = { Text("Cth: Less Ice, Extra Shot") }, 
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { 
                    viewModel.updateCartItemNotes(productToEditNotes!!, notes)
                    productToEditNotes = null 
                }) { Text("SIMPAN") }
            },
            dismissButton = { TextButton(onClick = { productToEditNotes = null }) { Text("Batal") } }
        )
    }

    if (showOpenShiftDialog) {
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Default.LockOpen, contentDescription = null, tint = ZokasGreen) },
            title = { Text("Buka Kasir") },
            text = {
                Column {
                    Text("Masukkan modal awal uang tunai di laci:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = modalAwal, onValueChange = { if (it.length <= 10) modalAwal = it }, 
                        label = { Text("Modal Awal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.openShift(currentUser, modalAwal.toLongOrNull() ?: 0L); showOpenShiftDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("MULAI SHIFT") }
            }
        )
    }

    if (showCloseShiftDialog) { CloseShiftDialog(onDismiss = { showCloseShiftDialog = false }, onConfirm = { uangFisik -> viewModel.closeShift(currentUser, uangFisik) { showCloseShiftDialog = false; onLogout() } }) }
    if (showCashFlowDialog) { CashFlowDialog(onDismiss = { showCashFlowDialog = false }, onSave = { type, amount, note -> viewModel.addCashFlow(type, amount, note, currentUser); showCashFlowDialog = false }) }
    if (showDiscountDialog) { DiscountDialog(currentDiscount = discount, onDismiss = { showDiscountDialog = false }, onSave = { amount -> viewModel.setDiscount(amount); showDiscountDialog = false }) }
    if (showCustomerDialog) { CustomerDialog(customers = customers, selectedCustomer = selectedCustomer, onDismiss = { showCustomerDialog = false }, onSelect = { viewModel.setCustomer(it); showCustomerDialog = false }, onAddCustomer = { name, phone -> viewModel.addCustomer(name, phone) }, onRedeemPoints = { points -> viewModel.redeemPoints(points) }) }
    if (showTableSelector) { TableSelectorDialog(currentTable = selectedTable, occupiedTables = occupiedTables, onDismiss = { showTableSelector = false }, onSelect = { viewModel.setTable(it) }) }
    if (showVoucherDialog) { VoucherDialog(currentCode = appliedVoucher, onDismiss = { showVoucherDialog = false }, onApply = { code -> viewModel.applyVoucher(code) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); if (success) showVoucherDialog = false } }, onRemove = { viewModel.applyVoucher("") { _, _ -> }; showVoucherDialog = false }) }
    if (showHoldDialog) { HoldOrderDialog(holdOrders = holdOrders, onDismiss = { showHoldDialog = false }, onResume = { viewModel.resumeHoldOrder(it); showHoldDialog = false }) }

    if (completedTransaction != null) {
        ReceiptDialog(
            transaction = completedTransaction!!, viewModel = viewModel,
            storeName = if (settingsManager.isPro) settingsManager.storeName ?: "" else "ZoKas (Gratis)",
            storeAddress = if (settingsManager.isPro) settingsManager.storeAddress ?: "" else "",
            storePhone = if (settingsManager.isPro) settingsManager.storePhone ?: "" else "",
            storeFooter = if (settingsManager.isPro) settingsManager.storeFooter ?: "" else "",
            settingsManager = settingsManager, onDismiss = { completedTransaction = null }
        )
    }

    if (showPaymentSheet) {
        val totalToPay = if (isSplitBillMode) cart.filter { it.isSelectedForSplit }.sumOf { it.product.price * it.quantity } else totalPrice
        PaymentBottomSheet(
            totalPrice = totalToPay, onDismiss = { showPaymentSheet = false },
            onConfirmPayment = { cashReceived, method ->
                viewModel.processPayment(cashReceived, currentUser.id, currentUser.name, isSplitBillMode, method) { newTransaction ->
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
            onSave = { name, price, stock, category, costPrice, barcode ->
                if (productToEdit == null) viewModel.addProduct(name, price, stock, category, costPrice, barcode, currentUser.id, currentUser.name)
                else viewModel.updateProduct(productToEdit!!.copy(name = name, price = price, stock = stock, category = category, costPrice = costPrice, barcode = barcode), currentUser.id, currentUser.name)
                showProductForm = false; productToEdit = null
            }
        )
    }

    if (productToRestock != null) {
        StockOpnameDialog(product = productToRestock!!, onDismiss = { productToRestock = null }, onSave = { qty ->
            viewModel.addStock(productToRestock!!, qty, currentUser); productToRestock = null
            Toast.makeText(context, "Stok berhasil ditambah!", Toast.LENGTH_SHORT).show()
        })
    }

    if (productToDelete != null) {
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red) },
            title = { Text("Hapus Produk?") },
            text = { Text("Apakah Anda yakin ingin menghapus '${productToDelete!!.name}'? Tindakan ini tidak dapat dibatalkan.") },
            confirmButton = { Button(onClick = { viewModel.deleteProduct(productToDelete!!, currentUser.id, currentUser.name); productToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("YA, HAPUS") } },
            dismissButton = { TextButton(onClick = { productToDelete = null }) { Text("Batal") } }
        )
    }

    // --- MAIN UI ---
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_zokas), 
                            contentDescription = "Logo", 
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (settingsManager.isPro) settingsManager.storeName ?: "ZoKas" else "ZoKas", 
                                fontWeight = FontWeight.ExtraBold, 
                                fontSize = 18.sp, 
                                color = ZokasOrange,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Kasir: ${currentUser.name}", 
                                fontSize = 12.sp, 
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    if (currentUser.role == "OWNER" || currentUser.role == "SUPERVISOR") {
                        IconButton(onClick = { showDrawerConfirm = true }) { Icon(Icons.Default.PointOfSale, contentDescription = "Buka Laci", tint = ZokasGreen) }
                    }
                    IconButton(onClick = { showHoldDialog = true }) { Icon(Icons.Default.List, contentDescription = "Parkir", tint = ZokasGreen) }
                    IconButton(onClick = { showCashFlowDialog = true }) { Icon(Icons.Default.Wallet, contentDescription = "Kas", tint = ZokasOrange) }
                    IconButton(onClick = { showCloseShiftDialog = true }) { Icon(Icons.Default.Lock, contentDescription = "Tutup Kasir", tint = ZokasOrange) }
                    IconButton(onClick = onLogout) { Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(12.dp, CircleShape, spotColor = ZokasOrangeDark)
                    .background(brush = Brush.linearGradient(colors = listOf(ZokasOrange, ZokasOrangeDark)), shape = CircleShape)
                    .clickable { productToEdit = null; showProductForm = true },
                contentAlignment = Alignment.Center
            ) { 
                Icon(Icons.Default.Add, contentDescription = "Tambah", tint = Color.White, modifier = Modifier.size(32.dp)) 
            }
        },
        bottomBar = {
            if (cart.isNotEmpty() && !showCartOnPhone) {
                BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.height(72.dp)) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), 
                        horizontalArrangement = Arrangement.SpaceBetween, 
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${cart.sumOf { it.quantity }} item", fontSize = 12.sp, color = Color.Gray)
                            Text(formatRupiah(totalPrice), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = ZokasOrange)
                        }
                        Button(
                            onClick = { showCartOnPhone = true }, 
                            colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp)
                        ) { 
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lihat Keranjang", fontWeight = FontWeight.Bold) 
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            
            AnimatedVisibility(
                visible = isHappyHour,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(colors = listOf(Color.Red, Color(0xFFFF5722))))
                        .padding(vertical = 8.dp), 
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "HAPPY HOUR! Diskon ${settingsManager.happyHourDiscount}%", 
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp
                        )
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
                                onProductClick = { viewModel.addToCart(it) }, onProductLongClick = { showActionDialogFor = it },
                                onBarcodeScan = { barcode -> viewModel.processBarcodeScan(barcode) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); if (success) keyboardController?.hide() } }
                            ) 
                        }
                        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        Box(modifier = Modifier.weight(0.4f).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                            CartList(
                                cart = cart, subtotal = subtotal, discount = totalDiscount, taxAmount = taxAmount, totalPrice = totalPrice,
                                selectedCustomer = selectedCustomer, pointsUsed = pointsUsed, selectedTable = selectedTable,
                                appliedVoucher = appliedVoucher, voucherDiscount = voucherDiscount, isSplitBillMode = isSplitBillMode,
                                onToggleSplitMode = { isSplitBillMode = !isSplitBillMode }, onToggleSplitSelection = { viewModel.toggleSplitSelection(it) },
                                onTableClick = { showTableSelector = true }, onVoucherClick = { showVoucherDialog = true },
                                onHoldClick = { if (cart.isNotEmpty()) { viewModel.saveHoldOrder(selectedCustomer?.name ?: "Pelanggan"); Toast.makeText(context, "Pesanan diparkir.", Toast.LENGTH_SHORT).show() } },
                                onViewHoldClick = { showHoldDialog = true }, onCustomerClick = { showCustomerDialog = true },
                                onAdd = { viewModel.addToCart(it) }, onRemove = { viewModel.removeFromCart(it) }, onDelete = { viewModel.deleteItem(it) }, 
                                onDiscountClick = { showDiscountDialog = true }, onCheckout = { showPaymentSheet = true }, onEditNotes = { productToEditNotes = it }
                            )
                        }
                    }
                } else {
                    if (showCartOnPhone) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TextButton(
                                onClick = { showCartOnPhone = false }, 
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) { 
                                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kembali ke Katalog", color = ZokasOrange, fontWeight = FontWeight.Bold) 
                            }
                            CartList(
                                cart = cart, subtotal = subtotal, discount = totalDiscount, taxAmount = taxAmount, totalPrice = totalPrice,
                                selectedCustomer = selectedCustomer, pointsUsed = pointsUsed, selectedTable = selectedTable,
                                appliedVoucher = appliedVoucher, voucherDiscount = voucherDiscount, isSplitBillMode = isSplitBillMode,
                                onToggleSplitMode = { isSplitBillMode = !isSplitBillMode }, onToggleSplitSelection = { viewModel.toggleSplitSelection(it) },
                                onTableClick = { showTableSelector = true }, onVoucherClick = { showVoucherDialog = true },
                                onHoldClick = { if (cart.isNotEmpty()) { viewModel.saveHoldOrder(selectedCustomer?.name ?: "Pelanggan"); Toast.makeText(context, "Pesanan diparkir.", Toast.LENGTH_SHORT).show() } },
                                onViewHoldClick = { showHoldDialog = true }, onCustomerClick = { showCustomerDialog = true },
                                onAdd = { viewModel.addToCart(it) }, onRemove = { viewModel.removeFromCart(it) }, onDelete = { viewModel.deleteItem(it) }, 
                                onDiscountClick = { showDiscountDialog = true }, onCheckout = { showPaymentSheet = true }, onEditNotes = { productToEditNotes = it }
                            )
                        }
                    } else {
                        ProductCatalog(
                            products = filteredProducts, categories = categories, selectedCategory = selectedCategory,
                            onCategorySelect = { viewModel.setCategory(it) }, searchQuery = searchQuery, onSearchChange = { viewModel.setSearchQuery(it) },
                            onProductClick = { viewModel.addToCart(it) }, onProductLongClick = { showActionDialogFor = it },
                            onBarcodeScan = { barcode -> viewModel.processBarcodeScan(barcode) { success, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); if (success) keyboardController?.hide() } }
                        )
                    }
                }
            }
        }
    }
}

// ================= KOMPONEN UI BAWAH =================

@Composable
fun ActionMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductCatalog(
    products: List<ProductEntity>, categories: List<String>, selectedCategory: String,
    onCategorySelect: (String) -> Unit, searchQuery: String, onSearchChange: (String) -> Unit,
    onProductClick: (ProductEntity) -> Unit, onProductLongClick: (ProductEntity) -> Unit,
    onBarcodeScan: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery, 
            onValueChange = onSearchChange, 
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            placeholder = { Text("Cari produk / scan barcode...") }, 
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ZokasGreen) }, 
            trailingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan", tint = ZokasGreen) },
            singleLine = true, 
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ZokasGreen,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { 
                    if (searchQuery.isNotBlank()) {
                        onBarcodeScan(searchQuery)
                        onSearchChange("")
                    }
                }
            )
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                FilterChip(
                    selected = category == selectedCategory, 
                    onClick = { onCategorySelect(category) }, 
                    label = { Text(category, fontWeight = if(category == selectedCategory) FontWeight.Bold else FontWeight.Normal) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZokasGreen, 
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
        if (products.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                Text("Produk tidak ditemukan.", color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) 
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp), contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp), 
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items = products, key = { it.id }) { product ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
                        label = "scale"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .scale(scale)
                            .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = ZokasGreenDark)
                            .clip(RoundedCornerShape(24.dp))
                            .background(brush = Brush.linearGradient(colors = listOf(ZokasGreen, ZokasGreenDark)))
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = null, 
                                onClick = { onProductClick(product) }, 
                                onLongClick = { onProductLongClick(product) }
                            )
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Surface(
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = product.category, 
                                    fontSize = 10.sp, 
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = product.name, 
                                fontWeight = FontWeight.ExtraBold, 
                                maxLines = 2, 
                                style = MaterialTheme.typography.titleLarge, 
                                color = Color.White,
                                lineHeight = 24.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Column {
                                Text(
                                    text = formatRupiah(product.price), 
                                    style = MaterialTheme.typography.titleLarge, 
                                    color = ZokasOrange, 
                                    fontWeight = FontWeight.Black
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Inventory, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Stok: ${product.stock}", 
                                        style = MaterialTheme.typography.bodySmall, 
                                        fontWeight = FontWeight.Bold, 
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormBottomSheet(productToEdit: ProductEntity? = null, onDismiss: () -> Unit, onSave: (String, Long, Int, String, Long, String) -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf(productToEdit?.name ?: "") }
    var price by remember { mutableStateOf(productToEdit?.price?.toString() ?: "") }
    var costPrice by remember { mutableStateOf(productToEdit?.costPrice?.toString() ?: "") } 
    var stock by remember { mutableStateOf(productToEdit?.stock?.toString() ?: "") }
    var category by remember { mutableStateOf(productToEdit?.category ?: "Umum") } 
    var barcode by remember { mutableStateOf(productToEdit?.barcode ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (productToEdit == null) "Tambah Produk Baru" else "Edit Produk", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Produk") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("Barcode (Opsional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Kategori (cth: Minuman)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = costPrice, onValueChange = { if (it.length <= 10) costPrice = it }, label = { Text("Harga Beli / Modal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = price, onValueChange = { if (it.length <= 10) price = it }, label = { Text("Harga Jual (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = stock, onValueChange = { if (it.length <= 5) stock = it }, label = { Text("Stok") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { 
                val p = price.toLongOrNull() ?: 0L
                val cp = costPrice.toLongOrNull() ?: 0L
                val s = stock.toIntOrNull() ?: 0
                if (name.isNotBlank() && p > 0) onSave(name, p, s, category.ifBlank { "Umum" }, cp, barcode) 
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
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { paymentMethod = "CASH" },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (paymentMethod == "CASH") ZokasGreen else MaterialTheme.colorScheme.surfaceVariant)
                ) { Text("TUNAI", color = if (paymentMethod == "CASH") Color.White else Color.Gray, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { paymentMethod = "BON" },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (paymentMethod == "BON") ZokasOrange else MaterialTheme.colorScheme.surfaceVariant)
                ) { Text("BON (KASBON)", color = if (paymentMethod == "BON") Color.White else Color.Gray, fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (paymentMethod == "CASH") {
                OutlinedTextField(
                    value = cashInput, 
                    onValueChange = { if (it.length <= 10) cashInput = it }, 
                    label = { Text("Uang Diterima (Rp)") }, 
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                    modifier = Modifier.fillMaxWidth(), 
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kembalian:", style = MaterialTheme.typography.titleMedium)
                    Text(formatRupiah(change), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (change > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface)
                }
            } else {
                Surface(color = Color.Red.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Transaksi akan dicatat sebagai BON (Belum Lunas).", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { onConfirmPayment(if (paymentMethod == "BON") 0L else cashReceived, paymentMethod) }, 
                modifier = Modifier.fillMaxWidth().height(56.dp), 
                enabled = (paymentMethod == "BON") || (cashReceived >= totalPrice && totalPrice > 0), 
                colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange),
                shape = RoundedCornerShape(12.dp)
            ) { 
                Text("SELESAIKAN PEMBAYARAN", fontSize = 16.sp, fontWeight = FontWeight.Bold) 
            }
            Spacer(modifier = Modifier.height(32.dp))
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
    onEditNotes: (ProductEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ElevatedButton(
                onClick = onTableClick, 
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.elevatedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = ButtonDefaults.elevatedButtonElevation(2.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(selectedTable, fontWeight = FontWeight.Bold, color = ZokasOrange, fontSize = 12.sp, maxLines = 1)
            }
            ElevatedButton(
                onClick = onHoldClick, 
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.elevatedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = ButtonDefaults.elevatedButtonElevation(2.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Parkir", fontWeight = FontWeight.Bold, color = ZokasGreen, fontSize = 12.sp)
            }
            ElevatedButton(
                onClick = onViewHoldClick, 
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.elevatedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = ButtonDefaults.elevatedButtonElevation(2.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Lihat", fontWeight = FontWeight.Bold, color = ZokasGreen, fontSize = 12.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedButton(
            onClick = onCustomerClick, 
            modifier = Modifier.fillMaxWidth().height(48.dp), 
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ZokasOrange.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = ZokasOrange, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = if (selectedCustomer != null) "Pelanggan: ${selectedCustomer.name}" else "Pelanggan: Umum", fontWeight = FontWeight.Bold, color = ZokasOrange)
        }
        
        if (pointsUsed > 0) {
            Text("Poin digunakan: $pointsUsed (Potongan ${formatRupiah((pointsUsed * 1000).toLong())})", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Mode Split Bill", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Switch(checked = isSplitBillMode, onCheckedChange = { onToggleSplitMode() })
        }
        
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cart) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(), 
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        if (isSplitBillMode) {
                            Checkbox(checked = item.isSelectedForSplit, onCheckedChange = { onToggleSplitSelection(item.product) })
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(formatRupiah(item.product.price), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                            if (item.notes.isNotEmpty()) {
                                Text("Catatan: ${item.notes}", fontSize = 11.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onEditNotes(item.product) }) { 
                                Icon(Icons.Default.EditNote, contentDescription = "Catatan", tint = ZokasGreen) 
                            }
                            IconButton(onClick = { onRemove(item.product) }) { Icon(Icons.Default.Remove, contentDescription = "Kurang") }
                            Text(text = "${item.quantity}", modifier = Modifier.padding(horizontal = 4.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            IconButton(onClick = { onAdd(item.product) }) { Icon(Icons.Default.Add, contentDescription = "Tambah") }
                            IconButton(onClick = { onDelete(item.product) }) { Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red) }
                        }
                    }
                }
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Subtotal:", style = MaterialTheme.typography.titleMedium)
            Text(formatRupiah(subtotal), style = MaterialTheme.typography.titleMedium)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Diskon:", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDiscountClick) { Text("Ubah", color = ZokasOrange) }
            }
            Text("- ${formatRupiah(discount)}", style = MaterialTheme.typography.titleMedium, color = Color.Red)
        }
        if (appliedVoucher.isNotEmpty() || voucherDiscount > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Voucher ($appliedVoucher):", style = MaterialTheme.typography.titleMedium)
                Text("- ${formatRupiah(voucherDiscount)}", style = MaterialTheme.typography.titleMedium, color = Color.Red)
            }
        }
        TextButton(onClick = onVoucherClick, modifier = Modifier.align(Alignment.End)) { 
            Text(if (appliedVoucher.isEmpty()) "+ Gunakan Voucher" else "Ganti Voucher", color = ZokasGreen, fontWeight = FontWeight.Bold) 
        }
        
        if (taxAmount > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pajak:", style = MaterialTheme.typography.titleMedium)
                Text("+ ${formatRupiah(taxAmount)}", style = MaterialTheme.typography.titleMedium, color = ZokasOrange)
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total:", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(formatRupiah(totalPrice), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = ZokasOrange)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onCheckout, 
            modifier = Modifier.fillMaxWidth().height(56.dp), 
            enabled = cart.isNotEmpty() && (!isSplitBillMode || cart.any { it.isSelectedForSplit }), 
            colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (isSplitBillMode) "BAYAR SPLIT BILL (${cart.filter { it.isSelectedForSplit }.size} item)" else "PROSES PEMBAYARAN", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TableSelectorDialog(currentTable: String, occupiedTables: List<String>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val tables = listOf("Take-Away", "Meja 1", "Meja 2", "Meja 3", "Meja 4", "Meja 5")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.TableRestaurant, contentDescription = null, tint = ZokasOrange) },
        title = { Text("Pilih Meja (Peta Meja)") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Box(modifier = Modifier.size(12.dp).background(ZokasGreen, CircleShape))
                    Text("Tersedia", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.size(12.dp).background(Color.Red, CircleShape))
                    Text("Terisi (Bon/Parkir)", fontSize = 12.sp)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                for (table in tables) {
                    val isOccupied = occupiedTables.contains(table)
                    OutlinedButton(
                        onClick = { onSelect(table); onDismiss() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (table == currentTable) ZokasGreen.copy(alpha = 0.2f) 
                                             else if (isOccupied) Color.Red.copy(alpha = 0.1f) 
                                             else Color.Transparent
                        ),
                        border = if (isOccupied) BorderStroke(1.dp, Color.Red) else null,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isOccupied) "$table (Terisi)" else table, 
                            color = if (isOccupied) Color.Red else if (table == currentTable) ZokasGreen else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isOccupied) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
fun VoucherDialog(
    currentCode: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
    onRemove: () -> Unit
) {
    var code by remember { mutableStateOf(currentCode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = ZokasGreen) },
        title = { Text("Gunakan Voucher") },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Kode Voucher") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                if (currentCode.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Voucher terpasang: $currentCode", color = ZokasGreen, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(code) },
                enabled = code.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)
            ) { Text("TERAPKAN") }
        },
        dismissButton = {
            if (currentCode.isNotEmpty()) {
                TextButton(onClick = onRemove) { Text("Hapus Voucher", color = Color.Red) }
            } else {
                TextButton(onClick = onDismiss) { Text("Tutup") }
            }
        }
    )
}

@Composable
fun CustomerDialog(
    customers: List<CustomerEntity>,
    selectedCustomer: CustomerEntity?,
    onDismiss: () -> Unit,
    onSelect: (CustomerEntity?) -> Unit,
    onAddCustomer: (String, String) -> Unit,
    onRedeemPoints: (Int) -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    var showRedeemForm by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var pointsToRedeem by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredCustomers = customers.filter { 
        it.name.contains(searchQuery, ignoreCase = true) || 
        it.phone.contains(searchQuery, ignoreCase = true) 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.People, contentDescription = null, tint = ZokasOrange) },
        title = { 
            Text(
                when {
                    showAddForm -> "Tambah Pelanggan Baru"
                    showRedeemForm -> "Tukar Poin Pelanggan"
                    else -> "Pilih Pelanggan"
                }
            ) 
        },
        text = {
            when {
                showAddForm -> {
                    Column {
                        OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Nama Pelanggan") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = newPhone, onValueChange = { newPhone = it }, label = { Text("No. HP (Opsional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                }
                showRedeemForm && selectedCustomer != null -> {
                    Column {
                        Text("Poin tersedia: ${selectedCustomer.points}")
                        Text("1 Poin = Rp 1.000", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pointsToRedeem, 
                            onValueChange = { if (it.length <= 5) pointsToRedeem = it }, 
                            label = { Text("Jumlah Poin yang Ditukar") }, 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                else -> {
                    Column {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            placeholder = { Text("Cari nama / no. HP...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        TextButton(onClick = { onSelect(null); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Pelanggan Umum (Tanpa Nama)", fontWeight = FontWeight.Bold, color = ZokasOrange)
                        }
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (filteredCustomers.isEmpty()) {
                            Text("Pelanggan tidak ditemukan.", color = Color.Gray, modifier = Modifier.padding(16.dp))
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                items(filteredCustomers) { customer ->
                                    TextButton(
                                        onClick = { onSelect(customer); onDismiss() },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("${customer.name} (${customer.points} Poin)", fontWeight = FontWeight.Bold)
                                            val displayPhone = if (customer.phone == "-" || customer.phone.isBlank()) "" else customer.phone
                                            Text(displayPhone, fontSize = 12.sp, color = Color.Gray)
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                showAddForm -> {
                    Button(onClick = { 
                        if (newName.isNotBlank()) {
                            onAddCustomer(newName, if(newPhone.isBlank()) "-" else newPhone)
                            showAddForm = false
                            newName = ""
                            newPhone = ""
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("SIMPAN") }
                }
                showRedeemForm && selectedCustomer != null -> {
                    Button(onClick = { 
                        val pts = pointsToRedeem.toIntOrNull() ?: 0
                        if (pts > 0 && pts <= selectedCustomer.points) {
                            onRedeemPoints(pts)
                            onDismiss()
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("TUKAR POIN") }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selectedCustomer != null && selectedCustomer.points > 0) {
                            Button(onClick = { showRedeemForm = true }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) {
                                Text("Tukar Poin")
                            }
                        }
                        Button(onClick = { showAddForm = true }, colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)) {
                            Text("+ Pelanggan")
                        }
                    }
                }
            }
        },
        dismissButton = { 
            TextButton(onClick = { 
                if (showAddForm || showRedeemForm) { showAddForm = false; showRedeemForm = false } 
                else onDismiss() 
            }) { Text("Tutup") } 
        }
    )
}

@Composable
fun CashFlowDialog(onDismiss: () -> Unit, onSave: (String, Long, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("OUT") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Wallet, contentDescription = null, tint = ZokasOrange) },
        title = { Text("Catat Kas Keluar / Masuk") },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { type = "OUT" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (type == "OUT") ZokasOrange else Color.Gray)) { Text("Kas Keluar") }
                    Button(onClick = { type = "IN" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (type == "IN") ZokasGreen else Color.Gray)) { Text("Kas Masuk") }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = amount, onValueChange = { if (it.length <= 10) amount = it }, label = { Text("Nominal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Keterangan (cth: Beli Galon)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = {
            Button(onClick = { val amt = amount.toLongOrNull() ?: 0L; if (amt > 0 && note.isNotBlank()) onSave(type, amt, note) }, colors = ButtonDefaults.buttonColors(containerColor = if (type == "OUT") ZokasOrange else ZokasGreen)) { Text("SIMPAN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun DiscountDialog(currentDiscount: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var amount by remember { mutableStateOf(if (currentDiscount > 0) currentDiscount.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.LocalOffer, contentDescription = null, tint = ZokasGreen) },
        title = { Text("Diskon Manual") },
        text = {
            Column {
                Text("Masukkan nominal potongan harga (Rp):")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = amount, onValueChange = { if (it.length <= 10) amount = it }, label = { Text("Nominal Diskon") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = { Button(onClick = { onSave(amount.toLongOrNull() ?: 0L) }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("SIMPAN DISKON") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun CloseShiftDialog(onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var uangFisik by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = ZokasOrange) },
        title = { Text("Tutup Kasir (Blind Closing)") },
        text = {
            Column {
                Text("Masukkan jumlah uang fisik yang ada di laci saat ini (tanpa melihat sistem):")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = uangFisik, onValueChange = { if (it.length <= 10) uangFisik = it }, label = { Text("Total Uang Fisik (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = { Button(onClick = { onConfirm(uangFisik.toLongOrNull() ?: 0L) }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("TUTUP KASIR") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
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
        if (it.notes.isNotEmpty()) {
            output.write("  *Cat: ${it.notes}\n".toByteArray())
        }
    }
    output.write("\n\n\n".toByteArray())
    output.write(EscPosCommands.FEED_3)
    output.write(EscPosCommands.CUT_PAPER)
    return output.toByteArray()
}