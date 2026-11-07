package com.karyaku.kasir

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.karyaku.kasir.data.local.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExportHelper {
    fun exportTransactionsToPdf(
        context: Context,
        transactions: List<TransactionEntity>,
        targetUri: Uri
    ): Boolean {
        return try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            val paint = Paint()
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

            var y = 50f
            val margin = 40f
            val lineHeight = 20f

            // Header
            paint.textSize = 18f
            paint.isFakeBoldText = true
            canvas.drawText("Laporan Transaksi ZoKas", margin, y, paint)
            y += lineHeight * 2

            paint.textSize = 12f
            paint.isFakeBoldText = false
            canvas.drawText("Dicetak pada: ${dateFormat.format(Date())}", margin, y, paint)
            y += lineHeight
            canvas.drawText("Total Transaksi: ${transactions.size}", margin, y, paint)
            y += lineHeight * 2

            // Table Header
            paint.isFakeBoldText = true
            canvas.drawText("ID", margin, y, paint)
            canvas.drawText("Tanggal", margin + 40, y, paint)
            canvas.drawText("Pelanggan", margin + 160, y, paint)
            canvas.drawText("Total", margin + 320, y, paint)
            canvas.drawText("Status", margin + 450, y, paint)
            y += lineHeight
            
            paint.color = Color.GRAY
            canvas.drawLine(margin, y - 15, 555f, y - 15, paint)
            paint.color = Color.BLACK
            paint.isFakeBoldText = false

            // Table Body
            transactions.take(35).forEach { tx -> // Max 35 per page for simplicity
                if (y > 800f) return@forEach
                canvas.drawText("#${tx.id}", margin, y, paint)
                canvas.drawText(dateFormat.format(Date(tx.timestamp)), margin + 40, y, paint)
                canvas.drawText(tx.customerName.take(15), margin + 160, y, paint)
                canvas.drawText(formatRupiah(tx.totalPrice), margin + 320, y, paint)
                canvas.drawText(tx.status, margin + 450, y, paint)
                y += lineHeight
            }

            document.finishPage(page)

            val outputStream = context.contentResolver.openOutputStream(targetUri)
            document.writeTo(outputStream)
            document.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}