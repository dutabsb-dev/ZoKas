package com.karyaku.kasir

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.ShiftEntity
import com.karyaku.kasir.data.local.TransactionEntity
import com.karyaku.kasir.data.local.TransactionItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val RECEIPT_WIDTH = 32
private const val INVOICE_WIDTH = 48

private fun formatNumPlain(amount: Long): String {
    val nf = NumberFormat.getInstance(Locale("id", "ID"))
    return "Rp" + nf.format(amount)
}

private fun centerLine(text: String, width: Int = RECEIPT_WIDTH): String {
    val clean = text.trim()
    if (clean.length >= width) return clean.take(width)
    val padLeft = (width - clean.length) / 2
    return " ".repeat(padLeft) + clean
}

private fun wrapTextLines(text: String, maxWidth: Int = RECEIPT_WIDTH): List<String> {
    val words = text.trim().split(Regex("\\s+"))
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    for (w in words) {
        if (current.isEmpty()) {
            if (w.length > maxWidth) {
                w.chunked(maxWidth).forEach { lines.add(it) }
            } else {
                current.append(w)
            }
        } else if (current.length + 1 + w.length <= maxWidth) {
            current.append(" ").append(w)
        } else {
            lines.add(current.toString())
            current = StringBuilder()
            if (w.length > maxWidth) {
                w.chunked(maxWidth).forEach { lines.add(it) }
            } else {
                current.append(w)
            }
        }
    }
    if (current.isNotEmpty()) lines.add(current.toString())
    return if (lines.isEmpty()) listOf("") else lines
}

private fun leftRightLine(left: String, right: String, width: Int = RECEIPT_WIDTH): String {
    val r = right.trim()
    val maxLeft = (width - r.length - 1).coerceAtLeast(1)
    val l = if (left.length > maxLeft) left.take(maxLeft) else left
    val spaces = (width - l.length - r.length).coerceAtLeast(1)
    return l + " ".repeat(spaces) + r
}

private fun resolveDisplayQtyString(item: TransactionItemEntity): Pair<String, Double> {
    val fracMatch = Regex("\\[Qty:\\s*([0-9.]+)\\]").find(item.notes)
    val fracQty = fracMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    val effectiveQty = if (fracQty != null && fracQty > 0.0) fracQty else item.quantity.toDouble()
    val displayQtyStr = if (fracQty != null && fracQty > 0.0) {
        String.format(Locale.US, "%.3f", fracQty).trimEnd('0').trimEnd('.')
    } else {
        item.quantity.toString()
    }
    return displayQtyStr to effectiveQty
}

