package com.karyaku.kasir

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Remove
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
import java.text.NumberFormat
import java.util.Locale

fun formatRupiah(amount: Long): String {
    val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    return format.format(amount).split(",")[0].replace("Rp", "Rp ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(viewModel: PosViewModel, settingsManager: SettingsManager, currentUser: UserEntity, onLogout: () -> Unit) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val totalPrice = cart.sumOf { it.product.price * it.quantity }
    
    var showCartOnPhone by remember { mutableStateOf(false) }
    var showPaymentSheet by remember { mutableStateOf(false) }
    
    var showProductForm by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<ProductEntity?>(null) }
    
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }
    var showActionDialogFor by remember { mutableStateOf<ProductEntity?>(null) }
    
    var completedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    
    var showOpenShiftDialog by remember { mutableStateOf(false) }
    var modalAwal by remember { mutableStateOf("") }

    LaunchedEffect(activeShift) {
        if (activeShift == null) {
            showOpenShiftDialog = true
        }
    }

    if (showOpenShiftDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Buka Kasir (Mulai Shift)") },
            text = {
                Column {
                    Text("Masukkan modal awal uang tunai di laci:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = modalAwal,
                        onValueChange = { if (it.length <= 10) modalAwal = it },
                        label = { Text("Modal Awal (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val modal = modalAwal.toLongOrNull() ?: 0L
                        viewModel.openShift(currentUser, modal)
                        showOpenShiftDialog = false
                    }
                ) { Text("BUKA KASIR") }
            }
        )
    }

    if (completedTransaction != null) {
        ReceiptDialog(
            transaction = completedTransaction!!,
            storeName = if (settingsManager.isPro) settingsManager.storeName else "ZoKas (Gratis)",
            storeFooter = if (settingsManager.isPro) settingsManager.storeFooter else "Upgrade ke Pro untuk kustom struk!",
            onDismiss = { completedTransaction = null }
        )
    }

    if (showPaymentSheet) {
        PaymentBottomSheet(
            totalPrice = totalPrice,
            onDismiss = { showPaymentSheet = false },
            onConfirmPayment = { cashReceived ->
                viewModel.processPayment(cashReceived, currentUser.id, currentUser.name) { newId ->
                    showPaymentSheet = false
                    showCartOnPhone = false
                    completedTransaction = TransactionEntity(id = newId, totalPrice = totalPrice, cashReceived = cashReceived, changeAmount = cashReceived - totalPrice)
                }
            }
        )
    }

    if (showProductForm) {
        ProductFormBottomSheet(
            productToEdit = productToEdit,
            onDismiss = { showProductForm = false; productToEdit = null },
            onSave = { name, price, stock ->
                if (productToEdit == null) {
                    viewModel.addProduct(name, price, stock, currentUser.id, currentUser.name)
                } else {
                    viewModel.updateProduct(productToEdit!!.copy(name = name, price = price, stock = stock), currentUser.id, currentUser.name)
                }
                showProductForm = false
                productToEdit = null
            }
        )
    }

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
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    productToDelete = showActionDialogFor
                    showActionDialogFor = null
                }) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hapus", color = Color.Red)
                }
            }
        )
    }

    if (productToDelete != null) {
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Konfirmasi Hapus") },
            text = { Text("Apakah Anda yakin ingin menghapus '${productToDelete!!.name}' secara permanen?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProduct(productToDelete!!, currentUser.id, currentUser.name)
                    productToDelete = null
                }) {
                    Text("Ya, Hapus", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) { Text("Batal") }
            }
        )
    }

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
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = ZokasOrange)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        floatingActionButton = {
            // TOMBOL FAB 3D ORANGE
            FloatingActionButton(
                onClick = { productToEdit = null; showProductForm = true },
                containerColor = Color.Transparent,
                contentColor = Color.White,
                modifier = Modifier
                    .size(64.dp)
                    .shadow(12.dp, RoundedCornerShape(50), spotColor = ZokasOrangeDark)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(ZokasOrange, ZokasOrangeDark)
                        ),
                        shape = RoundedCornerShape(50)
                    )
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Produk Baru", modifier = Modifier.size(32.dp))
            }
        },
        bottomBar = {
            if (cart.isNotEmpty() && !showCartOnPhone) {
                BottomAppBar(containerColor = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.height(72.dp)) {
                    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Total (${cart.sumOf { it.quantity }} item)", fontSize = 12.sp, color = ZokasTextWhite)
                            Text(formatRupiah(totalPrice), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ZokasOrange)
                        }
                        Button(
                            onClick = { showCartOnPhone = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)
                        ) { Text("Lihat Keranjang") }
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
                            products = products, 
                            onProductClick = { viewModel.addToCart(it) },
                            onProductLongClick = { showActionDialogFor = it }
                        ) 
                    }
                    Divider(modifier = Modifier.fillMaxHeight().width(1.dp), color = ZokasSurfaceVariant)
                    Box(modifier = Modifier.weight(0.4f).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                        CartList(cart = cart, totalPrice = totalPrice, onAdd = { viewModel.addToCart(it) }, onRemove = { viewModel.removeFromCart(it) }, onDelete = { viewModel.deleteItem(it) }, onCheckout = { showPaymentSheet = true })
                    }
                }
            } else {
                if (showCartOnPhone) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TextButton(onClick = { showCartOnPhone = false }, modifier = Modifier.padding(8.dp)) { Text("< Kembali ke Katalog", color = ZokasOrange) }
                        CartList(cart = cart, totalPrice = totalPrice, onAdd = { viewModel.addToCart(it) }, onRemove = { viewModel.removeFromCart(it) }, onDelete = { viewModel.deleteItem(it) }, onCheckout = { showPaymentSheet = true })
                    }
                } else {
                    ProductCatalog(
                        products = products, 
                        onProductClick = { viewModel.addToCart(it) },
                        onProductLongClick = { showActionDialogFor = it }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductCatalog(
    products: List<ProductEntity>, 
    onProductClick: (ProductEntity) -> Unit,
    onProductLongClick: (ProductEntity) -> Unit
) {
    if (products.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Belum ada produk.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(32.dp), color = ZokasTextWhite)
        }
    } else {
        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 160.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(products) { product ->
                // KARTU PRODUK 3D DENGAN GRADASI HIJAU
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(10.dp, RoundedCornerShape(20.dp), spotColor = Color.Black)
                        .combinedClickable(
                            onClick = { onProductClick(product) },
                            onLongClick = { onProductLongClick(product) }
                        ), 
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ) 
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(ZokasGreenLight, ZokasGreenDark)
                                )
                            )
                            .padding(16.dp), 
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = product.name, fontWeight = FontWeight.Bold, maxLines = 2, style = MaterialTheme.typography.titleMedium, color = ZokasTextWhite)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = formatRupiah(product.price), style = MaterialTheme.typography.titleLarge, color = ZokasOrange, fontWeight = FontWeight.Bold)
                        Text(text = "Stok: ${product.stock}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = ZokasTextWhite)
                    }
                }
            }
        }
    }
}

