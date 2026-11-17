package com.karyaku.kasir

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.karyaku.kasir.data.local.RawMaterialEntity

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

    // State untuk form
    var name by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("gram") }
    var costPerUnit by remember { mutableStateOf("") }

    // Dropdown Satuan
    var unitExpanded by remember { mutableStateOf(false) }
    val unitOptions = listOf("gram", "ml", "pcs", "kg", "liter")

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
                    "Bahan '${materialToDelete!!.name}' akan dihapus permanen. Resep (BOM) yang menggunakan bahan ini mungkin akan error.",
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
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Kelola Bahan Baku",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Atur stok dan harga per satuan",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

                    if (rawMaterials.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Belum ada bahan baku.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                top = 16.dp,
                                end = 16.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(rawMaterials, key = { it.id }) { material ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    material.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    "Stok: ${material.stock} ${material.unit}",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    "Harga: ${formatRupiah(material.costPerUnit)} / ${material.unit}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
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

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.restockRawMaterial(material.id, 10.0, "Restock manual +10")
                                                    Toast.makeText(context, "+10 ${material.unit} ditambahkan", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(vertical = 4.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Tambah +10", fontSize = 11.sp)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.wasteRawMaterial(material.id, 1.0, "Waste/Basi -1")
                                                    Toast.makeText(context, "-1 ${material.unit} (Waste)", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                ),
                                                contentPadding = PaddingValues(vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Remove,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Kurangi -1", fontSize = 11.sp)
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
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
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
