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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

        // AUTO-BACKUP HARIAN (Defensive Programming)
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
                var isDarkMode by remember { mutableStateOf(settingsManager.isDarkMode) }
                var isHighContrast by remember { mutableStateOf(settingsManager.isHighContrast) }
                var uiScale by remember { mutableStateOf(settingsManager.uiScale) }
                var language by remember { mutableStateOf(settingsManager.language) }
                var isPro by remember { mutableStateOf(settingsManager.isPro) }
                var currentUser by remember { mutableStateOf<UserEntity?>(null) }

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
                            isPro = isPro,
                            isDarkMode = isDarkMode,
                            isHighContrast = isHighContrast,
                            uiScale = uiScale,
                            language = language,
                            onUserChange = { currentUser = it },
                            onUpgrade = { isPro = true; settingsManager.isPro = true },
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
    isPro: Boolean,
    isDarkMode: Boolean,
    isHighContrast: Boolean,
    uiScale: Float,
    language: String,
    onUserChange: (UserEntity?) -> Unit,
    onUpgrade: () -> Unit,
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
                isPro = isPro,
                isDarkMode = isDarkMode,
                isHighContrast = isHighContrast,
                uiScale = uiScale,
                onToggleDarkMode = onToggleDarkMode,
                onToggleHighContrast = onToggleHighContrast,
                onChangeUiScale = onChangeUiScale,
                language = language,
                onToggleLanguage = onToggleLanguage,
                onUpgradeClick = onUpgrade
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
    isPro: Boolean,
    isDarkMode: Boolean,
    isHighContrast: Boolean,
    uiScale: Float,
    onToggleDarkMode: () -> Unit,
    onToggleHighContrast: () -> Unit,
    onChangeUiScale: (Float) -> Unit,
    language: String,
    onToggleLanguage: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            // HEADER MODERN (Minimalis & Clean)
            Row(
                modifier = Modifier
                    .fillMaxWidth() // <--- PERBAIKAN: Dari fillMaxSize() menjadi fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (settingsManager.isPro) settingsManager.storeName ?: "ZoKas" else "ZoKas",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        // CROWN BADGE MINIMALIS
                        if (settingsManager.isPro) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = "Pro Version",
                                tint = AmberAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "Kasir Pintar & Laporan Keuangan",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Storefront, contentDescription = "Kasir") },
                    label = { Text("Kasir") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Inventory, contentDescription = "Produk") },
                    label = { Text("Produk") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Assessment, contentDescription = "Laporan") },
                    label = { Text("Laporan") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Pengaturan") },
                    label = { Text("Pengaturan") },
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
            // ANIMASI TRANSISI HALUS (Fade + Slide 200ms)
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(animationSpec = tween(200)) { fullWidth -> fullWidth * direction } + fadeIn(animationSpec = tween(200))) togetherWith
                    (slideOutHorizontally(animationSpec = tween(200)) { fullWidth -> -fullWidth * direction } + fadeOut(animationSpec = tween(200)))
                }, label = "tab_transition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> PosScreen(viewModel = viewModel, settingsManager = settingsManager, currentUser = currentUser, onLogout = onLogout)
                    1 -> PosScreen(viewModel = viewModel, settingsManager = settingsManager, currentUser = currentUser, onLogout = onLogout) // Placeholder Sementara untuk Tab Produk
                    2 -> HistoryScreen(transactions = transactions, isPro = isPro, viewModel = viewModel, settingsManager = settingsManager, currentUser = currentUser)
                    3 -> SettingsScreen(
                        settingsManager = settingsManager, isPro = isPro, onUpgradeClick = onUpgradeClick,
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