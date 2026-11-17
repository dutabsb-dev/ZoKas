package com.karyaku.kasir

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karyaku.kasir.data.local.ProductBomEntity
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.RawMaterialEntity

// ====================================================================
// SESI 4 (POIN #2) — AKSES CEPAT KELOLA BAHAN BAKU DI DIALOG BOM:
//   1. Menambahkan tombol "+ Kelola Bahan Baku" di header & di dalam
//      dropdown saat bahan baku kosong, yang langsung membuka
//      RawMaterialManagementDialog tanpa harus masuk ke Pengaturan -> Modul Toko.
//   2. Seluruh warna sudah 100% adaptif Material 3 (Dark Theme Ready).
// ====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BomManagementDialog(
    product: ProductEntity,
    rawMaterials: List<RawMaterialEntity>,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // State untuk menyimpan daftar resep yang sedang diinput (Sementara)
    val tempBomList = remember { mutableStateListOf<Pair<RawMaterialEntity, Double>>() }
    var existingBoms by remember { mutableStateOf<List<ProductBomEntity>>(emptyList()) }

    // State Form Input
    var selectedMaterial by remember { mutableStateOf<RawMaterialEntity?>(null) }
    var qtyUsed by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // State Konfirmasi Hapus & Dialog Kelola Bahan Baku
    var showClearConfirm by remember { mutableStateOf(false) }
    var showRawMaterialDialog by remember { mutableStateOf(false) }

    // Ambil data BOM yang sudah ada di database saat dialog dibuka
    LaunchedEffect(product.id, existingBoms) {
        existingBoms = viewModel.getBomForProductOnce(product.id)
    }

    // Dialog Kelola Bahan Baku langsung dari dalam Resep BOM
    if (showRawMaterialDialog) {
        RawMaterialManagementDialog(
            rawMaterials = rawMaterials,
            viewModel = viewModel,
            onDismiss = { showRawMaterialDialog = false }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Kosongkan Resep?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Semua bahan baku yang tersimpan untuk produk ini akan dihapus permanen.",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBomProduct(product.id) {
                            Toast.makeText(context, "Resep berhasil dikosongkan", Toast.LENGTH_SHORT).show()
                            showClearConfirm = false
                            existingBoms = emptyList()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("HAPUS SEMUA") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Resep Produk (BOM)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        product.name,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                OutlinedButton(
                    onClick = { showRawMaterialDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.tertiary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        Icons.Default.Inventory,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Bahan Baku",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {

                // --- FORM INPUT BAHAN ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Tambah Bahan Baku:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = { showRawMaterialDialog = true },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Icon(
                            Icons.Default.AddCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Tambah / Stok Bahan",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                Box {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            selectedMaterial?.let { "${it.name} (${it.unit})" } ?: "Pilih Bahan...",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start,
                            fontSize = 13.sp,
                            color = if (selectedMaterial != null)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.heightIn(max = 220.dp)
                    ) {
                        if (rawMaterials.isEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Belum ada data bahan baku. Ketuk untuk tambah.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                onClick = {
                                    dropdownExpanded = false
                                    showRawMaterialDialog = true
                                }
                            )
                        } else {
                            rawMaterials.forEach { material ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${material.name} (Stok: ${material.stock} ${material.unit})",
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = {
                                        selectedMaterial = material
                                        dropdownExpanded = false
                                    }
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Kelola / Tambah Bahan Baku Baru",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                onClick = {
                                    dropdownExpanded = false
                                    showRawMaterialDialog = true
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = qtyUsed,
                        onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) qtyUsed = it },
                        label = {
                            Text(
                                if (selectedMaterial != null) "Jumlah (${selectedMaterial!!.unit})" else "Jumlah",
                                fontSize = 12.sp
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (selectedMaterial != null && qtyUsed.isNotBlank()) {
                                val qty = qtyUsed.toDoubleOrNull()
                                if (qty != null && qty > 0) {
                                    tempBomList.add(selectedMaterial!! to qty)
                                    selectedMaterial = null
                                    qtyUsed = ""
                                }
                            }
                        },
                        enabled = selectedMaterial != null && qtyUsed.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Tambah") }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                // --- DAFTAR RESEP YANG AKAN DISIMPAN ---
                Text(
                    "Daftar Resep Baru:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (tempBomList.isEmpty()) {
                    Text(
                        "Belum ada bahan yang ditambahkan.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(tempBomList) { index, item ->
                            val (material, qty) = item
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            material.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "$qty ${material.unit}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { tempBomList.removeAt(index) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Hapus",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Tampilkan Resep Lama (Jika ada) + Tombol Hapus
                if (existingBoms.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Resep Tersimpan Sebelumnya:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(onClick = { showClearConfirm = true }) {
                            Text(
                                "Kosongkan",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                        itemsIndexed(existingBoms) { _, bom ->
                            val rm = rawMaterials.find { it.id == bom.rawMaterialId }
                            if (rm != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        rm.name,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${bom.quantityUsed} ${rm.unit}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (tempBomList.isNotEmpty()) {
                        viewModel.saveBomBatch(product.id, tempBomList.toList()) {
                            Toast.makeText(context, "Resep berhasil disimpan!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    } else {
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (tempBomList.isEmpty()) "Tutup" else "SIMPAN RESEP",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}
