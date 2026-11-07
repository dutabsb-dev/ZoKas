package com.karyaku.kasir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karyaku.kasir.data.local.ProductEntity

@Composable
fun StockOpnameDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var qty by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Stok: ${product.name}") },
        text = {
            Column {
                Text("Stok saat ini: ${product.stock}")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = qty,
                    onValueChange = { if (it.length <= 5) qty = it },
                    label = { Text("Jumlah Barang Masuk") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    val q = qty.toIntOrNull() ?: 0
                    if (q > 0) onSave(q) 
                },
                enabled = (qty.toIntOrNull() ?: 0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)
            ) { Text("TAMBAH STOK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}