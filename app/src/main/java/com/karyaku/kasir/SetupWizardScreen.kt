package com.karyaku.kasir

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karyaku.kasir.data.local.UserEntity

enum class BusinessType(val label: String, val description: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    KULINER("Kuliner", "Restoran, Cafe, Warung, Katering", Icons.Default.Restaurant),
    RETAIL("Pedagang Eceran", "Toko Kelontong, Minimarket, Bangunan", Icons.Default.Store),
    JASA("Usaha Jasa", "Salon, Barbershop, Laundry, Servis", Icons.Default.Build),
    MUSIMAN("Usaha Khusus / Musiman", "Event Organizer, Toko Kue Musiman", Icons.Default.Event)
}

data class SetupWizardState(
    val businessType: BusinessType? = null,
    val storeName: String = "",
    val storeAddress: String = "",
    val storePhone: String = "",
    val adminName: String = "",
    val adminPin: String = "",
    val cashierName: String = "",
    val cashierPin: String = "",
    val paymentMethods: Set<String> = setOf("CASH"),
    val isTosAgreed: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    settingsManager: SettingsManager,
    viewModel: PosViewModel,
    onSetupComplete: (UserEntity) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 8
    
    var wizardState by remember { mutableStateOf(SetupWizardState()) }
    
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ZoKas Setup", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = EmeraldGreen)
                    Text("Langkah $currentStep dari $totalSteps", fontSize = 14.sp, color = SlateGrey)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { currentStep.toFloat() / totalSteps.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = EmeraldGreen,
                    trackColor = SlateSurface
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(animationSpec = tween(300)) { fullWidth -> fullWidth * direction } + fadeIn(animationSpec = tween(300))) togetherWith
                    (slideOutHorizontally(animationSpec = tween(300)) { fullWidth -> -fullWidth * direction } + fadeOut(animationSpec = tween(300)))
                }, label = "wizard_step_transition"
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (step) {
                        1 -> Step1BusinessType(
                            selectedType = wizardState.businessType,
                            onSelect = { wizardState = wizardState.copy(businessType = it); errorMessage = "" }
                        )
                        2 -> Step2StoreDetails(
                            name = wizardState.storeName, address = wizardState.storeAddress, phone = wizardState.storePhone,
                            onNameChange = { wizardState = wizardState.copy(storeName = it); errorMessage = "" },
                            onAddressChange = { wizardState = wizardState.copy(storeAddress = it) },
                            onPhoneChange = { wizardState = wizardState.copy(storePhone = it) }
                        )
                        3 -> Step3AdminAccount(
                            name = wizardState.adminName, pin = wizardState.adminPin,
                            onNameChange = { wizardState = wizardState.copy(adminName = it); errorMessage = "" },
                            onPinChange = { wizardState = wizardState.copy(adminPin = it); errorMessage = "" }
                        )
                        4 -> Step4CashierAccount(
                            name = wizardState.cashierName, pin = wizardState.cashierPin,
                            onNameChange = { wizardState = wizardState.copy(cashierName = it); errorMessage = "" },
                            onPinChange = { wizardState = wizardState.copy(cashierPin = it); errorMessage = "" }
                        )
                        5 -> Step5PaymentMethods(
                            selectedMethods = wizardState.paymentMethods,
                            onToggleMethod = { method ->
                                val newMethods = if (wizardState.paymentMethods.contains(method)) {
                                    wizardState.paymentMethods - method
                                } else {
                                    wizardState.paymentMethods + method
                                }
                                wizardState = wizardState.copy(paymentMethods = newMethods)
                            }
                        )
                        6 -> Step6ModularFeatures(businessType = wizardState.businessType)
                        7 -> Step7TermsAndConditions(
                            isAgreed = wizardState.isTosAgreed,
                            onToggleAgree = { wizardState = wizardState.copy(isTosAgreed = it); errorMessage = "" }
                        )
                        8 -> Step8Summary(wizardState = wizardState)
                    }

                    if (errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(errorMessage, color = ErrorRed, fontSize = 14.sp, textAlign = TextAlign.Center)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // TOMBOL NAVIGASI
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (currentStep > 1) {
                            OutlinedButton(
                                onClick = { if (!isLoading) { currentStep--; errorMessage = "" } },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isLoading,
                                border = BorderStroke(1.dp, SlateGrey)
                            ) {
                                Text("Kembali", color = SlateGrey, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        
                        Button(
                            onClick = {
                                when (currentStep) {
                                    1 -> {
                                        if (wizardState.businessType == null) errorMessage = "Silakan pilih jenis usaha Anda."
                                        else currentStep++
                                    }
                                    2 -> {
                                        if (wizardState.storeName.isBlank()) errorMessage = "Nama toko wajib diisi."
                                        else currentStep++
                                    }
                                    3 -> {
                                        if (wizardState.adminName.isBlank()) errorMessage = "Nama admin wajib diisi."
                                        else if (wizardState.adminPin.length != 6) errorMessage = "PIN Admin harus 6 digit."
                                        else currentStep++
                                    }
                                    4 -> {
                                        if (wizardState.cashierName.isBlank()) errorMessage = "Nama kasir wajib diisi."
                                        else if (wizardState.cashierPin.length != 6) errorMessage = "PIN Kasir harus 6 digit."
                                        else currentStep++
                                    }
                                    5 -> {
                                        if (wizardState.paymentMethods.isEmpty()) errorMessage = "Pilih minimal satu metode pembayaran."
                                        else currentStep++
                                    }
                                    6 -> currentStep++
                                    7 -> {
                                        if (!wizardState.isTosAgreed) errorMessage = "Anda harus menyetujui Syarat & Ketentuan."
                                        else currentStep++
                                    }
                                    8 -> {
                                        // PROSES SIMPAN KE DATABASE
                                        isLoading = true
                                        settingsManager.businessType = wizardState.businessType?.name ?: "KULINER"
                                        settingsManager.storeName = wizardState.storeName
                                        settingsManager.storeAddress = wizardState.storeAddress
                                        settingsManager.storePhone = wizardState.storePhone
                                        settingsManager.paymentMethods = wizardState.paymentMethods
                                        
                                        // Konfigurasi otomatis fitur berdasarkan jenis usaha
                                        when (wizardState.businessType) {
                                            BusinessType.KULINER -> {
                                                settingsManager.isTableModeEnabled = true
                                            }
                                            BusinessType.RETAIL -> {
                                                settingsManager.isTableModeEnabled = false
                                            }
                                            BusinessType.JASA -> {
                                                settingsManager.isTableModeEnabled = false
                                            }
                                            BusinessType.MUSIMAN -> {
                                                settingsManager.isTableModeEnabled = false
                                            }
                                            null -> {}
                                        }

                                        viewModel.registerFullSetup(
                                            businessType = settingsManager.businessType,
                                            storeName = wizardState.storeName,
                                            storeAddress = wizardState.storeAddress,
                                            storePhone = wizardState.storePhone,
                                            adminName = wizardState.adminName,
                                            adminPin = wizardState.adminPin,
                                            cashierName = wizardState.cashierName,
                                            cashierPin = wizardState.cashierPin
                                        ) { newAdmin ->
                                            isLoading = false
                                            settingsManager.isSetupCompleted = true
                                            onSetupComplete(newAdmin)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Text(if (currentStep == 8) "Selesai & Mulai" else "Lanjut", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- KOMPONEN LANGKAH 1-3 (Sama seperti Sub-sesi A2) ---
@Composable fun Step1BusinessType(selectedType: BusinessType?, onSelect: (BusinessType) -> Unit) { /* Kode dari A2 */ 
    Text("Apa Jenis Usaha Anda?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(24.dp))
    BusinessType.entries.forEach { type ->
        val isSelected = selectedType == type
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onSelect(type) },
            colors = CardDefaults.cardColors(containerColor = if (isSelected) EmeraldGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, if (isSelected) EmeraldGreen else SlateGreyLight),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(type.icon, contentDescription = type.label, tint = if (isSelected) EmeraldGreen else SlateGrey, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(type.label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (isSelected) EmeraldGreen else SlateGreyDark)
                    Text(type.description, fontSize = 12.sp, color = SlateGrey)
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isSelected) Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = EmeraldGreen)
            }
        }
    }
}
@Composable fun Step2StoreDetails(name: String, address: String, phone: String, onNameChange: (String) -> Unit, onAddressChange: (String) -> Unit, onPhoneChange: (String) -> Unit) { /* Kode dari A2 */ 
    Text("Detail Toko", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(24.dp))
    OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text("Nama Toko *") }, leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(value = address, onValueChange = onAddressChange, label = { Text("Alamat Toko") }, leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(value = phone, onValueChange = onPhoneChange, label = { Text("No. Telepon") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
}
@Composable fun Step3AdminAccount(name: String, pin: String, onNameChange: (String) -> Unit, onPinChange: (String) -> Unit) { /* Kode dari A2 */ 
    Text("Akun Admin (Owner)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(24.dp))
    OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text("Nama Admin *") }, leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(value = pin, onValueChange = { if (it.length <= 6) onPinChange(it) }, label = { Text("PIN Keamanan (6-Digit) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
}

// --- KOMPONEN LANGKAH 4: AKUN KASIR ---
@Composable
fun Step4CashierAccount(name: String, pin: String, onNameChange: (String) -> Unit, onPinChange: (String) -> Unit) {
    Text("Akun Kasir", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Text("Kasir hanya dapat mengakses halaman transaksi dan tidak dapat melihat laporan keuangan.", style = MaterialTheme.typography.bodyMedium, color = SlateGrey, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(24.dp))

    OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text("Nama Kasir *") }, leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(value = pin, onValueChange = { if (it.length <= 6) onPinChange(it) }, label = { Text("PIN Keamanan (6-Digit) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
}

// --- KOMPONEN LANGKAH 5: METODE PEMBAYARAN ---
@Composable
fun Step5PaymentMethods(selectedMethods: Set<String>, onToggleMethod: (String) -> Unit) {
    Text("Metode Pembayaran", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Text("Pilih metode pembayaran yang Anda terima di toko. (Bisa dipilih lebih dari satu).", style = MaterialTheme.typography.bodyMedium, color = SlateGrey, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(24.dp))

    val methods = listOf("CASH", "QRIS", "Virtual Account", "Bank Transfer")
    methods.forEach { method ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { onToggleMethod(method) }.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = selectedMethods.contains(method),
                onCheckedChange = { onToggleMethod(method) },
                colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(method, fontSize = 16.sp)
        }
    }
}

// --- KOMPONEN LANGKAH 6: FITUR MODULAR ---
@Composable
fun Step6ModularFeatures(businessType: BusinessType?) {
    Text("Fitur Toko", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Text("ZoKas telah menyalakan fitur yang sesuai dengan jenis usaha Anda secara otomatis. Anda dapat mengubahnya nanti di menu Pengaturan.", style = MaterialTheme.typography.bodyMedium, color = SlateGrey, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(24.dp))

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            val isTableMode = businessType == BusinessType.KULINER
            Text("Mode Meja Restoran (Aktif: ${if (isTableMode) "Ya" else "Tidak"})", fontWeight = FontWeight.Bold)
            Text(if (isTableMode) "Karena Anda memilih Kuliner, fitur pilih meja otomatis dinyalakan." else "Anda bisa menyalakannya nanti jika ada area dine-in.", fontSize = 12.sp, color = SlateGrey)
        }
    }
}

// --- KOMPONEN LANGKAH 7: SYARAT & KETENTUAN ---
@Composable
fun Step7TermsAndConditions(isAgreed: Boolean, onToggleAgree: (Boolean) -> Unit) {
    Text("Syarat & Ketentuan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(16.dp))

    Card(modifier = Modifier.fillMaxWidth().height(250.dp), colors = CardDefaults.cardColors(containerColor = SlateSurface)) {
        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Kebijakan Privasi ZoKas", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("1. Aplikasi ZoKas menyimpan data Anda secara OFFLINE di perangkat ini.\n\n2. Kami tidak mengambil atau mengirim data transaksi Anda ke server manapun (kecuali Anda mengaktifkan sinkronisasi cloud di masa depan).\n\n3. Anda bertanggung jawab penuh atas keamanan perangkat dan PIN akun Anda.\n\n4. Data pelanggan (jika ada) adalah tanggung jawab pemilik toko.\n\n5. Dengan menyetujui ini, Anda setuju bahwa ZoKas tidak bertanggung jawab atas kerugian yang timbul akibat kelalaian pengguna.", fontSize = 12.sp, color = SlateGreyDark)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onToggleAgree(!isAgreed) }) {
        Checkbox(checked = isAgreed, onCheckedChange = { onToggleAgree(it) }, colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Saya telah membaca dan menyetujui Syarat & Ketentuan serta Kebijakan Privasi.", fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// --- KOMPONEN LANGKAH 8: RINGKASAN ---
@Composable
fun Step8Summary(wizardState: SetupWizardState) {
    Text("Ringkasan Data", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Text("Pastikan data di bawah ini sudah benar sebelum memulai.", style = MaterialTheme.typography.bodyMedium, color = SlateGrey, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(24.dp))

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Jenis Usaha: ${wizardState.businessType?.label ?: "-"}", fontWeight = FontWeight.Bold)
            Text("Nama Toko: ${wizardState.storeName}")
            Text("Admin: ${wizardState.adminName}")
            Text("Kasir: ${wizardState.cashierName}")
            Text("Pembayaran: ${wizardState.paymentMethods.joinToString(", ")}")
        }
    }
}