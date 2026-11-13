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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    // State eksplisit untuk loading items
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

    // Ambil logo dari SettingsManager
    val logoFile = remember(settingsManager.storeLogoPath) {
        val path = settingsManager.storeLogoPath
        if (!path.isNullOrBlank()) File(path) else null
    }

    if (showPrinterError) {
        PrinterErrorDialog { showPrinterError = false }
    }

    if (showShareFallbackDialog) {
        AlertDialog(
            onDismissRequest = { showShareFallbackDialog = false },
            icon = { Icon(Icons.Default.Share, contentDescription = null, tint = AmberAccent) },
            title = { Text("Aplikasi Tidak Ditemukan") },
            text = { Text("WhatsApp tidak terpasang atau tidak dapat diakses. Anda dapat menyalin teks struk dan mengirimkannya secara manual.") },
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
                            textToShare = generateTextReceipt(transaction, storeName, storeAddress, storePhone, storeFooter, dateFormat, items)
                            
                            // Generate gambar struk di background thread
                            val bitmap = withContext(Dispatchers.IO) {
                                generateReceiptBitmap(
                                    transaction = transaction,
                                    storeName = storeName,
                                    storeAddress = storeAddress,
                                    storePhone = storePhone,
                                    storeFooter = storeFooter,
                                    dateFormat = dateFormat,
                                    items = items,
                                    logoFile = logoFile
                                )
                            }
                            
                            isGeneratingImage = false

                            if (bitmap != null) {
                                // Simpan bitmap ke cache
                                val cachePath = File(context.cacheDir, "images")
                                cachePath.mkdirs()
                                val file = File(cachePath, "receipt_${transaction.id}.png")
                                try {
                                    val stream = FileOutputStream(file)
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                    stream.close()

                                    // Gunakan FileProvider untuk keamanan Android 7+
                                    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    
                                    // Intent kirim gambar + teks (caption)
                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, textToShare)
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        type = "image/png"
                                    }
                                    
                                    // Coba buka WhatsApp / WA Business / System Chooser
                                    try {
                                        val waIntent = Intent(shareIntent).apply { setPackage("com.whatsapp") }
                                        context.startActivity(waIntent)
                                    } catch (e: Exception) {
                                        try {
                                            val waBizIntent = Intent(shareIntent).apply { setPackage("com.whatsapp.w4b") }
                                            context.startActivity(waBizIntent)
                                        } catch (e2: Exception) {
                                            // Jika WA tidak ada, buka share sheet bawaan Android
                                            val chooser = Intent.createChooser(shareIntent, "Kirim Struk via...")
                                            context.startActivity(chooser)
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    showShareFallbackDialog = true
                                }
                            } else {
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
                                    val printData = generatePrintData(transaction, storeName, storeAddress, storePhone, storeFooter, dateFormat, items, logoFile)
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
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {

                // TAMPILKAN LOGO TOKO DI PREVIEW
                if (logoFile != null && logoFile.exists()) {
                    AsyncImage(
                        model = logoFile,
                        contentDescription = "Store Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(64.dp).padding(bottom = 8.dp)
                    )
                }

                Text(text = storeName, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                Text(text = storeAddress, fontFamily = FontFamily.Monospace, fontSize = 10.sp, textAlign = TextAlign.Center)
                Text(text = "Telp: $storePhone", fontFamily = FontFamily.Monospace, fontSize = 10.sp, textAlign = TextAlign.Center)

                Text(text = "=========================", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text(text = "ID: #${transaction.id} | ${dateFormat.format(Date(transaction.timestamp))}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text(text = "Tipe: ${transaction.orderType}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (transaction.pickupDate > 0) {
                    Text(text = "Ambil: ${dateFormat.format(Date(transaction.pickupDate))}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.Red)
                }
                Text(text = "Kasir: ${transaction.cashierName} | ${transaction.tableName}", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                Text(text = "Pelanggan: ${transaction.customerName}", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                Text(text = "=========================", fontFamily = FontFamily.Monospace, fontSize = 12.sp)

                when {
                    !isItemsLoaded -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = EmeraldGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Memuat rincian...", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = SlateGrey)
                        }
                    }
                    items.isEmpty() -> {
                        Text("Tidak ada item tercatat.", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = SlateGrey)
                    }
                    else -> {
                        items.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${item.quantity}x ${item.productName}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                Text(formatRupiah(item.subtotal), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                            if (item.notes.isNotEmpty()) {
                                Text("  *Cat: ${item.notes}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = SlateGreyDark)
                            }
                            if (item.serialNumber.isNotEmpty()) {
                                Text("  *SN: ${item.serialNumber}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Blue)
                            }
                            if (item.assignedStaff.isNotEmpty()) {
                                Text("  *Staf: ${item.assignedStaff}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Magenta)
                            }
                        }
                        Text(text = "-------------------------", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }

                if (isItemsLoaded) {
                    val subtotalItems = items.sumOf { it.subtotal }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subtotal", fontFamily = FontFamily.Monospace)
                        Text(formatRupiah(subtotalItems), fontFamily = FontFamily.Monospace)
                    }

                    if (transaction.taxAmount > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pajak", fontFamily = FontFamily.Monospace)
                            Text(formatRupiah(transaction.taxAmount), fontFamily = FontFamily.Monospace)
                        }
                    }

                    if (transaction.serviceCharge > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Service Charge", fontFamily = FontFamily.Monospace)
                            Text(formatRupiah(transaction.serviceCharge), fontFamily = FontFamily.Monospace)
                        }
                    }

                    val diskon = subtotalItems + transaction.taxAmount + transaction.serviceCharge - transaction.totalPrice
                    if (diskon > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Diskon / Poin", fontFamily = FontFamily.Monospace)
                            Text("- ${formatRupiah(diskon)}", fontFamily = FontFamily.Monospace)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Belanja", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(formatRupiah(transaction.totalPrice), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    if (transaction.paymentMethod == "BON" && transaction.cashReceived == 0L) {
                        Text("METODE: BON (BELUM BAYAR)", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = ErrorRed)
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tunai", fontFamily = FontFamily.Monospace)
                            Text(formatRupiah(transaction.cashReceived), fontFamily = FontFamily.Monospace)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Kembalian", fontFamily = FontFamily.Monospace)
                            Text(formatRupiah(transaction.changeAmount), fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "=========================", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text(text = storeFooter, fontFamily = FontFamily.Monospace, fontSize = 10.sp, textAlign = TextAlign.Center)
            }
        }
    )
}

// ================================================================
// HELPER: GENERATE TEXT (Untuk Caption WhatsApp)
// ================================================================
private fun generateTextReceipt(
    transaction: TransactionEntity,
    storeName: String, storeAddress: String, storePhone: String, storeFooter: String,
    dateFormat: SimpleDateFormat, items: List<TransactionItemEntity>?
): String {
    val sb = StringBuilder()
    sb.append("*$storeName*\n")
    sb.append("$storeAddress\n")
    sb.append("Telp: $storePhone\n")
    sb.append("------------------------\n")
    sb.append("ID: #${transaction.id}\n")
    sb.append("Tgl: ${dateFormat.format(Date(transaction.timestamp))}\n")
    sb.append("Tipe: ${transaction.orderType}\n")
    if (transaction.pickupDate > 0) {
        sb.append("Ambil: ${dateFormat.format(Date(transaction.pickupDate))}\n")
    }
    sb.append("Kasir: ${transaction.cashierName}\n")
    sb.append("Pelanggan: ${transaction.customerName}\n")
    sb.append("------------------------\n")

    items?.forEach {
        sb.append("${it.quantity}x ${it.productName}\n")
        if (it.notes.isNotEmpty()) { sb.append("   *Cat: ${it.notes}*\n") }
        if (it.serialNumber.isNotEmpty()) { sb.append("   *SN: ${it.serialNumber}*\n") }
        if (it.assignedStaff.isNotEmpty()) { sb.append("   *Staf: ${it.assignedStaff}*\n") }
        sb.append("   Rp ${it.subtotal}\n")
    }
    sb.append("------------------------\n")

    val subtotal = items?.sumOf { it.subtotal } ?: transaction.totalPrice
    sb.append("Subtotal: Rp $subtotal\n")

    if (transaction.taxAmount > 0) sb.append("Pajak: Rp ${transaction.taxAmount}\n")
    if (transaction.serviceCharge > 0) sb.append("Service: Rp ${transaction.serviceCharge}\n")

    val diskon = subtotal + transaction.taxAmount + transaction.serviceCharge - transaction.totalPrice
    if (diskon > 0) sb.append("Diskon: -Rp $diskon\n")

    sb.append("*Total: Rp ${transaction.totalPrice}*\n")

    if (transaction.paymentMethod == "BON" && transaction.cashReceived == 0L) {
        sb.append("*STATUS: BON (BELUM BAYAR)*\n")
    } else {
        sb.append("Tunai: Rp ${transaction.cashReceived}\n")
        sb.append("Kembali: Rp ${transaction.changeAmount}\n")
    }
    sb.append("------------------------\n")
    sb.append("$storeFooter\n")
    return sb.toString()
}

// ================================================================
// HELPER: GENERATE BITMAP (Untuk Gambar WhatsApp)
// ================================================================
private fun generateReceiptBitmap(
    transaction: TransactionEntity,
    storeName: String, storeAddress: String, storePhone: String, storeFooter: String,
    dateFormat: SimpleDateFormat, items: List<TransactionItemEntity>?, logoFile: File?
): Bitmap? {
    return try {
        val width = 600
        var height = 200 // Default height, akan ditambah sesuai isi
        
        // Hitung tinggi total (estimasi kasar)
        val lineHeight = 30f
        val textSize = 24f
        height += (items?.size ?: 0) * 60
        height += 400 // Ruang untuk header & footer

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)

        val paint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = textSize
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
        val boldPaint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = textSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        val centerPaint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = textSize
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val centerBoldPaint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = textSize + 10f // Lebih besar untuk judul
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        var y = 50f
        val centerX = width / 2f
        val leftX = 40f
        val rightX = width - 40f

        // Gambar Logo
        if (logoFile != null && logoFile.exists()) {
            val logoBitmap = BitmapFactory.decodeFile(logoFile.absolutePath)
            if (logoBitmap != null) {
                // Resize logo
                val logoWidth = 120
                val logoHeight = (logoBitmap.height.toFloat() / logoBitmap.width.toFloat() * logoWidth).toInt()
                val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, logoWidth, logoHeight, true)
                
                // Taruh di tengah
                val left = (width - logoWidth) / 2f
                canvas.drawBitmap(scaledLogo, left, y, null)
                y += logoHeight + 20f
            }
        }

        canvas.drawText(storeName, centerX, y, centerBoldPaint); y += 35f
        canvas.drawText(storeAddress, centerX, y, centerPaint); y += 25f
        canvas.drawText("Telp: $storePhone", centerX, y, centerPaint); y += 35f

        val line = "=============================================="
        canvas.drawText(line, centerX, y, centerPaint); y += 30f

        // Info Transaksi
        val dateStr = dateFormat.format(Date(transaction.timestamp))
        canvas.drawText("ID: #${transaction.id} | $dateStr", leftX, y, paint); y += 30f
        canvas.drawText("Tipe: ${transaction.orderType}", leftX, y, boldPaint); y += 30f
        if (transaction.pickupDate > 0) {
            canvas.drawText("Ambil: ${dateFormat.format(Date(transaction.pickupDate))}", leftX, y, paint); y += 30f
        }
        canvas.drawText("Kasir: ${transaction.cashierName} | ${transaction.tableName}", leftX, y, paint); y += 30f
        canvas.drawText("Pelanggan: ${transaction.customerName}", leftX, y, paint); y += 30f

        canvas.drawText(line, centerX, y, centerPaint); y += 30f

        // Items
        items?.forEach { item ->
            val itemName = "${item.quantity}x ${item.productName}"
            val itemPrice = formatRupiah(item.subtotal)
            
            canvas.drawText(itemName, leftX, y, paint)
            val priceWidth = paint.measureText(itemPrice)
            canvas.drawText(itemPrice, rightX - priceWidth, y, paint)
            y += 30f

            if (item.notes.isNotEmpty()) {
                canvas.drawText("  *Cat: ${item.notes}", leftX, y, paint); y += 25f
            }
            if (item.serialNumber.isNotEmpty()) {
                canvas.drawText("  *SN: ${item.serialNumber}", leftX, y, paint); y += 25f
            }
            if (item.assignedStaff.isNotEmpty()) {
                canvas.drawText("  *Staf: ${item.assignedStaff}", leftX, y, paint); y += 25f
            }
        }

        val dashLine = "----------------------------------------------"
        canvas.drawText(dashLine, centerX, y, centerPaint); y += 30f

        // Ringkasan
        val subtotalItems = items?.sumOf { it.subtotal } ?: transaction.totalPrice
        canvas.drawText("Subtotal:", leftX, y, paint)
        var valStr = formatRupiah(subtotalItems)
        var valWidth = paint.measureText(valStr)
        canvas.drawText(valStr, rightX - valWidth, y, paint); y += 30f

        if (transaction.taxAmount > 0) {
            canvas.drawText("Pajak:", leftX, y, paint)
            valStr = formatRupiah(transaction.taxAmount)
            valWidth = paint.measureText(valStr)
            canvas.drawText(valStr, rightX - valWidth, y, paint); y += 30f
        }
        if (transaction.serviceCharge > 0) {
            canvas.drawText("Service Charge:", leftX, y, paint)
            valStr = formatRupiah(transaction.serviceCharge)
            valWidth = paint.measureText(valStr)
            canvas.drawText(valStr, rightX - valWidth, y, paint); y += 30f
        }

        val diskon = subtotalItems + transaction.taxAmount + transaction.serviceCharge - transaction.totalPrice
        if (diskon > 0) {
            canvas.drawText("Diskon/Poin:", leftX, y, paint)
            valStr = "- ${formatRupiah(diskon)}"
            valWidth = paint.measureText(valStr)
            canvas.drawText(valStr, rightX - valWidth, y, paint); y += 30f
        }

        y += 10f
        canvas.drawText("Total Belanja:", leftX, y, boldPaint)
        valStr = formatRupiah(transaction.totalPrice)
        valWidth = boldPaint.measureText(valStr)
        canvas.drawText(valStr, rightX - valWidth, y, boldPaint); y += 35f

        if (transaction.paymentMethod == "BON" && transaction.cashReceived == 0L) {
            canvas.drawText("METODE: BON (BELUM BAYAR)", leftX, y, boldPaint); y += 30f
        } else {
            canvas.drawText("Tunai:", leftX, y, paint)
            valStr = formatRupiah(transaction.cashReceived)
            valWidth = paint.measureText(valStr)
            canvas.drawText(valStr, rightX - valWidth, y, paint); y += 30f

            canvas.drawText("Kembalian:", leftX, y, paint)
            valStr = formatRupiah(transaction.changeAmount)
            valWidth = paint.measureText(valStr)
            canvas.drawText(valStr, rightX - valWidth, y, paint); y += 30f
        }

        y += 20f
        canvas.drawText(line, centerX, y, centerPaint); y += 30f
        canvas.drawText(storeFooter, centerX, y, centerPaint)

        // Crop bitmap jika ada ruang kosong berlebih di bawah
        Bitmap.createBitmap(bitmap, 0, 0, width, (y + 50).toInt())
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// ================================================================
// HELPER: GENERATE PRINT DATA (Untuk Bluetooth Thermal)
// ================================================================
private fun generatePrintData(
    transaction: TransactionEntity,
    storeName: String, storeAddress: String, storePhone: String, storeFooter: String,
    dateFormat: SimpleDateFormat, items: List<TransactionItemEntity>?, logoFile: File?
): ByteArray {
    val output = ByteArrayOutputStream()
    output.write(EscPosCommands.INIT)

    // --- FITUR CETAK LOGO ---
    if (logoFile != null && logoFile.exists()) {
        try {
            val bitmap = BitmapFactory.decodeFile(logoFile.absolutePath)
            if (bitmap != null) {
                // Resize agar pas di kertas thermal (lebar max 384px)
                val maxWidth = 384
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val newWidth = maxWidth
                val newHeight = (newWidth / ratio).toInt()
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

                output.write(EscPosCommands.ALIGN_CENTER)
                output.write(EscPosCommands.printImage(resizedBitmap))
                output.write(byteArrayOf(0x0A)) // Line feed setelah gambar
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    output.write(EscPosCommands.ALIGN_CENTER)
    output.write(EscPosCommands.BOLD_ON)
    output.write("$storeName\n".toByteArray())
    output.write(EscPosCommands.BOLD_OFF)
    output.write("$storeAddress\nTelp: $storePhone\n".toByteArray())
    output.write("================================\n".toByteArray())
    output.write("ID: #${transaction.id}\n".toByteArray())
    output.write("${dateFormat.format(Date(transaction.timestamp))}\n".toByteArray())
    output.write("Tipe: ${transaction.orderType}\n".toByteArray())
    if (transaction.pickupDate > 0) {
        output.write("Ambil: ${dateFormat.format(Date(transaction.pickupDate))}\n".toByteArray())
    }
    output.write("Kasir: ${transaction.cashierName} | ${transaction.tableName}\n".toByteArray())
    output.write("Pelanggan: ${transaction.customerName}\n".toByteArray())
    output.write("================================\n".toByteArray())
    output.write(EscPosCommands.ALIGN_LEFT)

    items?.forEach {
        output.write("${it.quantity}x ${it.productName}\n".toByteArray())
        if (it.notes.isNotEmpty()) { output.write("  *Cat: ${it.notes}\n".toByteArray()) }
        if (it.serialNumber.isNotEmpty()) { output.write("  *SN: ${it.serialNumber}\n".toByteArray()) }
        if (it.assignedStaff.isNotEmpty()) { output.write("  *Staf: ${it.assignedStaff}\n".toByteArray()) }
        output.write("  Rp ${it.subtotal}\n".toByteArray())
    }
    output.write("--------------------------------\n".toByteArray())

    val subtotal = items?.sumOf { it.subtotal } ?: transaction.totalPrice
    output.write("Subtotal: Rp $subtotal\n".toByteArray())
    if (transaction.taxAmount > 0) output.write("Pajak   : Rp ${transaction.taxAmount}\n".toByteArray())
    if (transaction.serviceCharge > 0) output.write("Service : Rp ${transaction.serviceCharge}\n".toByteArray())

    val diskon = subtotal + transaction.taxAmount + transaction.serviceCharge - transaction.totalPrice
    if (diskon > 0) output.write("Diskon  : -Rp $diskon\n".toByteArray())

    output.write("Total   : Rp ${transaction.totalPrice}\n".toByteArray())

    if (transaction.paymentMethod == "BON" && transaction.cashReceived == 0L) {
        output.write("STATUS  : BON (BELUM BAYAR)\n".toByteArray())
    } else {
        output.write("Tunai   : Rp ${transaction.cashReceived}\n".toByteArray())
        output.write("Kembali : Rp ${transaction.changeAmount}\n".toByteArray())
    }
    output.write("--------------------------------\n".toByteArray())
    output.write(EscPosCommands.ALIGN_CENTER)
    output.write("$storeFooter\n\n\n".toByteArray())
    output.write(EscPosCommands.FEED_3)
    output.write(EscPosCommands.CUT_PAPER)
    return output.toByteArray()
}