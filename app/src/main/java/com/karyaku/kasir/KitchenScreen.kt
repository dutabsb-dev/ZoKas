package com.karyaku.kasir

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.KitchenTicketEntity
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ====================================================================
// MODUL B — KDS (KITCHEN DISPLAY SYSTEM)
// ====================================================================
// Layar khusus untuk koki/dapur melihat antrean pesanan.
// ====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenScreen(
    viewModel: PosViewModel
) {
    val tickets by viewModel.activeKitchenTickets.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // State untuk trigger bunyi notifikasi
    var previousTicketCount by remember { mutableStateOf(0) }

    // Efek untuk memutar suara saat ada tiket baru
    LaunchedEffect(tickets.size) {
        if (tickets.size > previousTicketCount && previousTicketCount > 0) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                // Beri jeda agar ToneGenerator tidak langsung di-release
                delay(300)
                toneGen.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        previousTicketCount = tickets.size
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ------------------------------------------------------------
        // HEADER
        // ------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Restaurant,
                contentDescription = null,
                tint = AmberAccent,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Layar Dapur (KDS)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${tickets.size} pesanan dalam antrean",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tombol bersihkan tiket yang sudah READY (jika ada)
            // Catatan: READY tickets tidak muncul di activeKitchenTickets,
            // jadi tombol ini untuk jaga-jaga membersihkan DB dari tiket lama.
            IconButton(
                onClick = { viewModel.clearCompletedKitchenTickets() },
                modifier = Modifier
                    .background(EmeraldGreen.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .size(48.dp)
            ) {
                Icon(
                    Icons.Default.CleaningServices,
                    contentDescription = "Bersihkan Riwayat",
                    tint = EmeraldGreen
                )
            }
        }

        // ------------------------------------------------------------
        // CONTENT (GRID)
        // ------------------------------------------------------------
        if (tickets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldGreen.copy(alpha = 0.5f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Dapur Bersih!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = SlateGrey
                    )
                    Text(
                        "Belum ada pesanan masuk.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 280.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tickets, key = { it.id }) { ticket ->
                    KitchenTicketCard(
                        ticket = ticket,
                        onUpdateStatus = { newStatus ->
                            viewModel.updateKitchenTicketStatus(ticket.id, newStatus)
                        }
                    )
                }
            }
        }
    }
}

// ====================================================================
// KARTU TIKET PESANAN
// ====================================================================
@Composable
fun KitchenTicketCard(
    ticket: KitchenTicketEntity,
    onUpdateStatus: (String) -> Unit
) {
    // Timer state (update setiap 30 detik agar tidak terlalu berat)
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            currentTime = System.currentTimeMillis()
        }
    }

    val elapsedMinutes = ((currentTime - ticket.timestamp) / 60000).coerceAtLeast(0)

    // Warna berdasarkan status
    val statusColor = when (ticket.status) {
        "PENDING" -> ErrorRed
        "COOKING" -> AmberAccent
        else -> EmeraldGreen
    }
    val bgColor = statusColor.copy(alpha = 0.08f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Tiket
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ticket.tableName,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${ticket.orderType} • #${ticket.transactionId}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    if (ticket.customerName != "Pelanggan Umum") {
                        Text(
                            text = "Pelanggan: ${ticket.customerName}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Timer Badge
                Surface(
                    color = statusColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${elapsedMinutes}m",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = statusColor.copy(alpha = 0.3f)
            )

            // Item Pesanan (Parse JSON)
            val items = remember(ticket.itemsJson) {
                parseTicketItems(ticket.itemsJson)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Qty Badge
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(statusColor, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${item.qty}x",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (item.notes.isNotBlank()) {
                                Text(
                                    text = item.notes,
                                    fontSize = 12.sp,
                                    color = ErrorRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tombol Aksi
            if (ticket.status == "PENDING") {
                Button(
                    onClick = { onUpdateStatus("COOKING") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("MULAI MASAK", fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else if (ticket.status == "COOKING") {
                Button(
                    onClick = { onUpdateStatus("READY") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SELESAI DIMASAK", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ====================================================================
// HELPER: PARSER JSON ITEMS
// ====================================================================
data class TicketItem(
    val name: String,
    val qty: Int,
    val notes: String
)

fun parseTicketItems(jsonString: String): List<TicketItem> {
    return try {
        val list = mutableListOf<TicketItem>()
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                TicketItem(
                    name = obj.optString("name", "Unknown Item"),
                    qty = obj.optInt("qty", 1),
                    notes = obj.optString("notes", "")
                )
            )
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}