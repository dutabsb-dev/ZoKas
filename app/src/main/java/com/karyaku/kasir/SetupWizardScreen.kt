package com.karyaku.kasir

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karyaku.kasir.data.local.UserEntity

private data class BusinessTypePreset(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val recommendedModules: List<String>,
    val enableTableMode: Boolean,
    val enableOrderTypes: Boolean,
    val enableWholesale: Boolean,
    val enablePreOrder: Boolean,
    val enableMembership: Boolean,
    val enableReservation: Boolean,
    val enableSupplier: Boolean,
    val enableCrm: Boolean
)

private val businessTypePresets = listOf(
    BusinessTypePreset(
        key = "KULINER",
        title = "Kuliner (Restoran, Cafe, Warung)",
        subtitle = "Cocok untuk rumah makan, kedai kopi, bakery, & minuman.",
        icon = Icons.Default.Restaurant,
        recommendedModules = listOf(
            "Mode Meja Restoran (Dine-In & Gabung Meja)",
            "Tipe Pesanan (Dine-In, Take-Away, Delivery)",
            "Layar Dapur (KDS) & Printer Dapur",
            "Resep Produk (BOM) & Pemotongan Bahan Baku"
        ),
        enableTableMode = true,
        enableOrderTypes = true,
        enableWholesale = false,
        enablePreOrder = false,
        enableMembership = false,
        enableReservation = false,
        enableSupplier = true,
        enableCrm = false
    ),
    BusinessTypePreset(
        key = "RETAIL",
        title = "Retail (Toko, Minimarket, Grosir)",
        subtitle = "Cocok untuk toko kelontong, minimarket, elektronik, & bangunan.",
        icon = Icons.Default.Storefront,
        recommendedModules = listOf(
            "Harga Grosir Otomatis (Wholesale Min. Qty)",
            "Kode Unik Produk (SKU) & Scan Barcode Cepat",
            "Supplier & Pembelian Stok (Purchase Order)",
            "Multi-Varian Satuan (Biji, Renteng, Dus)"
        ),
        enableTableMode = false,
        enableOrderTypes = false,
        enableWholesale = true,
        enablePreOrder = false,
        enableMembership = false,
        enableReservation = false,
        enableSupplier = true,
        enableCrm = false
    ),
    BusinessTypePreset(
        key = "JASA",
        title = "Jasa (Salon, Laundry, Bengkel, Klinik)",
        subtitle = "Cocok untuk barbershop, klinik kecantikan, cuci kendaraan, & servis.",
        icon = Icons.Default.ContentCut,
        recommendedModules = listOf(
            "Paket Langganan / Kuota (Membership)",
            "Reservasi & Jadwal Booking Pelanggan",
            "Penugasan Staf / Terapis & Komisi per Layanan",
            "Modul CRM & Riwayat Pelanggan Setia"
        ),
        enableTableMode = false,
        enableOrderTypes = false,
        enableWholesale = false,
        enablePreOrder = false,
        enableMembership = true,
        enableReservation = true,
        enableSupplier = false,
        enableCrm = true
    ),
    BusinessTypePreset(
        key = "MUSIMAN",
        title = "Musiman (Toko Kue, Hampers, Event)",
        subtitle = "Cocok untuk usaha PO kue kering, katering, bazar, & musiman.",
        icon = Icons.Default.EventAvailable,
        recommendedModules = listOf(
            "Pre-Order (Atur Tanggal Pengambilan Pesanan)",
            "Voucher Promo & Diskon Happy Hour",
            "Parkir Pesanan (Hold Order) & Pembayaran DP/Bon",
            "Modul CRM Pelanggan untuk Repeat Order"
        ),
        enableTableMode = false,
        enableOrderTypes = true,
        enableWholesale = false,
        enablePreOrder = true,
        enableMembership = false,
        enableReservation = false,
        enableSupplier = false,
        enableCrm = true
    )
)

