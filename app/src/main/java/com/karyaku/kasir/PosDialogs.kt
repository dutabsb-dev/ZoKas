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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ====================================================================
// SESI 4 (POIN #2) & TEMA GELAP ADAPTIF — PosDialogs.kt:
//   1. Menambahkan opsi "Kelola Bahan Baku" pada PosActionMenuBottomSheet
//      agar user bisa langsung membuka manajemen stok bahan baku dari
//      menu klik-tahan produk di layar kasir.
//   2. Mengubah seluruh warna keras (EmeraldGreen, AmberAccent, SlateGrey,
//      ErrorRed) di seluruh dialog kasir menjadi warna semantik adaptif
//      MaterialTheme.colorScheme.* agar jelas dan kontras di tema gelap.
// ====================================================================

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
    onRawMaterial: (() -> Unit)? = null,
    onSaveBarcode: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            ActionMenuItem(
                icon = Icons.Default.Edit,
                title = "Edit Produk",
                subtitle = "Ubah detail, harga, varian & resep",
                color = MaterialTheme.colorScheme.primary,
                onClick = onEdit
            )
            ActionMenuItem(
                icon = Icons.Default.Add,
                title = "Tambah Stok",
                subtitle = "Restock barang jadi",
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onRestock
            )
            ActionMenuItem(
                icon = Icons.Default.DeleteSweep,
                title = "Barang Rusak",
                subtitle = "Catat waste/expired",
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onWaste
            )
            ActionMenuItem(
                icon = Icons.Default.ReceiptLong,
                title = "Atur Resep (BOM)",
                subtitle = "Takaran komposisi & kelola bahan baku",
                color = MaterialTheme.colorScheme.primary,
                onClick = onBom
            )
            if (onRawMaterial != null) {
                ActionMenuItem(
                    icon = Icons.Default.Inventory,
                    title = "Kelola Bahan Baku",
                    subtitle = "Tambah item & stok bahan mentah",
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = onRawMaterial
                )
            }
            ActionMenuItem(
                icon = Icons.Default.QrCode,
                title = "Simpan Barcode (JPG)",
                subtitle = "Simpan label ke galeri",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onSaveBarcode
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            ActionMenuItem(
                icon = Icons.Default.Delete,
                title = "Hapus Produk",
                subtitle = "Hapus permanen",
                color = MaterialTheme.colorScheme.error,
                onClick = onDelete
            )
        }
    }
}

