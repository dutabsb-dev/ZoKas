package com.karyaku.kasir

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("zokas_prefs", Context.MODE_PRIVATE)

    var isPro: Boolean
        get() = prefs.getBoolean("is_pro", false)
        set(value) = prefs.edit().putBoolean("is_pro", value).apply()

    var isDarkMode: Boolean
        get() = prefs.getBoolean("dark_mode", false)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()

    var language: String
        get() = prefs.getString("language", "in") ?: "in"
        set(value) = prefs.edit().putString("language", value).apply()

    // FITUR BARU: High Contrast & UI Scale
    var isHighContrast: Boolean
        get() = prefs.getBoolean("high_contrast", false)
        set(value) = prefs.edit().putBoolean("high_contrast", value).apply()

    var uiScale: Float
        get() = prefs.getFloat("ui_scale", 1.0f)
        set(value) = prefs.edit().putFloat("ui_scale", value).apply()

    // ... (Sisa variabel storeName, printer, dll tetap sama seperti sebelumnya) ...
    var storeName: String?
        get() = prefs.getString("store_name", "ZoKas Store")
        set(value) = prefs.edit().putString("store_name", value).apply()

    var storeAddress: String?
        get() = prefs.getString("store_address", "Jl. Raya UMKM No. 123")
        set(value) = prefs.edit().putString("store_address", value).apply()

    var storePhone: String?
        get() = prefs.getString("store_phone", "081234567890")
        set(value) = prefs.edit().putString("store_phone", value).apply()

    var storeFooter: String?
        get() = prefs.getString("store_footer", "Terima kasih telah berbelanja!")
        set(value) = prefs.edit().putString("store_footer", value).apply()

    var taxPercent: Double
        get() = prefs.getFloat("tax_percent", 0f).toDouble()
        set(value) = prefs.edit().putFloat("tax_percent", value.toFloat()).apply()

    var printerMacAddress: String?
        get() = prefs.getString("printer_mac", null)
        set(value) = prefs.edit().putString("printer_mac", value).apply()

    var kitchenPrinterMacAddress: String?
        get() = prefs.getString("kitchen_printer_mac", null)
        set(value) = prefs.edit().putString("kitchen_printer_mac", value).apply()

    var happyHourStart: String
        get() = prefs.getString("happy_start", "17:00") ?: "17:00"
        set(value) = prefs.edit().putString("happy_start", value).apply()

    var happyHourEnd: String
        get() = prefs.getString("happy_end", "19:00") ?: "19:00"
        set(value) = prefs.edit().putString("happy_end", value).apply()

    var happyHourDiscount: Double
        get() = prefs.getFloat("happy_discount", 0f).toDouble()
        set(value) = prefs.edit().putFloat("happy_discount", value.toFloat()).apply()

    var requirePinForDiscount: Boolean
        get() = prefs.getBoolean("pin_for_discount", true)
        set(value) = prefs.edit().putBoolean("pin_for_discount", value).apply()

    var lastAutoBackup: Long
        get() = prefs.getLong("last_auto_backup", 0L)
        set(value) = prefs.edit().putLong("last_auto_backup", value).apply()
}