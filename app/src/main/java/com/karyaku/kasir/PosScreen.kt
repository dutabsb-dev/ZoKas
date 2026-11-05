package com.karyaku.kasir

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Wallet // IMPORT IKON DOMPET
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
    var showCloseShiftDialog by remember { mutableStateOf(false) }
    var showCashFlowDialog by remember { mutableStateOf(false) } // State untuk Kas Keluar/Masuk
    var modalAwal by remember { mutableStateOf("") }

    LaunchedEffect(activeShift) {
        if (activeShift == null) showOpenShiftDialog = true
    }

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
                Button(onClick = {
                    viewModel.openShift(currentUser, modalAwal.toLongOrNull() ?: 0L)
                    showOpenShiftDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("BUKA KASIR") }
            }
        )
    }

    if (showCloseShiftDialog) {
        CloseShiftDialog(
            onDismiss = { showCloseShiftDialog = false },
            onConfirm = { uangFisik ->
                viewModel.closeShift(currentUser, uangFisik) { selisih ->
                    showCloseShiftDialog = false
                    onLogout()
                }
            }
        )
    }

    // DIALOG KAS KELUAR / MASUK
    if (showCashFlowDialog) {
        CashFlowDialog(
            onDismiss = { showCashFlowDialog = false },
            onSave = { type, amount, note ->
                viewModel.addCashFlow(type, amount, note, currentUser)
                showCashFlowDialog = false
            }
        )
    }

    if (completedTransaction != null) {
        ReceiptDialog(
            transaction = completedTransaction!!,
            storeName = if (settingsManager.isPro) settingsManager.storeName else "ZoKas (Gratis)",
            storeFooter = if (settingsManager.isPro) settingsManager.storeFooter else "Upgrade ke Pro!",
            settingsManager = settingsManager,
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
                if (productToEdit == null) viewModel.addProduct(name, price, stock, currentUser.id, currentUser.name)
                else viewModel.updateProduct(productToEdit!!.copy(name = name, price = price, stock = stock), currentUser.id, currentUser.name)
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
                TextButton(onClick = { productToEdit = showActionDialogFor; showProductForm = true; showActionDialogFor = null }) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = ZokasGreen); Spacer(modifier = Modifier.width(4.dp)); Text("Edit", color = ZokasGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = showActionDialogFor; showActionDialogFor = null }) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red); Spacer(modifier = Modifier.width(4.dp)); Text("Hapus", color = Color.Red)
                }
            }
        )
    }

    if (productToDelete != null) {
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Konfirmasi Hapus") },
            text = { Text("Apakah Anda yakin ingin menghapus '${productToDelete!!.name}'?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteProduct(productToDelete!!, currentUser.id, currentUser.name); productToDelete = null }) { Text("Ya, Hapus", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { productToDelete = null }) { Text("Batal") } }
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
                    // TOMBOL KAS KELUAR / MASUK (DOMPET)
                    IconButton(onClick = { showCashFlowDialog = true }) {
                        Icon(Icons.Default.Wallet, contentDescription = "Kas Keluar/Masuk", tint = ZokasOrange)
                    }
                    IconButton(onClick = { showCloseShiftDialog = true }) {
                        Icon(Icons.Default.Lock, contentDescription = "Tutup Kasir", tint = ZokasOrange)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(12.dp, CircleShape, spotColor = ZokasOrangeDark)
                    .background(
                        brush = Brush.linearGradient(colors = listOf(ZokasOrange, ZokasOrangeDark)),
                        shape = CircleShape
                    )
                    .clickable { productToEdit = null; showProductForm = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah", tint = Color.White, modifier = Modifier.size(32.dp))
            }
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
                    Box(modifier = Modifier.weight(0.6f).fillMaxHeight()) { ProductCatalog(products, { viewModel.addToCart(it) }, { showActionDialogFor = it }) }
                    Divider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    Box(modifier = Modifier.weight(0.4f).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                        CartList(cart, totalPrice, { viewModel.addToCart(it) }, { viewModel.removeFromCart(it) }, { viewModel.deleteItem(it) }, { showPaymentSheet = true })
                    }
                }
            } else {
                if (showCartOnPhone) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TextButton(onClick = { showCartOnPhone = false }, modifier = Modifier.padding(8.dp)) { Text("< Kembali ke Katalog", color = ZokasOrange) }
                        CartList(cart, totalPrice, { viewModel.addToCart(it) }, { viewModel.removeFromCart(it) }, { viewModel.deleteItem(it) }, { showPaymentSheet = true })
                    }
                } else {
                    ProductCatalog(products, { viewModel.addToCart(it) }, { showActionDialogFor = it })
                }
            }
        }
    }
}

// DIALOG KAS KELUAR / MASUK
@Composable
fun CashFlowDialog(onDismiss: () -> Unit, onSave: (String, Long, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("OUT") } // Default Kas Keluar

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catat Kas Keluar / Masuk") },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(
                        onClick = { type = "OUT" },
                        colors = ButtonDefaults.buttonColors(containerColor = if (type == "OUT") ZokasOrange else Color.Gray)
                    ) { Text("Kas Keluar") }
                    Button(
                        onClick = { type = "IN" },
                        colors = ButtonDefaults.buttonColors(containerColor = if (type == "IN") ZokasGreen else Color.Gray)
                    ) { Text("Kas Masuk") }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.length <= 10) amount = it },
                    label = { Text("Nominal (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Keterangan (cth: Beli Galon)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toLongOrNull() ?: 0L
                    if (amt > 0 && note.isNotBlank()) onSave(type, amt, note)
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (type == "OUT") ZokasOrange else ZokasGreen)
            ) { Text("SIMPAN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// (Fungsi ProductCatalog, ProductFormBottomSheet, PaymentBottomSheet, CartList, CloseShiftDialog, 
//  dan ReceiptDialog tetap SAMA seperti kode sebelumnya. 
//  Pastikan Anda tidak menghapusnya dari file ini!)