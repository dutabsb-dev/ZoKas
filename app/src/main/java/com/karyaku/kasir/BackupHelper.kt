package com.karyaku.kasir

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object BackupHelper {
    private const val DB_NAME = "pos_database"

    // Fungsi untuk menyimpan (Backup) database ke URI (Google Drive / Penyimpanan HP)
    fun backupDatabase(context: Context, targetUri: Uri): Boolean {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return false

            val inputStream = FileInputStream(dbFile)
            val outputStream = context.contentResolver.openOutputStream(targetUri)
            
            outputStream?.use { out ->
                inputStream.use { input ->
                    input.copyTo(out)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Fungsi untuk memulihkan (Restore) database dari URI
    fun restoreDatabase(context: Context, sourceUri: Uri): Boolean {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            
            // Pastikan folder database ada
            dbFile.parentFile?.mkdirs()

            val inputStream = context.contentResolver.openInputStream(sourceUri)
            val outputStream = FileOutputStream(dbFile)
            
            outputStream.use { out ->
                inputStream?.use { input ->
                    input.copyTo(out)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}