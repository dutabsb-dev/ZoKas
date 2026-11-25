package com.karyaku.kasir

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.karyaku.kasir.data.local.UserEntity

private val supportedLanguages = listOf(
    "id" to "Bahasa Indonesia",
    "en" to "English (Inggris)",
    "jv" to "Basa Jawa",
    "su" to "Basa Sunda",
    "mad" to "Bhâsa Madhurâ"
)

private val usageModes = listOf(
    "AWAM" to "Mode Awam — Paling mudah & praktis, tanpa ribet mikirin pembukuan.",
    "MUDAH" to "Mode Mudah — Ramah belajar teknologi, tampil bertahap & mudah dipahami.",
    "STANDAR" to "Mode Standar — Fitur usaha harian seimbang untuk stok, kas, & laporan.",
    "LENGKAP" to "Mode Lengkap — Tampilan & seluruh fitur penuh seperti aplikasi saat ini."
)

@Composable
fun SetupWizardScreen(
    settingsManager: SettingsManager,
    viewModel: PosViewModel,
    onSetupComplete: (UserEntity) -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity

    // 1. Persetujuan Kebijakan (teks kebijakan lengkap tampil jelas)
    var acceptedPolicy by remember { mutableStateOf(false) }

    // 2. Pilihan bahasa
    var selectedLang by remember { mutableStateOf(LanguageManager.normalizeLang(settingsManager.language)) }

    // 3. Dropdown + search ratusan jenis usaha & otomatis tentukan jenis usaha
    var businessSearch by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedBusiness by remember {
        mutableStateOf(BusinessCatalog500.allBusinesses.first { it.name == "Warung Sembako / Kelontong" })
    }
    val filteredBusinesses = remember(businessSearch) {
        if (businessSearch.isBlank()) BusinessCatalog500.allBusinesses.take(60)
        else BusinessCatalog500.allBusinesses.filter {
            it.name.contains(businessSearch.trim(), ignoreCase = true) ||
                it.sectorGroup.contains(businessSearch.trim(), ignoreCase = true)
        }.take(60)
    }

    // 4. Rekomendasi modul default OFF (bisa dinyalakan bila berkenan)
    val moduleOptions = remember(selectedBusiness.mappedBusinessType) {
        BusinessCatalog500.optionalModulesFor(selectedBusiness.mappedBusinessType)
    }
    val enabledOptionalModules = remember { mutableStateMapOf<String, Boolean>() }

    // 5. Pilih mode AWAM / MUDAH / STANDAR / LENGKAP
    var selectedMode by remember { mutableStateOf("AWAM") }

    // 6. Nama Toko & turunannya (Alamat, No. Telp, Pesan Struk) + Akun & Metode Login
    var storeNameInput by remember { mutableStateOf(settingsManager.storeName ?: "") }
    var storeAddressInput by remember { mutableStateOf(settingsManager.storeAddress ?: "") }
    var storePhoneInput by remember { mutableStateOf(settingsManager.storePhone ?: "") }
    var storeFooterInput by remember { mutableStateOf(settingsManager.storeFooter ?: "Terima Kasih atas Kunjungan Anda") }

    var username by remember { mutableStateOf("") }
    var pinCode by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    var loginMethod by remember { mutableStateOf("PIN") } // "PIN" atau "BIOMETRIC"
    var biometricReady by remember { mutableStateOf(false) }

    // 7. Metode pembayaran + langsung setting QRIS / Transfer Manual jika dipilih
    val paymentSelection = remember { mutableStateMapOf("CASH" to true, "QRIS" to false, "TRANSFER" to false) }
    var qrisInput by remember { mutableStateOf(settingsManager.qrisStaticPayload ?: "") }
    var bankNameInput by remember { mutableStateOf(settingsManager.bankName ?: "") }
    var bankAccInput by remember { mutableStateOf(settingsManager.bankAccountNumber ?: "") }
    var bankHolderInput by remember { mutableStateOf(settingsManager.bankAccountName ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = zokasText("Pendaftaran Awal ZoKas", "ZoKas Initial Setup", selectedLang),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )

        // 1. KEBIJAKAN (ISI KEBIJAKAN LENGKAP DITAMPILKAN)
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = zokasText("1. Kebijakan Penggunaan & Privasi", "1. Terms of Use & Privacy Policy", selectedLang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = zokasText(
                                "• Penyimpanan Lokal (Offline-First): Seluruh data transaksi, produk, stok, pelanggan, dan pengaturan disimpan langsung di perangkat Anda.",
                                "• Local Storage (Offline-First): All transaction, product, stock, customer, and settings data are stored directly on your device.",
                                selectedLang
                            ),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = zokasText(
                                "• Privasi & Kendali Pemilik: Data usaha tidak dikirim ke server pihak ketiga developer. Backup, ekspor, dan notifikasi jarak jauh berjalan sesuai pengaturan Anda sendiri.",
                                "• Privacy & Owner Control: Business data is not sent to third-party developer servers. Backups, exports, and remote alerts operate under your own configuration.",
                                selectedLang
                            ),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = zokasText(
                                "• Tanggung Jawab Operasional: Pastikan Anda rutin membuat backup data dan memverifikasi keakuratan stok fisik maupun pembayaran.",
                                "• Operational Responsibility: Please back up your data regularly and verify physical stock and payment accuracy.",
                                selectedLang
                            ),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { acceptedPolicy = !acceptedPolicy },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = acceptedPolicy, onCheckedChange = { acceptedPolicy = it })
                    Text(
                        text = zokasText("Saya telah membaca & menyetujui kebijakan penggunaan ZoKas", "I have read & agree to ZoKas terms and privacy policy", selectedLang),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 2. PILIHAN BAHASA
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(zokasText("2. Pilihan Bahasa", "2. Language Selection", selectedLang), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                supportedLanguages.forEach { (code, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedLang = code
                            LanguageManager.setLanguage(context, code)
                            ZokasLocaleManager.setLanguage(context, code)
                            settingsManager.language = code
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedLang == code,
                            onClick = {
                                selectedLang = code
                                LanguageManager.setLanguage(context, code)
                                ZokasLocaleManager.setLanguage(context, code)
                                settingsManager.language = code
                            }
                        )
                        Text(label, fontSize = 13.sp)
                    }
                }
            }
        }

        // 3. DETAIL USAHA (DROPDOWN + SEARCH 500+ USAHA) & OTOMATIS TENTUKAN JENIS USAHA
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(zokasText("3. Detail Jenis Usaha (${BusinessCatalog500.allBusinesses.size} Jenis Usaha)", "3. Business Type Details (${BusinessCatalog500.allBusinesses.size} Types)", selectedLang), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedTextField(
                    value = businessSearch,
                    onValueChange = {
                        businessSearch = it
                        dropdownExpanded = true
                    },
                    label = { Text(zokasText("Cari Jenis Usaha", "Search Business Type", selectedLang)) },
                    placeholder = { Text(zokasText("Ketik misal: ayam potong, warung, bengkel, kopi...", "Type e.g. chicken, warung, workshop, coffee...", selectedLang)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { dropdownExpanded = !dropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = zokasText("Pilih: ${selectedBusiness.name}", "Selected: ${selectedBusiness.name}", selectedLang),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.88f).heightIn(max = 280.dp)
                    ) {
                        filteredBusinesses.forEach { biz ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(biz.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${biz.sectorGroup} • Jenis: ${biz.mappedBusinessType}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    selectedBusiness = biz
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Text(
                    text = zokasText("Jenis usaha otomatis terdeteksi: ${selectedBusiness.mappedBusinessType} (${selectedBusiness.sectorGroup})", "Auto-detected business type: ${selectedBusiness.mappedBusinessType} (${selectedBusiness.sectorGroup})", selectedLang),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 4. REKOMENDASI MODUL (DEFAULT OFF, BISA DIONKAN & BISA DIUBAH DI SETTING)
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(zokasText("4. Rekomendasi Modul (Awalnya OFF)", "4. Recommended Modules (Default OFF)", selectedLang), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    zokasText(
                        "Semua modul rekomendasi dibuat OFF di awal agar tidak berat. Anda boleh menyalakannya sekarang atau mengaktifkannya kapan saja di menu Setting.",
                        "All recommended modules start OFF to stay light. You can enable them now or anytime in Settings.",
                        selectedLang
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                moduleOptions.forEach { mod ->
                    val isOn = enabledOptionalModules[mod.key] ?: false
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(mod.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(mod.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isOn,
                            onCheckedChange = { enabledOptionalModules[mod.key] = it }
                        )
                    }
                }
            }
        }

        // 5. PILIH MODE AWAM / MUDAH / STANDAR / LENGKAP
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(zokasText("5. Pilih Mode Penggunaan", "5. Select Usage Mode", selectedLang), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                usageModes.forEach { (modeKey, modeDesc) ->
                    val active = selectedMode == modeKey
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { selectedMode = modeKey },
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = active, onClick = { selectedMode = modeKey })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(modeDesc, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 6. IDENTITAS TOKO (NAMA TOKO & TURUNANNYA) + AKUN & METODE LOGIN
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(zokasText("6. Identitas Toko & Akun Login", "6. Store Identity & Login Account", selectedLang), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedTextField(
                    value = storeNameInput,
                    onValueChange = { storeNameInput = it },
                    label = { Text(zokasText("Nama Toko *", "Store Name *", selectedLang)) },
                    placeholder = { Text(zokasText("Contoh: Toko Berkah / Ayam Potong Bu Sari", "Example: Berkah Store / Fresh Chicken", selectedLang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = storeAddressInput,
                    onValueChange = { storeAddressInput = it },
                    label = { Text(zokasText("Alamat Toko (Opsional)", "Store Address (Optional)", selectedLang)) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = storePhoneInput,
                    onValueChange = { storePhoneInput = it },
                    label = { Text(zokasText("No. Telepon / WhatsApp Toko (Opsional)", "Store Phone / WhatsApp (Optional)", selectedLang)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = storeFooterInput,
                    onValueChange = { storeFooterInput = it },
                    label = { Text(zokasText("Pesan Penutup Struk (Opsional)", "Receipt Footer Message (Optional)", selectedLang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(zokasText("Username / Nama Pengguna *", "Username / Account Name *", selectedLang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pinCode,
                    onValueChange = { raw -> if (raw.length <= 6 && raw.all { it.isDigit() }) pinCode = raw },
                    label = { Text(zokasText("PIN / Password (4-6 Angka) *", "PIN / Passcode (4-6 Digits) *", selectedLang)) },
                    visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { loginMethod = "PIN" }) {
                        RadioButton(selected = loginMethod == "PIN", onClick = { loginMethod = "PIN" })
                        Text(zokasText("Login PIN", "PIN Login", selectedLang), fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        loginMethod = "BIOMETRIC"
                        if (activity != null) {
                            val tempOwner = UserEntity(
                                id = 1L,
                                name = username.trim().ifBlank { "Owner" },
                                pin = hashPin(pinCode.ifBlank { "0000" }),
                                role = "OWNER",
                                branchId = 1L
                            )
                            viewModel.registerBiometric(activity, tempOwner) { ok, msg ->
                                biometricReady = ok
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        RadioButton(selected = loginMethod == "BIOMETRIC", onClick = {
                            loginMethod = "BIOMETRIC"
                            if (activity != null) {
                                val tempOwner = UserEntity(
                                    id = 1L,
                                    name = username.trim().ifBlank { "Owner" },
                                    pin = hashPin(pinCode.ifBlank { "0000" }),
                                    role = "OWNER",
                                    branchId = 1L
                                )
                                viewModel.registerBiometric(activity, tempOwner) { ok, msg ->
                                    biometricReady = ok
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        })
                        Text(zokasText("Login Biometrik", "Biometric Login", selectedLang), fontSize = 13.sp)
                    }
                }
                if (loginMethod == "BIOMETRIC") {
                    Text(
                        text = if (biometricReady)
                            zokasText("Status Biometrik: Aktif & siap dipakai", "Biometric Status: Active & ready", selectedLang)
                        else
                            zokasText("Status Biometrik: Ketuk Login Biometrik untuk verifikasi sidik jari sekarang", "Biometric Status: Tap Biometric Login to verify fingerprint now", selectedLang),
                        fontSize = 11.sp,
                        color = if (biometricReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 7. PEMBAYARAN + LANGSUNG SETTING QRIS / TRANSFER MANUAL
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(zokasText("7. Metode Pembayaran", "7. Payment Methods", selectedLang), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                listOf(
                    "CASH" to zokasText("Tunai / Cash", "Cash", selectedLang),
                    "QRIS" to zokasText("QRIS", "QRIS", selectedLang),
                    "TRANSFER" to zokasText("Transfer Manual", "Manual Transfer", selectedLang)
                ).forEach { (key, label) ->
                    val checked = paymentSelection[key] ?: false
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { paymentSelection[key] = !checked },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { paymentSelection[key] = it })
                        Text(label, fontSize = 13.sp)
                    }
                }
                if (paymentSelection["QRIS"] == true) {
                    OutlinedTextField(
                        value = qrisInput,
                        onValueChange = { qrisInput = it },
                        label = { Text(zokasText("Setting Payload / Kode QRIS", "QRIS Payload / Code", selectedLang)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (paymentSelection["TRANSFER"] == true) {
                    OutlinedTextField(
                        value = bankNameInput,
                        onValueChange = { bankNameInput = it },
                        label = { Text(zokasText("Nama Bank (cth: BCA, BRI, Mandiri)", "Bank Name (e.g. BCA, BRI, Mandiri)", selectedLang)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bankAccInput,
                        onValueChange = { bankAccInput = it },
                        label = { Text(zokasText("Nomor Rekening", "Account Number", selectedLang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bankHolderInput,
                        onValueChange = { bankHolderInput = it },
                        label = { Text(zokasText("Atas Nama Rekening", "Account Holder Name", selectedLang)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Button(
            onClick = {
                if (!acceptedPolicy) {
                    Toast.makeText(context, zokasText("Centang persetujuan kebijakan terlebih dahulu.", "Please accept the policy first.", selectedLang), Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (storeNameInput.isBlank()) {
                    Toast.makeText(context, zokasText("Nama toko wajib diisi.", "Store name is required.", selectedLang), Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (username.isBlank()) {
                    Toast.makeText(context, zokasText("Username wajib diisi.", "Username is required.", selectedLang), Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (pinCode.length < 4) {
                    Toast.makeText(context, zokasText("PIN minimal 4 angka.", "PIN must be at least 4 digits.", selectedLang), Toast.LENGTH_SHORT).show()
                    return@Button
                }

                settingsManager.language = selectedLang
                LanguageManager.setLanguage(context, selectedLang)
                ZokasLocaleManager.setLanguage(context, selectedLang)

                val cleanStoreName = storeNameInput.trim()
                settingsManager.storeName = cleanStoreName
                settingsManager.storeAddress = storeAddressInput.trim()
                settingsManager.storePhone = storePhoneInput.trim()
                settingsManager.storeFooter = storeFooterInput.trim().ifBlank { "Terima Kasih atas Kunjungan Anda" }

                settingsManager.businessType = selectedBusiness.mappedBusinessType
                settingsManager.businessDetailName = selectedBusiness.name
                settingsManager.appMode = selectedMode

                settingsManager.enableFinance = enabledOptionalModules["finance"] ?: false
                settingsManager.enableWholesale = enabledOptionalModules["wholesale"] ?: false
                settingsManager.enableSupplier = enabledOptionalModules["supplier"] ?: false
                settingsManager.isTableModeEnabled = enabledOptionalModules["table_mode"] ?: false
                settingsManager.enableReservation = enabledOptionalModules["reservation"] ?: false
                settingsManager.enableCrmModule = enabledOptionalModules["crm"] ?: false

                val chosenPayments = paymentSelection.filterValues { it }.keys.ifEmpty { setOf("CASH") }
                settingsManager.paymentMethods = chosenPayments
                if (paymentSelection["QRIS"] == true && qrisInput.isNotBlank()) {
                    settingsManager.qrisStaticPayload = qrisInput.trim()
                }
                if (paymentSelection["TRANSFER"] == true) {
                    settingsManager.bankName = bankNameInput.trim()
                    settingsManager.bankAccountNumber = bankAccInput.trim()
                    settingsManager.bankAccountName = bankHolderInput.trim()
                }

                settingsManager.isBiometricEnabled = (loginMethod == "BIOMETRIC" && biometricReady)
                settingsManager.isSetupCompleted = true

                val adminEntity = UserEntity(
                    id = 1L,
                    name = username.trim(),
                    pin = hashPin(pinCode),
                    role = "OWNER",
                    totalCommission = 0L,
                    branchId = 1L
                )
                viewModel.addUser(username.trim(), pinCode, "OWNER", 0L, adminEntity)
                onSetupComplete(adminEntity)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(zokasText("SIMPAN & MULAI", "SAVE & START", selectedLang), fontWeight = FontWeight.Bold)
        }
    }
}
