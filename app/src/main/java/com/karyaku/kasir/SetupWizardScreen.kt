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

private val availableUnits = listOf("pcs", "kg", "gram", "liter", "porsi", "cup", "dus", "zak", "ikat", "jasa")

@Composable
fun SetupWizardScreen(
    settingsManager: SettingsManager,
    viewModel: PosViewModel,
    onSetupComplete: (UserEntity) -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity

    // 1. Setujui kebijakan (tanpa input email di awal)
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

    // 6. Username, PIN/Password, dan metode login (PIN atau Biometrik -> langsung setting biometrik jika dipilih)
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

    // 8. Detail produk (jika produk; jika jasa tidak perlu) & satuan
    var itemName by remember { mutableStateOf("") }
    var itemPriceText by remember { mutableStateOf("") }
    var itemStockText by remember { mutableStateOf("10") }
    var itemUnit by remember { mutableStateOf(selectedBusiness.defaultUnit) }

    LaunchedEffect(selectedBusiness.id) {
        itemUnit = selectedBusiness.defaultUnit
    }

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

        // 1. KEBIJAKAN
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Persetujuan Kebijakan", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Aplikasi berjalan offline-first di perangkat Anda dan menyimpan data usaha secara lokal. Lanjutkan dengan menyetujui kebijakan penggunaan.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { acceptedPolicy = !acceptedPolicy },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = acceptedPolicy, onCheckedChange = { acceptedPolicy = it })
                    Text("Saya menyetujui kebijakan penggunaan ZoKas", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // 2. PILIHAN BAHASA
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Pilihan Bahasa", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                Text("3. Detail Jenis Usaha (${BusinessCatalog500.allBusinesses.size} Jenis Usaha)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedTextField(
                    value = businessSearch,
                    onValueChange = {
                        businessSearch = it
                        dropdownExpanded = true
                    },
                    label = { Text("Cari Jenis Usaha") },
                    placeholder = { Text("Ketik misal: ayam potong, warung, bengkel, kopi...") },
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
                            text = "Pilih: ${selectedBusiness.name}",
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
                    text = "Jenis usaha otomatis terdeteksi: ${selectedBusiness.mappedBusinessType} (${selectedBusiness.sectorGroup})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 4. REKOMENDASI MODUL (DEFAULT OFF, BISA DIONKAN & BISA DIUBAH DI SETTING)
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("4. Rekomendasi Modul (Awalnya OFF)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Semua modul rekomendasi dibuat OFF di awal agar tidak berat. Anda boleh menyalakannya sekarang atau mengaktifkannya kapan saja di menu Setting.",
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
                Text("5. Pilih Mode Penggunaan", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

        // 6. USERNAME, PIN/PASSWORD & LOGIN (PIN ATAU BIOMETRIK -> LANGSUNG SETTING BIOMETRIK JIKA DIPILIH)
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("6. Akun & Metode Login", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username / Nama Pengguna") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pinCode,
                    onValueChange = { raw -> if (raw.length <= 6 && raw.all { it.isDigit() }) pinCode = raw },
                    label = { Text("PIN / Password (4-6 Angka)") },
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
                        Text("Login PIN", fontSize = 13.sp)
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
                        Text("Login Biometrik", fontSize = 13.sp)
                    }
                }
                if (loginMethod == "BIOMETRIC") {
                    Text(
                        text = if (biometricReady) "Status Biometrik: Aktif & siap dipakai" else "Status Biometrik: Ketuk Login Biometrik untuk verifikasi sidik jari sekarang",
                        fontSize = 11.sp,
                        color = if (biometricReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 7. PEMBAYARAN + LANGSUNG SETTING QRIS / TRANSFER MANUAL
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("7. Metode Pembayaran", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                listOf("CASH" to "Tunai / Cash", "QRIS" to "QRIS", "TRANSFER" to "Transfer Manual").forEach { (key, label) ->
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
                        label = { Text("Setting Payload / Kode QRIS") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (paymentSelection["TRANSFER"] == true) {
                    OutlinedTextField(
                        value = bankNameInput,
                        onValueChange = { bankNameInput = it },
                        label = { Text("Nama Bank (cth: BCA, BRI, Mandiri)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bankAccInput,
                        onValueChange = { bankAccInput = it },
                        label = { Text("Nomor Rekening") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bankHolderInput,
                        onValueChange = { bankHolderInput = it },
                        label = { Text("Atas Nama Rekening") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 8. DETAIL PRODUK (JIKA PRODUK; JIKA JASA TIDAK PERLU) & SATUAN
        if (!selectedBusiness.isServiceOnly) {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("8. Detail Produk Awal & Satuan", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("Nama Produk (Opsional)") },
                        placeholder = { Text("Contoh: Beras Ramos / Ayam Potong") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = itemPriceText,
                            onValueChange = { itemPriceText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Harga Jual") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = itemStockText,
                            onValueChange = { itemStockText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Stok Awal") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text("Satuan:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        availableUnits.take(5).forEach { unit ->
                            FilterChip(
                                selected = itemUnit == unit,
                                onClick = { itemUnit = unit },
                                label = { Text(unit, fontSize = 11.sp) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        availableUnits.drop(5).forEach { unit ->
                            FilterChip(
                                selected = itemUnit == unit,
                                onClick = { itemUnit = unit },
                                label = { Text(unit, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                if (!acceptedPolicy) {
                    Toast.makeText(context, "Centang persetujuan kebijakan terlebih dahulu.", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (username.isBlank()) {
                    Toast.makeText(context, "Username wajib diisi.", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (pinCode.length < 4) {
                    Toast.makeText(context, "PIN minimal 4 angka.", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                settingsManager.language = selectedLang
                LanguageManager.setLanguage(context, selectedLang)
                ZokasLocaleManager.setLanguage(context, selectedLang)

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

                if (!selectedBusiness.isServiceOnly && itemName.isNotBlank()) {
                    val priceVal = itemPriceText.toLongOrNull() ?: 0L
                    val stockVal = itemStockText.toIntOrNull() ?: 0
                    val packedCode = packSkuAndBarcode("", "", itemUnit)
                    viewModel.addProduct(
                        name = itemName.trim(),
                        price = priceVal,
                        stock = stockVal,
                        category = selectedBusiness.sectorGroup,
                        costPrice = 0L,
                        barcode = packedCode,
                        variations = "",
                        commission = 0L,
                        expiry = 0L,
                        isService = false,
                        isCombo = false,
                        imagePath = "",
                        wholesalePrice = 0L,
                        wholesaleMinQty = 0,
                        userId = 1L,
                        userName = username.trim()
                    )
                }

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
            Text("SIMPAN & MULAI", fontWeight = FontWeight.Bold)
        }
    }
}
