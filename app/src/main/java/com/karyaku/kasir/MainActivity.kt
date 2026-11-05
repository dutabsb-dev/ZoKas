package com.karyaku.kasir

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karyaku.kasir.data.local.AppDatabase
import com.karyaku.kasir.data.local.UserEntity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(this)
        val settingsManager = SettingsManager(this)

        setContent {
            var isDarkMode by remember { mutableStateOf(settingsManager.isDarkMode) }
            var language by remember { mutableStateOf(settingsManager.language) }
            var isPro by remember { mutableStateOf(settingsManager.isPro) }
            var currentUser by remember { mutableStateOf<UserEntity?>(null) }

            PosTheme(isDarkMode = isDarkMode, language = language) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    
                    // Inisialisasi ViewModel dengan SEMUA parameter yang dibutuhkan
                    val posViewModel: PosViewModel = viewModel(
                        factory = PosViewModelFactory(
                            database.productDao(), 
                            database.transactionDao(), 
                            database.userDao(),
                            database.cashFlowDao(),
                            database.customerDao(),
                            database.transactionItemDao() // Parameter ke-6
                        )
                    )

                    if (currentUser == null) {
                        LoginScreen(viewModel = posViewModel, onLoginSuccess = { currentUser = it })
                    } else {
                        MainNavigation(
                            viewModel = posViewModel,
                            settingsManager = settingsManager,
                            currentUser = currentUser!!,
                            onLogout = { currentUser = null },
                            isPro = isPro,
                            isDarkMode = isDarkMode,
                            onToggleDarkMode = { isDarkMode = !isDarkMode; settingsManager.isDarkMode = isDarkMode },
                            language = language,
                            onToggleLanguage = { val newLang = if (language == "in") "en" else "in"; language = newLang; settingsManager.language = newLang },
                            onUpgradeClick = { isPro = true; settingsManager.isPro = true }
                        )
                    }
                }
            }
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
    onToggleDarkMode: () -> Unit,
    language: String,
    onToggleLanguage: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Kasir") }, 
                    label = { Text("Kasir") }, 
                    selected = selectedTab == 0, 
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "Riwayat") }, 
                    label = { Text("Riwayat") }, 
                    selected = selectedTab == 1, 
                    onClick = { selectedTab = 1 }
                )
                if (currentUser.role == "OWNER") {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Pengaturan") }, 
                        label = { Text("Pengaturan") }, 
                        selected = selectedTab == 2, 
                        onClick = { selectedTab = 2 }
                    )
                }
            }
        }
    ) { paddingValues ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> PosScreen(
                    viewModel = viewModel, 
                    settingsManager = settingsManager, 
                    currentUser = currentUser, 
                    onLogout = onLogout
                )
                1 -> HistoryScreen(
                    transactions = transactions, 
                    isPro = isPro, 
                    viewModel = viewModel ,
                    settingsManager = settingsManager
                )
                2 -> SettingsScreen(
                    settingsManager = settingsManager,
                    isPro = isPro,
                    onUpgradeClick = { onUpgradeClick(); Toast.makeText(context, "Pro Aktif!", Toast.LENGTH_SHORT).show() },
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    language = language,
                    onToggleLanguage = onToggleLanguage,
                    onLogout = onLogout,
                    auditLogs = auditLogs,
                    currentUser = currentUser,
                    viewModel = viewModel
                )
            }
        }
    }
}