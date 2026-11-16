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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    
    // State untuk Form Tambah Bahan Baku Langsung
    var showAddRawMaterial by remember { mutableStateOf(false) }
    var newMaterialName by remember { mutableStateOf("") }
    var newMaterialStock by remember { mutableStateOf("") }
    var newMaterialUnit by remember { mutableStateOf("gram") }
    var newMaterialCost by remember { mutableStateOf("") }

    // State untuk Tambah Resep ke Produk
    var selectedMaterial by remember { mutableStateOf<RawMaterialEntity?>(null) }
    var qtyUsed by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text("Kelola Resep (BOM)", fontWeight = FontWeight.Bold)
                Text(product.name, fontSize = 12.sp, color = EmeraldGreen)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                
                if (showAddRawMaterial) {
                    // FORM TAMBAH BAHAN BAKU BARU (Tanpa keluar dialog)
                    Text("Tambah Bahan Baku Baru", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newMaterialName, onValueChange = { newMaterialName = it }, label = { Text("Nama Bahan (cth: Beras)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = newMaterialStock, onValueChange = { newMaterialStock = it }, label = { Text("Stok Awal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = newMaterialUnit, onValueChange = { newMaterialUnit = it }, label = { Text("Satuan (gram/pcs)") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newMaterialCost, onValueChange = { newMaterialCost = it }, label = { Text("Harga Modal per Satuan (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showAddRawMaterial = false }) { Text("Batal") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newMaterialName.isNotBlank() && newMaterialStock.isNotBlank() && newMaterialCost.isNotBlank()) {
                                    viewModel.addRawMaterial(
                                        name = newMaterialName,
                                        stock = newMaterialStock.toDoubleOrNull() ?: 0.0,
                                        unit = newMaterialUnit,
                                        costPerUnit = newMaterialCost.toLongOrNull() ?: 0L
                                    )
                                    Toast.makeText(context, "Bahan baku ditambahkan!", Toast.LENGTH_SHORT).show()
                                    showAddRawMaterial = false
                                    newMaterialName = ""
                                    newMaterialStock = ""
                                    newMaterialCost = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                        ) { Text("SIMPAN BAHAN", color = Color.White) }
                    }
                } else {
                    // FORM TAMBAH RESEP
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Daftar Bahan yang Dipakai:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        TextButton(onClick = { showAddRawMaterial = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Bahan Baru", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Box {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(selectedMaterial?.name ?: "Pilih Bahan Baku...", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                            if (rawMaterials.isEmpty()) {
                                DropdownMenuItem(text = { Text("Belum ada bahan baku") }, onClick = { dropdownExpanded = false })
                            } else {
                                rawMaterials.forEach { material ->
                                    DropdownMenuItem(
                                        text = { Text("${material.name} (Stok: ${material.stock} ${material.unit})") },
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
                            onValueChange = { qtyUsed = it },
                            label = { Text("Jumlah Pakai") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (selectedMaterial != null && qtyUsed.isNotBlank()) {
                                    viewModel.addBom(product.id, selectedMaterial!!.id, qtyUsed.toDoubleOrNull() ?: 0.0)
                                    Toast.makeText(context, "Resep ditambahkan!", Toast.LENGTH_SHORT).show()
                                    qtyUsed = ""
                                }
                            },
                            enabled = selectedMaterial != null && qtyUsed.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            modifier = Modifier.height(56.dp)
                        ) { Text("Tambah", color = Color.White) }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("Resep Saat Ini:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val currentBom by remember { mutableStateOf(emptyList<Any>()) } // Placeholder: Di aplikasi nyata, ambil dari ViewModel/Dao
                    // Catatan: Karena fitur getBomForProduct tidak di-observe dalam Flow di ViewModel utama, 
                    // UI ini hanya berfokus pada penambahan bahan baku baru agar praktis.
                    Text("Silakan tambahkan resep di atas. Fitur hapus resep bisa ditambahkan di masa depan.", fontSize = 11.sp, color = SlateGrey)
                }
            }
        },
        confirmButton = {
            if (!showAddRawMaterial) {
                TextButton(onClick = onDismiss) { Text("Tutup") }
            }
        }
    )
}