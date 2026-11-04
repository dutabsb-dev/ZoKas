package com.karyaku.kasir

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    isPro: Boolean
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        
        if (isPro && transactions.isNotEmpty()) {
            Button(
                onClick = {
                    try {
                        val csvHeader = "ID,Tanggal,Total Belanja,Uang Tunai,Kembalian\n"
                        val csvData = transactions.joinToString("\n") { tx ->
                            "${tx.id},${dateFormat.format(Date(tx.timestamp))},${tx.totalPrice},${tx.cashReceived},${tx.changeAmount}"
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
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Ekspor")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ekspor Laporan ke Excel/CSV")
            }
        } else if (!isPro && transactions.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text("Fitur Ekspor Laporan (CSV) hanya tersedia di Versi Pro.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada riwayat transaksi.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(transactions) { tx ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Struk #${tx.id}", fontWeight = FontWeight.Bold)
                                Text(dateFormat.format(Date(tx.timestamp)))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            // FORMAT RUPIAH DITERAPKAN DI SINI
                            Text("Total: ${formatRupiah(tx.totalPrice)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Tunai: ${formatRupiah(tx.cashReceived)}")
                            Text("Kembali: ${formatRupiah(tx.changeAmount)}")
                        }
                    }
                }
            }
        }
    }
}