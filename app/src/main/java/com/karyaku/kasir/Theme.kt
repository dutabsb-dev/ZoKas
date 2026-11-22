package com.karyaku.kasir

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import java.util.Locale

@Composable
fun PosTheme(
    isDarkMode: Boolean = isSystemInDarkTheme(),
    isHighContrast: Boolean = false,
    language: String = "in",
    content: @Composable () -> Unit
) {
    val rawContext = LocalContext.current
    ZokasThemeManager.ensureLoaded(rawContext)
    val activePreset = ZokasThemeManager.getActivePreset(rawContext)

    val colorScheme = when {
        isHighContrast -> lightColorScheme(
            primary = Color.Black, onPrimary = Color.White, background = Color.White,
            onBackground = Color.Black, surface = Color.White, onSurface = Color.Black
        )
        else -> ZokasThemeManager.buildColorScheme(activePreset, forceDarkMode = isDarkMode)
    }

    val configuration = Configuration(rawContext.resources.configuration)
    configuration.setLocale(Locale(language))
    val localizedContext = rawContext.createConfigurationContext(configuration)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            val isLightStatusBar = if (isHighContrast) true else colorScheme.background.luminance() > 0.45f
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLightStatusBar
        }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
