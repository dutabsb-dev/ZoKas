package com.karyaku.kasir

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.karyaku.kasir.data.local.AuditLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    auditLogs: List<AuditLogEntity>
) {
    val context = LocalContext.current
    val printerManager = remember { BluetoothPrinterManager(context) }
    
    var storeName by remember { mutableStateOf(settingsManager.storeName) }
    var storeFooter by remember { mutableStateOf(settingsManager.storeFooter) }
    var savedPrinterMac by remember { mutableStateOf(settingsManager.printerMacAddress) }
    
    var showPrinterDialog by remember { mutableStateOf(false) }
    var pairedPrinters by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    
    val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    // Izin Bluetooth
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val connectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
        if (connectGranted) {
            pairedPrinters = printerManager.getPairedPrinters()
            showPrinterDialog = true
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Pengaturan & Status", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // --- PENGATURAN PRINTER ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Printer Bluetooth", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            pairedPrinters = printerManager.getPairedPrinters()
                            showPrinterDialog = true
                        } else {
                            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
                        }
                    }) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pilih")
                    }
                }
                Text(
                    text = if (savedPrinterMac != null) "Terhubung: $savedPrinterMac" else "Belum ada printer dipilih",
                    fontSize = 12.sp,
                    color = if (savedPrinterMac != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // --- TEMA & BAHASA ---
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
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // ... (SISA KODE UI PRO & AUDIT LOG DI BAWAH INI SAMA SEPERTI SEBELUMNYA) ...
        // ... (Silakan salin sisa UI Pro, Kustomisasi Struk, Audit Log, dan Tombol Logout dari file SettingsScreen.kt Anda sebelumnya) ...

    }

    // DIALOG PILIH PRINTER
    if (showPrinterDialog) {
        AlertDialog(
            onDismissRequest = { showPrinterDialog = false },
            title = { Text("Pilih Printer Bluetooth") },
            text = {
                if (pairedPrinters.isEmpty()) {
                    Text("Tidak ada printer ditemukan. Pastikan printer sudah di-pairing di pengaturan Bluetooth HP Anda.")
                } else {
                    LazyColumn {
                        items(pairedPrinters) { device ->
                            TextButton(onClick = {
                                settingsManager.printerMacAddress = device.address
                                savedPrinterMac = device.address
                                showPrinterDialog = false
                            }) {
                                Text("${device.name} (${device.address})")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrinterDialog = false }) { Text("Tutup") }
            }
        )
    }
}