private fun buildFormattedReceiptByMode(
    mode: ReceiptTemplateMode,
    storeName: String,
    storeAddress: String,
    storePhone: String,
    storeFooter: String,
    storeTaxNpwp: String,
    transaction: TransactionEntity,
    items: List<TransactionItemEntity>,
    products: List<ProductEntity>,
    dpLedgerEntries: List<DpPaymentEntry>,
    latestShift: ShiftEntity?,
    dateStr: String,
    taxPercent: Double,
    serviceChargePercent: Double,
    isTaxInclusive: Boolean,
    isCulinaryBusiness: Boolean
): String {
    val width = if (mode == ReceiptTemplateMode.INVOICE_A5) INVOICE_WIDTH else RECEIPT_WIDTH
    val sb = StringBuilder()
    val dashLine = "-".repeat(width)
    val doubleLine = "=".repeat(width)
    val timeFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    // 1. STRUK ANTREAN (QUEUE TICKET)
    if (mode == ReceiptTemplateMode.QUEUE_TICKET) {
        wrapTextLines(storeName.ifBlank { "ZoKas Store" }.uppercase(), width).forEach {
            sb.appendLine(centerLine(it, width))
        }
        sb.appendLine(doubleLine)
        sb.appendLine(centerLine("NOMOR ANTREAN", width))
        val queueNum = String.format(Locale.US, "#%03d", (transaction.id % 1000L).toInt())
        sb.appendLine(centerLine("[  $queueNum  ]", width))
        sb.appendLine(doubleLine)
        sb.appendLine(leftRightLine("Waktu Cetak", dateStr, width))
        val customerLabel = transaction.customerName?.takeIf { it.isNotBlank() } ?: "Umum"
        sb.appendLine(leftRightLine("Pelanggan", customerLabel, width))
        if (isCulinaryBusiness) {
            val oType = transaction.orderType?.takeIf { it.isNotBlank() } ?: "Dine-In"
            val tLabel = if (transaction.tableName.isNotBlank() && oType == "Dine-In") "$oType (${transaction.tableName})" else oType
            sb.appendLine(leftRightLine("Tipe", tLabel, width))
        }
        sb.appendLine(dashLine)
        items.forEach { item ->
            val (qtyStr, effQty) = resolveDisplayQtyString(item)
            val unitPrice = if (effQty > 0.0) Math.round(item.subtotal / effQty) else item.price
            val matchedProduct = products.find { it.id == item.productId }
            val resolvedName = resolveVariantDisplayNameForReceipt(matchedProduct, item.productName, unitPrice)
            wrapTextLines("${qtyStr}x $resolvedName", width).forEach { sb.appendLine(it) }
            if (item.notes.isNotBlank()) {
                wrapTextLines("   Cat: ${item.notes}", width).forEach { sb.appendLine(it) }
            }
        }
        sb.appendLine(dashLine)
        sb.appendLine(leftRightLine("Total Item", "${items.sumOf { it.quantity }} item", width))
        sb.appendLine(centerLine("Mohon menunggu panggilan", width))
        return sb.toString().trimEnd()
    }

    // 2. STRUK TUTUP KASIR / SHIFT REPORT
    if (mode == ReceiptTemplateMode.SHIFT_REPORT) {
        wrapTextLines(storeName.ifBlank { "ZoKas Store" }.uppercase(), width).forEach {
            sb.appendLine(centerLine(it, width))
        }
        sb.appendLine(centerLine("STRUK LAPORAN SHIFT", width))
        sb.appendLine(doubleLine)
        if (latestShift != null) {
            sb.appendLine(leftRightLine("Kasir", latestShift.userName, width))
            sb.appendLine(leftRightLine("Waktu Buka", timeFmt.format(Date(latestShift.startTime)), width))
            val endStr = latestShift.endTime?.let { timeFmt.format(Date(it)) } ?: "Masih Aktif"
            sb.appendLine(leftRightLine("Waktu Tutup", endStr, width))
            sb.appendLine(dashLine)
            sb.appendLine(leftRightLine("Modal Awal", formatNumPlain(latestShift.openingCash), width))
            sb.appendLine(leftRightLine("Kas Sistem", formatNumPlain(latestShift.expectedCash), width))
            sb.appendLine(leftRightLine("Kas Fisik", formatNumPlain(latestShift.closingCash ?: 0L), width))
            sb.appendLine(dashLine)
            val diff = latestShift.discrepancy ?: 0L
            sb.appendLine(leftRightLine("SELISIH LACI", formatNumPlain(diff), width))
        } else {
            sb.appendLine(centerLine("Belum ada data shift tercatat", width))
        }
        sb.appendLine(doubleLine)
        sb.appendLine(centerLine("Dicetak: ${timeFmt.format(Date())}", width))
        return sb.toString().trimEnd()
    }

    // HEADER TOKO (Kecuali mode COMPACT yang hemat kertas)
    wrapTextLines(storeName.ifBlank { "ZoKas Store" }.uppercase(), width).forEach {
        sb.appendLine(centerLine(it, width))
    }
    if (mode != ReceiptTemplateMode.COMPACT) {
        if (storeAddress.isNotBlank()) {
            wrapTextLines(storeAddress, width).forEach { sb.appendLine(centerLine(it, width)) }
        }
        if (storePhone.isNotBlank()) {
            sb.appendLine(centerLine("Telp: $storePhone", width))
        }
        if (mode == ReceiptTemplateMode.TAX_INVOICE && storeTaxNpwp.isNotBlank()) {
            sb.appendLine(centerLine("NPWP: $storeTaxNpwp", width))
        }
    }

    // JUDUL DOKUMEN KHUSUS
    when (mode) {
        ReceiptTemplateMode.INVOICE_A5 -> {
            sb.appendLine(doubleLine)
            sb.appendLine(centerLine("NOTA TRANSAKSI / INVOICE", width))
        }
        ReceiptTemplateMode.DP_SETTLEMENT -> {
            sb.appendLine(doubleLine)
            sb.appendLine(centerLine("NOTA PEMBAYARAN DP / PELUNASAN", width))
        }
        ReceiptTemplateMode.TAX_INVOICE -> {
            sb.appendLine(doubleLine)
            sb.appendLine(centerLine("FAKTUR PAJAK / TAX RECEIPT", width))
        }
        ReceiptTemplateMode.GIFT_RECEIPT -> {
            sb.appendLine(doubleLine)
            sb.appendLine(centerLine("TANDA TERIMA / GIFT RECEIPT", width))
        }
        else -> Unit
    }

    sb.appendLine(doubleLine)

    // METADATA TRANSAKSI
    sb.appendLine(leftRightLine("No. Nota", "#${transaction.id}", width))
    sb.appendLine(leftRightLine("Tanggal", dateStr, width))
    if (mode != ReceiptTemplateMode.COMPACT) {
        sb.appendLine(leftRightLine("Kasir", transaction.cashierName ?: "Kasir", width))
    }
    val customerLabel = transaction.customerName?.takeIf { it.isNotBlank() } ?: "Umum"
    sb.appendLine(leftRightLine("Pelanggan", customerLabel, width))
    if (isCulinaryBusiness && mode != ReceiptTemplateMode.COMPACT) {
        val orderLabel = buildString {
            val oType = transaction.orderType?.takeIf { it.isNotBlank() } ?: "Dine-In"
            append(oType)
            if (transaction.tableName.isNotBlank() && oType == "Dine-In") {
                append(" (${transaction.tableName})")
            }
        }
        sb.appendLine(leftRightLine("Tipe", orderLabel, width))
    }
    sb.appendLine(dashLine)

    // DAFTAR ITEM
    var subtotalItems = 0L
    if (mode == ReceiptTemplateMode.SUMMARY) {
        items.forEach { subtotalItems += it.subtotal }
        val totalQty = items.sumOf { it.quantity }
        sb.appendLine(leftRightLine("Rekap Pembelian", "$totalQty item (${items.size} jenis)", width))
        sb.appendLine(leftRightLine("Total Belanja", formatNumPlain(subtotalItems), width))
    } else {
        items.forEach { item ->
            subtotalItems += item.subtotal
            val (displayQtyStr, effectiveQtyForReceipt) = resolveDisplayQtyString(item)
            val unitPrice = if (effectiveQtyForReceipt > 0.0) {
                Math.round(item.subtotal / effectiveQtyForReceipt)
            } else {
                item.price
            }
            val matchedProduct = products.find { it.id == item.productId }
            val resolvedItemName = resolveVariantDisplayNameForReceipt(matchedProduct, item.productName, unitPrice)

            when (mode) {
                ReceiptTemplateMode.COMPACT -> {
                    val leftCompact = "${displayQtyStr}x $resolvedItemName"
                    sb.appendLine(leftRightLine(leftCompact, formatNumPlain(item.subtotal), width))
                }
                ReceiptTemplateMode.GIFT_RECEIPT -> {
                    wrapTextLines(resolvedItemName, width).forEach { sb.appendLine(it) }
                    if (item.notes.isNotBlank()) {
                        wrapTextLines("  Cat: ${item.notes}", width).forEach { sb.appendLine(it) }
                    }
                    sb.appendLine(leftRightLine("  Jumlah", "$displayQtyStr item", width))
                }
                else -> {
                    wrapTextLines(resolvedItemName, width).forEach { nameLine ->
                        sb.appendLine(nameLine)
                    }
                    if (item.notes.isNotBlank()) {
                        wrapTextLines("  Cat: ${item.notes}", width).forEach { sb.appendLine(it) }
                    }
                    if (item.serialNumber.isNotBlank()) {
                        wrapTextLines("  SN: ${item.serialNumber}", width).forEach { sb.appendLine(it) }
                    }
                    if (item.assignedStaff.isNotBlank()) {
                        wrapTextLines("  Staf: ${item.assignedStaff}", width).forEach { sb.appendLine(it) }
                    }
                    val qtyPriceStr = "  $displayQtyStr x ${formatNumPlain(unitPrice)}"
                    val itemSubStr = formatNumPlain(item.subtotal)
                    sb.appendLine(leftRightLine(qtyPriceStr, itemSubStr, width))
                }
            }
        }
    }

    // GIFT RECEIPT BERHENTI TANPA NOMINAL HARGA
    if (mode == ReceiptTemplateMode.GIFT_RECEIPT) {
        sb.appendLine(doubleLine)
        sb.appendLine(leftRightLine("Total Barang", "${items.sumOf { it.quantity }} item", width))
        sb.appendLine(centerLine("Tanda Terima Tanpa Harga", width))
        sb.appendLine(doubleLine)
        val footerMsg = storeFooter.ifBlank { "Terima Kasih atas Kunjungan Anda" }
        wrapTextLines(footerMsg, width).forEach { sb.appendLine(centerLine(it, width)) }
        return sb.toString().trimEnd()
    }

    sb.appendLine(dashLine)
    if (mode != ReceiptTemplateMode.COMPACT) {
        sb.appendLine(leftRightLine("Subtotal", formatNumPlain(subtotalItems), width))
    }

    val savedTaxVal = transaction.taxAmount.coerceAtLeast(0L)
    val savedScVal = transaction.serviceCharge.coerceAtLeast(0L)

    val isExclusiveCharges = (savedTaxVal + savedScVal) > 0L &&
        (subtotalItems - (transaction.totalPrice - savedTaxVal - savedScVal)) >= 0L
    val netBeforeExtra = if (isExclusiveCharges) {
        (transaction.totalPrice - savedTaxVal - savedScVal).coerceAtLeast(0L)
    } else {
        transaction.totalPrice
    }

    val totalDiscountSaved = (subtotalItems - netBeforeExtra).coerceAtLeast(0L)
    if (totalDiscountSaved > 0L) {
        val vCode = transaction.voucherCode?.takeIf { it.isNotBlank() }
        val discLabel = if (vCode != null) "Diskon/Voucher ($vCode)" else "Total Diskon"
        sb.appendLine(leftRightLine(discLabel, "-${formatNumPlain(totalDiscountSaved)}", width))
    }

    val netBase = (subtotalItems - totalDiscountSaved).coerceAtLeast(0L)

    if (mode == ReceiptTemplateMode.TAX_INVOICE) {
        val dpp = if (isExclusiveCharges) netBase else (transaction.totalPrice - savedTaxVal).coerceAtLeast(0L)
        sb.appendLine(leftRightLine("Dasar Pengenaan Pajak", formatNumPlain(dpp), width))
        sb.appendLine(leftRightLine("PB1 / PPN", formatNumPlain(savedTaxVal), width))
        if (savedScVal > 0L) {
            sb.appendLine(leftRightLine("Service Charge", formatNumPlain(savedScVal), width))
        }
    } else if (mode != ReceiptTemplateMode.COMPACT) {
        if (isExclusiveCharges) {
            if (savedTaxVal > 0L) {
                val inferredTaxPct = if (netBase > 0L) Math.round((savedTaxVal * 100.0) / netBase).toInt() else 0
                val taxLabel = when {
                    inferredTaxPct == 10 -> "PB1 (10%)"
                    inferredTaxPct > 0 -> "Pajak ($inferredTaxPct%)"
                    else -> "Pajak"
                }
                sb.appendLine(leftRightLine(taxLabel, "+${formatNumPlain(savedTaxVal)}", width))
            }
            if (savedScVal > 0L) {
                val inferredScPct = if (netBase > 0L) Math.round((savedScVal * 100.0) / netBase).toInt() else 0
                val scLabel = if (inferredScPct > 0) "Service ($inferredScPct%)" else "Service"
                sb.appendLine(leftRightLine(scLabel, "+${formatNumPlain(savedScVal)}", width))
            }
        } else if (savedTaxVal > 0L) {
            val baseExcl = (transaction.totalPrice - savedTaxVal).coerceAtLeast(1L)
            val inferredIncPct = Math.round((savedTaxVal * 100.0) / baseExcl).toInt()
            val taxLabel = when {
                inferredIncPct == 10 -> "Termasuk PB1 10%"
                inferredIncPct > 0 -> "Termasuk Pajak $inferredIncPct%"
                else -> "Termasuk Pajak"
            }
            sb.appendLine(leftRightLine(taxLabel, "(${formatNumPlain(savedTaxVal)})", width))
        }
    }

    sb.appendLine(doubleLine)
    sb.appendLine(leftRightLine("TOTAL TAGIHAN", formatNumPlain(transaction.totalPrice), width))
    sb.appendLine(leftRightLine("Metode Bayar", transaction.paymentMethod ?: "CASH", width))

    val isDpOrBonTx = mode == ReceiptTemplateMode.DP_SETTLEMENT ||
        (transaction.paymentMethod ?: "").contains("DP", ignoreCase = true) ||
        (transaction.paymentMethod ?: "").contains("BON", ignoreCase = true) ||
        transaction.status == "UNPAID"

    if (isDpOrBonTx) {
        val paidSoFar = transaction.cashReceived.coerceAtLeast(0L)
        val remAfter = (transaction.totalPrice - paidSoFar).coerceAtLeast(0L)
        val totalItemQty = items.sumOf { it.quantity }
        sb.appendLine(leftRightLine("Waktu Order/DP", dateStr, width))
        sb.appendLine(leftRightLine("Qty Item", "$totalItemQty item", width))
        if (dpLedgerEntries.isNotEmpty()) {
            sb.appendLine(dashLine)
            sb.appendLine("RIWAYAT PEMBAYARAN DP / BON:")
            dpLedgerEntries.forEachIndexed { idx, entry ->
                val eTime = timeFmt.format(Date(entry.timestamp))
                sb.appendLine(leftRightLine("${idx + 1}. ${entry.method}", formatNumPlain(entry.amount), width))
                sb.appendLine("   Waktu: $eTime")
                if (entry.qtySummary.isNotBlank()) {
                    wrapTextLines("   Qty: ${entry.qtySummary}", width).forEach { sb.appendLine(it) }
                }
            }
            sb.appendLine(dashLine)
        }
        sb.appendLine(leftRightLine("Total Terbayar", formatNumPlain(paidSoFar), width))
        sb.appendLine(leftRightLine("Sisa Tagihan", formatNumPlain(remAfter), width))
        val statusLabel = if (remAfter == 0L && transaction.status == "SUCCESS") "LUNAS" else "BELUM LUNAS"
        sb.appendLine(leftRightLine("Status", statusLabel, width))
        if (remAfter == 0L && transaction.status == "SUCCESS") {
            val lunasTs = dpLedgerEntries.lastOrNull()?.timestamp ?: System.currentTimeMillis()
            sb.appendLine(leftRightLine("Waktu Lunas", timeFmt.format(Date(lunasTs)), width))
        }
    } else {
        sb.appendLine(leftRightLine("Bayar", formatNumPlain(transaction.cashReceived), width))
        sb.appendLine(leftRightLine("Kembali", formatNumPlain(transaction.changeAmount), width))
    }

    // KOLOM TANDA TANGAN UNTUK NOTA BESAR / INVOICE A5
    if (mode == ReceiptTemplateMode.INVOICE_A5) {
        sb.appendLine(dashLine)
        sb.appendLine(leftRightLine("Penerima / Pelanggan", "Hormat Kami (Toko)", width))
        sb.appendLine("")
        sb.appendLine("")
        sb.appendLine(leftRightLine("(..................)", "(..................)", width))
    }

    sb.appendLine(doubleLine)

    if (mode != ReceiptTemplateMode.COMPACT) {
        val footerMsg = storeFooter.ifBlank { "Terima Kasih atas Kunjungan Anda" }
        wrapTextLines(footerMsg, width).forEach {
            sb.appendLine(centerLine(it, width))
        }
    }

    return sb.toString().trimEnd()
}

