package com.karyaku.kasir

import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.widget.Toast
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

// ====================================================================
// SESI 4 (#2), SESI 5 & 6 (#3), & TEMA GELAP ADAPTIF — MainActivity.kt:
//   1. Menambahkan ikon Bantuan/Tutorial (?) di TopBar utama dan di
//      DropdownMenu agar semua pengguna (Owner maupun Kasir) dapat
//      membuka TutorialCenterDialog kapan saja dari layar mana pun.
//   2. Menghubungkan ProductFormBottomSheet & PosActionMenuBottomSheet
//      di ProductManagementScreen (Tab Produk) dengan manajemen Resep (BOM)
//      dan Kelola Bahan Baku secara langsung.
//   3. Mengubah seluruh warna keras (EmeraldGreen, AmberAccent, SlateGrey,
//      ErrorRed) di MainActivity menjadi warna adaptif Material 3.
// ====================================================================

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        if (isTablet) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val database = AppDatabase.getDatabase(this)
        val settingsManager = SettingsManager(this)

        setContent {
            CompositionLocalProvider(LocalActivity provides this@MainActivity, LocalActivityResultRegistryOwner provides this@MainActivity) {
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
                                val newLang = if (language == "in") "en" else "in"
                                language = newLang
                                settingsManager.language = newLang
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
    onToggleDarkMode: () -> Unit, onToggleHighContrast: () -> Unit, onChangeUiScale: (Float) -> Unit, onToggleLanguage: () -> Unit
) {
    val context = LocalContext.current
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val posViewModel: PosViewModel = viewModel(factory = PosViewModelFactory(database))

        var staffUserToLogout by remember { mutableStateOf<UserEntity?>(null) }
        var staffMessage by remember { mutableStateOf("") }

        if (!settingsManager.isSetupCompleted) {
            SetupWizardScreen(settingsManager, posViewModel, onSetupComplete = { newAdmin -> posViewModel.initCurrentBranchForUser(newAdmin); onUserChange(newAdmin) })
        } else if (staffUserToLogout != null) {
            LaunchedEffect(Unit) { delay(3000); staffUserToLogout = null; staffMessage = "" }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
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
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else if (currentUser == null) {
            LoginScreen(viewModel = posViewModel, settingsManager = settingsManager, onLoginSuccess = { user ->
                posViewModel.processLoginAttendance(user) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    if (success) {
                        posViewModel.initCurrentBranchForUser(user)
                        if (user.role == "STAFF") { staffMessage = msg; staffUserToLogout = user } else onUserChange(user)
                    }
                }
            })
        } else {
            MainNavigation(
                viewModel = posViewModel, settingsManager = settingsManager, currentUser = currentUser,
                storeName = storeName, storeLogoPath = storeLogoPath,
                onStoreNameChange = onStoreNameChange, onLogoChange = onLogoChange,
                onLogout = { onUserChange(null) },
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
    language: String, onToggleLanguage: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showCfdDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showTutorialDialog by remember { mutableStateOf(false) }
    var posActionTrigger by remember { mutableStateOf<String?>(null) }
    var showBranchManagementDialog by remember { mutableStateOf(false) }
    var showStockTransferDialog by remember { mutableStateOf(false) }
    val isKuliner = settingsManager.businessType.equals("KULINER", ignoreCase = true)

    LaunchedEffect(currentUser.role, selectedTab) {
        if (currentUser.role == "CASHIER" && selectedTab != 0) selectedTab = 0
        if (currentUser.role == "STAFF" && isKuliner && selectedTab != 4 && selectedTab != 0) selectedTab = 4
    }

    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()

    val context = LocalContext.current
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
                if (success) { showSearchDialog = false; viewModel.setSearchQuery("") }
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
            title = { Text(LanguageManager.tr("cari_produk", language)) },
            text = {
                OutlinedTextField(
                    value = query, onValueChange = { query = it; viewModel.setSearchQuery(it) },
                    placeholder = { Text(LanguageManager.tr("ketik_produk", language)) },
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
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text(LanguageManager.tr("tutup", language), color = MaterialTheme.colorScheme.primary)
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

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant else Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        if (logoFile != null && logoFile.exists()) {
                            AsyncImage(model = logoFile, contentDescription = "Store Logo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.logo_zokas),
                                contentDescription = "Logo",
                                modifier = Modifier.fillMaxSize(),
                                colorFilter = if (isDarkMode) ColorFilter.tint(Color.White) else null
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = storeName?.takeIf { it.isNotBlank() } ?: "ZoKas Store",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Tombol Cepat Buka Panduan / Tutorial di TopBar
                    IconButton(onClick = { showTutorialDialog = true }) {
                        Icon(
                            Icons.Default.HelpOutline,
                            contentDescription = "Panduan & Tutorial",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    if (selectedTab == 0) {
                        IconButton(onClick = { showSearchDialog = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showCfdDialog = true }) {
                            Icon(Icons.Default.Person, contentDescription = "Customer Display", tint = MaterialTheme.colorScheme.primary)
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface)
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
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 68.dp, end = 16.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BranchSelectorHeader(
                        viewModel = viewModel,
                        onManageBranchesClick = {
                            if (currentUser.role == "OWNER") showBranchManagementDialog = true
                            else Toast.makeText(context, "Hanya OWNER yang bisa mengelola cabang.", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    val shift = activeShift
                    if (shift != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Shift: ${shift.userName}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
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
                        actionTrigger = posActionTrigger, onActionHandled = { posActionTrigger = null }
                    )
                    1 -> ProductManagementScreen(viewModel = viewModel, settingsManager = settingsManager, currentUser = currentUser)
                    2 -> HistoryScreen(transactions = transactions, viewModel = viewModel, settingsManager = settingsManager, currentUser = currentUser)
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
    val filteredProducts by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val categories by viewModel.uniqueCategories.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()

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

    val context = LocalContext.current

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
                    val variants = productForVariantAction!!.variations.split(",").map { it.split(":")[0].trim() }
                    variants.forEach { vName ->
                        Button(
                            onClick = {
                                variantToProcess = vName
                                if (variantActionType == "RESTOCK") productToRestock = productForVariantAction
                                if (variantActionType == "WASTE") productToWaste = productForVariantAction
                                if (variantActionType == "BARCODE") {
                                    saveBarcodeToGallery(context, productForVariantAction!!)
                                    productForVariantAction = null
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
            OutlinedTextField(
                value = searchQuery, onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Cari nama atau scan barcode...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp), singleLine = true
            )
            PosProductSection(
                products = filteredProducts, categories = categories, selectedCategory = "Semua",
                onCategorySelect = { viewModel.setCategory(it) }, searchQuery = searchQuery, onSearchChange = { viewModel.setSearchQuery(it) },
                onProductClick = { prod -> productToEdit = prod; showProductForm = true },
                onProductLongClick = { prod -> showActionDialogFor = prod },
                onBarcodeScan = { }, onCameraScanClick = { }, searchFocusRequester = remember { FocusRequester() }
            )
        }

        ExtendedFloatingActionButton(
            onClick = { productToEdit = null; showProductForm = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Tambah Produk")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Tambah Produk", fontWeight = FontWeight.Bold)
        }

        if (showProductForm) {
            ProductFormBottomSheet(
                productToEdit = productToEdit,
                onDismiss = { showProductForm = false; productToEdit = null },
                onManageBom = { prod ->
                    productToManageBom = prod
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
                    if (showActionDialogFor!!.variations.isNotBlank()) { productForVariantAction = showActionDialogFor; variantActionType = "RESTOCK" }
                    else { productToRestock = showActionDialogFor }
                    showActionDialogFor = null
                },
                onWaste = {
                    if (showActionDialogFor!!.variations.isNotBlank()) { productForVariantAction = showActionDialogFor; variantActionType = "WASTE" }
                    else { productToWaste = showActionDialogFor }
                    showActionDialogFor = null
                },
                onBom = {
                    productToManageBom = showActionDialogFor
                    showActionDialogFor = null
                },
                onRawMaterial = {
                    showRawMaterialDialog = true
                    showActionDialogFor = null
                },
                onSaveBarcode = {
                    if (showActionDialogFor!!.variations.isNotBlank()) { productForVariantAction = showActionDialogFor; variantActionType = "BARCODE" }
                    else { saveBarcodeToGallery(context, showActionDialogFor!!); showActionDialogFor = null }
                },
                onDelete = {
                    productToDelete = showActionDialogFor
                    showActionDialogFor = null
                }
            )
        }

        if (productToManageBom != null) {
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
            StockOpnameDialog(
                product = productToRestock!!,
                onDismiss = { productToRestock = null },
                onSave = { qty -> viewModel.addStock(productToRestock!!, variantToProcess, qty, currentUser); productToRestock = null; variantToProcess = "" }
            )
        }
        if (productToWaste != null) {
            WasteRecordDialog(
                product = productToWaste!!,
                onDismiss = { productToWaste = null },
                onSave = { qty -> viewModel.recordWaste(productToWaste!!, variantToProcess, qty, currentUser); productToWaste = null; variantToProcess = "" }
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
