package com.karyaku.kasir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karyaku.kasir.data.local.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReceiptDialog(
    transaction: TransactionEntity,
    storeName: String,
    storeFooter: String,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("SELESAI / CETAK BARU")
            }
        },
        title = null,
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = storeName, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                Text(text = "=========================", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text(text = "ID: #${transaction.id} | ${dateFormat.format(Date(transaction.timestamp))}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text(text = "=========================", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                // FORMAT RUPIAH DITERAPKAN DI SINI
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