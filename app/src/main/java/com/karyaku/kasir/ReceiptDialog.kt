package com.karyaku.kasir

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // <--- IMPORT YANG TADI HILANG
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karyaku.kasir.data.local.TransactionEntity
import com.karyaku.kasir.data.local.TransactionItemEntity
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReceiptDialog(
    transaction: TransactionEntity,
    storeName: String,
    storeAddress: String,
    storePhone: String,
    storeFooter: String,
    settingsManager: SettingsManager,
    items: List<TransactionItemEntity>? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val printerManager = BluetoothPrinterManager(context)
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Column {
                // TOMBOL E-RECEIPT (WHATSAPP)
                Button(
                    onClick = {
                        val textStruk = generateTextReceipt(transaction, storeName, storeAddress, storePhone, storeFooter, dateFormat, items)
                        try {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, textStruk)
                                type = "text/plain"
                                setPackage("com.whatsapp") // Kirim langsung ke WA
                            }
                            context.startActivity(sendIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "WhatsApp tidak terinstal. Menyalin ke clipboard.", Toast.LENGTH_SHORT).show()
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Struk ZoKas", textStruk)
                            clipboard.setPrimaryClip(clip)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)) // Warna WhatsApp
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("KIRIM STRUK (WHATSAPP)", color = Color.White)
                }
                
                // TOMBOL CETAK THERMAL
                Button(
                    onClick = {
                        val mac = settingsManager.printerMacAddress
                        if (mac == null) {
                            Toast.makeText(context, "Tidak ada printer. Atur di Pengaturan.", Toast.LENGTH_LONG).show()
                        } else {
                            scope.launch {
                                val printData = generatePrintData(transaction, storeName, storeAddress, storePhone, storeFooter, dateFormat, items)
                                val success = printerManager.printData(mac, printData)
                                if (success) {
                                    Toast.makeText(context, "Struk dicetak!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Gagal mencetak. Pastikan printer menyala.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)
                ) {
                    Text("CETAK STRUK THERMAL")
                }
            }
        },
        title = null,
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                
                // HEADER TOKO
                Text(text = storeName, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                Text(text = storeAddress, fontFamily = FontFamily.Monospace, fontSize = 10.sp, textAlign = TextAlign.Center)
                Text(text = "Telp: $storePhone", fontFamily = FontFamily.Monospace, fontSize = 10.sp, textAlign = TextAlign.Center)
                
                Text(text = "=========================", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text(text = "ID: #${transaction.id} | ${dateFormat.format(Date(transaction.timestamp))}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text(text = "Kasir: ${transaction.cashierName} | ${transaction.tableName}", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                Text(text = "Pelanggan: ${transaction.customerName}", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                Text(text = "=========================", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                
                // RINCIAN BARANG
                if (items != null && items.isNotEmpty()) {
                    items.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.quantity}x ${item.productName}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            Text(formatRupiah(item.subtotal), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                    Text(text = "-------------------------", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }

                // SUBTOTAL, PAJAK, DISKON
                val subtotalItems = items?.sumOf { it.subtotal } ?: transaction.totalPrice
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

                val diskon = subtotalItems + transaction.taxAmount - transaction.totalPrice
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
                
                if (transaction.paymentMethod == "BON") {
                    Text("METODE: BON (BELUM BAYAR)", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.Red)
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
    sb.append("Kasir: ${transaction.cashierName}\n")
    sb.append("Pelanggan: ${transaction.customerName}\n")
    sb.append("------------------------\n")
    
    items?.forEach {
        sb.append("${it.quantity}x ${it.productName}\n")
        sb.append("   Rp ${it.subtotal}\n")
    }
    sb.append("------------------------\n")
    
    val subtotal = items?.sumOf { it.subtotal } ?: transaction.totalPrice
    sb.append("Subtotal: Rp $subtotal\n")
    if (transaction.taxAmount > 0) sb.append("Pajak: Rp ${transaction.taxAmount}\n")
    val diskon = subtotal + transaction.taxAmount - transaction.totalPrice
    if (diskon > 0) sb.append("Diskon: -Rp $diskon\n")
    
    sb.append("*Total: Rp ${transaction.totalPrice}*\n")
    if (transaction.paymentMethod == "BON") {
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
    dateFormat: SimpleDateFormat, items: List<TransactionItemEntity>?
): ByteArray {
    val output = ByteArrayOutputStream()
    output.write(EscPosCommands.INIT)
    output.write(EscPosCommands.ALIGN_CENTER)
    output.write(EscPosCommands.BOLD_ON)
    output.write("$storeName\n".toByteArray())
    output.write(EscPosCommands.BOLD_OFF)
    output.write("$storeAddress\nTelp: $storePhone\n".toByteArray())
    output.write("================================\n".toByteArray())
    output.write("ID: #${transaction.id}\n".toByteArray())
    output.write("${dateFormat.format(Date(transaction.timestamp))}\n".toByteArray())
    output.write("Kasir: ${transaction.cashierName} | ${transaction.tableName}\n".toByteArray())
    output.write("Pelanggan: ${transaction.customerName}\n".toByteArray())
    output.write("================================\n".toByteArray())
    output.write(EscPosCommands.ALIGN_LEFT)
    
    items?.forEach {
        output.write("${it.quantity}x ${it.productName}\n  Rp ${it.subtotal}\n".toByteArray())
    }
    output.write("--------------------------------\n".toByteArray())
    
    val subtotal = items?.sumOf { it.subtotal } ?: transaction.totalPrice
    output.write("Subtotal: Rp $subtotal\n".toByteArray())
    if (transaction.taxAmount > 0) output.write("Pajak   : Rp ${transaction.taxAmount}\n".toByteArray())
    val diskon = subtotal + transaction.taxAmount - transaction.totalPrice
    if (diskon > 0) output.write("Diskon  : -Rp $diskon\n".toByteArray())
    
    output.write("Total   : Rp ${transaction.totalPrice}\n".toByteArray())
    if (transaction.paymentMethod == "BON") {
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