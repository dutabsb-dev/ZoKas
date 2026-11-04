package com.karyaku.kasir

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.TransactionEntity
import java.text.NumberFormat
import java.util.Locale

fun formatRupiah(amount: Long): String {
    val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    return format.format(amount).split(",")[0].replace("Rp", "Rp ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(viewModel: PosViewModel, settingsManager: SettingsManager) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val totalPrice = cart.sumOf { it.product.price * it.quantity }
    
    var showCartOnPhone by remember { mutableStateOf(false) }
    var showPaymentSheet by remember { mutableStateOf(false) }
    var showAddProductSheet by remember { mutableStateOf(false) } // State untuk form tambah produk
    
    var completedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

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
                viewModel.processPayment(cashReceived) { newId ->
                    showPaymentSheet = false
                    showCartOnPhone = false
                    completedTransaction = TransactionEntity(id = newId, totalPrice = totalPrice, cashReceived = cashReceived, changeAmount = cashReceived - totalPrice)
                }
            }
        )
    }

    // Memanggil Form Tambah Produk
    if (showAddProductSheet) {
        AddProductBottomSheet(
            onDismiss = { showAddProductSheet = false },
            onSave = { name, price, stock ->
                viewModel.addProduct(name, price, stock)
                showAddProductSheet = false
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
                        Text(text = if (settingsManager.isPro) settingsManager.storeName else "ZoKas", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer, titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer)
            )
        },
        floatingActionButton = {
            // Tombol ini sekarang membuka form input produk asli
            FloatingActionButton(onClick = { showAddProductSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Produk Baru")
            }
        },
        bottomBar = {
            if (cart.isNotEmpty() && !showCartOnPhone) {
                BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.height(72.dp)) {
                    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Total (${cart.sumOf { it.quantity }} item)", fontSize = 12.sp)
                            Text(formatRupiah(totalPrice), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Button(onClick = { showCartOnPhone = true }) { Text("Lihat Keranjang") }
                    }
                }
            }
        }
    ) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            val isTablet = maxWidth > 600.dp

            if (isTablet) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(0.6f).fillMaxHeight()) { ProductCatalog(products = products, onProductClick = { viewModel.addToCart(it) }) }
                    Divider(modifier = Modifier.fillMaxHeight().width(1.dp))
                    Box(modifier = Modifier.weight(0.4f).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                        CartList(cart = cart, totalPrice = totalPrice, onAdd = { viewModel.addToCart(it) }, onRemove = { viewModel.removeFromCart(it) }, onDelete = { viewModel.deleteItem(it) }, onCheckout = { showPaymentSheet = true })
                    }
                }
            } else {
                if (showCartOnPhone) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TextButton(onClick = { showCartOnPhone = false }, modifier = Modifier.padding(8.dp)) { Text("< Kembali ke Katalog") }
                        CartList(cart = cart, totalPrice = totalPrice, onAdd = { viewModel.addToCart(it) }, onRemove = { viewModel.removeFromCart(it) }, onDelete = { viewModel.deleteItem(it) }, onCheckout = { showPaymentSheet = true })
                    }
                } else {
                    ProductCatalog(products = products, onProductClick = { viewModel.addToCart(it) })
                }
            }
        }
    }
}

// --- KOMPONEN FORM TAMBAH PRODUK BARU ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductBottomSheet(
    onDismiss: () -> Unit,
    onSave: (String, Long, Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Tambah Produk Baru", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Produk") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = price, onValueChange = { if (it.length <= 10) price = it },
                label = { Text("Harga Jual (Rp)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = stock, onValueChange = { if (it.length <= 5) stock = it },
                label = { Text("Stok Awal") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { 
                    val p = price.toLongOrNull() ?: 0L
                    val s = stock.toIntOrNull() ?: 0
                    if (name.isNotBlank() && p > 0) onSave(name, p, s) 
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = name.isNotBlank() && (price.toLongOrNull() ?: 0L) > 0
            ) {
                Text("SIMPAN PRODUK", fontSize = 16.sp)
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
            Text(formatRupiah(totalPrice), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(value = cashInput, onValueChange = { if (it.length <= 10) cashInput = it }, label = { Text("Uang Diterima (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Kembalian:", style = MaterialTheme.typography.titleMedium)
                Text(formatRupiah(change), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (change > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { onConfirmPayment(cashReceived) }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = cashReceived >= totalPrice && totalPrice > 0) {
                Text("SELESAIKAN PEMBAYARAN", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ProductCatalog(products: List<com.karyaku.kasir.data.local.ProductEntity>, onProductClick: (com.karyaku.kasir.data.local.ProductEntity) -> Unit) {
    if (products.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Belum ada produk. Tekan tombol + di pojok kanan bawah untuk menambah produk baru.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(32.dp))
        }
    } else {
        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 140.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(products) { product ->
                Card(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onProductClick(product) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(text = product.name, fontWeight = FontWeight.Bold, maxLines = 2, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = formatRupiah(product.price), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(text = "Stok: ${product.stock}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun CartList(cart: List<CartItem>, totalPrice: Long, onAdd: (com.karyaku.kasir.data.local.ProductEntity) -> Unit, onRemove: (com.karyaku.kasir.data.local.ProductEntity) -> Unit, onDelete: (com.karyaku.kasir.data.local.ProductEntity) -> Unit, onCheckout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Keranjang Belanja", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cart) { item ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total:", style = MaterialTheme.typography.titleLarge)
            Text(formatRupiah(totalPrice), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onCheckout, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = cart.isNotEmpty()) {
            Text("PROSES PEMBAYARAN", fontSize = 16.sp)
        }
    }
}