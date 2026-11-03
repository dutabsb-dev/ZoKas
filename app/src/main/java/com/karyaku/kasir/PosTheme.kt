package com.karyaku.kasir

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

val DarkColors = darkColorScheme()
val LightColors = lightColorScheme()

@Composable
fun PosTheme(
    isDarkMode: Boolean = isSystemInDarkTheme(),
    language: String = "in",
    content: @Composable () -> Unit
) {
    // Mengatur Warna Tema
    val colorScheme = if (isDarkMode) DarkColors else LightColors

    // Mengatur Bahasa (Locale) secara dinamis tanpa restart
    val context = LocalContext.current
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocale(Locale(language))
    val localizedContext = context.createConfigurationContext(configuration)

    CompositionLocalProvider(
        LocalContext provides localizedContext
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}