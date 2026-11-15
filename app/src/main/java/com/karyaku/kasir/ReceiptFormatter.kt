package com.karyaku.kasir

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formatter khusus untuk mencetak struk thermal (58mm / 32 karakter).
 * Menyelesaikan masalah teks bertumpuk saat nama barang terlalu panjang.
 */
object ReceiptFormatter {
    // Standar printer thermal 58mm adalah 32 karakter per baris.
    // Jika printer Anda 80mm, ubah angka ini menjadi 48.
    private const val LINE_WIDTH = 32 

    // Fungsi bantu untuk format Rupiah
    fun formatRupiah(amount: Double): String {
        val formatter = DecimalFormat("#,###", DecimalFormatSymbols(Locale("id", "ID")))
        return "Rp " + formatter.format(amount).replace(",", ".")
    }

    /**
     * Fungsi utama untuk merakit teks struk.
     * Panggil fungsi ini di dalam PrinterHelper/BluetoothPrinterManager Anda.
     */
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

        // --- BAGIAN 1: HEADER TOKO ---
        sb.append(centerText(storeName.uppercase())).append("\n")
        if (storeAddress.isNotBlank()) sb.append(centerText(storeAddress)).append("\n")
        if (storePhone.isNotBlank()) sb.append(centerText("Telp: $storePhone")).append("\n")
        sb.append("=".repeat(LINE_WIDTH)).append("\n")

        // --- BAGIAN 2: INFO TRANSAKSI ---
        sb.append(formatLeftRight("ID: #$transactionId", dateStr)).append("\n")
        sb.append("Tipe: $orderType\n")
        sb.append("Kasir: $cashierName\n")
        sb.append("Pelanggan: ${if (customerName.isBlank()) "Pelanggan Umum" else customerName}\n")
        sb.append("-".repeat(LINE_WIDTH)).append("\n")

        // --- BAGIAN 3: DAFTAR BARANG (INI YANG DIPERBAIKI) ---
        items.forEach { item ->
            val qtyText = "${item.qty}x "
            val priceText = formatRupiah(item.price * item.qty)
            
            // Cek apakah Nama Barang + Harga melebihi 32 karakter
            if (qtyText.length + item.name.length + priceText.length > LINE_WIDTH) {
                // Jika ya, cetak nama barang di baris 1
                sb.append(qtyText).append(item.name).append("\n")
                // Lalu cetak harga di baris 2 (rata kanan)
                sb.append(formatLeftRight("", priceText)).append("\n")
            } else {
                // Jika muat, cetak dalam satu baris rapi
                val space = LINE_WIDTH - qtyText.length - item.name.length - priceText.length
                sb.append(qtyText).append(item.name).append(" ".repeat(space)).append(priceText).append("\n")
            }

            // Cetak Varian / Catatan (Otomatis turun ke bawah jika panjang)
            if (item.variation.isNotBlank()) {
                val variationText = "  *Cat: ${item.variation}"
                sb.append(wrapText(variationText, LINE_WIDTH)).append("\n")
            }
        }

        // --- BAGIAN 4: TOTAL & PEMBAYARAN ---
        sb.append("-".repeat(LINE_WIDTH)).append("\n")
        sb.append(formatLeftRight("Subtotal:", formatRupiah(subtotal))).append("\n")
        sb.append(formatLeftRight("Total Belanja:", formatRupiah(total))).append("\n")
        sb.append(formatLeftRight("Tunai:", formatRupiah(payment))).append("\n")
        sb.append(formatLeftRight("Kembalian:", formatRupiah(change))).append("\n")
        
        // --- BAGIAN 5: FOOTER ---
        sb.append("=".repeat(LINE_WIDTH)).append("\n")
        if (storeFooter.isNotBlank()) {
            sb.append(centerText(storeFooter)).append("\n")
        }
        sb.append("\n\n\n") // Jarak untuk potong kertas

        return sb.toString()
    }

    // --- FUNGSI BANTU FORMATTING ---

    // Membuat teks rata tengah
    private fun centerText(text: String): String {
        if (text.length >= LINE_WIDTH) return text
        val space = (LINE_WIDTH - text.length) / 2
        return " ".repeat(space) + text
    }

    // Membuat teks kiri & kanan (untuk Subtotal, Total, dll)
    private fun formatLeftRight(left: String, right: String): String {
        if (left.length + right.length >= LINE_WIDTH) {
            val space = LINE_WIDTH - right.length
            return left + "\n" + " ".repeat(if (space > 0) space else 0) + right
        } else {
            val space = LINE_WIDTH - left.length - right.length
            return left + " ".repeat(space) + right
        }
    }

    // Memotong teks panjang menjadi beberapa baris (Word Wrap)
    private fun wrapText(text: String, maxWidth: Int): String {
        if (text.length <= maxWidth) return text
        val sb = StringBuilder()
        var currentLine = ""
        val words = text.split(" ")
        
        for (word in words) {
            if (currentLine.length + word.length + 1 > maxWidth) {
                sb.append(currentLine).append("\n")
                currentLine = word
            } else {
                if (currentLine.isNotEmpty()) currentLine += " "
                currentLine += word
            }
        }
        if (currentLine.isNotEmpty()) sb.append(currentLine)
        return sb.toString()
    }
}

// Data class untuk mempermudah passing data ke ReceiptFormatter
data class ReceiptItem(
    val name: String,
    val qty: Int,
    val price: Double,
    val variation: String = ""
)