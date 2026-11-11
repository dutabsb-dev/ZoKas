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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Money
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
    viewModel: PosViewModel,
    settingsManager: SettingsManager,
    currentUser: UserEntity
) {
    val context = LocalContext.current
    val activity = context.findActivity() // Memanggil fungsi dari Utils.kt
    val scope = rememberCoroutineScope()
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val lang = settingsManager.language

    val bestSellers by viewModel.bestSellers.collectAsStateWithLifecycle()
    val inventoryValue by viewModel.inventoryValue.collectAsStateWithLifecycle()
    val netProfit by viewModel.netProfit.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val cashFlows by viewModel.cashFlows.collectAsStateWithLifecycle()

    val lowStockProducts = remember(products) {
        products.filter { it.stock <= 5 && it.stock >= 0 }
    }

    val expiringProducts = remember(products) {
        val thirtyDaysFromNow = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
        products.filter { it.expiryDate > 0 && it.expiryDate <= thirtyDaysFromNow }
    }

    var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var dateFilter by remember { mutableStateOf("TODAY") }
    var showFinanceReport by remember { mutableStateOf(false) }

    var transactionsToExport by remember { mutableStateOf<List<TransactionEntity>>(emptyList()) }
    
    val createExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val success = ExcelExporter.exportTransactionsToExcel(context, uri, transactionsToExport)
                if (success) {
                    Toast.makeText(context, "Excel berhasil diekspor!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Gagal ekspor Excel.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                exportTransactionsToPdfDetailed(context, uri, transactionsToExport, dateFormat, viewModel, settingsManager)
            }
        }
    }

    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val startOfMonth = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val filteredTransactions = transactions.filter { tx ->
        when (dateFilter) {
            "TODAY" -> tx.timestamp >= startOfToday
            "THIS_MONTH" -> tx.timestamp >= startOfMonth
            "7_DAYS" -> tx.timestamp >= (startOfToday - (7 * 24 * 60 * 60 * 1000L))
            "30_DAYS" -> tx.timestamp >= (startOfToday - (30 * 24 * 60 * 60 * 1000L))
            else -> true
        }
    }
    
    val filteredCashFlows = cashFlows.filter { cf ->
        when (dateFilter) {
            "TODAY" -> cf.timestamp >= startOfToday
            "THIS_MONTH" -> cf.timestamp >= startOfMonth
            "7_DAYS" -> cf.timestamp >= (startOfToday - (7 * 24 * 60 * 60 * 1000L))
            "30_DAYS" -> cf.timestamp >= (startOfToday - (30 * 24 * 60 * 60 * 1000L))
            else -> true
        }
    }

    val validTransactions = filteredTransactions.filter { it.status == "SUCCESS" }
    val totalOmzet = validTransactions.sumOf { it.totalPrice }
    val totalHpp = validTransactions.sumOf { it.totalCost }
    val labaKotor = totalOmzet - totalHpp

    val totalCashIn = filteredCashFlows.filter { it.type == "IN" }.sumOf { it.amount }
    val totalCashOut = filteredCashFlows.filter { it.type == "OUT" }.sumOf { it.amount }
    val penjualanTunai = validTransactions.filter { it.paymentMethod == "CASH" }.sumOf { it.totalPrice }
    val penjualanNonTunai = validTransactions.filter { it.paymentMethod != "CASH" }.sumOf { it.totalPrice }
    val labaBersih = labaKotor - totalCashOut

    if (selectedTransaction != null) {
        TransactionDetailDialog(
            transaction = selectedTransaction!!,
            viewModel = viewModel,
            settingsManager = settingsManager,
            currentUser = currentUser,
            lang = lang,
            onDismiss = { selectedTransaction = null }
        )
    }

    if (showFinanceReport) {
        AlertDialog(
            onDismissRequest = { showFinanceReport = false },
            icon = { Icon(Icons.Default.Money, contentDescription = null, tint = EmeraldGreen) },
            title = { Text(LanguageManager.tr("financial_report", lang), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("${LanguageManager.tr("period", lang)}: ${dateFilter.replace("_", " ")}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SlateGrey)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text(LanguageManager.tr("revenue", lang), fontWeight = FontWeight.Bold, color = EmeraldGreen)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(LanguageManager.tr("cash_sales", lang)); Text(formatRupiah(penjualanTunai))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(LanguageManager.tr("non_cash_sales", lang)); Text(formatRupiah(penjualanNonTunai))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(LanguageManager.tr("cash_in_add", lang), fontSize = 12.sp); Text(formatRupiah(totalCashIn), fontSize = 12.sp)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(LanguageManager.tr("expenses", lang), fontWeight = FontWeight.Bold, color = ErrorRed)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(LanguageManager.tr("total_cogs_cost", lang)); Text(formatRupiah(totalHpp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(LanguageManager.tr("cash_out_op", lang), fontSize = 12.sp); Text(formatRupiah(totalCashOut), fontSize = 12.sp)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(LanguageManager.tr("net_profit", lang), fontWeight = FontWeight.Bold, color = AmberAccent)
                        Text(formatRupiah(labaBersih), fontWeight = FontWeight.Bold, color = AmberAccent)
                    }
                }
            },
            confirmButton = { Button(onClick = { showFinanceReport = false }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)) { Text(LanguageManager.tr("close", lang)) } }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val filters = listOf(
                    "TODAY" to LanguageManager.tr("today", lang), 
                    "THIS_MONTH" to LanguageManager.tr("this_month", lang), 
                    "7_DAYS" to LanguageManager.tr("days_7", lang), 
                    "30_DAYS" to LanguageManager.tr("days_30", lang), 
                    "ALL" to LanguageManager.tr("all", lang)
                )
                items(filters.size) { index ->
                    val (key, label) = filters[index]
                    FilterChip(
                        selected = dateFilter == key, 
                        onClick = { dateFilter = key }, 
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldGreen, selectedLabelColor = Color.White)
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(containerColor = EmeraldGreen)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(LanguageManager.tr("total_revenue", lang), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    Text(text = formatRupiah(totalOmzet), color = AmberAccent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${validTransactions.size} ${LanguageManager.tr("success_trx", lang)}", color = Color.White, fontSize = 12.sp)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.2f))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${LanguageManager.tr("cash_payment", lang)}:", color = Color.White, fontSize = 12.sp)
                        Text(formatRupiah(penjualanTunai), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${LanguageManager.tr("non_cash_payment", lang)}:", color = Color.White, fontSize = 12.sp)
                        Text(formatRupiah(penjualanNonTunai), color = Color(0xFFFFCDD2), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.2f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${LanguageManager.tr("gross_profit", lang)}:", color = Color.White, fontSize = 14.sp)
                        Text(formatRupiah(labaKotor), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${LanguageManager.tr("net_profit", lang)}:", color = Color.White, fontSize = 14.sp)
                        Text(formatRupiah(labaBersih), color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${LanguageManager.tr("total_cogs", lang)}:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Text(formatRupiah(totalHpp), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showFinanceReport = true }, 
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(LanguageManager.tr("view_detail_report", lang), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(LanguageManager.tr("inventory_value", lang), fontWeight = FontWeight.Bold)
                        Text(LanguageManager.tr("inventory_desc", lang), fontSize = 10.sp, color = SlateGrey)
                    }
                    Text(formatRupiah(inventoryValue), fontWeight = FontWeight.Bold, color = EmeraldGreen)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(LanguageManager.tr("last_7_days_chart", lang), fontWeight = FontWeight.Bold)
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
                            val start = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, -i); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                            }.timeInMillis
                            val end = start + 86400000L
                            transactions.filter { it.timestamp in start..end && it.status == "SUCCESS" }.sumOf { it.totalPrice }
                        }.reversed()

                        val maxSale = dailySales.maxOrNull() ?: 0L
                        val effectiveMax = if (maxSale == 0L) 1L else maxSale

                        val axisColor = Color.LightGray
                        val strokeWidth = 2f
                        
                        drawLine(color = axisColor, start = Offset(paddingLeft, 0f), end = Offset(paddingLeft, chartHeight), strokeWidth = strokeWidth)
                        drawLine(color = axisColor, start = Offset(paddingLeft, chartHeight), end = Offset(size.width, chartHeight), strokeWidth = strokeWidth)

                        val textPaint = android.graphics.Paint().apply { 
                            color = android.graphics.Color.GRAY
                            textSize = 24f 
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        val dateFormatShort = SimpleDateFormat("dd/MM", Locale.getDefault())

                        dailySales.forEachIndexed { index, sale ->
                            val x = paddingLeft + index * slotWidth + (slotWidth - barWidth) / 2f
                            val barHeight = (sale.toFloat() / effectiveMax) * chartHeight
                            
                            drawRect(
                                color = EmeraldGreen,
                                topLeft = Offset(x, chartHeight - barHeight),
                                size = Size(barWidth, barHeight)
                            )
                            
                            val dateStr = dateFormatShort.format(Date(System.currentTimeMillis() - (days - 1 - index) * 86400000L))
                            drawContext.canvas.nativeCanvas.drawText(
                                dateStr,
                                x + (barWidth / 2),
                                size.height - 10f,
                                textPaint
                            )
                        }
                    }
                }
            }
        }

        if (expiringProducts.isNotEmpty()) {
            item { Text("⚠️ ${LanguageManager.tr("expiring_items", lang)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ErrorRed) }
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        expiringProducts.forEachIndexed { index, product ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(product.name, fontWeight = FontWeight.Bold, color = Color.Black)
                                Text(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(product.expiryDate)), color = ErrorRed, fontWeight = FontWeight.Bold)
                            }
                            if (index < expiringProducts.size - 1) HorizontalDivider(color = ErrorRed.copy(alpha = 0.2f))
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
                            Text(LanguageManager.tr("low_stock_warning", lang), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("${LanguageManager.tr("low_stock_desc", lang)} (${lowStockProducts.size})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }

        item { Text(LanguageManager.tr("staff_performance", lang), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val validUsers = users.filter { it.role != "OWNER" }
                    if (validUsers.isEmpty()) {
                        Text(LanguageManager.tr("no_cashier_data", lang), fontSize = 12.sp, color = SlateGrey)
                    } else {
                        validUsers.forEachIndexed { index, user ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(user.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    val userTxCount = validTransactions.count { it.cashierName == user.name }
                                    Text("$userTxCount ${LanguageManager.tr("trx_period", lang)}", fontSize = 11.sp, color = SlateGrey)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(LanguageManager.tr("total_commission", lang), fontSize = 10.sp, color = SlateGrey)
                                    Text(formatRupiah(user.totalCommission), fontWeight = FontWeight.ExtraBold, color = EmeraldGreen)
                                }
                            }
                            if (index < validUsers.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        }
                    }
                }
            }
        }

        if (filteredTransactions.isNotEmpty()) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { 
                            transactionsToExport = filteredTransactions
                            createExcelLauncher.launch("Zokas_Report_${System.currentTimeMillis()}.xlsx") 
                        },
                        modifier = Modifier.weight(1f), 
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) { Text("EXCEL") }
                    
                    Button(
                        onClick = { 
                            transactionsToExport = filteredTransactions
                            createPdfLauncher.launch("Zokas_Laporan_${System.currentTimeMillis()}.pdf") 
                        },
                        modifier = Modifier.weight(1f), 
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) { Text("PDF") }
                }
            }
        }

        item { Text(LanguageManager.tr("transaction_history", lang), fontWeight = FontWeight.Bold, fontSize = 16.sp) }

        if (filteredTransactions.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text(LanguageManager.tr("no_trx_found", lang)) } }
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
                            Text("${LanguageManager.tr("receipt", lang)} #${tx.id}", fontWeight = FontWeight.Bold, textDecoration = if (isVoided) TextDecoration.LineThrough else TextDecoration.None)
                            Text(dateFormat.format(Date(tx.timestamp)))
                        }
                        Text("${LanguageManager.tr("customer", lang)}: ${tx.customerName} | ${LanguageManager.tr("kasir", lang)}: ${tx.cashierName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${LanguageManager.tr("total", lang)}: ${formatRupiah(tx.totalPrice)}", fontWeight = FontWeight.Bold, color = if (isVoided) SlateGrey else AmberAccent, textDecoration = if (isVoided) TextDecoration.LineThrough else TextDecoration.None)
                            if (isVoided) Text(LanguageManager.tr("void", lang), color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            if (isUnpaid) Text(LanguageManager.tr("bon", lang), color = AmberAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    val itemsFlow = remember(transaction.id) { viewModel.getTransactionItems(transaction.id) }
    val items by itemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var showReceipt by remember { mutableStateOf(false) }
    var showVoidPinDialog by remember { mutableStateOf(false) }
    var showRefundPinDialog by remember { mutableStateOf(false) }
    var showPayBonDialog by remember { mutableStateOf(false) }

    if (showReceipt) {
        ReceiptDialog(
            transaction = transaction,
            viewModel = viewModel,
            storeName = settingsManager.storeName ?: "ZoKas",
            storeAddress = settingsManager.storeAddress ?: "",
            storePhone = settingsManager.storePhone ?: "-",
            storeFooter = settingsManager.storeFooter ?: "Terima kasih!",
            settingsManager = settingsManager,
            onDismiss = { showReceipt = false }
        )
    }

    if (showVoidPinDialog) {
        VoidPinDialog(
            lang = lang,
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
            title = { Text(LanguageManager.tr("refund_auth", lang)) },
            text = {
                Column {
                    Text(LanguageManager.tr("enter_pin", lang))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text(LanguageManager.tr("pin", lang)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.refundTransaction(transaction, pin, currentUser) { success: Boolean, msg: String ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) onDismiss()
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)) { Text(LanguageManager.tr("refund", lang)) }
            },
            dismissButton = { TextButton(onClick = { showRefundPinDialog = false }) { Text(LanguageManager.tr("batal", lang)) } }
        )
    }

    if (showPayBonDialog) {
        var cash by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPayBonDialog = false },
            title = { Text(LanguageManager.tr("bon_payment", lang)) },
            text = {
                Column {
                    Text("${LanguageManager.tr("total_bill", lang)}: ${formatRupiah(transaction.totalPrice)}")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = cash, onValueChange = { cash = it }, label = { Text(LanguageManager.tr("cash_received", lang)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = {
                Button(onClick = {
                    val c = cash.toLongOrNull() ?: 0L
                    if (c >= transaction.totalPrice) {
                        viewModel.payUnpaidTransaction(transaction, c) {
                            Toast.makeText(context, "BON Lunas", Toast.LENGTH_SHORT).show()
                            showPayBonDialog = false
                            onDismiss()
                        }
                    } else {
                        Toast.makeText(context, "Uang kurang!", Toast.LENGTH_SHORT).show()
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)) { Text(LanguageManager.tr("pay", lang)) }
            },
            dismissButton = { TextButton(onClick = { showPayBonDialog = false }) { Text(LanguageManager.tr("batal", lang)) } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (transaction.status == "SUCCESS" && currentUser.role == "OWNER") {
                    Button(
                        onClick = { showRefundPinDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) {
                        Text(LanguageManager.tr("refund", lang))
                    }
                }

                if (transaction.status == "UNPAID") {
                    Button(
                        onClick = { showPayBonDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) {
                        Text(LanguageManager.tr("pay_bon", lang))
                    }
                }

                Button(onClick = { showReceipt = true }, colors = ButtonDefaults.buttonColors(containerColor = AmberAccent)) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(LanguageManager.tr("print", lang))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(LanguageManager.tr("close", lang)) } },
        title = { Text("${LanguageManager.tr("transaction_details", lang)} #${transaction.id}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (transaction.status == "VOIDED") {
                    Text(LanguageManager.tr("voided_msg", lang), color = ErrorRed, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (transaction.status == "UNPAID") {
                    Text(LanguageManager.tr("unpaid_msg", lang), color = AmberAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text("${LanguageManager.tr("date", lang)}: ${dateFormat.format(Date(transaction.timestamp))}", fontSize = 12.sp)
                Text("${LanguageManager.tr("customer", lang)}: ${transaction.customerName}", fontSize = 12.sp)
                Text("${LanguageManager.tr("kasir", lang)}: ${transaction.cashierName} | ${transaction.tableName}", fontSize = 12.sp)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (items.isEmpty()) {
                    Text(LanguageManager.tr("loading", lang), fontSize = 12.sp, color = SlateGrey)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                        items(items) { item ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${item.quantity} x ${formatRupiah(item.price)}", fontSize = 12.sp, color = SlateGrey)
                                    if (item.notes.isNotEmpty()) {
                                        Text("${LanguageManager.tr("note", lang)}: ${item.notes}", fontSize = 11.sp, color = ErrorRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(formatRupiah(item.subtotal), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${LanguageManager.tr("total", lang)}:", fontWeight = FontWeight.Bold)
                    Text(formatRupiah(transaction.totalPrice), fontWeight = FontWeight.Bold, color = if (transaction.status == "VOIDED") SlateGrey else AmberAccent)
                }
            }
        }
    )
}

@Composable
fun VoidPinDialog(lang: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LanguageManager.tr("supervisor_auth", lang)) },
        text = {
            Column {
                Text(LanguageManager.tr("enter_pin_cancel", lang))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6) pin = it },
                    label = { Text(LanguageManager.tr("supervisor_pin", lang)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(pin) }, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)) {
                Text(LanguageManager.tr("cancel_trx", lang))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(LanguageManager.tr("close", lang)) } }
    )
}

private suspend fun exportTransactionsToPdfDetailed(
    context: Context,
    uri: Uri,
    transactions: List<TransactionEntity>,
    dateFormat: SimpleDateFormat,
    viewModel: PosViewModel,
    settingsManager: SettingsManager
) {
    try {
        val pdfDocument = PdfDocument()
        val textPaint = Paint().apply { textSize = 10f; color = android.graphics.Color.BLACK }
        val headerPaint = Paint().apply { textSize = 16f; isFakeBoldText = true; color = android.graphics.Color.BLACK; textAlign = Paint.Align.CENTER }
        val subHeaderPaint = Paint().apply { textSize = 12f; isFakeBoldText = true; color = android.graphics.Color.DKGRAY }
        val linePaint = Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 1f }

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        var canvas = page.canvas
        var y = 40f

        val storeName = settingsManager.storeName ?: "ZoKas"
        val storeAddr = settingsManager.storeAddress ?: ""
        val storePhone = settingsManager.storePhone ?: ""
        
        canvas.drawText(storeName.uppercase(), 297.5f, y, headerPaint); y += 20f
        if (storeAddr.isNotBlank()) { canvas.drawText(storeAddr, 297.5f, y, textPaint.apply { textAlign = Paint.Align.CENTER }); y += 14f }
        if (storePhone.isNotBlank()) { canvas.drawText("Telp: $storePhone", 297.5f, y, textPaint); y += 14f }
        textPaint.textAlign = Paint.Align.LEFT

        y += 10f
        canvas.drawLine(40f, y, 555f, y, linePaint); y += 10f
        canvas.drawLine(40f, y, 555f, y, linePaint); y += 20f

        canvas.drawText("Laporan Transaksi", 40f, y, subHeaderPaint); y += 16f
        canvas.drawText("Dicetak: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}", 40f, y, textPaint); y += 16f
        canvas.drawText("Jumlah: ${transactions.size} transaksi", 40f, y, textPaint); y += 20f
        
        canvas.drawLine(40f, y, 555f, y, linePaint); y += 16f

        for (tx in transactions) {
            if (y > 780f) {
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                canvas = page.canvas
                y = 40f
                canvas.drawText("Laporan Transaksi (Lanjutan)", 40f, y, headerPaint); y += 20f
                canvas.drawLine(40f, y, 555f, y, linePaint); y += 16f
            }

            canvas.drawText("Struk #${tx.id} | ${dateFormat.format(Date(tx.timestamp))}", 40f, y, subHeaderPaint); y += 16f
            canvas.drawText("Pelanggan: ${tx.customerName} | Kasir: ${tx.cashierName}", 40f, y, textPaint); y += 16f

            val items = viewModel.getTransactionItems(tx.id).first()
            
            if (items.isEmpty()) {
                canvas.drawText("- (Tidak ada rincian item) -", 50f, y, textPaint); y += 16f
            } else {
                for (item in items) {
                    if (y > 800f) {
                        pdfDocument.finishPage(page)
                        pageNumber++
                        page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                        canvas = page.canvas
                        y = 40f
                    }
                    val itemText = "  ${item.quantity}x ${item.productName} @ ${formatRupiah(item.price)}"
                    val subtotalText = formatRupiah(item.subtotal)
                    canvas.drawText(itemText, 40f, y, textPaint)
                    val textWidth = textPaint.measureText(subtotalText)
                    canvas.drawText(subtotalText, 555f - textWidth, y, textPaint)
                    y += 14f

                    if (item.notes.isNotEmpty()) {
                        canvas.drawText("     *Catatan: ${item.notes}", 40f, y, textPaint); y += 14f
                    }
                }
            }

            y += 4f
            canvas.drawLine(40f, y, 555f, y, linePaint); y += 16f
            
            val totalText = "TOTAL: ${formatRupiah(tx.totalPrice)}"
            val totalWidth = subHeaderPaint.measureText(totalText)
            canvas.drawText(totalText, 555f - totalWidth, y, subHeaderPaint); y += 24f
        }

        pdfDocument.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        Toast.makeText(context, "PDF berhasil diekspor", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal ekspor PDF: ${e.message}", Toast.LENGTH_LONG).show()
    }
}