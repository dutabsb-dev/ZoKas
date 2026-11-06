package com.karyaku.kasir

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.TransactionEntity
import com.karyaku.kasir.data.local.UserEntity
import com.karyaku.kasir.data.local.CustomerEntity
import java.text.NumberFormat
import java.util.Locale

fun formatRupiah(amount: Long): String {
    val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    return format.format(amount).split(",")[0].replace("Rp", "Rp ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(viewModel: PosViewModel, settingsManager: SettingsManager, currentUser: UserEntity, onLogout: () -> Unit) {
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val discount by viewModel.discount.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val selectedCustomer by viewModel.selectedCustomer.collectAsStateWithLifecycle()
    
    // State Filter Realtime
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val filteredProducts by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val categories by viewModel.uniqueCategories.collectAsStateWithLifecycle()
    val pointsUsed by viewModel.pointsUsed.collectAsStateWithLifecycle()
    val subtotal = cart.sumOf { it.product.price * it.quantity }
    val totalPrice = (subtotal - discount).coerceAtLeast(0)

    var showCartOnPhone by remember { mutableStateOf(false) }
    var showPaymentSheet by remember { mutableStateOf(false) }
    var showProductForm by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<ProductEntity?>(null) }
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }
    var showActionDialogFor by remember { mutableStateOf<ProductEntity?>(null) }
    var completedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    
    var showOpenShiftDialog by remember { mutableStateOf(false) }
    var showCloseShiftDialog by remember { mutableStateOf(false) }
    var showCashFlowDialog by remember { mutableStateOf(false) }
    var showDiscountDialog by remember { mutableStateOf(false) } 
    var showCustomerDialog by remember { mutableStateOf(false) }
    var modalAwal by remember { mutableStateOf("") }

    LaunchedEffect(activeShift) {
        if (activeShift == null) showOpenShiftDialog = true
    }

    // ================= DIALOG - DIALOG =================
    
    // 1. Dialog Buka Kasir
    if (showOpenShiftDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Buka Kasir (Mulai Shift)") },
            text = {
                Column {
                    Text("Masukkan modal awal uang tunai di laci:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = modalAwal, onValueChange = { if (it.length <= 10) modalAwal = it }, label = { Text("Modal Awal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.openShift(currentUser, modalAwal.toLongOrNull() ?: 0L); showOpenShiftDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("BUKA KASIR") }
            }
        )
    }

    // 2. Dialog Tutup Kasir
    if (showCloseShiftDialog) {
        CloseShiftDialog(onDismiss = { showCloseShiftDialog = false }, onConfirm = { uangFisik -> 
            viewModel.closeShift(currentUser, uangFisik) { 
                showCloseShiftDialog = false
                onLogout() 
            } 
        })
    }

    // 3. Dialog Kas Masuk/Keluar
    if (showCashFlowDialog) {
        CashFlowDialog(onDismiss = { showCashFlowDialog = false }, onSave = { type, amount, note -> 
            viewModel.addCashFlow(type, amount, note, currentUser)
            showCashFlowDialog = false 
        })
    }

    // 4. Dialog Diskon
    if (showDiscountDialog) {
        DiscountDialog(currentDiscount = discount, onDismiss = { showDiscountDialog = false }, onSave = { amount -> 
            viewModel.setDiscount(amount)
            showDiscountDialog = false 
        })
    }

    // 5. Dialog Pelanggan
    if (showCustomerDialog) {
        CustomerDialog(
            customers = customers, 
            selectedCustomer = selectedCustomer, 
            onDismiss = { showCustomerDialog = false }, 
            onSelect = { customer -> 
                viewModel.setCustomer(customer)
                showCustomerDialog = false 
            }, 
            onAddCustomer = { name, phone -> viewModel.addCustomer(name, phone) },
            onRedeemPoints = { points -> viewModel.redeemPoints(points) }
        )
    }

    // 6. Dialog Struk Pembayaran
       if (completedTransaction != null) {
        ReceiptDialog(
            transaction = completedTransaction!!,
            storeName = if (settingsManager.isPro) settingsManager.storeName else "ZoKas (Gratis)",
            storeAddress = if (settingsManager.isPro) settingsManager.storeAddress else "Alamat belum diatur", // <--- TAMBAHAN
            storePhone = if (settingsManager.isPro) settingsManager.storePhone else "-", // <--- TAMBAHAN
            storeFooter = if (settingsManager.isPro) settingsManager.storeFooter else "Upgrade ke Pro!",
            settingsManager = settingsManager,
            onDismiss = { completedTransaction = null }
        )
    }

    // 7. Bottom Sheet Pembayaran
    if (showPaymentSheet) {
        PaymentBottomSheet(
            totalPrice = totalPrice,
            onDismiss = { showPaymentSheet = false },
            onConfirmPayment = { cashReceived ->
                viewModel.processPayment(cashReceived, currentUser.id, currentUser.name) { newId ->
                    showPaymentSheet = false
                    showCartOnPhone = false
                    completedTransaction = TransactionEntity(
                        id = newId, 
                        totalPrice = totalPrice, 
                        cashReceived = cashReceived, 
                        changeAmount = cashReceived - totalPrice, 
                        customerName = selectedCustomer?.name ?: "Pelanggan Umum"
                    )
                }
            }
        )
    }

    // 8. Bottom Sheet Form Produk
        if (showProductForm) {
        ProductFormBottomSheet(
            productToEdit = productToEdit, 
            onDismiss = { showProductForm = false; productToEdit = null }, 
            onSave = { name, price, stock, category, costPrice -> // <--- Tambah costPrice
                if (productToEdit == null) viewModel.addProduct(name, price, stock, category, costPrice, currentUser.id, currentUser.name)
                else viewModel.updateProduct(productToEdit!!.copy(name = name, price = price, stock = stock, category = category, costPrice = costPrice), currentUser.id, currentUser.name)
                showProductForm = false
                productToEdit = null
            }
        )
    }

    // 9. Dialog Aksi Produk (Edit/Hapus)
    if (showActionDialogFor != null) {
        AlertDialog(
            onDismissRequest = { showActionDialogFor = null },
            title = { Text(showActionDialogFor!!.name) },
            text = { Text("Pilih tindakan untuk produk ini:") },
            confirmButton = { 
                TextButton(onClick = { 
                    productToEdit = showActionDialogFor
                    showProductForm = true
                    showActionDialogFor = null 
                }) { 
                    Icon(Icons.Default.Edit, contentDescription = null, tint = ZokasGreen)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", color = ZokasGreen) 
                } 
            },
            dismissButton = { 
                TextButton(onClick = { 
                    productToDelete = showActionDialogFor
                    showActionDialogFor = null 
                }) { 
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hapus", color = Color.Red) 
                } 
            }
        )
    }

    // 10. Dialog Konfirmasi Hapus
    if (productToDelete != null) {
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Konfirmasi Hapus") },
            text = { Text("Apakah Anda yakin ingin menghapus '${productToDelete!!.name}'?") },
            confirmButton = { 
                TextButton(onClick = { 
                    viewModel.deleteProduct(productToDelete!!, currentUser.id, currentUser.name)
                    productToDelete = null 
                }) { 
                    Text("Ya, Hapus", color = Color.Red) 
                } 
            },
            dismissButton = { TextButton(onClick = { productToDelete = null }) { Text("Batal") } }
        )
    }

    // ================= UI UTAMA =================
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painter = painterResource(id = R.drawable.logo_zokas), contentDescription = "Logo", modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = if (settingsManager.isPro) settingsManager.storeName else "ZoKas", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(text = "Kasir: ${currentUser.name}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showCashFlowDialog = true }) { Icon(Icons.Default.Wallet, contentDescription = "Kas", tint = ZokasOrange) }
                    IconButton(onClick = { showCloseShiftDialog = true }) { Icon(Icons.Default.Lock, contentDescription = "Tutup Kasir", tint = ZokasOrange) }
                    IconButton(onClick = onLogout) { Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        floatingActionButton = {
            Box(
                modifier = Modifier.size(64.dp).shadow(12.dp, CircleShape, spotColor = ZokasOrangeDark)
                    .background(brush = Brush.linearGradient(colors = listOf(ZokasOrange, ZokasOrangeDark)), shape = CircleShape)
                    .clickable { productToEdit = null; showProductForm = true },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Add, contentDescription = "Tambah", tint = Color.White, modifier = Modifier.size(32.dp)) }
        },
        bottomBar = {
            if (cart.isNotEmpty() && !showCartOnPhone) {
                BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.height(72.dp)) {
                    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Total (${cart.sumOf { it.quantity }} item)", fontSize = 12.sp)
                            Text(formatRupiah(totalPrice), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ZokasOrange)
                        }
                        Button(onClick = { showCartOnPhone = true }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("Lihat Keranjang") }
                    }
                }
            }
        }
    ) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            val isTablet = maxWidth > 600.dp

            if (isTablet) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(0.6f).fillMaxHeight()) { 
                        ProductCatalog(
                            products = filteredProducts,
                            categories = categories,
                            selectedCategory = selectedCategory,
                            onCategorySelect = { viewModel.setCategory(it) },
                            searchQuery = searchQuery,
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            onProductClick = { viewModel.addToCart(it) },
                            onProductLongClick = { showActionDialogFor = it }
                        ) 
                    }
                    Divider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    Box(modifier = Modifier.weight(0.4f).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                        CartList(
                            cart = cart, subtotal = subtotal, discount = discount, totalPrice = totalPrice,
                            selectedCustomer = selectedCustomer,
                            pointsUsed = pointsUsed,
                            onCustomerClick = { showCustomerDialog = true },
                            onAdd = { viewModel.addToCart(it) }, onRemove = { viewModel.removeFromCart(it) }, onDelete = { viewModel.deleteItem(it) }, 
                            onDiscountClick = { showDiscountDialog = true }, onCheckout = { showPaymentSheet = true }
                        )
                    }
                }
            } else {
                if (showCartOnPhone) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TextButton(onClick = { showCartOnPhone = false }, modifier = Modifier.padding(8.dp)) { Text("< Kembali ke Katalog", color = ZokasOrange) }
                        CartList(
                            cart = cart, subtotal = subtotal, discount = discount, totalPrice = totalPrice,
                            selectedCustomer = selectedCustomer,
                            pointsUsed = pointsUsed,
                            onCustomerClick = { showCustomerDialog = true },
                            onAdd = { viewModel.addToCart(it) }, onRemove = { viewModel.removeFromCart(it) }, onDelete = { viewModel.deleteItem(it) }, 
                            onDiscountClick = { showDiscountDialog = true }, onCheckout = { showPaymentSheet = true }
                        )
                    }
                } else {
                    ProductCatalog(
                        products = filteredProducts,
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onCategorySelect = { viewModel.setCategory(it) },
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        onProductClick = { viewModel.addToCart(it) },
                        onProductLongClick = { showActionDialogFor = it }
                    )
                }
            }
        }
    }
}

