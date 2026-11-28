package com.karyaku.kasir

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
            recipientLine = "Kepada Yth.: ${transaction.customerName?.takeIf { it.isNotBlank() } ?: "Umum"}",
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

    // Format cetak thermal ESC/POS yang rapi tanpa garis vertikal putus-putus acak
    fun renderThermalPrintText(doc: NotaKontanDocument): String {
        val sb = StringBuilder()
        sb.appendLine(doc.storeName.uppercase(Locale.getDefault()))
        if (doc.storeAddress.isNotBlank()) sb.appendLine(doc.storeAddress)
        if (doc.storePhone.isNotBlank()) sb.appendLine("Telp: ${doc.storePhone}")
        sb.appendLine("========================================")
        sb.appendLine("${doc.title}          ${doc.noteNumber}")
        sb.appendLine("${doc.dateLine}")
        sb.appendLine("${doc.recipientLine}")
        sb.appendLine("----------------------------------------")
        sb.appendLine("NO  QTY    NAMA BARANG       HARGA   JUMLAH")
        sb.appendLine("----------------------------------------")
        doc.rows.filter { it.itemName.isNotBlank() || it.qtyText.isNotBlank() }.forEach { r ->
            val noCol = "${r.no}.".padEnd(3, ' ')
            val qtyCol = r.qtyText.take(5).padEnd(5, ' ')
            val nameCol = r.itemName.take(15).padEnd(15, ' ')
            val priceCol = r.unitPriceText.replace("Rp", "").trim().take(7).padStart(7, ' ')
            val sumCol = r.lineTotalText.replace("Rp", "").trim().take(8).padStart(8, ' ')
            sb.appendLine("$noCol $qtyCol $nameCol $priceCol $sumCol")
        }
        sb.appendLine("----------------------------------------")
        sb.appendLine("JUMLAH Rp.       : ${doc.totalAmountText}")
        sb.appendLine("Metode           : ${doc.paymentMethodText}")
        sb.appendLine("Tunai/Bayar      : ${doc.cashReceivedText}")
        sb.appendLine("Kembalian        : ${doc.changeAmountText}")
        sb.appendLine("========================================")
        sb.appendLine("${doc.signOffLabel}: ${doc.signOffName}")
        sb.appendLine(doc.disclaimerBoxText)
        return sb.toString().trimEnd()
    }

    // Merender JPG HD berwarna persis tampilan kartu NOTA KONTAN di aplikasi untuk dikirim ke WA
    fun generateNotaKontanJpg(context: Context, doc: NotaKontanDocument): File? {
        return try {
            val width = 900
            val pad = 36f
            val headerH = 155f
            val metaH = 54f
            val tableHeaderH = 54f
            val rowH = 44f
            val rowsCount = doc.rows.size.coerceAtLeast(1)
            val footerH = 195f
            val totalH = (pad * 2f + headerH + metaH + tableHeaderH + rowsCount * rowH + footerH + 24f).toInt()

            val bitmap = Bitmap.createBitmap(width, totalH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Latar hijau lembut khas kartu dialog nota kontan
            canvas.drawColor(AndroidColor.parseColor("#E8F5EE"))

            // Kartu putih utama dengan sudut membulat & border halus
            val cardRect = RectF(18f, 18f, width - 18f, totalH - 18f)
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(cardRect, 28f, 28f, cardPaint)

            val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#A7D7C5")
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            canvas.drawRoundRect(cardRect, 28f, 28f, cardBorder)

            var y = pad + 26f

            // Judul NOTA KONTAN (kiri) & No. X (kanan hijau)
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#0F172A")
                textSize = 30f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val noPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#059669")
                textSize = 24f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(doc.title, pad + 8f, y, titlePaint)
            canvas.drawText(doc.noteNumber, width - pad - 8f, y, noPaint)

            // Nama & alamat toko ringkas di bawah judul
            y += 28f
            val storePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#334155")
                textSize = 18f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            canvas.drawText(
                if (doc.storeAddress.isNotBlank()) "${doc.storeName} • ${doc.storeAddress}" else doc.storeName,
                pad + 8f,
                y,
                storePaint
            )

            // Baris Tanggal & Tuan/Toko
            y += 42f
            val metaLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#1E293B")
                textSize = 20f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val metaRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#1E293B")
                textSize = 20f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(doc.dateLine, pad + 8f, y, metaLeftPaint)
            canvas.drawText(doc.recipientLine, width - pad - 8f, y, metaRightPaint)

            // Header Tabel (NO | BANYAKNYA | NAMA BARANG | HARGA | JUMLAH)
            y += 18f
            val tableLeft = pad + 8f
            val tableRight = width - pad - 8f
            val tableW = tableRight - tableLeft

            val thBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#ECFDF5")
                style = Paint.Style.FILL
            }
            val thRect = RectF(tableLeft, y, tableRight, y + tableHeaderH)
            canvas.drawRoundRect(thRect, 10f, 10f, thBg)

            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#94A3B8")
                style = Paint.Style.STROKE
                strokeWidth = 1.6f
            }
            canvas.drawRoundRect(thRect, 10f, 10f, gridPaint)

            val colNoW = 62f
            val colQtyW = 148f
            val colPriceW = 152f
            val colSumW = 166f
            val colNameX = tableLeft + colNoW + colQtyW + 10f
            val colPriceRight = tableLeft + colNoW + colQtyW + (tableW - colNoW - colQtyW - colPriceW - colSumW) + colPriceW - 8f
            val colSumRight = tableRight - 8f

            val thTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#0F172A")
                textSize = 16.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val thRightPaint = Paint(thTextPaint).apply {
                textAlign = Paint.Align.RIGHT
            }
            val thCenterPaint = Paint(thTextPaint).apply {
                textAlign = Paint.Align.CENTER
            }

            val thTextY = y + 33f
            canvas.drawText("NO", tableLeft + 10f, thTextY, thTextPaint)
            canvas.drawText("BANYAKNYA", tableLeft + colNoW + colQtyW / 2f, thTextY, thCenterPaint)
            canvas.drawText("NAMA BARANG", colNameX, thTextY, thTextPaint)
            canvas.drawText("HARGA", colPriceRight, thTextY, thRightPaint)
            canvas.drawText("JUMLAH", colSumRight, thTextY, thRightPaint)

            // Baris Data 1..N
            var rowTop = y + tableHeaderH
            val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#1E293B")
                textSize = 18f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val cellBoldPaint = Paint(cellPaint).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                color = AndroidColor.parseColor("#0F172A")
            }
            val cellRightPaint = Paint(cellPaint).apply {
                textAlign = Paint.Align.RIGHT
            }
            val cellSumPaint = Paint(cellBoldPaint).apply {
                textAlign = Paint.Align.RIGHT
            }
            val cellCenterPaint = Paint(cellPaint).apply {
                textAlign = Paint.Align.CENTER
            }
            val rowLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#E2E8F0")
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
            }

            doc.rows.forEach { r ->
                val rBottom = rowTop + rowH
                canvas.drawLine(tableLeft, rBottom, tableRight, rBottom, rowLinePaint)
                val ty = rowTop + 28f
                canvas.drawText("${r.no}.", tableLeft + 10f, ty, cellPaint)
                if (r.qtyText.isNotBlank()) {
                    canvas.drawText(r.qtyText, tableLeft + colNoW + colQtyW / 2f, ty, cellCenterPaint)
                }
                if (r.itemName.isNotBlank()) {
                    canvas.drawText(r.itemName.take(22), colNameX, ty, cellBoldPaint)
                }
                if (r.unitPriceText.isNotBlank()) {
                    canvas.drawText(r.unitPriceText, colPriceRight, ty, cellRightPaint)
                }
                if (r.lineTotalText.isNotBlank()) {
                    canvas.drawText(r.lineTotalText, colSumRight, ty, cellSumPaint)
                }
                rowTop = rBottom
            }

            // Footer: Kiri (Hormat kami + kotak disclaimer) | Kanan (JUMLAH Rp., Bayar, Kembali)
            val footTop = rowTop + 24f
            val signPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#0F172A")
                textSize = 19f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            canvas.drawText(doc.signOffLabel, tableLeft, footTop + 14f, signPaint)

            val boxRect = RectF(tableLeft, footTop + 28f, tableLeft + 430f, footTop + 102f)
            val boxBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#F1F5F9")
                style = Paint.Style.FILL
            }
            val boxStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#64748B")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(boxRect, 8f, 8f, boxBg)
            canvas.drawRoundRect(boxRect, 8f, 8f, boxStroke)

            val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#1E293B")
                textSize = 15.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            canvas.drawText("Barang yang sudah dibeli tidak dapat", boxRect.left + 12f, boxRect.top + 30f, discPaint)
            canvas.drawText("ditukar / dikembalikan", boxRect.left + 12f, boxRect.top + 54f, discPaint)

            val rightLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#0F172A")
                textSize = 18f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val totalValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#059669")
                textSize = 26f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val payDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#475569")
                textSize = 18f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            val changePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#D97706")
                textSize = 19.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }

            canvas.drawText("JUMLAH Rp.", tableRight, footTop + 14f, rightLabelPaint)
            canvas.drawText(doc.totalAmountText, tableRight, footTop + 46f, totalValPaint)
            canvas.drawText("Bayar: ${doc.cashReceivedText}", tableRight, footTop + 74f, payDetailPaint)
            canvas.drawText("Kembali: ${doc.changeAmountText}", tableRight, footTop + 100f, changePaint)

            val outDir = File(context.getExternalFilesDir(null), "receipts")
            outDir.mkdirs()
            val jpgFile = File(outDir, "NotaKontan_${doc.noteNumber.filter { it.isDigit() }.ifBlank { "1" }}.jpg")
            FileOutputStream(jpgFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
            }
            bitmap.recycle()
            jpgFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun sendJpgViaWhatsApp(context: Context, doc: NotaKontanDocument) {
        CoroutineScope(Dispatchers.IO).launch {
            val jpgFile = generateNotaKontanJpg(context, doc)
            withContext(Dispatchers.Main) {
                if (jpgFile != null) {
                    try {
                        val authority = "${context.packageName}.fileprovider"
                        val uri: Uri = FileProvider.getUriForFile(context, authority, jpgFile)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TEXT, "${doc.title} ${doc.noteNumber} - ${doc.storeName} (Total: ${doc.totalAmountText}, Kembali: ${doc.changeAmountText})")
                            clipData = ClipData.newRawUri("NotaKontan", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(shareIntent, "Kirim Nota Kontan (JPG) via...")
                            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(chooser)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Gagal mengirim JPG: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Gagal membuat gambar JPG Nota Kontan.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun printNotaKontan(
        context: Context,
        settingsManager: SettingsManager,
        doc: NotaKontanDocument,
        isCashPayment: Boolean = true,
        onDone: (Boolean, String) -> Unit
    ) {
        val mac = settingsManager.printerMacAddress
        if (mac.isNullOrBlank()) {
            onDone(false, "Printer Bluetooth belum diatur di Pengaturan.")
            return
        }
        val printText = renderThermalPrintText(doc)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val printerManager = BluetoothPrinterManager(context)
                val cfg = PrinterUpgradePrefs.load(context)
                val out = ByteArrayOutputStream()
                out.write(EscPosCommands.INIT)
                out.write(EscPosCommands.ALIGN_LEFT)
                out.write(printText.toByteArray(charset("GBK")))
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
