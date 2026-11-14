package com.karyaku.kasir

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormBottomSheet(
    productToEdit: ProductEntity? = null, 
    onDismiss: () -> Unit, 
    onSave: (String, Long, Int, String, Long, String, String, Long, Long, Boolean, Boolean, String, Long, Int) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(productToEdit?.name ?: "") }
    var price by remember { mutableStateOf(productToEdit?.price?.toString() ?: "") }
    var costPrice by remember { mutableStateOf(productToEdit?.costPrice?.toString() ?: "") } 
    var stock by remember { mutableStateOf(productToEdit?.stock?.toString() ?: "") }
    var category by remember { mutableStateOf(productToEdit?.category ?: "Umum") } 
    var barcode by remember { mutableStateOf(productToEdit?.barcode ?: "") }
    var variations by remember { mutableStateOf(productToEdit?.variations ?: "") }
    var commission by remember { mutableStateOf(productToEdit?.commissionPerItem?.toString() ?: "") }
    var expiryDate by remember { mutableStateOf(productToEdit?.expiryDate ?: 0L) }
    var isService by remember { mutableStateOf(productToEdit?.isService ?: false) }
    var isCombo by remember { mutableStateOf(productToEdit?.isCombo ?: false) }
    
    var wholesalePrice by remember { mutableStateOf(productToEdit?.wholesalePrice?.toString() ?: "") }
    var wholesaleMinQty by remember { mutableStateOf(productToEdit?.wholesaleMinQty?.toString() ?: "") }
    
    var imagePath by remember { mutableStateOf(productToEdit?.imagePath ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val expiryText = if (expiryDate > 0) dateFormat.format(Date(expiryDate)) else "Pilih Tanggal"

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = File(context.filesDir, "img_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                imagePath = file.absolutePath
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal simpan gambar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = if (expiryDate > 0) expiryDate else System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    expiryDate = datePickerState.selectedDateMillis ?: 0L
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding() 
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (productToEdit == null) "Tambah Produk Baru" else "Edit Produk",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp), 
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (imagePath.isNotEmpty()) {
                            AsyncImage(
                                model = File(imagePath),
                                contentDescription = "Product Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = "Upload", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Upload Gambar", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { isService = !isService }) {
                            Checkbox(checked = isService, onCheckedChange = { isService = it }, colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen))
                            Text("Produk Jasa (Tanpa Stok)", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { isCombo = !isCombo }) {
                            Checkbox(checked = isCombo, onCheckedChange = { isCombo = it }, colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen))
                            Text("Produk Combo/Paket", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Produk") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // UPDATED: Label Varian untuk Harga Berbeda
                    OutlinedTextField(
                        value = variations, 
                        onValueChange = { variations = it }, 
                        label = { Text("Varian (cth: Normal:6000, Jumbo:10000)") }, 
                        placeholder = { Text("Pisah koma. Gunakan | untuk Matrix") }, 
                        modifier = Modifier.fillMaxWidth(), 
                        singleLine = true, 
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("Barcode (Opsional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Kategori") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = costPrice, onValueChange = { if (it.length <= 10) costPrice = it }, label = { Text("Harga Beli / Modal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = price, onValueChange = { if (it.length <= 10) price = it }, label = { Text("Harga Jual Utama (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!isService) {
                        OutlinedTextField(value = stock, onValueChange = { if (it.length <= 5) stock = it }, label = { Text("Stok") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = wholesalePrice, 
                                onValueChange = { if (it.length <= 10) wholesalePrice = it }, 
                                label = { Text("Harga Grosir") }, 
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                                modifier = Modifier.weight(1f), 
                                singleLine = true, 
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = wholesaleMinQty, 
                                onValueChange = { if (it.length <= 5) wholesaleMinQty = it }, 
                                label = { Text("Min Qty") }, 
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                                modifier = Modifier.weight(1f), 
                                singleLine = true, 
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(value = commission, onValueChange = { if (it.length <= 10) commission = it }, label = { Text("Komisi Kasir / Item (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    
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
                    Button(onClick = { 
                        val p = price.toLongOrNull() ?: 0L
                        val cp = costPrice.toLongOrNull() ?: 0L
                        val s = if (isService) 0 else stock.toIntOrNull() ?: 0
                        val c = commission.toLongOrNull() ?: 0L
                        val wp = wholesalePrice.toLongOrNull() ?: 0L
                        val wm = wholesaleMinQty.toIntOrNull() ?: 0
                        if (name.isNotBlank() && p > 0) onSave(name, p, s, category.ifBlank { "Umum" }, cp, barcode, variations, c, expiryDate, isService, isCombo, imagePath, wp, wm) 
                    }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = name.isNotBlank() && (price.toLongOrNull() ?: 0L) > 0, colors = ButtonDefaults.buttonColors(containerColor = AmberAccent), shape = RoundedCornerShape(12.dp)) {
                        Text(if (productToEdit == null) "SIMPAN PRODUK" else "UPDATE PRODUK", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cashInput by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("CASH") }
    val cashReceived = cashInput.toLongOrNull() ?: 0L
    val change = if (cashReceived >= totalPrice) cashReceived - totalPrice else 0L

    // Generate QRIS / Transfer saat metode dipilih
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
            
            // Tombol Metode Pembayaran
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
                    // Tombol Konfirmasi Manual
                    TextButton(onClick = { 
                        // Paksa pembayaran sukses secara manual
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