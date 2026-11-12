package com.karyaku.kasir

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("zokas_prefs", Context.MODE_PRIVATE)

    var isSetupCompleted: Boolean get() = prefs.getBoolean("is_setup_completed", false); set(value) = prefs.edit().putBoolean("is_setup_completed", value).apply()
    var isBiometricEnabled: Boolean get() = prefs.getBoolean("is_biometric_enabled", false); set(value) = prefs.edit().putBoolean("is_biometric_enabled", value).apply()
    var businessType: String get() = prefs.getString("business_type", "KULINER") ?: "KULINER"; set(value) = prefs.edit().putString("business_type", value).apply()

    var isDarkMode: Boolean get() = prefs.getBoolean("dark_mode", false); set(value) = prefs.edit().putBoolean("dark_mode", value).apply()
    var isHighContrast: Boolean get() = prefs.getBoolean("high_contrast", false); set(value) = prefs.edit().putBoolean("high_contrast", value).apply()
    var uiScale: Float get() = prefs.getFloat("ui_scale", 1.0f); set(value) = prefs.edit().putFloat("ui_scale", value).apply()
    var language: String get() = prefs.getString("language", "in") ?: "in"; set(value) = prefs.edit().putString("language", value).apply()
    
    // FIX: Hapus dummy string, gunakan string kosong
    var storeName: String? get() = prefs.getString("store_name", ""); set(value) = prefs.edit().putString("store_name", value).apply()
    var storeAddress: String? get() = prefs.getString("store_address", ""); set(value) = prefs.edit().putString("store_address", value).apply()
    var storePhone: String? get() = prefs.getString("store_phone", ""); set(value) = prefs.edit().putString("store_phone", value).apply()
    var storeFooter: String? get() = prefs.getString("store_footer", ""); set(value) = prefs.edit().putString("store_footer", value).apply()
    var storeLogoPath: String? get() = prefs.getString("store_logo_path", null); set(value) = prefs.edit().putString("store_logo_path", value).apply()
    
    var paymentMethods: Set<String> 
        get() = prefs.getStringSet("payment_methods", setOf("CASH", "QRIS", "Bank Transfer")) ?: setOf("CASH", "QRIS", "Bank Transfer")
        set(value) = prefs.edit().putStringSet("payment_methods", value).apply()
    
    var taxPercent: Double get() = prefs.getFloat("tax_percent", 0f).toDouble(); set(value) = prefs.edit().putFloat("tax_percent", value.toFloat()).apply()
    var serviceChargePercent: Double get() = prefs.getFloat("service_charge", 0f).toDouble(); set(value) = prefs.edit().putFloat("service_charge", value.toFloat()).apply()
    
    var printerMacAddress: String? get() = prefs.getString("printer_mac", null); set(value) = prefs.edit().putString("printer_mac", value).apply()
    var kitchenPrinterMacAddress: String? get() = prefs.getString("kitchen_printer_mac", null); set(value) = prefs.edit().putString("kitchen_printer_mac", value).apply()
    var happyHourStart: String get() = prefs.getString("happy_start", "17:00") ?: "17:00"; set(value) = prefs.edit().putString("happy_start", value).apply()
    var happyHourEnd: String get() = prefs.getString("happy_end", "19:00") ?: "19:00"; set(value) = prefs.edit().putString("happy_end", value).apply()
    var happyHourDiscount: Double get() = prefs.getFloat("happy_discount", 0f).toDouble(); set(value) = prefs.edit().putFloat("happy_discount", value.toFloat()).apply()
    var requirePinForDiscount: Boolean get() = prefs.getBoolean("pin_for_discount", true); set(value) = prefs.edit().putBoolean("pin_for_discount", value).apply()
    var lastAutoBackup: Long get() = prefs.getLong("last_auto_backup", 0L); set(value) = prefs.edit().putLong("last_auto_backup", value).apply()

    var isTableModeEnabled: Boolean get() = prefs.getBoolean("table_mode_enabled", true); set(value) = prefs.edit().putBoolean("table_mode_enabled", value).apply()
    var maxDiscountCap: Long get() = prefs.getLong("max_discount_cap", 0L); set(value) = prefs.edit().putLong("max_discount_cap", value).apply()
    var enableMultiCurrency: Boolean get() = prefs.getBoolean("enable_multi_currency", false); set(value) = prefs.edit().putBoolean("enable_multi_currency", value).apply()
    var exchangeRateUsd: Double get() = prefs.getFloat("exchange_rate_usd", 15000f).toDouble(); set(value) = prefs.edit().putFloat("exchange_rate_usd", value.toFloat()).apply()

    // FITUR MODUL
    var enableOrderTypes: Boolean get() = prefs.getBoolean("enable_order_types", true); set(value) = prefs.edit().putBoolean("enable_order_types", value).apply()
    var enableWholesale: Boolean get() = prefs.getBoolean("enable_wholesale", false); set(value) = prefs.edit().putBoolean("enable_wholesale", value).apply()
    var enablePreOrder: Boolean get() = prefs.getBoolean("enable_pre_order", false); set(value) = prefs.edit().putBoolean("enable_pre_order", value).apply()
    var enableMembership: Boolean get() = prefs.getBoolean("enable_membership", true); set(value) = prefs.edit().putBoolean("enable_membership", value).apply()
    var enableSupplier: Boolean get() = prefs.getBoolean("enable_supplier", false); set(value) = prefs.edit().putBoolean("enable_supplier", value).apply()
    var enableReservation: Boolean get() = prefs.getBoolean("enable_reservation", true); set(value) = prefs.edit().putBoolean("enable_reservation", value).apply()
    var enableFinance: Boolean get() = prefs.getBoolean("enable_finance", true); set(value) = prefs.edit().putBoolean("enable_finance", value).apply()
    
    // FITUR QRIS & TRANSFER
    var qrisStaticPayload: String? get() = prefs.getString("qris_static_payload", null); set(value) = prefs.edit().putString("qris_static_payload", value).apply()
    var enableQrisAutoDetect: Boolean get() = prefs.getBoolean("enable_qris_auto_detect", false); set(value) = prefs.edit().putBoolean("enable_qris_auto_detect", value).apply()

    // FIX: Hapus dummy bank
    var bankName: String? get() = prefs.getString("bank_name", ""); set(value) = prefs.edit().putString("bank_name", value).apply()
    var bankAccountNumber: String? get() = prefs.getString("bank_account_number", ""); set(value) = prefs.edit().putString("bank_account_number", value).apply()
    var bankAccountName: String? get() = prefs.getString("bank_account_name", ""); set(value) = prefs.edit().putString("bank_account_name", value).apply()
    var enableTransferAutoDetect: Boolean get() = prefs.getBoolean("enable_transfer_auto_detect", false); set(value) = prefs.edit().putBoolean("enable_transfer_auto_detect", value).apply()
}