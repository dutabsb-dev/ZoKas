package com.karyaku.kasir

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// =====================================================================
// KONSTANTA WARNA BRAND BAWAAN (HIJAU & PUTIH — TEMA DEFAULT)
// =====================================================================
val EmeraldGreen = Color(0xFF059669)
val EmeraldGreenDark = Color(0xFF047857)
val EmeraldGreenLight = Color(0xFF34D399)

val SlateGrey = Color(0xFF64748B)
val SlateGreyDark = Color(0xFF334155)
val SlateGreyLight = Color(0xFF94A3B8)
val SlateSurface = Color(0xFFF1F5F9)

val AmberAccent = Color(0xFFF59E0B)
val AmberAccentDark = Color(0xFFD97706)

val BackgroundLight = Color(0xFFF8FAFC)
val SurfaceLight = Color(0xFFFFFFFF)

val BackgroundDark = Color(0xFF0F172A)
val SurfaceDark = Color(0xFF1E293B)
val SurfaceVariantDark = Color(0xFF334155)

val ErrorRed = Color(0xFFEF4444)

// Legacy mapping (backward compat)
val ZokasGreen = EmeraldGreen
val ZokasGreenDark = EmeraldGreenDark
val ZokasGreenLight = EmeraldGreenLight
val ZokasOrange = AmberAccent
val ZokasOrangeDark = AmberAccentDark
val LightBackground = BackgroundLight
val LightSurface = SurfaceLight
val LightSurfaceVariant = SlateSurface
val LightText = SlateGreyDark
val DarkBackground = BackgroundDark
val DarkSurface = SurfaceDark
val DarkSurfaceVariant = SurfaceVariantDark
val DarkText = SlateGreyLight

data class ZokasThemePreset(
    val id: String,
    val name: String,
    val category: String,
    val isDark: Boolean,
    val primaryHex: Long,
    val secondaryHex: Long,
    val tertiaryHex: Long,
    val backgroundHex: Long,
    val surfaceHex: Long,
    val surfaceVariantHex: Long,
    val gradientStartHex: Long,
    val gradientEndHex: Long,
    val glassAlpha: Float = 0.88f
)

private fun readableOnColor(bg: Color): Color {
    // Gunakan luminansi relatif WCAG agar teks selalu kontras & tidak menyatu dengan warna tema
    return if (bg.luminance() > 0.42f) Color(0xFF0F172A) else Color(0xFFF8FAFC)
}

private fun readableSecondaryOnColor(bg: Color): Color {
    return if (bg.luminance() > 0.42f) Color(0xFF334155) else Color(0xFFE2E8F0)
}

