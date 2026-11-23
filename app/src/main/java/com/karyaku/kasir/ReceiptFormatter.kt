package com.karyaku.kasir

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object ReceiptFormatter {
    // Batas karakter printer thermal 58mm adalah 32.
    // Jika printer Anda 80mm, ubah menjadi 48.
    private const val LINE_WIDTH = 32 

    fun formatRupiah(amount: Double): String {
        val formatter = DecimalFormat("#,###", DecimalFormatSymbols(Locale("id", "ID")))
        return "Rp " + formatter.format(amount).replace(",", ".")
    }

    fun buildReceipt(
        storeName: String,
        storeAddress: String,
        storePhone: String,
        storeFooter: String,
        transactionId: Long,
        dateStr: String,
        orderType: String,
        cashierName: String,
        customerName: String,
        items: List<ReceiptItem>,
        subtotal: Double,
        total: Double,
        payment: Double,
        change: Double
    ): String {
        val sb = StringBuilder()

        // --- HEADER ---
        sb.append(centerText(storeName.uppercase())).append("\n")
        if (storeAddress.isNotBlank()) sb.append(centerText(storeAddress)).append("\n")
        if (storePhone.isNotBlank()) sb.append(centerText("Telp: $storePhone")).append("\n")
        sb.append("=".repeat(LINE_WIDTH)).append("\n")

        // --- INFO TRANSAKSI ---
        sb.append(formatLeftRight("ID: #$transactionId", dateStr)).append("\n")
        sb.append("Tipe: $orderType\n")
        sb.append("Kasir: $cashierName\n")
        sb.append("Pelanggan: ${if (customerName.isBlank()) "Pelanggan Umum" else customerName}\n")
        sb.append("-".repeat(LINE_WIDTH)).append("\n")

        // --- DAFTAR BARANG (FIXED: Word Wrap) ---
        items.forEach { item ->
            val qtyText = "${item.qty}x "
            val priceText = formatRupiah(item.price * item.qty)
            
            // 1. Cetak Nama Barang (dipotong jika kepanjangan)
            val nameLines = wrapText(item.name, LINE_WIDTH - qtyText.length)
            for ((index, line) in nameLines.withIndex()) {
                if (index == 0) sb.append(qtyText).append(line).append("\n")
                else sb.append(" ".repeat(qtyText.length)).append(line).append("\n")
            }

            // 2. Cetak Harga (Rata Kanan di baris baru)
            sb.append(formatLeftRight("", priceText)).append("\n")

            // 3. Cetak Varian / Catatan (Otomatis turun ke bawah jika panjang)
            if (item.variation.isNotBlank()) {
                val variationLines = wrapText("  *Cat: ${item.variation}", LINE_WIDTH)
                for (line in variationLines) {
                    sb.append(line).append("\n")
                }
            }
        }

        // --- TOTAL & PEMBAYARAN ---
        sb.append("-".repeat(LINE_WIDTH)).append("\n")
        sb.append(formatLeftRight("Subtotal:", formatRupiah(subtotal))).append("\n")
        sb.append(formatLeftRight("Total Belanja:", formatRupiah(total))).append("\n")
        sb.append(formatLeftRight("Tunai:", formatRupiah(payment))).append("\n")
        sb.append(formatLeftRight("Kembalian:", formatRupiah(change))).append("\n")
        
        // --- FOOTER ---
        sb.append("=".repeat(LINE_WIDTH)).append("\n")
        if (storeFooter.isNotBlank()) {
            sb.append(centerText(storeFooter)).append("\n")
        }

        return sb.toString().trimEnd() + "\n"
    }

    // --- FUNGSI BANTU ---

    private fun centerText(text: String): String {
        if (text.length >= LINE_WIDTH) return text
        val space = (LINE_WIDTH - text.length) / 2
        return " ".repeat(space) + text
    }

    private fun formatLeftRight(left: String, right: String): String {
        if (left.length + right.length >= LINE_WIDTH) {
            val space = LINE_WIDTH - right.length
            return left + "\n" + " ".repeat(if (space > 0) space else 0) + right
        } else {
            val space = LINE_WIDTH - left.length - right.length
            return left + " ".repeat(space) + right
        }
    }

    private fun wrapText(text: String, maxWidth: Int): List<String> {
        if (text.length <= maxWidth) return listOf(text)
        val lines = mutableListOf<String>()
        var currentLine = ""
        val words = text.split(" ")
        
        for (word in words) {
            if (currentLine.length + word.length + 1 > maxWidth) {
                lines.add(currentLine)
                currentLine = word
            } else {
                if (currentLine.isNotEmpty()) currentLine += " "
                currentLine += word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines
    }
}

data class ReceiptItem(
    val name: String,
    val qty: Int,
    val price: Double,
    val variation: String = ""
)