// ================= KOMPONEN UI =================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductCatalog(
    products: List<ProductEntity>, 
    categories: List<String>,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onProductClick: (ProductEntity) -> Unit, 
    onProductLongClick: (ProductEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            placeholder = { Text("Cari produk...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                FilterChip(
                    selected = category == selectedCategory,
                    onClick = { onCategorySelect(category) },
                    label = { Text(category) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ZokasGreen,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        if (products.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Produk tidak ditemukan.", color = MaterialTheme.colorScheme.onSurface) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp), 
                contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp), 
                horizontalArrangement = Arrangement.spacedBy(16.dp), 
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items = products, key = { it.id }) { product ->
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).shadow(8.dp, RoundedCornerShape(20.dp), spotColor = ZokasGreenDark)
                            .clip(RoundedCornerShape(20.dp)).background(brush = Brush.linearGradient(colors = listOf(ZokasGreen, ZokasGreenDark)))
                            .combinedClickable(onClick = { onProductClick(product) }, onLongClick = { onProductLongClick(product) })
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Text(text = product.category, fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                            Text(text = product.name, fontWeight = FontWeight.Bold, maxLines = 2, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(text = formatRupiah(product.price), style = MaterialTheme.typography.titleLarge, color = ZokasOrange, fontWeight = FontWeight.Bold)
                            Text(text = "Stok: ${product.stock}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormBottomSheet(
    productToEdit: ProductEntity? = null, 
    onDismiss: () -> Unit, 
    onSave: (String, Long, Int, String, Long) -> Unit // <--- Parameter Baru (costPrice)
) {
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf(productToEdit?.name ?: "") }
    var price by remember { mutableStateOf(productToEdit?.price?.toString() ?: "") }
    var costPrice by remember { mutableStateOf(productToEdit?.costPrice?.toString() ?: "") } // State HPP
    var stock by remember { mutableStateOf(productToEdit?.stock?.toString() ?: "") }
    var category by remember { mutableStateOf(productToEdit?.category ?: "Umum") } 

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (productToEdit == null) "Tambah Produk Baru" else "Edit Produk", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Produk") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Kategori (cth: Minuman)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            
            Spacer(modifier = Modifier.height(8.dp))
            // INPUT HARGA BELI (HPP)
            OutlinedTextField(
                value = costPrice, 
                onValueChange = { if (it.length <= 10) costPrice = it }, 
                label = { Text("Harga Beli / Modal (Rp)") }, 
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                modifier = Modifier.fillMaxWidth(), 
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = price, onValueChange = { if (it.length <= 10) price = it }, label = { Text("Harga Jual (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
            
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = stock, onValueChange = { if (it.length <= 5) stock = it }, label = { Text("Stok") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
            
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { 
                val p = price.toLongOrNull() ?: 0L
                val cp = costPrice.toLongOrNull() ?: 0L
                val s = stock.toIntOrNull() ?: 0
                if (name.isNotBlank() && p > 0) onSave(name, p, s, category.ifBlank { "Umum" }, cp) 
            }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = name.isNotBlank() && (price.toLongOrNull() ?: 0L) > 0, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) {
                Text(if (productToEdit == null) "SIMPAN PRODUK" else "UPDATE PRODUK", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentBottomSheet(totalPrice: Long, onDismiss: () -> Unit, onConfirmPayment: (Long) -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    var cashInput by remember { mutableStateOf("") }
    val cashReceived = cashInput.toLongOrNull() ?: 0L
    val change = if (cashReceived >= totalPrice) cashReceived - totalPrice else 0L

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Pembayaran Tunai", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Total Tagihan:", style = MaterialTheme.typography.titleMedium)
            Text(formatRupiah(totalPrice), style = MaterialTheme.typography.headlineMedium, color = ZokasOrange, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(value = cashInput, onValueChange = { if (it.length <= 10) cashInput = it }, label = { Text("Uang Diterima (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Kembalian:", style = MaterialTheme.typography.titleMedium)
                Text(formatRupiah(change), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (change > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { onConfirmPayment(cashReceived) }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = cashReceived >= totalPrice && totalPrice > 0, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("SELESAIKAN PEMBAYARAN", fontSize = 16.sp) }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun CartList(
    cart: List<CartItem>, 
    subtotal: Long, 
    discount: Long, 
    totalPrice: Long, 
    selectedCustomer: CustomerEntity?, 
    pointsUsed: Int,
    onCustomerClick: () -> Unit,      
    onAdd: (ProductEntity) -> Unit, 
    onRemove: (ProductEntity) -> Unit, 
    onDelete: (ProductEntity) -> Unit, 
    onDiscountClick: () -> Unit, 
    onCheckout: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Keranjang Belanja", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedButton(onClick = onCustomerClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.Person, contentDescription = null, tint = ZokasOrange, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (selectedCustomer != null) "Pelanggan: ${selectedCustomer.name}" else "Pelanggan: Umum", 
                fontWeight = FontWeight.Bold, color = ZokasOrange
            )
        }
        
        // TAMPILKAN INFO POIN JIKA ADA YANG DITUKAR
        if (pointsUsed > 0) {
            Text("Poin digunakan: $pointsUsed (Potongan ${formatRupiah((pointsUsed * 1000).toLong())})", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cart) { item ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.product.name, fontWeight = FontWeight.Bold)
                            Text(formatRupiah(item.product.price), style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onRemove(item.product) }) { Icon(Icons.Default.Remove, contentDescription = "Kurang") }
                            Text(text = "${item.quantity}", modifier = Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
                            IconButton(onClick = { onAdd(item.product) }) { Icon(Icons.Default.Add, contentDescription = "Tambah") }
                            IconButton(onClick = { onDelete(item.product) }) { Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red) }
                        }
                    }
                }
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        
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

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total:", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(formatRupiah(totalPrice), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ZokasOrange)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onCheckout, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = cart.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) {
            Text("PROSES PEMBAYARAN", fontSize = 16.sp)
        }
    }
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
    
    // State untuk Pencarian
    var searchQuery by remember { mutableStateOf("") }

    // Filter pelanggan berdasarkan pencarian (Nama atau No HP)
    val filteredCustomers = customers.filter { 
        it.name.contains(searchQuery, ignoreCase = true) || 
        it.phone.contains(searchQuery, ignoreCase = true) 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                        OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Nama Pelanggan") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = newPhone, onValueChange = { newPhone = it }, label = { Text("No. HP (Opsional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
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
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                else -> {
                    Column {
                        // KOLOM PENCARIAN DITAMBAHKAN DI SINI
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            placeholder = { Text("Cari nama / no. HP...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
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
                                            // Sembunyikan tanda '-' jika nomor HP kosong
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
        title = { Text("Catat Kas Keluar / Masuk") },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { type = "OUT" }, colors = ButtonDefaults.buttonColors(containerColor = if (type == "OUT") ZokasOrange else Color.Gray)) { Text("Kas Keluar") }
                    Button(onClick = { type = "IN" }, colors = ButtonDefaults.buttonColors(containerColor = if (type == "IN") ZokasGreen else Color.Gray)) { Text("Kas Masuk") }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = amount, onValueChange = { if (it.length <= 10) amount = it }, label = { Text("Nominal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Keterangan (cth: Beli Galon)") }, modifier = Modifier.fillMaxWidth())
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
        title = { Text("Diskon Manual") },
        text = {
            Column {
                Text("Masukkan nominal potongan harga (Rp):")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = amount, onValueChange = { if (it.length <= 10) amount = it }, label = { Text("Nominal Diskon") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
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
        title = { Text("Tutup Kasir (Blind Closing)") },
        text = {
            Column {
                Text("Masukkan jumlah uang fisik yang ada di laci saat ini (tanpa melihat sistem):")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = uangFisik, onValueChange = { if (it.length <= 10) uangFisik = it }, label = { Text("Total Uang Fisik (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onConfirm(uangFisik.toLongOrNull() ?: 0L) }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("TUTUP KASIR") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}