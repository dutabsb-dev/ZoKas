package com.karyaku.kasir

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.karyaku.kasir.data.local.TransactionEntity
import com.karyaku.kasir.data.local.TransactionItemEntity
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
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

    // ================================================================
    // FIX #1: Loading state eksplisit.
    //   itemsNullable == null  -> Flow belum emit (loading awal)
    //   itemsNullable == []    -> emit tapi kosong (edge case)
    //   itemsNullable == list  -> ready
    // Tombol Share & Print didisable selama loading agar tidak mengirim
    // struk kosong.
    // ================================================================
    val itemsFlow = remember(transaction.id) { viewModel.getTransactionItems(transaction.id) }
    val itemsNullable by itemsFlow.collectAsStateWithLifecycle(
        initialValue = null as List<TransactionItemEntity>?
    )
    val isItemsLoaded = itemsNullable != null
    val items: List<TransactionItemEntity> = itemsNullable ?: emptyList()

    var showPrinterError by remember { mutableStateOf(false) }
    var showShareFallbackDialog by remember { mutableStateOf(false) }
    var textToShare by remember { mutableStateOf("") }

    // Ambil logo dari SettingsManager
    val logoFile = remember(settingsManager.storeLogoPath) {
        settingsManager.storeLogoPath?.let { File(it) }
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
                Button(
                    onClick = {
                        try {
                            textToShare = generateTextReceipt(transaction, storeName, storeAddress, storePhone, storeFooter, dateFormat, items)

                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, textToShare)
                                type = "text/plain"
                            }
                            val waIntent = Intent(sendIntent).apply { setPackage("com.whatsapp") }
                            val waBusinessIntent = Intent(sendIntent).apply { setPackage("com.whatsapp.w4b") }

                            if (waIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(waIntent)
                            } else if (waBusinessIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(waBusinessIntent)
                            } else {
                                showShareFallbackDialog = true
                            }
                        } catch (e: Exception) {
                            showShareFallbackDialog = true
                        }
                    },
                    enabled = isItemsLoaded,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("KIRIM STRUK (WHATSAPP)", fontWeight = FontWeight.Bold, color = Color.White)
                }

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

                // ============================================================
                // FIX #1: Tiga state eksplisit — loading / kosong / ada item.
                // ============================================================
                when {
                    !isItemsLoaded -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = EmeraldGreen
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Memuat rincian...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = SlateGrey
                            )
                        }
                    }
                    items.isEmpty() -> {
                        Text(
                            "Tidak ada item tercatat.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = SlateGrey
                        )
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

                // ============================================================
                // FIX #1: Bagian perhitungan HANYA dirender setelah loaded.
                // Jika belum loaded, tidak ada teks "Subtotal Rp 0" palsu.
                // ============================================================
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