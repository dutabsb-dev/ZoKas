package com.karyaku.kasir

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object ReceiptLogoHelper {
    fun resolveReceiptLogoBitmap(context: Context, logoPath: String?): Bitmap? {
        if (!logoPath.isNullOrBlank()) {
            val file = File(logoPath)
            if (file.exists() && file.isFile) {
                val custom = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                if (custom != null) return custom
            }
        }
        return runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.logo_zokas)
        }.getOrNull()
    }
}