val ZOKAS_50_THEMES: List<ZokasThemePreset> = listOf(
    // 1. DEFAULT (Hijau & Putih — Bawaan ZoKas)
    ZokasThemePreset("default_emerald", "01. Hijau Zamrud & Putih (Default)", "Default", false, 0xFF059669, 0xFF64748B, 0xFFF59E0B, 0xFFF8FAFC, 0xFFFFFFFF, 0xFFF1F5F9, 0xFF059669, 0xFF10B981, 0.96f),

    // HARI BESAR INDONESIA (02 - 11)
    ZokasThemePreset("id_kemerdekaan_17", "02. Kemerdekaan Indonesia (17 Agustus)", "Hari Besar RI", false, 0xFFDC2626, 0xFF475569, 0xFFD97706, 0xFFFFFBFB, 0xF5FFFFFF, 0xFFFEE2E2, 0xFFDC2626, 0xFFEF4444, 0.92f),
    ZokasThemePreset("id_idul_fitri", "03. Idul Fitri Nusantara (Ketupat & Emas)", "Hari Besar RI", false, 0xFF047857, 0xFF475569, 0xFFD97706, 0xFFF4FBF7, 0xF2FFFFFF, 0xFFFEF3C7, 0xFF047857, 0xFFD97706, 0.90f),
    ZokasThemePreset("id_sumpah_pemuda", "04. Sumpah Pemuda (28 Oktober)", "Hari Besar RI", false, 0xFFB91C1C, 0xFF334155, 0xFF1D4ED8, 0xFFFDF8F8, 0xF0FFFFFF, 0xFFE2E8F0, 0xFFB91C1C, 0xFF1E3A8A, 0.88f),
    ZokasThemePreset("id_hari_kartini", "05. Hari Kartini (21 April)", "Hari Besar RI", false, 0xFF9D174D, 0xFF57534E, 0xFFB45309, 0xFFFFF8FB, 0xEBFFFFFF, 0xFFFCE7F3, 0xFF9D174D, 0xFFD97706, 0.88f),
    ZokasThemePreset("id_hari_batik", "06. Hari Batik Nasional (2 Oktober)", "Hari Besar RI", true, 0xFFFBBF24, 0xFFD6D3D1, 0xFF38BDF8, 0xFF1A120B, 0xEB2B1E13, 0xFF422E1E, 0xFF92400E, 0xFFD97706, 0.88f),
    ZokasThemePreset("id_hari_pahlawan", "07. Hari Pahlawan (10 November)", "Hari Besar RI", true, 0xFFF87171, 0xFFCBD5E1, 0xFFFACC15, 0xFF1C0D0D, 0xEB2E1515, 0xFF472020, 0xFF991B1B, 0xFFDC2626, 0.87f),
    ZokasThemePreset("id_hardiknas", "08. Hari Pendidikan Nasional (2 Mei)", "Hari Besar RI", false, 0xFF1E40AF, 0xFF475569, 0xFFD97706, 0xFFF5F8FF, 0xF0FFFFFF, 0xFFFEF3C7, 0xFF1E40AF, 0xFF2563EB, 0.89f),
    ZokasThemePreset("id_imlek_nusantara", "09. Tahun Baru Imlek (Lentera & Emas)", "Hari Besar RI", false, 0xFFDC2626, 0xFF57534E, 0xFFCA8A04, 0xFFFFF8F6, 0xEBFFFFFF, 0xFFFEF08A, 0xFFDC2626, 0xFFEAB308, 0.88f),
    ZokasThemePreset("id_natal_tahun_baru", "10. Natal & Tahun Baru Indonesia", "Hari Besar RI", true, 0xFF34D399, 0xFFCBD5E1, 0xFFF87171, 0xFF0A1F18, 0xE8133328, 0xFF1E4D3C, 0xFF047857, 0xFFDC2626, 0.86f),
    ZokasThemePreset("id_hari_bumi_ri", "11. Bumi Khatulistiwa (Hijau & Biru RI)", "Hari Besar RI", false, 0xFF0D9488, 0xFF475569, 0xFF0284C7, 0xFFF0FDFA, 0xE8FFFFFF, 0xFFE0F2FE, 0xFF0D9488, 0xFF0284C7, 0.87f),

    // ALAM & SEGAR (12 - 20)
    ZokasThemePreset("emerald_glass_mint", "12. Mint Transparan", "Alam & Segar", false, 0xFF0D9488, 0xFF475569, 0xFFD97706, 0xFFF0FDFA, 0xE6FFFFFF, 0xFFCCFBF1, 0xFF0D9488, 0xFF2DD4BF, 0.86f),
    ZokasThemePreset("forest_jade", "13. Hutan Giok", "Alam & Segar", true, 0xFF34D399, 0xFF94A3B8, 0xFFFBBF24, 0xFF062019, 0xE60D2E25, 0xFF164236, 0xFF059669, 0xFF34D399, 0.85f),
    ZokasThemePreset("lime_citrus", "14. Jeruk Nipis Segar", "Alam & Segar", false, 0xFF4D7C0F, 0xFF57534E, 0xFFEA580C, 0xFFF7FEE7, 0xF2FFFFFF, 0xFFECFCCB, 0xFF65A30D, 0xFFA3E635, 0.90f),
    ZokasThemePreset("sage_botanical", "15. Daun Sage Lembut", "Alam & Segar", false, 0xFF2F6F4E, 0xFF52525B, 0xFFC2410C, 0xFFF4F7F5, 0xF5FFFFFF, 0xFFE2ECE6, 0xFF2F6F4E, 0xFF5FA37E, 0.92f),
    ZokasThemePreset("matcha_latte", "16. Matcha Latte", "Alam & Segar", false, 0xFF3F6212, 0xFF57534E, 0xFFB45309, 0xFFFAF9F5, 0xF5FFFFFF, 0xFFE9EED9, 0xFF4D7C0F, 0xFF84CC16, 0.92f),
    ZokasThemePreset("bamboo_mist", "17. Kabut Bambu", "Alam & Segar", true, 0xFF4ADE80, 0xFFA1A1AA, 0xFFF59E0B, 0xFF0B1911, 0xE0132A1D, 0xFF1E3F2C, 0xFF16A34A, 0xFF4ADE80, 0.84f),
    ZokasThemePreset("alpine_pine", "18. Pinus Alpen", "Alam & Segar", true, 0xFF2DD4BF, 0xFF94A3B8, 0xFFFB923C, 0xFF071E22, 0xE60F2E34, 0xFF1A434B, 0xFF0F766E, 0xFF2DD4BF, 0.86f),
    ZokasThemePreset("pistachio_cream", "19. Krim Pistachio", "Alam & Segar", false, 0xFF15803D, 0xFF475569, 0xFFD97706, 0xFFF6FDF8, 0xF5FFFFFF, 0xFFDCFCE7, 0xFF16A34A, 0xFF4ADE80, 0.92f),
    ZokasThemePreset("seafoam_breeze", "20. Angin Laut Hijau", "Alam & Segar", false, 0xFF0F766E, 0xFF475569, 0xFFE11D48, 0xFFF2FBF9, 0xEBFFFFFF, 0xFFD5F5EE, 0xFF0F766E, 0xFF14B8A6, 0.88f),

    // BIRU & LANGIT (21 - 29)
    ZokasThemePreset("ocean_sapphire", "21. Safir Samudra", "Biru & Langit", false, 0xFF1D4ED8, 0xFF475569, 0xFFD97706, 0xFFF8FAFC, 0xF5FFFFFF, 0xFFDBEAFE, 0xFF1D4ED8, 0xFF3B82F6, 0.92f),
    ZokasThemePreset("sky_glass", "22. Kaca Langit Biru", "Biru & Langit", false, 0xFF0284C7, 0xFF475569, 0xFFEA580C, 0xFFF0F9FF, 0xE3FFFFFF, 0xFFE0F2FE, 0xFF0284C7, 0xFF38BDF8, 0.85f),
    ZokasThemePreset("midnight_navy", "23. Biru Tengah Malam", "Biru & Langit", true, 0xFF60A5FA, 0xFF94A3B8, 0xFFFBBF24, 0xFF091226, 0xE611203E, 0xFF1B325E, 0xFF2563EB, 0xFF60A5FA, 0.86f),
    ZokasThemePreset("cobalt_wave", "24. Gelombang Kobalt", "Biru & Langit", false, 0xFF2563EB, 0xFF4B5563, 0xFF059669, 0xFFF5F8FF, 0xF2FFFFFF, 0xFFE0E7FF, 0xFF1E40AF, 0xFF60A5FA, 0.90f),
    ZokasThemePreset("arctic_frost", "25. Embun Arktik", "Biru & Langit", false, 0xFF0369A1, 0xFF475569, 0xFF7C3AED, 0xFFF3FAFD, 0xE0FFFFFF, 0xFFD9EEF7, 0xFF0369A1, 0xFF06B6D4, 0.83f),
    ZokasThemePreset("deep_abyss", "26. Palung Laut Gelap", "Biru & Langit", true, 0xFF38BDF8, 0xFF94A3B8, 0xFFF472B6, 0xFF06131D, 0xE60C2233, 0xFF15364F, 0xFF0284C7, 0xFF38BDF8, 0.85f),
    ZokasThemePreset("cerulean_aqua", "27. Biru Cerulean", "Biru & Langit", false, 0xFF0891B2, 0xFF475569, 0xFFD97706, 0xFFECFEFF, 0xEBFFFFFF, 0xFFCFFAFE, 0xFF0891B2, 0xFF22D3EE, 0.88f),
    ZokasThemePreset("indigo_dusk", "28. Senja Indigo", "Biru & Langit", true, 0xFF818CF8, 0xFF94A3B8, 0xFF34D399, 0xFF0E1028, 0xE6191D42, 0xFF262C5E, 0xFF4F46E5, 0xFF818CF8, 0.86f),
    ZokasThemePreset("electric_cyan", "29. Sian Elektrik", "Biru & Langit", true, 0xFF22D3EE, 0xFF94A3B8, 0xFFFACC15, 0xFF071921, 0xE60E2B38, 0xFF174154, 0xFF06B6D4, 0xFF22D3EE, 0.85f),

    // KOPI & HANGAT (30 - 37)
    ZokasThemePreset("espresso_gold", "30. Kopi Espresso & Emas", "Kopi & Hangat", true, 0xFFFBBF24, 0xFFD6D3D1, 0xFF34D399, 0xFF19120D, 0xEB291E16, 0xFF3D2D21, 0xFFB45309, 0xFFF59E0B, 0.88f),
    ZokasThemePreset("caramel_latte", "31. Karamel Latte", "Kopi & Hangat", false, 0xFFB45309, 0xFF57534E, 0xFF059669, 0xFFFFFBF5, 0xF5FFFFFF, 0xFFFEF3C7, 0xFFB45309, 0xFFF59E0B, 0.92f),
    ZokasThemePreset("sunset_amber", "32. Senja Kuning Emas", "Kopi & Hangat", false, 0xFFD97706, 0xFF57534E, 0xFFDC2626, 0xFFFFFBEB, 0xEBFFFFFF, 0xFFFDE68A, 0xFFD97706, 0xFFFBBF24, 0.88f),
    ZokasThemePreset("terracotta_clay", "33. Terakota Hangat", "Kopi & Hangat", false, 0xFFC2410C, 0xFF57534E, 0xFF0D9488, 0xFFFFF7ED, 0xF2FFFFFF, 0xFFFFEDD5, 0xFFC2410C, 0xFFF97316, 0.90f),
    ZokasThemePreset("mocha_velvet", "34. Beludru Moka", "Kopi & Hangat", true, 0xFFFB923C, 0xFFD6D3D1, 0xFF38BDF8, 0xFF1C1310, 0xEB2E201B, 0xFF453129, 0xFFEA580C, 0xFFFB923C, 0.87f),
    ZokasThemePreset("honey_comb", "35. Madu Nusantara", "Kopi & Hangat", false, 0xFFA16207, 0xFF57534E, 0xFF15803D, 0xFFFEFCE8, 0xF2FFFFFF, 0xFFFEF08A, 0xFFCA8A04, 0xFFEAB308, 0.90f),
    ZokasThemePreset("cinnamon_spice", "36. Kayu Manis", "Kopi & Hangat", false, 0xFF9A3412, 0xFF57534E, 0xFF047857, 0xFFFAF6F2, 0xF5FFFFFF, 0xFFF5E6D8, 0xFF9A3412, 0xFFEA580C, 0.92f),
    ZokasThemePreset("peach_blossom", "37. Persik Transparan", "Kopi & Hangat", false, 0xFFEA580C, 0xFF57534E, 0xFF7C3AED, 0xFFFFF8F3, 0xE6FFFFFF, 0xFFFFE4D6, 0xFFEA580C, 0xFFFB923C, 0.85f),

    // MERAH & UNGU (38 - 44)
    ZokasThemePreset("crimson_ruby", "38. Merah Delima", "Merah & Ungu", false, 0xFFDC2626, 0xFF475569, 0xFFD97706, 0xFFFEF2F2, 0xF5FFFFFF, 0xFFFEE2E2, 0xFFDC2626, 0xFFF87171, 0.92f),
    ZokasThemePreset("rose_quartz_glass", "39. Kaca Mawar Kuarsa", "Merah & Ungu", false, 0xFFE11D48, 0xFF475569, 0xFF0284C7, 0xFFFFF1F2, 0xE3FFFFFF, 0xFFFFE4E6, 0xFFE11D48, 0xFFFB7185, 0.84f),
    ZokasThemePreset("velvet_maroon", "40. Marun Elegan", "Merah & Ungu", true, 0xFFFB7185, 0xFFCBD5E1, 0xFFFBBF24, 0xFF1F0B12, 0xEB33131E, 0xFF4D1D2E, 0xFFE11D48, 0xFFFB7185, 0.86f),
    ZokasThemePreset("royal_violet", "41. Ungu Violet", "Merah & Ungu", false, 0xFF7C3AED, 0xFF475569, 0xFF059669, 0xFFF5F3FF, 0xF2FFFFFF, 0xFFEDE9FE, 0xFF6D28D9, 0xFFA78BFA, 0.90f),
    ZokasThemePreset("lavender_mist", "42. Kabut Lavender", "Merah & Ungu", false, 0xFF6D28D9, 0xFF52525B, 0xFFD97706, 0xFFFAF5FF, 0xE6FFFFFF, 0xFFF3E8FF, 0xFF7E22CE, 0xFFC084FC, 0.85f),
    ZokasThemePreset("cyber_amethyst", "43. Kecubung Malam", "Merah & Ungu", true, 0xFFC084FC, 0xFFCBD5E1, 0xFF34D399, 0xFF150C24, 0xEB24153D, 0xFF37215C, 0xFF9333EA, 0xFFC084FC, 0.86f),
    ZokasThemePreset("cherry_blossom", "44. Bunga Sakura", "Merah & Ungu", false, 0xFFDB2777, 0xFF475569, 0xFF059669, 0xFFFDF2F8, 0xE8FFFFFF, 0xFFFCE7F3, 0xFFDB2777, 0xFFF472B6, 0.86f),

    // MODERN & ELEGAN (45 - 50)
    ZokasThemePreset("slate_corporate", "45. Abu-Abu Korporat", "Modern & Elegan", false, 0xFF334155, 0xFF64748B, 0xFF059669, 0xFFF8FAFC, 0xF7FFFFFF, 0xFFE2E8F0, 0xFF334155, 0xFF64748B, 0.95f),
    ZokasThemePreset("midnight_slate", "46. Slate Gelap Modern", "Modern & Elegan", true, 0xFF34D399, 0xFF94A3B8, 0xFFF59E0B, 0xFF0F172A, 0xF21E293B, 0xFF334155, 0xFF1E293B, 0xFF334155, 0.92f),
    ZokasThemePreset("pearl_white_glass", "47. Putih Mutiara Kaca", "Modern & Elegan", false, 0xFF047857, 0xFF475569, 0xFF1D4ED8, 0xFFF9FAFB, 0xDEFFFFFF, 0xFFE5E7EB, 0xFF047857, 0xFF10B981, 0.82f),
    ZokasThemePreset("aurora_borealis", "48. Aurora Gradasi", "Modern & Elegan", true, 0xFF2DD4BF, 0xFFCBD5E1, 0xFFA78BFA, 0xFF081822, 0xE3102938, 0xFF193E54, 0xFF0D9488, 0xFF7C3AED, 0.84f),
    ZokasThemePreset("amoled_pitch_black", "49. Hitam Pekat AMOLED", "Modern & Elegan", true, 0xFF34D399, 0xFFA1A1AA, 0xFFFBBF24, 0xFF000000, 0xF2121212, 0xFF222222, 0xFF059669, 0xFF10B981, 0.94f),
    ZokasThemePreset("crystal_prism", "50. Prisma Transparan", "Modern & Elegan", false, 0xFF2563EB, 0xFF475569, 0xFF059669, 0xFFF4F7FB, 0xDBFFFFFF, 0xFFDBEAFE, 0xFF2563EB, 0xFF059669, 0.80f)
)