// --- FUNGSI BAWAH INI TIDAK BERUBAH DARI SEBELUMNYA, HANYA MENAMBAHKAN WARNA ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormBottomSheet(
    productToEdit: ProductEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, Long, Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf(productToEdit?.name ?: "") }
    var price by remember { mutableStateOf(productToEdit?.price?.toString() ?: "") }
    var stock by remember { mutableStateOf(productToEdit?.stock?.toString() ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = ZokasSurface) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (productToEdit == null) "Tambah Produk Baru" else "Edit Produk", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ZokasTextWhite)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Produk") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = price, onValueChange = { if (it.length <= 10) price = it }, label = { Text("Harga Jual (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = stock, onValueChange = { if (it.length <= 5) stock = it }, label = { Text("Stok") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { 
                    val p = price.toLongOrNull() ?: 0L
                    val s = stock.toIntOrNull() ?: 0
                    if (name.isNotBlank() && p > 0) onSave(name, p, s) 
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = name.isNotBlank() && (price.toLongOrNull() ?: 0L) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)
            ) {
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = ZokasSurface) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Pembayaran Tunai", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ZokasTextWhite)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Total Tagihan:", style = MaterialTheme.typography.titleMedium, color = ZokasTextWhite)
            Text(formatRupiah(totalPrice), style = MaterialTheme.typography.headlineMedium, color = ZokasOrange, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(value = cashInput, onValueChange = { if (it.length <= 10) cashInput = it }, label = { Text("Uang Diterima (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Kembalian:", style = MaterialTheme.typography.titleMedium, color = ZokasTextWhite)
                Text(formatRupiah(change), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (change > 0) Color(0xFF4CAF50) else ZokasTextWhite)
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { onConfirmPayment(cashReceived) }, 
                modifier = Modifier.fillMaxWidth().height(56.dp), 
                enabled = cashReceived >= totalPrice && totalPrice > 0,
                colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)
            ) {
                Text("SELESAIKAN PEMBAYARAN", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun CartList(cart: List<CartItem>, totalPrice: Long, onAdd: (ProductEntity) -> Unit, onRemove: (ProductEntity) -> Unit, onDelete: (ProductEntity) -> Unit, onCheckout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Keranjang Belanja", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ZokasTextWhite)
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cart) { item ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ZokasSurfaceVariant)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.product.name, fontWeight = FontWeight.Bold, color = ZokasTextWhite)
                            Text(formatRupiah(item.product.price), style = MaterialTheme.typography.bodyMedium, color = ZokasTextWhite)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onRemove(item.product) }) { Icon(Icons.Default.Remove, contentDescription = "Kurang", tint = ZokasTextWhite) }
                            Text(text = "${item.quantity}", modifier = Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold, color = ZokasTextWhite)
                            IconButton(onClick = { onAdd(item.product) }) { Icon(Icons.Default.Add, contentDescription = "Tambah", tint = ZokasTextWhite) }
                            IconButton(onClick = { onDelete(item.product) }) { Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red) }
                        }
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = ZokasSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total:", style = MaterialTheme.typography.titleLarge, color = ZokasTextWhite)
            Text(formatRupiah(totalPrice), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ZokasOrange)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onCheckout, 
            modifier = Modifier.fillMaxWidth().height(56.dp), 
            enabled = cart.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)
        ) {
            Text("PROSES PEMBAYARAN", fontSize = 16.sp)
        }
    }
}