package com.karyaku.kasir

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

// Memaksa tema gelap agar warna 3D dan Hijau/Orange terlihat sempurna
val ZokasColors = darkColorScheme(
    primary = ZokasGreenLight,
    onPrimary = ZokasTextWhite,
    primaryContainer = ZokasGreenDark, // Warna TopBar
    onPrimaryContainer = ZokasTextWhite,
    secondary = ZokasOrange, // Warna Tombol/FAB
    onSecondary = ZokasTextWhite,
    background = ZokasBackground,
    surface = ZokasSurface,
    surfaceVariant = ZokasSurfaceVariant,
    onSurface = ZokasTextWhite,
    onSurfaceVariant = ZokasTextWhite
)

@Composable
fun PosTheme(
    isDarkMode: Boolean = true, // Default ke true untuk efek 3D
    language: String = "in",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocale(Locale(language))
    val localizedContext = context.createConfigurationContext(configuration)

    CompositionLocalProvider(
        LocalContext provides localizedContext
    ) {
        MaterialTheme(
            colorScheme = ZokasColors, // Gunakan skema warna ZoKas
            content = content
        )
    }
}