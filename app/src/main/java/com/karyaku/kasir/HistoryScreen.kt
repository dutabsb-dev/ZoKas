package com.karyaku.kasir

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karyaku.kasir.data.local.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun HistoryScreen(
    transactions: List<TransactionEntity>,
    isPro: Boolean,
    viewModel: PosViewModel,
    settingsManager: SettingsManager // <--- Parameter Baru
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    if (selectedTransaction != null) {
        TransactionDetailDialog(
            transaction = selectedTransaction!!,
            viewModel = viewModel,
            settingsManager = settingsManager, // Kirim ke dialog
            onDismiss = { selectedTransaction = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        
        // --- KARTU RINGKASAN OMZET ---
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).shadow(6.dp, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = ZokasGreen)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total Omzet (Semua Waktu)", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                Text(
                    text = formatRupiah(transactions.sumOf { it.totalPrice }),
                    color = ZokasOrange,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("${transactions.size} Transaksi", color = Color.White, fontSize = 12.sp)
            }
        }

        // Tombol Ekspor
        if (isPro && transactions.isNotEmpty()) {
            Button(
                onClick = {
                    try {
                        val csvHeader = "ID,Tanggal,Total Belanja,Uang Tunai,Kembalian,Pelanggan\n"
                        val csvData = transactions.joinToString("\n") { tx ->
                            "${tx.id},${dateFormat.format(Date(tx.timestamp))},${tx.totalPrice},${tx.cashReceived},${tx.changeAmount},${tx.customerName}"
                        }
                        val fullCsv = csvHeader + csvData
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, fullCsv)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Ekspor Laporan (CSV)")
                        if (activity != null) activity.startActivity(shareIntent) else context.startActivity(shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Gagal ekspor: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Ekspor")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ekspor Laporan ke Excel/CSV")
            }
        }

        if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada riwayat transaksi.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(transactions) { tx ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTransaction = tx },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Struk #${tx.id}", fontWeight = FontWeight.Bold)
                                Text(dateFormat.format(Date(tx.timestamp)))
                            }
                            Text("Pelanggan: ${tx.customerName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Total: ${formatRupiah(tx.totalPrice)}", fontWeight = FontWeight.Bold, color = ZokasOrange)
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
    settingsManager: SettingsManager, // <--- Parameter Baru
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val items by viewModel.getTransactionItems(transaction.id).collectAsState()
    
    // State untuk pop-up struk
    var showReceipt by remember { mutableStateOf(false) }

    if (showReceipt) {
        ReceiptDialog(
            transaction = transaction,
            storeName = if (settingsManager.isPro) settingsManager.storeName else "ZoKas (Gratis)",
            storeFooter = if (settingsManager.isPro) settingsManager.storeFooter else "Terima kasih!",
            settingsManager = settingsManager,
            items = items, // Kirim rincian barang ke struk
            onDismiss = { showReceipt = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { showReceipt = true }, 
                colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("CETAK ULANG")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        },
        title = { Text("Rincian Transaksi #${transaction.id}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Tanggal: ${dateFormat.format(Date(transaction.timestamp))}", fontSize = 12.sp)
                Text("Pelanggan: ${transaction.customerName}", fontSize = 12.sp)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                if (items.isEmpty()) {
                    Text("Memuat rincian...", fontSize = 12.sp, color = Color.Gray)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                        items(items) { item ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${item.quantity} x ${formatRupiah(item.price)}", fontSize = 12.sp, color = Color.Gray)
                                }
                                Text(formatRupiah(item.subtotal), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total:", fontWeight = FontWeight.Bold)
                    Text(formatRupiah(transaction.totalPrice), fontWeight = FontWeight.Bold, color = ZokasOrange)
                }
            }
        }
    )
}