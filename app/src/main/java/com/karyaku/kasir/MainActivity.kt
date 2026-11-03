package com.karyaku.kasir

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inisialisasi Settings
        val settingsManager = SettingsManager(this)

        setContent {
            // State untuk memantau perubahan tema & bahasa
            var isDarkMode by remember { mutableStateOf(settingsManager.isDarkMode) }
            var language by remember { mutableStateOf(settingsManager.language) }

            PosTheme(isDarkMode = isDarkMode, language = language) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TampilanPengaturan(
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = {
                            isDarkMode = !isDarkMode
                            settingsManager.isDarkMode = isDarkMode
                        },
                        language = language,
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

@Composable
fun TampilanPengaturan(
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    language: String,
    onToggleLanguage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.welcome_text),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onToggleDarkMode) {
            Text(text = stringResource(id = R.string.btn_dark_mode))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onToggleLanguage) {
            val textId = if (language == "in") R.string.btn_language_en else R.string.btn_language
            Text(text = stringResource(id = textId))
        }
    }
}