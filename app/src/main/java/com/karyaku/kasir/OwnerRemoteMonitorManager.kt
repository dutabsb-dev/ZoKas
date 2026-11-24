package com.karyaku.kasir

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class OwnerMonitorConfig(
    val isEnabled: Boolean = false,
    val botToken: String = "",
    val chatId: String = "",
    val notifyOnEachTransaction: Boolean = true,
    val notifyOnCloseShift: Boolean = true,
    val notifyOnLowStock: Boolean = true,
    val notifyOnVoidRefund: Boolean = true
)

/**
 * OwnerRemoteMonitorManager (Zero-Server Bring-Your-Own-Cloud untuk HP Owner)
 * Memungkinkan HP Owner (Perangkat A) memantau transaksi & tutup shift dari perangkat kasir (B, C, D)
 * di lokasi berbeda tanpa server terpusat milik developer.
 * Menggunakan antrean berbasis waktu (timestamp) & deduplikasi ID transaksi agar aman dari race condition.
 */
object OwnerRemoteMonitorManager {
    private const val PREFS_NAME = "zokas_owner_remote_monitor"
    private val sentEventKeys = ConcurrentHashMap<String, Long>()

    fun getConfig(context: Context): OwnerMonitorConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return OwnerMonitorConfig(
            isEnabled = prefs.getBoolean("enabled", false),
            botToken = prefs.getString("bot_token", "") ?: "",
            chatId = prefs.getString("chat_id", "") ?: "",
            notifyOnEachTransaction = prefs.getBoolean("notify_trx", true),
            notifyOnCloseShift = prefs.getBoolean("notify_shift", true),
            notifyOnLowStock = prefs.getBoolean("notify_low_stock", true),
            notifyOnVoidRefund = prefs.getBoolean("notify_void", true)
        )
    }

    fun saveConfig(context: Context, config: OwnerMonitorConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("enabled", config.isEnabled)
            .putString("bot_token", config.botToken.trim())
            .putString("chat_id", config.chatId.trim())
            .putBoolean("notify_trx", config.notifyOnEachTransaction)
            .putBoolean("notify_shift", config.notifyOnCloseShift)
            .putBoolean("notify_low_stock", config.notifyOnLowStock)
            .putBoolean("notify_void", config.notifyOnVoidRefund)
            .apply()
    }

    fun getRegisteredOwnerEmail(context: Context): String {
        val prefs = context.getSharedPreferences("zokas_account_prefs", Context.MODE_PRIVATE)
        return prefs.getString("registered_email", "") ?: ""
    }

    private fun formatRp(amount: Long): String {
        val nf = NumberFormat.getInstance(Locale("id", "ID"))
        return "Rp " + nf.format(amount)
    }

    private fun formatTime(ts: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("id", "ID"))
        return sdf.format(Date(if (ts > 0L) ts else System.currentTimeMillis()))
    }

    private fun enqueuePendingEvent(context: Context, eventKey: String, message: String, timestamp: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawQueue = prefs.getString("pending_queue", "[]") ?: "[]"
        try {
            val arr = JSONArray(rawQueue)
            val obj = JSONObject().apply {
                put("key", eventKey)
                put("msg", message)
                put("ts", timestamp)
            }
            arr.put(obj)
            prefs.edit().putString("pending_queue", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    suspend fun flushPendingQueue(context: Context) {
        val cfg = getConfig(context)
        if (!cfg.isEnabled || cfg.botToken.isBlank() || cfg.chatId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawQueue = prefs.getString("pending_queue", "[]") ?: "[]"
        try {
            val arr = JSONArray(rawQueue)
            if (arr.length() == 0) return

            // Urutkan antrean menurut waktu (timestamp) agar urutan laporan di HP Owner konsisten
            val entries = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) {
                entries.add(arr.getJSONObject(i))
            }
            entries.sortBy { it.optLong("ts", 0L) }

            val remaining = JSONArray()
            for (entry in entries) {
                val key = entry.optString("key", "")
                val msg = entry.optString("msg", "")
                if (key.isNotEmpty() && sentEventKeys.containsKey(key)) continue
                val ok = postTelegramMessage(cfg.botToken, cfg.chatId, msg)
                if (ok) {
                    if (key.isNotEmpty()) sentEventKeys[key] = System.currentTimeMillis()
                } else {
                    remaining.put(entry)
                }
            }
            prefs.edit().putString("pending_queue", remaining.toString()).apply()
        } catch (_: Exception) {
        }
    }

    suspend fun notifyTransactionCompleted(
        context: Context,
        storeName: String,
        branchName: String,
        transactionId: Long,
        timestamp: Long,
        cashierName: String,
        paymentMethod: String,
        totalPrice: Long,
        itemCount: Int
    ) {
        val cfg = getConfig(context)
        if (!cfg.isEnabled || !cfg.notifyOnEachTransaction) return
        val key = "trx_${branchName}_${transactionId}_${timestamp}"
        if (sentEventKeys.containsKey(key)) return

        val ownerEmail = getRegisteredOwnerEmail(context)
        val msg = buildString {
            appendLine("🔔 *LAPORAN TRANSAKSI BARU - ${storeName.uppercase()}*")
            if (ownerEmail.isNotBlank()) appendLine("Akun: $ownerEmail")
            appendLine("Cabang/Device: $branchName")
            appendLine("No. Struk: #$transactionId")
            appendLine("Waktu: ${formatTime(timestamp)}")
            appendLine("Kasir: $cashierName")
            appendLine("Jumlah Item: $itemCount")
            appendLine("Metode: $paymentMethod")
            appendLine("Total: *${formatRp(totalPrice)}*")
        }.trim()

        sendOrQueue(context, key, msg, timestamp)
    }

    suspend fun notifyShiftClosed(
        context: Context,
        storeName: String,
        branchName: String,
        shiftId: Long,
        timestamp: Long,
        cashierName: String,
        openingCash: Long,
        expectedCash: Long,
        closingCash: Long,
        discrepancy: Long,
        note: String
    ) {
        val cfg = getConfig(context)
        if (!cfg.isEnabled || !cfg.notifyOnCloseShift) return
        val key = "shift_${branchName}_${shiftId}_${timestamp}"
        if (sentEventKeys.containsKey(key)) return

        val ownerEmail = getRegisteredOwnerEmail(context)
        val msg = buildString {
            appendLine("🔒 *TUTUP KASIR (CLOSE SHIFT) - ${storeName.uppercase()}*")
            if (ownerEmail.isNotBlank()) appendLine("Akun: $ownerEmail")
            appendLine("Cabang/Device: $branchName")
            appendLine("Waktu Tutup: ${formatTime(timestamp)}")
            appendLine("Kasir: $cashierName")
            appendLine("Modal Awal: ${formatRp(openingCash)}")
            appendLine("Kas Sistem: ${formatRp(expectedCash)}")
            appendLine("Kas Fisik: ${formatRp(closingCash)}")
            appendLine("Selisih: *${formatRp(discrepancy)}*")
            if (note.isNotBlank()) appendLine("Catatan: $note")
        }.trim()

        sendOrQueue(context, key, msg, timestamp)
    }

    suspend fun notifyVoidOrRefund(
        context: Context,
        storeName: String,
        branchName: String,
        actionType: String,
        transactionId: Long,
        timestamp: Long,
        actorName: String,
        amount: Long
    ) {
        val cfg = getConfig(context)
        if (!cfg.isEnabled || !cfg.notifyOnVoidRefund) return
        val key = "${actionType.lowercase()}_${branchName}_${transactionId}_${timestamp}"
        if (sentEventKeys.containsKey(key)) return

        val msg = buildString {
            appendLine("⚠️ *PERINGATAN $actionType - ${storeName.uppercase()}*")
            appendLine("Cabang/Device: $branchName")
            appendLine("No. Struk: #$transactionId")
            appendLine("Waktu: ${formatTime(timestamp)}")
            appendLine("Oleh: $actorName")
            appendLine("Nominal: ${formatRp(amount)}")
        }.trim()

        sendOrQueue(context, key, msg, timestamp)
    }

    suspend fun sendTestNotification(context: Context, storeName: String): Pair<Boolean, String> {
        val cfg = getConfig(context)
        if (cfg.botToken.isBlank() || cfg.chatId.isBlank()) {
            return Pair(false, "Isi Bot Token dan Chat ID Telegram terlebih dahulu.")
        }
        val ownerEmail = getRegisteredOwnerEmail(context)
        val sampleMsg = buildString {
            appendLine("✅ *KONEKSI PANTAUAN HP OWNER AKTIF*")
            appendLine("Aplikasi: ZoKas (${storeName.ifBlank { "Toko" }})")
            if (ownerEmail.isNotBlank()) appendLine("Email Terdaftar: $ownerEmail")
            appendLine("Waktu Uji: ${formatTime(System.currentTimeMillis())}")
            appendLine("Laporan transaksi & tutup kasir akan masuk otomatis ke HP Owner.")
        }.trim()
        val ok = postTelegramMessage(cfg.botToken, cfg.chatId, sampleMsg)
        return if (ok) {
            Pair(true, "Pesan uji coba berhasil dikirim ke HP Owner!")
        } else {
            Pair(false, "Gagal mengirim pesan. Periksa Bot Token, Chat ID, atau koneksi internet.")
        }
    }

    private suspend fun sendOrQueue(context: Context, key: String, msg: String, timestamp: Long) {
        val cfg = getConfig(context)
        if (cfg.botToken.isBlank() || cfg.chatId.isBlank()) {
            enqueuePendingEvent(context, key, msg, timestamp)
            return
        }
        val sent = postTelegramMessage(cfg.botToken, cfg.chatId, msg)
        if (sent) {
            sentEventKeys[key] = System.currentTimeMillis()
            flushPendingQueue(context)
        } else {
            enqueuePendingEvent(context, key, msg, timestamp)
        }
    }

    private suspend fun postTelegramMessage(botToken: String, chatId: String, text: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val encodedChatId = URLEncoder.encode(chatId.trim(), "UTF-8")
                val urlStr = "https://api.telegram.org/bot${botToken.trim()}/sendMessage?chat_id=$encodedChatId&text=$encodedText&parse_mode=Markdown"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val code = conn.responseCode
                conn.disconnect()
                code in 200..299
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun isCategoryRemoteAllowed(context: Context, category: NotificationCategory): Boolean {
        val map = NotificationCenterManager.loadCategorySettings(context)
        return map[category]?.remoteOwnerEnabled ?: true
    }

    suspend fun notifyDebtOrExpiryAlert(
        context: Context,
        storeName: String,
        branchName: String,
        category: NotificationCategory,
        title: String,
        detail: String
    ) {
        val cfg = getConfig(context)
        if (!cfg.isEnabled || !isCategoryRemoteAllowed(context, category)) return
        val key = "${category.name}_${branchName}_${title.hashCode()}_${System.currentTimeMillis() / 60000L}"
        if (sentEventKeys.containsKey(key)) return

        val msg = buildString {
            appendLine("🔔 *${title.uppercase()} - ${storeName.uppercase()}*")
            appendLine("Cabang/Device: $branchName")
            appendLine("Waktu: ${formatTime(System.currentTimeMillis())}")
            appendLine(detail)
        }.trim()
        sendOrQueue(context, key, msg, System.currentTimeMillis())
    }
}
