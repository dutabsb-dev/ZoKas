package com.karyaku.kasir

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color // <--- IMPORT INI DITAMBAHKAN
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

val ZokasLightColors = lightColorScheme(
    primary = ZokasGreen,
    onPrimary = Color.White,
    primaryContainer = ZokasGreen, // TopBar Hijau
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
    primaryContainer = ZokasGreen, // TopBar Hijau Gelap
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