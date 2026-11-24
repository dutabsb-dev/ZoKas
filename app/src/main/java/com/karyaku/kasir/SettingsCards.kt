package com.karyaku.kasir

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.karyaku.kasir.data.local.UserEntity
import java.io.File
import java.io.FileOutputStream

import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

// ====================================================================
// FIX #6 — TEMA GELAP ADAPTIF
// Semua warna keras (EmeraldGreen, AmberAccent, ErrorRed, SlateGrey)
// diganti ke MaterialTheme.colorScheme.* sesuai peran:
//   - Aksi utama (SIMPAN, BACKUP, pilih printer) → primary/onPrimary
//   - Aksi sekunder (RESTORE, diskon) → tertiary/onTertiary
//   - Aksi bahaya (reset, hapus) → error/onError
//   - Ikon non-aksi → onSurfaceVariant atau primary/tertiary sebagai aksen
//   - Divider → outlineVariant
//   - Teks sekunder → onSurfaceVariant
//
// FIX #1 & #5 (SESI 2b) — KONSOLIDASI METODE PEMBAYARAN & NONAKTIFKAN VA:
//   - SettingsQrisCard & SettingsBankTransferCard dihapus sebagai card terpisah
//   - Seluruh konfigurasi QRIS Statis & Rekening Transfer Bank Manual disatukan
//     ke dalam PaymentMethodManagementDialog (muncul inline saat dicentang)
//   - Virtual Account (enableTransferAutoDetect) disembunyikan dan dinonaktifkan
// ====================================================================

