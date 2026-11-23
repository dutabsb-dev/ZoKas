package com.karyaku.kasir

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BackupScheduleMode(val label: String, val desc: String) {
    ON_CLOSE_SHIFT("Saat Tutup Kasir (Close Shift)", "Otomatis membuat paket backup setiap kali shift ditutup"),
    DAILY("Harian (Jam Pilihan)", "Jadwal backup otomatis setiap hari pada jam yang ditentukan"),
    WEEKLY("Mingguan (Tiap 7 Hari)", "Jadwal backup otomatis setiap minggu untuk arsip rutin"),
    MANUAL_AND_QUICK("Cepat & Manual (Kapan Saja)", "Simpan snapshot lokal instan & kirim cepat ke Google Drive")
}

data class AutoBackupSettings(
    val isEnabled: Boolean = false,
    val scheduleMode: BackupScheduleMode = BackupScheduleMode.ON_CLOSE_SHIFT,
    val dailyHour: Int = 21,
    val dailyMinute: Int = 0,
    val openGoogleDriveAfterBackup: Boolean = true,
    val lastBackupTimeMs: Long = 0L,
    val lastBackupFileName: String = "",
    val lastBackupStatus: String = "Belum ada jadwal backup yang dijalankan"
)

object AutoBackupDriveManager {
    private const val PREFS_NAME = "zokas_autobackup_gdrive_prefs"
    private const val BACKUP_DIR_NAME = "zokas_auto_backups"
    private const val GOOGLE_DRIVE_PKG = "com.google.android.apps.docs"

