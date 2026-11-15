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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.karyaku.kasir.data.local.ProductEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// =====================================================================
// DATA CLASS UNTUK VARIAN DINAMIS
// =====================================================================
data class VariantInput(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var costPrice: String = "",
    var price: String = "",
    var stock: String = ""
)

// =====================================================================
// 1. PRODUCT FORM BOTTOM SHEET
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormBottomSheet(
    productToEdit: ProductEntity? = null,
    onDismiss: () -> Unit,
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
    var barcode by remember { mutableStateOf(productToEdit?.barcode ?: "") }
    var category by remember { mutableStateOf(productToEdit?.category ?: "Umum") }
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
            val parsed = parseVariationString(productToEdit.variations)
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
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = SlateGrey)
                        Text("Pilih Foto", color = SlateGrey, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- CHECKBOX JASA / COMBO ---
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { isService = !isService }) {
                Checkbox(checked = isService, onCheckedChange = { isService = it }, colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen))
                Text("Produk Jasa (Tanpa Stok)", fontSize = 14.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { isCombo = !isCombo }) {
                Checkbox(checked = isCombo, onCheckedChange = { isCombo = it }, colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen))
                Text("Produk Combo/Paket", fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- FIELD UTAMA ---
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Produk *") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Kategori") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("Barcode (Opsional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))

            Spacer(modifier = Modifier.height(16.dp))

            // --- TOGGLE VARIAN ---
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { 
                hasVariants = !hasVariants 
                if (hasVariants && variantList.isEmpty()) variantList = listOf(VariantInput())
            }) {
                Checkbox(checked = hasVariants, onCheckedChange = { 
                    hasVariants = it 
                    if (it && variantList.isEmpty()) variantList = listOf(VariantInput())
                }, colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen))
                Text("Produk ini memiliki Varian (Ukuran/Rasa, dll)", fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- JIKA TANPA VARIAN ---
            if (!hasVariants) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = singleCost, onValueChange = { singleCost = it },
                        label = { Text("Harga Beli") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = singlePrice, onValueChange = { singlePrice = it },
                        label = { Text("Harga Jual") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                }
                if (!isService) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = singleStock, onValueChange = { singleStock = it },
                        label = { Text("Stok Awal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                }
            } else {
                // --- JIKA DENGAN VARIAN (BARIS DINAMIS) ---
                Text("Daftar Varian", fontWeight = FontWeight.Bold, color = EmeraldGreen, modifier = Modifier.padding(bottom = 8.dp))
                
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
                                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp)
                                )
                                IconButton(onClick = {
                                    val newList = variantList.toMutableList()
                                    newList.removeAt(index)
                                    variantList = newList
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = ErrorRed)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = variant.costPrice, onValueChange = { 
                                        val newList = variantList.toMutableList()
                                        newList[index] = variant.copy(costPrice = it)
                                        variantList = newList
                                    },
                                    label = { Text("Harga Beli") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp)
                                )
                                OutlinedTextField(
                                    value = variant.price, onValueChange = { 
                                        val newList = variantList.toMutableList()
                                        newList[index] = variant.copy(price = it)
                                        variantList = newList
                                    },
                                    label = { Text("Harga Jual") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp)
                                )
                            }
                            if (!isService) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = variant.stock, onValueChange = { 
                                        val newList = variantList.toMutableList()
                                        newList[index] = variant.copy(stock = it)
                                        variantList = newList
                                    },
                                    label = { Text("Stok Varian Ini") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { variantList = variantList + VariantInput() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EmeraldGreen)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = EmeraldGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tambah Baris Varian", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- FIELD TAMBAHAN (GROSIR & KOMISI) ---
            if (!isService) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = wholesalePrice, onValueChange = { wholesalePrice = it },
                        label = { Text("Harga Grosir") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = wholesaleMinQty, onValueChange = { wholesaleMinQty = it },
                        label = { Text("Min Qty Grosir") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            OutlinedTextField(
                value = commission, onValueChange = { commission = it },
                label = { Text("Komisi Kasir / Item (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
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
                Icon(Icons.Default.DateRange, contentDescription = null, tint = AmberAccent)
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
                        
                        // Rakit string: Nama:HargaJual:HargaBeli:Stok
                        val variationParts = variantList.map { 
                            val p = it.price.toLongOrNull() ?: 0L
                            val c = it.costPrice.toLongOrNull() ?: 0L
                            val s = if(isService) 0 else (it.stock.toIntOrNull() ?: 0)
                            "${it.name.trim()}:$p:$c:$s"
                        }
                        finalVariations = variationParts.joinToString(",")
                        
                        finalStock = if(isService) 0 else variantList.sumOf { it.stock.toIntOrNull() ?: 0 }
                        finalPrice = variantList.firstOrNull()?.price?.toLongOrNull() ?: 0L
                        finalCost = variantList.firstOrNull()?.costPrice?.toLongOrNull() ?: 0L

                    } else {
                        finalPrice = singlePrice.toLongOrNull() ?: 0L
                        finalCost = singleCost.toLongOrNull() ?: 0L
                        finalStock = if(isService) 0 else (singleStock.toIntOrNull() ?: 0)
                        finalVariations = ""
                    }

                    if (finalPrice <= 0) {
                        Toast.makeText(context, "Harga jual harus lebih dari 0!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    onSave(
                        name, finalPrice, finalStock, category, finalCost, barcode, 
                        finalVariations, commission.toLongOrNull() ?: 0L, expiry, 
                        isService, isCombo, imagePath, 
                        wholesalePrice.toLongOrNull() ?: 0L, wholesaleMinQty.toIntOrNull() ?: 0
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (productToEdit == null) "SIMPAN PRODUK" else "UPDATE PRODUK", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }
        }
    }
}

// Parser untuk membaca database format "Nama:HargaJual:HargaBeli:Stok"
// FIX: Mengubah syarat dari >= 3 menjadi >= 2 agar produk lama (yang belum punya harga beli/stok) tidak hilang
private fun parseVariationString(variations: String): List<VariantInput> {
    if (variations.isBlank()) return emptyList()
    return variations.split(",").mapNotNull { part ->
        val pieces = part.split(":")
        if (pieces.size >= 2) {
            VariantInput(
                name = pieces[0].trim(),
                price = pieces[1].trim(),
                costPrice = pieces.getOrNull(2)?.trim() ?: "0",
                stock = pieces.getOrNull(3)?.trim() ?: "0"
            )
        } else null
    }
}

// =====================================================================
// 2. PAYMENT BOTTOM SHEET
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cashInput by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("CASH") }
    val cashReceived = cashInput.toLongOrNull() ?: 0L
    val change = if (cashReceived >= totalPrice) cashReceived - totalPrice else 0L

    LaunchedEffect(paymentMethod) {
        if (paymentMethod == "QRIS" && qrisString.isEmpty()) {
            onGenerateQris()
        } else if (paymentMethod == "Bank Transfer" && uniqueTransferAmount == 0L) {
            onGenerateTransfer()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss, 
        sheetState = sheetState, 
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
            Text("Pembayaran", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Total Tagihan:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatRupiah(totalPrice), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            
            if (settingsManager.enableMultiCurrency && settingsManager.exchangeRateUsd > 0) {
                val usdAmount = totalPrice / settingsManager.exchangeRateUsd
                val formattedUsd = String.format(Locale.US, "$%.2f", usdAmount)
                Text("≈ $formattedUsd USD", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (totalCommission > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(color = EmeraldGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                    Text("Komisi Kasir: +${formatRupiah(totalCommission)}", color = EmeraldGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp), fontSize = 12.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), 
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val availableMethods = settingsManager.paymentMethods.toList()
                availableMethods.forEach { method ->
                    Button(
                        onClick = { paymentMethod = method }, 
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp), 
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (paymentMethod == method) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) { 
                        Text(
                            text = method, 
                            color = if (paymentMethod == method) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, 
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
                OutlinedTextField(value = cashInput, onValueChange = { if (it.length <= 10) cashInput = it }, label = { Text("Uang Diterima (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kembalian:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(formatRupiah(change), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (change > 0) EmeraldGreen else MaterialTheme.colorScheme.onSurface)
                }
            } else if (paymentMethod == "QRIS") {
                if (isQrisPaid) {
                    Surface(color = EmeraldGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Pembayaran Diterima!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = EmeraldGreen)
                        }
                    }
                } else if (qrisString.isNotEmpty()) {
                    val bitmap = remember(qrisString) { generateQrCodeBitmap(qrisString) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(250.dp)
                        )
                        Text("Scan dengan aplikasi Bank/E-Wallet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Gagal membuat QR Code. Periksa Payload di Pengaturan.", color = ErrorRed)
                    }
                } else {
                    CircularProgressIndicator(color = EmeraldGreen)
                    Text("Memuat QRIS...", fontSize = 12.sp)
                }
            } else if (paymentMethod == "Bank Transfer") {
                if (isTransferPaid) {
                    Surface(color = EmeraldGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Transfer Diterima!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = EmeraldGreen)
                        }
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Transfer ke Rekening:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(settingsManager.bankName ?: "-", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(settingsManager.bankAccountNumber ?: "-", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = EmeraldGreen)
                            Text("a/n ${settingsManager.bankAccountName ?: "-"}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            
                            Text("Nominal Transfer (Tepat):", fontSize = 14.sp, color = ErrorRed)
                            Text(formatRupiah(uniqueTransferAmount), fontWeight = FontWeight.Black, fontSize = 28.sp, color = ErrorRed)
                            Text("*Wajib transfer sesuai nominal hingga 3 digit terakhir", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    if (settingsManager.enableTransferAutoDetect) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = EmeraldGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Menunggu notifikasi transfer masuk...", fontSize = 12.sp, color = EmeraldGreen)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    TextButton(onClick = { 
                        onConfirmPayment(totalPrice, "Bank Transfer") 
                    }) {
                        Text("Konfirmasi Manual (Sudah Transfer)", color = AmberAccent, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Surface(color = ErrorRed.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Pembayaran via $paymentMethod belum didukung secara offline.", color = ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { onConfirmPayment(if (paymentMethod == "CASH") cashReceived else totalPrice, paymentMethod) }, 
                modifier = Modifier.fillMaxWidth().height(56.dp), 
                enabled = (paymentMethod == "CASH" && cashReceived >= totalPrice && totalPrice > 0) || (paymentMethod == "QRIS" && isQrisPaid) || (paymentMethod == "Bank Transfer" && isTransferPaid) || (paymentMethod != "CASH" && paymentMethod != "QRIS" && paymentMethod != "Bank Transfer"),
                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                shape = RoundedCornerShape(12.dp)
            ) { 
                Text("SELESAIKAN PEMBAYARAN", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) 
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}