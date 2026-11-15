package com.karyaku.kasir

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.AuditLogEntity
import com.karyaku.kasir.data.local.UserEntity
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    isHighContrast: Boolean,
    onToggleHighContrast: () -> Unit,
    uiScale: Float,
    onChangeUiScale: (Float) -> Unit,
    language: String,
    onToggleLanguage: () -> Unit,
    onLogout: () -> Unit,
    auditLogs: List<AuditLogEntity>,
    currentUser: UserEntity,
    viewModel: PosViewModel,
    storeName: String?,
    onStoreNameChange: (String) -> Unit,
    storeLogoPath: String?,
    onLogoChange: (String) -> Unit
) {
    val context = LocalContext.current
    val lang = language

    var storeFooter by remember { mutableStateOf(settingsManager.storeFooter ?: "") }
    var storeAddress by remember { mutableStateOf(settingsManager.storeAddress ?: "") }
    var storePhone by remember { mutableStateOf(settingsManager.storePhone ?: "") }
    var taxPercent by remember { mutableStateOf(settingsManager.taxPercent.toString()) }
    var serviceChargePercent by remember { mutableStateOf(settingsManager.serviceChargePercent.toString()) }
    
    var businessTypeState by remember { mutableStateOf(settingsManager.businessType) }

    var qrisPayload by remember { mutableStateOf(settingsManager.qrisStaticPayload ?: "") }
    var enableQrisAutoDetect by remember { mutableStateOf(settingsManager.enableQrisAutoDetect) }
    var showPaymentMethodsDialog by remember { mutableStateOf(false) }

    var bankName by remember { mutableStateOf(settingsManager.bankName ?: "") }
    var bankAccountNumber by remember { mutableStateOf(settingsManager.bankAccountNumber ?: "") }
    var bankAccountName by remember { mutableStateOf(settingsManager.bankAccountName ?: "") }
    var enableTransferAutoDetect by remember { mutableStateOf(settingsManager.enableTransferAutoDetect) }

    var hhStart by remember { mutableStateOf(settingsManager.happyHourStart) }
    var hhEnd by remember { mutableStateOf(settingsManager.happyHourEnd) }
    var hhDiscount by remember { mutableStateOf(settingsManager.happyHourDiscount.toString()) }
    var maxDiscountCap by remember { mutableStateOf(settingsManager.maxDiscountCap.toString()) }

    var enableMultiCurrency by remember { mutableStateOf(settingsManager.enableMultiCurrency) }
    var exchangeRateUsd by remember { mutableStateOf(settingsManager.exchangeRateUsd.toString()) }
    var isTableModeEnabled by remember { mutableStateOf(settingsManager.isTableModeEnabled) }
    var isBiometricEnabled by remember { mutableStateOf(settingsManager.isBiometricEnabled) }

    var enableOrderTypes by remember { mutableStateOf(settingsManager.enableOrderTypes) }
    var enableWholesale by remember { mutableStateOf(settingsManager.enableWholesale) }
    var enablePreOrder by remember { mutableStateOf(settingsManager.enablePreOrder) }
    var enableMembership by remember { mutableStateOf(settingsManager.enableMembership) }
    var enableSupplier by remember { mutableStateOf(settingsManager.enableSupplier) }
    var enableReservation by remember { mutableStateOf(settingsManager.enableReservation) }
    var enableFinance by remember { mutableStateOf(settingsManager.enableFinance) }

    var enableBirthdayDiscount by remember { mutableStateOf(settingsManager.enableBirthdayDiscount) }
    var birthdayDiscountPercent by remember { mutableStateOf(settingsManager.birthdayDiscountPercent.toFloat()) }

    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    var showChangePinDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var showUserManagementDialog by remember { mutableStateOf(false) }
    var showShiftHistory by remember { mutableStateOf(false) }
    var showRawMaterialDialog by remember { mutableStateOf(false) }
    var showMembershipDialog by remember { mutableStateOf(false) }
    var showSupplierDialog by remember { mutableStateOf(false) }
    var showPODialog by remember { mutableStateOf(false) }
    var showReservationDialog by remember { mutableStateOf(false) }
    var showExpenseDialog by remember { mutableStateOf(false) }
    var showReceivableDialog by remember { mutableStateOf(false) }
    var showPayableDialog by remember { mutableStateOf(false) }
    var showBranchManagementDialog by remember { mutableStateOf(false) }
    var showStockTransferDialog by remember { mutableStateOf(false) }
    var showCustomerManagementDialog by remember { mutableStateOf(false) }
    
    var showAttendanceDialog by remember { mutableStateOf(false) }
    var showPayrollDialog by remember { mutableStateOf(false) }
    var showBiometricDialog by remember { mutableStateOf(false) }

    val users by viewModel.users.collectAsStateWithLifecycle()
    val shifts by viewModel.shifts.collectAsStateWithLifecycle()
    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()
    val memberships by viewModel.memberships.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val reservations by viewModel.reservations.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()

    val currentBranch by viewModel.currentBranch.collectAsStateWithLifecycle()
    val allBranches by viewModel.allBranches.collectAsStateWithLifecycle()

    val printerManager = remember { BluetoothPrinterManager(context) }
    var savedPrinterMac by remember { mutableStateOf(settingsManager.printerMacAddress) }
    var savedKitchenMac by remember { mutableStateOf(settingsManager.kitchenPrinterMacAddress) }
    var showPrinterDialog by remember { mutableStateOf(false) }
    var showKitchenDialog by remember { mutableStateOf(false) }
    var pairedPrinters by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms[Manifest.permission.BLUETOOTH_CONNECT] == true) {
            pairedPrinters = printerManager.getPairedPrinters()
            showPrinterDialog = true
        }
    }
    val kitchenPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms[Manifest.permission.BLUETOOTH_CONNECT] == true) {
            pairedPrinters = printerManager.getPairedPrinters()
            showKitchenDialog = true
        }
    }
    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) {
            if (BackupHelper.backupDatabase(context, uri)) Toast.makeText(context, "Backup berhasil!", Toast.LENGTH_SHORT).show()
            else Toast.makeText(context, "Gagal melakukan backup.", Toast.LENGTH_SHORT).show()
        }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            if (BackupHelper.restoreDatabase(context, uri)) showRestartDialog = true
            else Toast.makeText(context, "Gagal restore database.", Toast.LENGTH_SHORT).show()
        }
    }

    val createExcelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportProductsToExcel(context, uri) { success ->
                if (success) Toast.makeText(context, "Data produk berhasil diekspor ke Excel!", Toast.LENGTH_SHORT).show()
                else Toast.makeText(context, "Gagal ekspor Excel.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val openExcelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { }

            viewModel.importProductsFromExcel(context, uri) { inserts, updates ->
                Toast.makeText(context, "Import Excel Selesai! $inserts Baru, $updates Diperbarui.", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showChangePinDialog) {
        ChangePinDialog(user = currentUser, onDismiss = { showChangePinDialog = false }, onSave = { newPin ->
            viewModel.updatePin(currentUser, newPin); showChangePinDialog = false; Toast.makeText(context, "PIN berhasil diubah!", Toast.LENGTH_SHORT).show()
        })
    }
    if (showUserManagementDialog) {
        UserManagementDialog(
            users = users, currentUser = currentUser, onDismiss = { showUserManagementDialog = false },
            onAddUser = { name, pin, role, salary -> viewModel.addUser(name, pin, role, salary, currentUser) },
            onUpdateUser = { user -> viewModel.updateUser(user, currentUser) },
            onDeleteUser = { user -> viewModel.deleteUser(user, currentUser) }
        )
    }
    if (showBranchManagementDialog) {
        BranchManagementDialog(viewModel = viewModel, currentUser = currentUser, onDismiss = { showBranchManagementDialog = false })
    }
    if (showStockTransferDialog) {
        StockTransferDialog(viewModel = viewModel, currentUser = currentUser, onDismiss = { showStockTransferDialog = false })
    }
    if (showCustomerManagementDialog) {
        CustomerManagementDialog(viewModel = viewModel, currentUser = currentUser, onDismiss = { showCustomerManagementDialog = false })
    }
    if (showShiftHistory) {
        ShiftHistoryDialog(shifts = shifts, onDismiss = { showShiftHistory = false })
    }
    if (showRawMaterialDialog) {
        RawMaterialManagementDialog(rawMaterials = rawMaterials, viewModel = viewModel, onDismiss = { showRawMaterialDialog = false })
    }
    if (showMembershipDialog) {
        MembershipManagementDialog(
            memberships = memberships,
            onAddMembership = { name, price, quota, days -> viewModel.addMembership(name, price, quota, days) },
            onDismiss = { showMembershipDialog = false }
        )
    }
    if (showSupplierDialog) {
        SupplierManagementDialog(
            suppliers = suppliers,
            onAddSupplier = { name, contact, phone, address -> viewModel.addSupplier(name, contact, phone, address) },
            onDismiss = { showSupplierDialog = false }
        )
    }
    if (showPODialog) {
        PurchaseOrderDialog(
            suppliers = suppliers,
            products = products,
            onCreatePO = { supplier, invoice, items, isPaid, updateCost ->
                viewModel.createPurchaseOrder(supplier, invoice, items, isPaid, updateCost) {
                    Toast.makeText(context, "PO Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
                    showPODialog = false
                }
            },
            onDismiss = { showPODialog = false }
        )
    }
    if (showReservationDialog) {
        ReservationManagementDialog(
            reservations = reservations,
            onAddReservation = { name, phone, dateTime, resource, notes -> viewModel.addReservation(name, phone, dateTime, resource, notes) },
            onUpdateStatus = { id, status -> viewModel.updateReservationStatus(id, status) },
            onDeleteReservation = { id -> viewModel.deleteReservation(id) },
            onDismiss = { showReservationDialog = false }
        )
    }
    if (showExpenseDialog) {
        ExpenseManagementDialog(
            expenses = expenses,
            onAddExpense = { category, amount, note -> viewModel.addExpense(category, amount, note, currentUser) },
            onDismiss = { showExpenseDialog = false }
        )
    }
    if (showReceivableDialog) {
        ReceivableDialog(
            transactions = transactions,
            onPay = { tx, cash -> viewModel.payUnpaidTransaction(tx, cash) { Toast.makeText(context, "Bon Lunas!", Toast.LENGTH_SHORT).show() } },
            onDismiss = { showReceivableDialog = false }
        )
    }
    if (showPayableDialog) {
        SupplierDebtDialog(viewModel = viewModel, currentUser = currentUser, onDismiss = { showPayableDialog = false })
    }
    if (showPaymentMethodsDialog) {
        PaymentMethodManagementDialog(
            currentMethods = settingsManager.paymentMethods,
            onSave = { newMethods -> settingsManager.paymentMethods = newMethods; showPaymentMethodsDialog = false },
            onDismiss = { showPaymentMethodsDialog = false }
        )
    }
    if (showPrinterDialog) {
        AlertDialog(
            onDismissRequest = { showPrinterDialog = false },
            title = { Text(LanguageManager.tr("pilih_printer", lang)) },
            text = {
                if (pairedPrinters.isEmpty()) Text(LanguageManager.tr("belum_printer", lang))
                else {
                    LazyColumn {
                        items(pairedPrinters) { device ->
                            TextButton(onClick = { settingsManager.printerMacAddress = device.address; savedPrinterMac = device.address; showPrinterDialog = false }) { Text("${device.name} (${device.address})") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPrinterDialog = false }) { Text(LanguageManager.tr("tutup", lang)) } }
        )
    }
    if (showKitchenDialog) {
        AlertDialog(
            onDismissRequest = { showKitchenDialog = false },
            title = { Text(LanguageManager.tr("pilih_printer_dapur", lang)) },
            text = {
                if (pairedPrinters.isEmpty()) Text(LanguageManager.tr("belum_printer_dapur", lang))
                else {
                    LazyColumn {
                        items(pairedPrinters) { device ->
                            TextButton(onClick = { settingsManager.kitchenPrinterMacAddress = device.address; savedKitchenMac = device.address; showKitchenDialog = false }) { Text("${device.name} (${device.address})") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showKitchenDialog = false }) { Text(LanguageManager.tr("tutup", lang)) } }
        )
    }
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { }, title = { Text("Restore Berhasil") },
            text = { Text("Aplikasi harus ditutup dan dibuka kembali untuk menerapkan data baru.") },
            confirmButton = { Button(onClick = { android.os.Process.killProcess(android.os.Process.myPid()) }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)) { Text("TUTUP APLIKASI") } }
        )
    }
    
    if (showAttendanceDialog) {
        AttendanceDialog(viewModel = viewModel, currentUser = currentUser, onDismiss = { showAttendanceDialog = false })
    }
    if (showPayrollDialog) {
        PayrollManagementDialog(viewModel = viewModel, currentUser = currentUser, onDismiss = { showPayrollDialog = false })
    }
    if (showBiometricDialog) {
        BiometricRegistrationDialog(viewModel = viewModel, currentUser = currentUser, onDismiss = { showBiometricDialog = false })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp).imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageManager.tr("pengaturan", lang), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        item {
            SettingsAppearanceCard(
                isDarkMode = isDarkMode, onToggleDarkMode = onToggleDarkMode,
                uiScale = uiScale, onChangeUiScale = onChangeUiScale,
                language = language, onToggleLanguage = onToggleLanguage
            )
        }

        if (currentUser.role == "OWNER") {
            item {
                SettingsBusinessTypeCard(
                    currentBusinessType = businessTypeState,
                    onBusinessTypeChange = { newType ->
                        businessTypeState = newType
                        settingsManager.businessType = newType
                        val autoTable = newType.equals("KULINER", ignoreCase = true)
                        isTableModeEnabled = autoTable
                        settingsManager.isTableModeEnabled = autoTable
                        Toast.makeText(context, "Tipe usaha diperbarui. Tab & menu akan menyesuaikan saat Anda pindah tab.", Toast.LENGTH_LONG).show()
                    },
                    language = lang
                )
            }
        }

        item {
            SettingsAccountCard(
                currentUser = currentUser,
                isBiometricEnabled = isBiometricEnabled,
                onBiometricToggle = { isChecked -> isBiometricEnabled = isChecked; settingsManager.isBiometricEnabled = isChecked },
                onChangePinClick = { showChangePinDialog = true },
                onManageStaffClick = { showUserManagementDialog = true },
                onShiftHistoryClick = { showShiftHistory = true },
                onRegisterBiometricClick = {
                    if (currentUser.role == "OWNER") {
                        showBiometricDialog = true
                    } else {
                        Toast.makeText(context, "Hanya OWNER yang bisa mendaftarkan biometrik.", Toast.LENGTH_SHORT).show()
                    }
                },
                language = language
            )
        }

        if (currentUser.role == "OWNER") {
            item {
                SettingsBranchManagementCard(
                    currentBranch = currentBranch,
                    totalBranches = allBranches.size,
                    onManageClick = { showBranchManagementDialog = true },
                    onTransferClick = { showStockTransferDialog = true },
                    canTransfer = allBranches.size > 1
                )
            }
        }

        if (currentUser.role == "OWNER") {
            item {
                SettingsCrmCard(
                    enableBirthdayDiscount = enableBirthdayDiscount,
                    onToggleBirthdayDiscount = { isOn ->
                        enableBirthdayDiscount = isOn
                        settingsManager.enableBirthdayDiscount = isOn
                    },
                    birthdayDiscountPercent = birthdayDiscountPercent,
                    onChangeBirthdayPercent = { newVal ->
                        birthdayDiscountPercent = newVal
                        settingsManager.birthdayDiscountPercent = newVal.toDouble()
                    },
                    totalCustomers = customers.size,
                    onManageClick = { showCustomerManagementDialog = true }
                )
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(LanguageManager.tr("kepegawaian_hr", lang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(LanguageManager.tr("kepegawaian_hr_desc", lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showAttendanceDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(LanguageManager.tr("absensi", lang), fontSize = 12.sp, color = Color.White)
                            }
                            Button(
                                onClick = { showPayrollDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.MonetizationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(LanguageManager.tr("gaji_payroll", lang), fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsModuleCard(
                isTableModeEnabled = isTableModeEnabled, 
                onTableModeToggle = { isTableModeEnabled = it; settingsManager.isTableModeEnabled = it },
                enableMultiCurrency = enableMultiCurrency, 
                onMultiCurrencyToggle = { enableMultiCurrency = it; settingsManager.enableMultiCurrency = it },
                exchangeRateUsd = exchangeRateUsd, 
                onExchangeRateChange = { exchangeRateUsd = it; settingsManager.exchangeRateUsd = it.toDoubleOrNull() ?: 15000.0 },
                onRawMaterialClick = { showRawMaterialDialog = true },
                enableOrderTypes = enableOrderTypes,
                onOrderTypesToggle = { enableOrderTypes = it; settingsManager.enableOrderTypes = it },
                enableWholesale = enableWholesale,
                onWholesaleToggle = { enableWholesale = it; settingsManager.enableWholesale = it },
                enablePreOrder = enablePreOrder,
                onPreOrderToggle = { enablePreOrder = it; settingsManager.enablePreOrder = it },
                enableMembership = enableMembership,
                onMembershipToggle = { enableMembership = it; settingsManager.enableMembership = it },
                onManageMembershipClick = { showMembershipDialog = true },
                enableSupplier = enableSupplier,
                onSupplierToggle = { enableSupplier = it; settingsManager.enableSupplier = it },
                onManageSupplierClick = { showSupplierDialog = true },
                onCreatePOClick = { showPODialog = true },
                enableReservation = enableReservation,
                onReservationToggle = { enableReservation = it; settingsManager.enableReservation = it },
                onManageReservationClick = { showReservationDialog = true },
                enableFinance = enableFinance,
                onFinanceToggle = { enableFinance = it; settingsManager.enableFinance = it },
                onManageReceivableClick = { showReceivableDialog = true },
                onManagePayableClick = { showPayableDialog = true },
                onManageExpenseClick = { showExpenseDialog = true },
                language = language
            )
        }

        item {
            SettingsQrisCard(
                qrisPayload = qrisPayload,
                onQrisPayloadChange = { qrisPayload = it; settingsManager.qrisStaticPayload = it },
                enableAutoDetect = enableQrisAutoDetect,
                onToggleAutoDetect = { enableQrisAutoDetect = it; settingsManager.enableQrisAutoDetect = it },
                onManagePaymentMethodsClick = { showPaymentMethodsDialog = true },
                language = language
            )
        }

        item {
            SettingsBankTransferCard(
                bankName = bankName,
                onBankNameChange = { bankName = it; settingsManager.bankName = it },
                accountNumber = bankAccountNumber,
                onAccountNumberChange = { bankAccountNumber = it; settingsManager.bankAccountNumber = it },
                accountName = bankAccountName,
                onAccountNameChange = { bankAccountName = it; settingsManager.bankAccountName = it },
                enableAutoDetect = enableTransferAutoDetect,
                onToggleAutoDetect = { enableTransferAutoDetect = it; settingsManager.enableTransferAutoDetect = it },
                language = language
            )
        }

        item {
            SettingsPrinterCard(
                savedPrinterMac = savedPrinterMac,
                savedKitchenMac = savedKitchenMac,
                onPrinterClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        pairedPrinters = printerManager.getPairedPrinters(); showPrinterDialog = true
                    } else { permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)) }
                },
                onKitchenClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        pairedPrinters = printerManager.getPairedPrinters(); showKitchenDialog = true
                    } else { kitchenPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)) }
                },
                language = language
            )
        }

        item {
            SettingsStoreProfileCard(
                storeName = storeName ?: "",
                onStoreNameChange = onStoreNameChange,
                storeAddress = storeAddress, onStoreAddressChange = { storeAddress = it; settingsManager.storeAddress = it },
                storePhone = storePhone, onStorePhoneChange = { storePhone = it; settingsManager.storePhone = it },
                storeFooter = storeFooter, onStoreFooterChange = { storeFooter = it; settingsManager.storeFooter = it },
                taxPercent = taxPercent, onTaxPercentChange = { taxPercent = it; val tax = it.toDoubleOrNull() ?: 0.0; settingsManager.taxPercent = tax; viewModel.setTaxPercent(tax) },
                serviceChargePercent = serviceChargePercent,
                onServiceChargeChange = {
                    serviceChargePercent = it
                    val sc = it.toDoubleOrNull() ?: 0.0
                    settingsManager.serviceChargePercent = sc
                    viewModel.setServiceChargePercent(sc)
                },
                logoPath = storeLogoPath,
                onLogoChange = onLogoChange,
                language = language
            )
        }

        item {
            SettingsDiscountCard(
                hhStart = hhStart, onHhStartChange = { hhStart = it; settingsManager.happyHourStart = it },
                hhEnd = hhEnd, onHhEndChange = { hhEnd = it; settingsManager.happyHourEnd = it },
                hhDiscount = hhDiscount, onHhDiscountChange = { hhDiscount = it; settingsManager.happyHourDiscount = it.toDoubleOrNull() ?: 0.0 },
                maxDiscountCap = maxDiscountCap, onMaxDiscountCapChange = { maxDiscountCap = it; settingsManager.maxDiscountCap = it.toLongOrNull() ?: 0L },
                language = language
            )
        }

        item {
            SettingsBackupCard(
                onBackupClick = { createBackupLauncher.launch("Zokas_Backup_${System.currentTimeMillis()}.db") },
                onRestoreClick = { restoreBackupLauncher.launch(arrayOf("*/*")) },
                onExportExcelClick = { createExcelLauncher.launch("Zokas_Produk_${System.currentTimeMillis()}.xlsx") },
                onImportExcelClick = { openExcelLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "*/*")) },
                language = language
            )
        }

        item {
            SettingsAuditLogCard(
                auditLogs = auditLogs,
                dateFormat = dateFormat
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) {
                Text(LanguageManager.tr("logout", lang), fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}