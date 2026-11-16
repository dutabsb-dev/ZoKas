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

            // Tutup database agar file aman
            AppDatabase.closeAndResetDatabase()

            // FIX: Gunakan use { } untuk memastikan stream selalu tertutup
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                FileInputStream(dbFile).use { input ->
                    input.copyTo(out)
                }
            }
            
            // Buka kembali database
            AppDatabase.getDatabase(context)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // Pastikan dibuka jika gagal
            AppDatabase.getDatabase(context)
            false
        }
    }

    fun restoreDatabase(context: Context, sourceUri: Uri): Boolean {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            dbFile.parentFile?.mkdirs()

            // 1. TUTUP DATABASE
            AppDatabase.closeAndResetDatabase()

            // 2. HAPUS FILE WAL & SHM (Wajib agar tidak bentrok)
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            // 3. TIMPA FILE DATABASE
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(dbFile).use { out ->
                    input.copyTo(out)
                }
            }
            
            // 4. FIX: JANGAN LUPA BUKA ULANG DATABASE SETELAH RESTORE
            AppDatabase.getDatabase(context)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            AppDatabase.getDatabase(context) // Pastikan dibuka jika error
            false
        }
    }
}