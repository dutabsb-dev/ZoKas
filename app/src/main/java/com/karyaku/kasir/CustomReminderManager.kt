package com.karyaku.kasir

import android.content.Context
import com.karyaku.kasir.data.local.ProductEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ReminderScopeType(val labelId: String) {
    ALL("Semua Produk"),
    CATEGORY("Kategori Tertentu"),
    SPECIFIC_PRODUCTS("Produk Spesifik")
}

enum class ReminderTriggerType(val labelId: String) {
    LOW_STOCK("Stok Menipis / Habis"),
    EXPIRY("Mendekati / Lewat Expired Date"),
    BOTH("Stok & Expired Date")
}

enum class ExpiryActionPreset(val labelId: String, val defaultDiscount: Int) {
    PROMO_DISCOUNT("Diskon Cepat / Cuci Gudang", 20),
    RETURN_SUPPLIER("Retur ke Supplier", 0),
    PRIORITY_SELL("Prioritas Jual (FEFO)", 10),
    WASTE_CHECK("Cek Fisik & Karantina", 0)
}

data class ReminderRule(
    val id: String,
    val name: String,
    val isEnabled: Boolean = true,
    val scopeType: ReminderScopeType = ReminderScopeType.ALL,
    val categories: List<String> = emptyList(),
    val productIds: List<Long> = emptyList(),
    val triggerType: ReminderTriggerType = ReminderTriggerType.BOTH,
    val stockThreshold: Int = 5,
    val expiryDaysBefore: Int = 7, // Bebas: 0 (Hari H), 1, 3, 7, 14, 30, 60, 90, atau custom hari apa saja
    val includeAlreadyExpired: Boolean = true,
    val alertEvenIfZeroStock: Boolean = false,
    val repeatDailyUntilHandled: Boolean = true,
    val expiryActionPreset: ExpiryActionPreset = ExpiryActionPreset.PROMO_DISCOUNT,
    val customPromoDiscountPercent: Int = 20
)

data class TriggeredReminder(
    val eventKey: String,
    val ruleId: String,
    val ruleName: String,
    val product: ProductEntity,
    val kind: String, // "STOCK" atau "EXPIRY"
    val title: String,
    val detail: String,
    val daysToExpiry: Long? = null,
    val suggestedPromoDiscountPercent: Int = 15,
    val actionHint: String = ""
)

object CustomReminderManager {
    private const val PREFS_NAME = "zokas_custom_reminders"
    private const val KEY_RULES = "reminder_rules_json"
    private const val KEY_DISMISSED = "dismissed_reminder_keys_json"

