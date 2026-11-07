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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.AuditLogEntity
import com.karyaku.kasir.data.local.UserEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    isPro: Boolean,
    onUpgradeClick: () -> Unit,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    language: String,
    onToggleLanguage: () -> Unit,
    onLogout: () -> Unit,
    auditLogs: List<AuditLogEntity>,
    currentUser: UserEntity,
    viewModel: PosViewModel
) {
    val context = LocalContext.current
    // FIX: Tambahkan ?: "" untuk menghindari crash jika SharedPreferences mengembalikan null
    var storeName by remember { mutableStateOf(settingsManager.storeName ?: "") }
    var storeFooter by remember { mutableStateOf(settingsManager.storeFooter ?: "") }
    var storeAddress by remember { mutableStateOf(settingsManager.storeAddress ?: "") }
    var storePhone by remember { mutableStateOf(settingsManager.storePhone ?: "") }
    var taxPercent by remember { mutableStateOf(settingsManager.taxPercent.toString()) }
    
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var showUserManagementDialog by remember { mutableStateOf(false) }
    var showShiftHistory by remember { mutableStateOf(false) }
    
    val users by viewModel.users.collectAsStateWithLifecycle()
    val shifts by viewModel.shifts.collectAsStateWithLifecycle()
    
    val printerManager = remember { BluetoothPrinterManager(context) }
    var savedPrinterMac by remember { mutableStateOf(settingsManager.printerMacAddress) }
    var showPrinterDialog by remember { mutableStateOf(false) }
    var pairedPrinters by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms[Manifest.permission.BLUETOOTH_CONNECT] == true) {
            pairedPrinters = printerManager.getPairedPrinters()
            showPrinterDialog = true
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) {
            if (BackupHelper.backupDatabase(context, uri)) Toast.makeText(context, "Backup berhasil!", Toast.LENGTH_SHORT).show()
            else Toast.makeText(context, "Gagal backup.", Toast.LENGTH_SHORT).show()
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            if (BackupHelper.restoreDatabase(context, uri)) showRestartDialog = true
            else Toast.makeText(context, "Gagal restore.", Toast.LENGTH_SHORT).show()
        }
    }

    if (showChangePinDialog) {
        ChangePinDialog(user = currentUser, onDismiss = { showChangePinDialog = false }, onSave = { newPin ->
            viewModel.updatePin(currentUser, newPin); showChangePinDialog = false; Toast.makeText(context, "PIN diubah!", Toast.LENGTH_SHORT).show()
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

    if (showPrinterDialog) {
        AlertDialog(
            onDismissRequest = { showPrinterDialog = false },
            title = { Text("Pilih Printer Bluetooth") },
            text = {
                if (pairedPrinters.isEmpty()) Text("Tidak ada printer. Pastikan sudah pairing di HP.")
                else {
                    LazyColumn {
                        items(pairedPrinters) { device ->
                            TextButton(onClick = {
                                settingsManager.printerMacAddress = device.address
                                savedPrinterMac = device.address
                                showPrinterDialog = false
                            }) { Text("${device.name} (${device.address})") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPrinterDialog = false }) { Text("Tutup") } }
        )
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { }, title = { Text("Restore Berhasil") },
            text = { Text("Aplikasi harus ditutup dan dibuka kembali.") },
            confirmButton = { Button(onClick = { android.os.Process.killProcess(android.os.Process.myPid()) }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("TUTUP") } }
        )
    }

    // FIX BESAR: Mengubah Column menjadi LazyColumn untuk mencegah Out of Memory (OOM) saat Log banyak
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Pengaturan & Status", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }

        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Mode Gelap", style = MaterialTheme.typography.titleMedium); Switch(checked = isDarkMode, onCheckedChange = { onToggleDarkMode() })
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Bahasa", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { onToggleLanguage() }, colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)) { Text(if (language == "in") "Indonesia" else "English") }
                    }
                }
            }
        }
        
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Manajemen Akun", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Login sebagai: ${currentUser.name} (${currentUser.role})", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showChangePinDialog = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("Ubah PIN") }
                        if (currentUser.role == "OWNER") {
                            Button(onClick = { showUserManagementDialog = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)) { Text("Kelola Karyawan") }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showShiftHistory = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)) {
                        Text("LIHAT RIWAYAT SHIFT KASIR")
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Printer Thermal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(if (savedPrinterMac != null) "Terhubung: $savedPrinterMac" else "Belum ada printer", fontSize = 12.sp, color = if(savedPrinterMac != null) Color(0xFF4CAF50) else Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                pairedPrinters = printerManager.getPairedPrinters(); showPrinterDialog = true
                            } else {
                                permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
                            }
                        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)
                    ) { Text("PILIH PRINTER") }
                }
            }
        }

        if (isPro) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD700)), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Star, contentDescription = "Pro", tint = Color.White, modifier = Modifier.size(48.dp))
                            Text("VERSI PRO AKTIF", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Profil Toko & Struk", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = storeName, onValueChange = { storeName = it; settingsManager.storeName = it }, label = { Text("Nama Toko") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = storeAddress, onValueChange = { storeAddress = it; settingsManager.storeAddress = it }, label = { Text("Alamat Toko") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = storePhone, onValueChange = { storePhone = it; settingsManager.storePhone = it }, label = { Text("No. Telepon") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = storeFooter, onValueChange = { storeFooter = it; settingsManager.storeFooter = it }, label = { Text("Pesan Bawah Struk") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = taxPercent, 
                        onValueChange = { 
                            taxPercent = it
                            val tax = it.toDoubleOrNull() ?: 0.0
                            settingsManager.taxPercent = tax
                            viewModel.setTaxPercent(tax)
                        }, 
                        label = { Text("Pajak (%) cth: 11") }, 
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                        modifier = Modifier.fillMaxWidth(), 
                        singleLine = true
                    )
                }
            }
        } else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Versi Gratis", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Upgrade ke Pro untuk kustom struk & ekspor CSV.", textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onUpgradeClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("Beli Versi Pro - Rp 10.000", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Data & Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Cadangkan seluruh data ke file.", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { createBackupLauncher.launch("Zokas_Backup_${System.currentTimeMillis()}.db") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)) { Text("BACKUP") }
                        Button(onClick = { restoreBackupLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) { Text("RESTORE") }
                    }
                }
            }
        }

        item { Text("Log Aktivitas (Audit)", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
        
        if (auditLogs.isEmpty()) {
            item { Text("Belum ada aktivitas.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth()) }
        } else {
            // FIX: Menggunakan items() di dalam LazyColumn agar tidak OOM/Force Close jika log ribuan
            items(auditLogs) { log ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Text("[${dateFormat.format(Date(log.timestamp))}] ${log.userName}: ${log.action}", fontSize = 11.sp, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("LOGOUT (KELUAR)", fontWeight = FontWeight.Bold) }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
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
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 6) { newPin = it; error = "" } },
                    label = { Text("PIN Baru") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6) { confirmPin = it; error = "" } },
                    label = { Text("Konfirmasi PIN Baru") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error.isNotEmpty()
                )
                if (error.isNotEmpty()) {
                    Text(error, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPin.length == 6 && newPin == confirmPin) {
                        onSave(newPin)
                    } else {
                        error = "PIN harus 6 digit dan sama!"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)
            ) { Text("SIMPAN PIN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}