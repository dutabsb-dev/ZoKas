package com.karyaku.kasir

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.karyaku.kasir.data.local.UserEntity
import java.io.File
import java.io.FileOutputStream

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
                Text(LanguageManager.tr("mode_gelap", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Switch(
                    checked = isDarkMode, 
                    onCheckedChange = { onToggleDarkMode() }, 
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = EmeraldGreen,
                        uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Column {
                Text(LanguageManager.tr("ukuran_font", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Slider(value = uiScale, onValueChange = { onChangeUiScale(it) }, valueRange = 0.8f..1.5f, steps = 6, colors = SliderDefaults.colors(thumbColor = EmeraldGreen, activeTrackColor = EmeraldGreen))
                Text("${LanguageManager.tr("skala", lang)}: ${(uiScale * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageManager.tr("bahasa", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Button(onClick = { onToggleLanguage() }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { 
                    Text(if (lang == "in") "Indonesia" else "English", color = Color.White) 
                }
            }
        }
    }
}

// --- 2. CARD MANAJEMEN AKUN (MINIMIZE) ---
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
    var isExpanded by remember { mutableStateOf(false) }
    val lang = language
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(LanguageManager.tr("manajemen_akun", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(currentUser.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Toggle", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    
                    Text("${LanguageManager.tr("login_sebagai", lang)}: ${currentUser.name} (${currentUser.role})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onChangePinClick, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AmberAccent), shape = RoundedCornerShape(12.dp)) { Text(LanguageManager.tr("ubah_pin", lang), fontSize = 12.sp, color = Color.White) }
                        if (currentUser.role == "OWNER") {
                            Button(onClick = onManageStaffClick, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text(LanguageManager.tr("kelola_karyawan", lang), fontSize = 12.sp, color = Color.White) }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onShiftHistoryClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SlateGrey), shape = RoundedCornerShape(12.dp)) {
                        Text(LanguageManager.tr("riwayat_shift", lang), fontSize = 12.sp, color = Color.White)
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Login Sidik Jari", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = EmeraldGreen,
                                uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    }
}

// --- 3. CARD MODUL TOKO (MINIMIZE) ---
@Composable
fun SettingsModuleCard(
    isTableModeEnabled: Boolean, onTableModeToggle: (Boolean) -> Unit,
    enableMultiCurrency: Boolean, onMultiCurrencyToggle: (Boolean) -> Unit,
    exchangeRateUsd: String, onExchangeRateChange: (String) -> Unit,
    onRawMaterialClick: () -> Unit,
    enableOrderTypes: Boolean, onOrderTypesToggle: (Boolean) -> Unit,
    enableWholesale: Boolean, onWholesaleToggle: (Boolean) -> Unit,
    enablePreOrder: Boolean, onPreOrderToggle: (Boolean) -> Unit,
    enableMembership: Boolean, onMembershipToggle: (Boolean) -> Unit,
    onManageMembershipClick: () -> Unit,
    enableSupplier: Boolean, onSupplierToggle: (Boolean) -> Unit,
    onManageSupplierClick: () -> Unit,
    onCreatePOClick: () -> Unit,
    enableReservation: Boolean, onReservationToggle: (Boolean) -> Unit,
    onManageReservationClick: () -> Unit,
    enableFinance: Boolean, onFinanceToggle: (Boolean) -> Unit,
    onManageReceivableClick: () -> Unit,
    onManagePayableClick: () -> Unit,
    onManageExpenseClick: () -> Unit,
    language: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    val lang = language
    
    val switchColors = SwitchDefaults.colors(
        checkedTrackColor = EmeraldGreen,
        uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    )

    Card(
        modifier = Modifier.fillMaxWidth(), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Modul Toko", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Sesuaikan fitur dengan kebutuhan bisnis.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Toggle", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    // Mode Meja
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onTableModeToggle(!isTableModeEnabled) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Restaurant, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mode Meja Restoran", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Tampilkan pilihan meja di halaman kasir.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isTableModeEnabled, onCheckedChange = onTableModeToggle, colors = switchColors)
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Multi Currency
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onMultiCurrencyToggle(!enableMultiCurrency) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Multi-Mata Uang (USD)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Estimasi harga dalam Dolar AS.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enableMultiCurrency, onCheckedChange = onMultiCurrencyToggle, colors = switchColors)
                    }

                    if (enableMultiCurrency) {
                        OutlinedTextField(
                            value = exchangeRateUsd, 
                            onValueChange = onExchangeRateChange,
                            label = { Text(LanguageManager.tr("kurs_usd", lang), fontSize = 12.sp) }, 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), 
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Tipe Pesanan
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOrderTypesToggle(!enableOrderTypes) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocalDining, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tipe Pesanan (Order Type)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Dine-In, Take-Away, Delivery, Pick-Up.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enableOrderTypes, onCheckedChange = onOrderTypesToggle, colors = switchColors)
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Harga Grosir
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onWholesaleToggle(!enableWholesale) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Harga Grosir (Retail)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Harga khusus untuk pembelian jumlah banyak.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enableWholesale, onCheckedChange = onWholesaleToggle, colors = switchColors)
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Pre-Order
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPreOrderToggle(!enablePreOrder) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pre-Order (Pesanan Musiman)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Atur tanggal pengambilan pesanan pelanggan.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enablePreOrder, onCheckedChange = onPreOrderToggle, colors = switchColors)
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Reservasi (Booking)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onReservationToggle(!enableReservation) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reservasi / Booking", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Jadwal meja atau terapis (Salon/Klinik).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enableReservation, onCheckedChange = onReservationToggle, colors = switchColors)
                    }

                    if (enableReservation) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onManageReservationClick() }.padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Kelola Jadwal Reservasi", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Lihat & buat jadwal pelanggan.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Membership
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onMembershipToggle(!enableMembership) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CardMembership, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Paket Langganan (Membership)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Jual paket kuota (cth: Facial 5x).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enableMembership, onCheckedChange = onMembershipToggle, colors = switchColors)
                    }

                    if (enableMembership) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onManageMembershipClick() }.padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Kelola Paket Langganan", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Buat dan atur daftar paket.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Supplier & PO
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSupplierToggle(!enableSupplier) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Supplier & Pembelian (PO)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Catat barang masuk dari supplier.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enableSupplier, onCheckedChange = onSupplierToggle, colors = switchColors)
                    }

                    if (enableSupplier) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onManageSupplierClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Kelola Supplier", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Daftar pemasok barang.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onCreatePOClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Buat Pembelian (PO)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Catat barang masuk & tambah stok.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // MODUL KEUANGAN (FINANCE)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onFinanceToggle(!enableFinance) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Modul Keuangan (Finance)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Kelola hutang, piutang & pengeluaran.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enableFinance, onCheckedChange = onFinanceToggle, colors = switchColors)
                    }

                    if (enableFinance) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onManageReceivableClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Buku Piutang (Bon)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Daftar bon pelanggan yang belum lunas.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onManagePayableClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.RequestQuote, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Buku Hutang (Supplier)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Daftar PO yang belum dibayar.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onManageExpenseClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MoneyOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pengeluaran Operasional", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Catat biaya gaji, listrik, sewa, dll.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Bahan Baku
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onRawMaterialClick() }.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Inventory, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kelola Bahan Baku", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Atur stok bahan mentah & resep (BOM).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// --- 4. CARD KONEKSI PRINTER (MINIMIZE) ---
@Composable
fun SettingsPrinterCard(
    savedPrinterMac: String?,
    savedKitchenMac: String?,
    onPrinterClick: () -> Unit,
    onKitchenClick: () -> Unit,
    language: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    val lang = language

    Card(
        modifier = Modifier.fillMaxWidth(), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(LanguageManager.tr("printer_kasir", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (savedPrinterMac != null) "Terhubung" else "Belum diatur", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Toggle", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    
                    Text(LanguageManager.tr("printer_kasir", lang), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (savedPrinterMac != null) "${LanguageManager.tr("terhubung", lang)}: $savedPrinterMac" else LanguageManager.tr("belum_printer", lang), fontSize = 12.sp, color = if(savedPrinterMac != null) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onPrinterClick, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text(LanguageManager.tr("pilih_printer", lang), color = Color.White) }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(LanguageManager.tr("printer_dapur", lang), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (savedKitchenMac != null) "${LanguageManager.tr("terhubung", lang)}: $savedKitchenMac" else LanguageManager.tr("belum_printer_dapur", lang), fontSize = 12.sp, color = if(savedKitchenMac != null) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onKitchenClick, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = SlateGrey), shape = RoundedCornerShape(12.dp)) { Text(LanguageManager.tr("pilih_printer_dapur", lang), color = Color.White) }
                }
            }
        }
    }
}