    fun loadRules(context: Context): List<ReminderRule> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RULES, null)
        if (raw.isNullOrBlank()) {
            val defaults = defaultRules()
            saveRules(context, defaults)
            return defaults
        }
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<ReminderRule>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val scopeStr = obj.optString("scopeType", ReminderScopeType.ALL.name)
                val trigStr = obj.optString("triggerType", ReminderTriggerType.BOTH.name)
                val actionStr = obj.optString("expiryActionPreset", ExpiryActionPreset.PROMO_DISCOUNT.name)
                val catArr = obj.optJSONArray("categories") ?: JSONArray()
                val prodArr = obj.optJSONArray("productIds") ?: JSONArray()
                val cats = (0 until catArr.length()).mapNotNull { idx -> catArr.optString(idx, null)?.takeIf { it.isNotBlank() } }
                val prods = (0 until prodArr.length()).mapNotNull { idx -> prodArr.optLong(idx, -1L).takeIf { it > 0L } }

                list.add(
                    ReminderRule(
                        id = obj.optString("id", "rule_$i"),
                        name = obj.optString("name", "Aturan Pengingat"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        scopeType = runCatching { ReminderScopeType.valueOf(scopeStr) }.getOrDefault(ReminderScopeType.ALL),
                        categories = cats,
                        productIds = prods,
                        triggerType = runCatching { ReminderTriggerType.valueOf(trigStr) }.getOrDefault(ReminderTriggerType.BOTH),
                        stockThreshold = obj.optInt("stockThreshold", 5).coerceAtLeast(0),
                        expiryDaysBefore = obj.optInt("expiryDaysBefore", 7).coerceAtLeast(0),
                        includeAlreadyExpired = obj.optBoolean("includeAlreadyExpired", true),
                        alertEvenIfZeroStock = obj.optBoolean("alertEvenIfZeroStock", false),
                        repeatDailyUntilHandled = obj.optBoolean("repeatDailyUntilHandled", true),
                        expiryActionPreset = runCatching { ExpiryActionPreset.valueOf(actionStr) }.getOrDefault(ExpiryActionPreset.PROMO_DISCOUNT),
                        customPromoDiscountPercent = obj.optInt("customPromoDiscountPercent", 20).coerceIn(0, 95)
                    )
                )
            }
            if (list.isEmpty()) defaultRules() else list
        } catch (_: Exception) {
            defaultRules()
        }
    }

    fun saveRules(context: Context, rules: List<ReminderRule>) {
        val arr = JSONArray()
        rules.forEach { rule ->
            val obj = JSONObject().apply {
                put("id", rule.id)
                put("name", rule.name)
                put("isEnabled", rule.isEnabled)
                put("scopeType", rule.scopeType.name)
                put("triggerType", rule.triggerType.name)
                put("stockThreshold", rule.stockThreshold)
                put("expiryDaysBefore", rule.expiryDaysBefore)
                put("includeAlreadyExpired", rule.includeAlreadyExpired)
                put("alertEvenIfZeroStock", rule.alertEvenIfZeroStock)
                put("repeatDailyUntilHandled", rule.repeatDailyUntilHandled)
                put("expiryActionPreset", rule.expiryActionPreset.name)
                put("customPromoDiscountPercent", rule.customPromoDiscountPercent)
                put("categories", JSONArray(rule.categories))
                put("productIds", JSONArray(rule.productIds))
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RULES, arr.toString())
            .apply()
    }

    fun loadDismissedKeys(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DISMISSED, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    val k = arr.optString(i, "")
                    if (k.isNotBlank()) add(k)
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun dismissReminder(context: Context, eventKey: String) {
        val current = loadDismissedKeys(context).toMutableSet()
        current.add(eventKey)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISMISSED, JSONArray(current.toList()).toString())
            .apply()
    }

    fun clearDismissedReminders(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISMISSED, "[]")
            .apply()
    }

    fun defaultRules(): List<ReminderRule> = listOf(
        ReminderRule(
            id = "default_stock_rule",
            name = "Stok Kritis (<= 5)",
            isEnabled = true,
            scopeType = ReminderScopeType.ALL,
            triggerType = ReminderTriggerType.LOW_STOCK,
            stockThreshold = 5,
            expiryDaysBefore = 7
        ),
        ReminderRule(
            id = "default_expiry_rule",
            name = "Produk Mendekati Expired (H-7)",
            isEnabled = true,
            scopeType = ReminderScopeType.ALL,
            triggerType = ReminderTriggerType.EXPIRY,
            stockThreshold = 5,
            expiryDaysBefore = 7,
            includeAlreadyExpired = true,
            alertEvenIfZeroStock = false,
            repeatDailyUntilHandled = true,
            expiryActionPreset = ExpiryActionPreset.PROMO_DISCOUNT,
            customPromoDiscountPercent = 20
        )
    )

    fun evaluateActiveReminders(
        products: List<ProductEntity>,
        rules: List<ReminderRule>,
        dismissedKeys: Set<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<TriggeredReminder> {
        val activeRules = rules.filter { it.isEnabled }
        if (activeRules.isEmpty() || products.isEmpty()) return emptyList()

        val results = mutableListOf<TriggeredReminder>()
        val seenKeys = mutableSetOf<String>()
        val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dayStamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(nowMillis))

        for (rule in activeRules) {
            val matchedProducts = products.filter { prod ->
                if (prod.isService) return@filter false
                when (rule.scopeType) {
                    ReminderScopeType.ALL -> true
                    ReminderScopeType.CATEGORY -> rule.categories.any { cat ->
                        prod.category.equals(cat, ignoreCase = true)
                    }
                    ReminderScopeType.SPECIFIC_PRODUCTS -> rule.productIds.contains(prod.id)
                }
            }

            for (prod in matchedProducts) {
                val checkStock = rule.triggerType == ReminderTriggerType.LOW_STOCK || rule.triggerType == ReminderTriggerType.BOTH
                val checkExpiry = rule.triggerType == ReminderTriggerType.EXPIRY || rule.triggerType == ReminderTriggerType.BOTH

                if (checkStock && prod.stock <= rule.stockThreshold) {
                    val key = "STOCK_${rule.id}_${prod.id}_${prod.stock}"
                    if (!dismissedKeys.contains(key) && seenKeys.add(key)) {
                        results.add(
                            TriggeredReminder(
                                eventKey = key,
                                ruleId = rule.id,
                                ruleName = rule.name,
                                product = prod,
                                kind = "STOCK",
                                title = if (prod.stock <= 0) "Stok Habis: ${prod.name}" else "Stok Menipis: ${prod.name}",
                                detail = "Sisa stok ${prod.stock} (batas aturan <= ${rule.stockThreshold}) • Aturan: ${rule.name}"
                            )
                        )
                    }
                }

                if (checkExpiry && prod.expiryDate > 0L) {
                    if (!rule.alertEvenIfZeroStock && prod.stock <= 0) continue

                    val diffMillis = prod.expiryDate - nowMillis
                    val daysLeft = TimeUnit.MILLISECONDS.toDays(diffMillis)
                    val withinWindow = if (rule.includeAlreadyExpired) {
                        daysLeft <= rule.expiryDaysBefore.toLong()
                    } else {
                        daysLeft in 0L..rule.expiryDaysBefore.toLong()
                    }

                    if (withinWindow) {
                        val batchTag = prod.batchNumber.ifBlank { "default" }
                        val repeatSuffix = if (rule.repeatDailyUntilHandled) "_$dayStamp" else ""
                        val key = "EXPIRY_${rule.id}_${prod.id}_${prod.expiryDate}_$batchTag$repeatSuffix"
                        if (!dismissedKeys.contains(key) && seenKeys.add(key)) {
                            val expDateStr = dateFmt.format(Date(prod.expiryDate))
                            val statusText = when {
                                daysLeft < 0L -> "Sudah lewat expired ${kotlin.math.abs(daysLeft)} hari lalu ($expDateStr)"
                                daysLeft == 0L -> "Jatuh tempo expired HARI INI ($expDateStr)"
                                else -> "Expired dalam $daysLeft hari • Tgl: $expDateStr (batas H-${rule.expiryDaysBefore})"
                            }
                            val batchInfo = if (prod.batchNumber.isNotBlank()) " • Batch: ${prod.batchNumber}" else ""
                            val suggestedDisc = when {
                                rule.customPromoDiscountPercent > 0 -> rule.customPromoDiscountPercent
                                daysLeft <= 1L -> 25
                                else -> 15
                            }
                            results.add(
                                TriggeredReminder(
                                    eventKey = key,
                                    ruleId = rule.id,
                                    ruleName = rule.name,
                                    product = prod,
                                    kind = "EXPIRY",
                                    title = "Expired Alert: ${prod.name}",
                                    detail = "$statusText$batchInfo • Stok: ${prod.stock} • Saran: ${rule.expiryActionPreset.labelId}",
                                    daysToExpiry = daysLeft,
                                    suggestedPromoDiscountPercent = suggestedDisc,
                                    actionHint = rule.expiryActionPreset.labelId
                                )
                            )
                        }
                    }
                }
            }
        }

        return results.sortedWith(
            compareBy<TriggeredReminder> { if (it.kind == "EXPIRY") 0 else 1 }
                .thenBy { it.daysToExpiry ?: Long.MAX_VALUE }
        )
    }
}
