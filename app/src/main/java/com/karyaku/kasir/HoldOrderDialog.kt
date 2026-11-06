package com.karyaku.kasir

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karyaku.kasir.data.local.HoldOrderEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HoldOrderDialog(
    holdOrders: List<HoldOrderEntity>,
    onDismiss: () -> Unit,
    onResume: (HoldOrderEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parkir Pesanan (Meja)") },
        text = {
            if (holdOrders.isEmpty()) {
                Text("Tidak ada pesanan yang diparkir.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(holdOrders) { order ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onResume(order) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(order.tableName, fontWeight = FontWeight.Bold, color = ZokasOrange)
                                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(order.timestamp)), fontSize = 12.sp)
                                }
                                Text("Nama: ${order.customerName}", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}