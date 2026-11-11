package com.karyaku.kasir

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.karyaku.kasir.data.local.AppDatabase
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.UserEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = AppDatabase.getDatabase(this)
        val settingsManager = SettingsManager(this)

        // AUTO-BACKUP HARIAN
        val currentTime = System.currentTimeMillis()
        if (currentTime - settingsManager.lastAutoBackup > 24 * 60 * 60 * 1000) {
            try {
                val dbFile = getDatabasePath("pos_database")
                val backupDir = File(getExternalFilesDir(null), "AutoBackups")
                if (!backupDir.exists()) backupDir.mkdirs()
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val backupFile = File(backupDir, "autobackup_${dateFormat.format(Date())}.db")
                
                if (dbFile.exists()) {
                    dbFile.copyTo(backupFile, overwrite = true)
                    settingsManager.lastAutoBackup = currentTime
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        setContent {
            // SUNTIKKAN LocalActivity KE SELURUH UI
            CompositionLocalProvider(
                LocalActivity provides this@MainActivity,
                LocalActivityResultRegistryOwner provides this@MainActivity
            ) {
                var isDarkMode by rememberSaveable { mutableStateOf(settingsManager.isDarkMode) }
                var isHighContrast by rememberSaveable { mutableStateOf(settingsManager.isHighContrast) }
                var uiScale by rememberSaveable { mutableStateOf(settingsManager.uiScale) }
                var language by rememberSaveable { mutableStateOf(settingsManager.language) }
                
                val userSaver = listSaver<UserEntity?, Any>(
                    save = { user -> 
                        if (user == null) emptyList() 
                        else listOf(user.id, user.name, user.pin, user.role, user.totalCommission) 
                    },
                    restore = { list -> 
                        if (list.isEmpty()) null 
                        else UserEntity(list[0] as Long, list[1] as String, list[2] as String, list[3] as String, list[4] as Long) 
                    }
                )
                var currentUser by rememberSaveable(stateSaver = userSaver) { mutableStateOf<UserEntity?>(null) }

                val currentDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = currentDensity.density, fontScale = uiScale)
                ) {
                    PosTheme(
                        isDarkMode = isDarkMode,
                        isHighContrast = isHighContrast,
                        language = language
                    ) {
                        MainContent(
                            database = database,
                            settingsManager = settingsManager,
                            currentUser = currentUser,
                            isDarkMode = isDarkMode,
                            isHighContrast = isHighContrast,
                            uiScale = uiScale,
                            language = language,
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
    database: AppDatabase,
    settingsManager: SettingsManager,
    currentUser: UserEntity?,
    isDarkMode: Boolean,
    isHighContrast: Boolean,
    uiScale: Float,
    language: String,
    onUserChange: (UserEntity?) -> Unit,
    onToggleDarkMode: () -> Unit,
    onToggleHighContrast: () -> Unit,
    onChangeUiScale: (Float) -> Unit,
    onToggleLanguage: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val posViewModel: PosViewModel = viewModel(
            factory = PosViewModelFactory(
                database.productDao(),
                database.transactionDao(),
                database.userDao(),
                database.cashFlowDao(),
                database.customerDao(),
                database.transactionItemDao(),
                database.voucherDao(),
                database.holdOrderDao()
            )
        )

        if (!settingsManager.isSetupCompleted) {
            SetupWizardScreen(
                settingsManager = settingsManager,
                viewModel = posViewModel,
                onSetupComplete = { newAdmin -> onUserChange(newAdmin) }
            )
        } else if (currentUser == null) {
            LoginScreen(
                viewModel = posViewModel, 
                settingsManager = settingsManager, 
                onLoginSuccess = { onUserChange(it) }
            )
        } else {
            MainNavigation(
                viewModel = posViewModel,
                settingsManager = settingsManager,
                currentUser = currentUser,
                onLogout = { onUserChange(null) },
                isDarkMode = isDarkMode,
                isHighContrast = isHighContrast,
                uiScale = uiScale,
                onToggleDarkMode = onToggleDarkMode,
                onToggleHighContrast = onToggleHighContrast,
                onChangeUiScale = onChangeUiScale,
                language = language,
                onToggleLanguage = onToggleLanguage
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    viewModel: PosViewModel,
    settingsManager: SettingsManager,
    currentUser: UserEntity,
    onLogout: () -> Unit,
    isDarkMode: Boolean,
    isHighContrast: Boolean,
    uiScale: Float,
    onToggleDarkMode: () -> Unit,
    onToggleHighContrast: () -> Unit,
    onChangeUiScale: (Float) -> Unit,
    language: String,
    onToggleLanguage: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var isOnline by remember { mutableStateOf(false) }
    var showCfd by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    // Network State Listener
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { isOnline = true }
            override fun onLost(network: android.net.Network) { isOnline = false }
        }
        cm.registerDefaultNetworkCallback(callback)
        isOnline = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        onDispose { cm.unregisterNetworkCallback(callback) }
    }

    // BARCODE SCANNER LAUNCHER UNTUK DIALOG PENCARIAN
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

    // Dialog Pencarian
    if (showSearchDialog) {
        var query by remember { mutableStateOf(searchQuery) }
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text(LanguageManager.tr("cari_produk", language)) },
            text = {
                OutlinedTextField(
                    value = query,
                    onValueChange = { 
                        query = it
                        viewModel.setSearchQuery(it)
                    },
                    placeholder = { Text(LanguageManager.tr("ketik_produk", language)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            val options = ScanOptions()
                            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                            options.setPrompt("Scan Barcode Produk")
                            options.setCameraId(0)
                            options.setBeepEnabled(false)
                            options.setBarcodeImageEnabled(true)
                            options.setOrientationLocked(false)
                            barcodeLauncher.launch(options)
                        }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode", tint = EmeraldGreen)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = { 
                TextButton(onClick = { showSearchDialog = false }) { Text(LanguageManager.tr("tutup", language)) } 
            },
            dismissButton = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = { 
                        query = ""
                        viewModel.setSearchQuery("")
                    }) { Text(LanguageManager.tr("hapus", language), color = ErrorRed) }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(MaterialTheme.colorScheme.background),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_zokas),
                    contentDescription = "Logo",
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = settingsManager.storeName ?: "ZoKas Store",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(if (isOnline) EmeraldGreen else ErrorRed, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isOnline) "Online" else "Offline",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                IconButton(onClick = { showSearchDialog = true }) { 
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary) 
                }
                IconButton(onClick = { showCfd = true }) { 
                    Icon(Icons.Default.Person, contentDescription = "Customer Display", tint = MaterialTheme.colorScheme.primary) 
                }
                
                // BUNGKUS DENGAN BOX AGAR MENU TIDAK MELEBAR KE KIRI
                Box {
                    IconButton(onClick = { showMenu = true }) { 
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface) 
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text(LanguageManager.tr("gabung_meja", language)) }, leadingIcon = { Icon(Icons.Default.Merge, contentDescription = null, tint = EmeraldGreen) }, onClick = { showMenu = false })
                        DropdownMenuItem(text = { Text(LanguageManager.tr("buka_laci", language)) }, leadingIcon = { Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = EmeraldGreen) }, onClick = { viewModel.logCashDrawerOpen(currentUser); showMenu = false })
                        DropdownMenuItem(text = { Text(LanguageManager.tr("pesanan_parkir", language)) }, leadingIcon = { Icon(Icons.Default.List, contentDescription = null, tint = EmeraldGreen) }, onClick = { showMenu = false })
                        DropdownMenuItem(text = { Text(LanguageManager.tr("kas_masuk_keluar", language)) }, leadingIcon = { Icon(Icons.Default.AccountBox, contentDescription = null, tint = AmberAccent) }, onClick = { showMenu = false })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(LanguageManager.tr("logout", language), color = ErrorRed) }, leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = ErrorRed) }, onClick = { onLogout(); showMenu = false })
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Storefront, contentDescription = "Cashier") },
                    label = { Text(LanguageManager.tr("kasir", language)) },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Inventory, contentDescription = "Products") },
                    label = { Text(LanguageManager.tr("produk", language)) },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Assessment, contentDescription = "Reports") },
                    label = { Text(LanguageManager.tr("laporan", language)) },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text(LanguageManager.tr("pengaturan", language)) },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(animationSpec = tween(200)) { fullWidth -> fullWidth * direction } + fadeIn(animationSpec = tween(200))) togetherWith
                    (slideOutHorizontally(animationSpec = tween(200)) { fullWidth -> -fullWidth * direction } + fadeOut(animationSpec = tween(200)))
                }, label = "tab_transition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> PosScreen(
                        viewModel = viewModel, 
                        settingsManager = settingsManager, 
                        currentUser = currentUser, 
                        onLogout = onLogout, 
                        showCfd = showCfd, 
                        onDismissCfd = { showCfd = false }
                    )
                    1 -> ProductManagementScreen(viewModel = viewModel, settingsManager = settingsManager, currentUser = currentUser)
                    2 -> HistoryScreen(transactions = transactions, viewModel = viewModel, settingsManager = settingsManager, currentUser = currentUser)
                    3 -> SettingsScreen(
                        settingsManager = settingsManager,
                        isDarkMode = isDarkMode, onToggleDarkMode = onToggleDarkMode,
                        isHighContrast = isHighContrast, onToggleHighContrast = onToggleHighContrast,
                        uiScale = uiScale, onChangeUiScale = onChangeUiScale,
                        language = language, onToggleLanguage = onToggleLanguage,
                        onLogout = onLogout, auditLogs = auditLogs, currentUser = currentUser, viewModel = viewModel
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductManagementScreen(viewModel: PosViewModel, settingsManager: SettingsManager, currentUser: UserEntity) {
    val filteredProducts by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val categories by viewModel.uniqueCategories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    
    var showProductForm by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<ProductEntity?>(null) }
    var showActionDialogFor by remember { mutableStateOf<ProductEntity?>(null) }
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }
    var productToRestock by remember { mutableStateOf<ProductEntity?>(null) }
    var productToWaste by remember { mutableStateOf<ProductEntity?>(null) }
    var productToManageBom by remember { mutableStateOf<ProductEntity?>(null) }

    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()
    val searchFocusRequester = remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize()) {
        ProductCatalog(
            products = filteredProducts, 
            categories = categories, 
            selectedCategory = selectedCategory, 
            onCategorySelect = { viewModel.setCategory(it) }, 
            onProductClick = { 
                productToEdit = it
                showProductForm = true
            }, 
            onProductLongClick = { showActionDialogFor = it }, 
            searchFocusRequester = searchFocusRequester,
            lang = settingsManager.language
        )

        FloatingActionButton(
            onClick = { productToEdit = null; showProductForm = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = EmeraldGreen
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Product", tint = Color.White)
        }

        if (showProductForm) {
            ProductFormBottomSheet(
                productToEdit = productToEdit, 
                onDismiss = { showProductForm = false; productToEdit = null }, 
                onSave = { name, price, stock, category, costPrice, barcode, variations, commission, expiry, isService, isCombo, imagePath ->
                    if (productToEdit == null) viewModel.addProduct(name, price, stock, category, costPrice, barcode, variations, commission, expiry, isService, isCombo, imagePath, currentUser.id, currentUser.name)
                    else viewModel.updateProduct(productToEdit!!.copy(name = name, price = price, stock = stock, category = category, costPrice = costPrice, barcode = barcode, variations = variations, commissionPerItem = commission, expiryDate = expiry, isService = isService, isCombo = isCombo, imagePath = imagePath), currentUser.id, currentUser.name)
                    showProductForm = false; productToEdit = null
                }
            )
        }

        if (showActionDialogFor != null) {
            ModalBottomSheet(
                onDismissRequest = { showActionDialogFor = null },
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(modifier = Modifier.padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
                    Text(text = showActionDialogFor!!.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                    ActionMenuItem(icon = Icons.Default.Edit, title = LanguageManager.tr("edit_produk", settingsManager.language), subtitle = "Change details", color = EmeraldGreen, onClick = { productToEdit = showActionDialogFor; showProductForm = true; showActionDialogFor = null })
                    ActionMenuItem(icon = Icons.Default.Add, title = "Add Stock", subtitle = "Restock item", color = AmberAccent, onClick = { productToRestock = showActionDialogFor; showActionDialogFor = null })
                    ActionMenuItem(icon = Icons.Default.Delete, title = "Damaged Item", subtitle = "Record waste/expired", color = AmberAccentDark, onClick = { productToWaste = showActionDialogFor; showActionDialogFor = null })
                    ActionMenuItem(icon = Icons.Default.List, title = "Manage Recipe (BOM)", subtitle = "Raw materials & composition", color = EmeraldGreenLight, onClick = { productToManageBom = showActionDialogFor; showActionDialogFor = null })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    ActionMenuItem(icon = Icons.Default.Delete, title = "Delete Product", subtitle = "Delete permanently", color = ErrorRed, onClick = { productToDelete = showActionDialogFor; showActionDialogFor = null })
                }
            }
        }

        if (productToRestock != null) {
            StockOpnameDialog(product = productToRestock!!, onDismiss = { productToRestock = null }, onSave = { qty -> viewModel.addStock(productToRestock!!, qty, currentUser); productToRestock = null })
        }

        if (productToWaste != null) {
            var qty by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { productToWaste = null },
                icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed) },
                title = { Text("Record Damaged Item") },
                text = {
                    Column {
                        Text("Product: ${productToWaste!!.name}", fontWeight = FontWeight.Bold)
                        Text("Stock: ${productToWaste!!.stock} pcs", color = SlateGrey)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(value = qty, onValueChange = { if (it.length <= 5) qty = it }, label = { Text("Damaged Quantity") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                },
                confirmButton = {
                    Button(onClick = { 
                        val q = qty.toIntOrNull() ?: 0
                        if (q > 0 && q <= productToWaste!!.stock) { viewModel.recordWaste(productToWaste!!, q, currentUser); productToWaste = null }
                    }, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed), shape = RoundedCornerShape(12.dp)) { Text("SAVE") }
                },
                dismissButton = { TextButton(onClick = { productToWaste = null }) { Text("Cancel") } }
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

        if (productToDelete != null) {
            AlertDialog(
                onDismissRequest = { productToDelete = null },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed) },
                title = { Text("Delete Product?") },
                text = { Text("Delete '${productToDelete!!.name}' permanently?") },
                confirmButton = { Button(onClick = { viewModel.deleteProduct(productToDelete!!, currentUser.id, currentUser.name); productToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed), shape = RoundedCornerShape(12.dp)) { Text("DELETE") } },
                dismissButton = { TextButton(onClick = { productToDelete = null }) { Text("Cancel") } }
            )
        }
    }
}