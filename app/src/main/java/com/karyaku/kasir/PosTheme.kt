package com.karyaku.kasir

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

val LightColors = lightColorScheme(
    primary = DarkGreenPrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = DarkOrangeSecondary,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = LightBackground,
    surface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE0E6E0)
)

val DarkColors = darkColorScheme(
    primary = GradientGreenTop,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = GradientOrangeTop,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = DarkBackground,
    surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2C2C2C)
)

@Composable
fun PosTheme(
    isDarkMode: Boolean = isSystemInDarkTheme(),
    language: String = "in",
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDarkMode) DarkColors else LightColors

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