package com.karyaku.kasir

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pos_settings", Context.MODE_PRIVATE)

    var isDarkMode: Boolean
        get() = prefs.getBoolean("DARK_MODE", false)
        set(value) = prefs.edit().putBoolean("DARK_MODE", value).apply()

    var language: String
        get() = prefs.getString("LANGUAGE", "in") ?: "in"
        set(value) = prefs.edit().putString("LANGUAGE", value).apply()

    // TAMBAHKAN INI: Status apakah pengguna sudah beli versi Pro
    var isPro: Boolean
        get() = prefs.getBoolean("IS_PRO", false)
        set(value) = prefs.edit().putBoolean("IS_PRO", value).apply()
}