package com.karyaku.kasir

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.ShiftEntity
import com.karyaku.kasir.data.local.UserEntity
import com.karyaku.kasir.data.local.RawMaterialEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ====================================================================
// MENU AKSI PRODUK (BOTTOM SHEET)
// ====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosActionMenuBottomSheet(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRestock: () -> Unit,
    onWaste: () -> Unit,
    onBom: () -> Unit,
    onSaveBarcode: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(modifier = Modifier.padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
            Text(text = product.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
            ActionMenuItem(icon = Icons.Default.Edit, title = "Edit Produk", subtitle = "Ubah detail", color = EmeraldGreen, onClick = onEdit)
            ActionMenuItem(icon = Icons.Default.Add, title = "Tambah Stok", subtitle = "Restock barang", color = AmberAccent, onClick = onRestock)
            ActionMenuItem(icon = Icons.Default.Delete, title = "Barang Rusak", subtitle = "Catat waste/expired", color = AmberAccentDark, onClick = onWaste)
            ActionMenuItem(icon = Icons.Default.List, title = "Atur Resep (BOM)", subtitle = "Bahan baku & komposisi", color = EmeraldGreenLight, onClick = onBom)
            ActionMenuItem(icon = Icons.Default.List, title = "Simpan Barcode (JPG)", subtitle = "Simpan label ke galeri", color = SlateGrey, onClick = onSaveBarcode)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            ActionMenuItem(icon = Icons.Default.Delete, title = "Hapus Produk", subtitle = "Hapus permanen", color = ErrorRed, onClick = onDelete)
        }
    }
}

@Composable
fun ActionMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp)) }
        Spacer(modifier = Modifier.width(16.dp))
        Column { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(subtitle, fontSize = 12.sp, color = SlateGrey) }
    }
}

