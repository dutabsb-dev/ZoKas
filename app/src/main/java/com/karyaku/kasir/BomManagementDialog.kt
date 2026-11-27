package com.karyaku.kasir

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BomManagementDialog(
    product: ProductEntity,
    rawMaterials: List<RawMaterialEntity>,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val variantNames = remember(product.id, product.variations) {
        VariantBomManager.getVariantNamesForProduct(product)
    }
    val hasVariants = variantNames.isNotEmpty()

    // null = Produk Utama (atau Default Semua Varian jika punya varian)
    var selectedVariantTarget by remember(product.id, variantNames) {
        mutableStateOf<String?>(variantNames.firstOrNull())
    }

    // State daftar resep yang sedang diinput (Sementara)
    val tempBomList = remember { mutableStateListOf<Pair<RawMaterialEntity, Double>>() }
    var existingBoms by remember { mutableStateOf<List<ProductBomEntity>>(emptyList()) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    // State Form Input
    var selectedMaterial by remember { mutableStateOf<RawMaterialEntity?>(null) }
    var qtyUsed by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // State Konfirmasi Hapus & Dialog Kelola Bahan Baku
    var showClearConfirm by remember { mutableStateOf(false) }
    var showRawMaterialDialog by remember { mutableStateOf(false) }

    // Ambil data BOM tersimpan sesuai produk & varian target yang dipilih
    LaunchedEffect(product.id, selectedVariantTarget, reloadTrigger) {
        tempBomList.clear()
        existingBoms = viewModel.getBomForTargetOnce(product.id, selectedVariantTarget)
    }

    // Dialog Kelola Bahan Baku langsung dari dalam Resep BOM
    if (showRawMaterialDialog) {
        RawMaterialManagementDialog(
            rawMaterials = rawMaterials,
            viewModel = viewModel,
            onDismiss = { showRawMaterialDialog = false }
        )
    }

    val targetDisplayName = remember(selectedVariantTarget, hasVariants) {
        when {
            !selectedVariantTarget.isNullOrBlank() -> "Varian: $selectedVariantTarget"
            hasVariants -> "Default Semua Varian"
            else -> "Produk Utama"
        }
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
                    "Semua bahan baku yang tersimpan untuk $targetDisplayName akan dihapus permanen.",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBomForTarget(product.id, selectedVariantTarget) {
                            Toast.makeText(context, "Resep ($targetDisplayName) dikosongkan", Toast.LENGTH_SHORT).show()
                            showClearConfirm = false
                            reloadTrigger++
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("HAPUS RESEP") }
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
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {

                // --- PEMILIH TARGET VARIAN (JIKA PRODUK MEMILIKI VARIAN) ---
                if (hasVariants) {
                    Text(
                        "Pilih Varian Target Resep:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        variantNames.forEach { vName ->
                            val isSelected = selectedVariantTarget == vName
                            val hasSavedRecipe = remember(product.id, vName, reloadTrigger) {
                                VariantBomManager.hasRecipe(context, product.id, vName)
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedVariantTarget = vName },
                                label = {
                                    Text(
                                        text = if (hasSavedRecipe) "$vName ✓" else vName,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                        val isDefaultSelected = selectedVariantTarget == null
                        FilterChip(
                            selected = isDefaultSelected,
                            onClick = { selectedVariantTarget = null },
                            label = {
                                Text(
                                    text = "Semua / Default",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isDefaultSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // --- FORM INPUT BAHAN ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Tambah Bahan ($targetDisplayName):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
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
                            "Stok Bahan",
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
                            selectedMaterial?.let { "${it.name} (${formatRupiah(it.costPerUnit)}/${it.unit})" } ?: "Pilih Bahan...",
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
                                            "${material.name} (Stok: ${material.stock} ${material.unit} • ${formatRupiah(material.costPerUnit)}/${material.unit})",
                                            fontSize = 12.5.sp
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
                                    val existingIdx = tempBomList.indexOfFirst { it.first.id == selectedMaterial!!.id }
                                    if (existingIdx >= 0) {
                                        val prev = tempBomList[existingIdx]
                                        tempBomList[existingIdx] = prev.first to (prev.second + qty)
                                    } else {
                                        tempBomList.add(selectedMaterial!! to qty)
                                    }
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

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                // --- DAFTAR RESEP BARU & TOTAL HARGA BELI / MODAL OTOMATIS ---
                val estimatedBomHpp = VariantBomManager.calculateBatchPairCost(tempBomList.toList())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Komposisi Baru:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (tempBomList.isNotEmpty()) {
                        Text(
                            "Harga Beli/Modal: ${formatRupiah(estimatedBomHpp)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                if (tempBomList.isEmpty()) {
                    Text(
                        "Belum ada bahan baru ditambahkan.",
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
                            val lineCost = Math.round(material.costPerUnit.toDouble() * qty)
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
                                            "$qty ${material.unit} • ${formatRupiah(lineCost)}",
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

                // Tampilkan Resep Tersimpan untuk target ini + Total Harga Modalnya
                if (existingBoms.isNotEmpty()) {
                    val savedEntries = existingBoms.map { VariantBomEntry(it.rawMaterialId, it.quantityUsed) }
                    val savedCost = VariantBomManager.calculateRecipeCost(savedEntries, rawMaterials)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Resep Aktif ($targetDisplayName):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Modal Aktif: ${formatRupiah(savedCost)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row {
                            TextButton(
                                onClick = {
                                    tempBomList.clear()
                                    existingBoms.forEach { bom ->
                                        val rm = rawMaterials.find { it.id == bom.rawMaterialId }
                                        if (rm != null) {
                                            tempBomList.add(rm to bom.quantityUsed)
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("Ubah", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(
                                onClick = { showClearConfirm = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("Kosongkan", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    LazyColumn(modifier = Modifier.heightIn(max = 110.dp)) {
                        itemsIndexed(existingBoms) { _, bom ->
                            val rm = rawMaterials.find { it.id == bom.rawMaterialId }
                            if (rm != null) {
                                val rowCost = Math.round(rm.costPerUnit.toDouble() * bom.quantityUsed)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        rm.name,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${bom.quantityUsed} ${rm.unit} (${formatRupiah(rowCost)})",
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
                        viewModel.saveBomBatchForTarget(
                            productId = product.id,
                            targetVariantName = selectedVariantTarget,
                            boms = tempBomList.toList()
                        ) { calculatedBomCost ->
                            Toast.makeText(
                                context,
                                "Resep ($targetDisplayName) & Harga Beli (${formatRupiah(calculatedBomCost)}) disimpan!",
                                Toast.LENGTH_SHORT
                            ).show()
                            tempBomList.clear()
                            reloadTrigger++
                            if (!hasVariants) {
                                onDismiss()
                            }
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
                    if (tempBomList.isEmpty()) "Selesai" else "SIMPAN RESEP",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}
