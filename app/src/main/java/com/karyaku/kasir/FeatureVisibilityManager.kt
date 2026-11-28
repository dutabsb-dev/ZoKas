package com.karyaku.kasir

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class ZokasUsagePreset(
    val key: String,
    val titleId: String,
    val titleEn: String,
    val descId: String,
    val descEn: String
) {
    NOTA_SAJA(
        "NOTA_SAJA",
        "Hanya Nota & Struk",
        "Notes & Receipts Only",
        "Fokus buat, cetak & bagikan nota/struk/invoice tanpa kerumitan shift, laci, stok, atau akuntansi.",
        "Focus on creating, printing & sharing notes/receipts without shift, drawer, stock, or accounting clutter."
    ),
    KASIR_TUNAI_KILAT(
        "KASIR_TUNAI_KILAT",
        "Kasir Tunai Kilat",
        "Quick Cash POS",
        "Fokus jual-beli tunai cepat, hitung kembalian otomatis & cetak struk.",
        "Fast cash checkout with automatic change calculation & receipt printing."
    ),
    KASIR_MULTI_BAYAR(
        "KASIR_MULTI_BAYAR",
        "Kasir Multi-Bayar & Bon/DP",
        "Multi-Pay & Credit/DP POS",
        "Mendukung Tunai, QRIS, Transfer Bank, EDC, serta catatan DP & kas bon pelanggan.",
        "Supports Cash, QRIS, Bank Transfer, EDC, plus customer down payments & credit."
    ),
    STOK_DAN_PRODUKSI(
        "STOK_DAN_PRODUKSI",
        "Stok, Bahan Baku & Kulakan",
        "Inventory, BOM & Purchasing",
        "Kontrol stok barang, varian/satuan, resep BOM, rendemen/susut & supplier PO.",
        "Control inventory, variants/units, BOM recipes, yield/shrinkage & supplier POs."
    ),
    KULINER_MEJA_KDS(
        "KULINER_MEJA_KDS",
        "Kuliner, Meja & Dapur (KDS)",
        "Culinary, Tables & Kitchen",
        "Alur makan di tempat/bungkus, nomor meja, tiket dapur KDS & kasir harian.",
        "Dine-in/take-away flow, table numbers, KDS kitchen tickets & daily cashier."
    ),
    LAPORAN_KEUANGAN(
        "LAPORAN_KEUANGAN",
        "Pembukuan & Laporan Usaha",
        "Bookkeeping & Financial Reports",
        "Rekap omzet, HPP/modal, arus kas, biaya operasional, laba rugi & ekspor pembukuan.",
        "Revenue summary, COGS, cash flow, operating expenses, P&L & bookkeeping export."
    ),
    LENGKAP_MULTI_UNIT(
        "LENGKAP_MULTI_UNIT",
        "Usaha Lengkap / Multi-Tim & Cabang",
        "Full Suite / Multi-Team & Branch",
        "Seluruh modul lengkap termasuk HR/gaji, CRM member, multi-cabang & pantauan HP Owner.",
        "All modules enabled including HR/payroll, CRM loyalty, multi-branch & Owner monitor."
    ),
    CUSTOM(
        "CUSTOM",
        "Kustom (Pilihan Sendiri)",
        "Custom Selection",
        "Kombinasi fitur yang disesuaikan secara manual oleh pengguna.",
        "Manually tailored combination of features."
    );

    companion object {
        fun fromKey(raw: String?): ZokasUsagePreset {
            return entries.find { it.key.equals(raw, ignoreCase = true) } ?: LENGKAP_MULTI_UNIT
        }
    }
}

