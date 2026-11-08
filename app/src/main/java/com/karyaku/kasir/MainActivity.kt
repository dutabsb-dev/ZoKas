package com.karyaku.kasir

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
                    Toast.makeText(this, "Auto-Backup Database Berhasil!", Toast.LENGTH_SHORT).show()
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

                // FITUR UI SCALING: Mengubah ukuran font secara global
                val currentDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = currentDensity.density, fontScale = uiScale)
                ) {
                    // FITUR HIGH CONTRAST
                    if (isHighContrast) {
                        MaterialTheme(
                            colorScheme = lightColorScheme(
                                primary = androidx.compose.ui.graphics.Color.Black,
                                onPrimary = androidx.compose.ui.graphics.Color.White,
                                background = androidx.compose.ui.graphics.Color.White,
                                onBackground = androidx.compose.ui.graphics.Color.Black,
                                surface = androidx.compose.ui.graphics.Color.White,
                                onSurface = androidx.compose.ui.graphics.Color.Black
                            )
                        ) {
                            MainContent(database, settingsManager, currentUser, isPro, isDarkMode, isHighContrast, uiScale, language, 
                                onUserChange = { currentUser = it },
                                onUpgrade = { isPro = true; settingsManager.isPro = true },
                                onToggleDarkMode = { isDarkMode = !isDarkMode; settingsManager.isDarkMode = isDarkMode },
                                onToggleHighContrast = { isHighContrast = !isHighContrast; settingsManager.isHighContrast = isHighContrast },
                                onChangeUiScale = { uiScale = it; settingsManager.uiScale = it },
                                onToggleLanguage = { 
                                    val newLang = if (language == "in") "en" else "in"
                                    language = newLang; settingsManager.language = newLang 
                                }
                            )
                        }
                    } else {
                        PosTheme(isDarkMode = isDarkMode, language = language) {
                            MainContent(database, settingsManager, currentUser, isPro, isDarkMode, isHighContrast, uiScale, language, 
                                onUserChange = { currentUser = it },
                                onUpgrade = { isPro = true; settingsManager.isPro = true },
                                onToggleDarkMode = { isDarkMode = !isDarkMode; settingsManager.isDarkMode = isDarkMode },
                                onToggleHighContrast = { isHighContrast = !isHighContrast; settingsManager.isHighContrast = isHighContrast },
                                onChangeUiScale = { uiScale = it; settingsManager.uiScale = it },
                                onToggleLanguage = { 
                                    val newLang = if (language == "in") "en" else "in"
                                    language = newLang; settingsManager.language = newLang 
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainContent(
    database: AppDatabase, settingsManager: SettingsManager, currentUser: UserEntity?, 
    isPro: Boolean, isDarkMode: Boolean, isHighContrast: Boolean, uiScale: Float, language: String,
    onUserChange: (UserEntity?) -> Unit, onUpgrade: () -> Unit, 
    onToggleDarkMode: () -> Unit, onToggleHighContrast: () -> Unit, onChangeUiScale: (Float) -> Unit, onToggleLanguage: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val posViewModel: PosViewModel = viewModel(
            factory = PosViewModelFactory(
                database.productDao(), database.transactionDao(), database.userDao(), database.cashFlowDao(),
                database.customerDao(), database.transactionItemDao(), database.voucherDao(), database.holdOrderDao()
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
    viewModel: PosViewModel, settingsManager: SettingsManager, currentUser: UserEntity, onLogout: () -> Unit,
    isPro: Boolean, isDarkMode: Boolean, isHighContrast: Boolean, uiScale: Float, 
    onToggleDarkMode: () -> Unit, onToggleHighContrast: () -> Unit, onChangeUiScale: (Float) -> Unit, 
    language: String, onToggleLanguage: () -> Unit, onUpgradeClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(icon = { Icon(Icons.Default.Home, contentDescription = "Kasir") }, label = { Text("Kasir") }, selected = selectedTab == 0, onClick = { selectedTab = 0 })
                NavigationBarItem(icon = { Icon(Icons.Default.List, contentDescription = "Riwayat") }, label = { Text("Riwayat") }, selected = selectedTab == 1, onClick = { selectedTab = 1 })
                if (currentUser.role == "OWNER") {
                    NavigationBarItem(icon = { Icon(Icons.Default.Settings, contentDescription = "Pengaturan") }, label = { Text("Pengaturan") }, selected = selectedTab == 2, onClick = { selectedTab = 2 })
                }
            }
        }
    ) { paddingValues ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> PosScreen(viewModel = viewModel, settingsManager = settingsManager, currentUser = currentUser, onLogout = onLogout)
                1 -> HistoryScreen(transactions = transactions, isPro = isPro, viewModel = viewModel, settingsManager = settingsManager, currentUser = currentUser)
                2 -> SettingsScreen(
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