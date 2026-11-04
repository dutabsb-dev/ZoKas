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

    var isPro: Boolean
        get() = prefs.getBoolean("IS_PRO", false)
        set(value) = prefs.edit().putBoolean("IS_PRO", value).apply()

    // TAMBAHAN BARU: Pengaturan Nama Toko & Pesan Bawah (Khusus Pro)
    var storeName: String
        get() = prefs.getString("STORE_NAME", "Aplikasi Kasir") ?: "Aplikasi Kasir"
        set(value) = prefs.edit().putString("STORE_NAME", value).apply()

    var storeFooter: String
        get() = prefs.getString("STORE_FOOTER", "Terima kasih telah berbelanja!") ?: "Terima kasih telah berbelanja!"
        set(value) = prefs.edit().putString("STORE_FOOTER", value).apply()
}