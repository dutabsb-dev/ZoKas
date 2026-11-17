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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.KitchenTicketEntity
import kotlinx.coroutines.delay
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenScreen(viewModel: PosViewModel) {
    val tickets by viewModel.activeKitchenTickets.collectAsStateWithLifecycle()

    var previousTicketCount by remember { mutableStateOf(0) }

    LaunchedEffect(tickets.size) {
        if (tickets.size > previousTicketCount && previousTicketCount > 0) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                delay(300)
                toneGen.release()
            } catch (e: Exception) { e.printStackTrace() }
        }
        previousTicketCount = tickets.size
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Restaurant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,      // ← adaptif amber
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Layar Dapur (KDS)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${tickets.size} pesanan dalam antrean",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant   // ← FIX
                )
            }

            IconButton(
                onClick = { viewModel.clearCompletedKitchenTickets() },
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        RoundedCornerShape(12.dp)
                    )
                    .size(48.dp)
            ) {
                Icon(
                    Icons.Default.CleaningServices,
                    contentDescription = "Bersihkan Riwayat",
                    tint = MaterialTheme.colorScheme.primary               // ← FIX
                )
            }
        }

        // CONTENT
        if (tickets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Dapur Bersih!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant      // ← FIX
                    )
                    Text(
                        "Belum ada pesanan masuk.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant      // ← FIX
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
                        onUpdateStatus = { viewModel.updateKitchenTicketStatus(ticket.id, it) }
                    )
                }
            }
        }
    }
}

@Composable
fun KitchenTicketCard(ticket: KitchenTicketEntity, onUpdateStatus: (String) -> Unit) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); currentTime = System.currentTimeMillis() }
    }
    val elapsedMinutes = ((currentTime - ticket.timestamp) / 60000).coerceAtLeast(0)

    // ★ Warna status ADAPTIF (light ↔ dark)
    val statusColor = when (ticket.status) {
        "PENDING" -> MaterialTheme.colorScheme.error
        "COOKING" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val bgColor = statusColor.copy(alpha = 0.10f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        ticket.tableName,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${ticket.orderType} • #${ticket.transactionId}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    if (ticket.customerName != "Pelanggan Umum") {
                        Text(
                            "Pelanggan: ${ticket.customerName}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(color = statusColor, shape = RoundedCornerShape(8.dp)) {
                    // Teks di atas statusColor: gunakan onPrimary/onError/onTertiary
                    val onBadgeColor = when (ticket.status) {
                        "PENDING" -> MaterialTheme.colorScheme.onError
                        "COOKING" -> MaterialTheme.colorScheme.onTertiary
                        else -> MaterialTheme.colorScheme.onPrimary
                    }
                    Text(
                        "${elapsedMinutes}m",
                        color = onBadgeColor,
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

            val items = remember(ticket.itemsJson) { parseTicketItems(ticket.itemsJson) }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(statusColor, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val onBadgeColor = when (ticket.status) {
                                "PENDING" -> MaterialTheme.colorScheme.onError
                                "COOKING" -> MaterialTheme.colorScheme.onTertiary
                                else -> MaterialTheme.colorScheme.onPrimary
                            }
                            Text(
                                "${item.qty}x",
                                color = onBadgeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (item.notes.isNotBlank()) {
                                Text(
                                    item.notes,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,   // ← FIX
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (ticket.status == "PENDING") {
                Button(
                    onClick = { onUpdateStatus("COOKING") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "MULAI MASAK",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                }
            } else if (ticket.status == "COOKING") {
                Button(
                    onClick = { onUpdateStatus("READY") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "SELESAI DIMASAK",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

data class TicketItem(val name: String, val qty: Int, val notes: String)

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
    } catch (e: Exception) { emptyList() }
}