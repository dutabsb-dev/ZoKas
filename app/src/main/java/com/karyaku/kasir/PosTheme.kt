package com.karyaku.kasir

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.core.os.LocaleListCompat
import java.util.Locale

val ZokasLightColors = lightColorScheme(
    primary = ZokasGreen,
    onPrimary = Color.White,
    primaryContainer = ZokasGreen, 
    onPrimaryContainer = Color.White,
    secondary = ZokasOrange,
    onSecondary = Color.White,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurface = LightText,
    onSurfaceVariant = LightText
)

val ZokasDarkColors = darkColorScheme(
    primary = ZokasGreenLight,
    onPrimary = Color.White,
    primaryContainer = ZokasGreen, 
    onPrimaryContainer = Color.White,
    secondary = ZokasOrange,
    onSecondary = Color.White,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurface = DarkText,
    onSurfaceVariant = DarkText
)

@Composable
fun PosTheme(
    isDarkMode: Boolean = false,
    language: String = "in",
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDarkMode) ZokasDarkColors else ZokasLightColors

    // PERBAIKAN: Menggunakan AppCompatDelegate, TIDAK membungkus LocalContext
    val locales = LocaleListCompat.forLanguageTags(language)
    if (AppCompatDelegate.getApplicationLocales() != locales) {
        AppCompatDelegate.setApplicationLocales(locales)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}