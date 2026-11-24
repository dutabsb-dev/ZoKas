package com.karyaku.kasir

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ZokasLocaleManager {
    private const val LEGACY_PREFS_NAME = "zokas_locale_prefs"
    private const val LEGACY_KEY_LANG = "app_language"

    var currentLanguage by mutableStateOf("in")
        private set

    fun ensureLoaded(context: Context): String {
        val settings = SettingsManager(context)
        val mainLang = settings.language
        val norm = if (mainLang.equals("en", ignoreCase = true)) "en" else "in"
        if (currentLanguage != norm) {
            currentLanguage = norm
        }
        return currentLanguage
    }

    fun setLanguage(context: Context, lang: String) {
        val norm = if (lang.equals("en", ignoreCase = true)) "en" else "in"
        currentLanguage = norm
        SettingsManager(context).language = norm
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LEGACY_KEY_LANG, norm)
            .apply()
    }

    fun toggleLanguage(context: Context): String {
        val next = if (isEnglish()) "in" else "en"
        setLanguage(context, next)
        return next
    }

    fun isEnglish(): Boolean = currentLanguage.equals("en", ignoreCase = true)
}

fun zokasText(id: String, en: String, lang: String = ZokasLocaleManager.currentLanguage): String {
    return if (lang.equals("en", ignoreCase = true)) en else id
}

fun translateBusinessTypeLabel(rawType: String, lang: String = ZokasLocaleManager.currentLanguage): String {
    if (!lang.equals("en", ignoreCase = true)) {
        return when (rawType.uppercase()) {
            "KULINER" -> "Kuliner (Restoran, Cafe, Warung)"
            "RETAIL" -> "Retail (Toko, Minimarket, Bangunan)"
            "JASA" -> "Jasa (Salon, Laundry, Servis)"
            "MUSIMAN" -> "Musiman (Event, Toko Kue)"
            else -> rawType
        }
    }
    return when (rawType.uppercase()) {
        "KULINER" -> "Culinary (Restaurant, Cafe, Warung)"
        "RETAIL" -> "Retail (Store, Minimarket, Hardware)"
        "JASA" -> "Services (Salon, Laundry, Repair)"
        "MUSIMAN" -> "Seasonal (Events, Pastry Shop)"
        else -> rawType
    }
}

fun translateCategoryLabel(category: String, lang: String = ZokasLocaleManager.currentLanguage): String {
    if (!lang.equals("en", ignoreCase = true)) return category
    return when (category.trim().lowercase()) {
        "semua" -> "All"
        "makanan" -> "Food"
        "minuman" -> "Beverages"
        "alat tulis" -> "Stationery"
        "jasa" -> "Services"
        "sembako" -> "Groceries"
        "elektronik" -> "Electronics"
        "pakaian" -> "Apparel"
        "kebersihan" -> "Cleaning"
        "kesehatan" -> "Health"
        "kecantikan" -> "Beauty"
        "otomotif" -> "Automotive"
        else -> category
    }
}
