package com.karyaku.kasir

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RemoteSyncTransactionRecord(
    val eventId: String,
    val sourceDeviceLabel: String,
    val branchId: Long,
    val branchName: String,
    val timestamp: Long,
    val totalPrice: Long,
    val cashReceived: Long,
    val changeAmount: Long,
    val customerName: String,
    val status: String,
    val totalCost: Long,
    val tableName: String,
    val voucherCode: String,
    val taxAmount: Long,
    val paymentMethod: String,
    val cashierName: String,
    val totalCommission: Long,
    val orderType: String,
    val serviceCharge: Long,
    val ownerEmail: String
)

data class RemoteSyncShiftRecord(
    val eventId: String,
    val sourceDeviceLabel: String,
    val branchId: Long,
    val branchName: String,
    val userId: Long,
    val userName: String,
    val startTime: Long,
    val endTime: Long?,
    val openingCash: Long,
    val expectedCash: Long,
    val closingCash: Long?,
    val discrepancy: Long?,
    val status: String,
    val note: String,
    val ownerEmail: String
)

object MultiDeviceTransactionSyncManager {
    private const val PREFS_SYNC = "zokas_multidevice_sync_prefs"
    private const val MAX_QUEUE_SIZE = 2000

    var lastSyncStatusMessage by mutableStateOf("Multi-Cabang Sync siap (Cabang A, Cabang B & HP Owner)")
        private set

    var pendingQueueCount by mutableStateOf(0)
        private set

    var importedCountLastRun by mutableStateOf(0)
        private set

