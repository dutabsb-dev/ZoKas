package com.karyaku.kasir

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.karyaku.kasir.data.local.RawMaterialEntity

// ====================================================================
// REKOMENDASI #5 & #6 DITERAPKAN — RawMaterialManagementDialog-v3.kt:
//   1. Mengganti tombol kaku "+10" dan "-1" dengan Dialog Input Bebas
//      ("+ Tambah Stok" dan "- Kurangi / Waste") lengkap dengan chip
//      nominal cepat (+100, +500, +1.000, +5.000, +10.000) serta
//      input angka manual dan keterangan, sehingga sangat praktis untuk
//      satuan ribuan/puluhan ribu (gram, ml) maupun satuan besar (kg, liter).
//   2. Menambahkan Kolom Pencarian bahan baku & Ringkasan Total Nilai Stok.
//   3. Menambahkan Empty State ilustratif dengan tombol Call-to-Action
//      serta border kartu halus (outlineVariant) agar tampilan modern.
// ====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawMaterialManagementDialog(
    rawMaterials: List<RawMaterialEntity>,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showAddForm by remember { mutableStateOf(false) }
    var materialToEdit by remember { mutableStateOf<RawMaterialEntity?>(null) }
    var materialToDelete by remember { mutableStateOf<RawMaterialEntity?>(null) }

    // State untuk Dialog Input Bebas Restock / Waste
    var materialForStockAdjust by remember { mutableStateOf<RawMaterialEntity?>(null) }
    var isRestockMode by remember { mutableStateOf(true) }
    var adjustQtyInput by remember { mutableStateOf("") }
    var adjustNoteInput by remember { mutableStateOf("") }

    // State Pencarian
    var searchQuery by remember { mutableStateOf("") }

    // State untuk form tambah/edit bahan
    var name by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("gram") }
    var costPerUnit by remember { mutableStateOf("") }

    // Dropdown Satuan
    var unitExpanded by remember { mutableStateOf(false) }
    val unitOptions = listOf("gram", "ml", "pcs", "kg", "liter")

    val filteredMaterials = remember(rawMaterials, searchQuery) {
        if (searchQuery.isBlank()) rawMaterials
        else rawMaterials.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val totalInventoryValue = remember(rawMaterials) {
        rawMaterials.sumOf { (it.stock * it.costPerUnit).toLong() }
    }

    // DIALOG INPUT BEBAS TAMBAH STOK / KURANGI (WASTE)
    if (materialForStockAdjust != null) {
        val target = materialForStockAdjust!!
        val quickAmounts = remember(target.unit, isRestockMode) {
            when (target.unit.lowercase()) {
                "gram", "ml" -> if (isRestockMode) listOf(100.0, 250.0, 500.0, 1000.0, 5000.0) else listOf(10.0, 50.0, 100.0, 250.0, 500.0)
                "kg", "liter" -> if (isRestockMode) listOf(1.0, 2.0, 5.0, 10.0, 25.0) else listOf(0.5, 1.0, 2.0, 5.0)
                else -> if (isRestockMode) listOf(5.0, 10.0, 24.0, 50.0, 100.0) else listOf(1.0, 2.0, 5.0, 10.0)
            }
        }
        val accentColor = if (isRestockMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

        AlertDialog(
            onDismissRequest = {
                materialForStockAdjust = null
                adjustQtyInput = ""
                adjustNoteInput = ""
            },
            icon = {
                Icon(
                    imageVector = if (isRestockMode) Icons.Default.AddCircle else Icons.Default.RemoveCircle,
                    contentDescription = null,
                    tint = accentColor
                )
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isRestockMode) "Tambah Stok Bahan Baku" else "Kurangi Stok / Catat Waste",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${target.name} (Stok saat ini: ${target.stock} ${target.unit})",
                        fontSize = 12.sp,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Pilih Cepat (${target.unit}):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickAmounts.forEach { preset ->
                            val currentVal = adjustQtyInput.toDoubleOrNull() ?: 0.0
                            val labelStr = if (preset % 1.0 == 0.0) preset.toLong().toString() else preset.toString()
                            OutlinedButton(
                                onClick = {
                                    val nextVal = currentVal + preset
                                    adjustQtyInput = if (nextVal % 1.0 == 0.0) nextVal.toLong().toString() else nextVal.toString()
                                },
                                modifier = Modifier.weight(1f).height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
                            ) {
                                Text(
                                    text = "${if (isRestockMode) "+" else "-"}$labelStr",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = adjustQtyInput,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) adjustQtyInput = it },
                        label = {
                            Text(
                                if (isRestockMode) "Jumlah Tambah (${target.unit})" else "Jumlah Dikurangi (${target.unit})"
                            )
                        },
                        placeholder = { Text("Ketik nominal bebas, cth: 2500") },
                        trailingIcon = {
                            if (adjustQtyInput.isNotEmpty()) {
                                IconButton(onClick = { adjustQtyInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Reset", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = adjustNoteInput,
                        onValueChange = { adjustNoteInput = it },
                        label = { Text("Keterangan (Opsional)") },
                        placeholder = {
                            Text(
                                if (isRestockMode) "Cth: Belanja mingguan" else "Cth: Tumpah / Kedaluwarsa",
                                fontSize = 12.sp
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                val inputQty = adjustQtyInput.toDoubleOrNull() ?: 0.0
                Button(
                    onClick = {
                        if (inputQty > 0) {
                            val note = adjustNoteInput.ifBlank {
                                if (isRestockMode) "Restock manual +$inputQty ${target.unit}"
                                else "Waste/Kurangi -$inputQty ${target.unit}"
                            }
                            if (isRestockMode) {
                                viewModel.restockRawMaterial(target.id, inputQty, note)
                                Toast.makeText(context, "+$inputQty ${target.unit} ${target.name} berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.wasteRawMaterial(target.id, inputQty, note)
                                Toast.makeText(context, "-$inputQty ${target.unit} ${target.name} telah dikurangi", Toast.LENGTH_SHORT).show()
                            }
                            materialForStockAdjust = null
                            adjustQtyInput = ""
                            adjustNoteInput = ""
                        }
                    },
                    enabled = inputQty > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = if (isRestockMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isRestockMode) "TAMBAH STOK" else "KURANGI STOK",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        materialForStockAdjust = null
                        adjustQtyInput = ""
                        adjustNoteInput = ""
                    }
                ) {
                    Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showAddForm || materialToEdit != null) {
        val isEdit = materialToEdit != null
        LaunchedEffect(materialToEdit) {
            if (isEdit) {
                name = materialToEdit!!.name
                stock = materialToEdit!!.stock.toString()
                unit = materialToEdit!!.unit
                costPerUnit = materialToEdit!!.costPerUnit.toString()
            } else {
                name = ""
                stock = ""
                unit = "gram"
                costPerUnit = ""
            }
        }

        AlertDialog(
            onDismissRequest = { showAddForm = false; materialToEdit = null },
            title = {
                Text(
                    if (isEdit) "Edit Bahan Baku" else "Tambah Bahan Baku",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Bahan (cth: Biji Kopi)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = stock,
                            onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) stock = it },
                            label = { Text("Stok Awal") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { unitExpanded = true },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(unit, color = MaterialTheme.colorScheme.onSurface)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                                unitOptions.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt) },
                                        onClick = { unit = opt; unitExpanded = false }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = costPerUnit,
                        onValueChange = { if (it.all { c -> c.isDigit() }) costPerUnit = it },
                        label = { Text("Harga Modal (Rp) per $unit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = stock.toDoubleOrNull() ?: 0.0
                        val cost = costPerUnit.toLongOrNull() ?: 0L
                        if (name.isNotBlank()) {
                            if (isEdit) {
                                viewModel.updateRawMaterial(materialToEdit!!.id, name, qty, unit, cost) {
                                    Toast.makeText(context, "Bahan baku diperbarui", Toast.LENGTH_SHORT).show()
                                    materialToEdit = null
                                }
                            } else {
                                viewModel.addRawMaterial(name, qty, unit, cost)
                                Toast.makeText(context, "Bahan baku ditambahkan", Toast.LENGTH_SHORT).show()
                                showAddForm = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (isEdit) "SIMPAN" else "TAMBAH") }
            },
            dismissButton = {
                TextButton(onClick = { showAddForm = false; materialToEdit = null }) {
                    Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (materialToDelete != null) {
        AlertDialog(
            onDismissRequest = { materialToDelete = null },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Hapus Bahan Baku?") },
            text = {
                Text(
                    "Bahan '${materialToDelete!!.name}' akan dihapus permanen. Resep (BOM) yang menggunakan bahan ini mungkin akan terpengaruh.",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRawMaterial(materialToDelete!!.id) {
                            Toast.makeText(context, "Bahan dihapus", Toast.LENGTH_SHORT).show()
                            materialToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("HAPUS") }
            },
            dismissButton = {
                TextButton(onClick = { materialToDelete = null }) {
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
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Kelola Bahan Baku",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${rawMaterials.size} item terdaftar • Nilai stok: ${formatRupiah(totalInventoryValue)}",
                                fontSize = 12.sp,
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

                    // Baris Pencarian Bahan Baku
                    if (rawMaterials.isNotEmpty()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari nama bahan baku...", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Hapus", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    if (filteredMaterials.isEmpty()) {
                        // REKOMENDASI #6: Empty State Ilustratif & Modern
                        Box(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Inventory2,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "Bahan '$searchQuery' tidak ditemukan" else "Belum Ada Bahan Baku",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tambahkan bahan mentah (seperti biji kopi, susu, gula, atau kemasan) untuk diintegrasikan dengan resep produk (BOM).",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                top = 8.dp,
                                end = 16.dp,
                                bottom = 140.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredMaterials, key = { it.id }) { material ->
                                val isLowStock = material.stock <= 0.0
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isLowStock) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    material.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(
                                                        color = if (isLowStock)
                                                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                                        else
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "Stok: ${material.stock} ${material.unit}",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isLowStock)
                                                                MaterialTheme.colorScheme.error
                                                            else
                                                                MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        "${formatRupiah(material.costPerUnit)} / ${material.unit}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            IconButton(onClick = { materialToEdit = material }) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Edit",
                                                    tint = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            IconButton(onClick = { materialToDelete = material }) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Hapus",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        // Tombol Aksi Restock & Waste dengan Dialog Input Bebas
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    isRestockMode = true
                                                    adjustQtyInput = ""
                                                    adjustNoteInput = ""
                                                    materialForStockAdjust = material
                                                },
                                                modifier = Modifier.weight(1f).height(38.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    contentColor = MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                Icon(
                                                    Icons.Default.AddCircleOutline,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Tambah Stok", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    isRestockMode = false
                                                    adjustQtyInput = ""
                                                    adjustNoteInput = ""
                                                    materialForStockAdjust = material
                                                },
                                                modifier = Modifier.weight(1f).height(38.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                ),
                                                contentPadding = PaddingValues(vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.RemoveCircleOutline,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Kurangi / Waste", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // FAB
                ExtendedFloatingActionButton(
                    onClick = { showAddForm = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 72.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tambah Bahan", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
