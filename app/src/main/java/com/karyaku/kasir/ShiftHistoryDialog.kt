package com.karyaku.kasir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karyaku.kasir.data.local.ShiftEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ShiftHistoryDialog(
    shifts: List<ShiftEntity>,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Riwayat Shift Kasir") },
        text = {
            if (shifts.isEmpty()) {
                Text("Belum ada riwayat shift.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(shifts) { shift ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Kasir: ${shift.userName}", fontWeight = FontWeight.Bold)
                                Text("Mulai: ${dateFormat.format(Date(shift.startTime))}", fontSize = 12.sp)
                                if (shift.endTime != null) {
                                    Text("Selesai: ${dateFormat.format(Date(shift.endTime))}", fontSize = 12.sp)
                                    Text("Modal: ${formatRupiah(shift.openingCash)}", fontSize = 12.sp)
                                    Text("Sistem: ${formatRupiah(shift.expectedCash)}", fontSize = 12.sp)
                                    Text("Fisik: ${formatRupiah(shift.closingCash ?: 0)}", fontSize = 12.sp)
                                    val selisih = shift.discrepancy ?: 0
                                    Text("Selisih: ${formatRupiah(selisih)}", color = if (selisih < 0) Color.Red else Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                } else {
                                    Text("Status: MASIH BERJALAN", color = ZokasOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}