package com.karyaku.kasir

import android.app.Activity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import java.util.Locale

// DEFINISI TEMA WARNA
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
    primaryContainer = EmeraldGreenDark,
    onPrimaryContainer = EmeraldGreenLight,
    secondary = SlateGreyLight,
    onSecondary = SlateGreyDark,
    secondaryContainer = SlateGreyDark,
    onSecondaryContainer = SlateGreyLight,
    tertiary = AmberAccent,
    onTertiary = Color.White,
    background = BackgroundDark,
    onBackground = BackgroundLight,
    surface = SurfaceDark,
    onSurface = BackgroundLight,
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = SlateGreyLight,
    error = ErrorRed,
    onError = Color.White
)

// STANDARISASI BORDER RADIUS 12DP
private val ZokasShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), // Standar Tombol & Card
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun PosTheme(
    isDarkMode: Boolean = false,
    isHighContrast: Boolean = false,
    language: String = "in",
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isHighContrast -> lightColorScheme(
            primary = Color.Black,
            onPrimary = Color.White,
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black
        )
        isDarkMode -> DarkColors
        else -> LightColors
    }

    // Force Locale Update (Fix Bug Bahasa)
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val localizedContext = remember(language) {
        val newConfig = android.content.res.Configuration(config)
        newConfig.setLocale(Locale(language))
        context.createConfigurationContext(newConfig)
    }

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
        LocalConfiguration provides localizedContext.resources.configuration
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = ZokasShapes,
            content = content
        )
    }
}

// --- EXTENSION MODIFIER UNTUK TOUCH FEEDBACK (SCALE 95) ---
@Composable
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f, // Efek scale-95
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bouncy_scale"
    )

    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
}