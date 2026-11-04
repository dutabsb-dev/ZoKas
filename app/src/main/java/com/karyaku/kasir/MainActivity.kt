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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(this)
        val settingsManager = SettingsManager(this)

        setContent {
            var isDarkMode by remember { mutableStateOf(settingsManager.isDarkMode) }
            var language by remember { mutableStateOf(settingsManager.language) }
            var isPro by remember { mutableStateOf(settingsManager.isPro) }

            PosTheme(isDarkMode = isDarkMode, language = language) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val posViewModel: PosViewModel = viewModel(
                        factory = PosViewModelFactory(database.productDao(), database.transactionDao())
                    )

                    MainNavigation(
                        viewModel = posViewModel,
                        settingsManager = settingsManager,
                        isPro = isPro,
                        onUpgradeClick = {
                            isPro = true
                            settingsManager.isPro = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainNavigation(
    viewModel: PosViewModel,
    settingsManager: SettingsManager,
    isPro: Boolean,
    onUpgradeClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
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
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Pengaturan") },
                    label = { Text("Pro & Pengaturan") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { paddingValues ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> PosScreen(viewModel = viewModel, settingsManager = settingsManager)
                // Kirim parameter isPro ke HistoryScreen
                1 -> HistoryScreen(transactions = transactions, isPro = isPro)
                2 -> SettingsScreen(
                    settingsManager = settingsManager,
                    isPro = isPro,
                    onUpgradeClick = {
                        onUpgradeClick()
                        Toast.makeText(context, "Berhasil Upgrade ke Pro!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}