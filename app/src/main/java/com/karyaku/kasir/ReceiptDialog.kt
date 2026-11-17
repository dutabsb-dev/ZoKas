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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.karyaku.kasir.data.local.TransactionEntity
import com.karyaku.kasir.data.local.TransactionItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

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

    // --- MERAKIT TEKS STRUK YANG RAPI ---
    val receiptItems = items.map { item ->
        val variationText = buildString {
            if (item.notes.isNotEmpty()) append("${item.notes} | ")
            if (item.serialNumber.isNotEmpty()) append("SN: ${item.serialNumber} | ")
            if (item.assignedStaff.isNotEmpty()) append("Staf: ${item.assignedStaff}")
        }.trimEnd(' ', '|')

        ReceiptItem(
            name = item.productName,
            qty = item.quantity,
            price = if (item.quantity > 0) item.subtotal.toDouble() / item.quantity else 0.0,
            variation = variationText
        )
    }

    val subtotalItems = items.sumOf { it.subtotal }
    
    val strukText = ReceiptFormatter.buildReceipt(
        storeName = storeName,
        storeAddress = storeAddress,
        storePhone = storePhone,
        storeFooter = storeFooter,
        transactionId = transaction.id,
        dateStr = dateFormat.format(Date(transaction.timestamp)),
        orderType = transaction.orderType ?: "Dine-In",
        cashierName = transaction.cashierName ?: "Kasir",
        customerName = transaction.customerName ?: "Pelanggan Umum",
        items = receiptItems,
        subtotal = subtotalItems.toDouble(),
        total = transaction.totalPrice.toDouble(),
        payment = transaction.cashReceived.toDouble(),
        change = transaction.changeAmount.toDouble()
    )

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
            Column {
                // TOMBOL WHATSAPP (Kirim File PDF)
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
                                        putExtra(Intent.EXTRA_TEXT, "Struk Pembayaran #${transaction.id}")
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
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("KIRIM STRUK (PDF)", fontWeight = FontWeight.Bold)
                    }
                }

                // TOMBOL CETAK THERMAL
                Button(
                    onClick = {
                        val mac = settingsManager.printerMacAddress
                        if (mac == null) {
                            Toast.makeText(context, "Tidak ada printer. Atur di Pengaturan.", Toast.LENGTH_LONG).show()
                        } else {
                            scope.launch {
                                try {
                                    val printData = generatePrintData(strukText, logoFile)
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
                    Text("CETAK STRUK THERMAL", fontWeight = FontWeight.Bold)
                }
            }
        },
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (logoFile != null && logoFile.exists()) {
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
                            color = Color(0xFF64748B)  // Di atas kertas putih, SlateGrey aman
                        )
                    }
                } else {
                    // TAMPILAN STRUK — teks hitam di atas kertas putih, TIDAK diubah
                    Text(
                        text = strukText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color.Black,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    )
}

// =========================================================================
// FUNGSI PENDUKUNG UNTUK MEMBUAT FILE PDF
// =========================================================================
private fun generateReceiptPdf(context: Context, strukText: String, transactionId: Long, logoFile: File?): File? {
    val document = PdfDocument()
    val lines = strukText.lines()
    
    val pageWidth = 350
    val fontSize = 12f
    val lineHeight = 16f
    val padding = 20f
    val logoHeight = if (logoFile != null && logoFile.exists()) 100f else 0f
    
    val pageHeight = ((lines.size * lineHeight) + (padding * 2) + logoHeight + 50).toInt()

    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = document.startPage(pageInfo)

    val canvas = page.canvas
    val paint = Paint().apply {
        color = AndroidColor.BLACK
        textSize = fontSize
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }

    var currentY = padding

    if (logoFile != null && logoFile.exists()) {
        try {
            val bitmap = BitmapFactory.decodeFile(logoFile.absolutePath)
            if (bitmap != null) {
                val scaledLogo = android.graphics.Bitmap.createScaledBitmap(
                    bitmap, 
                    (bitmap.width * (80f / bitmap.height)).toInt(), 
                    80, 
                    true
                )
                val left = (pageWidth - scaledLogo.width) / 2f
                canvas.drawBitmap(scaledLogo, left, currentY, null)
                currentY += 90f
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    for (line in lines) {
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

// =========================================================================
// FUNGSI PENDUKUNG UNTUK CETAK BLUETOOTH
// =========================================================================
private fun generatePrintData(strukText: String, logoFile: File?): ByteArray {
    val output = ByteArrayOutputStream()
    output.write(EscPosCommands.INIT)

    if (logoFile != null && logoFile.exists()) {
        try {
            val bitmap = BitmapFactory.decodeFile(logoFile.absolutePath)
            if (bitmap != null) {
                val maxWidth = 384
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val newWidth = maxWidth
                val newHeight = (newWidth / ratio).toInt()
                val resizedBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

                output.write(EscPosCommands.ALIGN_CENTER)
                output.write(EscPosCommands.printImage(resizedBitmap))
                output.write(byteArrayOf(0x0A))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    output.write(EscPosCommands.ALIGN_LEFT)
    output.write(strukText.toByteArray(charset("GBK"))) 
    
    output.write(EscPosCommands.FEED_3)
    output.write(EscPosCommands.CUT_PAPER)
    
    return output.toByteArray()
}