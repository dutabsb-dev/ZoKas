package com.karyaku.kasir

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.karyaku.kasir.data.local.ProductEntity
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

private fun buildFormattedReceipt32(
    storeName: String,
    storeAddress: String,
    storePhone: String,
    storeFooter: String,
    transaction: TransactionEntity,
    items: List<TransactionItemEntity>,
    products: List<ProductEntity>,
    dateStr: String,
    taxPercent: Double,
    serviceChargePercent: Double,
    isTaxInclusive: Boolean,
    isCulinaryBusiness: Boolean
): String {
    val sb = StringBuilder()
    val dashLine = "-".repeat(RECEIPT_WIDTH)
    val doubleLine = "=".repeat(RECEIPT_WIDTH)

    wrapTextLines(storeName.ifBlank { "ZoKas Store" }.uppercase()).forEach {
        sb.appendLine(centerLine(it))
    }
    if (storeAddress.isNotBlank()) {
        wrapTextLines(storeAddress).forEach { sb.appendLine(centerLine(it)) }
    }
    if (storePhone.isNotBlank()) {
        sb.appendLine(centerLine("Telp: $storePhone"))
    }
    sb.appendLine(doubleLine)

    sb.appendLine(leftRightLine("No. Struk", "#${transaction.id}"))
    sb.appendLine(leftRightLine("Tanggal", dateStr))
    sb.appendLine(leftRightLine("Kasir", transaction.cashierName ?: "Kasir"))
    val customerLabel = transaction.customerName?.takeIf { it.isNotBlank() } ?: "Umum"
    sb.appendLine(leftRightLine("Pelanggan", customerLabel))
    if (isCulinaryBusiness) {
        val orderLabel = buildString {
            val oType = transaction.orderType?.takeIf { it.isNotBlank() } ?: "Dine-In"
            append(oType)
            if (transaction.tableName.isNotBlank() && oType == "Dine-In") {
                append(" (${transaction.tableName})")
            }
        }
        sb.appendLine(leftRightLine("Tipe", orderLabel))
    }
    sb.appendLine(dashLine)

    var subtotalItems = 0L
    items.forEach { item ->
        subtotalItems += item.subtotal
        val unitPrice = if (item.quantity > 0) item.subtotal / item.quantity else item.price
        val matchedProduct = products.find { it.id == item.productId }
        val resolvedItemName = resolveVariantDisplayNameForReceipt(matchedProduct, item.productName, unitPrice)
        wrapTextLines(resolvedItemName, RECEIPT_WIDTH).forEach { nameLine ->
            sb.appendLine(nameLine)
        }
        if (item.notes.isNotBlank()) {
            wrapTextLines("  Cat: ${item.notes}", RECEIPT_WIDTH).forEach { sb.appendLine(it) }
        }
        if (item.serialNumber.isNotBlank()) {
            wrapTextLines("  SN: ${item.serialNumber}", RECEIPT_WIDTH).forEach { sb.appendLine(it) }
        }
        if (item.assignedStaff.isNotBlank()) {
            wrapTextLines("  Staf: ${item.assignedStaff}", RECEIPT_WIDTH).forEach { sb.appendLine(it) }
        }
        val qtyPriceStr = "  ${item.quantity} x ${formatNumPlain(unitPrice)}"
        val itemSubStr = formatNumPlain(item.subtotal)
        sb.appendLine(leftRightLine(qtyPriceStr, itemSubStr))
    }

    sb.appendLine(dashLine)
    sb.appendLine(leftRightLine("Subtotal", formatNumPlain(subtotalItems)))

    val effectiveTaxRate = if (isTaxInclusive) 0.0 else taxPercent
    val totalExtraRate = (effectiveTaxRate + serviceChargePercent) / 100.0
    val estimatedNetBeforeTax = if (totalExtraRate > 0.0) {
        Math.round(transaction.totalPrice / (1.0 + totalExtraRate))
    } else {
        transaction.totalPrice
    }

    val totalDiscountSaved = (subtotalItems - estimatedNetBeforeTax).coerceAtLeast(0L)
    if (totalDiscountSaved > 0L) {
        val vCode = transaction.voucherCode?.takeIf { it.isNotBlank() }
        val discLabel = if (vCode != null) "Diskon/Voucher ($vCode)" else "Total Diskon"
        sb.appendLine(leftRightLine(discLabel, "-${formatNumPlain(totalDiscountSaved)}"))
    }

    val netBase = (subtotalItems - totalDiscountSaved).coerceAtLeast(0L)
    val diffCharges = (transaction.totalPrice - netBase).coerceAtLeast(0L)

    if (diffCharges > 0L) {
        val totalRate = effectiveTaxRate + serviceChargePercent
        val scVal = if (serviceChargePercent > 0.0 && totalRate > 0.0) {
            Math.round(diffCharges * (serviceChargePercent / totalRate))
        } else 0L
        val taxVal = (diffCharges - scVal).coerceAtLeast(0L)

        if (taxVal > 0L) {
            val taxLabel = if (taxPercent == 10.0) "PB1 (10%)" else if (taxPercent > 0.0) "Pajak (${taxPercent.toInt()}%)" else "Pajak"
            sb.appendLine(leftRightLine(taxLabel, "+${formatNumPlain(taxVal)}"))
        }
        if (scVal > 0L) {
            sb.appendLine(leftRightLine("Service (${serviceChargePercent.toInt()}%)", "+${formatNumPlain(scVal)}"))
        }
    }

    if (isTaxInclusive && taxPercent > 0.0 && transaction.totalPrice > 0L) {
        val includedTax = (transaction.totalPrice - (transaction.totalPrice / (1.0 + (taxPercent / 100.0)))).toLong()
        if (includedTax > 0L) {
            val taxLabel = if (taxPercent == 10.0) "Termasuk PB1 10%" else "Termasuk Pajak ${taxPercent.toInt()}%"
            sb.appendLine(leftRightLine(taxLabel, "(${formatNumPlain(includedTax)})"))
        }
    }

    sb.appendLine(doubleLine)
    sb.appendLine(leftRightLine("TOTAL TAGIHAN", formatNumPlain(transaction.totalPrice)))
    sb.appendLine(leftRightLine("Metode Bayar", transaction.paymentMethod ?: "CASH"))
    sb.appendLine(leftRightLine("Bayar", formatNumPlain(transaction.cashReceived)))
    sb.appendLine(leftRightLine("Kembali", formatNumPlain(transaction.changeAmount)))
    sb.appendLine(doubleLine)

    val footerMsg = storeFooter.ifBlank { "Terima Kasih atas Kunjungan Anda" }
    wrapTextLines(footerMsg).forEach {
        sb.appendLine(centerLine(it))
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

    val taxPrefs = remember { context.getSharedPreferences("zokas_tax_ext", Context.MODE_PRIVATE) }
    val isTaxInclusive = remember { taxPrefs.getBoolean("is_tax_inclusive", false) }
    val isCulinaryBusiness = remember(settingsManager.businessType) {
        settingsManager.businessType.equals("KULINER", ignoreCase = true)
    }

    val allProducts by viewModel.products.collectAsStateWithLifecycle()
    val itemsFlow = remember(transaction.id) { viewModel.getTransactionItems(transaction.id) }
    val itemsNullable by itemsFlow.collectAsStateWithLifecycle(
        initialValue = null as List<TransactionItemEntity>?
    )
    val isItemsLoaded = itemsNullable != null
    val items: List<TransactionItemEntity> = itemsNullable ?: emptyList()

    var showPrinterError by remember { mutableStateOf(false) }
    var showShareFallbackDialog by remember { mutableStateOf(false) }
    var textToShare by remember { mutableStateOf("") }
    var isGeneratingFile by remember { mutableStateOf(false) }

    val logoFile = remember(settingsManager.storeLogoPath) {
        val path = settingsManager.storeLogoPath
        if (!path.isNullOrBlank()) File(path) else null
    }

    val strukText = remember(
        transaction,
        items,
        allProducts,
        storeName,
        storeAddress,
        storePhone,
        storeFooter,
        isTaxInclusive,
        isCulinaryBusiness
    ) {
        buildFormattedReceipt32(
            storeName = storeName,
            storeAddress = storeAddress,
            storePhone = storePhone,
            storeFooter = storeFooter,
            transaction = transaction,
            items = items,
            products = allProducts,
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
                    "Sistem gagal membagikan PDF. Anda dapat menyalin teks struk di bawah ini dan mengirimkannya secara manual.",
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
                Button(
                    onClick = {
                        if (!isItemsLoaded) return@Button
                        scope.launch {
                            isGeneratingFile = true
                            textToShare = strukText

                            val pdfFile = withContext(Dispatchers.IO) {
                                generateReceiptPdf(context, strukText, transaction.id, logoFile)
                            }

                            isGeneratingFile = false

                            if (pdfFile != null) {
                                try {
                                    val authority = "${context.packageName}.fileprovider"
                                    val uri: Uri = FileProvider.getUriForFile(context, authority, pdfFile)

                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Struk Pembayaran #${transaction.id} - $storeName")
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        clipData = ClipData.newRawUri("Receipt", uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        type = "application/pdf"
                                    }

                                    val chooser = Intent.createChooser(shareIntent, "Kirim Struk via...")
                                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(chooser)

                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    showShareFallbackDialog = true
                                }
                            } else {
                                Toast.makeText(context, "Gagal membuat file PDF.", Toast.LENGTH_SHORT).show()
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
                        Text("MEMPROSES PDF...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("KIRIM STRUK (PDF / WA)", fontWeight = FontWeight.Bold)
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
                                    val printData = generatePrintData(context, strukText, logoFile)
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
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
                    val previewLogoBitmap = remember(logoFile) {
                        ReceiptLogoHelper.resolveReceiptLogoBitmap(context, logoFile?.absolutePath)
                    }
                    if (previewLogoBitmap != null) {
                        Image(
                            bitmap = previewLogoBitmap.asImageBitmap(),
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
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = Color.Black,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    )
}

private fun generateReceiptPdf(context: Context, strukText: String, transactionId: Long, logoFile: File?): File? {
    val document = PdfDocument()
    val lines = strukText.lines()

    val paint = Paint().apply {
        color = AndroidColor.BLACK
        textSize = 11.5f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        isAntiAlias = true
    }

    val sampleWidth = paint.measureText("W".repeat(RECEIPT_WIDTH))
    val padding = 22f
    val pageWidth = (sampleWidth + (padding * 2)).toInt().coerceAtLeast(280)
    val lineHeight = 16f
    val resolvedPdfLogo = ReceiptLogoHelper.resolveReceiptLogoBitmap(context, logoFile?.absolutePath)
    val logoHeight = if (resolvedPdfLogo != null) 95f else 0f
    val pageHeight = ((lines.size * lineHeight) + (padding * 2) + logoHeight + 36).toInt()

    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = document.startPage(pageInfo)

    val canvas = page.canvas
    canvas.drawColor(AndroidColor.WHITE)

    var currentY = padding + 10f

    if (resolvedPdfLogo != null) {
        try {
            if (resolvedPdfLogo.height > 0) {
                val targetH = 72
                val targetW = (resolvedPdfLogo.width * (targetH.toFloat() / resolvedPdfLogo.height)).toInt().coerceAtMost(pageWidth - 40)
                val scaledLogo = android.graphics.Bitmap.createScaledBitmap(resolvedPdfLogo, targetW, targetH, true)
                val left = (pageWidth - scaledLogo.width) / 2f
                canvas.drawBitmap(scaledLogo, left, currentY - 6f, null)
                currentY += 84f
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    for (line in lines) {
        val isBoldLine = line.startsWith("TOTAL TAGIHAN")
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

private fun generatePrintData(context: Context, strukText: String, logoFile: File?): ByteArray {
    val output = ByteArrayOutputStream()
    output.write(EscPosCommands.INIT)

    val bitmap = ReceiptLogoHelper.resolveReceiptLogoBitmap(context, logoFile?.absolutePath)
    if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
        try {
            val targetWidth = 192
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val newHeight = ((targetWidth / ratio).toInt()).coerceAtLeast(8)
            val resizedBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, targetWidth, newHeight, true)

            output.write(EscPosCommands.ALIGN_CENTER)
            output.write(EscPosCommands.printImage(resizedBitmap))
            output.write(byteArrayOf(0x0A))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    output.write(EscPosCommands.ALIGN_LEFT)
    output.write(strukText.toByteArray(charset("GBK")))
    output.write(byteArrayOf(0x0A))

    output.write(EscPosCommands.FEED_3)
    output.write(EscPosCommands.CUT_PAPER)

    return output.toByteArray()
}
