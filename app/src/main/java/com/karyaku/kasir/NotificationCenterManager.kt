package com.karyaku.kasir

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class NotificationCategory(val titleId: String, val descId: String) {
    TRANSACTION("Transaksi", "Transaksi sukses, bon/piutang baru, atau void/refund"),
    STOCK("Stok & Audit", "Stok menipis/habis atau hasil opname/audit stok"),
    EXPIRY("Expired Date", "Barang mendekati kadaluarsa atau sudah lewat tanggal"),
    DEBT("Hutang & Piutang", "Jatuh tempo PO supplier atau bon pelanggan belum lunas"),
    SHIFT("Tutup Shift / Kas", "Ringkasan tutup kasir, selisih kas, atau kas masuk/keluar")
}

data class CategoryNotificationSetting(
    val category: NotificationCategory,
    val bellEnabled: Boolean = true,
    val systemBarEnabled: Boolean = false,
    val remoteOwnerEnabled: Boolean = true
)

data class AppNotificationItem(
    val id: String,
    val category: NotificationCategory,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val productId: Long? = null,
    val quickAction: String = "" // "RESTOCK", "PROMO", "WASTE", "DEBT", ""
)

object NotificationCenterManager {
    private const val PREFS_NAME = "zokas_notification_center"
    private const val KEY_SETTINGS = "category_settings_json"
    private const val KEY_ITEMS = "notification_items_json"
    private const val CHANNEL_ID = "zokas_custom_alerts_channel"
    private const val MAX_ITEMS = 100

    private val _settingsFlow = MutableStateFlow<Map<NotificationCategory, CategoryNotificationSetting>>(emptyMap())
    val settingsFlow: StateFlow<Map<NotificationCategory, CategoryNotificationSetting>> = _settingsFlow.asStateFlow()

    private val _notificationsFlow = MutableStateFlow<List<AppNotificationItem>>(emptyList())
    val notificationsFlow: StateFlow<List<AppNotificationItem>> = _notificationsFlow.asStateFlow()

    fun ensureInitialized(context: Context) {
        if (_settingsFlow.value.isEmpty()) {
            _settingsFlow.value = loadCategorySettings(context)
        }
        if (_notificationsFlow.value.isEmpty()) {
            _notificationsFlow.value = loadNotifications(context)
        }
        ensureAndroidChannel(context)
    }

    private fun ensureAndroidChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Pengingat & Notifikasi ZoKas",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifikasi transaksi, stok, expired date, hutang/piutang, dan tutup shift"
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun loadCategorySettings(context: Context): Map<NotificationCategory, CategoryNotificationSetting> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaults = NotificationCategory.entries.associateWith { cat ->
            when (cat) {
                NotificationCategory.TRANSACTION -> CategoryNotificationSetting(cat, bellEnabled = true, systemBarEnabled = false, remoteOwnerEnabled = true)
                NotificationCategory.STOCK -> CategoryNotificationSetting(cat, bellEnabled = true, systemBarEnabled = true, remoteOwnerEnabled = true)
                NotificationCategory.EXPIRY -> CategoryNotificationSetting(cat, bellEnabled = true, systemBarEnabled = true, remoteOwnerEnabled = false)
                NotificationCategory.DEBT -> CategoryNotificationSetting(cat, bellEnabled = true, systemBarEnabled = true, remoteOwnerEnabled = true)
                NotificationCategory.SHIFT -> CategoryNotificationSetting(cat, bellEnabled = true, systemBarEnabled = false, remoteOwnerEnabled = true)
            }
        }.toMutableMap()