@Composable
fun SettingsSectionLeadingIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(tint.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

// --- 1. CARD TAMPILAN, 50 TEMA INTERAKTIF & BAHASA ---
@Composable
fun SettingsAppearanceCard(
    isDarkMode: Boolean, onToggleDarkMode: () -> Unit,
    uiScale: Float, onChangeUiScale: (Float) -> Unit,
    language: String, onToggleLanguage: () -> Unit
) {
    val lang = language
    val context = LocalContext.current
    ZokasThemeManager.ensureLoaded(context)
    val currentThemeId = ZokasThemeManager.selectedThemeId
    val activePreset = remember(currentThemeId) { ZokasThemeManager.getActivePreset(context) }
    var showThemeGalleryDialog by remember { mutableStateOf(false) }
    var selectedThemeCategory by remember { mutableStateOf("SEMUA") }
    var themeSearchQuery by remember { mutableStateOf("") }

    if (showThemeGalleryDialog) {
        val categories = remember {
            listOf("SEMUA") + ZOKAS_50_THEMES.map { it.category }.distinct()
        }
        val filteredThemes = remember(selectedThemeCategory, themeSearchQuery) {
            ZOKAS_50_THEMES.filter { preset ->
                val matchCat = selectedThemeCategory == "SEMUA" || preset.category == selectedThemeCategory
                val matchQuery = themeSearchQuery.isBlank() ||
                    preset.name.contains(themeSearchQuery, ignoreCase = true) ||
                    preset.category.contains(themeSearchQuery, ignoreCase = true)
                matchCat && matchQuery
            }
        }

        AlertDialog(
            onDismissRequest = { showThemeGalleryDialog = false },
            title = {
                Column {
                    Text(zokasText("Galeri 50 Tema Interaktif", "50 Interactive Themes Gallery"), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        zokasText("Tema aktif: ${activePreset.name}", "Active theme: ${activePreset.name}"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = themeSearchQuery,
                        onValueChange = { themeSearchQuery = it },
                        placeholder = { Text(zokasText("Cari nama tema atau warna...", "Search theme name or color..."), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (themeSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { themeSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = zokasText("Hapus", "Delete"), modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedThemeCategory == cat,
                                onClick = { selectedThemeCategory = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredThemes.forEach { preset ->
                            val isSelected = preset.id == currentThemeId
                            val pColor = Color(preset.primaryHex)
                            val sColor = Color(preset.surfaceHex)
                            val gStart = Color(preset.gradientStartHex)
                            val gEnd = Color(preset.gradientEndHex)
                            val previewTextColor = if (sColor.luminance() > 0.42f) Color(0xFF0F172A) else Color(0xFFF8FAFC)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        ZokasThemeManager.selectTheme(context, preset.id)
                                        if (preset.id != "default_emerald" && preset.isDark != isDarkMode) {
                                            onToggleDarkMode()
                                        } else if (preset.id == "default_emerald" && isDarkMode) {
                                            onToggleDarkMode()
                                        }
                                    },
                                color = sColor.copy(alpha = preset.glassAlpha),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) pColor else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Brush.linearGradient(listOf(gStart, gEnd)))
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = preset.name,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = previewTextColor
                                                )
                                                Text(
                                                    text = "${preset.category} • Transparansi ${(preset.glassAlpha * 100).toInt()}%",
                                                    fontSize = 10.sp,
                                                    color = previewTextColor.copy(alpha = 0.78f)
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = zokasText("Aktif", "Active"),
                                                tint = pColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Brush.horizontalGradient(listOf(gStart, gEnd)))
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (currentThemeId != "default_emerald") {
                        OutlinedButton(
                            onClick = {
                                ZokasThemeManager.selectTheme(context, "default_emerald")
                                if (isDarkMode) onToggleDarkMode()
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(zokasText("Reset Default", "Reset Default"), fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = { showThemeGalleryDialog = false },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(zokasText("Selesai", "Done"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            // PILIHAN 50 TEMA INTERAKTIF (GRADASI & TRANSPARANSI)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showThemeGalleryDialog = true }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(activePreset.gradientStartHex),
                                        Color(activePreset.gradientEndHex)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = zokasText("Tema Aplikasi", "App Theme"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = activePreset.name,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                OutlinedButton(
                    onClick = { showThemeGalleryDialog = true },
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(zokasText("Pilih Tema", "Choose Theme"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(4.dp))
                SettingsMenuHelpIconButton(menuKey = "appearance")
            }



            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    LanguageManager.tr("mode_gelap", lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = {
                        if (currentThemeId != "default_emerald") {
                            ZokasThemeManager.selectTheme(context, "default_emerald")
                        }
                        onToggleDarkMode()
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Column {
                Text(
                    LanguageManager.tr("ukuran_font", lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = uiScale,
                    onValueChange = { onChangeUiScale(it) },
                    valueRange = 0.8f..1.5f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    "${LanguageManager.tr("skala", lang)}: ${(uiScale * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    LanguageManager.tr("bahasa", lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = { ZokasLocaleManager.toggleLanguage(context); onToggleLanguage() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(if (!ZokasLocaleManager.isEnglish()) "Indonesia" else "English")
                }
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
    onPhotoSettingsClick: () -> Unit = {},
    onManageStaffClick: () -> Unit,
    onShiftHistoryClick: () -> Unit,
    onRegisterBiometricClick: () -> Unit,
    language: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    val lang = language
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    SettingsSectionLeadingIcon(Icons.Default.ManageAccounts, MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            LanguageManager.tr("manajemen_akun", lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            currentUser.name,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsMenuHelpIconButton(menuKey = "account")
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        "${LanguageManager.tr("login_sebagai", lang)}: ${currentUser.name} (${currentUser.role})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onChangePinClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(LanguageManager.tr("ubah_pin", lang), fontSize = 12.sp)
                        }
                        if (currentUser.role == "OWNER") {
                            Button(
                                onClick = onManageStaffClick,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(LanguageManager.tr("kelola_karyawan", lang), fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onPhotoSettingsClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(zokasText("Foto Profil Akun", "Account Profile Photo"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onRegisterBiometricClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentUser.biometricKey.isNotBlank())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.secondary,
                            contentColor = if (currentUser.biometricKey.isNotBlank())
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSecondary
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (currentUser.biometricKey.isNotBlank()) zokasText("Biometrik Terdaftar (Ubah)", "Biometrics Registered (Change)") else zokasText("Daftarkan Sidik Jari/Wajah", "Register Fingerprint/Face"),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onShiftHistoryClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(LanguageManager.tr("riwayat_shift", lang), fontSize = 12.sp)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                zokasText("Login Sidik Jari (Login Screen)", "Fingerprint Login (Login Screen)"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                zokasText("Aktifkan fitur login cepat di halaman awal.", "Enable quick sign-in on the login screen."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (activity == null) {
                                        Toast.makeText(context, zokasText("ERROR: Activity tidak ditemukan.", "ERROR: Activity not found."), Toast.LENGTH_LONG).show()
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
                                                    Toast.makeText(context, zokasText("Sidik jari berfungsi.", "Fingerprint works."), Toast.LENGTH_SHORT).show()
                                                }

                                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                    super.onAuthenticationError(errorCode, errString)
                                                    onBiometricToggle(false)
                                                    Toast.makeText(context, zokasText("Gagal: $errString", "Failed: $errString"), Toast.LENGTH_LONG).show()
                                                }
                                            })
                                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                            .setTitle(zokasText("Aktivasi Sidik Jari", "Fingerprint Activation"))
                                            .setSubtitle(zokasText("Sentuh sensor untuk mengonfirmasi", "Touch sensor to confirm"))
                                            .setNegativeButtonText(zokasText("Batal", "Cancel"))
                                            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                                            .build()
                                        biometricPrompt.authenticate(promptInfo)
                                    } else {
                                        Toast.makeText(context, zokasText("HP belum mendaftarkan sidik jari.", "No fingerprint enrolled on this device."), Toast.LENGTH_LONG).show()
                                        onBiometricToggle(false)
                                    }
                                } else {
                                    onBiometricToggle(false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
                }
            }
        }
    }
}

// --- 3. CARD TIPE USAHA ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBusinessTypeCard(
    currentBusinessType: String,
    onBusinessTypeChange: (String) -> Unit,
    language: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val lang = language

    val businessTypes = listOf(
        "KULINER" to translateBusinessTypeLabel("KULINER", lang),
        "RETAIL" to translateBusinessTypeLabel("RETAIL", lang),
        "JASA" to translateBusinessTypeLabel("JASA", lang),
        "MUSIMAN" to translateBusinessTypeLabel("MUSIMAN", lang)
    )
    val currentLabel = businessTypes.find { it.first == currentBusinessType }?.second ?: currentBusinessType

    if (showConfirmDialog != null) {
        val newType = showConfirmDialog!!
        val newLabel = businessTypes.find { it.first == newType }?.second ?: newType
        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            },
            title = {
                Text(
                    if (lang == "in") "Ganti Tipe Usaha?" else "Change Business Type?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        if (lang == "in") "Tipe usaha akan diubah menjadi:" else "Business type will be changed to:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(newLabel, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (lang == "in") "Perubahan yang akan terjadi:" else "Changes that will occur:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (newType == "KULINER") {
                        Text(
                            if (lang == "in") "• Tab \"Dapur\" (KDS) akan MUNCUL di bottom nav" else "• \"Kitchen\" (KDS) tab will APPEAR in bottom nav",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (lang == "in") "• Menu \"Gabung Meja\" akan muncul di dropdown kasir" else "• \"Merge Table\" menu will appear in cashier dropdown",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (lang == "in") zokasText("• Mode Meja otomatis DIAKTIFKAN", "• Table Mode automatically ENABLED") else "• Table Mode automatically ENABLED",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            if (lang == "in") "• Tab \"Dapur\" (KDS) akan DISEMBUNYIKAN" else "• \"Kitchen\" (KDS) tab will be HIDDEN",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (lang == "in") "• Menu \"Gabung Meja\" akan hilang dari dropdown" else "• \"Merge Table\" menu will disappear from dropdown",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (lang == "in") zokasText("• Mode Meja otomatis DINONAKTIFKAN", "• Table Mode automatically DISABLED") else "• Table Mode automatically DISABLED",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (lang == "in") zokasText("Catatan: Data transaksi, produk, pelanggan, dan struk TIDAK terpengaruh.", "Note: Transaction data, products, customers, and receipts are NOT affected.")
                        else "Note: Transaction data, products, customers, and receipts are NOT affected.",
                        fontSize = 10.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onBusinessTypeChange(newType)
                        showConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        if (lang == "in") zokasText("GANTI", "CHANGE") else "CHANGE",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = null }) {
                    Text(if (lang == "in") zokasText("Batal", "Cancel") else "Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    SettingsSectionLeadingIcon(Icons.Default.BusinessCenter, MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            LanguageManager.tr("tipe_usaha", lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            currentLabel,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsMenuHelpIconButton(menuKey = "business_type")
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        LanguageManager.tr("tipe_usaha_desc", lang),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Box {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.Store,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                currentLabel,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            businessTypes.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (currentBusinessType == key) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            } else {
                                                Spacer(modifier = Modifier.width(24.dp))
                                            }
                                            Text(
                                                label,
                                                fontSize = 13.sp,
                                                fontWeight = if (currentBusinessType == key) FontWeight.Bold else FontWeight.Normal,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    onClick = {
                                        dropdownExpanded = false
                                        if (currentBusinessType != key) {
                                            showConfirmDialog = key
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

// --- 4. CARD MODUL TOKO (DENGAN SAKLAR HR & CRM) ---
@Composable
fun SettingsModuleCard(
    isTableModeEnabled: Boolean, onTableModeToggle: (Boolean) -> Unit,
    enableMultiCurrency: Boolean, onMultiCurrencyToggle: (Boolean) -> Unit,
    exchangeRateUsd: String, onExchangeRateChange: (String) -> Unit,
    onRawMaterialClick: () -> Unit,
    enableOrderTypes: Boolean, onOrderTypesToggle: (Boolean) -> Unit,
    enableWholesale: Boolean, onWholesaleToggle: (Boolean) -> Unit,
    enablePreOrder: Boolean, onPreOrderToggle: (Boolean) -> Unit,
    enableBomModule: Boolean = false, onBomModuleToggle: (Boolean) -> Unit = {},
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
    enableHrModule: Boolean, onHrModuleToggle: (Boolean) -> Unit,
    enableCrmModule: Boolean, onCrmModuleToggle: (Boolean) -> Unit,
    isOwner: Boolean = false,
    currentBranchName: String = "-",
    totalBranches: Int = 1,
    onManageBranchesClick: () -> Unit = {},
    onTransferStockClick: () -> Unit = {},
    hhStart: String = "17:00",
    onHhStartChange: (String) -> Unit = {},
    hhEnd: String = "19:00",
    onHhEndChange: (String) -> Unit = {},
    hhDiscount: String = "0",
    onHhDiscountChange: (String) -> Unit = {},
    maxDiscountCap: String = "0",
    onMaxDiscountCapChange: (String) -> Unit = {},
    language: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    val lang = language

    val switchColors = SwitchDefaults.colors(
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    SettingsSectionLeadingIcon(Icons.Default.Widgets, MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            LanguageManager.tr("modul_toko", lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            LanguageManager.tr("modul_toko_desc", lang),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsMenuHelpIconButton(menuKey = "modules")
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // MULTI-CABANG DI DALAM MODUL TOKO
                    if (isOwner) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        zokasText("Multi-Cabang ($totalBranches Cabang)", "Multi-Branch ($totalBranches Branches)"),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        zokasText("Cabang aktif: $currentBranchName", "Active branch: $currentBranchName"),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onManageBranchesClick,
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(zokasText("Kelola Cabang", "Manage Branches"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = onTransferStockClick,
                                    enabled = totalBranches > 1,
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(zokasText("Transfer Stok", "Stock Transfer"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }

                    // HAPPY HOUR PROMO DI DALAM MODUL TOKO
                    var showHappyHourInline by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showHappyHourInline = !showHappyHourInline }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocalOffer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    zokasText("Happy Hour Promo ($hhStart - $hhEnd)", "Happy Hour Promo ($hhStart - $hhEnd)"),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    zokasText("Diskon otomatis pada jam tertentu & batas maksimal diskon.", "Automatic discount during set hours & max discount cap."),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (showHappyHourInline) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AnimatedVisibility(visible = showHappyHourInline) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = hhStart,
                                        onValueChange = onHhStartChange,
                                        label = { Text(zokasText("Mulai", "Start"), fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    OutlinedTextField(
                                        value = hhEnd,
                                        onValueChange = onHhEndChange,
                                        label = { Text(zokasText("Selesai", "Done"), fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = hhDiscount,
                                        onValueChange = onHhDiscountChange,
                                        label = { Text(zokasText("Diskon (%)", "Discount (%)"), fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    OutlinedTextField(
                                        value = maxDiscountCap,
                                        onValueChange = onMaxDiscountCapChange,
                                        label = { Text(zokasText("Maks. Diskon (Rp)", "Max Discount (Rp)"), fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Modul HR
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onHrModuleToggle(!enableHrModule) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Badge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                zokasText("Modul Kepegawaian (HR)", "Human Resources Module (HR)"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                zokasText("Aktifkan jika punya karyawan (Absensi & Gaji).", "Enable if you have staff (Attendance & Payroll)."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enableHrModule, onCheckedChange = onHrModuleToggle, colors = switchColors)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Modul CRM
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onCrmModuleToggle(!enableCrmModule) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                zokasText("Modul CRM (Pelanggan)", "CRM Module (Customers)"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                zokasText("Fitur member, poin, dan data pelanggan setia.", "Membership, points, and loyal customer data."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enableCrmModule, onCheckedChange = onCrmModuleToggle, colors = switchColors)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Mode Meja
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onTableModeToggle(!isTableModeEnabled) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                zokasText("Mode Meja Restoran", "Restaurant Table Mode"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                zokasText("Tampilkan pilihan meja di halaman kasir.", "Show table selection on cashier screen."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = isTableModeEnabled, onCheckedChange = onTableModeToggle, colors = switchColors)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Multi Currency
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onMultiCurrencyToggle(!enableMultiCurrency) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Payments,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                zokasText("Multi-Mata Uang (USD)", "Multi-Currency (USD)"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                zokasText("Estimasi harga dalam Dolar AS.", "Estimated price in US Dollars."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enableMultiCurrency, onCheckedChange = onMultiCurrencyToggle, colors = switchColors)
                    }

                    if (enableMultiCurrency) {
                        OutlinedTextField(
                            value = exchangeRateUsd,
                            onValueChange = onExchangeRateChange,
                            label = { Text(LanguageManager.tr("kurs_usd", lang), fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Tipe Pesanan
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOrderTypesToggle(!enableOrderTypes) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocalDining,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                zokasText("Tipe Pesanan (Order Type)", "Order Type"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                zokasText("Dine-In, Take-Away, Delivery, Pick-Up.", "Dine-In, Take-Away, Delivery, Pick-Up."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enableOrderTypes, onCheckedChange = onOrderTypesToggle, colors = switchColors)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Harga Grosir
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onWholesaleToggle(!enableWholesale) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.MonetizationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                zokasText("Harga Grosir (Retail)", "Wholesale Pricing (Retail)"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                zokasText("Harga khusus untuk pembelian jumlah banyak.", "Special pricing for bulk purchases."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enableWholesale, onCheckedChange = onWholesaleToggle, colors = switchColors)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Pre-Order
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPreOrderToggle(!enablePreOrder) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Event,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                zokasText("Pre-Order (Pesanan Musiman)", "Pre-Order (Seasonal Orders)"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                zokasText("Atur tanggal pengambilan pesanan pelanggan.", "Set customer order pickup dates."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enablePreOrder, onCheckedChange = onPreOrderToggle, colors = switchColors)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Reservasi
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onReservationToggle(!enableReservation) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                zokasText("Reservasi / Booking", "Reservation / Booking"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                zokasText("Jadwal meja atau terapis (Salon/Klinik).", "Table or therapist schedule (Salon/Clinic)."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enableReservation, onCheckedChange = onReservationToggle, colors = switchColors)
                    }

                    if (enableReservation) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onManageReservationClick() }.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    zokasText("Kelola Jadwal Reservasi", "Manage Reservations"),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    zokasText("Lihat & buat jadwal pelanggan.", "View & create customer bookings."),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Membership
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onMembershipToggle(!enableMembership) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CardMembership,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                zokasText("Paket Langganan (Membership)", "Membership Packages"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                zokasText("Jual paket kuota (cth: Facial 5x).", "Sell quota packages (e.g., 5x Facial)."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enableMembership, onCheckedChange = onMembershipToggle, colors = switchColors)
                    }

                    if (enableMembership) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onManageMembershipClick() }.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    zokasText("Kelola Paket Langganan", "Manage Membership Packages"),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    zokasText("Buat dan atur daftar paket.", "Create and manage package list."),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Supplier
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSupplierToggle(!enableSupplier) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                zokasText("Supplier & Pembelian (PO)", "Suppliers & Purchase Orders (PO)"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                zokasText("Catat barang masuk dari supplier.", "Record incoming goods from suppliers."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enableSupplier, onCheckedChange = onSupplierToggle, colors = switchColors)
                    }

                    if (enableSupplier) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onManageSupplierClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    zokasText("Kelola Supplier", "Manage Suppliers"),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    zokasText("Daftar pemasok barang.", "Vendor & supplier directory."),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onCreatePOClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AddShoppingCart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    zokasText("Buat Pembelian (PO)", "Create Purchase Order (PO)"),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    zokasText("Catat barang masuk & tambah stok.", "Record incoming stock & add inventory."),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Keuangan
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onFinanceToggle(!enableFinance) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                zokasText("Modul Keuangan (Finance)", "Finance Module"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                zokasText("Kelola hutang, piutang & pengeluaran.", "Manage receivables, payables & expenses."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enableFinance, onCheckedChange = onFinanceToggle, colors = switchColors)
                    }

                    if (enableFinance) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onManageReceivableClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    zokasText("Buku Piutang (Bon)", "Receivables Ledger (Credit)"),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    zokasText("Daftar bon pelanggan yang belum lunas.", "Unpaid customer credit list."),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onManagePayableClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.RequestQuote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    zokasText("Buku Hutang (Supplier)", "Payables Ledger (Suppliers)"),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    zokasText("Daftar PO yang belum dibayar.", "Unpaid purchase orders list."),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onManageExpenseClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MoneyOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    zokasText("Pengeluaran Operasional", "Operating Expenses"),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    zokasText("Catat biaya gaji, listrik, sewa, dll.", "Record salaries, utilities, rent, etc."),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    BomModuleSwitchRow(
                        enabled = enableBomModule,
                        onToggle = onBomModuleToggle
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Bahan Baku
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onRawMaterialClick() }.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Inventory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                zokasText("Kelola Bahan Baku", "Manage Raw Materials"),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                zokasText("Atur stok bahan mentah & resep (BOM).", "Manage raw material stock & recipes (BOM)."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                }
            }
        }
    }
}

// --- 5. CARD KONEKSI PRINTER ---
@Composable
fun SettingsPrinterCard(
    savedPrinterMac: String?,
    savedKitchenMac: String?,
    onPrinterClick: () -> Unit,
    onKitchenClick: () -> Unit,
    language: String
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    val lang = language

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    SettingsSectionLeadingIcon(Icons.Default.Print, MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            LanguageManager.tr("printer_kasir", lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (savedPrinterMac != null) LanguageManager.tr("terhubung", lang) else LanguageManager.tr("belum_printer", lang),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsMenuHelpIconButton(menuKey = "printer")
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        LanguageManager.tr("printer_kasir", lang),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (savedPrinterMac != null) "${LanguageManager.tr("terhubung", lang)}: $savedPrinterMac" else LanguageManager.tr("belum_printer", lang),
                        fontSize = 12.sp,
                        color = if (savedPrinterMac != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onPrinterClick,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) { Text(LanguageManager.tr("pilih_printer", lang)) }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        LanguageManager.tr("printer_dapur", lang),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (savedKitchenMac != null) "${LanguageManager.tr("terhubung", lang)}: $savedKitchenMac" else LanguageManager.tr("belum_printer_dapur", lang),
                        fontSize = 12.sp,
                        color = if (savedKitchenMac != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onKitchenClick,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) { Text(LanguageManager.tr("pilih_printer_dapur", lang)) }

                    Spacer(modifier = Modifier.height(10.dp))
                    PrinterUpgradeSettingsSection(
                        savedPrinterMac = savedPrinterMac,
                        storeName = settingsStoreNameOrDefault(context),
                        logoPath = SettingsManager(context).storeLogoPath
                    )
                }
                }
            }
        }
    }
}

// --- 6. CARD PROFIL TOKO (DENGAN PRESET PAJAK INDONESIA PB1/PPN & OPSI PAJAK INKLUSIF) ---
@Composable
fun SettingsStoreProfileCard(
    storeName: String, onStoreNameChange: (String) -> Unit,
    storeAddress: String, onStoreAddressChange: (String) -> Unit,
    storePhone: String, onStorePhoneChange: (String) -> Unit,
    storeFooter: String, onStoreFooterChange: (String) -> Unit,
    taxPercent: String, onTaxPercentChange: (String) -> Unit,
    serviceChargePercent: String, onServiceChargeChange: (String) -> Unit,
    logoPath: String?, onLogoChange: (String) -> Unit,
    language: String,
    isTaxInclusive: Boolean = false,
    onTaxInclusiveChange: (Boolean) -> Unit = {},
    cashRoundingUnit: Int = 0,
    onCashRoundingChange: (Int) -> Unit = {},
    cfdThankYouMessage: String = "Terima Kasih atas pembeliannya 🙏",
    onCfdThankYouMessageChange: (String) -> Unit = {}
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
                Toast.makeText(context, zokasText("Gagal simpan logo: ${e.message}", "Failed to save logo: ${e.message}"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    SettingsSectionLeadingIcon(Icons.Default.Store, MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            LanguageManager.tr("profil_toko", lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            storeName.ifBlank { LanguageManager.tr("belum_diatur", lang) },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsMenuHelpIconButton(menuKey = "store_profile")
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { logoPickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!logoPath.isNullOrEmpty() && File(logoPath).exists()) {
                                AsyncImage(
                                    model = File(logoPath),
                                    contentDescription = "Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    Icons.Default.Store,
                                    contentDescription = "Upload Logo",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                zokasText("Logo Toko", "Store Logo"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                zokasText("Ketuk gambar untuk mengganti logo di struk & aplikasi.", "Tap image to change logo on receipt & app."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = storeName,
                        onValueChange = onStoreNameChange,
                        label = { Text(LanguageManager.tr("nama_toko", lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = storeAddress,
                        onValueChange = onStoreAddressChange,
                        label = { Text(LanguageManager.tr("alamat_toko", lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        shape = RoundedCornerShape(6.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = storePhone,
                        onValueChange = onStorePhoneChange,
                        label = { Text(LanguageManager.tr("no_telp", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = storeFooter,
                        onValueChange = onStoreFooterChange,
                        label = { Text(LanguageManager.tr("pesan_struk", lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        shape = RoundedCornerShape(6.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = cfdThankYouMessage,
                        onValueChange = onCfdThankYouMessageChange,
                        label = { Text(zokasText("Pesan Layar Pelanggan (CFD) Selesai Bayar", "Customer Display (CFD) Completed Message")) },
                        placeholder = { Text(zokasText("Contoh: Terima Kasih atas pembeliannya 🙏", "Example: Thank you for your purchase 🙏")) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        shape = RoundedCornerShape(6.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        zokasText("Pengaturan Pajak & Layanan (Standar Indonesia)", "Tax & Service Settings"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        zokasText("Pilih cepat tarif pajak sesuai jenis usaha Anda di Indonesia:", "Quickly select a tax rate preset for your business:"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val taxPresets = listOf(
                            "0" to "0% (Non-PKP)",
                            "10" to "10% (PB1 Resto)",
                            "11" to "11% (PPN)",
                            "12" to "12% (PPN)"
                        )
                        taxPresets.forEach { (rateVal, rateLabel) ->
                            val isSelected = taxPercent.trim() == rateVal || taxPercent.trim() == "$rateVal.0"
                            OutlinedButton(
                                onClick = { onTaxPercentChange(rateVal) },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(
                                    rateLabel,
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = taxPercent,
                            onValueChange = onTaxPercentChange,
                            label = { Text(LanguageManager.tr("pajak", lang), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp)
                        )
                        OutlinedTextField(
                            value = serviceChargePercent,
                            onValueChange = onServiceChargeChange,
                            label = { Text("Service Charge (%)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onTaxInclusiveChange(!isTaxInclusive) },
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    zokasText("Harga Produk Sudah Termasuk Pajak (Inclusive)", "Prices Already Include Tax (Inclusive)"),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (isTaxInclusive)
                                        zokasText("Aktif: Pajak dihitung di dalam harga menu (tidak menambah total tagihan pembeli).", "Active: Tax is included in item price (does not add to buyer total).")
                                    else
                                        zokasText("Nonaktif (Eksklusif): Pajak ditambahkan di atas subtotal belanja.", "Inactive (Exclusive): Tax is added on top of subtotal."),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isTaxInclusive,
                                onCheckedChange = onTaxInclusiveChange,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        zokasText("Pembulatan Otomatis Pembayaran Tunai (Cash)", "Automatic Cash Rounding"),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        zokasText("Menghindari kembalian receh satuan (misal Rp 43) saat ada pajak/diskon persen:", "Avoid small coin change (e.g., Rp 43) with tax/percentage discounts:"),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val roundingOptions = listOf(
                            0 to zokasText("Tanpa Bulat", "No Rounding"),
                            100 to "Rp 100",
                            500 to "Rp 500"
                        )
                        roundingOptions.forEach { (unitVal, unitLabel) ->
                            val isSelected = cashRoundingUnit == unitVal
                            OutlinedButton(
                                onClick = { onCashRoundingChange(unitVal) },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(
                                    text = unitLabel,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

// --- 7. CARD HAPPY HOUR & DISKON ---
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        LanguageManager.tr("happy_hour", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "$hhStart - $hhEnd",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsMenuHelpIconButton(menuKey = "discount")
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        LanguageManager.tr("happy_hour_desc", lang),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hhStart,
                            onValueChange = onHhStartChange,
                            label = { Text(LanguageManager.tr("mulai", lang)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp)
                        )
                        OutlinedTextField(
                            value = hhEnd,
                            onValueChange = onHhEndChange,
                            label = { Text(LanguageManager.tr("selesai", lang)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = hhDiscount,
                        onValueChange = onHhDiscountChange,
                        label = { Text(LanguageManager.tr("diskon_persen", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = maxDiscountCap,
                        onValueChange = onMaxDiscountCapChange,
                        label = { Text(LanguageManager.tr("max_diskon", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp)
                    )
                }
                }
            }
        }
    }
}

// --- 8. CARD BACKUP & EXCEL ---
@Composable
fun SettingsBackupCard(
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onExportExcelClick: () -> Unit,
    onImportExcelClick: () -> Unit,
    language: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    val lang = language

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    SettingsSectionLeadingIcon(Icons.Default.CloudSync, MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            LanguageManager.tr("data_backup", lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            LanguageManager.tr("cadangkan_data", lang),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsMenuHelpIconButton(
                        menuKey = "backup",
                        onOpenQuickAction = { act ->
                            if (act == "backup_now") onBackupClick()
                            else if (act == "restore_now") onRestoreClick()
                        }
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        LanguageManager.tr("data_backup_desc", lang),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    var showAutoBackupDriveDialog by remember { mutableStateOf(false) }
                    if (showAutoBackupDriveDialog) {
                        ScheduledBackupDriveDialog(onDismiss = { showAutoBackupDriveDialog = false })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onBackupClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) { Text(LanguageManager.tr("backup", lang)) }
                        Button(
                            onClick = onRestoreClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) { Text(LanguageManager.tr("restore", lang)) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showAutoBackupDriveDialog = true },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(zokasText("Jadwal Auto-Backup & Google Drive", "Auto-Backup Schedule & Google Drive"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                }
                }
            }
        }
    }
}

// --- 9. CARD PENGATURAN METODE PEMBAYARAN (TERKONSOLIDASI) ---
@Composable
fun SettingsPaymentMethodCard(
    currentMethods: Set<String>,
    qrisConfigured: Boolean = true,
    bankConfigured: Boolean = true,
    onClick: () -> Unit
) {
    val missingQris = currentMethods.contains("QRIS") && !qrisConfigured
    val missingBank = currentMethods.contains("Bank Transfer") && !bankConfigured
    val hasWarning = missingQris || missingBank

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(
                        if (hasWarning) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasWarning) Icons.Default.Warning else Icons.Default.Payment,
                    contentDescription = null,
                    tint = if (hasWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = zokasText("Metode Pembayaran", "Payment Methods"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val pmContext = LocalContext.current
                val edcActive = remember(pmContext) { EdcConnectionManager.isEdcEnabled(pmContext) }
                val summaryMethods = remember(currentMethods, edcActive) {
                    val base = currentMethods.toMutableList()
                    if (edcActive && !base.contains("EDC")) base.add("EDC")
                    base.joinToString(", ").ifBlank { zokasText("Belum dipilih", "Not selected") }
                }
                Text(
                    text = summaryMethods,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                if (hasWarning) {
                    val warningText = buildList {
                        if (missingQris) add(zokasText("Payload QRIS belum diisi", "QRIS payload not set"))
                        if (missingBank) add(zokasText("Nomor rekening belum diisi", "Account number not set"))
                    }.joinToString(" • ")
                    Text(
                        text = warningText,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            SettingsMenuHelpIconButton(menuKey = "payment_method")
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- 10. DIALOG KELOLA METODE PEMBAYARAN (INLINE QRIS + BANK TRANSFER) ---
@Composable
fun PaymentMethodManagementDialog(
    currentMethods: Set<String>,
    currentQrisPayload: String = "",
    currentEnableQrisAutoDetect: Boolean = false,
    currentBankName: String = "",
    currentBankAccountNumber: String = "",
    currentBankAccountName: String = "",
    onSave: (
        methods: Set<String>,
        qrisPayload: String,
        enableQrisAutoDetect: Boolean,
        bankName: String,
        bankAccountNumber: String,
        bankAccountName: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val availableMethods = listOf("CASH", "QRIS", "Bank Transfer")
    val selectedMethods = remember { mutableStateListOf<String>().apply { addAll(currentMethods) } }

    var qrisPayload by remember { mutableStateOf(currentQrisPayload) }
    var enableQrisAutoDetect by remember { mutableStateOf(currentEnableQrisAutoDetect) }
    var bankName by remember { mutableStateOf(currentBankName) }
    var bankAccountNumber by remember { mutableStateOf(currentBankAccountNumber) }
    var bankAccountName by remember { mutableStateOf(currentBankAccountName) }
    var showQrisTutorial by remember { mutableStateOf(false) }

    var isEdcEnabledState by remember {
        mutableStateOf(EdcConnectionManager.isEdcEnabled(context))
    }
    var edcChannelState by remember {
        mutableStateOf(EdcConnectionManager.getPreferredChannel(context))
    }
    var edcHostState by remember {
        mutableStateOf(EdcConnectionManager.getTcpHost(context))
    }
    var edcPortState by remember {
        mutableStateOf(EdcConnectionManager.getTcpPort(context).toString())
    }
    var edcIntentState by remember {
        mutableStateOf(EdcConnectionManager.getIntentPackageOrUri(context))
    }

    val isQrisSelected = selectedMethods.contains("QRIS")
    val isBankSelected = selectedMethods.contains("Bank Transfer")

    val isQrisValid = !isQrisSelected || qrisPayload.isNotBlank()
    val isBankValid = !isBankSelected || (bankName.isNotBlank() && bankAccountNumber.isNotBlank())
    val canSave = selectedMethods.isNotEmpty() && isQrisValid && isBankValid

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            if (QrisHelper.isValidQris(result.contents)) {
                qrisPayload = result.contents
                Toast.makeText(context, zokasText("QRIS Statis berhasil dipindai!", "Static QRIS scanned successfully!"), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    zokasText("Gagal: QR Code ini bukan QRIS Statis yang valid (Gagal verifikasi CRC16).", "Error: Not a valid Static QRIS code (CRC16 check failed)."),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    if (showQrisTutorial) {
        AlertDialog(
            onDismissRequest = { showQrisTutorial = false },
            icon = {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            },
            title = { Text("Cara Mendapatkan QRIS Statis") },
            text = {
                Column {
                    Text(
                        zokasText("1. Buka aplikasi Bank atau E-Wallet Anda (BCA, Mandiri, GoPay, OVO, dll).", "1. Open your Bank or E-Wallet app (BCA, Mandiri, GoPay, OVO, etc.)."),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        zokasText("2. Cari menu \"QRIS\" atau \"Terima Pembayaran\".", "2. Find the \"QRIS\" or \"Receive Payment\" menu."),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        zokasText("3. Pastikan Anda menggunakan QRIS Statis (bukan QR dinamis yang nominalnya berubah-ubah).", "3. Make sure you use a Static QRIS (not a dynamic QR with changing amounts)."),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        zokasText("4. Screenshot QR tersebut, atau minta file gambar QRIS ke bank Anda.", "4. Screenshot the QR code or request the QRIS image file from your bank."),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        zokasText("5. Gunakan tombol 'Scan QRIS' di aplikasi ini untuk memindai gambar tersebut. Aplikasi akan otomatis menyimpan kode rahasianya.", "5. Use 'Scan QRIS' in this app to scan it. The payload will be saved automatically."),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        zokasText("Catatan: Aplikasi memvalidasi QRIS menggunakan checksum CRC16. Jika QR yang Anda scan adalah QRIS dinamis atau bukan QRIS, aplikasi akan menolak.", "Note: QRIS is validated using CRC16 checksum. Dynamic or invalid QR codes will be rejected."),
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showQrisTutorial = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text(zokasText("Mengerti", "Got It")) }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                zokasText("Metode Pembayaran", "Payment Methods"),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    zokasText("Pilih metode yang diterima di toko & lengkapi pengaturannya:", "Select accepted payment methods & configure details:"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                availableMethods.forEach { method ->
                    val isChecked = selectedMethods.contains(method)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) selectedMethods.remove(method)
                                    else selectedMethods.add(method)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (!selectedMethods.contains(method)) selectedMethods.add(method)
                                    } else {
                                        selectedMethods.remove(method)
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = when (method) {
                                    "CASH" -> zokasText("Tunai (CASH)", "Cash (CASH)")
                                    "QRIS" -> zokasText("QRIS Statis / Dinamis", "QRIS Static / Dynamic")
                                    "Bank Transfer" -> zokasText("Transfer Bank Manual", "Manual Bank Transfer")
                                    else -> method
                                },
                                fontSize = 14.sp,
                                fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // FORM INLINE QRIS
                        AnimatedVisibility(
                            visible = method == "QRIS" && isChecked,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, bottom = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            zokasText("Konfigurasi QRIS", "QRIS Configuration"),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        TextButton(
                                            onClick = { showQrisTutorial = true },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Help,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                zokasText("Tutorial", "Tutorial"),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = qrisPayload,
                                            onValueChange = { qrisPayload = it },
                                            label = { Text(zokasText("Payload QRIS (000201...)", "QRIS Payload (000201...)"), fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f),
                                            maxLines = 3,
                                            isError = qrisPayload.isBlank(),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                val options = ScanOptions().apply {
                                                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                                    setPrompt(zokasText("Scan QRIS Toko", "Scan Store QRIS"))
                                                    setCameraId(0)
                                                    setBeepEnabled(false)
                                                    setOrientationLocked(true)
                                                }
                                                scanLauncher.launch(options)
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(10.dp)
                                                )
                                        ) {
                                            Icon(
                                                Icons.Default.QrCodeScanner,
                                                contentDescription = zokasText("Scan QRIS", "Scan QRIS"),
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }

                                    if (qrisPayload.isBlank()) {
                                        Text(
                                            zokasText("Wajib diisi jika metode QRIS diaktifkan.", "Required when QRIS method is enabled."),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                zokasText("Deteksi Otomatis Notifikasi", "Auto-Detect Notifications"),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                zokasText("Konfirmasi lunas dari notifikasi aplikasi QRIS.", "Confirm payment from QRIS app notifications."),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = enableQrisAutoDetect,
                                            onCheckedChange = { enableQrisAutoDetect = it },
                                            colors = SwitchDefaults.colors(
                                                checkedTrackColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // FORM INLINE TRANSFER BANK MANUAL
                        AnimatedVisibility(
                            visible = method == "Bank Transfer" && isChecked,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, bottom = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                                    Text(
                                        zokasText("Rekening Tujuan Transfer", "Destination Bank Account"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = bankName,
                                        onValueChange = { bankName = it },
                                        label = { Text(zokasText("Nama Bank (cth: BCA / Mandiri)", "Bank Name (e.g., BCA / Mandiri)"), fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        isError = bankName.isBlank(),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = bankAccountNumber,
                                        onValueChange = { bankAccountNumber = it },
                                        label = { Text(zokasText("Nomor Rekening", "Account Number"), fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        isError = bankAccountNumber.isBlank(),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = bankAccountName,
                                        onValueChange = { bankAccountName = it },
                                        label = { Text(zokasText("Atas Nama Rekening", "Account Holder Name"), fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(6.dp)
                                    )

                                    if (bankName.isBlank() || bankAccountNumber.isBlank()) {
                                        Text(
                                            zokasText("Nama bank & nomor rekening wajib diisi jika Transfer Bank aktif.", "Bank name & account number are required when Bank Transfer is active."),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // TOGGLE EDC DI BAWAH TRANSFER BANK MANUAL (DEFAULT OFF)


                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isEdcEnabledState = !isEdcEnabledState }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = zokasText("Pembayaran Mesin EDC (Kartu)", "EDC Card Payment"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEdcEnabledState)
                                zokasText("Aktif: Opsi EDC muncul di kasir", "Active: EDC option appears on cashier screen")
                            else
                                zokasText("Nonaktif: Opsi EDC disembunyikan di kasir", "Inactive: EDC option hidden on cashier screen"),
                            fontSize = 11.sp,
                            color = if (isEdcEnabledState)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isEdcEnabledState,
                        onCheckedChange = { checked -> isEdcEnabledState = checked },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                AnimatedVisibility(
                    visible = isEdcEnabledState,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = zokasText("Jalur Koneksi EDC", "EDC Connection Channel"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                EdcChannel.entries.forEach { ch ->
                                    FilterChip(
                                        selected = edcChannelState == ch,
                                        onClick = { edcChannelState = ch },
                                        label = { Text(ch.label, fontSize = 10.5.sp) }
                                    )
                                }
                            }
                            if (edcChannelState == EdcChannel.TCP_WIFI || edcChannelState == EdcChannel.AUTO) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = edcHostState,
                                        onValueChange = { edcHostState = it },
                                        label = { Text(zokasText("IP EDC", "EDC IP"), fontSize = 11.sp) },
                                        modifier = Modifier.weight(2f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    OutlinedTextField(
                                        value = edcPortState,
                                        onValueChange = { edcPortState = it.filter { c -> c.isDigit() } },
                                        label = { Text(zokasText("Port", "Port"), fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                }
                            }
                            if (edcChannelState == EdcChannel.INTENT || edcChannelState == EdcChannel.AUTO) {
                                OutlinedTextField(
                                    value = edcIntentState,
                                    onValueChange = { edcIntentState = it },
                                    label = { Text(zokasText("Package / URI EDC (Opsional)", "EDC Package / URI (Optional)"), fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(6.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canSave) {
                        EdcConnectionManager.setEdcEnabled(context, isEdcEnabledState)
                        EdcConnectionManager.setPreferredChannel(context, edcChannelState)
                        EdcConnectionManager.setTcpHost(context, edcHostState)
                        EdcConnectionManager.setTcpPort(context, edcPortState.toIntOrNull() ?: 9100)
                        EdcConnectionManager.setIntentPackageOrUri(context, edcIntentState)
                        onSave(
                            selectedMethods.toSet(),
                            qrisPayload.trim(),
                            enableQrisAutoDetect,
                            bankName.trim(),
                            bankAccountNumber.trim(),
                            bankAccountName.trim()
                        )
                    }
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(6.dp)
            ) { Text(zokasText("SIMPAN", "SAVE"), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(zokasText("Batal", "Cancel"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}


// --- 11. CARD PANTAUAN HP OWNER (ZERO-SERVER VIA TELEGRAM BOT MILIK OWNER) ---
@Composable
fun SettingsOwnerMonitorCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExpanded by remember { mutableStateOf(false) }

    val initialCfg = remember { OwnerRemoteMonitorManager.getConfig(context) }
    var isEnabled by remember { mutableStateOf(initialCfg.isEnabled) }
    var botToken by remember { mutableStateOf(initialCfg.botToken) }
    var chatId by remember { mutableStateOf(initialCfg.chatId) }
    var notifyTrx by remember { mutableStateOf(initialCfg.notifyOnEachTransaction) }
    var notifyShift by remember { mutableStateOf(initialCfg.notifyOnCloseShift) }
    var notifyVoid by remember { mutableStateOf(initialCfg.notifyOnVoidRefund) }
    var autoExportOnCloseShift by remember {
        mutableStateOf(MultiDeviceTransactionSyncManager.isAutoExportOnCloseShiftEnabled(context))
    }
    var isBusy by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }

    val ownerEmail = remember { OwnerRemoteMonitorManager.getRegisteredOwnerEmail(context) }

    fun persistMonitorConfig(nextEnabled: Boolean = isEnabled) {
        OwnerRemoteMonitorManager.saveConfig(
            context,
            OwnerMonitorConfig(
                isEnabled = nextEnabled,
                botToken = botToken,
                chatId = chatId,
                notifyOnEachTransaction = notifyTrx,
                notifyOnCloseShift = notifyShift,
                notifyOnVoidRefund = notifyVoid
            )
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    SettingsSectionLeadingIcon(Icons.Default.NotificationsActive, MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = zokasText("Pantauan HP Owner (Tanpa Server)", "Owner Phone Monitor (Serverless)"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEnabled)
                                zokasText("Aktif • Kirim laporan transaksi & tutup kasir ke HP Owner", "Active • Send transaction & closing reports to Owner phone")
                            else
                                zokasText("Nonaktif • Pantau transaksi & tutup shift jarak jauh", "Inactive • Monitor transactions & shift closing remotely"),
                            fontSize = 11.sp,
                            color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
SettingsMenuHelpIconButton(menuKey = "owner_monitor")
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    if (ownerEmail.isNotBlank()) {
                        Text(
                            text = zokasText("Akun Terdaftar: $ownerEmail", "Registered Account: $ownerEmail"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = zokasText("Aktifkan Pantauan Jarak Jauh", "Enable Remote Monitoring"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = zokasText("Tanpa server berbayar developer. Menggunakan Bot Telegram milik Owner sendiri.", "No paid developer server. Uses the Owner's own Telegram Bot."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { checked ->
                                isEnabled = checked
                                persistMonitorConfig(checked)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    var showTelegramGuideDialog by remember { mutableStateOf(false) }

                    fun openTelegramLink(url: String) {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, zokasText("Aplikasi Telegram tidak ditemukan. Buka $url di browser.", "Telegram app not found. Open $url in browser."), Toast.LENGTH_LONG).show()
                        }
                    }

                    if (showTelegramGuideDialog) {
                        AlertDialog(
                            onDismissRequest = { showTelegramGuideDialog = false },
                            icon = {
                                Icon(
                                    Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            title = {
                                Text(
                                    zokasText("Panduan Setup Bot Telegram Owner", "Owner Telegram Bot Setup Guide"),
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        zokasText("Langkah 1 — Buat Bot Baru & Ambil Bot Token:", "Step 1 — Create a New Bot & Get Bot Token:"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        zokasText(
                                            "1. Ketuk tombol 'Buka @BotFather' di bawah.\n" +
                                            "2. Di chat @BotFather, tekan START lalu kirim perintah: /newbot\n" +
                                            "3. Masukkan nama bot (contoh: Kasir Toko Berkah).\n" +
                                            "4. Masukkan username bot berakhiran 'bot' (contoh: berkah_kasir_bot).\n" +
                                            "5. Salin kode HTTP API Token (contoh: 7123456789:AAH...) lalu tempel ke kolom Bot Token di ZoKas.",
                                            "1. Tap 'Open @BotFather in Telegram' below.\n" +
                                            "2. In @BotFather chat, tap START and send: /newbot\n" +
                                            "3. Enter a bot name (e.g., Kasir Toko Berkah).\n" +
                                            "4. Enter a bot username ending in 'bot' (e.g., berkah_kasir_bot).\n" +
                                            "5. Copy the HTTP API Token (e.g., 7123456789:AAH...) and paste it into Bot Token in ZoKas."
                                        ),
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    OutlinedButton(
                                        onClick = { openTelegramLink("https://t.me/BotFather") },
                                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(zokasText("Buka @BotFather di Telegram", "Open @BotFather in Telegram"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                    Text(
                                        zokasText("Langkah 2 — Ambil Chat ID HP Owner:", "Step 2 — Get Owner Phone Chat ID:"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        zokasText(
                                            "1. Ketuk tombol 'Buka @userinfobot' di bawah.\n" +
                                            "2. Tekan START di chat bot tersebut.\n" +
                                            "3. Salin angka pada bagian 'Id' (contoh: 987654321) lalu tempel ke kolom Chat ID Penerima.",
                                            "1. Tap 'Open @userinfobot in Telegram' below.\n" +
                                            "2. Tap START in that bot chat.\n" +
                                            "3. Copy the number under 'Id' (e.g., 987654321) and paste it into Owner Phone Chat ID."
                                        ),
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    OutlinedButton(
                                        onClick = { openTelegramLink("https://t.me/userinfobot") },
                                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(zokasText("Buka @userinfobot di Telegram", "Open @userinfobot in Telegram"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                    Text(
                                        zokasText("Langkah 3 — Aktivasi Chat Bot (Wajib):", "Step 3 — Activate Bot Chat (Required):"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        zokasText(
                                            "Buka link username bot yang baru Anda buat di Langkah 1, lalu tekan tombol START sekali agar bot diizinkan mengirim pesan ke HP Owner. Setelah itu kembali ke ZoKas dan tekan 'Tes Kirim'.",
                                            "Open the bot username link you created in Step 1 and tap START once so it can message the Owner phone. Then return to ZoKas and tap 'Test Send'."
                                        ),
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { showTelegramGuideDialog = false },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(zokasText("MENGERTI", "GOT IT"), fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }

                    OutlinedButton(
                        onClick = { showTelegramGuideDialog = true },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            zokasText("Tutorial Lengkap Cara Ambil Token & Chat ID", "Full Guide: Get Bot Token & Chat ID"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = botToken,
                        onValueChange = { botToken = it },
                        label = { Text(zokasText("Bot Token Telegram *", "Telegram Bot Token *"), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text("123456789:ABCdef...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { openTelegramLink("https://t.me/BotFather") },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(zokasText("Buka @BotFather (Buat Bot & Ambil Token)", "Open @BotFather (Create Bot & Get Token)"), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = chatId,
                        onValueChange = { chatId = it },
                        label = { Text(zokasText("Chat ID HP Owner *", "Owner Phone Chat ID *"), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text(zokasText("Contoh: 987654321", "Example: 987654321")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { openTelegramLink("https://t.me/userinfobot") },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(zokasText("Buka @userinfobot (Cek Chat ID Owner)", "Open @userinfobot (Check Owner Chat ID)"), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { notifyTrx = !notifyTrx },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = notifyTrx,
                            onCheckedChange = { notifyTrx = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text(zokasText("Kirim notifikasi setiap transaksi selesai", "Send alert on every completed transaction"), fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { notifyShift = !notifyShift },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = notifyShift,
                            onCheckedChange = { notifyShift = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text(zokasText("Kirim rekap saat Tutup Kasir (Close Shift)", "Send summary on Close Shift"), fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { notifyVoid = !notifyVoid },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = notifyVoid,
                            onCheckedChange = { notifyVoid = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text(zokasText("Kirim peringatan jika ada Void / Refund transaksi", "Send alert on Void / Refund transactions"), fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val nextVal = !autoExportOnCloseShift
                                autoExportOnCloseShift = nextVal
                                MultiDeviceTransactionSyncManager.setAutoExportOnCloseShift(context, nextVal)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = autoExportOnCloseShift,
                            onCheckedChange = { checked ->
                                autoExportOnCloseShift = checked
                                MultiDeviceTransactionSyncManager.setAutoExportOnCloseShift(context, checked)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            zokasText("Siapkan paket antrean otomatis saat Tutup Kasir", "Auto-prepare queue bundle on Close Shift"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = {
                            val (ok, info) = MultiDeviceTransactionSyncManager.shareLatestSyncBundle(context)
                            Toast.makeText(context, info.toString(), Toast.LENGTH_SHORT).show()
                            if (!ok) {
                                Toast.makeText(context, info.toString(), Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(zokasText("Kirim Paket Terbaru ke HP Owner", "Send Latest Bundle to Owner Phone"), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                persistMonitorConfig(isEnabled)
                                isTesting = true
                                scope.launch {
                                    val (ok, infoMsg) = OwnerRemoteMonitorManager.sendTestNotification(
                                        context,
                                        settingsStoreNameOrDefault(context)
                                    )
                                    isTesting = false
                                    Toast.makeText(context, infoMsg.toString(), Toast.LENGTH_LONG).show()
                                    if (!ok) {
                                        Toast.makeText(context, zokasText("Pengaturan tetap disimpan. Antrean akan dikirim saat terhubung.", "Settings saved. Queue will be sent once connected."), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isTesting,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(if (isTesting) zokasText("Mengirim...", "Sending...") else zokasText("Tes Kirim", "Test Send"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (botToken.isNotBlank() && chatId.isNotBlank()) {
                                    isEnabled = true
                                }
                                persistMonitorConfig(isEnabled)
                                Toast.makeText(context, zokasText("Pengaturan Pantauan HP Owner disimpan!", "Owner Phone Monitor settings saved!"), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(zokasText("SIMPAN", "SAVE"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                }
            }
        }
    }
}

private fun settingsStoreNameOrDefault(context: Context): String {
    return SettingsManager(context).storeName?.takeIf { it.isNotBlank() } ?: "ZoKas Store"
}


// --- 12. CARD SINKRONISASI ANTREAN TRANSAKSI MULTI-PERANGKAT (TAMPILAN PROFESIONAL) ---
@Composable
fun SettingsMultiDeviceSyncCard(viewModel: PosViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExpanded by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        MultiDeviceTransactionSyncManager.refreshQueueCount(context)
    }

    val rawSavedLabel = remember {
        val prefs = context.getSharedPreferences("zokas_multidevice_sync_prefs", Context.MODE_PRIVATE)
        prefs.getString("device_label", "") ?: ""
    }
    var deviceLabel by remember {
        mutableStateOf(
            if (rawSavedLabel.isNotBlank()) rawSavedLabel
            else SettingsManager(context).storeName?.takeIf { it.isNotBlank() }?.let { zokasText("Kasir $it", "Cashier $it") } ?: zokasText("Kasir Utama", "Main Cashier")
        )
    }
    var autoExportOnCloseShift by remember {
        mutableStateOf(MultiDeviceTransactionSyncManager.isAutoExportOnCloseShiftEnabled(context))
    }
    val registeredEmail = remember { MultiDeviceTransactionSyncManager.getRegisteredEmail(context) }
    var isBusy by remember { mutableStateOf(false) }
    val queueCount = MultiDeviceTransactionSyncManager.pendingQueueCount

    val exportSyncLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            isBusy = true
            scope.launch {
                val (_, msg) = MultiDeviceTransactionSyncManager.exportSyncBundleToUri(context, uri)
                isBusy = false
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val importSyncLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isBusy = true
            scope.launch {
                val (_, msg) = MultiDeviceTransactionSyncManager.importAndMergeSyncBundleFromUri(context, viewModel, uri)
                isBusy = false
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showGuideDialog) {
        AlertDialog(
            onDismissRequest = { showGuideDialog = false },
            icon = {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(zokasText("Panduan Sinkronisasi Multi-Perangkat", "Multi-Device Sync Guide"), fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        zokasText("Skenario: HP Owner memantau dan menggabungkan laporan transaksi dari beberapa perangkat kasir di cabang/meja berbeda secara offline-first tanpa biaya server.", "Scenario: Owner phone monitors and merges transaction reports from multiple cashier devices offline-first with zero server cost."),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        zokasText("1. Gunakan Email Pemilik yang Sama:\nPastikan seluruh perangkat mendaftarkan alamat email pemilik yang sama saat aktivasi awal.", "1. Use the Same Owner Email:\nEnsure all devices register the same owner email during initial setup."),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        zokasText("2. Tentukan Identitas Terminal Kasir:\nIsi nama perangkat (misal: 'Kasir Cabang Timur') agar setiap catatan transaksi dan tutup shift memiliki asal cabang yang jelas.", "2. Set Cashier Terminal Identity:\nEnter a device name (e.g., 'East Branch POS') so each transaction and shift close has a clear source."),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        zokasText("3. Kirim / Ekspor Paket Data (.json):\nSetiap transaksi & tutup shift otomatis masuk antrean kronologis. Tekan 'Kirim Paket Laporan ke HP Owner' (via WhatsApp/Email/Drive) atau 'Ekspor File'.", "3. Send / Export Data Bundle (.json):\nTransactions & shift closings queue automatically. Tap 'Send Report Bundle to Owner Phone' or 'Export File'."),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        zokasText("4. Gabungkan di Perangkat Owner:\nBuka menu ini di HP Owner lalu pilih 'Impor & Gabung'. Sistem menyusun ulang data berdasarkan waktu transaksi tanpa menimpa data lokal.", "4. Merge on Owner Device:\nOpen this menu on the Owner phone and tap 'Import & Merge'. Data is sorted chronologically without overwriting local records."),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showGuideDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(zokasText("MENGERTI", "GOT IT"), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    SettingsSectionLeadingIcon(Icons.Default.Sync, MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = zokasText("Sinkronisasi Multi-Perangkat", "Multi-Device Sync"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (queueCount > 0)
                                zokasText("$queueCount catatan siap disinkronkan • Lokal & Cloud", "$queueCount items ready to sync • Local & Cloud")
                            else
                                zokasText("Tersinkron • Antrean catatan lokal kosong", "Synced • Local queue is empty"),
                            fontSize = 11.sp,
                            color = if (queueCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
SettingsMenuHelpIconButton(menuKey = "multi_device_sync")
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                Column(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // PANEL STATUS & IDENTITAS AKUN TERVERIFIKASI
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = registeredEmail.ifBlank { zokasText("Akun Lokal Terdaftar", "Registered Local Account") },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    color = if (queueCount > 0)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = zokasText("$queueCount Data Antrean", "$queueCount Queued Items"),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (queueCount > 0)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                text = MultiDeviceTransactionSyncManager.lastSyncStatusMessage,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { showGuideDialog = true },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(zokasText("Cara Kerja & Panduan", "How It Works & Guide"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // IDENTITAS TERMINAL KASIR (NAMA RAMAH PENGGUNA, BUKAN KODE MODEL HARDWARE)
                    OutlinedTextField(
                        value = deviceLabel,
                        onValueChange = {
                            deviceLabel = it
                            MultiDeviceTransactionSyncManager.setDeviceLabel(context, it)
                        },
                        label = { Text(zokasText("Nama Terminal / Cabang Kasir", "Cashier Terminal / Branch Name"), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Default.PointOfSale, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp)
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val nextVal = !autoExportOnCloseShift
                                autoExportOnCloseShift = nextVal
                                MultiDeviceTransactionSyncManager.setAutoExportOnCloseShift(context, nextVal)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = autoExportOnCloseShift,
                                onCheckedChange = { checked ->
                                    autoExportOnCloseShift = checked
                                    MultiDeviceTransactionSyncManager.setAutoExportOnCloseShift(context, checked)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    zokasText("Otomatis siapkan file rekap saat Tutup Kasir", "Auto-prepare summary file on Close Shift"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    zokasText("Memudahkan pengiriman laporan shift ke HP Owner.", "Makes sending shift reports to Owner phone easier."),
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // AKSI UTAMA: BAGIKAN LANGSUNG KE OWNER
                    Button(
                        onClick = {
                            MultiDeviceTransactionSyncManager.setDeviceLabel(context, deviceLabel)
                            val (ok, info) = MultiDeviceTransactionSyncManager.shareLatestSyncBundle(context)
                            Toast.makeText(context, info, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 46.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(zokasText("Kirim Paket Laporan ke HP Owner", "Send Report Bundle to Owner Phone"), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    // AKSI SEKUNDER: EKSPOR & IMPOR FILE JSON
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                MultiDeviceTransactionSyncManager.setDeviceLabel(context, deviceLabel)
                                val cleanLabel = deviceLabel.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
                                val fname = "ZoKas_Sync_${cleanLabel.ifBlank { "Kasir" }}_${System.currentTimeMillis()}.json"
                                exportSyncLauncher.launch(fname)
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(zokasText("Ekspor File", "Export File"), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { importSyncLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.DownloadForOffline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(zokasText("Impor & Gabung", "Import & Merge"), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
                }
            }
        }
    }
}
