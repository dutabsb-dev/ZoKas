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

// =====================================================================
// FIX #6 — TEMA GELAP LEBIH TERANG & BERKONTRAS
// =====================================================================
// Perubahan utama dibanding versi lama:
//   1. onSurfaceVariant: 0xFFCBD5E1 → 0xFFE2E8F0 (jauh lebih terang,
//      ini yang paling sering bikin teks "tenggelam" di dark mode)
//   2. onPrimary: EmeraldGreenDark → 0xFF022C22 (teks di tombol emerald
//      sekarang HIJAU SANGAT TUA di atas hijau cerah → kontras tinggi)
//   3. error: 0xFFEF4444 → 0xFFF87171 (merah lebih cerah, terbaca di dark bg)
//   4. Tambah outline, outlineVariant, inverse*, scrim (field ini tidak
//      dideklarasikan sebelumnya → Material 3 pakai default black → border
//      kadang tak terlihat)
// =====================================================================
private val DarkColors = darkColorScheme(
    // PRIMARY — Emerald
    primary = EmeraldGreenLight,               // 0xFF34D399 (lebih cerah)
    onPrimary = Color(0xFF022C22),             // Hijau sangat tua → teks di tombol emerald
    primaryContainer = EmeraldGreenDark,
    onPrimaryContainer = Color(0xFFA7F3D0),    // Mint terang

    // SECONDARY — Slate
    secondary = SlateGreyLight,
    onSecondary = BackgroundDark,
    secondaryContainer = SurfaceVariantDark,
    onSecondaryContainer = Color(0xFFE2E8F0),

    // TERTIARY — Amber
    tertiary = AmberAccent,
    onTertiary = Color(0xFF451A03),            // Cokelat tua (teks di tombol amber)
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),   // Krim terang

    // BACKGROUND & SURFACE
    background = BackgroundDark,               // 0xFF0F172A
    onBackground = Color(0xFFF1F5F9),          // Lebih hangat dari F8FAFC
    surface = SurfaceDark,                     // 0xFF1E293B
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = SurfaceVariantDark,       // 0xFF334155
    onSurfaceVariant = Color(0xFFE2E8F0),      // ★ KUNCI PERBAIKAN
    surfaceTint = EmeraldGreenLight,

    // INVERSE (snackbar, tooltip, dll)
    inverseSurface = Color(0xFFE2E8F0),
    inverseOnSurface = BackgroundDark,
    inversePrimary = EmeraldGreenDark,

    // ERROR
    error = Color(0xFFF87171),                 // ★ Merah lebih cerah
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),

    // OUTLINE & BORDER
    outline = Color(0xFF94A3B8),               // ★ Border jadi jelas
    outlineVariant = Color(0xFF475569),        // Divider halus

    scrim = Color(0xFF000000)
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