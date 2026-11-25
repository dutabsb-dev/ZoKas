package com.karyaku.kasir

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ZokasLocaleManager {
    private const val PREFS_NAME = "zokas_locale_prefs"
    private const val KEY_LANG = "app_language"

    var currentLanguage by mutableStateOf("id")
        private set

    fun ensureLoaded(context: Context): String {
        val saved = LanguageManager.loadSavedLanguage(context)
        currentLanguage = saved
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, saved)
            .apply()
        return currentLanguage
    }

    fun setLanguage(context: Context, lang: String) {
        val norm = LanguageManager.normalizeLang(lang)
        currentLanguage = norm
        LanguageManager.setLanguage(context, norm)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, norm)
            .apply()
    }

    fun toggleLanguage(context: Context): String {
        val order = listOf("id", "en", "jv", "su", "mad")
        val idx = order.indexOf(LanguageManager.normalizeLang(currentLanguage))
        val next = order[(if (idx >= 0) idx + 1 else 1) % order.size]
        setLanguage(context, next)
        return next
    }

    fun isEnglish(): Boolean = LanguageManager.normalizeLang(currentLanguage) == "en"
}

fun zokasText(id: String, en: String? = null, lang: String = ZokasLocaleManager.currentLanguage): String {
    val norm = LanguageManager.normalizeLang(lang)
    return when (norm) {
        "id" -> id
        "en" -> en ?: LanguageManager.autoTranslateText(id, "en")
        else -> LanguageManager.autoTranslateText(id, norm)
    }
}

fun translateBusinessTypeLabel(rawType: String, lang: String = ZokasLocaleManager.currentLanguage): String {
    val baseLabel = when (rawType.uppercase()) {
        "KULINER" -> "Kuliner (Restoran, Cafe, Warung)"
        "RETAIL" -> "Retail (Toko, Minimarket, Bangunan)"
        "JASA" -> "Jasa (Salon, Laundry, Servis)"
        "MUSIMAN" -> "Musiman (Event, Toko Kue)"
        else -> rawType
    }
    return zokasText(baseLabel, null, lang)
}

fun translateCategoryLabel(category: String, lang: String = ZokasLocaleManager.currentLanguage): String {
    if (LanguageManager.normalizeLang(lang) == "id") return category
    return zokasText(category, null, lang)
}