object ZokasThemeManager {
    private const val PREFS_NAME = "zokas_theme_gallery_prefs"
    private const val KEY_THEME_ID = "selected_theme_id"

    var selectedThemeId by mutableStateOf("default_emerald")
        private set

    private var initialized = false

    fun ensureLoaded(context: Context) {
        if (!initialized) {
            val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_THEME_ID, "default_emerald") ?: "default_emerald"
            selectedThemeId = saved
            initialized = true
        }
    }

    fun selectTheme(context: Context, themeId: String) {
        selectedThemeId = themeId
        initialized = true
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_ID, themeId).apply()
    }

    fun getActivePreset(context: Context): ZokasThemePreset {
        ensureLoaded(context)
        return ZOKAS_50_THEMES.find { it.id == selectedThemeId } ?: ZOKAS_50_THEMES.first()
    }

    fun activeGradientBrush(context: Context): Brush {
        val preset = getActivePreset(context)
        return Brush.horizontalGradient(
            colors = listOf(
                Color(preset.gradientStartHex),
                Color(preset.gradientEndHex)
            )
        )
    }

    fun buildColorScheme(preset: ZokasThemePreset, forceDarkMode: Boolean): ColorScheme {
        val primary = Color(preset.primaryHex)
        val secondary = Color(preset.secondaryHex)
        val tertiary = Color(preset.tertiaryHex)

        // Jika pengguna memilih tema default ("default_emerald"), hormati toggle Dark Mode bawaan secara presisi
        if (preset.id == "default_emerald") {
            return if (forceDarkMode) {
                darkColorScheme(
                    primary = EmeraldGreenLight,
                    onPrimary = Color(0xFF022C22),
                    primaryContainer = EmeraldGreenDark,
                    onPrimaryContainer = Color(0xFFA7F3D0),
                    secondary = SlateGreyLight,
                    onSecondary = BackgroundDark,
                    secondaryContainer = SurfaceVariantDark,
                    onSecondaryContainer = Color(0xFFE2E8F0),
                    tertiary = AmberAccent,
                    onTertiary = Color(0xFF451A03),
                    tertiaryContainer = Color(0xFF78350F),
                    onTertiaryContainer = Color(0xFFFEF3C7),
                    background = BackgroundDark,
                    onBackground = Color(0xFFF1F5F9),
                    surface = SurfaceDark,
                    onSurface = Color(0xFFF1F5F9),
                    surfaceVariant = SurfaceVariantDark,
                    onSurfaceVariant = Color(0xFFE2E8F0),
                    surfaceTint = EmeraldGreenLight,
                    inverseSurface = Color(0xFFE2E8F0),
                    inverseOnSurface = BackgroundDark,
                    inversePrimary = EmeraldGreenDark,
                    error = Color(0xFFF87171),
                    onError = Color(0xFF450A0A),
                    errorContainer = Color(0xFF7F1D1D),
                    onErrorContainer = Color(0xFFFECACA),
                    outline = Color(0xFF94A3B8),
                    outlineVariant = Color(0xFF475569),
                    scrim = Color(0xFF000000)
                )
            } else {
                lightColorScheme(
                    primary = EmeraldGreen,
                    onPrimary = Color.White,
                    primaryContainer = EmeraldGreenLight,
                    onPrimaryContainer = Color(0xFF022C22),
                    secondary = SlateGrey,
                    onSecondary = Color.White,
                    secondaryContainer = SlateGreyLight,
                    onSecondaryContainer = SlateGreyDark,
                    tertiary = AmberAccent,
                    onTertiary = Color(0xFF1E293B),
                    background = BackgroundLight,
                    onBackground = Color(0xFF0F172A),
                    surface = SurfaceLight,
                    onSurface = Color(0xFF0F172A),
                    surfaceVariant = SlateSurface,
                    onSurfaceVariant = Color(0xFF334155),
                    outline = Color(0xFF64748B),
                    outlineVariant = Color(0xFFCBD5E1),
                    error = ErrorRed,
                    onError = Color.White
                )
            }
        }

        val bg = Color(preset.backgroundHex)
        val surf = Color(preset.surfaceHex).copy(alpha = preset.glassAlpha)
        val surfVar = Color(preset.surfaceVariantHex).copy(alpha = (preset.glassAlpha + 0.05f).coerceAtMost(1f))

        val onBg = readableOnColor(bg)
        val onSurf = readableOnColor(Color(preset.surfaceHex))
        val onSurfVar = readableSecondaryOnColor(Color(preset.surfaceVariantHex))
        val onPrim = readableOnColor(primary)
        val onSec = readableOnColor(secondary)
        val onTert = readableOnColor(tertiary)

        val primContainer = primary.copy(alpha = 0.20f)
        val onPrimContainer = onSurf

        return if (preset.isDark) {
            darkColorScheme(
                primary = primary,
                onPrimary = onPrim,
                primaryContainer = primContainer,
                onPrimaryContainer = onPrimContainer,
                secondary = secondary,
                onSecondary = onSec,
                secondaryContainer = secondary.copy(alpha = 0.22f),
                onSecondaryContainer = onSurf,
                tertiary = tertiary,
                onTertiary = onTert,
                tertiaryContainer = tertiary.copy(alpha = 0.22f),
                onTertiaryContainer = onSurf,
                background = bg,
                onBackground = onBg,
                surface = surf,
                onSurface = onSurf,
                surfaceVariant = surfVar,
                onSurfaceVariant = onSurfVar,
                outline = onSurfVar.copy(alpha = 0.55f),
                outlineVariant = onSurfVar.copy(alpha = 0.25f),
                error = Color(0xFFF87171),
                onError = Color(0xFF450A0A)
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = onPrim,
                primaryContainer = primContainer,
                onPrimaryContainer = onPrimContainer,
                secondary = secondary,
                onSecondary = onSec,
                secondaryContainer = secondary.copy(alpha = 0.18f),
                onSecondaryContainer = onSurf,
                tertiary = tertiary,
                onTertiary = onTert,
                tertiaryContainer = tertiary.copy(alpha = 0.18f),
                onTertiaryContainer = onSurf,
                background = bg,
                onBackground = onBg,
                surface = surf,
                onSurface = onSurf,
                surfaceVariant = surfVar,
                onSurfaceVariant = onSurfVar,
                outline = onSurfVar.copy(alpha = 0.55f),
                outlineVariant = onSurfVar.copy(alpha = 0.25f),
                error = ErrorRed,
                onError = Color.White
            )
        }
    }
}

// =====================================================================
// COMPOSABLE COLOR HELPERS (ADAPTIF TERHADAP SELURUH 50 TEMA)
// =====================================================================

@Composable
fun secondaryTextColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun accentTextColor(): Color = MaterialTheme.colorScheme.tertiary

@Composable
fun successTextColor(): Color = MaterialTheme.colorScheme.primary

@Composable
fun dangerTextColor(): Color = MaterialTheme.colorScheme.error

@Composable
fun mutedTextColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)

@Composable
fun subtleDividerColor(): Color = MaterialTheme.colorScheme.outlineVariant
