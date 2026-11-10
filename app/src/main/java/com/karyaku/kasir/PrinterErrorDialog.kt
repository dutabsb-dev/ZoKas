package com.karyaku.kasir

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp // <--- IMPORT YANG HILANG
import androidx.compose.ui.unit.sp // <--- IMPORT YANG HILANG

@Composable
fun PrinterErrorDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed) },
        title = { Text("Koneksi Printer Gagal", fontWeight = FontWeight.Bold) },
        text = { 
            Text("Aplikasi tidak dapat terhubung ke printer Bluetooth.\n\nPastikan:\n1. Printer dalam keadaan menyala.\n2. Bluetooth HP aktif.\n3. Printer sudah di-pairing dengan HP ini.", fontSize = 14.sp) 
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(12.dp)
            ) { 
                Text("BUKA PENGATURAN BLUETOOTH") 
            }
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { Text("Tutup", color = SlateGrey) } 
        }
    )
}