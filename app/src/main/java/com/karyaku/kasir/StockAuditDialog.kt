package com.karyaku.kasir

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.StockOpnameItemEntity
import com.karyaku.kasir.data.local.UserEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAuditDialog(
    products: List<ProductEntity>,
    viewModel: PosViewModel,
    currentUser: UserEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val auditableProducts = remember(products) {
        products.filter { !it.isService }.sortedBy { it.name }
    }

    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("ALL") } // ALL, DIFF_ONLY, LOW_STOCK
    val physicalCounts = remember {
        mutableStateMapOf<Long, String>().apply {
            auditableProducts.forEach { prod ->
                put(prod.id, prod.stock.toString())
            }
        }
    }
    var showConfirmSave by remember { mutableStateOf(false) }

    LaunchedEffect(auditableProducts) {
        auditableProducts.forEach { prod ->
            if (!physicalCounts.containsKey(prod.id)) {
                physicalCounts[prod.id] = prod.stock.toString()
            }
        }
    }

    val computedItems = remember(auditableProducts, physicalCounts.toMap()) {
        auditableProducts.map { prod ->
            val physStr = physicalCounts[prod.id] ?: prod.stock.toString()
            val physVal = physStr.toIntOrNull() ?: prod.stock
            val diff = physVal - prod.stock
            Triple(prod, physVal, diff)
        }
    }

    val totalCheckedCount = computedItems.size
    val totalDiffItemsCount = computedItems.count { it.third != 0 }
    val totalLossRp = computedItems.filter { it.third < 0 }.sumOf { Math.abs(it.third) * it.first.costPrice }
    val totalGainRp = computedItems.filter { it.third > 0 }.sumOf { it.third * it.first.costPrice }

    val filteredList = remember(computedItems, searchQuery, filterMode) {
        computedItems.filter { (prod, _, diff) ->
            val matchQuery = searchQuery.isBlank() || matchesProductSearch(prod, searchQuery)
            val matchFilter = when (filterMode) {
                "DIFF_ONLY" -> diff != 0
                "LOW_STOCK" -> prod.stock <= 5
                else -> true
            }
            matchQuery && matchFilter
        }
    }

    if (showConfirmSave) {
        AlertDialog(
            onDismissRequest = { showConfirmSave = false },
            icon = {
                Icon(
                    Icons.Default.FactCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Simpan Hasil Audit Stok?", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Stok produk di sistem akan disesuaikan dengan jumlah stok fisik hasil audit:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Item Diaudit:", fontSize = 12.sp)
                        Text("$totalCheckedCount produk", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Item Berselisih:", fontSize = 12.sp)
                        Text("$totalDiffItemsCount produk", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Nilai Selisih Kurang:", fontSize = 12.sp)
                        Text("- ${formatRupiah(totalLossRp)}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Nilai Selisih Lebih:", fontSize = 12.sp)
                        Text("+ ${formatRupiah(totalGainRp)}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val opnameEntities = computedItems.map { (prod, physVal, diff) ->
                            StockOpnameItemEntity(
                                opnameId = 0L,
                                productId = prod.id,
                                productName = prod.name,
                                systemStock = prod.stock,
                                physicalStock = physVal,
                                difference = diff
                            )
                        }
                        viewModel.processStockOpname(opnameEntities, currentUser) {
                            Toast.makeText(
                                context,
                                "Audit Stok selesai! ${opnameEntities.size} produk berhasil diperbarui.",
                                Toast.LENGTH_LONG
                            ).show()
                            showConfirmSave = false
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("SIMPAN & SESUAIKAN STOK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmSave = false }) {
                    Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                // TOP BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FactCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Audit Stok (Stock Opname)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$totalCheckedCount produk fisik • $totalDiffItemsCount item berselisih",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // RINGKASAN SELISIH REAL-TIME
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Selisih Kurang", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "- ${formatRupiah(totalLossRp)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Selisih Lebih", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "+ ${formatRupiah(totalGainRp)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // PENCARIAN & FILTER CHIP
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari nama produk atau Kode Unik (SKU)...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Hapus", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chips = listOf(
                        "ALL" to "Semua ($totalCheckedCount)",
                        "DIFF_ONLY" to "Ada Selisih ($totalDiffItemsCount)",
                        "LOW_STOCK" to "Stok Menipis"
                    )
                    chips.forEach { (key, label) ->
                        val selected = filterMode == key
                        FilterChip(
                            selected = selected,
                            onClick = { filterMode = key },
                            label = {
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                // DAFTAR ITEM AUDIT STOK
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Tidak ada produk yang cocok dengan filter.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredList, key = { it.first.id }) { (prod, physVal, diff) ->
                            val rawInput = physicalCounts[prod.id] ?: prod.stock.toString()
                            val unitLabel = extractProductBaseUnit(prod)
                            val sku = extractProductSku(prod.barcode)

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        diff < 0 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                                        diff > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        diff < 0 -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                        diff > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = prod.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = buildString {
                                                if (sku.isNotEmpty()) append("[$sku] ")
                                                append("Sistem: ${prod.stock} $unitLabel")
                                            },
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (diff != 0) {
                                            Text(
                                                text = if (diff > 0) "Selisih: +$diff $unitLabel" else "Selisih: $diff $unitLabel",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (diff > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val next = (physVal - 1).coerceAtLeast(0)
                                                physicalCounts[prod.id] = next.toString()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Kurang", modifier = Modifier.size(20.dp))
                                        }

                                        OutlinedTextField(
                                            value = rawInput,
                                            onValueChange = { txt ->
                                                val digits = txt.filter { it.isDigit() }
                                                if (digits.length <= 6) {
                                                    physicalCounts[prod.id] = digits
                                                }
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.width(74.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        )

                                        IconButton(
                                            onClick = {
                                                val next = physVal + 1
                                                physicalCounts[prod.id] = next.toString()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.AddCircleOutline, contentDescription = "Tambah", modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TOMBOL SIMPAN AUDIT DI BAGIAN BAWAH (DENGAN BEZEL BAWAH AGAR NAIK & TERLIHAT JELAS)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 42.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Batal", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = { showConfirmSave = true },
                            enabled = auditableProducts.isNotEmpty(),
                            modifier = Modifier.weight(1.6f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SIMPAN HASIL AUDIT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
