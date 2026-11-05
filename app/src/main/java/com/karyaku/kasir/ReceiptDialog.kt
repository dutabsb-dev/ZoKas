package com.karyaku.kasir

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    storeFooter: String,
    settingsManager: SettingsManager,
    items: List<TransactionItemEntity>? = null, // Parameter baru untuk rincian barang
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val printerManager = BluetoothPrinterManager(context)
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val mac = settingsManager.printerMacAddress
                    if (mac == null) {
                        Toast.makeText(context, "Tidak ada printer terhubung. Atur di Pengaturan.", Toast.LENGTH_LONG).show()
                    } else {
                        scope.launch {
                            val printData = generatePrintData(transaction, storeName, storeFooter, dateFormat, items)
                            val success = printerManager.printData(mac, printData)
                            if (success) {
                                Toast.makeText(context, "Struk berhasil dicetak!", Toast.LENGTH_SHORT).show()
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
                Text("CETAK STRUK")
            }
        },
        title = null,
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                // Tampilan UI Struk
                Text(text = storeName, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                Text(text = "=========================", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text(text = "ID: #${transaction.id} | ${dateFormat.format(Date(transaction.timestamp))}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text(text = "Pelanggan: ${transaction.customerName}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text(text = "=========================", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                
                // Rincian Barang (Jika ada)
                if (items != null && items.isNotEmpty()) {
                    items.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.quantity}x ${item.productName}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            Text(formatRupiah(item.subtotal), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                    Text(text = "-------------------------", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Belanja", fontFamily = FontFamily.Monospace)
                    Text(formatRupiah(transaction.totalPrice), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tunai", fontFamily = FontFamily.Monospace)
                    Text(formatRupiah(transaction.cashReceived), fontFamily = FontFamily.Monospace)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kembalian", fontFamily = FontFamily.Monospace)
                    Text(formatRupiah(transaction.changeAmount), fontFamily = FontFamily.Monospace)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "=========================", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text(text = storeFooter, fontFamily = FontFamily.Monospace, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    )
}

private fun generatePrintData(
    transaction: TransactionEntity,
    storeName: String,
    storeFooter: String,
    dateFormat: SimpleDateFormat,
    items: List<TransactionItemEntity>?
): ByteArray {
    val output = ByteArrayOutputStream()
    
    output.write(EscPosCommands.INIT)
    output.write(EscPosCommands.ALIGN_CENTER)
    
    output.write(EscPosCommands.BOLD_ON)
    output.write("$storeName\n".toByteArray())
    output.write(EscPosCommands.BOLD_OFF)
    
    output.write("================================\n".toByteArray())
    output.write("ID: #${transaction.id}\n".toByteArray())
    output.write("${dateFormat.format(Date(transaction.timestamp))}\n".toByteArray())
    output.write("Pelanggan: ${transaction.customerName}\n".toByteArray())
    output.write("================================\n".toByteArray())
    
    output.write(EscPosCommands.ALIGN_LEFT)
    
    // CETAK RINCIAN BARANG KE PRINTER
    if (items != null && items.isNotEmpty()) {
        items.forEach { item ->
            output.write("${item.quantity}x ${item.productName}\n".toByteArray())
            val priceStr = "  Rp ${item.subtotal}\n"
            output.write(priceStr.toByteArray())
        }
        output.write("--------------------------------\n".toByteArray())
    }

    output.write("Total  : ${formatRupiah(transaction.totalPrice)}\n".toByteArray())
    output.write("Tunai  : ${formatRupiah(transaction.cashReceived)}\n".toByteArray())
    output.write("Kembali: ${formatRupiah(transaction.changeAmount)}\n".toByteArray())
    output.write("--------------------------------\n".toByteArray())
    
    output.write(EscPosCommands.ALIGN_CENTER)
    output.write("$storeFooter\n\n\n".toByteArray())
    
    output.write(EscPosCommands.FEED_3)
    output.write(EscPosCommands.CUT_PAPER)
    
    return output.toByteArray()
}