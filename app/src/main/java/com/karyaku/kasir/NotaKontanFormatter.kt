package com.karyaku.kasir

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.karyaku.kasir.data.local.TransactionEntity
import com.karyaku.kasir.data.local.TransactionItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NotaKontanRow(
    val no: Int,
    val qtyText: String,
    val itemName: String,
    val unitPriceText: String,
    val lineTotalText: String
)

data class NotaKontanDocument(
    val title: String = "NOTA KONTAN",
    val noteNumber: String,
    val dateLine: String,
    val recipientLine: String,
    val storeName: String,
    val storeAddress: String,
    val storePhone: String,
    val rows: List<NotaKontanRow>,
    val totalAmountText: String,
    val paymentMethodText: String,
    val cashReceivedText: String,
    val changeAmountText: String,
    val signOffLabel: String = "Hormat kami",
    val signOffName: String,
    val disclaimerBoxText: String = "Barang yang sudah dibeli tidak dapat ditukar / dikembalikan"
)

object NotaKontanFormatter {
    private const val PREFS_NAME = "zokas_nota_kontan_prefs"
    private const val KEY_ENABLED = "nota_kontan_enabled"
    private const val KEY_MIN_ROWS = "nota_kontan_min_rows"

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun getMinRows(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MIN_ROWS, 15)
            .coerceIn(5, 30)
    }

    fun setMinRows(context: Context, minRows: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MIN_ROWS, minRows.coerceIn(5, 30))
            .apply()
    }

    fun buildDocument(
        transaction: TransactionEntity,
        items: List<TransactionItemEntity>,
        storeName: String,
        storeAddress: String = "",
        storePhone: String = "",
        minRows: Int = 15
    ): NotaKontanDocument {
        val fmtDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val mappedRows = items.mapIndexed { idx, it ->
            val qStr = if (it.quantity > 0) it.quantity.toString() else "1"
            val cleanName = if (it.notes.isNotBlank()) "${it.productName} (${it.notes})" else it.productName
            NotaKontanRow(
                no = idx + 1,
                qtyText = qStr,
                itemName = cleanName,
                unitPriceText = formatRupiah(it.price),
                lineTotalText = formatRupiah(it.subtotal)
            )
        }
        val rowCount = maxOf(minRows, mappedRows.size)
        val paddedRows = (1..rowCount).map { rowNum ->
            mappedRows.getOrNull(rowNum - 1) ?: NotaKontanRow(
                no = rowNum,
                qtyText = "",
                itemName = "",
                unitPriceText = "",
                lineTotalText = ""
            )
        }

        val effectivePaid = if (transaction.cashReceived > 0L) transaction.cashReceived else transaction.totalPrice
        val effectiveChange = if (transaction.cashReceived >= transaction.totalPrice) {
            (transaction.cashReceived - transaction.totalPrice).coerceAtLeast(0L)
        } else {
            transaction.changeAmount.coerceAtLeast(0L)
        }

        return NotaKontanDocument(
            title = "NOTA KONTAN",
            noteNumber = "No. ${transaction.id}",
            dateLine = "Tgl. ${fmtDate.format(Date(transaction.timestamp))}",
            recipientLine = "Tuan / Toko: ${transaction.customerName?.takeIf { it.isNotBlank() } ?: "Umum"}",
            storeName = storeName.ifBlank { "TOKO" },
            storeAddress = storeAddress,
            storePhone = storePhone,
            rows = paddedRows,
            totalAmountText = formatRupiah(transaction.totalPrice),
            paymentMethodText = transaction.paymentMethod.ifBlank { "CASH" },
            cashReceivedText = formatRupiah(effectivePaid),
            changeAmountText = formatRupiah(effectiveChange),
            signOffLabel = "Hormat kami",
            signOffName = transaction.cashierName?.takeIf { it.isNotBlank() } ?: storeName.ifBlank { "Toko" },
            disclaimerBoxText = "Barang yang sudah dibeli tidak dapat ditukar / dikembalikan"
        )
    }

    // Format teks/tabel murni tanpa gambar raster/bitmap
    fun renderFormattedText(doc: NotaKontanDocument): String {
        val sb = StringBuilder()
        sb.appendLine(doc.storeName.uppercase(Locale.getDefault()))
        if (doc.storeAddress.isNotBlank()) sb.appendLine(doc.storeAddress)
        if (doc.storePhone.isNotBlank()) sb.appendLine("Telp: ${doc.storePhone}")
        sb.appendLine("------------------------------------------------")
        sb.appendLine("${doc.dateLine}   ${doc.recipientLine}")
        sb.appendLine("${doc.title}   ${doc.noteNumber}")
        sb.appendLine("================================================")
        sb.appendLine("NO | BANYAKNYA | NAMA BARANG       | HARGA    | JUMLAH")
        sb.appendLine("------------------------------------------------")
        doc.rows.forEach { r ->
            val noCol = r.no.toString().padEnd(2, ' ')
            val qtyCol = r.qtyText.take(9).padEnd(9, ' ')
            val nameCol = r.itemName.take(17).padEnd(17, ' ')
            val priceCol = r.unitPriceText.take(8).padStart(8, ' ')
            val sumCol = r.lineTotalText.take(9).padStart(9, ' ')
            sb.appendLine("$noCol | $qtyCol | $nameCol | $priceCol | $sumCol")
        }
        sb.appendLine("================================================")
        sb.appendLine("JUMLAH Rp. ${doc.totalAmountText}")
        sb.appendLine("Metode     : ${doc.paymentMethodText}")
        sb.appendLine("Tunai/Bayar: ${doc.cashReceivedText}")
        sb.appendLine("Kembalian  : ${doc.changeAmountText}")
        sb.appendLine("------------------------------------------------")
        sb.appendLine("${doc.signOffLabel}: ${doc.signOffName}")
        sb.appendLine("[ ${doc.disclaimerBoxText} ]")
        return sb.toString().trimEnd()
    }

    fun sendViaWhatsApp(context: Context, formattedText: String) {
        val waIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, formattedText)
            setPackage("com.whatsapp")
        }
        val waBusinessIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, formattedText)
            setPackage("com.whatsapp.w4b")
        }
        try {
            context.startActivity(waIntent)
        } catch (_: Exception) {
            try {
                context.startActivity(waBusinessIntent)
            } catch (_: Exception) {
                val fallback = Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, formattedText)
                    },
                    "Kirim Nota Kontan via..."
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                runCatching { context.startActivity(fallback) }
                    .onFailure {
                        Toast.makeText(context, "Gagal membuka WhatsApp.", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    fun generateNotaKontanPdf(
        context: Context,
        formattedText: String,
        transactionId: Long
    ): File? {
        val document = PdfDocument()
        val lines = formattedText.lines()
        val paint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = 10.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }
        val sampleWidth = paint.measureText("W".repeat(58))
        val padding = 24f
        val pageWidth = (sampleWidth + padding * 2).toInt().coerceAtLeast(420)
        val lineHeight = 15f
        val pageHeight = (lines.size * lineHeight + padding * 2 + 24f).toInt().coerceAtLeast(360)

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(AndroidColor.WHITE)

        var y = padding + 10f
        for (line in lines) {
            val isBold = line.startsWith("NOTA KONTAN") ||
                line.startsWith("JUMLAH Rp.") ||
                line.startsWith("Kembalian")
            paint.typeface = Typeface.create(
                Typeface.MONOSPACE,
                if (isBold) Typeface.BOLD else Typeface.NORMAL
            )
            canvas.drawText(line, padding, y, paint)
            y += lineHeight
        }
        document.finishPage(page)

        val pdfDir = File(context.getExternalFilesDir(null), "receipts")
        pdfDir.mkdirs()
        val pdfFile = File(pdfDir, "NotaKontan_${transactionId}.pdf")
        return try {
            FileOutputStream(pdfFile).use { stream ->
                document.writeTo(stream)
            }
            document.close()
            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            document.close()
            null
        }
    }

    fun sharePdfViaWhatsApp(
        context: Context,
        formattedText: String,
        transactionId: Long,
        storeName: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val pdfFile = generateNotaKontanPdf(context, formattedText, transactionId)
            withContext(Dispatchers.Main) {
                if (pdfFile != null) {
                    try {
                        val authority = "${context.packageName}.fileprovider"
                        val uri: Uri = FileProvider.getUriForFile(context, authority, pdfFile)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_TEXT, "Nota Kontan #$transactionId - $storeName")
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri("NotaKontan", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(shareIntent, "Kirim Nota Kontan (PDF / WA)")
                            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(chooser)
                    } catch (e: Exception) {
                        sendViaWhatsApp(context, formattedText)
                    }
                } else {
                    sendViaWhatsApp(context, formattedText)
                }
            }
        }
    }

    fun printNotaKontan(
        context: Context,
        settingsManager: SettingsManager,
        formattedText: String,
        isCashPayment: Boolean = true,
        onDone: (Boolean, String) -> Unit
    ) {
        val mac = settingsManager.printerMacAddress
        if (mac.isNullOrBlank()) {
            onDone(false, "Printer Bluetooth belum diatur di Pengaturan.")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val printerManager = BluetoothPrinterManager(context)
                val cfg = PrinterUpgradePrefs.load(context)
                val out = ByteArrayOutputStream()
                out.write(EscPosCommands.INIT)
                out.write(EscPosCommands.ALIGN_LEFT)
                out.write(formattedText.toByteArray(charset("GBK")))
                val feed = cfg.feedLines.coerceIn(0, 3)
                if (feed > 0) {
                    out.write(byteArrayOf(0x1B, 0x64, feed.toByte()))
                }
                if (cfg.autoCutPaper) {
                    out.write(EscPosCommands.CUT_PAPER)
                }
                if (cfg.autoOpenDrawerOnCash && isCashPayment) {
                    printerManager.openCashDrawer(mac)
                }
                val ok = printerManager.printData(mac, out.toByteArray())
                withContext(Dispatchers.Main) {
                    if (ok) onDone(true, "Nota Kontan dicetak!")
                    else onDone(false, "Gagal mencetak ke printer ($mac).")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onDone(false, "Error cetak: ${e.message ?: "gagal"}")
                }
            }
        }
    }
}
