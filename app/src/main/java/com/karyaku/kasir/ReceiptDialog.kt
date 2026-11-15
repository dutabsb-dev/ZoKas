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
import android.graphics.Typeface
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
    var isGeneratingImage by remember { mutableStateOf(false) }

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
    val diskon = subtotalItems + transaction.taxAmount + transaction.serviceCharge - transaction.totalPrice

    // Rakit text menggunakan Formatter (Wajib rapi)
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
            icon = { Icon(Icons.Default.Share, contentDescription = null, tint = AmberAccent) },
            title = { Text("Gagal Membagikan Struk") },
            text = { Text("Sistem gagal membagikan gambar. Anda dapat menyalin teks struk di bawah ini dan mengirimkannya secara manual via WhatsApp.") },
            confirmButton = {
                Button(onClick = {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Struk ZoKas", textToShare)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Teks struk disalin ke Clipboard", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Gagal menyalin teks", Toast.LENGTH_SHORT).show()
                    }
                    showShareFallbackDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text("SALIN TEKS") }
            },
            dismissButton = { TextButton(onClick = { showShareFallbackDialog = false }) { Text("Tutup") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Column {
                // TOMBOL WHATSAPP (Kirim Gambar + Teks)
                Button(
                    onClick = {
                        if (!isItemsLoaded) return@Button
                        scope.launch {
                            isGeneratingImage = true
                            textToShare = strukText
                            
                            val bitmap = withContext(Dispatchers.IO) {
                                generateReceiptBitmap(strukText)
                            }
                            
                            isGeneratingImage = false

                            if (bitmap != null) {
                                val imagesDir = File(context.getExternalFilesDir(null), "receipts")
                                imagesDir.mkdirs()
                                val file = File(imagesDir, "receipt_${transaction.id}.png")
                                
                                try {
                                    val stream = FileOutputStream(file)
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                    stream.close()

                                    val authority = "${context.packageName}.fileprovider"
                                    val uri: Uri = FileProvider.getUriForFile(context, authority, file)
                                    
                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, textToShare)
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        clipData = ClipData.newRawUri("Receipt", uri) 
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        type = "image/png"
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
                                Toast.makeText(context, "Gagal membuat gambar struk.", Toast.LENGTH_SHORT).show()
                                showShareFallbackDialog = true
                            }
                        }
                    },
                    enabled = isItemsLoaded && !isGeneratingImage,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isGeneratingImage) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("MEMPROSES GAMBAR...", fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("KIRIM STRUK (WHATSAPP)", fontWeight = FontWeight.Bold, color = Color.White)
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
                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CETAK STRUK THERMAL", fontWeight = FontWeight.Bold, color = Color.White)
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
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = EmeraldGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Memuat rincian...", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = SlateGrey)
                    }
                } else {
                    // TAMPILAN STRUK (Menggunakan hasil dari ReceiptFormatter yang sudah di-wrap)
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
// FUNGSI PENDUKUNG UNTUK CETAK DAN SHARE
// =========================================================================

private fun generateReceiptBitmap(strukText: String): Bitmap? {
    return try {
        val width = 600
        val lines = strukText.lines()
        
        // Hitung tinggi bitmap secara dinamis berdasarkan jumlah baris
        // FIX: Mengubah nama variabel dari textSize menjadi fontSize agar tidak bentrok dengan Paint.textSize
        val fontSize = 24f 
        val lineHeight = 30f
        val padding = 50f
        val estimatedHeight = (lines.size * lineHeight) + (padding * 2) + 200f // 200 untuk logo/jaga-jaga
        val height = estimatedHeight.toInt().coerceAtLeast(600)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)

        val paint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = fontSize // FIX: Menggunakan fontSize yang baru
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }

        var y = padding
        val leftX = padding

        for (line in lines) {
            canvas.drawText(line, leftX, y, paint)
            y += lineHeight
        }

        // Potong sisa ruang kosong di bawah
        Bitmap.createBitmap(bitmap, 0, 0, width, y.toInt() + 50)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun generatePrintData(strukText: String, logoFile: File?): ByteArray {
    val output = ByteArrayOutputStream()
    output.write(EscPosCommands.INIT)

    // Cetak Logo jika ada
    if (logoFile != null && logoFile.exists()) {
        try {
            val bitmap = BitmapFactory.decodeFile(logoFile.absolutePath)
            if (bitmap != null) {
                val maxWidth = 384
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val newWidth = maxWidth
                val newHeight = (newWidth / ratio).toInt()
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

                output.write(EscPosCommands.ALIGN_CENTER)
                output.write(EscPosCommands.printImage(resizedBitmap))
                output.write(byteArrayOf(0x0A))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Cetak Teks Struk (Teks ini sudah di-wrap oleh ReceiptFormatter)
    // Gunakan ALIGN_LEFT karena format jarak (padding) sudah diatur di dalam teks.
    output.write(EscPosCommands.ALIGN_LEFT)
    output.write(strukText.toByteArray(charset("GBK"))) 
    
    // Perintah potong kertas
    output.write(EscPosCommands.FEED_3)
    output.write(EscPosCommands.CUT_PAPER)
    
    return output.toByteArray()
}