package com.karyaku.kasir

import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.fragment.app.FragmentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.karyaku.kasir.data.local.AppDatabase
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.UserEntity
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        if (isTablet) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val database = AppDatabase.getDatabase(this)
        val settingsManager = SettingsManager(this)

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, !isTablet)
        setContent {
            CompositionLocalProvider(LocalActivity provides this@MainActivity, LocalActivityResultRegistryOwner provides this@MainActivity) {
                var isImmersiveMode by rememberSaveable { mutableStateOf(isTablet) }
                SystemImmersiveModeEffect(immersive = isImmersiveMode)
                var isDarkMode by rememberSaveable { mutableStateOf(settingsManager.isDarkMode) }
                var isHighContrast by rememberSaveable { mutableStateOf(settingsManager.isHighContrast) }
                var uiScale by rememberSaveable { mutableStateOf(settingsManager.uiScale) }
                var language by rememberSaveable { mutableStateOf(settingsManager.language) }
                var storeNameState by remember { mutableStateOf(settingsManager.storeName) }
                var storeLogoPathState by remember { mutableStateOf(settingsManager.storeLogoPath) }

                val userSaver = listSaver<UserEntity?, Any>(
                    save = { user -> if (user == null) emptyList() else listOf(user.id, user.name, user.pin, user.role, user.totalCommission, user.branchId) },
                    restore = { list -> if (list.isEmpty()) null else UserEntity(id = list[0] as Long, name = list[1] as String, pin = list[2] as String, role = list[3] as String, totalCommission = list[4] as Long, branchId = if (list.size > 5) list[5] as Long else 1L) }
                )
                var currentUser by rememberSaveable(stateSaver = userSaver) { mutableStateOf<UserEntity?>(null) }

                val currentDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density = currentDensity.density, fontScale = uiScale)) {
                    PosTheme(isDarkMode = isDarkMode, isHighContrast = isHighContrast, language = language) {
                        MainContent(
                            database = database, settingsManager = settingsManager, currentUser = currentUser,
                            isDarkMode = isDarkMode, isHighContrast = isHighContrast, uiScale = uiScale, language = language,
                            storeName = storeNameState, storeLogoPath = storeLogoPathState,
                            onStoreNameChange = { newName -> storeNameState = newName; settingsManager.storeName = newName },
                            onLogoChange = { newPath -> storeLogoPathState = newPath; settingsManager.storeLogoPath = newPath },
                            onUserChange = { currentUser = it },
                            onToggleDarkMode = { isDarkMode = !isDarkMode; settingsManager.isDarkMode = isDarkMode },
                            onToggleHighContrast = { isHighContrast = !isHighContrast; settingsManager.isHighContrast = isHighContrast },
                            onChangeUiScale = { uiScale = it; settingsManager.uiScale = it },
                            onToggleLanguage = {
                                val order = listOf("id", "en", "jv", "su", "mad")
                                val curr = LanguageManager.normalizeLang(language)
                                val next = order[(order.indexOf(curr).coerceAtLeast(0) + 1) % order.size]
                                language = next
                                settingsManager.language = next
                                LanguageManager.setLanguage(this@MainActivity, next)
                                ZokasLocaleManager.setLanguage(this@MainActivity, next)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainContent(
    database: AppDatabase, settingsManager: SettingsManager, currentUser: UserEntity?,
    isDarkMode: Boolean, isHighContrast: Boolean, uiScale: Float, language: String,
    storeName: String?, storeLogoPath: String?,
    onStoreNameChange: (String) -> Unit, onLogoChange: (String) -> Unit, onUserChange: (UserEntity?) -> Unit,
    onToggleDarkMode: () -> Unit, onToggleHighContrast: () -> Unit, onChangeUiScale: (Float) -> Unit, onToggleLanguage: () -> Unit,
    onHideTopHeaderChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        LocalCfdServerManager.restoreReceiverLockState(context)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val posViewModel: PosViewModel = viewModel(factory = PosViewModelFactory(database))
        val allUsers by posViewModel.users.collectAsStateWithLifecycle()

        var staffUserToLogout by remember { mutableStateOf<UserEntity?>(null) }
        var staffMessage by remember { mutableStateOf("") }
        var showConnectCfdDialog by remember { mutableStateOf(false) }
        var cfdHostInput by remember { mutableStateOf("") }

        val cfdScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
            if (result.contents != null && result.contents.startsWith("http")) {
                LocalCfdServerManager.lockAsKioskReceiver(context, result.contents)
                showConnectCfdDialog = false
            } else if (result.contents != null) {
                Toast.makeText(context, "QR Code bukan alamat Layar Pelanggan ZoKas.", Toast.LENGTH_SHORT).show()
            }
        }

        LaunchedEffect(currentUser?.id) {
            if (currentUser != null) {
                posViewModel.initCurrentBranchForUser(currentUser)
            }
        }

        if (showConnectCfdDialog) {
            AlertDialog(
                onDismissRequest = { showConnectCfdDialog = false },
                icon = {
                    Icon(
                        Icons.Default.CastConnected,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text("Hubungkan Sebagai Layar Pelanggan (CFD)", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Jadikan perangkat ini sebagai layar pelanggan Full-Screen (otomatis terkunci PIN Owner setelah terhubung):",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                LocalCfdServerManager.lockAsKioskReceiver(context, LocalCfdServerManager.P2P_NEARBY_URI)
                                showConnectCfdDialog = false
                            },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Hubungkan Langsung (Tanpa Server / Wi-Fi Sama)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val options = ScanOptions().apply {
                                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    setPrompt("Scan QR Layar Pelanggan dari Perangkat Kasir")
                                    setCameraId(0)
                                    setBeepEnabled(false)
                                    setOrientationLocked(true)
                                }
                                cfdScanLauncher.launch(options)
                            },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Scan QR IP Lokal (1 Wi-Fi / Hotspot)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = 2
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = cfdHostInput,
                            onValueChange = { cfdHostInput = it },
                            label = { Text("Atau ketik IP Kasir (cth: 192.168.1.5:8765)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (cfdHostInput.isNotBlank()) {
                                LocalCfdServerManager.lockAsKioskReceiver(context, cfdHostInput)
                                showConnectCfdDialog = false
                            }
                        },
                        enabled = cfdHostInput.isNotBlank(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Hubungkan & Kunci", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConnectCfdDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        if (LocalCfdServerManager.isKioskReceiverLocked) {
            FullScreenKioskCfdReceiverScreen(
                hostUrl = LocalCfdServerManager.connectedHostUrl,
                users = allUsers,
                onUnlockedAndExit = {
                    Toast.makeText(context, "Mode Layar Pelanggan telah dihentikan.", Toast.LENGTH_SHORT).show()
                }
            )
        } else if (!settingsManager.isSetupCompleted) {
            SetupWizardScreen(
                settingsManager = settingsManager,
                viewModel = posViewModel,
                onSetupComplete = { newAdmin ->
                    val registeredStore = settingsManager.storeName?.trim().orEmpty()
                    if (registeredStore.isNotBlank()) {
                        onStoreNameChange(registeredStore)
                    }
                    val registeredLogo = settingsManager.storeLogoPath.orEmpty()
                    if (registeredLogo.isNotBlank()) {
                        onLogoChange(registeredLogo)
                    }
                    posViewModel.initCurrentBranchForUser(newAdmin)
                    onUserChange(newAdmin)
                }
            )
        } else if (staffUserToLogout != null) {
            val isKuliner = settingsManager.businessType.equals("KULINER", ignoreCase = true)
            LaunchedEffect(staffUserToLogout, isKuliner) {
                if (!isKuliner) {
                    delay(3000)
                    staffUserToLogout = null
                    staffMessage = ""
                }
            }
            Box(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(100.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Terima Kasih, ${staffUserToLogout!!.name}!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        staffMessage,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    if (isKuliner) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    staffUserToLogout = null
                                    staffMessage = ""
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Selesai Absen", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    val staffUser = staffUserToLogout
                                    staffUserToLogout = null
                                    staffMessage = ""
                                    onUserChange(staffUser)
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Layar Dapur", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        } else if (currentUser == null) {
            Box(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                LoginScreen(viewModel = posViewModel, settingsManager = settingsManager, onLoginSuccess = { user ->
                val cleanAndEnterApp: (String?) -> Unit = { attendanceMsg ->
                    posViewModel.cart.value.toList().forEach { posViewModel.deleteItem(it.product, it.selectedVariation) }
                    posViewModel.setDiscount(0L)
                    posViewModel.setCustomer(null)
                    posViewModel.applyVoucher("") { _, _ -> }
                    posViewModel.initCurrentBranchForUser(user)
                    if (user.role == "STAFF") {
                        staffMessage = attendanceMsg ?: "Absensi berhasil dicatat."
                        staffUserToLogout = user
                    } else {
                        onUserChange(user)
                    }
                }

                if (settingsManager.enableHrModule || user.role == "STAFF") {
                    posViewModel.processLoginAttendance(user) { success, msg ->
                        if (settingsManager.enableHrModule && msg.isNotBlank()) {
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                        if (success) {
                            cleanAndEnterApp(msg)
                        }
                    }
                } else {
                    cleanAndEnterApp(null)
                }
            })
                OutlinedButton(
                    onClick = { showConnectCfdDialog = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                ) {
                    Icon(
                        Icons.Default.CastConnected,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Mode Layar Pelanggan (CFD)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            MainNavigation(
                viewModel = posViewModel, settingsManager = settingsManager, currentUser = currentUser,
                storeName = storeName, storeLogoPath = storeLogoPath,
                onStoreNameChange = onStoreNameChange, onLogoChange = onLogoChange,
                onHideTopHeaderChange = onHideTopHeaderChange,
                onLogout = {
                    posViewModel.cart.value.toList().forEach { posViewModel.deleteItem(it.product, it.selectedVariation) }
                    posViewModel.setDiscount(0L)
                    posViewModel.setCustomer(null)
                    posViewModel.applyVoucher("") { _, _ -> }
                    onUserChange(null)
                },
                isDarkMode = isDarkMode, isHighContrast = isHighContrast, uiScale = uiScale,
                onToggleDarkMode = onToggleDarkMode, onToggleHighContrast = onToggleHighContrast,
                onChangeUiScale = onChangeUiScale, language = language, onToggleLanguage = onToggleLanguage
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    viewModel: PosViewModel, settingsManager: SettingsManager, currentUser: UserEntity,
    storeName: String?, storeLogoPath: String?, onStoreNameChange: (String) -> Unit, onLogoChange: (String) -> Unit,
    onLogout: () -> Unit, isDarkMode: Boolean, isHighContrast: Boolean, uiScale: Float,
    onToggleDarkMode: () -> Unit, onToggleHighContrast: () -> Unit, onChangeUiScale: (Float) -> Unit,
    language: String, onToggleLanguage: () -> Unit,
    onHideTopHeaderChange: (Boolean) -> Unit = {}
) {
    val isKuliner = settingsManager.businessType.equals("KULINER", ignoreCase = true)
    val defaultHomeTab = if (currentUser.role == "STAFF" && isKuliner) 4 else 0
    var selectedTab by rememberSaveable { mutableStateOf(defaultHomeTab) }
    var showCfdDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showTutorialDialog by remember { mutableStateOf(false) }
    var posActionTrigger by remember { mutableStateOf<String?>(null) }
    var showBranchManagementDialog by remember { mutableStateOf(false) }
    var showStockTransferDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = LocalActivity.current
    var lastBackPressTime by remember { mutableStateOf(0L) }
    BackHandler(enabled = true) {
        if (selectedTab != defaultHomeTab) {
            selectedTab = defaultHomeTab
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000L) {
                activity?.finish()
            } else {
                lastBackPressTime = now
                val msg = if (language == "in") "Tekan sekali lagi untuk keluar aplikasi" else "Press back again to exit"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(currentUser.role, selectedTab) {
        if (currentUser.role == "CASHIER" && selectedTab != 0) selectedTab = 0
        if (currentUser.role == "SUPERVISOR" && selectedTab != 0 && selectedTab != 2 && !(isKuliner && selectedTab == 4)) {
            selectedTab = 0
        }
        if (currentUser.role == "STAFF" && isKuliner && selectedTab != 4) selectedTab = 4
        if (selectedTab == 0 || selectedTab == 1) {
            viewModel.setCategory("Semua")
        }
    }

val allProductsForAlerts by viewModel.products.collectAsStateWithLifecycle()
    val currentBranchIdForAlerts by viewModel.currentBranchId.collectAsStateWithLifecycle()
    val notificationsList by NotificationCenterManager.notificationsFlow.collectAsStateWithLifecycle()
    val categorySettingsMap by NotificationCenterManager.settingsFlow.collectAsStateWithLifecycle()
    var reminderRules by remember { mutableStateOf(CustomReminderManager.loadRules(context)) }
    var dismissedReminderKeys by remember { mutableStateOf(CustomReminderManager.loadDismissedKeys(context)) }
    var showNotificationCenter by remember { mutableStateOf(false) }
    var showReminderRulesDialog by remember { mutableStateOf(false) }
    var showNotificationSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        NotificationCenterManager.ensureInitialized(context)
    }

    val activeReminders = remember(allProductsForAlerts, currentBranchIdForAlerts, reminderRules, dismissedReminderKeys) {
        val branchProds = allProductsForAlerts.filter { it.branchId == currentBranchIdForAlerts }
        CustomReminderManager.evaluateActiveReminders(
            products = branchProds,
            rules = reminderRules,
            dismissedKeys = dismissedReminderKeys
        )
    }

    val enabledBellCats = remember(categorySettingsMap) {
        NotificationCategory.entries.filter { categorySettingsMap[it]?.bellEnabled ?: true }.toSet()
    }
    val unreadNotifCount = remember(notificationsList, enabledBellCats, activeReminders) {
        notificationsList.count { !it.isRead && it.category in enabledBellCats } + activeReminders.size
    }

    if (showNotificationCenter) {
        NotificationAndReminderCenterDialog(
            notifications = notificationsList,
            activeReminders = activeReminders,
            categorySettings = categorySettingsMap,
            onMarkRead = { id -> NotificationCenterManager.markAsRead(context, id) },
            onMarkAllRead = { NotificationCenterManager.markAllAsRead(context) },
            onClearAllNotifications = { NotificationCenterManager.clearAll(context) },
            onDismissReminder = { key ->
                CustomReminderManager.dismissReminder(context, key)
                dismissedReminderKeys = CustomReminderManager.loadDismissedKeys(context)
            },
            onRestockProduct = { prod ->
                viewModel.addStock(prod, "", 5, currentUser)
                Toast.makeText(context, "Restock +5 untuk ${prod.name}", Toast.LENGTH_SHORT).show()
            },
            onPromoProduct = { prod, discountPct ->
                val discountedPrice = ((prod.price * (100 - discountPct)) / 100L).coerceAtLeast(100L)
                viewModel.updateProduct(prod.copy(price = discountedPrice), currentUser.id, currentUser.name)
                Toast.makeText(context, "Promo -$discountPct% diterapkan ke ${prod.name}", Toast.LENGTH_SHORT).show()
            },
            onWasteProduct = { prod ->
                viewModel.recordWaste(prod, "", prod.stock.coerceAtLeast(1), currentUser)
                Toast.makeText(context, "Stok ${prod.name} ditandai waste", Toast.LENGTH_SHORT).show()
            },
            onOpenReminderRulesDialog = {
                showNotificationCenter = false
                showReminderRulesDialog = true
            },
            onOpenNotificationSettingsDialog = {
                showNotificationCenter = false
                showNotificationSettingsDialog = true
            },
            onDismiss = { showNotificationCenter = false }
        )
    }

    if (showReminderRulesDialog) {
        ReminderRuleManagerDialog(
            rules = reminderRules,
            products = allProductsForAlerts.filter { it.branchId == currentBranchIdForAlerts },
            onSaveRules = { updated ->
                CustomReminderManager.saveRules(context, updated)
                reminderRules = updated
            },
            onDismiss = { showReminderRulesDialog = false }
        )
    }

    if (showNotificationSettingsDialog) {
        NotificationPreferencesDialog(
            settingsMap = categorySettingsMap,
            onUpdateSetting = { setting ->
                NotificationCenterManager.updateCategorySetting(context, setting)
            },
            onDismiss = { showNotificationSettingsDialog = false }
        )
    }

    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currentBranch by viewModel.currentBranch.collectAsStateWithLifecycle()

    var presentation by remember { mutableStateOf<CustomerDisplayPresentation?>(null) }
    val displayManager = remember { context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }

    DisposableEffect(Unit) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                val display = displayManager.getDisplay(displayId)
                if (display != null && display.displayId != Display.DEFAULT_DISPLAY) {
                    try {
                        presentation?.dismiss()
                        presentation = CustomerDisplayPresentation(context as androidx.activity.ComponentActivity, display, viewModel, settingsManager)
                        presentation?.show()
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
            override fun onDisplayRemoved(displayId: Int) { presentation?.dismiss(); presentation = null }
            override fun onDisplayChanged(displayId: Int) {}
        }
        displayManager.registerDisplayListener(listener, null)
        val displays = displayManager.displays
        val externalDisplay = displays.find { it.displayId != Display.DEFAULT_DISPLAY }
        if (externalDisplay != null) {
            try {
                presentation = CustomerDisplayPresentation(context as androidx.activity.ComponentActivity, externalDisplay, viewModel, settingsManager)
                presentation?.show()
            } catch (e: Exception) { e.printStackTrace() }
        }
        onDispose { displayManager.unregisterDisplayListener(listener); presentation?.dismiss() }
    }

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.processBarcodeScan(result.contents) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                if (success) {
                    showSearchDialog = false
                    viewModel.setSearchQuery("")
                }
            }
        }
    }

    if (showTutorialDialog) {
        TutorialCenterDialog(
            initialCategory = "SEMUA",
            onDismiss = { showTutorialDialog = false }
        )
    }

    if (showSearchDialog) {
        var query by remember { mutableStateOf(searchQuery) }
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Cari Nama / Kode Unik (SKU)") },
            text = {
                OutlinedTextField(
                    value = query, onValueChange = { query = it; viewModel.setSearchQuery(it) },
                    placeholder = { Text("Ketik nama produk atau Kode Unik (cth: KOP-01)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            val options = ScanOptions().apply { setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES); setPrompt("Scan Barcode Produk"); setCameraId(0); setBeepEnabled(false); setBarcodeImageEnabled(true); setOrientationLocked(true) }
                            barcodeLauncher.launch(options)
                        }) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = "Scan Barcode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (query.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.processBarcodeScan(query.trim()) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    if (success) {
                                        query = ""
                                        viewModel.setSearchQuery("")
                                        showSearchDialog = false
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Tambah Kode", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = { showSearchDialog = false }) {
                        Text(LanguageManager.tr("tutup", language), color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            dismissButton = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = { query = ""; viewModel.setSearchQuery("") }) {
                        Text(LanguageManager.tr("hapus", language), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    val logoFile = remember(storeLogoPath) { storeLogoPath?.let { File(it) } }

    var hideTopHeader by remember { mutableStateOf(false) }
    LaunchedEffect(selectedTab) {
        if (selectedTab != 0) {
            hideTopHeader = false
            onHideTopHeaderChange(false)
        }
    }
    Scaffold(
        topBar = {
            if (!hideTopHeader) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            if (logoFile != null && logoFile.exists()) {
                                AsyncImage(model = logoFile, contentDescription = "Store Logo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.logo_zokas),
                                    contentDescription = "Logo ZoKas",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        val activeStoreTitle = remember(storeName, settingsManager.storeName) {
                            storeName?.takeIf { it.isNotBlank() }
                                ?: settingsManager.storeName?.takeIf { it.isNotBlank() }
                                ?: "ZoKas Store"
                        }
                        LaunchedEffect(activeStoreTitle) {
                            if (storeName.isNullOrBlank() && activeStoreTitle != "ZoKas Store") {
                                onStoreNameChange(activeStoreTitle)
                            }
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = activeStoreTitle,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (selectedTab == 3) {
                                if (currentUser.role == "OWNER") {
                                    BranchSelectorHeader(
                                        viewModel = viewModel,
                                        onManageBranchesClick = { showBranchManagementDialog = true }
                                    )
                                } else {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Store,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = currentBranch?.name ?: "Cabang Utama",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        NotificationBellButton(
                            unreadCount = unreadNotifCount,
                            onClick = { showNotificationCenter = true }
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        IconButton(onClick = { showTutorialDialog = true }, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Default.HelpOutline,
                                contentDescription = zokasText("Panduan & Tutorial", "Guide & Tutorials", language),
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        if (selectedTab == 0) {
                            IconButton(onClick = { showSearchDialog = true }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { showCfdDialog = true }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Person, contentDescription = "Customer Display", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Panduan & Tutorial", fontWeight = FontWeight.SemiBold) },
                                        leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                                        onClick = { showTutorialDialog = true; showMenu = false }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    if (isKuliner) {
                                        DropdownMenuItem(
                                            text = { Text(LanguageManager.tr("gabung_meja", language)) },
                                            leadingIcon = { Icon(Icons.Default.Merge, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                            onClick = { posActionTrigger = "MERGE"; showMenu = false }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(LanguageManager.tr("buka_laci", language)) },
                                        leadingIcon = { Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = { posActionTrigger = "DRAWER"; showMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(LanguageManager.tr("pesanan_parkir", language)) },
                                        leadingIcon = { Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = { posActionTrigger = "HOLD"; showMenu = false }
                                    )
                                    if (currentUser.role == "OWNER") {
                                        DropdownMenuItem(
                                            text = { Text(LanguageManager.tr("kas_masuk_keluar", language)) },
                                            leadingIcon = { Icon(Icons.Default.AccountBox, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                                            onClick = { posActionTrigger = "CASHFLOW"; showMenu = false }
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    if (currentUser.role == "OWNER") {
                                        DropdownMenuItem(
                                            text = { Text("Kelola Cabang") },
                                            leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                            onClick = { showBranchManagementDialog = true; showMenu = false }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Transfer Stok") },
                                            leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                            onClick = { showStockTransferDialog = true; showMenu = false }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Tutup Kasir (Close Harian)") },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                                        onClick = { posActionTrigger = "CLOSE_SHIFT"; showMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(LanguageManager.tr("logout", language), color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = { onLogout(); showMenu = false }
                                    )
                                }
                            }
                        } else if (currentUser.role != "OWNER") {
                            IconButton(onClick = onLogout, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Default.ExitToApp,
                                    contentDescription = "Logout",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Storefront, contentDescription = "Kasir") },
                    label = { Text(LanguageManager.tr("kasir", language)) },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                if (currentUser.role == "OWNER") {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Inventory, contentDescription = "Products") },
                        label = { Text(LanguageManager.tr("produk", language)) },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Assessment, contentDescription = "Reports") },
                        label = { Text(LanguageManager.tr("laporan", language)) },
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 }
                    )
                    if (isKuliner) {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Restaurant, contentDescription = "Kitchen") },
                            label = { Text(LanguageManager.tr("dapur", language)) },
                            selected = selectedTab == 4,
                            onClick = { selectedTab = 4 }
                        )
                    }
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text(LanguageManager.tr("pengaturan", language)) },
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 }
                    )
                } else if (currentUser.role == "SUPERVISOR") {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Assessment, contentDescription = "Reports") },
                        label = { Text(LanguageManager.tr("laporan", language)) },
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 }
                    )
                    if (isKuliner) {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Restaurant, contentDescription = "Kitchen") },
                            label = { Text(LanguageManager.tr("dapur", language)) },
                            selected = selectedTab == 4,
                            onClick = { selectedTab = 4 }
                        )
                    }
                } else if (currentUser.role == "STAFF" && isKuliner) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Restaurant, contentDescription = "Kitchen") },
                        label = { Text(LanguageManager.tr("dapur", language)) },
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            AnimatedContent(targetState = selectedTab, transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(animationSpec = tween(200)) { fullWidth -> fullWidth * direction } + fadeIn(animationSpec = tween(200))) togetherWith (slideOutHorizontally(animationSpec = tween(200)) { fullWidth -> -fullWidth * direction } + fadeOut(animationSpec = tween(200)))
            }, label = "tab_transition") { targetTab ->
                when (targetTab) {
                    0 -> PosScreen(
                        viewModel = viewModel, settingsManager = settingsManager, currentUser = currentUser,
                        onLogout = onLogout, showCfd = showCfdDialog, onDismissCfd = { showCfdDialog = false },
                        actionTrigger = posActionTrigger, onActionHandled = { posActionTrigger = null },
                        onCartScannerModeChange = { active -> hideTopHeader = active; onHideTopHeaderChange(active) }
                    )
                    1 -> ProductManagementScreen(viewModel = viewModel, settingsManager = settingsManager, currentUser = currentUser)
                    2 -> Box(modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)) {
                        HistoryScreen(transactions = transactions, viewModel = viewModel, settingsManager = settingsManager, currentUser = currentUser)
                    }
                    3 -> SettingsScreen(
                        settingsManager = settingsManager, isDarkMode = isDarkMode, onToggleDarkMode = onToggleDarkMode,
                        isHighContrast = isHighContrast, onToggleHighContrast = onToggleHighContrast,
                        uiScale = uiScale, onChangeUiScale = onChangeUiScale, language = language,
                        onToggleLanguage = onToggleLanguage, onLogout = onLogout, auditLogs = auditLogs,
                        currentUser = currentUser, viewModel = viewModel, storeName = storeName,
                        onStoreNameChange = onStoreNameChange, storeLogoPath = storeLogoPath, onLogoChange = onLogoChange
                    )
                    4 -> if (isKuliner) KitchenScreen(viewModel = viewModel)
                }
            }
            if (showBranchManagementDialog) {
                BranchManagementDialog(viewModel = viewModel, currentUser = currentUser, onDismiss = { showBranchManagementDialog = false })
            }
            if (showStockTransferDialog) {
                StockTransferDialog(viewModel = viewModel, currentUser = currentUser, onDismiss = { showStockTransferDialog = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductManagementScreen(viewModel: PosViewModel, settingsManager: SettingsManager, currentUser: UserEntity) {
    val context = LocalContext.current
    val allProducts by viewModel.products.collectAsStateWithLifecycle()
    val vmFilteredProducts by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val currentBranchId by viewModel.currentBranchId.collectAsStateWithLifecycle()
    val categories by viewModel.uniqueCategories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()

    var showProactiveGuide by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!ProactiveGuideManager.hasSeenGuide(context, ProactiveGuideManager.KEY_PRODUCT_BOM)) {
            showProactiveGuide = true
        }
    }

    val filteredProducts = remember(allProducts, vmFilteredProducts, currentBranchId, selectedCategory, searchQuery) {
        if (searchQuery.isBlank()) {
            vmFilteredProducts
        } else {
            val q = searchQuery.trim()
            allProducts.filter { prod ->
                prod.branchId == currentBranchId &&
                    (selectedCategory == "Semua" || prod.category.equals(selectedCategory, ignoreCase = true)) &&
                    (matchesProductSearch(prod, q) || VariantBarcodeSearchHelper.matchesVariantBarcode(prod, q))
            }.sortedWith(
                compareByDescending<ProductEntity> {
                    extractProductSku(it.barcode).equals(q, ignoreCase = true) ||
                        VariantBarcodeSearchHelper.extractAllVariantBarcodes(it.variations).any { b -> b.equals(q, ignoreCase = true) }
                }.thenByDescending {
                    extractProductSku(it.barcode).startsWith(q, ignoreCase = true)
                }.thenBy { it.name }
            )
        }
    }

    var showProductForm by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<ProductEntity?>(null) }
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }
    var showActionDialogFor by remember { mutableStateOf<ProductEntity?>(null) }

    var productForVariantAction by remember { mutableStateOf<ProductEntity?>(null) }
    var variantActionType by remember { mutableStateOf("") }
    var variantToProcess by remember { mutableStateOf("") }

    var productToRestock by remember { mutableStateOf<ProductEntity?>(null) }
    var productToWaste by remember { mutableStateOf<ProductEntity?>(null) }
    var productToManageBom by remember { mutableStateOf<ProductEntity?>(null) }
    var showRawMaterialDialog by remember { mutableStateOf(false) }

    val users by viewModel.users.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    var isBatchDeleteMode by remember { mutableStateOf(false) }
    val selectedBatchIds = remember { mutableStateListOf<Long>() }
    var showBatchDeleteAuth by remember { mutableStateOf(false) }
    var showStockAuditDialog by remember { mutableStateOf(false) }

    if (showStockAuditDialog) {
        StockAuditDialog(
            products = allProducts.filter { it.branchId == currentBranchId },
            viewModel = viewModel,
            currentUser = currentUser,
            onDismiss = { showStockAuditDialog = false }
        )
    }

    if (showBatchDeleteAuth && selectedBatchIds.isNotEmpty()) {
        OwnerSecurityAuthDialog(
            title = "Hapus Massal ${selectedBatchIds.size} Produk?",
            subtitle = "Semua produk yang dipilih akan dihapus permanen. Verifikasi PIN Owner atau Sidik Jari untuk melanjutkan.",
            users = users,
            activity = activity,
            onSuccess = {
                val idsToDelete = selectedBatchIds.toSet()
                val targets = allProducts.filter { it.id in idsToDelete }
                targets.forEach { prod ->
                    viewModel.deleteProduct(prod, currentUser.id, currentUser.name)
                }
                Toast.makeText(context, "${targets.size} produk berhasil dihapus massal.", Toast.LENGTH_SHORT).show()
                selectedBatchIds.clear()
                isBatchDeleteMode = false
                showBatchDeleteAuth = false
            },
            onDismiss = { showBatchDeleteAuth = false }
        )
    }

    if (showProactiveGuide) {
        ProactiveGuideDialog(
            featureKey = ProactiveGuideManager.KEY_PRODUCT_BOM,
            onDismiss = { showProactiveGuide = false }
        )
    }

    if (productForVariantAction != null) {
        AlertDialog(
            onDismissRequest = { productForVariantAction = null },
            title = { Text("Pilih Varian", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Produk ${productForVariantAction!!.name} memiliki beberapa varian. Pilih varian untuk ${variantActionType.lowercase()}:",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val variants = productForVariantAction!!.variations
                        .replace("|", ",")
                        .split(",")
                        .map { it.split(":")[0].trim() }
                        .filter { it.isNotEmpty() }
                    variants.forEach { vName ->
                        Button(
                            onClick = {
                                variantToProcess = vName
                                val targetProd = productForVariantAction
                                productForVariantAction = null
                                if (variantActionType == "RESTOCK") productToRestock = targetProd
                                if (variantActionType == "WASTE") productToWaste = targetProd
                                if (variantActionType == "BARCODE" && targetProd != null) {
                                    saveBarcodeToGallery(context, targetProd)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text(vName) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { productForVariantAction = null }) {
                    Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val productBarcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
                    if (result.contents != null) {
                        viewModel.setSearchQuery(result.contents.trim())
                    }
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = {
                        Text(
                            text = zokasText("Cari nama, Kode Unik (SKU), atau barcode...", "Search name, SKU, or barcode...", settingsManager.language),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Hapus", modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(
                                onClick = {
                                    val options = ScanOptions().apply {
                                        setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                                        setPrompt("Scan Barcode Produk")
                                        setCameraId(0)
                                        setBeepEnabled(false)
                                        setOrientationLocked(true)
                                    }
                                    productBarcodeLauncher.launch(options)
                                }
                            ) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan Barcode",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                var showSimpleExcelDialog by remember { mutableStateOf(false) }
                if (showSimpleExcelDialog) {
                    SimpleProductExcelDialog(
                        products = allProducts.filter { it.branchId == currentBranchId },
                        onImportRows = { rows ->
                            rows.forEach { r ->
                                val packedCode = packSkuAndBarcode(r.sku, r.barcode, r.unit)
                                val varStr = if (r.variantName.isNotBlank()) {
                                    "${r.variantName}:${r.variantPrice}:${r.costPrice}:${r.variantStock}:1:${r.variantBarcode}"
                                } else {
                                    ""
                                }
                                viewModel.addProduct(
                                    r.name,
                                    r.sellPrice,
                                    r.stock,
                                    r.category,
                                    r.costPrice,
                                    packedCode,
                                    varStr,
                                    0L,
                                    0L,
                                    false,
                                    false,
                                    "",
                                    0L,
                                    0,
                                    currentUser.id,
                                    currentUser.name
                                )
                            }
                            Toast.makeText(context, "${rows.size} produk berhasil diimpor.", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { showSimpleExcelDialog = false }
                    )
                }

                if (filteredProducts.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showStockAuditDialog = true },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FactCheck,
                                contentDescription = "Audit Stok",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = zokasText("Audit Stok", "Stock Audit", settingsManager.language),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        OutlinedButton(
                            onClick = { showSimpleExcelDialog = true },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                                contentColor = MaterialTheme.colorScheme.tertiary
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = "Excel Simpel",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = zokasText("Excel Simpel", "Simple Excel", settingsManager.language),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                isBatchDeleteMode = !isBatchDeleteMode
                                selectedBatchIds.clear()
                            },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isBatchDeleteMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isBatchDeleteMode) MaterialTheme.colorScheme.error.copy(alpha = 0.14f) else Color.Transparent,
                                contentColor = if (isBatchDeleteMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (isBatchDeleteMode) Icons.Default.Close else Icons.Default.Checklist,
                                contentDescription = "Hapus Massal",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isBatchDeleteMode) zokasText("Batal", "Cancel", settingsManager.language) else zokasText("Hapus", "Delete", settingsManager.language),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            if (isBatchDeleteMode) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${selectedBatchIds.size} dari ${filteredProducts.size} produk dipilih",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = {
                                if (selectedBatchIds.size == filteredProducts.size) {
                                    selectedBatchIds.clear()
                                } else {
                                    selectedBatchIds.clear()
                                    selectedBatchIds.addAll(filteredProducts.map { it.id })
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(
                                text = if (selectedBatchIds.size == filteredProducts.size) "Batal Semua" else "Pilih Semua",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 76.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { prod ->
                        val isChecked = selectedBatchIds.contains(prod.id)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) selectedBatchIds.remove(prod.id)
                                    else selectedBatchIds.add(prod.id)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChecked)
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isChecked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (!selectedBatchIds.contains(prod.id)) selectedBatchIds.add(prod.id)
                                        } else {
                                            selectedBatchIds.remove(prod.id)
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prod.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val sku = extractProductSku(prod.barcode)
                                    Text(
                                        text = buildString {
                                            if (sku.isNotEmpty()) append("[$sku] ")
                                            append("${prod.category} • Stok: ${prod.stock}")
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = formatRupiah(prod.price),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            } else {
                PosProductSection(
                    products = filteredProducts,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelect = { viewModel.setCategory(it) },
                    searchQuery = searchQuery,
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onProductClick = { prod -> productToEdit = prod; showProductForm = true },
                    onProductLongClick = { prod -> showActionDialogFor = prod },
                    onBarcodeScan = { },
                    onCameraScanClick = { },
                    searchFocusRequester = remember { FocusRequester() }
                )
            }
        }

        if (isBatchDeleteMode) {
            ExtendedFloatingActionButton(
                onClick = {
                    if (selectedBatchIds.isEmpty()) {
                        Toast.makeText(context, "Pilih minimal 1 produk terlebih dahulu.", Toast.LENGTH_SHORT).show()
                    } else {
                        showBatchDeleteAuth = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Hapus Terpilih")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Hapus Terpilih (${selectedBatchIds.size})", fontWeight = FontWeight.Bold)
            }
        } else {
            ExtendedFloatingActionButton(
                onClick = { productToEdit = null; showProductForm = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Produk")
                Spacer(modifier = Modifier.width(6.dp))
                Text(zokasText("Tambah Produk", "Add Product", settingsManager.language), fontWeight = FontWeight.Bold)
            }
        }

        if (showProductForm) {
            ProductFormBottomSheet(
                productToEdit = productToEdit,
                onDismiss = { showProductForm = false; productToEdit = null },
                onManageBom = { prod ->
                    if (settingsManager.isBomModuleEnabled) {
                        productToManageBom = prod
                    } else {
                        Toast.makeText(context, "Modul Resep / BOM sedang nonaktif.", Toast.LENGTH_SHORT).show()
                    }
                },
                onManageRawMaterial = {
                    showRawMaterialDialog = true
                },
                onSave = { name, price, stock, category, costPrice, barcode, variations, commission, expiry, isService, isCombo, imagePath, wholesalePrice, wholesaleMinQty ->
                    if (productToEdit == null) viewModel.addProduct(name, price, stock, category, costPrice, barcode, variations, commission, expiry, isService, isCombo, imagePath, wholesalePrice, wholesaleMinQty, currentUser.id, currentUser.name)
                    else viewModel.updateProduct(productToEdit!!.copy(name = name, price = price, stock = stock, category = category, costPrice = costPrice, barcode = barcode, variations = variations, commissionPerItem = commission, expiryDate = expiry, isService = isService, isCombo = isCombo, imagePath = imagePath, wholesalePrice = wholesalePrice, wholesaleMinQty = wholesaleMinQty), currentUser.id, currentUser.name)
                    showProductForm = false; productToEdit = null
                }
            )
        }

        if (showActionDialogFor != null) {
            PosActionMenuBottomSheet(
                product = showActionDialogFor!!,
                onDismiss = { showActionDialogFor = null },
                onEdit = { productToEdit = showActionDialogFor; showProductForm = true; showActionDialogFor = null },
                onRestock = {
                    val target = showActionDialogFor!!
                    showActionDialogFor = null
                    if (target.variations.isNotBlank() && !target.variations.contains("|")) {
                        productForVariantAction = target
                        variantActionType = "RESTOCK"
                    } else {
                        variantToProcess = ""
                        productToRestock = target
                    }
                },
                onWaste = {
                    val target = showActionDialogFor!!
                    showActionDialogFor = null
                    if (target.variations.isNotBlank() && !target.variations.contains("|")) {
                        productForVariantAction = target
                        variantActionType = "WASTE"
                    } else {
                        variantToProcess = ""
                        productToWaste = target
                    }
                },
                onBom = if (settingsManager.isBomModuleEnabled) {
                    {
                        productToManageBom = showActionDialogFor
                        showActionDialogFor = null
                    }
                } else {
                    {
                        Toast.makeText(context, "Aktifkan Modul Resep / BOM di Pengaturan terlebih dahulu.", Toast.LENGTH_SHORT).show()
                        showActionDialogFor = null
                    }
                },
                onRawMaterial = {
                    showRawMaterialDialog = true
                    showActionDialogFor = null
                },
                onSaveBarcode = {
                    val target = showActionDialogFor!!
                    showActionDialogFor = null
                    if (target.variations.isNotBlank() && !target.variations.contains("|")) {
                        productForVariantAction = target
                        variantActionType = "BARCODE"
                    } else {
                        saveBarcodeToGallery(context, target)
                    }
                },
                onDelete = {
                    productToDelete = showActionDialogFor
                    showActionDialogFor = null
                }
            )
        }

        if (settingsManager.isBomModuleEnabled && productToManageBom != null) {
            BomManagementDialog(
                product = productToManageBom!!,
                rawMaterials = rawMaterials,
                viewModel = viewModel,
                onDismiss = { productToManageBom = null }
            )
        }

        if (showRawMaterialDialog) {
            RawMaterialManagementDialog(
                rawMaterials = rawMaterials,
                viewModel = viewModel,
                onDismiss = { showRawMaterialDialog = false }
            )
        }

        if (productToRestock != null) {
            val targetRestock = productToRestock!!
            val packMult = remember(targetRestock, variantToProcess) {
                getVariantPackMultiplier(targetRestock.variations, variantToProcess)
            }
            StockOpnameDialog(
                product = if (variantToProcess.isNotBlank()) targetRestock.copy(name = "${targetRestock.name} ($variantToProcess)") else targetRestock,
                onDismiss = { productToRestock = null; variantToProcess = "" },
                onSave = { qty ->
                    if (qty > 0) {
                        val totalSmallest = qty * packMult
                        viewModel.addStock(targetRestock, variantToProcess, totalSmallest, currentUser)
                        Toast.makeText(context, "Stok berhasil ditambahkan (+$totalSmallest)", Toast.LENGTH_SHORT).show()
                    }
                    productToRestock = null
                    variantToProcess = ""
                }
            )
        }
        if (productToWaste != null) {
            val targetWaste = productToWaste!!
            val packMult = remember(targetWaste, variantToProcess) {
                getVariantPackMultiplier(targetWaste.variations, variantToProcess)
            }
            val maxAvail = remember(targetWaste, variantToProcess) {
                if (variantToProcess.isNotBlank() && targetWaste.variations.isNotBlank() && !targetWaste.variations.contains("|")) {
                    val part = targetWaste.variations.split(",").find {
                        it.split(":").firstOrNull()?.trim().equals(variantToProcess, ignoreCase = true)
                    }
                    val vStockStr = part?.split(":")?.getOrNull(3)?.trim()
                    if (!vStockStr.isNullOrEmpty()) vStockStr.toIntOrNull() ?: targetWaste.stock else targetWaste.stock
                } else {
                    targetWaste.stock
                }
            }
            WasteRecordDialog(
                product = if (variantToProcess.isNotBlank()) targetWaste.copy(name = "${targetWaste.name} ($variantToProcess)", stock = maxAvail) else targetWaste,
                onDismiss = { productToWaste = null; variantToProcess = "" },
                onSave = { qty ->
                    if (qty > 0 && qty <= maxAvail) {
                        val totalSmallest = qty * packMult
                        viewModel.recordWaste(targetWaste, variantToProcess, totalSmallest, currentUser)
                        Toast.makeText(context, "Barang rusak tercatat (-$totalSmallest)", Toast.LENGTH_SHORT).show()
                        productToWaste = null
                        variantToProcess = ""
                    } else {
                        Toast.makeText(context, "Jumlah melebihi stok tersedia ($maxAvail)!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        if (productToDelete != null) {
            DeleteProductConfirmDialog(
                product = productToDelete!!,
                onDelete = { viewModel.deleteProduct(productToDelete!!, currentUser.id, currentUser.name); productToDelete = null },
                onDismiss = { productToDelete = null }
            )
        }
    }
}


@Composable
fun SystemImmersiveModeEffect(immersive: Boolean) {
    val context = LocalContext.current
    DisposableEffect(immersive, context) {
        val activity = context as? android.app.Activity
        val window = activity?.window
        val decorView = window?.decorView
        val controller = if (window != null && decorView != null) {
            WindowCompat.getInsetsController(window, decorView)
        } else null

        fun applyBarPolicy() {
            if (immersive) {
                controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller?.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller?.show(WindowInsetsCompat.Type.statusBars())
                controller?.show(WindowInsetsCompat.Type.navigationBars())
            }
        }

        applyBarPolicy()

        val focusListener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                applyBarPolicy()
            }
        }
        decorView?.viewTreeObserver?.addOnWindowFocusChangeListener(focusListener)

        onDispose {
            decorView?.viewTreeObserver?.removeOnWindowFocusChangeListener(focusListener)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