        val raw = prefs.getString(KEY_SETTINGS, null) ?: return defaults
        return try {
            val obj = JSONObject(raw)
            NotificationCategory.entries.forEach { cat ->
                val cObj = obj.optJSONObject(cat.name)
                if (cObj != null) {
                    defaults[cat] = CategoryNotificationSetting(
                        category = cat,
                        bellEnabled = cObj.optBoolean("bellEnabled", defaults[cat]?.bellEnabled ?: true),
                        systemBarEnabled = cObj.optBoolean("systemBarEnabled", defaults[cat]?.systemBarEnabled ?: false),
                        remoteOwnerEnabled = cObj.optBoolean("remoteOwnerEnabled", defaults[cat]?.remoteOwnerEnabled ?: true)
                    )
                }
            }
            defaults
        } catch (_: Exception) {
            defaults
        }
    }

    fun updateCategorySetting(context: Context, setting: CategoryNotificationSetting) {
        ensureInitialized(context)
        val updated = _settingsFlow.value.toMutableMap()
        updated[setting.category] = setting
        _settingsFlow.value = updated

        val root = JSONObject()
        updated.forEach { (cat, s) ->
            root.put(
                cat.name,
                JSONObject().apply {
                    put("bellEnabled", s.bellEnabled)
                    put("systemBarEnabled", s.systemBarEnabled)
                    put("remoteOwnerEnabled", s.remoteOwnerEnabled)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SETTINGS, root.toString())
            .apply()
    }

    private fun loadNotifications(context: Context): List<AppNotificationItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<AppNotificationItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val catName = o.optString("category", NotificationCategory.TRANSACTION.name)
                val cat = runCatching { NotificationCategory.valueOf(catName) }.getOrDefault(NotificationCategory.TRANSACTION)
                val pId = o.optLong("productId", -1L).takeIf { it > 0L }
                list.add(
                    AppNotificationItem(
                        id = o.optString("id", "notif_$i"),
                        category = cat,
                        title = o.optString("title", ""),
                        message = o.optString("message", ""),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                        isRead = o.optBoolean("isRead", false),
                        productId = pId,
                        quickAction = o.optString("quickAction", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistNotifications(context: Context, items: List<AppNotificationItem>) {
        val trimmed = items.take(MAX_ITEMS)
        val arr = JSONArray()
        trimmed.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("category", item.category.name)
                    put("title", item.title)
                    put("message", item.message)
                    put("timestamp", item.timestamp)
                    put("isRead", item.isRead)
                    put("productId", item.productId ?: -1L)
                    put("quickAction", item.quickAction)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, arr.toString())
            .apply()
    }

    fun pushNotification(
        context: Context,
        category: NotificationCategory,
        title: String,
        message: String,
        productId: Long? = null,
        quickAction: String = "",
        dedupKey: String? = null
    ) {
        ensureInitialized(context)
        val cfg = _settingsFlow.value[category] ?: CategoryNotificationSetting(category)
        if (!cfg.bellEnabled && !cfg.systemBarEnabled) return

        val current = _notificationsFlow.value.toMutableList()
        val itemId = dedupKey ?: "notif_${System.currentTimeMillis()}_${current.size + 1}"
        if (dedupKey != null && current.any { it.id == dedupKey }) return

        if (cfg.bellEnabled) {
            val newItem = AppNotificationItem(
                id = itemId,
                category = category,
                title = title,
                message = message,
                timestamp = System.currentTimeMillis(),
                isRead = false,
                productId = productId,
                quickAction = quickAction
            )
            current.add(0, newItem)
            _notificationsFlow.value = current
            persistNotifications(context, current)
        }

        if (cfg.systemBarEnabled) {
            showSystemStatusBarNotification(context, itemId.hashCode(), title, message)
        }
    }

    private fun showSystemStatusBarNotification(context: Context, notifId: Int, title: String, message: String) {
        try {
            ensureAndroidChannel(context)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {
            // Izin POST_NOTIFICATIONS belum diberikan oleh pengguna di Android 13+
        } catch (_: Exception) {
        }
    }

    fun markAsRead(context: Context, id: String) {
        ensureInitialized(context)
        val updated = _notificationsFlow.value.map { if (it.id == id) it.copy(isRead = true) else it }
        _notificationsFlow.value = updated
        persistNotifications(context, updated)
    }

    fun markAllAsRead(context: Context) {
        ensureInitialized(context)
        val updated = _notificationsFlow.value.map { it.copy(isRead = true) }
        _notificationsFlow.value = updated
        persistNotifications(context, updated)
    }

    fun clearAll(context: Context) {
        ensureInitialized(context)
        _notificationsFlow.value = emptyList()
        persistNotifications(context, emptyList())
    }
}
