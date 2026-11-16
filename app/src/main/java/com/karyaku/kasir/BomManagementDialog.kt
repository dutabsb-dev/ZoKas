package com.karyaku.kasir

import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
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
    
    // State untuk menyimpan daftar resep yang sedang diinput (Sementara)
    val tempBomList = remember { mutableStateListOf<Pair<RawMaterialEntity, Double>>() }
    var existingBoms by remember { mutableStateOf<List<ProductBomEntity>>(emptyList()) }
    
    // State Form Input
    var selectedMaterial by remember { mutableStateOf<RawMaterialEntity?>(null) }
    var qtyUsed by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    
    // State Konfirmasi Hapus
    var showClearConfirm by remember { mutableStateOf(false) }

    // Fungsi untuk memuat ulang data BOM dari database
    fun loadBoms() {
        // Menggunakan CoroutineScope dari ViewModel secara tidak langsung lewat LaunchedEffect
    }

    // Ambil data BOM yang sudah ada di database saat dialog dibuka
    LaunchedEffect(product.id, existingBoms) {
        existingBoms = viewModel.getBomForProductOnce(product.id)
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed) },
            title = { Text("Kosongkan Resep?", fontWeight = FontWeight.Bold) },
            text = { Text("Semua bahan baku yang tersimpan untuk produk ini akan dihapus permanen.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBomProduct(product.id) {
                            Toast.makeText(context, "Resep berhasil dikosongkan", Toast.LENGTH_SHORT).show()
                            showClearConfirm = false
                            // Memicu LaunchedEffect untuk reload
                            existingBoms = emptyList() 
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("HAPUS SEMUA", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Batal") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text("Resep Produk (BOM)", fontWeight = FontWeight.Bold)
                Text(product.name, fontSize = 12.sp, color = EmeraldGreen, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                
                // --- FORM INPUT BAHAN ---
                Text("Tambah Bahan Baku:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                
                Box {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            selectedMaterial?.name ?: "Pilih Bahan...", 
                            modifier = Modifier.weight(1f), 
                            textAlign = TextAlign.Start,
                            fontSize = 13.sp
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded, 
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        if (rawMaterials.isEmpty()) {
                            DropdownMenuItem(text = { Text("Belum ada data bahan baku", fontSize = 12.sp, color = ErrorRed) }, onClick = { dropdownExpanded = false })
                        } else {
                            rawMaterials.forEach { material ->
                                DropdownMenuItem(
                                    text = { Text("${material.name} (Stok: ${material.stock} ${material.unit})", fontSize = 13.sp) },
                                    onClick = { selectedMaterial = material; dropdownExpanded = false }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = qtyUsed,
                        onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) qtyUsed = it },
                        label = { Text("Jumlah", fontSize = 12.sp) },
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
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Tambah", color = Color.White) }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // --- DAFTAR RESEP YANG AKAN DISIMPAN ---
                Text("Daftar Resep Baru:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                
                if (tempBomList.isEmpty()) {
                    Text("Belum ada bahan yang ditambahkan.", fontSize = 11.sp, color = SlateGrey, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 120.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(tempBomList) { index, item ->
                            val (material, qty) = item
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(material.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("$qty ${material.unit}", fontSize = 11.sp, color = EmeraldGreen)
                                    }
                                    IconButton(onClick = { tempBomList.removeAt(index) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = ErrorRed, modifier = Modifier.size(16.dp))
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
                        Text("Resep Tersimpan Sebelumnya:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        TextButton(onClick = { showClearConfirm = true }) {
                            Text("Kosongkan", fontSize = 11.sp, color = ErrorRed)
                        }
                    }
                    
                    LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                        itemsIndexed(existingBoms) { _, bom ->
                            val rm = rawMaterials.find { it.id == bom.rawMaterialId }
                            if (rm != null) {
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(rm.name, fontSize = 12.sp, color = SlateGrey)
                                    Text("${bom.quantityUsed} ${rm.unit}", fontSize = 12.sp, color = SlateGrey)
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
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(12.dp)
            ) { 
                Text(if (tempBomList.isEmpty()) "Tutup" else "SIMPAN RESEP", color = Color.White, fontWeight = FontWeight.Bold) 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = ErrorRed) }
        }
    )
}