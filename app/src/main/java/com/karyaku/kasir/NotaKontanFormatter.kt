package com.karyaku.kasir

import android.content.Context
import com.karyaku.kasir.data.local.TransactionEntity
import com.karyaku.kasir.data.local.TransactionItemEntity
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
            signOffLabel = "Hormat kami",
            signOffName = transaction.cashierName?.takeIf { it.isNotBlank() } ?: storeName.ifBlank { "Toko" },
            disclaimerBoxText = "Barang yang sudah dibeli tidak dapat ditukar / dikembalikan"
        )
    }

    // Format teks/tabel terstruktur murni tanpa gambar bitmap/raster
    fun renderFormattedText(doc: NotaKontanDocument): String {
        val sb = StringBuilder()
        sb.appendLine(doc.storeName.uppercase(Locale.getDefault()))
        if (doc.storeAddress.isNotBlank()) sb.appendLine(doc.storeAddress)
        if (doc.storePhone.isNotBlank()) sb.appendLine("Telp: ${doc.storePhone}")
        sb.appendLine("------------------------------------------------")
        sb.appendLine("${doc.dateLine}          ${doc.recipientLine}")
        sb.appendLine("${doc.title}   ${doc.noteNumber}")
        sb.appendLine("================================================")
        sb.appendLine("NO | BANYAKNYA | NAMA BARANG          | HARGA     | JUMLAH")
        sb.appendLine("------------------------------------------------")
        doc.rows.forEach { r ->
            val noCol = r.no.toString().padEnd(2, ' ')
            val qtyCol = r.qtyText.take(9).padEnd(9, ' ')
            val nameCol = r.itemName.take(20).padEnd(20, ' ')
            val priceCol = r.unitPriceText.take(9).padStart(9, ' ')
            val sumCol = r.lineTotalText.take(10).padStart(10, ' ')
            sb.appendLine("$noCol | $qtyCol | $nameCol | $priceCol | $sumCol")
        }
        sb.appendLine("================================================")
        sb.appendLine("JUMLAH Rp. ${doc.totalAmountText}")
        sb.appendLine("------------------------------------------------")
        sb.appendLine("${doc.signOffLabel}: ${doc.signOffName}")
        sb.appendLine("[ ${doc.disclaimerBoxText} ]")
        return sb.toString().trimEnd()
    }
}
