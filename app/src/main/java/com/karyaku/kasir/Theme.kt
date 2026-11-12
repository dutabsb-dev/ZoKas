package com.karyaku.kasir

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import java.util.Locale

private val LightColors = lightColorScheme(
    primary = EmeraldGreen,
    onPrimary = Color.White,
    primaryContainer = EmeraldGreenLight,
    onPrimaryContainer = EmeraldGreenDark,
    secondary = SlateGrey,
    onSecondary = Color.White,
    secondaryContainer = SlateGreyLight,
    onSecondaryContainer = SlateGreyDark,
    tertiary = AmberAccent,
    onTertiary = Color.White,
    background = BackgroundLight,
    onBackground = SlateGreyDark,
    surface = SurfaceLight,
    onSurface = SlateGreyDark,
    surfaceVariant = SlateSurface,
    onSurfaceVariant = SlateGrey,
    error = ErrorRed,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = EmeraldGreenLight,
    onPrimary = EmeraldGreenDark,
    primaryContainer = Color(0xFF065F46), // Hijau gelap agar ikon putih terlihat
    onPrimaryContainer = Color.White,
    secondary = SlateGreyLight,
    onSecondary = SlateGreyDark,
    secondaryContainer = SlateGreyDark,
    onSecondaryContainer = SlateGreyLight,
    tertiary = AmberAccent,
    onTertiary = Color.White,
    background = BackgroundDark,
    onBackground = Color(0xFFF8FAFC), // Teks luar Putih
    surface = SurfaceDark,
    onSurface = Color(0xFFF8FAFC), // Teks dalam card Putih
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFCBD5E1), // Teks abu-abu terang (hampir putih)
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun PosTheme(
    isDarkMode: Boolean = isSystemInDarkTheme(),
    isHighContrast: Boolean = false,
    language: String = "in",
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isHighContrast -> lightColorScheme(
            primary = Color.Black, onPrimary = Color.White, background = Color.White,
            onBackground = Color.Black, surface = Color.White, onSurface = Color.Black
        )
        isDarkMode -> DarkColors
        else -> LightColors
    }

    val context = LocalContext.current
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocale(Locale(language))
    val localizedContext = context.createConfigurationContext(configuration)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkMode && !isHighContrast
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