@Composable
fun ReceiptDialog(
    transaction: TransactionEntity,
    viewModel: PosViewModel,
    storeName: String,
    storeAddress: String,
    storePhone: String,
    storeFooter: String,
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val printerManager = remember { BluetoothPrinterManager(context) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    val initialPrinterCfg = remember { PrinterUpgradePrefs.load(context) }
    val autoDetectedMode = remember(transaction.id, transaction.status, transaction.paymentMethod) {
        val isDpBon = (transaction.paymentMethod ?: "").contains("DP", ignoreCase = true) ||
            (transaction.paymentMethod ?: "").contains("BON", ignoreCase = true) ||
            transaction.status == "UNPAID"
        if (isDpBon) ReceiptTemplateMode.DP_SETTLEMENT else initialPrinterCfg.defaultReceiptMode
    }
    var selectedMode by remember { mutableStateOf(autoDetectedMode) }
    var selectedShareFormat by remember { mutableStateOf(initialPrinterCfg.defaultShareFormat) }

    val taxPrefs = remember { context.getSharedPreferences("zokas_tax_ext", Context.MODE_PRIVATE) }
    val isTaxInclusive = remember { taxPrefs.getBoolean("is_tax_inclusive", false) }
    val isCulinaryBusiness = remember(settingsManager.businessType) {
        settingsManager.businessType.equals("KULINER", ignoreCase = true)
    }

    val allProducts by viewModel.products.collectAsStateWithLifecycle()
    val allShifts by viewModel.shifts.collectAsStateWithLifecycle()
    val latestShift = remember(allShifts) { allShifts.firstOrNull() }
    val itemsFlow = remember(transaction.id) { viewModel.getTransactionItems(transaction.id) }
    val itemsNullable by itemsFlow.collectAsStateWithLifecycle(
        initialValue = null as List<TransactionItemEntity>?
    )
    val isItemsLoaded = itemsNullable != null
    val items: List<TransactionItemEntity> = itemsNullable ?: emptyList()

    val dpLedgerEntries = remember(transaction.id, transaction.cashReceived, transaction.status) {
        DpLedgerTracker.getEntries(context, transaction.id)
    }

    var showPrinterError by remember { mutableStateOf(false) }
    var showShareFallbackDialog by remember { mutableStateOf(false) }
    var textToShare by remember { mutableStateOf("") }
    var isGeneratingFile by remember { mutableStateOf(false) }

    val logoFile = remember(settingsManager.storeLogoPath) {
        val path = settingsManager.storeLogoPath
        if (!path.isNullOrBlank()) File(path) else null
    }

    val strukText = remember(
        selectedMode,
        transaction,
        items,
        allProducts,
        dpLedgerEntries,
        latestShift,
        storeName,
        storeAddress,
        storePhone,
        storeFooter,
        initialPrinterCfg.storeTaxNpwp,
        isTaxInclusive,
        isCulinaryBusiness
    ) {
        buildFormattedReceiptByMode(
            mode = selectedMode,
            storeName = storeName,
            storeAddress = storeAddress,
            storePhone = storePhone,
            storeFooter = storeFooter,
            storeTaxNpwp = initialPrinterCfg.storeTaxNpwp,
            transaction = transaction,
            items = items,
            products = allProducts,
            dpLedgerEntries = dpLedgerEntries,
            latestShift = latestShift,
            dateStr = dateFormat.format(Date(transaction.timestamp)),
            taxPercent = settingsManager.taxPercent,
            serviceChargePercent = settingsManager.serviceChargePercent,
            isTaxInclusive = isTaxInclusive,
            isCulinaryBusiness = isCulinaryBusiness
        )
    }

    if (showPrinterError) {
        PrinterErrorDialog { showPrinterError = false }
    }

    if (showShareFallbackDialog) {
        AlertDialog(
            onDismissRequest = { showShareFallbackDialog = false },
            icon = {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            },
            title = { Text("Gagal Membagikan File") },
            text = {
                Text(
                    "Sistem gagal membagikan file. Anda dapat menyalin teks struk di bawah ini dan mengirimkannya secara manual.",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Struk ZoKas", textToShare)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Teks struk disalin ke Clipboard", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal menyalin teks", Toast.LENGTH_SHORT).show()
                        }
                        showShareFallbackDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("SALIN TEKS") }
            },
            dismissButton = {
                TextButton(onClick = { showShareFallbackDialog = false }) {
                    Text("Tutup", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Pilihan Format File Bagikan (JPG HD / PNG / PDF)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReceiptShareFormat.entries.forEach { fmt ->
                        val active = selectedShareFormat == fmt
                        FilterChip(
                            selected = active,
                            onClick = { selectedShareFormat = fmt },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    text = fmt.key,
                                    fontSize = 11.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Struk ZoKas", strukText))
                                Toast.makeText(context, "Teks struk disalin!", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) { }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Salin Teks", modifier = Modifier.size(15.dp))
                    }
                }

                Button(
                    onClick = {
                        if (!isItemsLoaded) return@Button
                        scope.launch {
                            isGeneratingFile = true
                            textToShare = strukText
                            val isWideInvoice = selectedMode == ReceiptTemplateMode.INVOICE_A5

                            val generatedFile = withContext(Dispatchers.IO) {
                                when (selectedShareFormat) {
                                    ReceiptShareFormat.JPG -> generateReceiptImageFile(
                                        context = context,
                                        strukText = strukText,
                                        transactionId = transaction.id,
                                        logoFile = logoFile,
                                        isWideInvoice = isWideInvoice,
                                        asPng = false
                                    )
                                    ReceiptShareFormat.PNG -> generateReceiptImageFile(
                                        context = context,
                                        strukText = strukText,
                                        transactionId = transaction.id,
                                        logoFile = logoFile,
                                        isWideInvoice = isWideInvoice,
                                        asPng = true
                                    )
                                    ReceiptShareFormat.PDF -> generateReceiptPdf(
                                        context = context,
                                        strukText = strukText,
                                        transactionId = transaction.id,
                                        logoFile = logoFile,
                                        isWideInvoice = isWideInvoice
                                    )
                                }
                            }

                            isGeneratingFile = false

                            if (generatedFile != null) {
                                try {
                                    val authority = "${context.packageName}.fileprovider"
                                    val uri: Uri = FileProvider.getUriForFile(context, authority, generatedFile)
                                    val mimeType = when (selectedShareFormat) {
                                        ReceiptShareFormat.JPG -> "image/jpeg"
                                        ReceiptShareFormat.PNG -> "image/png"
                                        ReceiptShareFormat.PDF -> "application/pdf"
                                    }

                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "${selectedMode.titleLabel} #${transaction.id} - $storeName")
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        clipData = ClipData.newRawUri("Receipt", uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        type = mimeType
                                    }

                                    val chooser = Intent.createChooser(shareIntent, "Kirim Struk (${selectedShareFormat.key}) via...")
                                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(chooser)

                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    showShareFallbackDialog = true
                                }
                            } else {
                                Toast.makeText(context, "Gagal membuat file ${selectedShareFormat.key}.", Toast.LENGTH_SHORT).show()
                                showShareFallbackDialog = true
                            }
                        }
                    },
                    enabled = isItemsLoaded && !isGeneratingFile,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isGeneratingFile) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("MEMPROSES ${selectedShareFormat.key}...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = if (selectedShareFormat == ReceiptShareFormat.PDF) Icons.Default.Share else Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("KIRIM STRUK (${selectedShareFormat.key} / WA)", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        val mac = settingsManager.printerMacAddress
                        if (mac == null) {
                            Toast.makeText(context, "Tidak ada printer. Atur di Pengaturan.", Toast.LENGTH_LONG).show()
                        } else {
                            scope.launch {
                                try {
                                    val printerCfg = PrinterUpgradePrefs.load(context)
                                    val printData = generatePrintData(context, strukText, logoFile, printerCfg)
                                    if (printerCfg.autoOpenDrawerOnCash && transaction.paymentMethod.equals("CASH", ignoreCase = true)) {
                                        printerManager.openCashDrawer(mac)
                                    }
                                    val success = printerManager.printData(mac, printData)
                                    if (success) {
                                        Toast.makeText(context, "Struk dicetak!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } else {
                                        showPrinterError = true
                                    }
                                } catch (e: Exception) {
                                    showPrinterError = true
                                }
                            }
                        }
                    },
                    enabled = isItemsLoaded,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CETAK STRUK THERMAL", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tutup Struk", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        title = null,
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Selector Jenis Struk / Nota (Upgrade 1)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReceiptTemplateMode.entries.forEach { modeItem ->
                        val isSelected = selectedMode == modeItem
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMode = modeItem },
                            label = {
                                Text(
                                    text = modeItem.shortLabel,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    color = Color.White,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (logoFile != null && logoFile.exists() && selectedMode != ReceiptTemplateMode.COMPACT) {
                            AsyncImage(
                                model = logoFile,
                                contentDescription = "Store Logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(64.dp).padding(bottom = 8.dp)
                            )
                        }

                        if (!isItemsLoaded) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Memuat rincian...",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        } else {
                            Text(
                                text = strukText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = if (selectedMode == ReceiptTemplateMode.INVOICE_A5) 9.5.sp else 11.sp,
                                lineHeight = 15.sp,
                                color = Color.Black,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    )
}

/**
 * Merender struk menjadi file gambar HD (JPG atau PNG) dengan tinggi dinamis mengikuti jumlah baris
 * sehingga struk panjang tetap tajam dan aman dari OutOfMemoryError saat dibagikan ke WhatsApp.
 */
private fun generateReceiptImageFile(
    context: Context,
    strukText: String,
    transactionId: Long,
    logoFile: File?,
    isWideInvoice: Boolean = false,
    asPng: Boolean = false
): File? {
    val lines = strukText.lines()
    val cols = if (isWideInvoice) INVOICE_WIDTH else RECEIPT_WIDTH

    // Resolusi tinggi HD agar huruf tajam saat di-zoom di WhatsApp namun tetap ringan dikirim
    val baseTextSize = if (isWideInvoice) 24f else 28f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#0F172A")
        textSize = baseTextSize
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        isSubpixelText = true
    }

    val sampleWidth = paint.measureText("W".repeat(cols))
    val paddingX = 48f
    val paddingY = 48f
    val imgWidth = (sampleWidth + paddingX * 2).toInt().coerceIn(720, 1320)
    val lineHeight = (baseTextSize * 1.42f)

    val resolvedLogo = ReceiptLogoHelper.resolveReceiptLogoBitmap(context, logoFile?.absolutePath)
    val logoBlockHeight = if (resolvedLogo != null && resolvedLogo.width > 0 && resolvedLogo.height > 0) 150f else 0f

    // Batasi tinggi maksimum bitmap (max 14000px) agar aman di semua perangkat Android
    val rawHeight = (paddingY * 2 + logoBlockHeight + lines.size * lineHeight + 36f).toInt()
    val maxSafeHeight = 14_000
    val scaleFactor = if (rawHeight > maxSafeHeight) maxSafeHeight.toFloat() / rawHeight.toFloat() else 1f
    val finalW = (imgWidth * scaleFactor).toInt().coerceAtLeast(540)
    val finalH = (rawHeight * scaleFactor).toInt().coerceAtLeast(400)

    return try {
        val bitmap = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (scaleFactor < 1f) {
            canvas.scale(scaleFactor, scaleFactor)
        }

        // Latar kertas putih bersih + bingkai tipis rapi
        canvas.drawColor(AndroidColor.WHITE)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(
            RectF(12f, 12f, imgWidth - 12f, rawHeight - 12f),
            18f,
            18f,
            borderPaint
        )

        var currentY = paddingY + 14f

        if (resolvedLogo != null && resolvedLogo.width > 0 && resolvedLogo.height > 0) {
            val targetH = 116
            val targetW = (resolvedLogo.width * (targetH.toFloat() / resolvedLogo.height))
                .toInt()
                .coerceIn(64, (imgWidth - 120))
            val scaledLogo = Bitmap.createScaledBitmap(resolvedLogo, targetW, targetH, true)
            val left = (imgWidth - scaledLogo.width) / 2f
            canvas.drawBitmap(scaledLogo, left, currentY - 8f, Paint(Paint.FILTER_BITMAP_FLAG))
            currentY += logoBlockHeight
        }

        for ((idx, line) in lines.withIndex()) {
            val trimmed = line.trim()
            val isEmphasis = idx == 0 ||
                trimmed.startsWith("TOTAL TAGIHAN") ||
                trimmed.startsWith("SELISIH LACI") ||
                trimmed.startsWith("[  #") ||
                trimmed == "NOTA TRANSAKSI / INVOICE" ||
                trimmed == "NOTA PEMBAYARAN DP / PELUNASAN" ||
                trimmed == "FAKTUR PAJAK / TAX RECEIPT" ||
                trimmed == "TANDA TERIMA / GIFT RECEIPT" ||
                trimmed == "STRUK LAPORAN SHIFT"

            paint.typeface = Typeface.create(
                Typeface.MONOSPACE,
                if (isEmphasis) Typeface.BOLD else Typeface.NORMAL
            )
            paint.color = if (trimmed.startsWith("TOTAL TAGIHAN")) {
                AndroidColor.parseColor("#065F46")
            } else {
                AndroidColor.parseColor("#0F172A")
            }
            canvas.drawText(line, paddingX, currentY, paint)
            currentY += lineHeight
        }

        val outDir = File(context.getExternalFilesDir(null), "receipts")
        outDir.mkdirs()
        val ext = if (asPng) "png" else "jpg"
        val outFile = File(outDir, "Struk_${transactionId}.$ext")
        FileOutputStream(outFile).use { stream ->
            if (asPng) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            } else {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            }
        }
        bitmap.recycle()
        outFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun generateReceiptPdf(
    context: Context,
    strukText: String,
    transactionId: Long,
    logoFile: File?,
    isWideInvoice: Boolean = false
): File? {
    val document = PdfDocument()
    val lines = strukText.lines()
    val cols = if (isWideInvoice) INVOICE_WIDTH else RECEIPT_WIDTH

    val paint = Paint().apply {
        color = AndroidColor.BLACK
        textSize = if (isWideInvoice) 10.5f else 11.5f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        isAntiAlias = true
    }

    val sampleWidth = paint.measureText("W".repeat(cols))
    val padding = 24f
    val pageWidth = (sampleWidth + (padding * 2)).toInt().coerceAtLeast(if (isWideInvoice) 420 else 280)
    val lineHeight = 16f
    val logoHeight = if (logoFile != null && logoFile.exists()) 95f else 0f
    val pageHeight = ((lines.size * lineHeight) + (padding * 2) + logoHeight + 36).toInt()

    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = document.startPage(pageInfo)

    val canvas = page.canvas
    canvas.drawColor(AndroidColor.WHITE)

    var currentY = padding + 10f

    if (logoFile != null && logoFile.exists()) {
        try {
            val bitmap = BitmapFactory.decodeFile(logoFile.absolutePath)
            if (bitmap != null && bitmap.height > 0) {
                val targetH = 72
                val targetW = (bitmap.width * (targetH.toFloat() / bitmap.height)).toInt().coerceAtMost(pageWidth - 40)
                val scaledLogo = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                val left = (pageWidth - scaledLogo.width) / 2f
                canvas.drawBitmap(scaledLogo, left, currentY - 6f, null)
                currentY += 84f
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    for (line in lines) {
        val isBoldLine = line.startsWith("TOTAL TAGIHAN") ||
            line.trim() == "NOTA TRANSAKSI / INVOICE" ||
            line.trim() == "NOTA PEMBAYARAN DP / PELUNASAN"
        paint.typeface = Typeface.create(
            Typeface.MONOSPACE,
            if (isBoldLine) Typeface.BOLD else Typeface.NORMAL
        )
        canvas.drawText(line, padding, currentY, paint)
        currentY += lineHeight
    }

    document.finishPage(page)

    val pdfDir = File(context.getExternalFilesDir(null), "receipts")
    pdfDir.mkdirs()
    val pdfFile = File(pdfDir, "Struk_${transactionId}.pdf")

    return try {
        val stream = FileOutputStream(pdfFile)
        document.writeTo(stream)
        stream.close()
        document.close()
        pdfFile
    } catch (e: Exception) {
        e.printStackTrace()
        document.close()
        null
    }
}

private fun generatePrintData(
    context: Context,
    strukText: String,
    logoFile: File?,
    cfg: PrinterUpgradeConfig = PrinterUpgradePrefs.load(context)
): ByteArray {
    val output = ByteArrayOutputStream()
    output.write(EscPosCommands.INIT)

    if (cfg.printLogo) {
        val bitmap = ReceiptLogoHelper.resolveReceiptLogoBitmap(context, logoFile?.absolutePath)
        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            try {
                val targetWidth = cfg.paperSize.logoWidthPx
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val newHeight = ((targetWidth / ratio).toInt()).coerceAtLeast(8)
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, newHeight, true)

                output.write(EscPosCommands.ALIGN_CENTER)
                output.write(EscPosCommands.printImage(resizedBitmap))
                output.write(byteArrayOf(0x0A))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val adaptedText = if (cfg.paperSize == PrinterPaperSize.MM_80) {
        strukText.replace("=".repeat(32), "=".repeat(48)).replace("-".repeat(32), "-".repeat(48))
    } else {
        strukText
    }

    val cleanReceiptText = adaptedText.trimEnd()
    output.write(EscPosCommands.ALIGN_LEFT)
    output.write(cleanReceiptText.toByteArray(charset("GBK")))

    val effectiveFeed = cfg.feedLines.coerceIn(0, 3)
    if (effectiveFeed > 0) {
        output.write(byteArrayOf(0x1B, 0x64, effectiveFeed.toByte()))
    }
    if (cfg.autoCutPaper) {
        output.write(EscPosCommands.CUT_PAPER)
    }

    return output.toByteArray()
}
