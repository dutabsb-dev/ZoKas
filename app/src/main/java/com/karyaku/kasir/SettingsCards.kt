package com.karyaku.kasir

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.karyaku.kasir.data.local.UserEntity

// --- 1. CARD TAMPILAN & BAHASA ---
@Composable
fun SettingsAppearanceCard(
    isDarkMode: Boolean, onToggleDarkMode: () -> Unit,
    uiScale: Float, onChangeUiScale: (Float) -> Unit,
    language: String, onToggleLanguage: () -> Unit
) {
    val lang = language
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageManager.tr("mode_gelap", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Switch(checked = isDarkMode, onCheckedChange = { onToggleDarkMode() }, colors = SwitchDefaults.colors(checkedTrackColor = EmeraldGreen))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Column {
                Text(LanguageManager.tr("ukuran_font", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Slider(value = uiScale, onValueChange = { onChangeUiScale(it) }, valueRange = 0.8f..1.5f, steps = 6, colors = SliderDefaults.colors(thumbColor = EmeraldGreen, activeTrackColor = EmeraldGreen))
                Text("${LanguageManager.tr("skala", lang)}: ${(uiScale * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageManager.tr("bahasa", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(onClick = { onToggleLanguage() }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text(if (lang == "in") "Indonesia" else "English") }
            }
        }
    }
}

// --- 2. CARD MANAJEMEN AKUN ---
@Composable
fun SettingsAccountCard(
    currentUser: UserEntity,
    isBiometricEnabled: Boolean,
    onBiometricToggle: (Boolean) -> Unit,
    onChangePinClick: () -> Unit,
    onManageStaffClick: () -> Unit,
    onShiftHistoryClick: () -> Unit,
    language: String
) {
    val lang = language
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(LanguageManager.tr("manajemen_akun", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("${LanguageManager.tr("login_sebagai", lang)}: ${currentUser.name} (${currentUser.role})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onChangePinClick, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AmberAccent), shape = RoundedCornerShape(12.dp)) { Text(LanguageManager.tr("ubah_pin", lang), fontSize = 12.sp) }
                if (currentUser.role == "OWNER") {
                    Button(onClick = onManageStaffClick, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text(LanguageManager.tr("kelola_karyawan", lang), fontSize = 12.sp) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onShiftHistoryClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SlateGrey), shape = RoundedCornerShape(12.dp)) {
                Text(LanguageManager.tr("riwayat_shift", lang), fontSize = 12.sp)
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Login Sidik Jari", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Aktifkan login biometrik di halaman awal.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isBiometricEnabled, 
                    onCheckedChange = { isChecked -> 
                        if (isChecked) {
                            if (activity == null) {
                                Toast.makeText(context, "ERROR: Activity tidak ditemukan.", Toast.LENGTH_LONG).show()
                                return@Switch
                            }
                            val biometricManager = BiometricManager.from(context)
                            val authStatus = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                            if (authStatus == BiometricManager.BIOMETRIC_SUCCESS) {
                                val executor = ContextCompat.getMainExecutor(context)
                                val biometricPrompt = BiometricPrompt(activity, executor,
                                    object : BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                            super.onAuthenticationSucceeded(result)
                                            onBiometricToggle(true)
                                            Toast.makeText(context, "Sidik jari berfungsi.", Toast.LENGTH_SHORT).show()
                                        }
                                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                            super.onAuthenticationError(errorCode, errString)
                                            onBiometricToggle(false)
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
                            } else {
                                Toast.makeText(context, "HP belum mendaftarkan sidik jari.", Toast.LENGTH_LONG).show()
                                onBiometricToggle(false)
                            }
                        } else {
                            onBiometricToggle(false)
                        }
                    }, 
                    colors = SwitchDefaults.colors(checkedTrackColor = EmeraldGreen)
                )
            }
        }
    }
}

// --- 3. CARD MODUL TOKO (REDESIGN PROFESIONAL) ---
@Composable
fun SettingsModuleCard(
    isTableModeEnabled: Boolean, onTableModeToggle: (Boolean) -> Unit,
    enableMultiCurrency: Boolean, onMultiCurrencyToggle: (Boolean) -> Unit,
    exchangeRateUsd: String, onExchangeRateChange: (String) -> Unit,
    onRawMaterialClick: () -> Unit,
    language: String
) {
    val lang = language
    Card(
        modifier = Modifier.fillMaxWidth(), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            // Header
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Modul Toko", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Sesuaikan fitur dengan kebutuhan bisnis Anda.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Item 1: Mode Meja
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTableModeToggle(!isTableModeEnabled) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Restaurant, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mode Meja Restoran", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Tampilkan pilihan meja di halaman kasir.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isTableModeEnabled, 
                    onCheckedChange = onTableModeToggle, 
                    colors = SwitchDefaults.colors(checkedTrackColor = EmeraldGreen)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

            // Item 2: Multi-Currency
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMultiCurrencyToggle(!enableMultiCurrency) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Payments, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Multi-Mata Uang (USD)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Estimasi harga dalam Dolar AS.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = enableMultiCurrency, 
                    onCheckedChange = onMultiCurrencyToggle, 
                    colors = SwitchDefaults.colors(checkedTrackColor = EmeraldGreen)
                )
            }

            // Input Kurs (Hanya muncul jika Multi-Currency aktif)
            if (enableMultiCurrency) {
                OutlinedTextField(
                    value = exchangeRateUsd, 
                    onValueChange = onExchangeRateChange,
                    label = { Text(LanguageManager.tr("kurs_usd", lang), fontSize = 12.sp) }, 
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp), 
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

            // Item 3: Bahan Baku (Menu Style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRawMaterialClick() }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Inventory, contentDescription = null, tint = SlateGrey, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Kelola Bahan Baku", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Atur stok bahan mentah & resep (BOM).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// --- 4. CARD KONEKSI PRINTER ---
@Composable
fun SettingsPrinterCard(
    savedPrinterMac: String?,
    savedKitchenMac: String?,
    onPrinterClick: () -> Unit,
    onKitchenClick: () -> Unit,
    language: String
) {
    val lang = language
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(LanguageManager.tr("printer_kasir", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (savedPrinterMac != null) "${LanguageManager.tr("terhubung", lang)}: $savedPrinterMac" else LanguageManager.tr("belum_printer", lang), fontSize = 12.sp, color = if(savedPrinterMac != null) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Button(
                onClick = onPrinterClick, 
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), 
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(12.dp)
            ) { Text(LanguageManager.tr("pilih_printer", lang)) }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            
            Text(LanguageManager.tr("printer_dapur", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (savedKitchenMac != null) "${LanguageManager.tr("terhubung", lang)}: $savedKitchenMac" else LanguageManager.tr("belum_printer_dapur", lang), fontSize = 12.sp, color = if(savedKitchenMac != null) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Button(
                onClick = onKitchenClick, 
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), 
                colors = ButtonDefaults.buttonColors(containerColor = SlateGrey),
                shape = RoundedCornerShape(12.dp)
            ) { Text(LanguageManager.tr("pilih_printer_dapur", lang)) }
        }
    }
}

// --- 5. CARD PROFIL TOKO ---
@Composable
fun SettingsStoreProfileCard(
    storeName: String, onStoreNameChange: (String) -> Unit,
    storeAddress: String, onStoreAddressChange: (String) -> Unit,
    storePhone: String, onStorePhoneChange: (String) -> Unit,
    storeFooter: String, onStoreFooterChange: (String) -> Unit,
    taxPercent: String, onTaxPercentChange: (String) -> Unit,
    language: String
) {
    val lang = language
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(LanguageManager.tr("profil_toko", lang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = storeName, onValueChange = onStoreNameChange, label = { Text(LanguageManager.tr("nama_toko", lang)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = storeAddress, onValueChange = onStoreAddressChange, label = { Text(LanguageManager.tr("alamat_toko", lang)) }, modifier = Modifier.fillMaxWidth(), maxLines = 2, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = storePhone, onValueChange = onStorePhoneChange, label = { Text(LanguageManager.tr("no_telp", lang)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = storeFooter, onValueChange = onStoreFooterChange, label = { Text(LanguageManager.tr("pesan_struk", lang)) }, modifier = Modifier.fillMaxWidth(), maxLines = 2, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = taxPercent, onValueChange = onTaxPercentChange, label = { Text(LanguageManager.tr("pajak", lang)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
        }
    }
}

// --- 6. CARD HAPPY HOUR & DISKON ---
@Composable
fun SettingsDiscountCard(
    hhStart: String, onHhStartChange: (String) -> Unit,
    hhEnd: String, onHhEndChange: (String) -> Unit,
    hhDiscount: String, onHhDiscountChange: (String) -> Unit,
    maxDiscountCap: String, onMaxDiscountCapChange: (String) -> Unit,
    language: String
) {
    val lang = language
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(LanguageManager.tr("happy_hour", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(LanguageManager.tr("happy_hour_desc", lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = hhStart, onValueChange = onHhStartChange, label = { Text(LanguageManager.tr("mulai", lang)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = hhEnd, onValueChange = onHhEndChange, label = { Text(LanguageManager.tr("selesai", lang)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = hhDiscount, onValueChange = onHhDiscountChange, label = { Text(LanguageManager.tr("diskon_persen", lang)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = maxDiscountCap, onValueChange = onMaxDiscountCapChange, label = { Text(LanguageManager.tr("max_diskon", lang)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
        }
    }
}

// --- 7. CARD BACKUP & CSV ---
@Composable
fun SettingsBackupCard(
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onExportCsvClick: () -> Unit,
    onImportCsvClick: () -> Unit,
    language: String
) {
    val lang = language
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(LanguageManager.tr("data_backup", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(LanguageManager.tr("data_backup_desc", lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBackupClick, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text(LanguageManager.tr("backup", lang)) }
                Button(onClick = onRestoreClick, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AmberAccent), shape = RoundedCornerShape(12.dp)) { Text(LanguageManager.tr("restore", lang)) }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Text(LanguageManager.tr("manajemen_produk", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(LanguageManager.tr("manajemen_produk_desc", lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExportCsvClick, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text(LanguageManager.tr("ekspor_csv", lang)) }
                Button(onClick = onImportCsvClick, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SlateGrey), shape = RoundedCornerShape(12.dp)) { Text(LanguageManager.tr("impor_csv", lang)) }
            }
        }
    }
}