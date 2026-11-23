package com.karyaku.kasir

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
    private fun trimMargins(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 2 || h <= 2 || !src.isMutable && w * h > 2_000_000) return src
        return runCatching {
            val pixels = IntArray(w * h)
            src.getPixels(pixels, 0, w, 0, 0, w, h)
            var top = 0
            var bottom = h - 1
            var left = 0
            var right = w - 1

            fun isNonContentPixel(p: Int): Boolean {
                val a = Color.alpha(p)
                if (a < 24) return true
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                return r > 246 && g > 246 && b > 246
            }

            while (top < bottom) {
                var hasContent = false
                val rowStart = top * w
                for (x in 0 until w) {
                    if (!isNonContentPixel(pixels[rowStart + x])) {
                        hasContent = true
                        break
                    }
                }
                if (hasContent) break
                top++
            }

            while (bottom > top) {
                var hasContent = false
                val rowStart = bottom * w
                for (x in 0 until w) {
                    if (!isNonContentPixel(pixels[rowStart + x])) {
                        hasContent = true
                        break
                    }
                }
                if (hasContent) break
                bottom--
            }

            while (left < right) {
                var hasContent = false
                for (y in top..bottom) {
                    if (!isNonContentPixel(pixels[y * w + left])) {
                        hasContent = true
                        break
                    }
                }
                if (hasContent) break
                left++
            }

            while (right > left) {
                var hasContent = false
                for (y in top..bottom) {
                    if (!isNonContentPixel(pixels[y * w + right])) {
                        hasContent = true
                        break
                    }
                }
                if (hasContent) break
                right--
            }

            val cropW = (right - left + 1).coerceAtLeast(1)
            val cropH = (bottom - top + 1).coerceAtLeast(1)
            if (left == 0 && top == 0 && cropW == w && cropH == h) src
            else Bitmap.createBitmap(src, left, top, cropW, cropH)
        }.getOrDefault(src)
    }

    fun resolveReceiptLogoBitmap(context: Context, logoPath: String?): Bitmap? {
        if (!logoPath.isNullOrBlank()) {
            val file = File(logoPath)
            if (file.exists() && file.isFile) {
                val custom = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                if (custom != null) return trimMargins(custom)
            }
        }
        return runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.logo_zokas)?.let { trimMargins(it) }
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
