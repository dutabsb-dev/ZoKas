package com.karyaku.kasir

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
    var storeName by remember { mutableStateOf(settingsManager.storeName) }
    var storeFooter by remember { mutableStateOf(settingsManager.storeFooter) }
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }

    // Launcher Backup
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            if (BackupHelper.backupDatabase(context, uri)) {
                Toast.makeText(context, "Backup berhasil disimpan!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Gagal melakukan backup.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launcher Restore
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            if (BackupHelper.restoreDatabase(context, uri)) {
                showRestartDialog = true // Tampilkan dialog restart jika sukses
            } else {
                Toast.makeText(context, "Gagal memulihkan data.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showChangePinDialog) {
        ChangePinDialog(
            user = currentUser,
            onDismiss = { showChangePinDialog = false },
            onSave = { newPin ->
                viewModel.updatePin(currentUser, newPin)
                showChangePinDialog = false
                Toast.makeText(context, "PIN berhasil diubah!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { }, // Wajib di-restart, tidak bisa ditutup biasa
            title = { Text("Restore Berhasil") },
            text = { Text("Data berhasil dipulihkan. Aplikasi harus ditutup dan dibuka kembali agar data baru termuat sempurna.") },
            confirmButton = {
                Button(onClick = {
                    // Menutup aplikasi secara paksa agar Room Database me-reload file baru
                    android.os.Process.killProcess(android.os.Process.myPid())
                }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) {
                    Text("TUTUP APLIKASI SEKARANG")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Pengaturan & Status", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // --- PENGATURAN TEMA & BAHASA ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Mode Gelap", style = MaterialTheme.typography.titleMedium)
                    Switch(checked = isDarkMode, onCheckedChange = { onToggleDarkMode() })
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Bahasa", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { onToggleLanguage() }, colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)) {
                        Text(if (language == "in") "Indonesia" else "English")
                    }
                }
            }
        }
        
        // --- MANAJEMEN AKUN ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Manajemen Akun", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Login sebagai: ${currentUser.name} (${currentUser.role})", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showChangePinDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)
                ) {
                    Text("Ubah PIN Keamanan")
                }
            }
        }

        // --- STATUS PRO ---
        if (isPro) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD700)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Star, contentDescription = "Pro", tint = Color.White, modifier = Modifier.size(48.dp))
                        Text("VERSI PRO AKTIF", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Kustomisasi Struk", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = storeName, onValueChange = { storeName = it; settingsManager.storeName = it }, label = { Text("Nama Toko") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = storeFooter, onValueChange = { storeFooter = it; settingsManager.storeFooter = it }, label = { Text("Pesan Bawah Struk") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Versi Gratis", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Upgrade ke Pro untuk kustom struk & ekspor CSV.", textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onUpgradeClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) {
                        Text("Beli Versi Pro - Rp 10.000", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- DATA & BACKUP ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Data & Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Cadangkan seluruh data produk, transaksi, dan pelanggan ke file.", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { 
                            try { createBackupLauncher.launch("Zokas_Backup_${System.currentTimeMillis()}.db") } 
                            catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)
                    ) {
                        Text("BACKUP")
                    }
                    Button(
                        onClick = { 
                            try { restoreBackupLauncher.launch(arrayOf("*/*")) } 
                            catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)
                    ) {
                        Text("RESTORE")
                    }
                }
            }
        }

        // --- LOG AKTIVITAS (AUDIT TRAIL) ---
        Text("Log Aktivitas (Audit)", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
        
        if (auditLogs.isEmpty()) {
            Text("Belum ada aktivitas.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth())
        } else {
            auditLogs.forEach { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = "[${dateFormat.format(Date(log.timestamp))}] ${log.userName}: ${log.action}",
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // --- TOMBOL LOGOUT ---
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("LOGOUT (KELUAR)", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(32.dp))
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