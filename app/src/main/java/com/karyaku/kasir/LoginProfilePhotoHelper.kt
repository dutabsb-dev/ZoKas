package com.karyaku.kasir

import android.content.Context
import android.net.Uri
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

object LoginProfilePhotoHelper {
    private const val PREFS_NAME = "zokas_user_photos"

    fun getUserPhotoPath(context: Context, userId: Long): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString("user_$userId", null)
        return if (!path.isNullOrBlank() && File(path).exists()) path else null
    }

    fun saveUserPhotoFromUri(context: Context, userId: Long, uri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "user_avatars").apply { mkdirs() }
            val outFile = File(dir, "avatar_user_${userId}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            if (!outFile.exists() || outFile.length() == 0L) return null

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val oldPath = prefs.getString("user_$userId", null)
            if (!oldPath.isNullOrBlank() && oldPath != outFile.absolutePath) {
                runCatching { File(oldPath).delete() }
            }
            prefs.edit().putString("user_$userId", outFile.absolutePath).apply()
            outFile.absolutePath
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal menyimpan foto profil: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun clearUserPhoto(context: Context, userId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val oldPath = prefs.getString("user_$userId", null)
        if (!oldPath.isNullOrBlank()) {
            runCatching { File(oldPath).delete() }
        }
        prefs.edit().remove("user_$userId").apply()
    }
}
