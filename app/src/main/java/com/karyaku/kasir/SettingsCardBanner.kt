package com.karyaku.kasir

import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

object SettingsUpgradeOneHelper {
    private const val PREFS_NAME = "zokas_upgrade_satu_prefs"
    private const val KEY_DETECTED_INITIALIZED = "initial_locale_detected"
    private const val KEY_SELECTED_LANG = "selected_lang"

    fun detectSystemLanguageCode(): String {
        val primaryLocale: Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val list: LocaleList = LocaleList.getDefault()
            if (!list.isEmpty) list[0] else Locale.getDefault()
        } else {
            Locale.getDefault()
        }
        return when (primaryLocale.language.lowercase(Locale.ROOT)) {
            "en" -> "en"
            "ms" -> "ms"
            "ar" -> "ar"
            "zh" -> "zh"
            "id", "in" -> "in"
            else -> "en"
        }
    }

    fun applyAutoLocaleOnFirstLaunch(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alreadyInitialized = prefs.getBoolean(KEY_DETECTED_INITIALIZED, false)
        if (!alreadyInitialized) {
            val detected = detectSystemLanguageCode()
            prefs.edit()
                .putBoolean(KEY_DETECTED_INITIALIZED, true)
                .putString(KEY_SELECTED_LANG, detected)
                .apply()
            ZokasLocaleManager.setLanguage(context, detected)
            return detected
        }
        val saved = prefs.getString(KEY_SELECTED_LANG, null)
        if (!saved.isNullOrBlank()) {
            ZokasLocaleManager.setLanguage(context, saved)
            return saved
        }
        return ZokasLocaleManager.ensureLoaded(context)
    }

    fun persistManualLanguage(context: Context, langCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_DETECTED_INITIALIZED, true)
            .putString(KEY_SELECTED_LANG, langCode)
            .apply()
        ZokasLocaleManager.setLanguage(context, langCode)
    }

    fun isRtlLanguage(langCode: String = ZokasLocaleManager.currentLanguage): Boolean {
        return langCode.equals("ar", ignoreCase = true)
    }

    fun layoutDirectionFor(langCode: String = ZokasLocaleManager.currentLanguage): LayoutDirection {
        return if (isRtlLanguage(langCode)) LayoutDirection.Rtl else LayoutDirection.Ltr
    }

    fun supportedLanguageOptions(): List<Pair<String, String>> = listOf(
        "in" to "Indonesia",
        "en" to "English",
        "ms" to "Bahasa Melayu",
        "zh" to "中文 (Mandarin)",
        "ar" to "العربية (Arabic)"
    )

    fun localizedPhrase(
        id: String,
        en: String,
        ms: String? = null,
        zh: String? = null,
        ar: String? = null,
        lang: String = ZokasLocaleManager.currentLanguage
    ): String {
        return when (lang.lowercase(Locale.ROOT)) {
            "en" -> en
            "ms" -> ms ?: en
            "zh" -> zh ?: en
            "ar" -> ar ?: en
            else -> id
        }
    }
}

@Composable
fun SettingCardArtworkBanner(
    @DrawableRes drawableRes: Int,
    tagText: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    remember(context) { SettingsUpgradeOneHelper.applyAutoLocaleOnFirstLaunch(context) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = tagText,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.24f)
                            )
                        )
                    )
            )
            if (!tagText.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = tagText,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
