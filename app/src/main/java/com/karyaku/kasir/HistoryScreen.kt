package com.karyaku.kasir

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    val bestSellers by viewModel.bestSellers.collectAsStateWithLifecycle()
    val inventoryValue by viewModel.inventoryValue.collectAsStateWithLifecycle()
    val netProfit by viewModel.netProfit.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()

    val lowStockProducts = remember(products) {
        products.filter { it.stock <= 5 && it.stock >= 0 }
    }

    // FITUR BARU: Deteksi Produk Expired (< 30 hari)
    val expiringProducts = remember(products) {
        val thirtyDaysFromNow = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
        products.filter { it.expiryDate > 0 && it.expiryDate <= thirtyDaysFromNow }
    }

    var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var dateFilter by remember { mutableStateOf("HARI_INI") }

    var transactionsToExport by remember { mutableStateOf<List<TransactionEntity>>(emptyList()) }
    
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch { exportTransactionsToPdfDetailed(context, uri, transactionsToExport, dateFormat, viewModel, settingsManager) }
        }
    }

    val startOfToday = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val startOfMonth = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

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
        TransactionDetailDialog(transaction = selectedTransaction!!, viewModel = viewModel, settingsManager = settingsManager, currentUser = currentUser, onDismiss = { selectedTransaction = null })
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val filters = listOf("HARI_INI" to "Hari Ini", "BULAN_INI" to "Bulan Ini", "7_HARI" to "7 Hari", "30_HARI" to "30 Hari", "SEMUA" to "Semua")
                items(filters.size) { index ->
                    val (key, label) = filters[index]
                    FilterChip(selected = dateFilter == key, onClick = { dateFilter = key }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZokasGreen, selectedLabelColor = Color.White))
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

                    val totalCash = validTransactions.filter { it.paymentMethod == "CASH" }.sumOf { it.totalPrice }
                    val totalBon = validTransactions.filter { it.paymentMethod == "BON" }.sumOf { it.totalPrice }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pembayaran Tunai:", color = Color.White, fontSize = 12.sp); Text(formatRupiah(totalCash), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pembayaran BON:", color = Color.White, fontSize = 12.sp); Text(formatRupiah(totalBon), color = Color(0xFFFFCDD2), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.2f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Laba Kotor:", color = Color.White, fontSize = 14.sp); Text(formatRupiah(labaKotor), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Laba Bersih:", color = Color.White, fontSize = 14.sp); Text(formatRupiah(netProfit), color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Modal (HPP):", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp); Text(formatRupiah(totalHpp), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Nilai Aset Gudang (HPP)", fontWeight = FontWeight.Bold)
                        Text("Total nilai stok barang saat ini", fontSize = 10.sp, color = Color.Gray)
                    }
                    Text(formatRupiah(inventoryValue), fontWeight = FontWeight.Bold, color = ZokasGreen)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Grafik 7 Hari Terakhir", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                        val days = 7
                        val paddingLeft = 30f
                        val paddingBottom = 40f
                        val chartWidth = size.width - paddingLeft
                        val chartHeight = size.height - paddingBottom
                        
                        val slotWidth = chartWidth / days
                        val barWidth = slotWidth * 0.6f

                        val dailySales = (0 until days).map { i ->
                            val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
                            val end = start + 86400000L
                            transactions.filter { it.timestamp in start..end && it.status == "SUCCESS" }.sumOf { it.totalPrice }
                        }.reversed()

                        val maxSale = dailySales.maxOrNull() ?: 0L
                        val effectiveMax = if (maxSale == 0L) 1L else maxSale

                        val axisColor = Color.LightGray
                        val strokeWidth = 2f
                        drawLine(color = axisColor, start = Offset(paddingLeft, 0f), end = Offset(paddingLeft, chartHeight), strokeWidth = strokeWidth)
                        drawLine(color = axisColor, start = Offset(paddingLeft, chartHeight), end = Offset(size.width, chartHeight), strokeWidth = strokeWidth)

                        val textPaint = android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 24f; textAlign = android.graphics.Paint.Align.CENTER }
                        val dateFormatShort = SimpleDateFormat("dd/MM", Locale.getDefault())

                        dailySales.forEachIndexed { index, sale ->
                            val x = paddingLeft + index * slotWidth + (slotWidth - barWidth) / 2f
                            val barHeight = (sale.toFloat() / effectiveMax) * chartHeight
                            drawRect(color = ZokasGreen, topLeft = Offset(x, chartHeight - barHeight), size = Size(barWidth, barHeight))
                            val dateStr = dateFormatShort.format(Date(System.currentTimeMillis() - (days - 1 - index) * 86400000L))
                            drawContext.canvas.nativeCanvas.drawText(dateStr, x + (barWidth / 2), size.height - 10f, textPaint)
                        }
                    }
                }
            }
        }

        // LAPORAN BARANG EXPIRED
        if (expiringProducts.isNotEmpty()) {
            item { Text("⚠️ Barang Mendekati Expired", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Red) }
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        expiringProducts.forEachIndexed { index, product ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(product.name, fontWeight = FontWeight.Bold, color = Color.Black)
                                Text(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(product.expiryDate)), color = Color.Red, fontWeight = FontWeight.Bold)
                            }
                            if (index < expiringProducts.size - 1) HorizontalDivider(color = Color.Red.copy(alpha = 0.2f))
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
                            Text("Ada ${lowStockProducts.size} produk dengan stok <= 5.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }

        // KINERJA KASIR & KOMISI
        item { Text("👨‍💼 Kinerja & Komisi Kasir (All-Time)", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val validUsers = users.filter { it.role != "OWNER" }
                    if (validUsers.isEmpty()) {
                        Text("Belum ada data kasir.", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        validUsers.forEachIndexed { index, user ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(user.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    val userTxCount = validTransactions.count { it.cashierName == user.name }
                                    Text("$userTxCount Transaksi (Periode Ini)", fontSize = 11.sp, color = Color.Gray)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Total Komisi", fontSize = 10.sp, color = Color.Gray)
                                    Text(formatRupiah(user.totalCommission), fontWeight = FontWeight.ExtraBold, color = ZokasGreen)
                                }
                            }
                            if (index < validUsers.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        }
                    }
                }
            }
        }

        if (isPro && filteredTransactions.isNotEmpty()) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            try {
                                val csvHeader = "ID,Tanggal,Total Belanja,Uang Tunai,Kembalian,Pelanggan,Status,Komisi\n"
                                val csvData = filteredTransactions.joinToString("\n") { tx ->
                                    "${tx.id},${dateFormat.format(Date(tx.timestamp))},${tx.totalPrice},${tx.cashReceived},${tx.changeAmount},${tx.customerName},${tx.status},${tx.totalCommission}"
                                }
                                val fullCsv = csvHeader + csvData
                                val sendIntent = Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, fullCsv); type = "text/plain" }
                                val shareIntent = Intent.createChooser(sendIntent, "Ekspor Laporan (CSV)")
                                if (activity != null) activity.startActivity(shareIntent) else context.startActivity(shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (e: Exception) { Toast.makeText(context, "Gagal ekspor: ${e.message}", Toast.LENGTH_LONG).show() }
                        },
                        modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)
                    ) { Text("CSV") }
                    Button(
                        onClick = { transactionsToExport = filteredTransactions; createPdfLauncher.launch("Zokas_Laporan_${System.currentTimeMillis()}.pdf") },
                        modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("PDF") }
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
                        Text("Pelanggan: ${tx.customerName} | Kasir: ${tx.cashierName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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