data class FeatureVisibilityConfig(
    val presetKey: String = ZokasUsagePreset.LENGKAP_MULTI_UNIT.key,
    val receiptOnlyMode: Boolean = false,
    val receiptHistoryEnabled: Boolean = true,
    val posCheckoutEnabled: Boolean = true,
    val splitBillEnabled: Boolean = true,
    val discountVoucherEnabled: Boolean = true,
    val holdOrderEnabled: Boolean = true,
    val shiftAndDrawerEnabled: Boolean = true,
    val cashFlowEnabled: Boolean = true,
    val productCatalogEnabled: Boolean = true,
    val variantsAndStockEnabled: Boolean = true,
    val bomAndRawMaterialEnabled: Boolean = true,
    val supplierPoEnabled: Boolean = true,
    val reportsAndProfitEnabled: Boolean = true,
    val bookkeepingExportEnabled: Boolean = true,
    val crmEnabled: Boolean = true,
    val hrPayrollEnabled: Boolean = true,
    val multiBranchEnabled: Boolean = true,
    val remoteSyncEnabled: Boolean = true,
    val kitchenAndTableEnabled: Boolean = true
) {
    fun activeGroupCount(): Int {
        var count = 1 // Nota & Struk selalu tersedia
        if (posCheckoutEnabled) count++
        if (shiftAndDrawerEnabled || cashFlowEnabled) count++
        if (productCatalogEnabled || variantsAndStockEnabled) count++
        if (bomAndRawMaterialEnabled || supplierPoEnabled) count++
        if (kitchenAndTableEnabled) count++
        if (reportsAndProfitEnabled || bookkeepingExportEnabled) count++
        if (crmEnabled || hrPayrollEnabled || multiBranchEnabled || remoteSyncEnabled) count++
        return count
    }
}

object FeatureVisibilityManager {
    private const val PREFS_NAME = "zokas_feature_visibility_v1"
    private const val KEY_CONFIG_JSON = "feature_visibility_json"

