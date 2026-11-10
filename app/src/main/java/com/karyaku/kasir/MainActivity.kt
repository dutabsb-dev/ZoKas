package com.karyaku.kasir

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karyaku.kasir.data.local.AppDatabase
import com.karyaku.kasir.data.local.UserEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
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
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides this@MainActivity) {
                var isDarkMode by rememberSaveable { mutableStateOf(settingsManager.isDarkMode) }
                var isHighContrast by rememberSaveable { mutableStateOf(settingsManager.isHighContrast) }
                var uiScale by rememberSaveable { mutableStateOf(settingsManager.uiScale) }
                var language by rememberSaveable { mutableStateOf(settingsManager.language) }
                
                // SAAT ROTASI LAYAR, GUNAKAN rememberSaveable AGAR TIDAK LOGOUT
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

        if (currentUser == null) {
            LoginScreen(viewModel = posViewModel, onLoginSuccess = { onUserChange(it) })
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
    
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    // Network State Listener
    val context = androidx.compose.ui.platform.LocalContext.current
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
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = settingsManager.storeName ?: "ZoKas Store",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(if (isOnline) EmeraldGreen else ErrorRed, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isOnline) "Online" else "Offline",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // GABUNGAN ACTION BAR KE HEADER
                IconButton(onClick = { showCfd = true }) { 
                    Icon(Icons.Default.Person, contentDescription = "Customer Display", tint = MaterialTheme.colorScheme.primary) 
                }
                IconButton(onClick = { showMenu = true }) { 
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface) 
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Merge Table") }, leadingIcon = { Icon(Icons.Default.Merge, contentDescription = null, tint = EmeraldGreen) }, onClick = { /* TODO: Implement global merge trigger if needed */ showMenu = false })
                    DropdownMenuItem(text = { Text("Open Drawer") }, leadingIcon = { Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = EmeraldGreen) }, onClick = { viewModel.logCashDrawerOpen(currentUser); showMenu = false })
                    DropdownMenuItem(text = { Text("Hold Orders") }, leadingIcon = { Icon(Icons.Default.List, contentDescription = null, tint = EmeraldGreen) }, onClick = { showMenu = false })
                    DropdownMenuItem(text = { Text("Cash In/Out") }, leadingIcon = { Icon(Icons.Default.AccountBox, contentDescription = null, tint = AmberAccent) }, onClick = { showMenu = false })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Logout", color = ErrorRed) }, leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = ErrorRed) }, onClick = { onLogout(); showMenu = false })
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
                    label = { Text("Cashier") },
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
                    label = { Text("Products") },
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
                    label = { Text("Reports") },
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
                    label = { Text("Settings") },
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
                    0 -> PosScreen(viewModel = viewModel, settingsManager = settingsManager, currentUser = currentUser, onLogout = onLogout, showCfd = showCfd, onDismissCfd = { showCfd = false })
                    1 -> ProductManagementScreen(viewModel = viewModel, settingsManager = settingsManager, currentUser = currentUser)
                    2 -> HistoryScreen(transactions = transactions, isPro = true, viewModel = viewModel, settingsManager = settingsManager, currentUser = currentUser)
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