@Composable
fun ActionMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
        icon = { Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Pilih Varian (${product.name})") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Spacer(modifier = Modifier.width(60.dp))
                    cols.forEach { col ->
                        Text(
                            col,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            row,
                            modifier = Modifier.width(60.dp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        cols.forEach { col ->
                            val variantName = if (col.isBlank()) row else "$row - $col"
                            Box(modifier = Modifier.weight(1f).padding(horizontal = 2.dp)) {
                                Button(
                                    onClick = { onSelect(variantName) },
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Pilih", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

// ====================================================================
// DIALOG VARIAN TEKS BIASA (UPDATED: HARGA VARIAN)
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
        icon = { Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Pilih Varian") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Silakan pilih varian untuk ${product.name}:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                variants.forEach { variantRaw ->
                    val vName = variantRaw.substringBefore(":").trim()
                    val vPrice = getPriceForVariant(product, vName)

                    OutlinedButton(
                        onClick = { onSelect(vName) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                vName,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                formatRupiah(vPrice),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        icon = { Icon(Icons.Default.Merge, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Gabung Meja (Merge)") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Pilih Meja Utama (Tujuan):", color = MaterialTheme.colorScheme.onSurface)
                tables.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = tableA == t,
                            onClick = { tableA = t },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text(t, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Pilih Meja yang Digabung:", color = MaterialTheme.colorScheme.onSurface)
                tables.forEach { t ->
                    if (t != tableA) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = tableB == t,
                                onClick = { tableB = t },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text(t, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onMerge(tableA, tableB) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) { Text("GABUNG") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        icon = { Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Input IMEI / Serial Number") },
        text = {
            Column {
                Text("Masukkan nomor seri untuk ${product.name}:", color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = serial,
                    onValueChange = { serial = it },
                    label = { Text("Serial Number") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(serial) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) { Text("SIMPAN") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        icon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
        title = { Text("Tugaskan Staf / Terapis") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Pilih staf untuk ${product.name}:", color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                staffList.forEach { user ->
                    OutlinedButton(
                        onClick = { onAssign(user.name) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(user.name, color = MaterialTheme.colorScheme.onSurface) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Laporan Tutup Kasir") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("Kasir: ${shift.userName}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Waktu Buka: ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(shift.startTime))}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Modal Awal:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatRupiah(shift.openingCash), color = MaterialTheme.colorScheme.onSurface)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kas Sistem:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatRupiah(shift.expectedCash), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kas Fisik:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatRupiah(shift.closingCash ?: 0L), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Selisih:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    val selisih = shift.discrepancy ?: 0L
                    Text(
                        formatRupiah(selisih),
                        color = if (selisih < 0) MaterialTheme.colorScheme.error else if (selisih > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) { Text("LOGOUT") }
        }
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
        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
        title = { Text("Otorisasi Diskon") },
        text = {
            Column {
                Text("Masukkan PIN Supervisor/Owner:", color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onVerify(pin) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) { Text("VERIFIKASI") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Buka Laci Kasir") },
        text = { Text("Buka laci tanpa transaksi? Aktivitas ini akan dicatat.", color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) { Text("BUKA") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Catat Barang Rusak") },
        text = {
            Column {
                Text("Produk: ${product.name}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Stok: ${product.stock} pcs", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = qty,
                    onValueChange = { if (it.length <= 5) qty = it },
                    label = { Text("Jumlah Rusak") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(qty.toIntOrNull() ?: 0) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp)
            ) { Text("SIMPAN") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Catatan Pesanan") },
        text = {
            Column {
                Text("Catatan untuk ${product.name}:", color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Cth: Less Ice") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(notes) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) { Text("SIMPAN") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

// ====================================================================
// DIALOG BUKA KASIR (OPEN SHIFT)
// ====================================================================
@Composable
fun OpenShiftDialog(
    modalAwal: String,
    onModalChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    val parsedAmount = modalAwal.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val presets = listOf(
        0L to "Rp 0",
        100_000L to "100rb",
        200_000L to "200rb",
        500_000L to "500rb"
    )

    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        icon = { Icon(Icons.Default.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                "Buka Kasir (Shift Baru)",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Masukkan jumlah uang tunai (modal awal kembalian) yang ada di laci kasir saat ini:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { (valAmount, label) ->
                        val isSelected = parsedAmount == valAmount && modalAwal.isNotEmpty()
                        OutlinedButton(
                            onClick = { onModalChange(valAmount.toString()) },
                            modifier = Modifier.weight(1f).height(34.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = modalAwal,
                    onValueChange = { raw ->
                        val digitsOnly = raw.filter { it.isDigit() }
                        if (digitsOnly.length <= 10) onModalChange(digitsOnly)
                    },
                    label = { Text("Modal Awal (Rp)") },
                    placeholder = { Text("0") },
                    trailingIcon = {
                        if (modalAwal.isNotEmpty()) {
                            IconButton(onClick = { onModalChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Reset", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Konfirmasi Modal Awal:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatRupiah(parsedAmount),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("MULAI SHIFT", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = if (onDismiss != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text("Tutup", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else null
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
        icon = { Icon(Icons.Default.AccountBox, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
        title = { Text("Kas Keluar / Masuk") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { type = "OUT" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "OUT") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (type == "OUT") MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Keluar") }
                    Button(
                        onClick = { type = "IN" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "IN") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (type == "IN") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Masuk") }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.length <= 10) amount = it },
                    label = { Text("Nominal (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Keterangan") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(type, amount.toLongOrNull() ?: 0L, note) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (type == "OUT") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    contentColor = if (type == "OUT") MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) { Text("SIMPAN") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        icon = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Diskon Manual") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Masukkan nominal:", color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.length <= 10) amount = it },
                    label = { Text("Nominal (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(amount.toLongOrNull() ?: 0L) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) { Text("SIMPAN") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        icon = { Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
        title = { Text("Pilih Meja") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                for (table in tables) {
                    val isOccupied = occupiedTables.contains(table)
                    val isCurrent = table == currentTable
                    OutlinedButton(
                        onClick = { onSelect(table); onDismiss() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isCurrent)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else if (isOccupied)
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            else
                                Color.Transparent
                        ),
                        border = if (isOccupied) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isOccupied) "$table (Terisi)" else table,
                            color = if (isOccupied)
                                MaterialTheme.colorScheme.error
                            else if (isCurrent)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isOccupied || isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        icon = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Gunakan Voucher") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Kode Voucher") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(code) },
                enabled = code.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) { Text("TERAPKAN") }
        },
        dismissButton = {
            if (currentCode.isNotEmpty()) {
                TextButton(onClick = onRemove) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Tutup", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
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
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Hapus Produk?") },
        text = { Text("Hapus '${product.name}' secara permanen?", color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = {
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp)
            ) { Text("HAPUS") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
