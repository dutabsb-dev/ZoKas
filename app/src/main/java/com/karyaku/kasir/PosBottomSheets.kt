package com.karyaku.kasir

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.karyaku.kasir.data.local.ProductEntity
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class VariantInput(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var costPrice: String = "",
    var price: String = "",
    var stock: String = "",
    var packMultiplier: String = "1",
    var barcode: String = ""
)

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
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var name by remember { mutableStateOf(productToEdit?.name ?: "") }
    var productSku by remember { mutableStateOf(productToEdit?.let { extractProductSku(it.barcode) } ?: "") }
    var barcode by remember { mutableStateOf(productToEdit?.let { extractActualBarcode(it.barcode) } ?: "") }
    var category by remember { mutableStateOf(productToEdit?.category ?: "Umum") }
    var baseUnit by remember { mutableStateOf(productToEdit?.let { extractProductBaseUnit(it) } ?: "pcs") }
    var unitDropdownExpanded by remember { mutableStateOf(false) }
    var imagePath by remember { mutableStateOf(productToEdit?.imagePath ?: "") }

    var isScanningOcr by remember { mutableStateOf(false) }
    var ocrCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showAdvancedOptions by remember {
        mutableStateOf(
            productToEdit != null && (
                extractProductSku(productToEdit.barcode).isNotBlank() ||
                    extractActualBarcode(productToEdit.barcode).isNotBlank() ||
                    productToEdit.commissionPerItem > 0L ||
                    productToEdit.wholesalePrice > 0L ||
                    productToEdit.expiryDate > 0L ||
                    productToEdit.isService ||
                    productToEdit.isCombo
                )
        )
    }

    var hasVariants by remember { mutableStateOf(false) }
    var variantList by remember { mutableStateOf<List<VariantInput>>(emptyList()) }

    var singleCost by remember { mutableStateOf(productToEdit?.costPrice?.toString() ?: "") }
    var singlePrice by remember { mutableStateOf(productToEdit?.price?.toString() ?: "") }
    var singleStock by remember { mutableStateOf(productToEdit?.stock?.toString() ?: "") }

    var commission by remember { mutableStateOf(productToEdit?.commissionPerItem?.toString() ?: "0") }
    var wholesalePrice by remember { mutableStateOf(productToEdit?.wholesalePrice?.toString() ?: "0") }
    var wholesaleMinQty by remember { mutableStateOf(productToEdit?.wholesaleMinQty?.toString() ?: "0") }
    var expiry by remember { mutableStateOf(productToEdit?.expiryDate ?: 0L) }
    var isService by remember { mutableStateOf(productToEdit?.isService ?: false) }
    var isCombo by remember { mutableStateOf(productToEdit?.isCombo ?: false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var scanningVariantIndex by remember { mutableStateOf<Int?>(null) }

    val hasUnsavedInput = remember(
        name, productSku, barcode, category, imagePath,
        hasVariants, variantList, singleCost, singlePrice, singleStock,
        commission, wholesalePrice, wholesaleMinQty, expiry, isService, isCombo, productToEdit
    ) {
        if (productToEdit == null) {
            name.isNotBlank() || productSku.isNotBlank() || barcode.isNotBlank() ||
                imagePath.isNotBlank() || hasVariants || singleCost.isNotBlank() ||
                singlePrice.isNotBlank() || singleStock.isNotBlank() ||
                (commission.toLongOrNull() ?: 0L) > 0L ||
                (wholesalePrice.toLongOrNull() ?: 0L) > 0L ||
                (wholesaleMinQty.toIntOrNull() ?: 0) > 0 ||
                expiry > 0L || isService || isCombo
        } else {
            name != productToEdit.name ||
                category != productToEdit.category ||
                imagePath != productToEdit.imagePath ||
                singleCost != productToEdit.costPrice.toString() ||
                singlePrice != productToEdit.price.toString() ||
                singleStock != productToEdit.stock.toString() ||
                commission != productToEdit.commissionPerItem.toString() ||
                wholesalePrice != productToEdit.wholesalePrice.toString() ||
                wholesaleMinQty != productToEdit.wholesaleMinQty.toString() ||
                expiry != productToEdit.expiryDate ||
                isService != productToEdit.isService ||
                isCombo != productToEdit.isCombo
        }
    }

    fun handleBackOrCloseAttempt(forcePromptIfDirty: Boolean) {
        if (showDiscardConfirm) {
            showDiscardConfirm = false
            return
        }
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        if (forcePromptIfDirty && hasUnsavedInput) {
            showDiscardConfirm = true
        } else {
            onDismiss()
        }
    }

    fun runOcrFromUri(uri: Uri) {
        isScanningOcr = true
        scope.launch {
            val found = ProductVoiceOcrHelper.recognizeProductNameCandidates(context, uri)
            isScanningOcr = false
            ocrCandidates = found
            if (found.isNotEmpty()) {
                name = found.first()
                Toast.makeText(context, "Teks terbaca: \"${found.first()}\"", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    "Teks produk belum jelas terbaca. Coba arahkan kamera lebih dekat atau pilih dari galeri.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val ocrGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) runOcrFromUri(uri)
    }

    val ocrCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val capturedUri = pendingCaptureUri
        if (taken && capturedUri != null) {
            runOcrFromUri(capturedUri)
        } else {
            pendingCaptureFile?.delete()
        }
        pendingCaptureUri = null
        pendingCaptureFile = null
    }

    val variantBarcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val scanned = result.contents?.trim()
        val idx = scanningVariantIndex
        scanningVariantIndex = null
        if (!scanned.isNullOrEmpty() && idx != null && idx in variantList.indices) {
            val newList = variantList.toMutableList()
            newList[idx] = newList[idx].copy(barcode = scanned)
            variantList = newList
            Toast.makeText(context, "Barcode varian terbaca: $scanned", Toast.LENGTH_SHORT).show()
        }
    }

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

    Dialog(
        onDismissRequest = { handleBackOrCloseAttempt(forcePromptIfDirty = true) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler(enabled = true) {
            handleBackOrCloseAttempt(forcePromptIfDirty = true)
        }

        if (showDiscardConfirm) {
            AlertDialog(
                onDismissRequest = { showDiscardConfirm = false },
                icon = {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                },
                title = {
                    Text(
                        "Buang Inputan Produk?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        "Data yang sudah Anda ketik/scan belum disimpan. Jika ditutup sekarang, isian akan hilang.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDiscardConfirm = false
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("BUANG & KELUAR", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showDiscardConfirm = false },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("LANJUT EDIT", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 10.dp, bottom = 36.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (productToEdit == null) "Tambah Produk Baru" else "Edit Produk",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
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

                            val variationParts = variantList.map {
                                val p = it.price.toLongOrNull() ?: 0L
                                val c = it.costPrice.toLongOrNull() ?: 0L
                                val s = if (isService) 0 else (it.stock.toIntOrNull() ?: 0)
                                val m = (it.packMultiplier.toIntOrNull() ?: 1).coerceAtLeast(1)
                                val vb = it.barcode.trim()
                                if (vb.isNotBlank()) "${it.name.trim()}:$p:$c:$s:$m:$vb" else "${it.name.trim()}:$p:$c:$s:$m"
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
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (productToEdit == null) "Simpan" else "Update",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = { handleBackOrCloseAttempt(forcePromptIfDirty = true) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup Form", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    SelectionContainer {
                        Text(
                            text = "Tips: Tekan lama pada kolom teks untuk Copy / Paste. Tekan Back saat mengetik untuk menutup keyboard.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "1. Informasi Wajib & Isi Cepat",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
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
                                        Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Pilih Foto Produk (Opsional)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (isScanningOcr) return@OutlinedButton
                                        try {
                                            val (file, uri) = ProductVoiceOcrHelper.createTempOcrPhotoUri(context)
                                            pendingCaptureFile = file
                                            pendingCaptureUri = uri
                                            ocrCameraLauncher.launch(uri)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Kamera OCR tidak dapat dibuka. Gunakan Scan dari Foto.", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    enabled = !isScanningOcr,
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isScanningOcr) "Membaca..." else "Scan Nama (Kamera)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = { if (!isScanningOcr) ocrGalleryLauncher.launch("image/*") },
                                    enabled = !isScanningOcr,
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scan dari Foto", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }

                            if (isScanningOcr) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text("Mendeteksi teks nama produk pada label...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            if (ocrCandidates.size > 1) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Hasil scan terdeteksi (ketuk untuk mengganti nama):", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ocrCandidates.forEach { candidate ->
                                            FilterChip(
                                                selected = name.equals(candidate, ignoreCase = true),
                                                onClick = { name = candidate },
                                                label = { Text(candidate, fontSize = 11.sp, maxLines = 1) }
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Nama Produk *") },
                                placeholder = { Text("Ketik, paste, scan label, atau ucapkan nama") },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (name.isNotEmpty()) {
                                            IconButton(onClick = { name = "" }, modifier = Modifier.size(34.dp)) {
                                                Icon(Icons.Default.Clear, contentDescription = "Hapus Nama", modifier = Modifier.size(17.dp))
                                            }
                                        }
                                        VoiceMicButton(promptLabel = "nama produk", currentValue = name, onResultText = { name = it })
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = category,
                                onValueChange = { category = it },
                                label = { Text("Kategori *") },
                                trailingIcon = {
                                    VoiceMicButton(promptLabel = "kategori produk", currentValue = category, onResultText = { category = it })
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

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
                                Text("Produk ini memiliki Varian (Ukuran/Rasa, dll)", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            }

                            if (!hasVariants) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = singleCost,
                                        onValueChange = { singleCost = it.filter { c -> c.isDigit() } },
                                        label = { Text("Harga Beli") },
                                        trailingIcon = {
                                            VoiceMicButton(promptLabel = "harga beli", currentValue = singleCost, onResultText = { singleCost = it.filter { c -> c.isDigit() } })
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = singlePrice,
                                        onValueChange = { singlePrice = it.filter { c -> c.isDigit() } },
                                        label = { Text("Harga Jual *") },
                                        trailingIcon = {
                                            VoiceMicButton(promptLabel = "harga jual", currentValue = singlePrice, onResultText = { singlePrice = it.filter { c -> c.isDigit() } })
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                if (!isService) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = singleStock,
                                            onValueChange = { singleStock = it.filter { c -> c.isDigit() } },
                                            label = { Text("Stok Awal") },
                                            trailingIcon = {
                                                VoiceMicButton(promptLabel = "jumlah stok", currentValue = singleStock, onResultText = { singleStock = it.filter { c -> c.isDigit() } })
                                            },
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Daftar Varian & Satuan Terkecil", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        val previewTotalSmallest = variantList.sumOf {
                                            val q = it.stock.toIntOrNull() ?: 0
                                            val m = (it.packMultiplier.toIntOrNull() ?: 1).coerceAtLeast(1)
                                            q * m
                                        }
                                        if (!isService) {
                                            Text("Total Stok Gabungan: $previewTotalSmallest $baseUnit", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
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
                                                DropdownMenuItem(text = { Text(u) }, onClick = { baseUnit = u; unitDropdownExpanded = false })
                                            }
                                        }
                                    }
                                }

                                variantList.forEachIndexed { index, variant ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                OutlinedTextField(
                                                    value = variant.name,
                                                    onValueChange = { newName ->
                                                        val newList = variantList.toMutableList()
                                                        newList[index] = variant.copy(name = newName)
                                                        variantList = newList
                                                    },
                                                    label = { Text("Nama Varian (cth: Jumbo)") },
                                                    trailingIcon = {
                                                        VoiceMicButton(
                                                            promptLabel = "nama varian",
                                                            currentValue = variant.name,
                                                            onResultText = { spoken ->
                                                                val newList = variantList.toMutableList()
                                                                newList[index] = variant.copy(name = spoken)
                                                                variantList = newList
                                                            }
                                                        )
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                IconButton(onClick = {
                                                    val newList = variantList.toMutableList()
                                                    newList.removeAt(index)
                                                    variantList = newList
                                                }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = variant.costPrice,
                                                    onValueChange = {
                                                        val newList = variantList.toMutableList()
                                                        newList[index] = variant.copy(costPrice = it.filter { c -> c.isDigit() })
                                                        variantList = newList
                                                    },
                                                    label = { Text("Harga Beli") },
                                                    trailingIcon = {
                                                        VoiceMicButton(
                                                            promptLabel = "harga beli varian",
                                                            currentValue = variant.costPrice,
                                                            onResultText = { spoken ->
                                                                val newList = variantList.toMutableList()
                                                                newList[index] = variant.copy(costPrice = spoken.filter { c -> c.isDigit() })
                                                                variantList = newList
                                                            }
                                                        )
                                                    },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.weight(1f),
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                OutlinedTextField(
                                                    value = variant.price,
                                                    onValueChange = {
                                                        val newList = variantList.toMutableList()
                                                        newList[index] = variant.copy(price = it.filter { c -> c.isDigit() })
                                                        variantList = newList
                                                    },
                                                    label = { Text("Harga Jual") },
                                                    trailingIcon = {
                                                        VoiceMicButton(
                                                            promptLabel = "harga jual varian",
                                                            currentValue = variant.price,
                                                            onResultText = { spoken ->
                                                                val newList = variantList.toMutableList()
                                                                newList[index] = variant.copy(price = spoken.filter { c -> c.isDigit() })
                                                                variantList = newList
                                                            }
                                                        )
                                                    },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.weight(1f),
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                            }
                                            if (!isService) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    OutlinedTextField(
                                                        value = variant.stock,
                                                        onValueChange = {
                                                            val newList = variantList.toMutableList()
                                                            newList[index] = variant.copy(stock = it.filter { c -> c.isDigit() })
                                                            variantList = newList
                                                        },
                                                        label = { Text("Jumlah Stok") },
                                                        trailingIcon = {
                                                            VoiceMicButton(
                                                                promptLabel = "stok varian",
                                                                currentValue = variant.stock,
                                                                onResultText = { spoken ->
                                                                    val newList = variantList.toMutableList()
                                                                    newList[index] = variant.copy(stock = spoken.filter { c -> c.isDigit() })
                                                                    variantList = newList
                                                                }
                                                            )
                                                        },
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
                                                        label = { Text("Isi ($baseUnit)") },
                                                        placeholder = { Text("1") },
                                                        trailingIcon = {
                                                            VoiceMicButton(
                                                                promptLabel = "isi kemasan varian",
                                                                currentValue = variant.packMultiplier,
                                                                onResultText = { spoken ->
                                                                    val digits = spoken.filter { c -> c.isDigit() }
                                                                    val newList = variantList.toMutableList()
                                                                    newList[index] = variant.copy(packMultiplier = digits)
                                                                    variantList = newList
                                                                }
                                                            )
                                                        },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        modifier = Modifier.weight(1f),
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                }
                                                val vQty = variant.stock.toIntOrNull() ?: 0
                                                val vMult = (variant.packMultiplier.toIntOrNull() ?: 1).coerceAtLeast(1)
                                                if (vMult > 1) {
                                                    Text("Konversi satuan terkecil: $vQty x $vMult = ${vQty * vMult} $baseUnit", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                            OutlinedTextField(
                                                value = variant.barcode,
                                                onValueChange = { newCode ->
                                                    val newList = variantList.toMutableList()
                                                    newList[index] = variant.copy(barcode = newCode)
                                                    variantList = newList
                                                },
                                                label = { Text("Barcode Varian (Opsional)") },
                                                placeholder = { Text("Ketik, paste, ucapkan, atau scan barcode") },
                                                trailingIcon = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        VoiceMicButton(
                                                            promptLabel = "barcode varian",
                                                            currentValue = variant.barcode,
                                                            onResultText = { spoken ->
                                                                val newList = variantList.toMutableList()
                                                                newList[index] = variant.copy(barcode = spoken.replace(" ", ""))
                                                                variantList = newList
                                                            }
                                                        )
                                                        if (variant.barcode.isNotEmpty()) {
                                                            IconButton(onClick = {
                                                                val newList = variantList.toMutableList()
                                                                newList[index] = variant.copy(barcode = "")
                                                                variantList = newList
                                                            }, modifier = Modifier.size(32.dp)) {
                                                                Icon(Icons.Default.Clear, contentDescription = "Hapus Barcode Varian", modifier = Modifier.size(16.dp))
                                                            }
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                scanningVariantIndex = index
                                                                val options = ScanOptions().apply {
                                                                    setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                                                                    setPrompt("Scan Barcode Varian ${variant.name.ifBlank { "#${index + 1}" }}")
                                                                    setCameraId(0)
                                                                    setBeepEnabled(true)
                                                                    setBarcodeImageEnabled(true)
                                                                    setOrientationLocked(true)
                                                                }
                                                                variantBarcodeLauncher.launch(options)
                                                            },
                                                            modifier = Modifier.size(34.dp)
                                                        ) {
                                                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode Varian", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp)
                                            )
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
                                    Text("Tambah Baris Varian", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { showAdvancedOptions = !showAdvancedOptions },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("2. Pengaturan Lanjutan (Opsional)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text("SKU, barcode kemasan, tipe jasa/combo, resep BOM, grosir, komisi & expiry", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(imageVector = if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Toggle Pengaturan Lanjutan", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            if (showAdvancedOptions) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { isService = !isService }) {
                                    Checkbox(checked = isService, onCheckedChange = { isService = it }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                                    Text("Produk Jasa (Tanpa Stok)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { isCombo = !isCombo }) {
                                    Checkbox(checked = isCombo, onCheckedChange = { isCombo = it }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                                    Text("Produk Combo/Paket", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                }

                                OutlinedTextField(
                                    value = productSku,
                                    onValueChange = { productSku = it.uppercase() },
                                    label = { Text("Kode Unik / SKU (cth: KOP-01)") },
                                    placeholder = { Text("Bisa dicari di Kasir") },
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            VoiceMicButton(promptLabel = "kode SKU produk", currentValue = productSku, onResultText = { productSku = it.uppercase().replace(" ", "-") })
                                            IconButton(
                                                onClick = {
                                                    val catPrefix = category.trim().take(3).uppercase().ifBlank { "PRD" }
                                                    val namePrefix = name.trim().split(Regex("\\s+")).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").take(3).ifBlank { "X" }
                                                    val randNum = (10..99).random()
                                                    productSku = "$catPrefix-$namePrefix$randNum"
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Default.AutoAwesome, contentDescription = "Buat Kode Otomatis", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )

                                val packagingBarcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
                                    val scanned = result.contents?.trim()
                                    if (!scanned.isNullOrEmpty()) {
                                        barcode = scanned
                                        Toast.makeText(context, "Barcode kemasan terbaca: $scanned", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                OutlinedTextField(
                                    value = barcode,
                                    onValueChange = { barcode = it },
                                    label = { Text("Barcode Kemasan (Opsional)") },
                                    placeholder = { Text("Ketik, paste, ucapkan, atau scan barcode") },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            VoiceMicButton(promptLabel = "barcode kemasan", currentValue = barcode, onResultText = { barcode = it.replace(" ", "") })
                                            if (barcode.isNotEmpty()) {
                                                IconButton(onClick = { barcode = "" }, modifier = Modifier.size(32.dp)) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Hapus Barcode", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    val options = ScanOptions().apply {
                                                        setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                                                        setPrompt("Arahkan kamera ke barcode produk")
                                                        setCameraId(0)
                                                        setBeepEnabled(true)
                                                        setBarcodeImageEnabled(true)
                                                        setOrientationLocked(true)
                                                    }
                                                    packagingBarcodeLauncher.launch(options)
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode Kamera", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )

                                val isBomActiveForForm = remember { isBomEnabledInContext(context) }
                                if (isBomActiveForForm) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Resep (BOM) & Bahan Baku", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
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
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                if (productToEdit != null && onManageBom != null) {
                                                    Button(
                                                        onClick = { onManageBom(productToEdit) },
                                                        modifier = Modifier.weight(1f).height(42.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
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
                                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
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
                                }

                                if (!isService) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = wholesalePrice,
                                            onValueChange = { wholesalePrice = it.filter { c -> c.isDigit() } },
                                            label = { Text("Harga Grosir") },
                                            trailingIcon = {
                                                VoiceMicButton(promptLabel = "harga grosir", currentValue = wholesalePrice, onResultText = { wholesalePrice = it.filter { c -> c.isDigit() } })
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = wholesaleMinQty,
                                            onValueChange = { wholesaleMinQty = it.filter { c -> c.isDigit() } },
                                            label = { Text("Min Qty Grosir") },
                                            trailingIcon = {
                                                VoiceMicButton(promptLabel = "minimal jumlah grosir", currentValue = wholesaleMinQty, onResultText = { wholesaleMinQty = it.filter { c -> c.isDigit() } })
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = commission,
                                    onValueChange = { commission = it.filter { c -> c.isDigit() } },
                                    label = { Text("Komisi Kasir / Item (Rp)") },
                                    trailingIcon = {
                                        VoiceMicButton(promptLabel = "komisi kasir per item", currentValue = commission, onResultText = { commission = it.filter { c -> c.isDigit() } })
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )

                                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                val expiryText = if (expiry > 0) dateFormat.format(Date(expiry)) else "Pilih Tanggal"
                                OutlinedButton(
                                    onClick = { showDatePicker = true },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Expiry: $expiryText", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

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

                            val variationParts = variantList.map {
                                val p = it.price.toLongOrNull() ?: 0L
                                val c = it.costPrice.toLongOrNull() ?: 0L
                                val s = if (isService) 0 else (it.stock.toIntOrNull() ?: 0)
                                val m = (it.packMultiplier.toIntOrNull() ?: 1).coerceAtLeast(1)
                                val vb = it.barcode.trim()
                                if (vb.isNotBlank()) "${it.name.trim()}:$p:$c:$s:$m:$vb" else "${it.name.trim()}:$p:$c:$s:$m"
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
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (productToEdit == null) "SIMPAN PRODUK" else "UPDATE PRODUK",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Bezel bawah lega agar tombol bawah naik & terlihat jelas
                    Spacer(modifier = Modifier.height(56.dp))
                }

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
            val varBarcode = pieces.getOrNull(5)?.trim().orEmpty()
            list.add(
                VariantInput(
                    name = pieces[0].trim(),
                    price = pieces[1].trim(),
                    costPrice = pieces.getOrNull(2)?.trim() ?: "0",
                    stock = finalStock.toString(),
                    packMultiplier = multStr,
                    barcode = varBarcode
                )
            )
        }
    }
    return list
}

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
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
                    Text("Komisi Kasir: +${formatRupiah(totalCommission)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val availableMethods = settingsManager.paymentMethods.toList()
                availableMethods.forEach { method ->
                    val isSelected = paymentMethod == method
                    Button(
                        onClick = { paymentMethod = method },
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(text = method, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center)
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kembalian:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(formatRupiah(change), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (change > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            } else if (paymentMethod == "QRIS") {
                if (isQrisPaid) {
                    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Pembayaran Diterima!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else if (qrisString.isNotEmpty()) {
                    val bitmap = remember(qrisString) { generateQrCodeBitmap(qrisString) }
                    if (bitmap != null) {
                        Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(4.dp)) {
                            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(250.dp).padding(8.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Scan dengan aplikasi Bank/E-Wallet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Gagal membuat QR Code. Periksa Payload di Pengaturan.", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Memuat QRIS...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (paymentMethod == "Bank Transfer") {
                if (isTransferPaid) {
                    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Transfer Diterima!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Transfer ke Rekening:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            if (settingsManager.bankAccountNumber.isNullOrBlank()) {
                                Text("Rekening Bank belum diatur oleh Owner di Pengaturan -> Metode Pembayaran.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            } else {
                                Text(settingsManager.bankName?.ifBlank { "-" } ?: "-", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(settingsManager.bankAccountNumber?.ifBlank { "-" } ?: "-", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                                Text("a/n ${settingsManager.bankAccountName?.ifBlank { "-" } ?: "-"}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                            Text("Nominal Transfer:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatRupiah(if (uniqueTransferAmount > 0) uniqueTransferAmount else totalPrice), fontWeight = FontWeight.Black, fontSize = 26.sp, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { manualTransferConfirmed = !manualTransferConfirmed },
                        color = if (manualTransferConfirmed) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (manualTransferConfirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = manualTransferConfirmed, onCheckedChange = { manualTransferConfirmed = it }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saya sudah menerima transfer sesuai nominal", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            } else {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Pembayaran via $paymentMethod belum didukung secara offline.", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center)
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
                onClick = { onConfirmPayment(if (paymentMethod == "CASH") cashReceived else totalPrice, paymentMethod) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = canCompletePayment,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("SELESAIKAN PEMBAYARAN", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosActionMenuBottomSheet(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRestock: () -> Unit,
    onWaste: () -> Unit,
    onBom: () -> Unit = {},
    onRawMaterial: () -> Unit = {},
    onSaveBarcode: () -> Unit = {},
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isBomActive = remember(product.id) { isBomEnabledInContext(context) }

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
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = product.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 4.dp))

            ListItem(
                headlineContent = { Text("Edit Produk", fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(if (isBomActive) "Ubah detail, harga, varian & resep" else "Ubah detail, harga & varian", fontSize = 11.sp) },
                leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onEdit() }
            )

            ListItem(
                headlineContent = { Text("Tambah Stok", fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("Restock barang jadi", fontSize = 11.sp) },
                leadingContent = { Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onRestock() }
            )

            ListItem(
                headlineContent = { Text("Barang Rusak", fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("Catat waste/expired", fontSize = 11.sp) },
                leadingContent = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                modifier = Modifier.clickable { onWaste() }
            )

            if (isBomActive) {
                ListItem(
                    headlineContent = { Text("Atur Resep (BOM)", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Takaran komposisi & kelola bahan baku", fontSize = 11.sp) },
                    leadingContent = { Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { onBom() }
                )

                ListItem(
                    headlineContent = { Text("Kelola Bahan Baku", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Tambah item & stok bahan mentah", fontSize = 11.sp) },
                    leadingContent = { Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                    modifier = Modifier.clickable { onRawMaterial() }
                )
            }

            ListItem(
                headlineContent = { Text("Simpan Barcode (JPG)", fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("Simpan label ke galeri", fontSize = 11.sp) },
                leadingContent = { Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onSaveBarcode() }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            ListItem(
                headlineContent = { Text("Hapus Produk", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Hapus permanen", fontSize = 11.sp) },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { onDelete() }
            )
        }
    }
}