// --- 5. CARD PROFIL TOKO (MINIMIZE + LOGO + SERVICE CHARGE) ---
@Composable
fun SettingsStoreProfileCard(
    storeName: String, onStoreNameChange: (String) -> Unit,
    storeAddress: String, onStoreAddressChange: (String) -> Unit,
    storePhone: String, onStorePhoneChange: (String) -> Unit,
    storeFooter: String, onStoreFooterChange: (String) -> Unit,
    taxPercent: String, onTaxPercentChange: (String) -> Unit,
    serviceChargePercent: String, onServiceChargeChange: (String) -> Unit,
    logoPath: String?, onLogoChange: (String) -> Unit,
    language: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    val lang = language
    val context = LocalContext.current

    val logoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = File(context.filesDir, "store_logo.jpg")
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                onLogoChange(file.absolutePath)
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal simpan logo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(LanguageManager.tr("profil_toko", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(storeName.ifBlank { "Belum diatur" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Toggle", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Box(
                            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).clickable { logoPickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!logoPath.isNullOrEmpty() && File(logoPath).exists()) {
                                AsyncImage(model = File(logoPath), contentDescription = "Logo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(Icons.Default.Store, contentDescription = "Upload Logo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Logo Toko", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Ketuk gambar untuk mengganti logo di struk & aplikasi.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    OutlinedTextField(value = storeName, onValueChange = onStoreNameChange, label = { Text(LanguageManager.tr("nama_toko", lang)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = storeAddress, onValueChange = onStoreAddressChange, label = { Text(LanguageManager.tr("alamat_toko", lang)) }, modifier = Modifier.fillMaxWidth(), maxLines = 2, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = storePhone, onValueChange = onStorePhoneChange, label = { Text(LanguageManager.tr("no_telp", lang)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = storeFooter, onValueChange = onStoreFooterChange, label = { Text(LanguageManager.tr("pesan_struk", lang)) }, modifier = Modifier.fillMaxWidth(), maxLines = 2, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = taxPercent, onValueChange = onTaxPercentChange, label = { Text(LanguageManager.tr("pajak", lang)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = serviceChargePercent, onValueChange = onServiceChargeChange, label = { Text("Service Charge (%) cth: 5") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                }
            }
        }
    }
}

// --- 6. CARD HAPPY HOUR & DISKON (MINIMIZE) ---
@Composable
fun SettingsDiscountCard(
    hhStart: String, onHhStartChange: (String) -> Unit,
    hhEnd: String, onHhEndChange: (String) -> Unit,
    hhDiscount: String, onHhDiscountChange: (String) -> Unit,
    maxDiscountCap: String, onMaxDiscountCapChange: (String) -> Unit,
    language: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    val lang = language

    Card(
        modifier = Modifier.fillMaxWidth(), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(LanguageManager.tr("happy_hour", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("$hhStart - $hhEnd", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Toggle", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    
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
    }
}

// --- 7. CARD BACKUP & CSV (MINIMIZE) ---
@Composable
fun SettingsBackupCard(
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onExportCsvClick: () -> Unit,
    onImportCsvClick: () -> Unit,
    language: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    val lang = language
    
    Card(
        modifier = Modifier.fillMaxWidth(), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(LanguageManager.tr("data_backup", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Cadangkan seluruh data ke file.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Toggle", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    
                    Text(LanguageManager.tr("data_backup_desc", lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onBackupClick, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text(LanguageManager.tr("backup", lang), color = Color.White) }
                        Button(onClick = onRestoreClick, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AmberAccent), shape = RoundedCornerShape(12.dp)) { Text(LanguageManager.tr("restore", lang), color = Color.White) }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Text(LanguageManager.tr("manajemen_produk", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(LanguageManager.tr("manajemen_produk_desc", lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onExportCsvClick, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) { Text(LanguageManager.tr("ekspor_csv", lang), color = Color.White) }
                        Button(onClick = onImportCsvClick, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SlateGrey), shape = RoundedCornerShape(12.dp)) { Text(LanguageManager.tr("impor_csv", lang), color = Color.White) }
                    }
                }
            }
        }
    }
}

// --- 8. CARD PENGATURAN QRIS (BARU) ---
@Composable
fun SettingsQrisCard(
    qrisPayload: String,
    onQrisPayloadChange: (String) -> Unit,
    enableAutoDetect: Boolean,
    onToggleAutoDetect: (Boolean) -> Unit,
    onManagePaymentMethodsClick: () -> Unit,
    language: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    val lang = language

    Card(
        modifier = Modifier.fillMaxWidth(), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Pembayaran QRIS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (qrisPayload.isNotBlank()) "QRIS Statis Terpasang" else "Belum diatur", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Toggle", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    
                    Button(
                        onClick = onManagePaymentMethodsClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Kelola Metode Pembayaran", color = Color.White)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text("QRIS Statis (Payload)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Tempelkan kode teks QRIS dari bank Anda. Aplikasi akan mengubah nominal otomatis.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = qrisPayload,
                        onValueChange = onQrisPayloadChange,
                        label = { Text("Payload String (000201...)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Deteksi Otomatis (Notifikasi)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Dengarkan notifikasi bank untuk konfirmasi lunas.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = enableAutoDetect, 
                            onCheckedChange = onToggleAutoDetect, 
                            colors = SwitchDefaults.colors(checkedTrackColor = EmeraldGreen)
                        )
                    }
                }
            }
        }
    }
}

// --- 9. DIALOG KELOLA METODE PEMBAYARAN ---
@Composable
fun PaymentMethodManagementDialog(
    currentMethods: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val availableMethods = listOf("CASH", "QRIS", "Bank Transfer", "Virtual Account")
    val selectedMethods = remember { mutableStateListOf<String>().apply { addAll(currentMethods) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Metode Pembayaran") },
        text = {
            Column {
                Text("Pilih metode yang diterima di toko:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                availableMethods.forEach { method ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (selectedMethods.contains(method)) selectedMethods.remove(method)
                            else selectedMethods.add(method)
                        }.padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = selectedMethods.contains(method),
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen)
                        )
                        Text(method, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (selectedMethods.isNotEmpty()) onSave(selectedMethods.toSet()) },
                enabled = selectedMethods.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(12.dp)
            ) { Text("SIMPAN", color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}