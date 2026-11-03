package com.karyaku.kasir

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karyaku.kasir.data.local.AppDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inisialisasi Database
        val database = AppDatabase.getDatabase(this)
        val settingsManager = SettingsManager(this)

        setContent {
            var isDarkMode by remember { mutableStateOf(settingsManager.isDarkMode) }
            var language by remember { mutableStateOf(settingsManager.language) }

            PosTheme(isDarkMode = isDarkMode, language = language) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Memanggil ViewModel dengan Factory
                    val posViewModel: PosViewModel = viewModel(
                        factory = PosViewModelFactory(database.productDao())
                    )
                    
                    // Panggil Layar Kasir
                    PosScreen(viewModel = posViewModel)
                }
            }
        }
    }
}