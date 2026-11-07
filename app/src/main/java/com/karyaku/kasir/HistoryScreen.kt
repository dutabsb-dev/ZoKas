package com.karyaku.kasir

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.TransactionEntity
import com.karyaku.kasir.data.local.UserEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    transactions: List<TransactionEntity>,
    isPro: Boolean,
    viewModel: PosViewModel,
    settingsManager: SettingsManager,
    currentUser: UserEntity
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    val bestSellers by viewModel.bestSellers.collectAsStateWithLifecycle()
    val lowStockProducts = viewModel.getLowStockProducts()

    var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var dateFilter by remember { mutableStateOf("HARI_INI") }

    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val startOfMonth = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val filteredTransactions = transactions.filter { tx ->
        when (dateFilter) {
            "HARI_INI" -> tx.timestamp >= startOfToday
            "BULAN_INI" -> tx.timestamp >= startOfMonth
            "7_HARI" -> tx.timestamp >= (startOfToday - (7 * 24 * 60 * 60 * 1000L))
            "30_HARI" -> tx.timestamp >= (startOfToday - (30 * 24 * 60 * 60 * 1000L))
            else -> true
        }
    }

    val validTransactions = filteredTransactions.filter { it.status == "SUCCESS" }
    val totalOmzet = validTransactions.sumOf { it.totalPrice }
    val totalHpp = validTransactions.sumOf { it.totalCost }
    val labaKotor = totalOmzet - totalHpp

    if (selectedTransaction != null) {
        TransactionDetailDialog(
            transaction = selectedTransaction!!,
            viewModel = viewModel,
            settingsManager = settingsManager,
            currentUser = currentUser,
            onDismiss = { selectedTransaction = null }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val filters = listOf("HARI_INI" to "Hari Ini", "BULAN_INI" to "Bulan Ini", "7_HARI" to "7 Hari", "30_HARI" to "30 Hari", "SEMUA" to "Semua")
                items(filters.size) { index ->
                    val (key, label) = filters[index]
                    FilterChip(
                        selected = dateFilter == key, onClick = { dateFilter = key }, label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZokasGreen, selectedLabelColor = Color.White)
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(containerColor = ZokasGreen)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Omzet", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    Text(text = formatRupiah(totalOmzet), color = ZokasOrange, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${validTransactions.size} Transaksi Sukses", color = Color.White, fontSize = 12.sp)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.2f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Laba Kotor:", color = Color.White, fontSize = 14.sp)
                        Text(formatRupiah(labaKotor), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Modal (HPP):", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Text(formatRupiah(totalHpp), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
        }

        // GRAFIK PENJUALAN 7 HARI TERAKHIR
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Grafik 7 Hari Terakhir", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                        val days = 7
                        val barWidth = size.width / (days * 2)
                        val dailySales = (0 until days).map { i ->
                            val start = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, -i); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                            }.timeInMillis
                            val end = start + 86400000L
                            transactions.filter { it.timestamp in start..end && it.status == "SUCCESS" }.sumOf { it.totalPrice }
                        }.reversed()
                        
                        val maxSale = dailySales.maxOrNull() ?: 1L
                        if (maxSale == 0L) return@Canvas
                        dailySales.forEachIndexed { index, sale ->
                            val barHeight = (sale.toFloat() / maxSale.toFloat()) * size.height
                            val x = (index * 2.5f * barWidth) + barWidth
                            drawRect(color = if (sale > 0) ZokasGreen else Color.LightGray, topLeft = Offset(x, size.height - barHeight), size = Size(barWidth, barHeight))
                        }
                    }
                }
            }
        }

        if (lowStockProducts.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Peringatan Stok Menipis!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Ada ${lowStockProducts.size} produk dengan stok <= 5. Segera lakukan restock.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }

        if (bestSellers.isNotEmpty()) {
            item { Text("🏆 Top 5 Produk Terlaris", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        bestSellers.forEachIndexed { index, item ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${index + 1}. ${item.productName}", fontWeight = FontWeight.Medium)
                                Text("${item.totalQty} terjual", color = ZokasOrange, fontWeight = FontWeight.Bold)
                            }
                            if (index < bestSellers.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        }
                    }
                }
            }
        }

        if (isPro && filteredTransactions.isNotEmpty()) {
            item {
                Button(
                    onClick = {
                        try {
                            val csvHeader = "ID,Tanggal,Total Belanja,Uang Tunai,Kembalian,Pelanggan,Status\n"
                            val csvData = filteredTransactions.joinToString("\n") { tx ->
                                "${tx.id},${dateFormat.format(Date(tx.timestamp))},${tx.totalPrice},${tx.cashReceived},${tx.changeAmount},${tx.customerName},${tx.status}"
                            }
                            val fullCsv = csvHeader + csvData
                            val sendIntent = Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, fullCsv); type = "text/plain" }
                            val shareIntent = Intent.createChooser(sendIntent, "Ekspor Laporan (CSV)")
                            if (activity != null) activity.startActivity(shareIntent) else context.startActivity(shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) { Toast.makeText(context, "Gagal ekspor: ${e.message}", Toast.LENGTH_LONG).show() }
                    },
                    modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Ekspor"); Spacer(modifier = Modifier.width(8.dp)); Text("Ekspor Laporan ke Excel/CSV")
                }
            }
        }

        item { Text("Riwayat Transaksi", fontWeight = FontWeight.Bold, fontSize = 16.sp) }

        if (filteredTransactions.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("Tidak ada transaksi pada periode ini.") } }
        } else {
            items(filteredTransactions) { tx ->
                val isVoided = tx.status == "VOIDED"
                val isUnpaid = tx.status == "UNPAID"
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { selectedTransaction = tx },
                    colors = CardDefaults.cardColors(containerColor = if (isVoided) Color(0xFFFFCDD2) else if (isUnpaid) Color(0xFFFFF9C4) else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Struk #${tx.id}", fontWeight = FontWeight.Bold, textDecoration = if (isVoided) TextDecoration.LineThrough else TextDecoration.None)
                            Text(dateFormat.format(Date(tx.timestamp)))
                        }
                        Text("Pelanggan: ${tx.customerName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Total: ${formatRupiah(tx.totalPrice)}", fontWeight = FontWeight.Bold, color = if (isVoided) Color.Gray else ZokasOrange, textDecoration = if (isVoided) TextDecoration.LineThrough else TextDecoration.None)
                            if (isVoided) Text("VOID", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            if (isUnpaid) Text("BON", color = ZokasOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionDetailDialog(
    transaction: TransactionEntity,
    viewModel: PosViewModel,
    settingsManager: SettingsManager,
    currentUser: UserEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    
    val itemsFlow = remember(transaction.id) { viewModel.getTransactionItems(transaction.id) }
    val items by itemsFlow.collectAsStateWithLifecycle()
    
    var showReceipt by remember { mutableStateOf(false) }
    var showVoidPinDialog by remember { mutableStateOf(false) }
    var showRefundPinDialog by remember { mutableStateOf(false) }
    var showPayBonDialog by remember { mutableStateOf(false) }

    if (showReceipt) {
        ReceiptDialog(
            transaction = transaction,
            storeName = if (settingsManager.isPro) settingsManager.storeName else "ZoKas (Gratis)",
            storeAddress = if (settingsManager.isPro) settingsManager.storeAddress else "Alamat belum diatur",
            storePhone = if (settingsManager.isPro) settingsManager.storePhone else "-",
            storeFooter = if (settingsManager.isPro) settingsManager.storeFooter else "Terima kasih!",
            settingsManager = settingsManager,
            items = items,
            onDismiss = { showReceipt = false }
        )
    }

    if (showVoidPinDialog) {
        VoidPinDialog(
            onDismiss = { showVoidPinDialog = false },
            onConfirm = { pin ->
                viewModel.voidTransaction(transaction, pin, currentUser) { success: Boolean, message: String ->
                    showVoidPinDialog = false
                    if (success) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (showRefundPinDialog) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRefundPinDialog = false },
            title = { Text("Otorisasi Refund") },
            text = {
                Column {
                    Text("Masukkan PIN Supervisor/Owner:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("PIN") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = {
                Button(onClick = { 
                    viewModel.refundTransaction(transaction, pin, currentUser) { success: Boolean, msg: String ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) onDismiss()
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("REFUND") }
            },
            dismissButton = { TextButton(onClick = { showRefundPinDialog = false }) { Text("Batal") } }
        )
    }

    if (showPayBonDialog) {
        var cash by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPayBonDialog = false },
            title = { Text("Pelunasan Bon") },
            text = {
                Column {
                    Text("Total Tagihan: ${formatRupiah(transaction.totalPrice)}")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = cash, onValueChange = { cash = it }, label = { Text("Uang Diterima") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = {
                Button(onClick = { 
                    val c = cash.toLongOrNull() ?: 0L
                    if (c >= transaction.totalPrice) {
                        viewModel.payUnpaidTransaction(transaction, c) {
                            Toast.makeText(context, "Bon berhasil dilunasi!", Toast.LENGTH_SHORT).show()
                            showPayBonDialog = false
                            onDismiss()
                        }
                    } else {
                        Toast.makeText(context, "Uang kurang!", Toast.LENGTH_SHORT).show()
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)) { Text("BAYAR") }
            },
            dismissButton = { TextButton(onClick = { showPayBonDialog = false }) { Text("Batal") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (transaction.status == "SUCCESS" && currentUser.role == "OWNER") {
                    Button(
                        onClick = { showRefundPinDialog = true }, 
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("REFUND")
                    }
                }
                
                if (transaction.status == "UNPAID") {
                    Button(
                        onClick = { showPayBonDialog = true }, 
                        colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)
                    ) {
                        Text("BAYAR BON")
                    }
                }
                
                Button(onClick = { showReceipt = true }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CETAK")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        title = { Text("Rincian Transaksi #${transaction.id}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (transaction.status == "VOIDED") {
                    Text("TRANSAKSI INI TELAH DIBATALKAN (VOID)", color = Color.Red, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (transaction.status == "UNPAID") {
                    Text("TRANSAKSI INI BELUM DIBAYAR (BON)", color = ZokasOrange, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text("Tanggal: ${dateFormat.format(Date(transaction.timestamp))}", fontSize = 12.sp)
                Text("Pelanggan: ${transaction.customerName}", fontSize = 12.sp)
                Text("Kasir: ${transaction.cashierName} | ${transaction.tableName}", fontSize = 12.sp)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                if (items.isEmpty()) {
                    Text("Memuat rincian...", fontSize = 12.sp, color = Color.Gray)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                        items(items) { item ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${item.quantity} x ${formatRupiah(item.price)}", fontSize = 12.sp, color = Color.Gray)
                                }
                                Text(formatRupiah(item.subtotal), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total:", fontWeight = FontWeight.Bold)
                    Text(formatRupiah(transaction.totalPrice), fontWeight = FontWeight.Bold, color = if (transaction.status == "VOIDED") Color.Gray else ZokasOrange)
                }
            }
        }
    )
}

@Composable
fun VoidPinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Otorisasi Supervisor") },
        text = {
            Column {
                Text("Masukkan PIN Supervisor/Owner untuk membatalkan transaksi ini:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6) pin = it },
                    label = { Text("PIN Supervisor") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(pin) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                Text("BATALKAN TRANSAKSI")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}