    fun loadSettings(context: Context): AutoBackupSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeName = prefs.getString("schedule_mode", BackupScheduleMode.ON_CLOSE_SHIFT.name)
            ?: BackupScheduleMode.ON_CLOSE_SHIFT.name
        val mode = runCatching { BackupScheduleMode.valueOf(modeName) }
            .getOrDefault(BackupScheduleMode.ON_CLOSE_SHIFT)
        return AutoBackupSettings(
            isEnabled = prefs.getBoolean("is_enabled", false),
            scheduleMode = mode,
            dailyHour = prefs.getInt("daily_hour", 21).coerceIn(0, 23),
            dailyMinute = prefs.getInt("daily_minute", 0).coerceIn(0, 59),
            openGoogleDriveAfterBackup = prefs.getBoolean("open_gdrive", true),
            lastBackupTimeMs = prefs.getLong("last_backup_ms", 0L),
            lastBackupFileName = prefs.getString("last_backup_file", "") ?: "",
            lastBackupStatus = prefs.getString("last_backup_status", "Belum ada jadwal backup yang dijalankan")
                ?: "Belum ada jadwal backup yang dijalankan"
        )
    }

    fun saveSettings(context: Context, settings: AutoBackupSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_enabled", settings.isEnabled)
            .putString("schedule_mode", settings.scheduleMode.name)
            .putInt("daily_hour", settings.dailyHour.coerceIn(0, 23))
            .putInt("daily_minute", settings.dailyMinute.coerceIn(0, 59))
            .putBoolean("open_gdrive", settings.openGoogleDriveAfterBackup)
            .putLong("last_backup_ms", settings.lastBackupTimeMs)
            .putString("last_backup_file", settings.lastBackupFileName)
            .putString("last_backup_status", settings.lastBackupStatus)
            .apply()
    }

    fun getBackupFolder(context: Context): File {
        val dir = File(context.filesDir, BACKUP_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listBackupFiles(context: Context): List<File> {
        return getBackupFolder(context)
            .listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    fun isDueBySchedule(context: Context): Boolean {
        val cfg = loadSettings(context)
        if (!cfg.isEnabled) return false
        val now = System.currentTimeMillis()
        return when (cfg.scheduleMode) {
            BackupScheduleMode.MANUAL_AND_QUICK -> false
            BackupScheduleMode.ON_CLOSE_SHIFT -> false
            BackupScheduleMode.DAILY -> {
                if (cfg.lastBackupTimeMs <= 0L) true
                else (now - cfg.lastBackupTimeMs) >= 23L * 60L * 60L * 1000L
            }
            BackupScheduleMode.WEEKLY -> {
                if (cfg.lastBackupTimeMs <= 0L) true
                else (now - cfg.lastBackupTimeMs) >= 7L * 24L * 60L * 60L * 1000L
            }
        }
    }

    fun createLocalBackupSnapshot(context: Context, triggerLabel: String): Pair<Boolean, String> {
        return try {
            val now = System.currentTimeMillis()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(now))
            val storeSlug = (SettingsManager(context).storeName ?: "ZoKas")
                .trim()
                .replace(Regex("[^A-Za-z0-9_-]"), "_")
                .ifBlank { "ZoKas" }

            val dbFile = context.getDatabasePath("zokas_pos_db")
            val outFile = if (dbFile != null && dbFile.exists()) {
                val target = File(getBackupFolder(context), "Backup_${storeSlug}_$stamp.db")
                FileInputStream(dbFile).use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
                target
            } else {
                val target = File(getBackupFolder(context), "Backup_${storeSlug}_$stamp.json")
                val meta = JSONObject().apply {
                    put("app", "ZoKas")
                    put("store", storeSlug)
                    put("trigger", triggerLabel)
                    put("created_at", now)
                }
                target.writeText(meta.toString(2), Charsets.UTF_8)
                target
            }

            // Simpan maksimal 15 snapshot lokal terbaru agar memori HP tetap hemat
            val files = listBackupFiles(context)
            if (files.size > 15) {
                files.drop(15).forEach { runCatching { it.delete() } }
            }

            val prev = loadSettings(context)
            val statusMsg = "Berhasil ($triggerLabel) • ${SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(now))}"
            saveSettings(
                context,
                prev.copy(
                    lastBackupTimeMs = now,
                    lastBackupFileName = outFile.name,
                    lastBackupStatus = statusMsg
                )
            )
            true to outFile.absolutePath
        } catch (e: Exception) {
            val prev = loadSettings(context)
            val errMsg = "Gagal membuat backup: ${e.message ?: "kesalahan penyimpanan"}"
            saveSettings(context, prev.copy(lastBackupStatus = errMsg))
            false to errMsg
        }
    }

    fun sendBackupFileToGoogleDrive(context: Context, filePath: String): Pair<Boolean, String> {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return false to "File backup tidak ditemukan."
            }
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Backup Data ZoKas - ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val hasDriveApp = runCatching {
                context.packageManager.getPackageInfo(GOOGLE_DRIVE_PKG, 0)
                true
            }.getOrDefault(false)

            if (hasDriveApp) {
                val driveIntent = Intent(shareIntent).apply {
                    setPackage(GOOGLE_DRIVE_PKG)
                }
                context.startActivity(driveIntent)
                true to "Membuka Google Drive untuk menyimpan ${file.name}..."
            } else {
                val chooser = Intent.createChooser(shareIntent, "Simpan Backup ke Google Drive").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                true to "Membuka menu simpan/berbagi untuk ${file.name}..."
            }
        } catch (e: Exception) {
            false to "Gagal membuka Google Drive: ${e.message ?: "tidak tersedia"}"
        }
    }

    fun runScheduledOrManualBackup(
        context: Context,
        triggerLabel: String,
        forceDriveShare: Boolean? = null
    ): Pair<Boolean, String> {
        val cfg = loadSettings(context)
        val (ok, result) = createLocalBackupSnapshot(context, triggerLabel)
        if (!ok) return false to result

        val shouldOpenDrive = forceDriveShare ?: (cfg.isEnabled && cfg.openGoogleDriveAfterBackup)
        if (shouldOpenDrive) {
            val (driveOk, driveMsg) = sendBackupFileToGoogleDrive(context, result)
            return true to "$driveMsg (Tersimpan lokal: ${File(result).name}, status kirim: ${if (driveOk) "terbuka" else "tersimpan lokal"})"
        }
        return true to "Backup lokal tersimpan: ${File(result).name}"
    }
}