    private val _configFlow = MutableStateFlow(FeatureVisibilityConfig())
    val configFlow: StateFlow<FeatureVisibilityConfig> = _configFlow.asStateFlow()

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context): FeatureVisibilityConfig {
        if (!loaded) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_CONFIG_JSON, null)
            val parsed = if (raw.isNullOrBlank()) {
                // Default aman bagi user lama agar tidak kehilangan fitur mendadak
                FeatureVisibilityConfig()
            } else {
                fromJsonString(raw)
            }
            _configFlow.value = parsed
            loaded = true
        }
        return _configFlow.value
    }

    fun getConfig(context: Context): FeatureVisibilityConfig = ensureLoaded(context)

    fun saveConfig(context: Context, config: FeatureVisibilityConfig) {
        _configFlow.value = config
        loaded = true
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONFIG_JSON, toJsonString(config))
            .apply()
    }

    fun applyPreset(context: Context, preset: ZokasUsagePreset, isCulinary: Boolean = false): FeatureVisibilityConfig {
        val cfg = configForPreset(preset, isCulinary)
        saveConfig(context, cfg)
        return cfg
    }

    fun configForPreset(preset: ZokasUsagePreset, isCulinary: Boolean = false): FeatureVisibilityConfig {
        return when (preset) {
            ZokasUsagePreset.NOTA_SAJA -> FeatureVisibilityConfig(
                presetKey = preset.key,
                receiptOnlyMode = true,
                receiptHistoryEnabled = true,
                posCheckoutEnabled = true,
                splitBillEnabled = false,
                discountVoucherEnabled = true,
                holdOrderEnabled = false,
                shiftAndDrawerEnabled = false,
                cashFlowEnabled = false,
                productCatalogEnabled = true,
                variantsAndStockEnabled = false,
                bomAndRawMaterialEnabled = false,
                supplierPoEnabled = false,
                reportsAndProfitEnabled = false,
                bookkeepingExportEnabled = false,
                crmEnabled = false,
                hrPayrollEnabled = false,
                multiBranchEnabled = false,
                remoteSyncEnabled = false,
                kitchenAndTableEnabled = false
            )
            ZokasUsagePreset.KASIR_TUNAI_KILAT -> FeatureVisibilityConfig(
                presetKey = preset.key,
                receiptOnlyMode = false,
                posCheckoutEnabled = true,
                splitBillEnabled = false,
                discountVoucherEnabled = true,
                holdOrderEnabled = false,
                shiftAndDrawerEnabled = true,
                cashFlowEnabled = false,
                productCatalogEnabled = true,
                variantsAndStockEnabled = true,
                bomAndRawMaterialEnabled = false,
                supplierPoEnabled = false,
                reportsAndProfitEnabled = true,
                bookkeepingExportEnabled = false,
                crmEnabled = false,
                hrPayrollEnabled = false,
                multiBranchEnabled = false,
                remoteSyncEnabled = false,
                kitchenAndTableEnabled = false
            )
            ZokasUsagePreset.KASIR_MULTI_BAYAR -> FeatureVisibilityConfig(
                presetKey = preset.key,
                receiptOnlyMode = false,
                posCheckoutEnabled = true,
                splitBillEnabled = true,
                discountVoucherEnabled = true,
                holdOrderEnabled = true,
                shiftAndDrawerEnabled = true,
                cashFlowEnabled = true,
                productCatalogEnabled = true,
                variantsAndStockEnabled = true,
                bomAndRawMaterialEnabled = false,
                supplierPoEnabled = false,
                reportsAndProfitEnabled = true,
                bookkeepingExportEnabled = true,
                crmEnabled = true,
                hrPayrollEnabled = false,
                multiBranchEnabled = false,
                remoteSyncEnabled = false,
                kitchenAndTableEnabled = isCulinary
            )
            ZokasUsagePreset.STOK_DAN_PRODUKSI -> FeatureVisibilityConfig(
                presetKey = preset.key,
                receiptOnlyMode = false,
                posCheckoutEnabled = true,
                splitBillEnabled = false,
                discountVoucherEnabled = true,
                holdOrderEnabled = true,
                shiftAndDrawerEnabled = true,
                cashFlowEnabled = true,
                productCatalogEnabled = true,
                variantsAndStockEnabled = true,
                bomAndRawMaterialEnabled = true,
                supplierPoEnabled = true,
                reportsAndProfitEnabled = true,
                bookkeepingExportEnabled = true,
                crmEnabled = false,
                hrPayrollEnabled = false,
                multiBranchEnabled = false,
                remoteSyncEnabled = false,
                kitchenAndTableEnabled = isCulinary
            )
            ZokasUsagePreset.KULINER_MEJA_KDS -> FeatureVisibilityConfig(
                presetKey = preset.key,
                receiptOnlyMode = false,
                posCheckoutEnabled = true,
                splitBillEnabled = true,
                discountVoucherEnabled = true,
                holdOrderEnabled = true,
                shiftAndDrawerEnabled = true,
                cashFlowEnabled = true,
                productCatalogEnabled = true,
                variantsAndStockEnabled = true,
                bomAndRawMaterialEnabled = true,
                supplierPoEnabled = false,
                reportsAndProfitEnabled = true,
                bookkeepingExportEnabled = false,
                crmEnabled = false,
                hrPayrollEnabled = false,
                multiBranchEnabled = false,
                remoteSyncEnabled = false,
                kitchenAndTableEnabled = true
            )
            ZokasUsagePreset.LAPORAN_KEUANGAN -> FeatureVisibilityConfig(
                presetKey = preset.key,
                receiptOnlyMode = false,
                posCheckoutEnabled = true,
                splitBillEnabled = false,
                discountVoucherEnabled = true,
                holdOrderEnabled = false,
                shiftAndDrawerEnabled = true,
                cashFlowEnabled = true,
                productCatalogEnabled = true,
                variantsAndStockEnabled = true,
                bomAndRawMaterialEnabled = false,
                supplierPoEnabled = true,
                reportsAndProfitEnabled = true,
                bookkeepingExportEnabled = true,
                crmEnabled = false,
                hrPayrollEnabled = false,
                multiBranchEnabled = false,
                remoteSyncEnabled = false,
                kitchenAndTableEnabled = isCulinary
            )
            ZokasUsagePreset.LENGKAP_MULTI_UNIT,
            ZokasUsagePreset.CUSTOM -> FeatureVisibilityConfig(
                presetKey = ZokasUsagePreset.LENGKAP_MULTI_UNIT.key,
                receiptOnlyMode = false,
                posCheckoutEnabled = true,
                splitBillEnabled = true,
                discountVoucherEnabled = true,
                holdOrderEnabled = true,
                shiftAndDrawerEnabled = true,
                cashFlowEnabled = true,
                productCatalogEnabled = true,
                variantsAndStockEnabled = true,
                bomAndRawMaterialEnabled = true,
                supplierPoEnabled = true,
                reportsAndProfitEnabled = true,
                bookkeepingExportEnabled = true,
                crmEnabled = true,
                hrPayrollEnabled = true,
                multiBranchEnabled = true,
                remoteSyncEnabled = true,
                kitchenAndTableEnabled = true
            )
        }
    }

    fun recommendPresetForBusiness(biz: IndonesiaBusinessItem): Pair<ZokasUsagePreset, String> {
        val lowerName = biz.name.lowercase()
        return when {
            lowerName.contains("fotokopi") || lowerName.contains("cetak") || lowerName.contains("sablon") ||
                lowerName.contains("nota") || lowerName.contains("percetakan") ->
                ZokasUsagePreset.NOTA_SAJA to "Cocok untuk cetak & kirim nota/invoice pesanan dengan cepat tanpa kerumitan kasir shift."

            biz.mappedBusinessType.equals("KULINER", ignoreCase = true) ->
                ZokasUsagePreset.KULINER_MEJA_KDS to "Mendukung alur makan di tempat/bungkus, nomor meja, dan tiket dapur (KDS)."

            lowerName.contains("ayam potong") || lowerName.contains("daging") || lowerName.contains("ikan") ||
                lowerName.contains("roti") || lowerName.contains("kue") || lowerName.contains("catering") ||
                lowerName.contains("produksi") || lowerName.contains("pakan") || lowerName.contains("bangunan") ->
                ZokasUsagePreset.STOK_DAN_PRODUKSI to "Mendukung kontrol stok, satuan/timbangan, bahan baku & kulakan supplier."

            biz.mappedBusinessType.equals("JASA", ignoreCase = true) ->
                ZokasUsagePreset.KASIR_MULTI_BAYAR to "Mendukung pembayaran Tunai, QRIS, Transfer Bank, serta DP/Bon pelanggan."

            lowerName.contains("grosir") ->
                ZokasUsagePreset.LAPORAN_KEUANGAN to "Memudahkan pantauan omzet, HPP, arus kas, dan laporan pembukuan usaha."

            else ->
                ZokasUsagePreset.KASIR_TUNAI_KILAT to "Ringkas dan cepat untuk transaksi harian warung/toko tanpa menu yang membingungkan."
        }
    }

    fun syncToSettingsManager(context: Context, settingsManager: SettingsManager, config: FeatureVisibilityConfig) {
        saveConfig(context, config)
        if (config.receiptOnlyMode) {
            settingsManager.appMode = "AWAM"
        }
        settingsManager.enableFinance = config.cashFlowEnabled || config.reportsAndProfitEnabled
        settingsManager.enableWholesale = config.variantsAndStockEnabled
        settingsManager.enableSupplier = config.supplierPoEnabled
        settingsManager.isTableModeEnabled = config.kitchenAndTableEnabled && settingsManager.businessType.equals("KULINER", ignoreCase = true)
        settingsManager.enableOrderTypes = config.kitchenAndTableEnabled
        settingsManager.enablePreOrder = config.holdOrderEnabled
        settingsManager.enableCrmModule = config.crmEnabled
        settingsManager.enableMembership = config.crmEnabled
        settingsManager.enableHrModule = config.hrPayrollEnabled
        settingsManager.setBomModuleEnabled(config.bomAndRawMaterialEnabled)
    }

    private fun toJsonString(c: FeatureVisibilityConfig): String {
        return JSONObject().apply {
            put("presetKey", c.presetKey)
            put("receiptOnlyMode", c.receiptOnlyMode)
            put("receiptHistoryEnabled", c.receiptHistoryEnabled)
            put("posCheckoutEnabled", c.posCheckoutEnabled)
            put("splitBillEnabled", c.splitBillEnabled)
            put("discountVoucherEnabled", c.discountVoucherEnabled)
            put("holdOrderEnabled", c.holdOrderEnabled)
            put("shiftAndDrawerEnabled", c.shiftAndDrawerEnabled)
            put("cashFlowEnabled", c.cashFlowEnabled)
            put("productCatalogEnabled", c.productCatalogEnabled)
            put("variantsAndStockEnabled", c.variantsAndStockEnabled)
            put("bomAndRawMaterialEnabled", c.bomAndRawMaterialEnabled)
            put("supplierPoEnabled", c.supplierPoEnabled)
            put("reportsAndProfitEnabled", c.reportsAndProfitEnabled)
            put("bookkeepingExportEnabled", c.bookkeepingExportEnabled)
            put("crmEnabled", c.crmEnabled)
            put("hrPayrollEnabled", c.hrPayrollEnabled)
            put("multiBranchEnabled", c.multiBranchEnabled)
            put("remoteSyncEnabled", c.remoteSyncEnabled)
            put("kitchenAndTableEnabled", c.kitchenAndTableEnabled)
        }.toString()
    }

    private fun fromJsonString(raw: String): FeatureVisibilityConfig {
        return runCatching {
            val o = JSONObject(raw)
            FeatureVisibilityConfig(
                presetKey = o.optString("presetKey", ZokasUsagePreset.LENGKAP_MULTI_UNIT.key),
                receiptOnlyMode = o.optBoolean("receiptOnlyMode", false),
                receiptHistoryEnabled = o.optBoolean("receiptHistoryEnabled", true),
                posCheckoutEnabled = o.optBoolean("posCheckoutEnabled", true),
                splitBillEnabled = o.optBoolean("splitBillEnabled", true),
                discountVoucherEnabled = o.optBoolean("discountVoucherEnabled", true),
                holdOrderEnabled = o.optBoolean("holdOrderEnabled", true),
                shiftAndDrawerEnabled = o.optBoolean("shiftAndDrawerEnabled", true),
                cashFlowEnabled = o.optBoolean("cashFlowEnabled", true),
                productCatalogEnabled = o.optBoolean("productCatalogEnabled", true),
                variantsAndStockEnabled = o.optBoolean("variantsAndStockEnabled", true),
                bomAndRawMaterialEnabled = o.optBoolean("bomAndRawMaterialEnabled", true),
                supplierPoEnabled = o.optBoolean("supplierPoEnabled", true),
                reportsAndProfitEnabled = o.optBoolean("reportsAndProfitEnabled", true),
                bookkeepingExportEnabled = o.optBoolean("bookkeepingExportEnabled", true),
                crmEnabled = o.optBoolean("crmEnabled", true),
                hrPayrollEnabled = o.optBoolean("hrPayrollEnabled", true),
                multiBranchEnabled = o.optBoolean("multiBranchEnabled", true),
                remoteSyncEnabled = o.optBoolean("remoteSyncEnabled", true),
                kitchenAndTableEnabled = o.optBoolean("kitchenAndTableEnabled", true)
            )
        }.getOrDefault(FeatureVisibilityConfig())
    }
}
