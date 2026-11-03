package com.karyaku.kasir

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pos_settings", Context.MODE_PRIVATE)

    // Pengaturan Tema
    var isDarkMode: Boolean
        get() = prefs.getBoolean("DARK_MODE", false)
        set(value) = prefs.edit().putBoolean("DARK_MODE", value).apply()

    // Pengaturan Bahasa ("in" untuk Indonesia, "en" untuk English)
    var language: String
        get() = prefs.getString("LANGUAGE", "in") ?: "in"
        set(value) = prefs.edit().putString("LANGUAGE", value).apply()
}