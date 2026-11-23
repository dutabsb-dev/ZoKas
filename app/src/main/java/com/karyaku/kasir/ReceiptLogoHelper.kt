package com.karyaku.kasir

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
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

    fun resolveThermalLogoBitmap(context: Context, logoPath: String?, targetWidthPx: Int = 192): Bitmap? {
        val raw = resolveReceiptLogoBitmap(context, logoPath) ?: return null
        val safeW = targetWidthPx.coerceAtLeast(64)
        val alignedW = safeW + ((8 - (safeW % 8)) % 8)
        val ratio = alignedW.toFloat() / raw.width.coerceAtLeast(1).toFloat()
        val targetH = (raw.height * ratio).toInt().coerceAtLeast(24)
        return runCatching {
            Bitmap.createScaledBitmap(raw, alignedW, targetH, true)
        }.getOrDefault(raw)
    }
}

@Composable
fun ReceiptLogoHeader(
    logoPath: String?,
    modifier: Modifier = Modifier.size(56.dp)
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bmp = remember(logoPath) {
        ReceiptLogoHelper.resolveReceiptLogoBitmap(context, logoPath)
    }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Logo Toko",
            contentScale = ContentScale.Fit,
            modifier = modifier.clip(RoundedCornerShape(8.dp))
        )
    }
}
