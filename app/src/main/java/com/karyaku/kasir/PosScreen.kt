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
    
    // State untuk Buka Kas
    var showOpenShiftDialog by remember { mutableStateOf(false) }
    var modalAwal by remember { mutableStateOf("") }

    // Jika belum ada shift aktif, paksa buka kas
    LaunchedEffect(activeShift) {
        if (activeShift == null) {
            showOpenShiftDialog = true
        }
    }

    // Dialog Buka Kas
    if (showOpenShiftDialog) {
        AlertDialog(
            onDismissRequest = { /* Tidak bisa ditutup paksa */ },
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

    // ... (Dialog Pembayaran, Edit Produk, Hapus Produk, Struk - Biarkan Sama Seperti Sebelumnya) ...

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
                    // TOMBOL LOGOUT UNTUK KASIR & OWNER
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer, titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer)
            )
        },
        floatingActionButton = {
            // FAB DIBERI EFEK 3D DAN WARNA ORANGE
            FloatingActionButton(
                onClick = { productToEdit = null; showProductForm = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White,
                modifier = Modifier.shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Color.Black)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Produk Baru")
            }
        },
        // ... (BottomBar dll)
    ) { paddingValues ->
        // ... (Logika Katalog & Keranjang HP/Tablet - Biarkan Sama) ...
        // Ubah desain Card di ProductCatalog agar 3D (lihat fungsi ProductCatalog di bawah)
    }
}

// --- CONTOH DESAIN CARD 3D UNTUK KATALOG PRODUK ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductCatalog(
    products: List<ProductEntity>, 
    onProductClick: (ProductEntity) -> Unit,
    onProductLongClick: (ProductEntity) -> Unit
) {
    if (products.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Belum ada produk.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(32.dp))
        }
    } else {
        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 140.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(products) { product ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(6.dp, RoundedCornerShape(16.dp), spotColor = Color.Gray) // EFEK BAYANGAN 3D
                        .combinedClickable(
                            onClick = { onProductClick(product) },
                            onLongClick = { onProductLongClick(product) }
                        ), 
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if(product.stock > 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer
                    ), 
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(text = product.name, fontWeight = FontWeight.Bold, maxLines = 2, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = formatRupiah(product.price), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                        Text(text = "Stok: ${product.stock}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}