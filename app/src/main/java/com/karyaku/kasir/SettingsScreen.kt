package com.karyaku.kasir

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.AuditLogEntity
import com.karyaku.kasir.data.local.UserEntity
import com.karyaku.kasir.data.local.RawMaterialEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    isHighContrast: Boolean,
    onToggleHighContrast: () -> Unit,
    uiScale: Float,
    onChangeUiScale: (Float) -> Unit,
    language: String,
    onToggleLanguage: () -> Unit,
    onLogout: () -> Unit,
    auditLogs: List<AuditLogEntity>,
    currentUser: UserEntity,
    viewModel: PosViewModel
) {
    val context = LocalContext.current
    // MENGGUNAKAN LOCAL ACTIVITY YANG BARU
    val activity = LocalActivity.current as? FragmentActivity
    val lang = language
    
    var storeName by remember { mutableStateOf(settingsManager.storeName ?: "") }
    var storeFooter by remember { mutableStateOf(settingsManager.storeFooter ?: "") }
    var storeAddress by remember { mutableStateOf(settingsManager.storeAddress ?: "") }
    var storePhone by remember { mutableStateOf(settingsManager.storePhone ?: "") }
    var taxPercent by remember { mutableStateOf(settingsManager.taxPercent.toString()) }
    var csvDataToExport by remember { mutableStateOf("") }
    
    var hhStart by remember { mutableStateOf(settingsManager.happyHourStart) }
    var hhEnd by remember { mutableStateOf(settingsManager.happyHourEnd) }
    var hhDiscount by remember { mutableStateOf(settingsManager.happyHourDiscount.toString()) }
    var maxDiscountCap by remember { mutableStateOf(settingsManager.maxDiscountCap.toString()) }
    
    var enableMultiCurrency by remember { mutableStateOf(settingsManager.enableMultiCurrency) }
    var exchangeRateUsd by remember { mutableStateOf(settingsManager.exchangeRateUsd.toString()) }
    var isTableModeEnabled by remember { mutableStateOf(settingsManager.isTableModeEnabled) }
    var isBiometricEnabled by remember { mutableStateOf(settingsManager.isBiometricEnabled) }
    
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var showUserManagementDialog by remember { mutableStateOf(false) }
    var showShiftHistory by remember { mutableStateOf(false) }
    var showRawMaterialDialog by remember { mutableStateOf(false) } 
    
    val users by viewModel.users.collectAsStateWithLifecycle()
    val shifts by viewModel.shifts.collectAsStateWithLifecycle()
    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()
    
    val printerManager = remember { BluetoothPrinterManager(context) }
    var savedPrinterMac by remember { mutableStateOf(settingsManager.printerMacAddress) }
    var savedKitchenMac by remember { mutableStateOf(settingsManager.kitchenPrinterMacAddress) }
    var showPrinterDialog by remember { mutableStateOf(false) }
    var showKitchenDialog by remember { mutableStateOf(false) }
    var pairedPrinters by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms[Manifest.permission.BLUETOOTH_CONNECT] == true) {
            pairedPrinters = printerManager.getPairedPrinters()
            showPrinterDialog = true
        }
    }
    
    val kitchenPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms[Manifest.permission.BLUETOOTH_CONNECT] == true) {
            pairedPrinters = printerManager.getPairedPrinters()
            showKitchenDialog = true
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) {
            if (BackupHelper.backupDatabase(context, uri)) Toast.makeText(context, "Backup berhasil!", Toast.LENGTH_SHORT).show()
            else Toast.makeText(context, "Gagal melakukan backup.", Toast.LENGTH_SHORT).show()
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            if (BackupHelper.restoreDatabase(context, uri)) showRestartDialog = true
            else Toast.makeText(context, "Gagal restore database.", Toast.LENGTH_SHORT).show()
        }
    }

    val createCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream -> 
                    outputStream.write(csvDataToExport.toByteArray()) 
                }
                Toast.makeText(context, "Data produk berhasil diekspor!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { 
                Toast.makeText(context, "Gagal ekspor CSV: ${e.message}", Toast.LENGTH_SHORT).show() 
            }
        }
    }

    val openCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val csvText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                viewModel.importProductsFromCsv(csvText) { inserts, updates ->
                    Toast.makeText(context, "Import Selesai! $inserts Baru, $updates Diperbarui.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) { 
                Toast.makeText(context, "Gagal impor CSV: ${e.message}", Toast.LENGTH_SHORT).show() 
            }
        }
    }

    if (showChangePinDialog) {
        ChangePinDialog(user = currentUser, onDismiss = { showChangePinDialog = false }, onSave = { newPin ->
            viewModel.updatePin(currentUser, newPin); showChangePinDialog = false; Toast.makeText(context, "PIN berhasil diubah!", Toast.LENGTH_SHORT).show()
        })
    }

    if (showUserManagementDialog) {
        UserManagementDialog(
            users = users, currentUser = currentUser, onDismiss = { showUserManagementDialog = false },
            onAddUser = { name, pin, role -> viewModel.addUser(name, pin, role, currentUser) },
            onUpdateUser = { user -> viewModel.updateUser(user, currentUser) },
            onDeleteUser = { user -> viewModel.deleteUser(user, currentUser) }
        )
    }
    
    if (showShiftHistory) {
        ShiftHistoryDialog(shifts = shifts, onDismiss = { showShiftHistory = false })
    }

    if (showRawMaterialDialog) {
        RawMaterialManagementDialog(
            rawMaterials = rawMaterials,
            viewModel = viewModel,
            onDismiss = { showRawMaterialDialog = false }
        )
    }

    if (showPrinterDialog) {
        AlertDialog(
            onDismissRequest = { showPrinterDialog = false },
            title = { Text(LanguageManager.tr("pilih_printer", lang)) },
            text = {
                if (pairedPrinters.isEmpty()) Text(LanguageManager.tr("belum_printer", lang))
                else {
                    LazyColumn {
                        items(pairedPrinters) { device ->
                            TextButton(onClick = { settingsManager.printerMacAddress = device.address; savedPrinterMac = device.address; showPrinterDialog = false }) { Text("${device.name} (${device.address})") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPrinterDialog = false }) { Text(LanguageManager.tr("tutup", lang)) } }
        )
    }
    
    if (showKitchenDialog) {
        AlertDialog(
            onDismissRequest = { showKitchenDialog = false },
            title = { Text(LanguageManager.tr("pilih_printer_dapur", lang)) },
            text = {
                if (pairedPrinters.isEmpty()) Text(LanguageManager.tr("belum_printer_dapur", lang))
                else {
                    LazyColumn {
                        items(pairedPrinters) { device ->
                            TextButton(onClick = { settingsManager.kitchenPrinterMacAddress = device.address; savedKitchenMac = device.address; showKitchenDialog = false }) { Text("${device.name} (${device.address})") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showKitchenDialog = false }) { Text(LanguageManager.tr("tutup", lang)) } }
        )
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { }, title = { Text("Restore Berhasil") },
            text = { Text("Aplikasi harus ditutup dan dibuka kembali untuk menerapkan data baru.") },
            confirmButton = { Button(onClick = { android.os.Process.killProcess(android.os.Process.myPid()) }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)) { Text("TUTUP APLIKASI") } }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageManager.tr("pengaturan", lang), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }

        // --- TAMPILAN & AKSESIBILITAS ---
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(LanguageManager.tr("mode_kontras", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(LanguageManager.tr("mode_kontras_desc", lang), fontSize = 10.sp, color = SlateGrey)
                        }
                        Switch(checked = isHighContrast, onCheckedChange = { onToggleHighContrast() }, colors = SwitchDefaults.colors(checkedTrackColor = EmeraldGreen))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(LanguageManager.tr("mode_gelap", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Switch(checked = isDarkMode, onCheckedChange = { onToggleDarkMode() }, colors = SwitchDefaults.colors(checkedTrackColor = EmeraldGreen))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Column {
                        Text(LanguageManager.tr("ukuran_font", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Slider(value = uiScale, onValueChange = { onChangeUiScale(it) }, valueRange = 0.8f..1.5f, steps = 6, colors = SliderDefaults.colors(thumbColor = EmeraldGreen, activeTrackColor = EmeraldGreen))
                        Text("${LanguageManager.tr("skala", lang)}: ${(uiScale * 100).toInt()}%", fontSize = 12.sp, color = SlateGrey)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(LanguageManager.tr("bahasa", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Button(onClick = { onToggleLanguage() }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)) { Text(if (lang == "in") "Indonesia" else "English") }
                    }
                }
            }
        }

        // --- MODUL TOKO ---
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Modul Toko", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Aktifkan fitur sesuai kebutuhan bisnis Anda.", fontSize = 12.sp, color = SlateGrey)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mode Meja Restoran", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Tampilkan pilihan meja di halaman kasir.", fontSize = 11.sp, color = SlateGrey)
                        }
                        Switch(
                            checked = isTableModeEnabled, 
                            onCheckedChange = { isTableModeEnabled = it; settingsManager.isTableModeEnabled = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = EmeraldGreen)
                        )
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Multi-Mata Uang (USD)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Tampilkan estimasi harga dalam USD.", fontSize = 11.sp, color = SlateGrey)
                        }
                        Switch(
                            checked = enableMultiCurrency, 
                            onCheckedChange = { enableMultiCurrency = it; settingsManager.enableMultiCurrency = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = EmeraldGreen)
                        )
                    }
                    
                    if (enableMultiCurrency) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = exchangeRateUsd,
                            onValueChange = { exchangeRateUsd = it; settingsManager.exchangeRateUsd = it.toDoubleOrNull() ?: 15000.0 },
                            label = { Text(LanguageManager.tr("kurs_usd", lang)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }
        
        // --- MANAJEMEN AKUN & KEAMANAN ---
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(LanguageManager.tr("manajemen_akun", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${LanguageManager.tr("login_sebagai", lang)}: ${currentUser.name} (${currentUser.role})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateGreyDark)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showChangePinDialog = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AmberAccent)) { Text(LanguageManager.tr("ubah_pin", lang)) }
                        if (currentUser.role == "OWNER") {
                            Button(onClick = { showUserManagementDialog = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)) { Text(LanguageManager.tr("kelola_karyawan", lang)) }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showShiftHistory = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SlateGrey)) {
                        Text(LanguageManager.tr("riwayat_shift", lang))
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    // LOGIKA BIOMETRIK
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Login Sidik Jari", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Aktifkan login biometrik di halaman awal.", fontSize = 11.sp, color = SlateGrey)
                        }
                        Switch(
                            checked = isBiometricEnabled, 
                            onCheckedChange = { isChecked -> 
                                if (isChecked) {
                                    // Jika toggle dinyalakan, langsung tes sensor!
                                    if (activity == null) {
                                        Toast.makeText(context, "ERROR: Activity tidak ditemukan (Context issue).", Toast.LENGTH_LONG).show()
                                        return@Switch
                                    }

                                    val biometricManager = BiometricManager.from(context)
                                    val authStatus = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)

                                    when (authStatus) {
                                        BiometricManager.BIOMETRIC_SUCCESS -> {
                                            // Sensor siap! Munculkan pop-up tes
                                            val executor = ContextCompat.getMainExecutor(context)
                                            val biometricPrompt = BiometricPrompt(activity, executor,
                                                object : BiometricPrompt.AuthenticationCallback() {
                                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                        super.onAuthenticationSucceeded(result)
                                                        isBiometricEnabled = true
                                                        settingsManager.isBiometricEnabled = true
                                                        Toast.makeText(context, "BERHASIL! Sidik jari terdaftar dan berfungsi.", Toast.LENGTH_SHORT).show()
                                                    }
                                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                        super.onAuthenticationError(errorCode, errString)
                                                        isBiometricEnabled = false // Matikan toggle jika gagal
                                                        settingsManager.isBiometricEnabled = false
                                                        Toast.makeText(context, "Gagal: $errString", Toast.LENGTH_LONG).show()
                                                    }
                                                })

                                            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                                .setTitle("Aktivasi Sidik Jari")
                                                .setSubtitle("Sentuh sensor untuk mengonfirmasi")
                                                .setNegativeButtonText("Batal")
                                                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                                                .build()

                                            biometricPrompt.authenticate(promptInfo)
                                        }
                                        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                                            Toast.makeText(context, "Gagal: HP ini tidak memiliki sensor sidik jari fisik.", Toast.LENGTH_LONG).show()
                                            isBiometricEnabled = false
                                        }
                                        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                                            Toast.makeText(context, "Gagal: Sensor sidik jari sedang tidak tersedia (sibuk/error).", Toast.LENGTH_LONG).show()
                                            isBiometricEnabled = false
                                        }
                                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                                            Toast.makeText(context, "Gagal: Anda BELUM mendaftarkan sidik jari di Pengaturan HP (Keamanan -> Sidik Jari).", Toast.LENGTH_LONG).show()
                                            isBiometricEnabled = false
                                        }
                                        else -> {
                                            Toast.makeText(context, "Gagal: Error tidak dikenal (Kode: $authStatus).", Toast.LENGTH_LONG).show()
                                            isBiometricEnabled = false
                                        }
                                    }
                                } else {
                                    isBiometricEnabled = false
                                    settingsManager.isBiometricEnabled = false
                                    Toast.makeText(context, "Login sidik jari dimatikan.", Toast.LENGTH_SHORT).show()
                                }
                            }, 
                            colors = SwitchDefaults.colors(checkedTrackColor = EmeraldGreen)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { showRawMaterialDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)) {
                        Text("Kelola Bahan Baku (Resep)")
                    }
                }
            }
        }

        // --- PRINTER ---
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(LanguageManager.tr("printer_kasir", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(if (savedPrinterMac != null) "${LanguageManager.tr("terhubung", lang)}: $savedPrinterMac" else LanguageManager.tr("belum_printer", lang), fontSize = 12.sp, color = if(savedPrinterMac != null) EmeraldGreen else SlateGrey, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                pairedPrinters = printerManager.getPairedPrinters(); showPrinterDialog = true
                            } else { permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)) }
                        }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) { Text(LanguageManager.tr("pilih_printer", lang)) }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    Text(LanguageManager.tr("printer_dapur", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (savedKitchenMac != null) "${LanguageManager.tr("terhubung", lang)}: $savedKitchenMac" else LanguageManager.tr("belum_printer_dapur", lang), fontSize = 12.sp, color = if(savedKitchenMac != null) EmeraldGreen else SlateGrey, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                pairedPrinters = printerManager.getPairedPrinters(); showKitchenDialog = true
                            } else { kitchenPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)) }
                        }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = SlateGrey)
                    ) { Text(LanguageManager.tr("pilih_printer_dapur", lang)) }
                }
            }
        }

        // --- PROFIL TOKO ---
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(LanguageManager.tr("profil_toko", lang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = storeName, onValueChange = { storeName = it; settingsManager.storeName = it }, label = { Text(LanguageManager.tr("nama_toko", lang)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = storeAddress, onValueChange = { storeAddress = it; settingsManager.storeAddress = it }, label = { Text(LanguageManager.tr("alamat_toko", lang)) }, modifier = Modifier.fillMaxWidth(), maxLines = 2, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = storePhone, onValueChange = { storePhone = it; settingsManager.storePhone = it }, label = { Text(LanguageManager.tr("no_telp", lang)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = storeFooter, onValueChange = { storeFooter = it; settingsManager.storeFooter = it }, label = { Text(LanguageManager.tr("pesan_struk", lang)) }, modifier = Modifier.fillMaxWidth(), maxLines = 2, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = taxPercent, 
                        onValueChange = { taxPercent = it; val tax = it.toDoubleOrNull() ?: 0.0; settingsManager.taxPercent = tax; viewModel.setTaxPercent(tax) }, 
                        label = { Text(LanguageManager.tr("pajak", lang)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
        
        // --- HAPPY HOUR ---
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(LanguageManager.tr("happy_hour", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(LanguageManager.tr("happy_hour_desc", lang), fontSize = 12.sp, color = SlateGrey)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = hhStart, onValueChange = { hhStart = it; settingsManager.happyHourStart = it }, label = { Text(LanguageManager.tr("mulai", lang)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                        OutlinedTextField(value = hhEnd, onValueChange = { hhEnd = it; settingsManager.happyHourEnd = it }, label = { Text(LanguageManager.tr("selesai", lang)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = hhDiscount, onValueChange = { hhDiscount = it; settingsManager.happyHourDiscount = it.toDoubleOrNull() ?: 0.0 }, label = { Text(LanguageManager.tr("diskon_persen", lang)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                }
            }
        }

        // --- KEAMANAN DISKON ---
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(LanguageManager.tr("keamanan_diskon", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = maxDiscountCap,
                        onValueChange = { maxDiscountCap = it; settingsManager.maxDiscountCap = it.toLongOrNull() ?: 0L },
                        label = { Text(LanguageManager.tr("max_diskon", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // --- BACKUP ---
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(LanguageManager.tr("data_backup", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(LanguageManager.tr("data_backup_desc", lang), fontSize = 12.sp, color = SlateGrey)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { createBackupLauncher.launch("Zokas_Backup_${System.currentTimeMillis()}.db") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)) { Text(LanguageManager.tr("backup", lang)) }
                        Button(onClick = { restoreBackupLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AmberAccent)) { Text(LanguageManager.tr("restore", lang)) }
                    }
                }
            }
        }

        // --- CSV ---
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(LanguageManager.tr("manajemen_produk", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(LanguageManager.tr("manajemen_produk_desc", lang), fontSize = 12.sp, color = SlateGrey)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { csvDataToExport = viewModel.exportProductsToCsv(); createCsvLauncher.launch("Zokas_Produk_${System.currentTimeMillis()}.csv") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)) { Text(LanguageManager.tr("ekspor_csv", lang)) }
                        Button(onClick = { openCsvLauncher.launch(arrayOf("text/*", "text/csv", "application/csv")) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SlateGrey)) { Text(LanguageManager.tr("impor_csv", lang)) }
                    }
                }
            }
        }

        item { Text(LanguageManager.tr("log_aktivitas", lang), fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
        
        if (auditLogs.isEmpty()) {
            item { Text(LanguageManager.tr("belum_aktivitas", lang), fontSize = 12.sp, color = SlateGrey, modifier = Modifier.fillMaxWidth()) }
        } else {
            items(auditLogs) { log ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Text("[${dateFormat.format(Date(log.timestamp))}] ${log.userName}: ${log.action}", fontSize = 11.sp, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) { Text(LanguageManager.tr("logout", lang), fontWeight = FontWeight.Bold) }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun RawMaterialManagementDialog(
    rawMaterials: List<RawMaterialEntity>,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("gram") }
    var cost by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kelola Bahan Baku") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Bahan (cth: Biji Kopi)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = stock, onValueChange = { if (it.length <= 10) stock = it }, label = { Text("Stok Awal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Satuan") }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = cost, onValueChange = { if (it.length <= 10) cost = it }, label = { Text("Harga Modal / Satuan (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Daftar Bahan Baku:", fontWeight = FontWeight.Bold)
                rawMaterials.forEach { material ->
                    Text("- ${material.name}: ${material.stock} ${material.unit}", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = stock.toDoubleOrNull() ?: 0.0
                    val c = cost.toLongOrNull() ?: 0L
                    if (name.isNotBlank()) {
                        viewModel.addRawMaterial(name, s, unit, c)
                        name = ""
                        stock = ""
                        cost = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) { Text("TAMBAH BAHAN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
fun ChangePinDialog(user: UserEntity, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah PIN Keamanan") },
        text = {
            Column {
                Text("Masukkan PIN baru (6 digit):")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = newPin, onValueChange = { if (it.length <= 6) { newPin = it; error = "" } }, label = { Text("PIN Baru") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = confirmPin, onValueChange = { if (it.length <= 6) { confirmPin = it; error = "" } }, label = { Text("Konfirmasi PIN Baru") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, isError = error.isNotEmpty(), shape = RoundedCornerShape(12.dp))
                if (error.isNotEmpty()) { Text(error, color = ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
        },
        confirmButton = { Button(onClick = { if (newPin.length == 6 && newPin == confirmPin) { onSave(newPin) } else { error = "PIN harus 6 digit dan sama!" } }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)) { Text("SIMPAN PIN") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}