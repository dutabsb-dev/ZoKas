package com.karyaku.kasir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karyaku.kasir.data.local.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(transactions: List<TransactionEntity>) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    if (transactions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Belum ada riwayat transaksi.")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(transactions) { tx ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ID: #${tx.id}", fontWeight = FontWeight.Bold)
                            Text(dateFormat.format(Date(tx.timestamp)))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Total: Rp ${tx.totalPrice}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Tunai: Rp ${tx.cashReceived}")
                        Text("Kembali: Rp ${tx.changeAmount}")
                    }
                }
            }
        }
    }
}