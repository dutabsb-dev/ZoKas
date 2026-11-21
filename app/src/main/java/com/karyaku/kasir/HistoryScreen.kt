package com.karyaku.kasir

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    transactions: List<TransactionEntity>,
    viewModel: PosViewModel,
    settingsManager: SettingsManager,
    currentUser: UserEntity
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val lang = settingsManager.language

    val bestSellers by viewModel.bestSellers.collectAsStateWithLifecycle()
    val inventoryValue by viewModel.inventoryValue.collectAsStateWithLifecycle()
    val netProfit by viewModel.netProfit.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val cashFlows by viewModel.cashFlows.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val shifts by viewModel.shifts.collectAsStateWithLifecycle()

    val lowStockProducts = remember(products) {
        products.filter { !it.isService && it.stock <= 5 && it.stock >= 0 }
    }

    val expiringProducts = remember(products) {
        val thirtyDaysFromNow = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
        products.filter { it.expiryDate > 0 && it.expiryDate <= thirtyDaysFromNow }
    }

    var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var dateFilter by remember { mutableStateOf("TODAY") }
    val initialRangeEnd = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }
    val initialRangeStart = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    var rangeStartMillis by remember { mutableStateOf(initialRangeStart) }
    var rangeEndMillis by remember { mutableStateOf(initialRangeEnd) }
    var showRangeDialog by remember { mutableStateOf(false) }
    var pickingRangeTarget by remember { mutableStateOf("START") } // "START" | "END"
    var showCustomDatePicker by remember { mutableStateOf(false) }
    var showFinanceReport by remember { mutableStateOf(false) }
    var showResetOmzetAuth by remember { mutableStateOf(false) }

    val resetPrefs = remember { context.getSharedPreferences("zokas_finance_reset", Context.MODE_PRIVATE) }
    var revenueResetTimestamp by remember {
        mutableStateOf(resetPrefs.getLong("revenue_reset_timestamp", 0L))
    }

    var transactionsToExport by remember { mutableStateOf<List<TransactionEntity>>(emptyList()) }

    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val startOfWeek = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (timeInMillis > System.currentTimeMillis()) {
            add(Calendar.DAY_OF_YEAR, -7)
        }
    }.timeInMillis

    val startOfMonth = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val normalizedRangeStart = remember(rangeStartMillis, rangeEndMillis) {
        val minMillis = minOf(rangeStartMillis, rangeEndMillis)
        Calendar.getInstance().apply {
            timeInMillis = minMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val normalizedRangeEnd = remember(rangeStartMillis, rangeEndMillis) {
        val maxMillis = maxOf(rangeStartMillis, rangeEndMillis)
        Calendar.getInstance().apply {
            timeInMillis = maxMillis
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    fun matchesSelectedPeriod(ts: Long): Boolean {
        if (ts < revenueResetTimestamp) return false
        return when (dateFilter) {
            "TODAY" -> ts >= startOfToday
            "THIS_WEEK" -> ts >= startOfWeek
            "THIS_MONTH" -> ts >= startOfMonth
            "DATE_RANGE", "CUSTOM_DATE" -> ts in normalizedRangeStart..normalizedRangeEnd
            else -> true
        }
    }

    val filteredTransactions = transactions.filter { matchesSelectedPeriod(it.timestamp) }
    val filteredCashFlows = cashFlows.filter { matchesSelectedPeriod(it.timestamp) }
    val filteredExpenses = expenses.filter { matchesSelectedPeriod(it.timestamp) }
    val filteredShifts = shifts.filter { matchesSelectedPeriod(it.startTime) }
    val filteredPurchases = purchases.filter { matchesSelectedPeriod(it.timestamp) }
    val filteredAuditLogs = auditLogs.filter { matchesSelectedPeriod(it.timestamp) }

    val shortDateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")) }
    val periodDisplayLabel = remember(dateFilter, normalizedRangeStart, normalizedRangeEnd) {
        when (dateFilter) {
            "TODAY" -> "Hari Ini (${shortDateFmt.format(Date())})"
            "THIS_WEEK" -> "Minggu Ini"
            "THIS_MONTH" -> "Bulan Ini (${SimpleDateFormat("MMMM yyyy", Locale("id", "ID")).format(Date())})"
            "DATE_RANGE", "CUSTOM_DATE" -> "${shortDateFmt.format(Date(normalizedRangeStart))} s/d ${shortDateFmt.format(Date(normalizedRangeEnd))}"
            else -> "Semua Periode"
        }
    }

    val createExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val success = ExcelExporter.exportTransactionsToExcel(context, uri, transactionsToExport)
                if (success) {
                    Toast.makeText(context, "Excel Transaksi berhasil diekspor!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Gagal ekspor Excel.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val createShiftExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val success = ExcelExporter.exportShiftsToExcel(context, uri, shifts)
                if (success) {
                    Toast.makeText(context, "Laporan Shift berhasil diekspor!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Gagal ekspor Laporan Shift.", Toast.LENGTH_SHORT).show()
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

    val createUniversalExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = UniversalBookkeepingExporter.exportUniversalBookkeepingExcel(
                    context = context,
                    uri = uri,
                    viewModel = viewModel,
                    settingsManager = settingsManager,
                    transactions = filteredTransactions,
                    shifts = filteredShifts,
                    cashFlows = filteredCashFlows,
                    expenses = filteredExpenses,
                    products = products,
                    rawMaterials = rawMaterials,
                    purchases = filteredPurchases,
                    auditLogs = filteredAuditLogs,
                    periodLabel = periodDisplayLabel
                )
                if (ok) {
                    Toast.makeText(context, "Buku Pembukuan Lengkap (Excel) berhasil diekspor!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Gagal mengekspor Buku Pembukuan Excel.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val createUniversalPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                UniversalBookkeepingExporter.exportUniversalBookkeepingPdf(
                    context = context,
                    uri = uri,
                    viewModel = viewModel,
                    settingsManager = settingsManager,
                    transactions = filteredTransactions,
                    shifts = filteredShifts,
                    cashFlows = filteredCashFlows,
                    expenses = filteredExpenses,
                    products = products,
                    rawMaterials = rawMaterials,
                    purchases = filteredPurchases,
                    auditLogs = filteredAuditLogs,
                    periodLabel = periodDisplayLabel
                )
            }
        }
    }

    val taxPrefs = remember { context.getSharedPreferences("zokas_tax_ext", Context.MODE_PRIVATE) }
    val isTaxInclusive = remember { taxPrefs.getBoolean("is_tax_inclusive", false) }
    val currentTaxRate = settingsManager.taxPercent

    val validTransactions = filteredTransactions.filter { it.status == "SUCCESS" }
    val totalOmzet = validTransactions.sumOf { it.totalPrice }
    val totalHpp = validTransactions.sumOf { it.totalCost }

    val totalPajakTerkumpul = remember(validTransactions, currentTaxRate, isTaxInclusive) {
        if (currentTaxRate <= 0.0) 0L
        else validTransactions.sumOf { tx ->
            (tx.totalPrice - (tx.totalPrice / (1.0 + (currentTaxRate / 100.0)))).toLong()
        }
    }
    val omzetBersihTanpaPajak = (totalOmzet - totalPajakTerkumpul).coerceAtLeast(0L)
    val labaKotor = omzetBersihTanpaPajak - totalHpp

    val totalCashIn = filteredCashFlows.filter { it.type == "IN" }.sumOf { it.amount }
    val totalCashOut = filteredCashFlows.filter { it.type == "OUT" }.sumOf { it.amount }
    val totalOperationalExpenses = filteredExpenses.sumOf { it.amount }
    val penjualanTunai = validTransactions.filter { it.paymentMethod == "CASH" }.sumOf { it.totalPrice }
    val penjualanNonTunai = validTransactions.filter { it.paymentMethod != "CASH" }.sumOf { it.totalPrice }
    val saldoLaciTunai = (penjualanTunai + totalCashIn - totalCashOut).coerceAtLeast(0L)
    val labaBersih = labaKotor - totalCashOut - totalOperationalExpenses

    val chartBarColor = MaterialTheme.colorScheme.primary
    val chartAxisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val chartTextColorArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    if (showRangeDialog) {
        AlertDialog(
            onDismissRequest = { showRangeDialog = false },
            title = { Text("Pilih Range Tanggal", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Tentukan tanggal awal dan akhir (misal 10 Nov s/d 20 Nov) untuk menyaring seluruh data pembukuan:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            pickingRangeTarget = "START"
                            showCustomDatePicker = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Dari Tanggal: ${shortDateFmt.format(Date(normalizedRangeStart))}", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = {
                            pickingRangeTarget = "END"
                            showCustomDatePicker = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Sampai Tanggal: ${shortDateFmt.format(Date(normalizedRangeEnd))}", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        dateFilter = "DATE_RANGE"
                        showRangeDialog = false
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Terapkan Range", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRangeDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showCustomDatePicker) {
        val initMillis = if (pickingRangeTarget == "START") normalizedRangeStart else normalizedRangeEnd
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showCustomDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val picked = datePickerState.selectedDateMillis ?: initMillis
                    if (pickingRangeTarget == "START") {
                        rangeStartMillis = picked
                        if (picked > rangeEndMillis) rangeEndMillis = picked
                    } else {
                        rangeEndMillis = picked
                        if (picked < rangeStartMillis) rangeStartMillis = picked
                    }
                    dateFilter = "DATE_RANGE"
                    showCustomDatePicker = false
                }) {
                    Text("Pilih", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDatePicker = false }) {
                    Text("Batal")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showResetOmzetAuth) {
        OwnerSecurityAuthDialog(
            title = "Reset Total Omzet ke Rp 0",
            subtitle = "Semua ringkasan omzet, laba kotor, dan laba bersih akan dimulai ulang dari Rp 0. Verifikasi PIN Owner atau Sidik Jari:",
            users = users,
            activity = LocalActivity.current as? FragmentActivity,
            onSuccess = {
                val nowMillis = System.currentTimeMillis()
                revenueResetTimestamp = nowMillis
                resetPrefs.edit().putLong("revenue_reset_timestamp", nowMillis).apply()
                viewModel.addCashFlow("IN", 0L, "RESET LAPORAN OMZET KE RP 0 oleh ${currentUser.name}", currentUser)
                showResetOmzetAuth = false
                Toast.makeText(context, "Total Omzet & Laporan Keuangan berhasil direset ke Rp 0!", Toast.LENGTH_LONG).show()
            },
            onDismiss = { showResetOmzetAuth = false }
        )
    }

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
            icon = {
                Icon(
                    Icons.Default.Money,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    LanguageManager.tr("financial_report", lang),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "${LanguageManager.tr("period", lang)}: ${dateFilter.replace("_", " ")}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        LanguageManager.tr("revenue", lang),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(LanguageManager.tr("cash_sales", lang), color = MaterialTheme.colorScheme.onSurface)
                        Text(formatRupiah(penjualanTunai), color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(LanguageManager.tr("non_cash_sales", lang), color = MaterialTheme.colorScheme.onSurface)
                        Text(formatRupiah(penjualanNonTunai), color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            LanguageManager.tr("cash_in_add", lang),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatRupiah(totalCashIn),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        LanguageManager.tr("expenses", lang),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(LanguageManager.tr("total_cogs_cost", lang), color = MaterialTheme.colorScheme.onSurface)
                        Text(formatRupiah(totalHpp), color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            LanguageManager.tr("cash_out_op", lang),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatRupiah(totalCashOut),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Biaya Operasional (Listrik/Gaji/dll)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatRupiah(totalOperationalExpenses),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (totalPajakTerkumpul > 0L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "Titipan Pajak (PB1/PPN)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                formatRupiah(totalPajakTerkumpul),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Estimasi Saldo Laci Tunai",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            formatRupiah(saldoLaciTunai),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            LanguageManager.tr("net_profit", lang),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            formatRupiah(labaBersih),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showFinanceReport = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(LanguageManager.tr("close", lang)) }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val rangeChipLabel = remember(normalizedRangeStart, normalizedRangeEnd, dateFilter) {
                if (dateFilter == "DATE_RANGE" || dateFilter == "CUSTOM_DATE") {
                    val d1 = SimpleDateFormat("dd MMM", Locale("id", "ID")).format(Date(normalizedRangeStart))
                    val d2 = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(normalizedRangeEnd))
                    "$d1 - $d2"
                } else {
                    "Range Tanggal"
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val filters = listOf(
                    "TODAY" to "Hari Ini",
                    "THIS_WEEK" to "Minggu Ini",
                    "THIS_MONTH" to "Bulan Ini",
                    "DATE_RANGE" to rangeChipLabel
                )
                items(filters.size) { index ->
                    val (key, label) = filters[index]
                    FilterChip(
                        selected = dateFilter == key || (key == "DATE_RANGE" && dateFilter == "CUSTOM_DATE"),
                        onClick = {
                            if (key == "DATE_RANGE") {
                                showRangeDialog = true
                            } else {
                                dateFilter = key
                            }
                        },
                        label = {
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontWeight = if (dateFilter == key) FontWeight.Bold else FontWeight.Normal,
                                color = if (dateFilter == key)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        LanguageManager.tr("total_revenue", lang),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatRupiah(totalOmzet),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${validTransactions.size} ${LanguageManager.tr("success_trx", lang)}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${LanguageManager.tr("cash_payment", lang)}:",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Text(
                            formatRupiah(penjualanTunai),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${LanguageManager.tr("non_cash_payment", lang)}:",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Text(
                            formatRupiah(penjualanNonTunai),
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${LanguageManager.tr("gross_profit", lang)}:",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                        Text(
                            formatRupiah(labaKotor),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${LanguageManager.tr("net_profit", lang)}:",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            formatRupiah(labaBersih),
                            color = if (labaBersih >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (totalPajakTerkumpul > 0L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "Titipan Pajak (PB1/PPN):",
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 12.sp
                            )
                            Text(
                                formatRupiah(totalPajakTerkumpul),
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    if (totalOperationalExpenses > 0L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "Biaya Operasional:",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                            Text(
                                "- ${formatRupiah(totalOperationalExpenses)}",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${LanguageManager.tr("total_cogs", lang)}:",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Text(
                            formatRupiah(totalHpp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showFinanceReport = true },
                            modifier = Modifier.weight(1.35f).defaultMinSize(minHeight = 44.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Detail Laporan",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        if (currentUser.role == "OWNER") {
                            OutlinedButton(
                                onClick = { showResetOmzetAuth = true },
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Reset Rp 0",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            LanguageManager.tr("inventory_value", lang),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            LanguageManager.tr("inventory_desc", lang),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        formatRupiah(inventoryValue),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        LanguageManager.tr("last_7_days_chart", lang),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        val days = 7
                        val paddingLeft = 80f
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
                            transactions.filter { it.timestamp in start..end && it.status == "SUCCESS" }
                                .sumOf { it.totalPrice }
                        }.reversed()

                        val maxSale = dailySales.maxOrNull() ?: 0L
                        val yAxisMax = if (maxSale == 0L) 100000L else ((maxSale / 100000) + 1) * 100000L

                        val strokeWidth = 2f

                        val textPaint = android.graphics.Paint().apply {
                            color = chartTextColorArgb
                            textSize = 24f
                            textAlign = android.graphics.Paint.Align.RIGHT
                        }

                        val datePaint = android.graphics.Paint().apply {
                            color = chartTextColorArgb
                            textSize = 24f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }

                        val ySteps = 4
                        for (i in 0..ySteps) {
                            val yValue = (yAxisMax / ySteps) * i
                            val yPos = chartHeight - ((yValue.toFloat() / yAxisMax.toFloat()) * chartHeight)

                            drawLine(
                                color = chartAxisColor,
                                start = Offset(paddingLeft, yPos),
                                end = Offset(size.width, yPos),
                                strokeWidth = 1f
                            )

                            val labelText = if (yValue == 0L) "0"
                                else if (yValue >= 1000000) "${yValue / 1000000}Jt"
                                else "${yValue / 1000}rb"
                            drawContext.canvas.nativeCanvas.drawText(
                                labelText,
                                paddingLeft - 8f,
                                yPos + 8f,
                                textPaint
                            )
                        }

                        drawLine(
                            color = chartAxisColor,
                            start = Offset(paddingLeft, 0f),
                            end = Offset(paddingLeft, chartHeight),
                            strokeWidth = strokeWidth
                        )
                        drawLine(
                            color = chartAxisColor,
                            start = Offset(paddingLeft, chartHeight),
                            end = Offset(size.width, chartHeight),
                            strokeWidth = strokeWidth
                        )

                        val dateFormatShort = SimpleDateFormat("dd/MM", Locale.getDefault())

                        dailySales.forEachIndexed { index, sale ->
                            val x = paddingLeft + index * slotWidth + (slotWidth - barWidth) / 2f
                            val barHeight = (sale.toFloat() / yAxisMax.toFloat()) * chartHeight

                            drawRect(
                                color = chartBarColor,
                                topLeft = Offset(x, chartHeight - barHeight),
                                size = Size(barWidth, barHeight)
                            )

                            val dateStr = dateFormatShort.format(
                                Date(System.currentTimeMillis() - (days - 1 - index) * 86400000L)
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                dateStr,
                                x + (barWidth / 2),
                                size.height - 10f,
                                datePaint
                            )
                        }
                    }
                }
            }
        }

        if (expiringProducts.isNotEmpty()) {
            item {
                Text(
                    "⚠️ ${LanguageManager.tr("expiring_items", lang)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        expiringProducts.forEachIndexed { index, product ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    product.name,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(product.expiryDate)),
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (index < expiringProducts.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                LanguageManager.tr("staff_performance", lang),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val validUsers = users.filter { it.role != "OWNER" }
                    if (validUsers.isEmpty()) {
                        Text(
                            LanguageManager.tr("no_cashier_data", lang),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        validUsers.forEachIndexed { index, user ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        user.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val userTxCount = validTransactions.count { it.cashierName == user.name }
                                    Text(
                                        "$userTxCount ${LanguageManager.tr("trx_period", lang)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        LanguageManager.tr("total_commission", lang),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        formatRupiah(user.totalCommission),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (index < validUsers.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        // KARTU BUKU PEMBUKUAN UNIVERSAL (E-BOOK PDF & EXCEL MULTI-SHEET)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Buku Pembukuan Lengkap (Universal)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Satukan Laba Rugi, Kas, Shift, Transaksi, Stok, Bahan Baku, PO, dan Log Audit dalam satu dokumen berlogo & identitas toko.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val storeSlug = (settingsManager.storeName ?: "ZoKas").replace(Regex("[^A-Za-z0-9]"), "_")
                                createUniversalExcelLauncher.launch("Buku_Pembukuan_${storeSlug}_${System.currentTimeMillis()}.xlsx")
                            },
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("EXCEL LENGKAP", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                val storeSlug = (settingsManager.storeName ?: "ZoKas").replace(Regex("[^A-Za-z0-9]"), "_")
                                createUniversalPdfLauncher.launch("EBook_Pembukuan_${storeSlug}_${System.currentTimeMillis()}.pdf")
                            },
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("E-BOOK PDF", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            transactionsToExport = filteredTransactions
                            createExcelLauncher.launch("Zokas_Transaksi_${System.currentTimeMillis()}.xlsx")
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) { Text("EXCEL TRANSAKSI", fontSize = 12.sp) }

                    Button(
                        onClick = {
                            transactionsToExport = filteredTransactions
                            createPdfLauncher.launch("Zokas_Transaksi_${System.currentTimeMillis()}.pdf")
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) { Text("PDF TRANSAKSI", fontSize = 12.sp) }
                }

                Button(
                    onClick = {
                        if (shifts.isEmpty()) {
                            Toast.makeText(context, "Belum ada riwayat shift.", Toast.LENGTH_SHORT).show()
                        } else {
                            createShiftExcelLauncher.launch("Zokas_Laporan_Shift_${System.currentTimeMillis()}.xlsx")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Icon(
                        Icons.Default.List,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "EKSPOR EXCEL LAPORAN SHIFT (TUTUP KASIR)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Text(
                LanguageManager.tr("transaction_history", lang),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (filteredTransactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        LanguageManager.tr("no_trx_found", lang),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredTransactions) { tx ->
                val isVoided = tx.status == "VOIDED"
                val isUnpaid = tx.status == "UNPAID"

                val cardContainerColor = when {
                    isVoided -> MaterialTheme.colorScheme.errorContainer
                    isUnpaid -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val cardOnColor = when {
                    isVoided -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Card(
                    modifier = Modifier.fillMaxWidth().clickable { selectedTransaction = tx },
                    colors = CardDefaults.cardColors(containerColor = cardContainerColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${LanguageManager.tr("receipt", lang)} #${tx.id}",
                                fontWeight = FontWeight.Bold,
                                textDecoration = if (isVoided) TextDecoration.LineThrough else TextDecoration.None,
                                color = cardOnColor
                            )
                            Text(dateFormat.format(Date(tx.timestamp)), color = cardOnColor)
                        }
                        Text(
                            "${LanguageManager.tr("customer", lang)}: ${tx.customerName} | ${LanguageManager.tr("kasir", lang)}: ${tx.cashierName}",
                            fontSize = 12.sp,
                            color = if (isVoided)
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${LanguageManager.tr("total", lang)}: ${formatRupiah(tx.totalPrice)}",
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isVoided -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                textDecoration = if (isVoided) TextDecoration.LineThrough else TextDecoration.None
                            )
                            if (isVoided) {
                                Text(
                                    LanguageManager.tr("void", lang),
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            if (isUnpaid) {
                                Text(
                                    LanguageManager.tr("bon", lang),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
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
    val txItems by itemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

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
                viewModel.voidTransaction(transaction, pin, currentUser) { success, message ->
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
                    Text(LanguageManager.tr("enter_pin", lang), color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text(LanguageManager.tr("pin", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.refundTransaction(transaction, pin, currentUser) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(LanguageManager.tr("refund", lang)) }
            },
            dismissButton = {
                TextButton(onClick = { showRefundPinDialog = false }) {
                    Text(LanguageManager.tr("batal", lang), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showPayBonDialog) {
        var cash by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPayBonDialog = false },
            title = { Text(LanguageManager.tr("bon_payment", lang)) },
            text = {
                Column {
                    Text(
                        "${LanguageManager.tr("total_bill", lang)}: ${formatRupiah(transaction.totalPrice)}",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cash,
                        onValueChange = { cash = it },
                        label = { Text(LanguageManager.tr("cash_received", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val c = cash.toLongOrNull() ?: 0L
                        if (c >= transaction.totalPrice) {
                            viewModel.payUnpaidTransaction(transaction, c) {
                                viewModel.addCashFlow("IN", transaction.totalPrice, "Pelunasan Bon Struk #${transaction.id}", currentUser)
                                Toast.makeText(context, "BON Lunas & dicatat ke Kas Masuk", Toast.LENGTH_SHORT).show()
                                showPayBonDialog = false
                                onDismiss()
                            }
                        } else {
                            Toast.makeText(context, "Uang kurang!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text(LanguageManager.tr("pay", lang)) }
            },
            dismissButton = {
                TextButton(onClick = { showPayBonDialog = false }) {
                    Text(LanguageManager.tr("batal", lang), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (transaction.status == "SUCCESS" && currentUser.role == "OWNER") {
                    Button(
                        onClick = { showRefundPinDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) { Text(LanguageManager.tr("refund", lang)) }
                }

                if (transaction.status == "UNPAID") {
                    Button(
                        onClick = { showPayBonDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) { Text(LanguageManager.tr("pay_bon", lang)) }
                }

                Button(
                    onClick = { showReceipt = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Icon(
                        Icons.Default.Print,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(LanguageManager.tr("print", lang))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(LanguageManager.tr("close", lang), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        title = { Text("${LanguageManager.tr("transaction_details", lang)} #${transaction.id}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (transaction.status == "VOIDED") {
                    Text(
                        LanguageManager.tr("voided_msg", lang),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (transaction.status == "UNPAID") {
                    Text(
                        LanguageManager.tr("unpaid_msg", lang),
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Text(
                    "${LanguageManager.tr("date", lang)}: ${dateFormat.format(Date(transaction.timestamp))}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${LanguageManager.tr("customer", lang)}: ${transaction.customerName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${LanguageManager.tr("kasir", lang)}: ${transaction.cashierName} | ${transaction.tableName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (txItems.isEmpty()) {
                    Text(
                        LanguageManager.tr("loading", lang),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                        items(txItems) { item ->
                            val unitPrice = if (item.quantity > 0) item.subtotal / item.quantity else item.price
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.productName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "${item.quantity} x ${formatRupiah(unitPrice)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (item.notes.isNotEmpty()) {
                                        Text(
                                            "${LanguageManager.tr("note", lang)}: ${item.notes}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    formatRupiah(item.subtotal),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${LanguageManager.tr("total", lang)}:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        formatRupiah(transaction.totalPrice),
                        fontWeight = FontWeight.Bold,
                        color = if (transaction.status == "VOIDED")
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.primary
                    )
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
                Text(LanguageManager.tr("enter_pin_cancel", lang), color = MaterialTheme.colorScheme.onSurface)
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
            Button(
                onClick = { onConfirm(pin) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text(LanguageManager.tr("cancel_trx", lang)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(LanguageManager.tr("close", lang), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        val logoPath = settingsManager.storeLogoPath
        val logoBmp: Bitmap? = try {
            if (!logoPath.isNullOrBlank() && File(logoPath).exists()) {
                BitmapFactory.decodeFile(logoPath)
            } else {
                ContextCompat.getDrawable(context, R.drawable.logo_zokas)?.let { d ->
                    val b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
                    val c = Canvas(b)
                    d.setBounds(0, 0, 120, 120)
                    d.draw(c)
                    b
                }
            }
        } catch (_: Exception) { null }

        if (logoBmp != null) {
            canvas.drawBitmap(logoBmp, null, RectF(40f, 24f, 88f, 72f), null)
        }

        canvas.drawText(storeName.uppercase(), 297.5f, y, headerPaint); y += 20f
        if (storeAddr.isNotBlank()) { canvas.drawText(storeAddr, 297.5f, y, textPaint.apply { textAlign = Paint.Align.CENTER }); y += 14f }
        if (storePhone.isNotBlank()) { canvas.drawText("Telp: $storePhone", 297.5f, y, textPaint); y += 14f }
        textPaint.textAlign = Paint.Align.LEFT

        y += 14f
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
                canvas.drawText("${storeName.uppercase()} — Laporan Transaksi (Hal. $pageNumber)", 297.5f, y, headerPaint); y += 20f
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
                    val unitPrice = if (item.quantity > 0) item.subtotal / item.quantity else item.price
                    val itemText = "  ${item.quantity}x ${item.productName} @ ${formatRupiah(unitPrice)}"
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
