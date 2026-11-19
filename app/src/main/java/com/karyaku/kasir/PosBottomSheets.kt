package com.karyaku.kasir

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.karyaku.kasir.data.local.ProductEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// =====================================================================
// SESI 1b, SESI 2a, & SESI 4 (POIN #1, #2, #5, #6):
//   1. ProductFormBottomSheet:
//      - Menambahkan tombol akses langsung "Atur Resep (BOM) & Bahan Baku"
//        saat mengedit produk (onManageBom), sehingga user bisa langsung
//        mengatur resep dan stok bahan baku dari form Edit Produk.
//      - Semua warna keras (SlateGrey, EmeraldGreen, AmberAccent, ErrorRed)
//        diganti ke MaterialTheme.colorScheme.* adaptif.
//   2. PaymentBottomSheet (Sesi 2a):
//      - Menghilangkan spinner "Menunggu notifikasi transfer masuk..." (VA)
//      - Mengganti link kecil konfirmasi manual dengan Checkbox jelas
//        "Saya sudah menerima transfer sesuai nominal" yang mengaktifkan
//        tombol SELESAIKAN PEMBAYARAN.
//      - Semua warna keras diganti ke MaterialTheme.colorScheme.* adaptif.
// =====================================================================

// =====================================================================
// DATA CLASS UNTUK VARIAN DINAMIS
// =====================================================================
data class VariantInput(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var costPrice: String = "",
    var price: String = "",
    var stock: String = "",
    var packMultiplier: String = "1"
)

