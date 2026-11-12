package com.karyaku.kasir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.StockOpnameEntity
import com.karyaku.kasir.data.local.StockOpnameItemEntity
import com.karyaku.kasir.data.local.UserEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- 1. DIALOG TAMBAH STOK SATUAN (RESTOCK) ---
@Composable
fun StockOpnameDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var qty by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Stok: ${product.name}") },
        text = {
            Column {
                Text("Stok saat ini: ${product.stock}", color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = qty,
                    onValueChange = { if (it.length <= 5) qty = it },
                    label = { Text("Jumlah Barang Masuk") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    val q = qty.toIntOrNull() ?: 0
                    if (q > 0) onSave(q) 
                },
                enabled = (qty.toIntOrNull() ?: 0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(12.dp)
            ) { Text("TAMBAH STOK", color = androidx.compose.ui.graphics.Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// --- 2. DIALOG AUDIT STOK MASSAL (OPNAME) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkStockOpnameDialog(
    products: List<ProductEntity>,
    currentUser: UserEntity,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    var showHistory by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Map untuk menyimpan input stok fisik (ProductId -> String Input)
    val physicalStocks = remember { mutableStateMapOf<Long, String>() }

    val filteredProducts = products.filter { 
        it.name.contains(searchQuery, ignoreCase = true) && !it.isService 
    }

    if (showHistory) {
        val history by viewModel.stockOpnames.collectAsStateWithLifecycle()
        StockOpnameHistoryDialog(
            history = history,
            onDismiss = { showHistory = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                // --- TOP BAR ---
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Audit Stok (Opname)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Masukkan jumlah fisik barang yang ada.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        IconButton(onClick = { showHistory = true }) {
                            Icon(Icons.Default.History, contentDescription = "Riwayat", tint = EmeraldGreen)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                }

                // --- SEARCH BAR ---
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari produk...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = EmeraldGreen) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // --- LIST PRODUK ---
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        val inputVal = physicalStocks[product.id] ?: ""
                        val physical = inputVal.toIntOrNull()
                        val diff = if (physical != null) physical - product.stock else 0
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(product.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Sistem: ${product.stock} Pcs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    
                                    if (physical != null && diff != 0) {
                                        val diffColor = if (diff < 0) ErrorRed else EmeraldGreen
                                        val diffText = if (diff > 0) "+$diff" else "$diff"
                                        Text("Selisih: $diffText", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = diffColor)
                                    }
                                }
                                
                                OutlinedTextField(
                                    value = inputVal,
                                    onValueChange = { 
                                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                            physicalStocks[product.id] = it 
                                        }
                                    },
                                    label = { Text("Fisik", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(80.dp),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldGreen,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }
                    }
                }

                // --- BOTTOM BAR (SAVE) ---
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val itemsToSave = filteredProducts.mapNotNull { product ->
                            val input = physicalStocks[product.id]
                            val physical = input?.toIntOrNull()
                            if (physical != null && physical != product.stock) {
                                StockOpnameItemEntity(
                                    opnameId = 0,
                                    productId = product.id,
                                    productName = product.name,
                                    systemStock = product.stock,
                                    physicalStock = physical,
                                    difference = physical - product.stock
                                )
                            } else null
                        }

                        val totalItems = itemsToSave.size
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Item diubah: $totalItems", fontWeight = FontWeight.Bold)
                            Button(
                                onClick = {
                                    if (itemsToSave.isNotEmpty()) {
                                        viewModel.processStockOpname(itemsToSave, currentUser) {
                                            onDismiss()
                                        }
                                    }
                                },
                                enabled = itemsToSave.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SIMPAN AUDIT")
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 3. DIALOG RIWAYAT AUDIT STOK ---
@Composable
fun StockOpnameHistoryDialog(
    history: List<StockOpnameEntity>,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Riwayat Audit Stok") },
        text = {
            if (history.isEmpty()) {
                Text("Belum ada riwayat audit.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(item.userName, fontWeight = FontWeight.Bold)
                                    Text(dateFormat.format(Date(item.timestamp)), fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Item diaudit: ${item.totalItemsChecked}", fontSize = 12.sp)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Rugi: ${formatRupiah(item.totalLossValue)}", fontSize = 12.sp, color = ErrorRed)
                                    Text("Untung: ${formatRupiah(item.totalGainValue)}", fontSize = 12.sp, color = EmeraldGreen)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}