    fun getDeviceLabel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
        val saved = prefs.getString("device_label", "") ?: ""
        if (saved.isNotBlank()) return saved
        val model = android.os.Build.MODEL?.takeIf { it.isNotBlank() } ?: "Perangkat"
        return "$model (${android.os.Build.MANUFACTURER ?: "Android"})"
    }

    fun setDeviceLabel(context: Context, label: String) {
        val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
        prefs.edit().putString("device_label", label.trim()).apply()
    }

    fun getRegisteredEmail(context: Context): String {
        val prefs = context.getSharedPreferences("zokas_account_prefs", Context.MODE_PRIVATE)
        return prefs.getString("registered_email", "") ?: ""
    }

    fun refreshQueueCount(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
        val trxArr = JSONArray(prefs.getString("trx_queue", "[]") ?: "[]")
        val shiftArr = JSONArray(prefs.getString("shift_queue", "[]") ?: "[]")
        pendingQueueCount = trxArr.length() + shiftArr.length()
    }

    fun recordLocalTransactionToQueue(
        context: Context,
        tx: TransactionEntity,
        branchName: String
    ) {
        val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
        val deviceLabel = getDeviceLabel(context)
        val email = getRegisteredEmail(context)
        val eventId = "TX_${tx.branchId}_${tx.id}_${tx.timestamp}"

        try {
            val arr = JSONArray(prefs.getString("trx_queue", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                if (arr.optJSONObject(i)?.optString("eventId") == eventId) {
                    refreshQueueCount(context)
                    return
                }
            }
            val obj = JSONObject().apply {
                put("eventId", eventId)
                put("sourceDeviceLabel", deviceLabel)
                put("branchId", tx.branchId)
                put("branchName", branchName)
                put("timestamp", tx.timestamp)
                put("totalPrice", tx.totalPrice)
                put("cashReceived", tx.cashReceived)
                put("changeAmount", tx.changeAmount)
                put("customerName", tx.customerName)
                put("status", tx.status)
                put("totalCost", tx.totalCost)
                put("tableName", tx.tableName)
                put("voucherCode", tx.voucherCode)
                put("taxAmount", tx.taxAmount)
                put("paymentMethod", tx.paymentMethod)
                put("cashierName", tx.cashierName)
                put("totalCommission", tx.totalCommission)
                put("orderType", tx.orderType)
                put("serviceCharge", tx.serviceCharge)
                put("ownerEmail", email)
            }
            arr.put(obj)
            val trimmed = if (arr.length() > MAX_QUEUE_SIZE) {
                JSONArray().apply {
                    for (i in (arr.length() - MAX_QUEUE_SIZE) until arr.length()) {
                        put(arr.get(i))
                    }
                }
            } else arr
            prefs.edit().putString("trx_queue", trimmed.toString()).apply()
            refreshQueueCount(context)
            writeAutoSnapshotToCache(context)
            lastSyncStatusMessage = "Antrean transaksi #${tx.id} dicatat (${formatTime(tx.timestamp)})"
        } catch (_: Exception) {
        }
    }

    fun recordLocalShiftToQueue(
        context: Context,
        shift: ShiftEntity,
        branchName: String
    ) {
        val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
        val deviceLabel = getDeviceLabel(context)
        val email = getRegisteredEmail(context)
        val ts = shift.endTime ?: shift.startTime
        val eventId = "SH_${shift.branchId}_${shift.id}_${ts}"

        try {
            val arr = JSONArray(prefs.getString("shift_queue", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                if (arr.optJSONObject(i)?.optString("eventId") == eventId) {
                    refreshQueueCount(context)
                    return
                }
            }
            val obj = JSONObject().apply {
                put("eventId", eventId)
                put("sourceDeviceLabel", deviceLabel)
                put("branchId", shift.branchId)
                put("branchName", branchName)
                put("userId", shift.userId)
                put("userName", shift.userName)
                put("startTime", shift.startTime)
                put("endTime", shift.endTime ?: 0L)
                put("openingCash", shift.openingCash)
                put("expectedCash", shift.expectedCash)
                put("closingCash", shift.closingCash ?: 0L)
                put("discrepancy", shift.discrepancy ?: 0L)
                put("status", shift.status)
                put("note", shift.note)
                put("ownerEmail", email)
            }
            arr.put(obj)
            prefs.edit().putString("shift_queue", arr.toString()).apply()
            refreshQueueCount(context)
            writeAutoSnapshotToCache(context)
        } catch (_: Exception) {
        }
    }

    fun buildSyncBundleJson(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
        val trxRaw = prefs.getString("trx_queue", "[]") ?: "[]"
        val shiftRaw = prefs.getString("shift_queue", "[]") ?: "[]"

        // Urutkan antrean transaksi & shift berdasarkan waktu (timestamp) sebelum diekspor
        val trxList = mutableListOf<JSONObject>()
        val trxArrIn = JSONArray(trxRaw)
        for (i in 0 until trxArrIn.length()) {
            trxArrIn.optJSONObject(i)?.let { trxList.add(it) }
        }
        trxList.sortBy { it.optLong("timestamp", 0L) }

        val shiftList = mutableListOf<JSONObject>()
        val shiftArrIn = JSONArray(shiftRaw)
        for (i in 0 until shiftArrIn.length()) {
            shiftArrIn.optJSONObject(i)?.let { shiftList.add(it) }
        }
        shiftList.sortBy {
            val endT = it.optLong("endTime", 0L)
            if (endT > 0L) endT else it.optLong("startTime", 0L)
        }

        return JSONObject().apply {
            put("app", "ZoKas")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("ownerEmail", getRegisteredEmail(context))
            put("sourceDevice", getDeviceLabel(context))
            put("transactions", JSONArray(trxList))
            put("shifts", JSONArray(shiftList))
        }.toString(2)
    }

    suspend fun exportSyncBundleToUri(context: Context, targetUri: Uri): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val bundleJson = buildSyncBundleJson(context)
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                        writer.write(bundleJson)
                        writer.flush()
                    }
                }
                withContext(Dispatchers.Main) {
                    lastSyncStatusMessage = "Paket antrean sinkronisasi diekspor (${formatTime(System.currentTimeMillis())})"
                }
                Pair(true, "Paket sinkronisasi berhasil diekspor!")
            } catch (e: Exception) {
                Pair(false, "Gagal mengekspor paket sinkronisasi: ${e.message}")
            }
        }
    }

    suspend fun importAndMergeSyncBundleFromUri(
        context: Context,
        viewModel: PosViewModel,
        sourceUri: Uri
    ): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val raw = context.contentResolver.openInputStream(sourceUri)?.use { inp ->
                    BufferedReader(InputStreamReader(inp, Charsets.UTF_8)).use { it.readText() }
                } ?: return@withContext Pair(false, "File paket sinkronisasi kosong.")

                applySyncBundleJson(context, viewModel, raw)
            } catch (e: Exception) {
                Pair(false, "Gagal membaca paket sinkronisasi: ${e.message}")
            }
        }
    }

    suspend fun applySyncBundleJson(
        context: Context,
        viewModel: PosViewModel,
        rawJson: String
    ): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val root = JSONObject(rawJson)
                val bundleEmail = root.optString("ownerEmail", "").trim()
                val localEmail = getRegisteredEmail(context).trim()
                if (localEmail.isNotBlank() && bundleEmail.isNotBlank() &&
                    !localEmail.equals(bundleEmail, ignoreCase = true)
                ) {
                    return@withContext Pair(
                        false,
                        "Email tidak cocok! Paket berasal dari $bundleEmail, sedangkan perangkat ini terdaftar dengan $localEmail."
                    )
                }

                val existingTxs = viewModel.transactionDao.getAllTransactions().first()
                val existingShifts = viewModel.userDao.getAllShifts().first()
                val existingTxFingerprints = existingTxs.map {
                    "${it.branchId}_${it.timestamp}_${it.totalPrice}_${it.cashierName ?: ""}"
                }.toMutableSet()
                val existingShiftFingerprints = existingShifts.map {
                    "${it.branchId}_${it.userId}_${it.startTime}_${it.openingCash}"
                }.toMutableSet()

                val trxArr = root.optJSONArray("transactions") ?: JSONArray()
                val parsedTxs = mutableListOf<JSONObject>()
                for (i in 0 until trxArr.length()) {
                    trxArr.optJSONObject(i)?.let { parsedTxs.add(it) }
                }
                // Antrean menurut waktu (timestamp naik/kronologis) agar tidak ada race condition
                parsedTxs.sortBy { it.optLong("timestamp", 0L) }

                var insertedTx = 0
                for (tObj in parsedTxs) {
                    val ts = tObj.optLong("timestamp", 0L)
                    val totalPrice = tObj.optLong("totalPrice", 0L)
                    val cashier = tObj.optString("cashierName", "Kasir")
                    val branchId = tObj.optLong("branchId", 1L).coerceAtLeast(1L)

                    val alreadyExists = existingTxs.any { ex ->
                        ex.timestamp == ts && ex.totalPrice == totalPrice && ex.cashierName == cashier
                    }
                    if (!alreadyExists && ts > 0L) {
                        val branchTag = tObj.optString("branchName", "")
                        val customerLabel = tObj.optString("customerName", "Pelanggan Umum").let { cust ->
                            if (branchTag.isNotBlank() && !cust.contains("[$branchTag]")) {
                                "$cust [$branchTag]"
                            } else cust
                        }
                        viewModel.transactionDao.insertTransaction(
                            TransactionEntity(
                                timestamp = ts,
                                totalPrice = totalPrice,
                                cashReceived = tObj.optLong("cashReceived", totalPrice),
                                changeAmount = tObj.optLong("changeAmount", 0L),
                                customerName = customerLabel,
                                status = tObj.optString("status", "SUCCESS"),
                                totalCost = tObj.optLong("totalCost", 0L),
                                tableName = tObj.optString("tableName", "Take-Away"),
                                voucherCode = tObj.optString("voucherCode", ""),
                                taxAmount = tObj.optLong("taxAmount", 0L),
                                paymentMethod = tObj.optString("paymentMethod", "CASH"),
                                cashierName = cashier,
                                totalCommission = tObj.optLong("totalCommission", 0L),
                                orderType = tObj.optString("orderType", "Dine-In"),
                                serviceCharge = tObj.optLong("serviceCharge", 0L),
                                branchId = branchId,
                                updatedAt = System.currentTimeMillis(),
                                isSynced = true
                            )
                        )
                        insertedTx++
                    }
                }

                val shiftArr = root.optJSONArray("shifts") ?: JSONArray()
                val parsedShifts = mutableListOf<JSONObject>()
                for (i in 0 until shiftArr.length()) {
                    shiftArr.optJSONObject(i)?.let { parsedShifts.add(it) }
                }
                parsedShifts.sortBy { it.optLong("startTime", 0L) }

                var insertedShifts = 0
                for (sObj in parsedShifts) {
                    val startTime = sObj.optLong("startTime", 0L)
                    val uName = sObj.optString("userName", "Kasir")
                    val branchId = sObj.optLong("branchId", 1L).coerceAtLeast(1L)
                    val exists = existingShifts.any { sh ->
                        sh.startTime == startTime && sh.userName == uName
                    }
                    if (!exists && startTime > 0L) {
                        val endVal = sObj.optLong("endTime", 0L).let { if (it > 0L) it else null }
                        val closeVal = sObj.optLong("closingCash", 0L).let { if (endVal != null) it else null }
                        val discVal = sObj.optLong("discrepancy", 0L).let { if (endVal != null) it else null }
                        val bTag = sObj.optString("branchName", "")
                        val noteCombined = sObj.optString("note", "").let { n ->
                            if (bTag.isNotBlank() && !n.contains(bTag)) {
                                if (n.isBlank()) "[$bTag]" else "$n [$bTag]"
                            } else n
                        }
                        viewModel.userDao.insertShift(
                            ShiftEntity(
                                userId = sObj.optLong("userId", 1L).coerceAtLeast(1L),
                                userName = uName,
                                startTime = startTime,
                                endTime = endVal,
                                openingCash = sObj.optLong("openingCash", 0L),
                                expectedCash = sObj.optLong("expectedCash", 0L),
                                closingCash = closeVal,
                                discrepancy = discVal,
                                status = sObj.optString("status", if (endVal != null) "CLOSED" else "OPEN"),
                                branchId = branchId,
                                note = noteCombined
                            )
                        )
                        insertedShifts++
                    }
                }

                withContext(Dispatchers.Main) {
                    importedCountLastRun = insertedTx + insertedShifts
                    lastSyncStatusMessage = "Sinkronisasi selesai: +$insertedTx transaksi, +$insertedShifts shift (${formatTime(System.currentTimeMillis())})"
                }
                Pair(true, "Berhasil menggabungkan $insertedTx transaksi baru & $insertedShifts data shift secara berurutan!")
            } catch (e: Exception) {
                Pair(false, "Gagal memproses paket sinkronisasi: ${e.message}")
            }
        }
    }


    fun isAutoExportOnCloseShiftEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
        return prefs.getBoolean("auto_export_close_shift", true)
    }

    fun setAutoExportOnCloseShift(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_export_close_shift", enabled).apply()
    }

    fun writeAutoSnapshotToCache(context: Context): java.io.File? {
        return try {
            val bundleJson = buildSyncBundleJson(context)
            val dir = java.io.File(context.cacheDir, "sync_bundles")
            dir.mkdirs()
            val cleanLabel = getDeviceLabel(context).trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
            val outFile = java.io.File(dir, "ZoKas_Sync_${cleanLabel.ifBlank { "Device" }}_latest.json")
            outFile.writeText(bundleJson, Charsets.UTF_8)
            outFile
        } catch (_: Exception) {
            null
        }
    }

    fun shareLatestSyncBundle(context: Context): Pair<Boolean, String> {
        return try {
            val file = writeAutoSnapshotToCache(context)
                ?: return Pair(false, "Gagal menyiapkan file paket sinkronisasi.")
            val authority = "${context.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(
                    android.content.Intent.EXTRA_SUBJECT,
                    "Paket Sinkronisasi Transaksi ZoKas (${getDeviceLabel(context)})"
                )
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = android.content.Intent.createChooser(intent, "Kirim Paket Sinkronisasi ke HP Owner")
            chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            Pair(true, "Membuka menu kirim paket sinkronisasi...")
        } catch (e: Exception) {
            Pair(false, "Gagal membagikan file: ${e.message}")
        }
    }

    private fun formatTime(ts: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID"))
        return sdf.format(Date(ts))
    }
}