// =====================================================================
// 1. PRODUCT FORM BOTTOM SHEET
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormBottomSheet(
    productToEdit: ProductEntity? = null,
    onDismiss: () -> Unit,
    onManageBom: ((ProductEntity) -> Unit)? = null,
    onManageRawMaterial: (() -> Unit)? = null,
    onSave: (
        name: String, price: Long, stock: Int, category: String, costPrice: Long,
        barcode: String, variations: String, commission: Long, expiry: Long,
        isService: Boolean, isCombo: Boolean, imagePath: String,
        wholesalePrice: Long, wholesaleMinQty: Int
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // STATE FORM
    var name by remember { mutableStateOf(productToEdit?.name ?: "") }
    var productSku by remember { mutableStateOf(productToEdit?.let { extractProductSku(it.barcode) } ?: "") }
    var barcode by remember { mutableStateOf(productToEdit?.let { extractActualBarcode(it.barcode) } ?: "") }
    var category by remember { mutableStateOf(productToEdit?.category ?: "Umum") }
    var baseUnit by remember { mutableStateOf(productToEdit?.let { extractProductBaseUnit(it) } ?: "pcs") }
    var unitDropdownExpanded by remember { mutableStateOf(false) }
    var imagePath by remember { mutableStateOf(productToEdit?.imagePath ?: "") }

    // State Varian
    var hasVariants by remember { mutableStateOf(false) }
    var variantList by remember { mutableStateOf<List<VariantInput>>(emptyList()) }

    // State Tanpa Varian
    var singleCost by remember { mutableStateOf(productToEdit?.costPrice?.toString() ?: "") }
    var singlePrice by remember { mutableStateOf(productToEdit?.price?.toString() ?: "") }
    var singleStock by remember { mutableStateOf(productToEdit?.stock?.toString() ?: "") }

    // State Tambahan
    var commission by remember { mutableStateOf(productToEdit?.commissionPerItem?.toString() ?: "0") }
    var wholesalePrice by remember { mutableStateOf(productToEdit?.wholesalePrice?.toString() ?: "0") }
    var wholesaleMinQty by remember { mutableStateOf(productToEdit?.wholesaleMinQty?.toString() ?: "0") }
    var expiry by remember { mutableStateOf(productToEdit?.expiryDate ?: 0L) }
    var isService by remember { mutableStateOf(productToEdit?.isService ?: false) }
    var isCombo by remember { mutableStateOf(productToEdit?.isCombo ?: false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Parse data varian saat pertama kali dibuka
    LaunchedEffect(productToEdit) {
        if (productToEdit != null && productToEdit.variations.isNotBlank()) {
            hasVariants = true
            val parsed = parseVariationString(productToEdit.variations, productToEdit.stock)
            variantList = if (parsed.isNotEmpty()) parsed else listOf(VariantInput())
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = File(context.filesDir, "product_img_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                imagePath = file.absolutePath
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memuat gambar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = if (expiry > 0) expiry else System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { expiry = datePickerState.selectedDateMillis ?: 0L; showDatePicker = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Batal") } }
        ) { DatePicker(state = datePickerState) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (productToEdit == null) "Tambah Produk Baru" else "Edit Produk",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // --- FOTO PRODUK ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (imagePath.isNotEmpty() && File(imagePath).exists()) {
                    AsyncImage(
                        model = File(imagePath),
                        contentDescription = "Foto Produk",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AddAPhoto,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Pilih Foto",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- SESI 4 (POIN #2): AKSES CEPAT RESEP (BOM) & BAHAN BAKU DI FORM PRODUK ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Science,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Resep (BOM) & Bahan Baku",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (productToEdit != null)
                            "Atur takaran resep produk ini atau kelola stok bahan baku secara langsung."
                        else
                            "Anda dapat menyiapkan daftar bahan baku sekarang, lalu mengatur resep setelah produk disimpan.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (productToEdit != null && onManageBom != null) {
                            Button(
                                onClick = { onManageBom(productToEdit) },
                                modifier = Modifier.weight(1f).height(42.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Atur Resep (BOM)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (onManageRawMaterial != null) {
                            OutlinedButton(
                                onClick = onManageRawMaterial,
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.tertiary
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kelola Bahan Baku", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- CHECKBOX JASA / COMBO ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { isService = !isService }
            ) {
                Checkbox(
                    checked = isService,
                    onCheckedChange = { isService = it },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                Text(
                    "Produk Jasa (Tanpa Stok)",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { isCombo = !isCombo }
            ) {
                Checkbox(
                    checked = isCombo,
                    onCheckedChange = { isCombo = it },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                Text(
                    "Produk Combo/Paket",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- FIELD UTAMA ---
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama Produk *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Kategori") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = productSku,
                onValueChange = { productSku = it.uppercase() },
                label = { Text("Kode Unik / SKU (cth: KOP-01)") },
                placeholder = { Text("Bisa dicari di Kasir") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val catPrefix = category.trim().take(3).uppercase().ifBlank { "PRD" }
                            val namePrefix = name.trim().split(Regex("\\s+"))
                                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                .joinToString("")
                                .take(3)
                                .ifBlank { "X" }
                            val randNum = (10..99).random()
                            productSku = "$catPrefix-$namePrefix$randNum"
                        }
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Buat Kode Otomatis",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Text(
                text = "Tips: Ketuk ikon bintang untuk membuat Kode Unik otomatis, atau ketik kode sendiri.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 6.dp)
            )
            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it },
                label = { Text("Barcode Kemasan (Opsional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- TOGGLE VARIAN ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable {
                    hasVariants = !hasVariants
                    if (hasVariants && variantList.isEmpty()) variantList = listOf(VariantInput())
                }
            ) {
                Checkbox(
                    checked = hasVariants,
                    onCheckedChange = {
                        hasVariants = it
                        if (it && variantList.isEmpty()) variantList = listOf(VariantInput())
                    },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                Text(
                    "Produk ini memiliki Varian (Ukuran/Rasa, dll)",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- JIKA TANPA VARIAN ---
            if (!hasVariants) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = singleCost,
                        onValueChange = { singleCost = it },
                        label = { Text("Harga Beli") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = singlePrice,
                        onValueChange = { singlePrice = it },
                        label = { Text("Harga Jual") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                if (!isService) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = singleStock,
                            onValueChange = { singleStock = it },
                            label = { Text("Stok Awal") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1.4f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { unitDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Satuan: $baseUnit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = unitDropdownExpanded,
                                onDismissRequest = { unitDropdownExpanded = false },
                                modifier = Modifier.heightIn(max = 260.dp)
                            ) {
                                AVAILABLE_STOCK_UNITS.forEach { u ->
                                    DropdownMenuItem(
                                        text = { Text(u, fontWeight = if (u == baseUnit) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = { baseUnit = u; unitDropdownExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // --- JIKA DENGAN VARIAN (BARIS DINAMIS + SATUAN & KONVERSI KEMASAN) ---
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Daftar Varian & Satuan Terkecil",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val previewTotalSmallest = variantList.sumOf {
                            val q = it.stock.toIntOrNull() ?: 0
                            val m = (it.packMultiplier.toIntOrNull() ?: 1).coerceAtLeast(1)
                            q * m
                        }
                        if (!isService) {
                            Text(
                                text = "Total Stok Gabungan: $previewTotalSmallest $baseUnit",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    Box {
                        OutlinedButton(
                            onClick = { unitDropdownExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Satuan: $baseUnit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = unitDropdownExpanded,
                            onDismissRequest = { unitDropdownExpanded = false },
                            modifier = Modifier.heightIn(max = 260.dp)
                        ) {
                            AVAILABLE_STOCK_UNITS.forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u) },
                                    onClick = { baseUnit = u; unitDropdownExpanded = false }
                                )
                            }
                        }
                    }
                }

                variantList.forEachIndexed { index, variant ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = variant.name,
                                    onValueChange = { newName ->
                                        val newList = variantList.toMutableList()
                                        newList[index] = variant.copy(name = newName)
                                        variantList = newList
                                    },
                                    label = { Text("Nama Varian (cth: Jumbo)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                IconButton(onClick = {
                                    val newList = variantList.toMutableList()
                                    newList.removeAt(index)
                                    variantList = newList
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Hapus",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = variant.costPrice,
                                    onValueChange = {
                                        val newList = variantList.toMutableList()
                                        newList[index] = variant.copy(costPrice = it)
                                        variantList = newList
                                    },
                                    label = { Text("Harga Beli") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                OutlinedTextField(
                                    value = variant.price,
                                    onValueChange = {
                                        val newList = variantList.toMutableList()
                                        newList[index] = variant.copy(price = it)
                                        variantList = newList
                                    },
                                    label = { Text("Harga Jual") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                            if (!isService) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = variant.stock,
                                        onValueChange = {
                                            val newList = variantList.toMutableList()
                                            newList[index] = variant.copy(stock = it)
                                            variantList = newList
                                        },
                                        label = { Text("Jumlah Stok") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    OutlinedTextField(
                                        value = variant.packMultiplier,
                                        onValueChange = {
                                            val digits = it.filter { c -> c.isDigit() }
                                            val newList = variantList.toMutableList()
                                            newList[index] = variant.copy(packMultiplier = digits)
                                            variantList = newList
                                        },
                                        label = { Text("Isi / Kemasan ($baseUnit)") },
                                        placeholder = { Text("1") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                                val vQty = variant.stock.toIntOrNull() ?: 0
                                val vMult = (variant.packMultiplier.toIntOrNull() ?: 1).coerceAtLeast(1)
                                if (vMult > 1) {
                                    Text(
                                        text = "Konversi satuan terkecil: $vQty x $vMult = ${vQty * vMult} $baseUnit",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { variantList = variantList + VariantInput() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Tambah Baris Varian",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- FIELD TAMBAHAN (GROSIR & KOMISI) ---
            if (!isService) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = wholesalePrice,
                        onValueChange = { wholesalePrice = it },
                        label = { Text("Harga Grosir") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = wholesaleMinQty,
                        onValueChange = { wholesaleMinQty = it },
                        label = { Text("Min Qty Grosir") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = commission,
                onValueChange = { commission = it },
                label = { Text("Komisi Kasir / Item (Rp)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- EXPIRY ---
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val expiryText = if (expiry > 0) dateFormat.format(Date(expiry)) else "Pilih Tanggal"
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Expiry: $expiryText", color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- TOMBOL SIMPAN ---
            Button(
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "Nama produk wajib diisi!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    var finalPrice = 0L
                    var finalCost = 0L
                    var finalStock = 0
                    var finalVariations = ""

                    if (hasVariants) {
                        if (variantList.isEmpty() || variantList.any { it.name.isBlank() }) {
                            Toast.makeText(context, "Nama varian tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        // Rakit string: Nama:HargaJual:HargaBeli:Stok:IsiPerKemasan
                        val variationParts = variantList.map {
                            val p = it.price.toLongOrNull() ?: 0L
                            val c = it.costPrice.toLongOrNull() ?: 0L
                            val s = if (isService) 0 else (it.stock.toIntOrNull() ?: 0)
                            val m = (it.packMultiplier.toIntOrNull() ?: 1).coerceAtLeast(1)
                            "${it.name.trim()}:$p:$c:$s:$m"
                        }
                        finalVariations = variationParts.joinToString(",")

                        finalStock = if (isService) 0 else variantList.sumOf {
                            val s = it.stock.toIntOrNull() ?: 0
                            val m = (it.packMultiplier.toIntOrNull() ?: 1).coerceAtLeast(1)
                            s * m
                        }
                        finalPrice = variantList.firstOrNull()?.price?.toLongOrNull() ?: 0L
                        finalCost = variantList.firstOrNull()?.costPrice?.toLongOrNull() ?: 0L

                    } else {
                        finalPrice = singlePrice.toLongOrNull() ?: 0L
                        finalCost = singleCost.toLongOrNull() ?: 0L
                        finalStock = if (isService) 0 else (singleStock.toIntOrNull() ?: 0)
                        finalVariations = ""
                    }

                    if (finalPrice <= 0) {
                        Toast.makeText(context, "Harga jual harus lebih dari 0!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val combinedCode = packSkuAndBarcode(productSku, barcode, baseUnit)
                    onSave(
                        name.trim(), finalPrice, finalStock, category.trim().ifBlank { "Umum" }, finalCost,
                        combinedCode, finalVariations, commission.toLongOrNull() ?: 0L, expiry,
                        isService, isCombo, imagePath,
                        wholesalePrice.toLongOrNull() ?: 0L, wholesaleMinQty.toIntOrNull() ?: 0
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (productToEdit == null) "SIMPAN PRODUK" else "UPDATE PRODUK",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

private fun parseVariationString(variations: String, mainStock: Int): List<VariantInput> {
    if (variations.isBlank()) return emptyList()
    val list = mutableListOf<VariantInput>()
    val parts = variations.split(",")

    for ((index, part) in parts.withIndex()) {
        val pieces = part.split(":")
        if (pieces.size >= 2) {
            val stockStr = pieces.getOrNull(3)?.trim()
            val finalStock = if (stockStr != null && stockStr.isNotEmpty()) {
                stockStr.toIntOrNull() ?: 0
            } else {
                if (index == 0) mainStock else 0
            }

            val multStr = pieces.getOrNull(4)?.trim()?.takeIf { it.isNotEmpty() } ?: "1"
            list.add(
                VariantInput(
                    name = pieces[0].trim(),
                    price = pieces[1].trim(),
                    costPrice = pieces.getOrNull(2)?.trim() ?: "0",
                    stock = finalStock.toString(),
                    packMultiplier = multStr
                )
            )
        }
    }
    return list
}

// =====================================================================
// 2. PAYMENT BOTTOM SHEET (SESI 2a: VA DISEMBUNYIKAN + TRANSFER MANUAL RAPI)
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentBottomSheet(
    totalPrice: Long,
    totalCommission: Long,
    settingsManager: SettingsManager,
    qrisString: String,
    isQrisPaid: Boolean,
    uniqueTransferAmount: Long,
    isTransferPaid: Boolean,
    onGenerateQris: () -> Unit,
    onGenerateTransfer: () -> Unit,
    onDismiss: () -> Unit,
    onConfirmPayment: (Long, String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cashInput by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("CASH") }
    var manualTransferConfirmed by remember { mutableStateOf(false) }

    val cashReceived = cashInput.toLongOrNull() ?: 0L
    val change = if (cashReceived >= totalPrice) cashReceived - totalPrice else 0L

    LaunchedEffect(paymentMethod, qrisString, uniqueTransferAmount, totalPrice, cashReceived, change) {
        if (paymentMethod != "Bank Transfer") {
            manualTransferConfirmed = false
        }
        if (paymentMethod == "QRIS" && qrisString.isEmpty()) {
            onGenerateQris()
        } else if (paymentMethod == "Bank Transfer" && uniqueTransferAmount == 0L) {
            onGenerateTransfer()
        }
        val activePayAmount = if (paymentMethod == "Bank Transfer" && uniqueTransferAmount > 0L) {
            uniqueTransferAmount
        } else {
            totalPrice
        }
        val activeQris = qrisString.ifBlank { settingsManager.qrisStaticPayload ?: "" }
        LocalCfdServerManager.updatePaymentStage(
            isPaymentOpen = true,
            method = paymentMethod,
            amountToPay = activePayAmount,
            cashReceived = if (paymentMethod == "CASH") cashReceived else activePayAmount,
            changeAmount = if (paymentMethod == "CASH") change else 0L,
            qrisPayload = activeQris,
            bankName = settingsManager.bankName ?: "",
            bankAccountNumber = settingsManager.bankAccountNumber ?: "",
            bankAccountName = settingsManager.bankAccountName ?: ""
        )
        P2pCfdManager.broadcastCurrentState(context, force = true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        windowInsets = WindowInsets(0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Pembayaran",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Total Tagihan:",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatRupiah(totalPrice),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black
            )

            if (settingsManager.enableMultiCurrency && settingsManager.exchangeRateUsd > 0) {
                val usdAmount = totalPrice / settingsManager.exchangeRateUsd
                val formattedUsd = String.format(Locale.US, "$%.2f", usdAmount)
                Text(
                    "≈ $formattedUsd USD",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (totalCommission > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Komisi Kasir: +${formatRupiah(totalCommission)}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(8.dp),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val availableMethods = settingsManager.paymentMethods.toList()
                availableMethods.forEach { method ->
                    val isSelected = paymentMethod == method
                    Button(
                        onClick = { paymentMethod = method },
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = method,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (paymentMethod == "CASH") {
                OutlinedTextField(
                    value = cashInput,
                    onValueChange = { if (it.length <= 10) cashInput = it },
                    label = { Text("Uang Diterima (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Kembalian:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        formatRupiah(change),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (change > 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (paymentMethod == "QRIS") {
                if (isQrisPaid) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Pembayaran Diterima!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else if (qrisString.isNotEmpty()) {
                    val bitmap = remember(qrisString) { generateQrCodeBitmap(qrisString) }
                    if (bitmap != null) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.size(250.dp).padding(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Scan dengan aplikasi Bank/E-Wallet",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Gagal membuat QR Code. Periksa Payload di Pengaturan.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Memuat QRIS...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (paymentMethod == "Bank Transfer") {
                if (isTransferPaid) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Transfer Diterima!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Transfer ke Rekening:",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (settingsManager.bankAccountNumber.isNullOrBlank()) {
                                Text(
                                    "Rekening Bank belum diatur oleh Owner di Pengaturan -> Metode Pembayaran.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    settingsManager.bankName?.ifBlank { "-" } ?: "-",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    settingsManager.bankAccountNumber?.ifBlank { "-" } ?: "-",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "a/n ${settingsManager.bankAccountName?.ifBlank { "-" } ?: "-"}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            Text(
                                "Nominal Transfer:",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatRupiah(if (uniqueTransferAmount > 0) uniqueTransferAmount else totalPrice),
                                fontWeight = FontWeight.Black,
                                fontSize = 26.sp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Checkbox Konfirmasi Transfer Manual (Pengganti VA Auto-Detect)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { manualTransferConfirmed = !manualTransferConfirmed },
                        color = if (manualTransferConfirmed)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (manualTransferConfirmed)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = manualTransferConfirmed,
                                onCheckedChange = { manualTransferConfirmed = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Saya sudah menerima transfer sesuai nominal",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Pembayaran via $paymentMethod belum didukung secara offline.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            val canCompletePayment = when (paymentMethod) {
                "CASH" -> cashReceived >= totalPrice && totalPrice > 0
                "QRIS" -> isQrisPaid
                "Bank Transfer" -> isTransferPaid || manualTransferConfirmed
                else -> true
            }
            Button(
                onClick = {
                    onConfirmPayment(
                        if (paymentMethod == "CASH") cashReceived else totalPrice,
                        paymentMethod
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = canCompletePayment,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "SELESAIKAN PEMBAYARAN",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
