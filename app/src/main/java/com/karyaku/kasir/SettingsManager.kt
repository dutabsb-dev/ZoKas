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

    var storeName: String
        get() = prefs.getString("STORE_NAME", "ZoKas") ?: "ZoKas"
        set(value) = prefs.edit().putString("STORE_NAME", value).apply()

    var storeFooter: String
        get() = prefs.getString("STORE_FOOTER", "Terima kasih telah berbelanja!") ?: "Terima kasih telah berbelanja!"
        set(value) = prefs.edit().putString("STORE_FOOTER", value).apply()

    var storeAddress: String
        get() = prefs.getString("STORE_ADDRESS", "Jl. Raya UMKM No. 123") ?: "Jl. Raya UMKM No. 123"
        set(value) = prefs.edit().putString("STORE_ADDRESS", value).apply()

    var storePhone: String
        get() = prefs.getString("STORE_PHONE", "081234567890") ?: "081234567890"
        set(value) = prefs.edit().putString("STORE_PHONE", value).apply()

    var printerMacAddress: String?
        get() = prefs.getString("PRINTER_MAC", null)
        set(value) = prefs.edit().putString("PRINTER_MAC", value).apply()

    // TAMBAHAN BARU: Persentase Pajak (Default 0% karena UMKM sering tidak memungut pajak)
    var taxPercent: Double
        get() = prefs.getFloat("TAX_PERCENT", 0f).toDouble()
        set(value) = prefs.edit().putFloat("TAX_PERCENT", value.toFloat()).apply()
}