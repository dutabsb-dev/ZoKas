package com.karyaku.kasir

import android.content.Context
import android.net.Uri
import com.karyaku.kasir.data.local.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object BackupHelper {
    private const val DB_NAME = "pos_database"

    fun backupDatabase(context: Context, targetUri: Uri): Boolean {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return false

            // Tutup database agar file bisa disalin secara aman
            AppDatabase.closeAndResetDatabase()

            val inputStream = FileInputStream(dbFile)
            val outputStream = context.contentResolver.openOutputStream(targetUri)
            
            outputStream?.use { out ->
                inputStream.use { input ->
                    input.copyTo(out)
                }
            }
            
            // Buka kembali database
            AppDatabase.getDatabase(context)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // Pastikan database dibuka kembali jika terjadi error
            AppDatabase.getDatabase(context)
            false
        }
    }

    fun restoreDatabase(context: Context, sourceUri: Uri): Boolean {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            dbFile.parentFile?.mkdirs()

            // 1. TUTUP DATABASE TERLEBIH DAHULU
            AppDatabase.closeAndResetDatabase()

            // 2. HAPUS FILE WAL & SHM AGAR TIDAK BENTROK
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            // 3. TIMPA FILE DATABASE BARU
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