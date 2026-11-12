package com.karyaku.kasir

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.staticCompositionLocalOf
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.karyaku.kasir.data.local.ProductEntity
import java.text.NumberFormat
import java.util.Locale

// State Global untuk Activity (Fix Fingerprint)
val LocalActivity = staticCompositionLocalOf<Activity> {
    error("No Activity provided in CompositionLocalProvider")
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

// Fungsi Format Rupiah Global
fun formatRupiah(amount: Long): String {
    val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    return format.format(amount).split(",")[0].replace("Rp", "Rp ")
}

// FITUR BARU: Fungsi Generate QR Code Bitmap dari String
fun generateQrCodeBitmap(content: String, size: Int = 512): Bitmap? {
    return try {
        val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// Fungsi Simpan Barcode
fun saveBarcodeToGallery(context: Context, product: ProductEntity) {
    try {
        val width = 500
        val height = 250
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val textPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 24f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        canvas.drawText(product.name.lowercase(), width / 2f, 40f, textPaint)
        canvas.drawText("Rp ${product.price}", width / 2f, height - 20f, textPaint)

        val barcodePaint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.FILL
        }
        
        val code39Map = mapOf(
            '0' to "101001101101", '1' to "110100101011", '2' to "101100101011", '3' to "110110010101", '4' to "101001101011", '5' to "110100110101", '6' to "101100110101", '7' to "101001011011", '8' to "110100101101", '9' to "101100101101",
            'A' to "110101001011", 'B' to "101101001011", 'C' to "110110100101", 'D' to "101011001011", 'E' to "110101100101", 'F' to "101101100101", 'G' to "101010011011", 'H' to "110101001101", 'I' to "101101001101", 'J' to "101011001101", 'K' to "110101010011", 'L' to "101101010011", 'M' to "110110101001", 'N' to "101011010011", 'O' to "110101101001", 'P' to "101101101001", 'Q' to "101010110011", 'R' to "110101011001", 'S' to "101101011001", 'T' to "101011011001", 'U' to "110010101011", 'V' to "100110101011", 'W' to "110011010101", 'X' to "100101101011", 'Y' to "110010110101", 'Z' to "100110110101",
            '-' to "100101011011", '.' to "110010101101", ' ' to "100110101101", '$' to "100100100101", '/' to "100100101001", '+' to "100101001001", '%' to "101001001001", '*' to "100101101101"
        )

        val barcodeText = "*${product.barcode.ifBlank { product.name.take(10).uppercase() }}*"
        var x = 50f
        val barWidth = 3f
        val barHeight = 100f
        
        for (char in barcodeText) {
            val pattern = code39Map[char] ?: code39Map['-']!!
            for (bit in pattern) {
                if (bit == '1') {
                    canvas.drawRect(x, 70f, x + barWidth, 70f + barHeight, barcodePaint)
                }
                x += barWidth
            }
            x += barWidth 
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "barcode_${product.name.replace(" ", "_")}_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                Toast.makeText(context, "Barcode disimpan ke Galeri", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal simpan barcode: ${e.message}", Toast.LENGTH_LONG).show()
    }
}