@Composable
fun SetupWizardScreen(
    settingsManager: SettingsManager,
    viewModel: PosViewModel,
    onSetupComplete: (UserEntity) -> Unit
) {
    val context = LocalContext.current

    var storeName by remember { mutableStateOf(settingsManager.storeName ?: "") }
    var storeAddress by remember { mutableStateOf(settingsManager.storeAddress ?: "") }
    var selectedBusinessType by remember { mutableStateOf("RETAIL") }
    var applyRecommendedModules by remember { mutableStateOf(true) }

    var ownerName by remember { mutableStateOf("") }
    var ownerPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    val activePreset = remember(selectedBusinessType) {
        businessTypePresets.find { it.key == selectedBusinessType } ?: businessTypePresets.first()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.RocketLaunch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Selamat Datang di ZoKas",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Lengkapi profil usaha Anda agar sistem menyesuaikan fitur secara otomatis.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // STEP 1: IDENTITAS TOKO
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "1. Identitas Toko / Usaha",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("Nama Toko / Usaha *") },
                    placeholder = { Text("Contoh: Toko Berkah Jaya") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = storeAddress,
                    onValueChange = { storeAddress = it },
                    label = { Text("Alamat Singkat (Opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // STEP 2: PILIH JENIS USAHA & REKOMENDASI MODUL DINAMIS
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "2. Pilih Jenis Usaha Anda",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Rekomendasi modul akan menyesuaikan otomatis dengan kategori usaha yang Anda pilih:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                businessTypePresets.forEach { preset ->
                    val isSelected = preset.key == selectedBusinessType
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedBusinessType = preset.key },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else
                            MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = preset.icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = preset.subtitle,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedBusinessType = preset.key },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // KOTAK REKOMENDASI MODUL DINAMIS SESUAI JENIS USAHA TERPILIH
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Rekomendasi Modul untuk ${activePreset.key}:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        activePreset.recommendedModules.forEach { modDesc ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = modDesc,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { applyRecommendedModules = !applyRecommendedModules },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = applyRecommendedModules,
                                onCheckedChange = { applyRecommendedModules = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = "Aktifkan otomatis rekomendasi modul ${activePreset.key}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // STEP 3: AKUN PEMILIK (OWNER)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "3. Akun Pemilik Toko (Owner)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = { Text("Nama Pemilik *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = ownerPin,
                        onValueChange = { raw -> if (raw.length <= 6 && raw.all { it.isDigit() }) ownerPin = raw },
                        label = { Text("PIN (4-6 Angka) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { raw -> if (raw.length <= 6 && raw.all { it.isDigit() }) confirmPin = raw },
                        label = { Text("Ulangi PIN *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Button(
            onClick = {
                if (storeName.isBlank()) {
                    Toast.makeText(context, "Nama toko wajib diisi!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (ownerName.isBlank()) {
                    Toast.makeText(context, "Nama pemilik wajib diisi!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (ownerPin.length < 4) {
                    Toast.makeText(context, "PIN minimal 4 digit angka!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (ownerPin != confirmPin) {
                    Toast.makeText(context, "Konfirmasi PIN tidak cocok!", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                settingsManager.storeName = storeName.trim()
                settingsManager.storeAddress = storeAddress.trim()
                settingsManager.businessType = activePreset.key

                if (applyRecommendedModules) {
                    settingsManager.isTableModeEnabled = activePreset.enableTableMode
                    settingsManager.enableOrderTypes = activePreset.enableOrderTypes
                    settingsManager.enableWholesale = activePreset.enableWholesale
                    settingsManager.enablePreOrder = activePreset.enablePreOrder
                    settingsManager.enableMembership = activePreset.enableMembership
                    settingsManager.enableReservation = activePreset.enableReservation
                    settingsManager.enableSupplier = activePreset.enableSupplier
                    settingsManager.enableCrmModule = activePreset.enableCrm
                } else {
                    settingsManager.isTableModeEnabled = activePreset.key == "KULINER"
                }

                // Default modul HR dimatikan di awal agar notifikasi absensi tidak muncul sebelum diaktifkan
                settingsManager.enableHrModule = false
                settingsManager.isSetupCompleted = true

                val adminEntity = UserEntity(
                    id = 1L,
                    name = ownerName.trim(),
                    pin = hashPin(ownerPin),
                    role = "OWNER",
                    totalCommission = 0L,
                    branchId = 1L
                )
                viewModel.addUser(ownerName.trim(), ownerPin, "OWNER", 0L, adminEntity)
                onSetupComplete(adminEntity)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "SIMPAN & MULAI GUNAKAN ZOKAS",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