// ====================================================================
// DIALOG MATRIX 2D
// ====================================================================
@Composable
fun ProductMatrixDialog(
    product: ProductEntity,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val parts = product.variations.split("|")
    val rows = parts[0].split(",").map { it.trim() }
    val cols = if (parts.size > 1) parts[1].split(",").map { it.trim() } else listOf("")

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.List, contentDescription = null, tint = EmeraldGreen) },
        title = { Text("Pilih Varian (${product.name})") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Spacer(modifier = Modifier.width(60.dp))
                    cols.forEach { col -> Text(col, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) }
                }
                rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(row, modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold)
                        cols.forEach { col ->
                            val variantName = if (col.isBlank()) row else "$row - $col"
                            Box(modifier = Modifier.weight(1f).padding(horizontal = 2.dp)) {
                                Button(
                                    onClick = { onSelect(variantName) },
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen.copy(alpha = 0.1f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Pilih", fontSize = 10.sp, color = EmeraldGreen)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ====================================================================
// DIALOG VARIAN TEKS BIASA
// ====================================================================
@Composable
fun ProductVariantDialog(
    product: ProductEntity,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val variants = product.variations.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.List, contentDescription = null, tint = EmeraldGreen) },
        title = { Text("Pilih Varian") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Silakan pilih varian untuk ${product.name}:", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                variants.forEach { variant ->
                    OutlinedButton(
                        onClick = { onSelect(variant) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(variant, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ====================================================================
// DIALOG GABUNG MEJA
// ====================================================================
@Composable
fun MergeTableDialog(
    onMerge: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var tableA by remember { mutableStateOf("Meja 1") }
    var tableB by remember { mutableStateOf("Meja 2") }
    val tables = listOf("Meja 1", "Meja 2", "Meja 3", "Meja 4", "Meja 5")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Merge, contentDescription = null, tint = EmeraldGreen) },
        title = { Text("Gabung Meja (Merge)") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Pilih Meja Utama (Tujuan):")
                tables.forEach { t -> 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = tableA == t, onClick = { tableA = t }, colors = RadioButtonDefaults.colors(selectedColor = EmeraldGreen))
                        Text(t)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Pilih Meja yang Digabung:")
                tables.forEach { t -> 
                    if (t != tableA) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = tableB == t, onClick = { tableB = t }, colors = RadioButtonDefaults.colors(selectedColor = EmeraldGreen))
                            Text(t)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onMerge(tableA, tableB) }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text("GABUNG") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ====================================================================
// DIALOG SERIAL NUMBER
// ====================================================================
@Composable
fun SerialNumberDialog(
    product: ProductEntity,
    currentSerial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var serial by remember { mutableStateOf(currentSerial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.QrCode, contentDescription = null) },
        title = { Text("Input IMEI / Serial Number") },
        text = {
            Column {
                Text("Masukkan nomor seri untuk ${product.name}:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = serial, onValueChange = { serial = it }, label = { Text("Serial Number") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = { Button(onClick = { onSave(serial) }, shape = RoundedCornerShape(12.dp)) { Text("SIMPAN") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ====================================================================
// DIALOG ASSIGN STAFF
// ====================================================================
@Composable
fun AssignStaffDialog(
    product: ProductEntity,
    users: List<UserEntity>,
    onAssign: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val staffList = users.filter { it.role != "OWNER" }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Person, contentDescription = null) },
        title = { Text("Tugaskan Staf / Terapis") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Pilih staf untuk ${product.name}:")
                Spacer(modifier = Modifier.height(8.dp))
                staffList.forEach { user ->
                    OutlinedButton(
                        onClick = { onAssign(user.name) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(user.name) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ====================================================================
// DIALOG LAPORAN TUTUP KASIR
// ====================================================================
@Composable
fun ShiftReportDialog(
    shift: ShiftEntity,
    onLogout: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onLogout() },
        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen) },
        title = { Text("Laporan Tutup Kasir") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("Kasir: ${shift.userName}", fontWeight = FontWeight.Bold)
                Text("Waktu Buka: ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(shift.startTime))}")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Modal Awal:"); Text(formatRupiah(shift.openingCash)) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Kas Sistem:"); Text(formatRupiah(shift.expectedCash), color = EmeraldGreen, fontWeight = FontWeight.Bold) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Kas Fisik:"); Text(formatRupiah(shift.closingCash ?: 0L), fontWeight = FontWeight.Bold) }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Selisih:", fontWeight = FontWeight.Bold)
                    val selisih = shift.discrepancy ?: 0L
                    Text(formatRupiah(selisih), color = if (selisih < 0) ErrorRed else if (selisih > 0) SlateGrey else EmeraldGreen, fontWeight = FontWeight.ExtraBold)
                }
            }
        },
        confirmButton = { Button(onClick = onLogout, shape = RoundedCornerShape(12.dp)) { Text("LOGOUT") } }
    )
}

// ====================================================================
// DIALOG PIN DISKON
// ====================================================================
@Composable
fun PinDiscountDialog(
    onVerify: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = AmberAccent) },
        title = { Text("Otorisasi Diskon") },
        text = {
            Column {
                Text("Masukkan PIN Supervisor/Owner:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it }, label = { Text("PIN") }, 
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), 
                    visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(onClick = { onVerify(pin) }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text("VERIFIKASI") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ====================================================================
// DIALOG BUKA LACI
// ====================================================================
@Composable
fun CashDrawerConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
        title = { Text("Buka Laci Kasir") },
        text = { Text("Buka laci tanpa transaksi? Aktivitas ini akan dicatat.") },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text("BUKA") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ====================================================================
// DIALOG CATAT WASTE
// ====================================================================
@Composable
fun WasteRecordDialog(
    product: ProductEntity,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var qty by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed) },
        title = { Text("Catat Barang Rusak") },
        text = {
            Column {
                Text("Produk: ${product.name}", fontWeight = FontWeight.Bold)
                Text("Stok: ${product.stock} pcs", color = SlateGrey)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = qty, onValueChange = { if (it.length <= 5) qty = it }, label = { Text("Jumlah Rusak") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = { Button(onClick = { onSave(qty.toIntOrNull() ?: 0) }, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed), shape = RoundedCornerShape(12.dp)) { Text("SIMPAN") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ====================================================================
// DIALOG CATATAN PESANAN
// ====================================================================
@Composable
fun OrderNotesDialog(
    product: ProductEntity,
    currentNotes: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var notes by remember { mutableStateOf(currentNotes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
        title = { Text("Catatan Pesanan") },
        text = {
            Column {
                Text("Catatan untuk ${product.name}:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Cth: Less Ice") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = { Button(onClick = { onSave(notes) }, shape = RoundedCornerShape(12.dp)) { Text("SIMPAN") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ====================================================================
// DIALOG BUKA KASIR (OPEN SHIFT)
// ====================================================================
@Composable
fun OpenShiftDialog(
    modalAwal: String,
    onModalChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = EmeraldGreen) },
        title = { Text("Buka Kasir") },
        text = {
            Column {
                Text("Masukkan modal awal:")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = modalAwal, onValueChange = onModalChange, label = { Text("Modal Awal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = AmberAccent), shape = RoundedCornerShape(12.dp)) { Text("MULAI") } }
    )
}

// ====================================================================
// DIALOG KAS MASUK / KELUAR
// ====================================================================
@Composable
fun CashFlowDialog(
    onSave: (String, Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("OUT") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AccountBox, contentDescription = null, tint = AmberAccent) },
        title = { Text("Kas Keluar / Masuk") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { 
                    Button(onClick = { type = "OUT" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (type == "OUT") AmberAccent else SlateGrey), shape = RoundedCornerShape(12.dp)) { Text("Keluar") }
                    Button(onClick = { type = "IN" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (type == "IN") EmeraldGreen else SlateGrey), shape = RoundedCornerShape(12.dp)) { Text("Masuk") } 
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = amount, onValueChange = { if (it.length <= 10) amount = it }, label = { Text("Nominal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Keterangan") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = { Button(onClick = { onSave(type, amount.toLongOrNull() ?: 0L, note) }, colors = ButtonDefaults.buttonColors(containerColor = if (type == "OUT") AmberAccent else EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text("SIMPAN") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ====================================================================
// DIALOG DISKON MANUAL
// ====================================================================
@Composable
fun ManualDiscountDialog(
    currentDiscount: Long,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf(if (currentDiscount > 0) currentDiscount.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Star, contentDescription = null, tint = EmeraldGreen) },
        title = { Text("Diskon Manual") },
        text = { 
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) { 
                Text("Masukkan nominal:"); Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = amount, onValueChange = { if (it.length <= 10) amount = it }, label = { Text("Nominal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) 
            } 
        },
        confirmButton = { Button(onClick = { onSave(amount.toLongOrNull() ?: 0L) }, colors = ButtonDefaults.buttonColors(containerColor = AmberAccent), shape = RoundedCornerShape(12.dp)) { Text("SIMPAN") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ====================================================================
// DIALOG PILIH MEJA
// ====================================================================
@Composable
fun TableSelectorDialog(
    currentTable: String,
    occupiedTables: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val tables = listOf("Take-Away", "Meja 1", "Meja 2", "Meja 3", "Meja 4", "Meja 5")
    AlertDialog(
        onDismissRequest = onDismiss, 
        icon = { Icon(Icons.Default.List, contentDescription = null, tint = AmberAccent) },
        title = { Text("Pilih Meja") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                for (table in tables) {
                    val isOccupied = occupiedTables.contains(table)
                    OutlinedButton(
                        onClick = { onSelect(table); onDismiss() }, 
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), 
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (table == currentTable) EmeraldGreen.copy(alpha = 0.2f) else if (isOccupied) ErrorRed.copy(alpha = 0.1f) else Color.Transparent), 
                        border = if (isOccupied) BorderStroke(1.dp, ErrorRed) else null, 
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = if (isOccupied) "$table (Terisi)" else table, color = if (isOccupied) ErrorRed else if (table == currentTable) EmeraldGreen else MaterialTheme.colorScheme.onSurface, fontWeight = if (isOccupied) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

// ====================================================================
// DIALOG VOUCHER
// ====================================================================
@Composable
fun VoucherDialog(
    currentCode: String,
    onApply: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf(currentCode) }
    AlertDialog(
        onDismissRequest = onDismiss, 
        icon = { Icon(Icons.Default.Star, contentDescription = null, tint = EmeraldGreen) },
        title = { Text("Gunakan Voucher") },
        text = { 
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) { 
                OutlinedTextField(value = code, onValueChange = { code = it.uppercase() }, label = { Text("Kode Voucher") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)) 
            } 
        },
        confirmButton = { Button(onClick = { onApply(code) }, enabled = code.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text("TERAPKAN") } },
        dismissButton = { if (currentCode.isNotEmpty()) TextButton(onClick = onRemove) { Text("Hapus", color = ErrorRed) } else TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

// ====================================================================
// DIALOG HAPUS PRODUK
// ====================================================================
@Composable
fun DeleteProductConfirmDialog(
    product: ProductEntity,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed) },
        title = { Text("Hapus Produk?") },
        text = { Text("Hapus '${product.name}' secara permanen?") },
        confirmButton = { Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed), shape = RoundedCornerShape(12.dp)) { Text("HAPUS") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ====================================================================
// DIALOG MANAJEMEN BOM (RESEP)
// ====================================================================
@Composable
fun BomManagementDialog(
    product: ProductEntity,
    rawMaterials: List<RawMaterialEntity>,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    var selectedMaterial by remember { mutableStateOf<RawMaterialEntity?>(null) }
    var qtyUsed by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.List, contentDescription = null, tint = EmeraldGreenLight) },
        title = { Text("Resep (BOM): ${product.name}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Tambahkan bahan baku yang digunakan untuk membuat produk ini.", fontSize = 12.sp, color = SlateGrey)
                Spacer(modifier = Modifier.height(12.dp))
                
                if (rawMaterials.isEmpty()) {
                    Surface(color = ErrorRed.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Belum ada data Bahan Baku. Tambahkan di menu Pengaturan.", fontSize = 12.sp, color = ErrorRed, modifier = Modifier.padding(12.dp))
                    }
                } else {
                    Text("Pilih Bahan Baku:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    rawMaterials.forEach { material ->
                        OutlinedButton(
                            onClick = { selectedMaterial = material },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selectedMaterial?.id == material.id) EmeraldGreen.copy(alpha = 0.2f) else Color.Transparent
                            )
                        ) {
                            Text("${material.name} (Sisa: ${material.stock} ${material.unit})")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = qtyUsed,
                        onValueChange = { if (it.length <= 5) qtyUsed = it },
                        label = { Text("Jumlah Digunakan (${selectedMaterial?.unit ?: "Pilih bahan dulu"})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedMaterial != null,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = qtyUsed.toDoubleOrNull() ?: 0.0
                    if (selectedMaterial != null && qty > 0) {
                        viewModel.addBom(product.id, selectedMaterial!!.id, qty)
                        onDismiss()
                    }
                },
                enabled = selectedMaterial != null && qtyUsed.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(12.dp)
            ) { Text("SIMPAN RESEP") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}