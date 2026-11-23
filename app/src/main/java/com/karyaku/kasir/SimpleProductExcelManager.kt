package com.karyaku.kasir

import android.content.Context
import android.net.Uri
import com.karyaku.kasir.data.local.ProductEntity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class SimpleProductRow(
    val name: String,
    val category: String,
    val costPrice: Long,
    val sellPrice: Long,
    val stock: Int,
    val unit: String,
    val sku: String,
    val barcode: String,
    val variantName: String = "",
    val variantPrice: Long = 0L,
    val variantStock: Int = 0,
    val variantBarcode: String = ""
)

object SimpleProductExcelManager {
    // Template kolom terpisah (1 kolom = 1 data) agar mudah diedit tanpa rentan typo di kolom gabungan
    val SIMPLE_HEADERS = listOf(
        "Nama Produk",
        "Kategori",
        "Harga Beli",
        "Harga Jual",
        "Stok",
        "Satuan",
        "SKU",
        "Barcode Kemasan",
        "Nama Varian",
        "Harga Varian",
        "Stok Varian",
        "Barcode Varian"
    )

    fun buildSimpleTemplateCsv(): String {
        val sb = StringBuilder()
        // UTF-8 BOM agar Excel / Google Sheets membuka kolom terpisah secara otomatis
        sb.append('\uFEFF')
        sb.appendLine(SIMPLE_HEADERS.joinToString(",") { escapeCsvCell(it) })

        // Baris contoh 1: Produk satuan biasa
        sb.appendLine(
            listOf(
                "Kopi Hitam Tubruk",
                "Minuman",
                "3500",
                "6000",
                "25",
                "pcs",
                "KOP-001",
                "899100100001",
                "",
                "",
                "",
                ""
            ).joinToString(",") { escapeCsvCell(it) }
        )

        // Baris contoh 2: Produk dengan varian terpisah di kolom sendiri
        sb.appendLine(
            listOf(
                "Sambal Pedas Botol",
                "Sembako",
                "8000",
                "12000",
                "15",
                "btl",
                "SBL-002",
                "899100200001",
                "Extra Pedas",
                "13500",
                "8",
                "899100200002"
            ).joinToString(",") { escapeCsvCell(it) }
        )
        return sb.toString()
    }

    fun exportProductsToSimpleCsvUri(
        context: Context,
        uri: Uri,
        products: List<ProductEntity>
    ): Pair<Int, String> {
        return try {
            val out = context.contentResolver.openOutputStream(uri)
                ?: return 0 to "Tidak dapat membuka file tujuan ekspor."
            OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                writer.write('\uFEFF'.code)
                writer.write(SIMPLE_HEADERS.joinToString(",") { escapeCsvCell(it) } + "\n")

                products.forEach { p ->
                    val sku = extractProductSku(p.barcode)
                    val bar = extractActualBarcode(p.barcode)
                    val unit = extractProductBaseUnit(p)
                    val variantEntries = if (p.variations.isNotBlank()) {
                        p.variations.replace("|", ",").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    } else {
                        emptyList()
                    }

                    if (variantEntries.isEmpty()) {
                        val row = listOf(
                            p.name,
                            p.category,
                            p.costPrice.toString(),
                            p.price.toString(),
                            p.stock.toString(),
                            unit,
                            sku,
                            bar,
                            "",
                            "",
                            "",
                            ""
                        )
                        writer.write(row.joinToString(",") { escapeCsvCell(it) } + "\n")
                    } else {
                        variantEntries.forEach { vEntry ->
                            val parts = vEntry.split(":")
                            val vName = parts.getOrNull(0)?.trim().orEmpty()
                            val vPrice = parts.getOrNull(1)?.trim().orEmpty().ifBlank { p.price.toString() }
                            val vStock = parts.getOrNull(3)?.trim().orEmpty().ifBlank { p.stock.toString() }
                            val vBar = parts.getOrNull(5)?.trim().orEmpty()
                            val row = listOf(
                                p.name,
                                p.category,
                                parts.getOrNull(2)?.trim().orEmpty().ifBlank { p.costPrice.toString() },
                                p.price.toString(),
                                p.stock.toString(),
                                unit,
                                sku,
                                bar,
                                vName,
                                vPrice,
                                vStock,
                                vBar
                            )
                            writer.write(row.joinToString(",") { escapeCsvCell(it) } + "\n")
                        }
                    }
                }
                writer.flush()
            }
            products.size to "Berhasil mengekspor ${products.size} produk ke template simpel (1 kolom = 1 data)."
        } catch (e: Exception) {
            0 to "Gagal ekspor: ${e.message ?: "kesalahan penulisan file"}"
        }
    }

    fun parseSimpleCsvUri(context: Context, uri: Uri): List<SimpleProductRow> {
        val results = mutableListOf<SimpleProductRow>()
        val input = context.contentResolver.openInputStream(uri) ?: return emptyList()
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
            var lineIndex = 0
            var delimiter = ','
            var rawLine = reader.readLine()
            while (rawLine != null) {
                val cleaned = rawLine.removePrefix("\uFEFF").trim()
                if (cleaned.isNotEmpty()) {
                    if (lineIndex == 0) {
                        // Deteksi otomatis pemisah koma (,) atau titik koma (;) dari Excel Indonesia
                        delimiter = if (cleaned.count { it == ';' } > cleaned.count { it == ',' }) ';' else ','
                        lineIndex++
                        rawLine = reader.readLine()
                        continue
                    }
                    val cols = splitCsvRow(cleaned, delimiter)
                    val pName = cols.getOrNull(0)?.trim().orEmpty()
                    val sellPrice = cols.getOrNull(3)?.trim()?.filter { it.isDigit() }?.toLongOrNull() ?: 0L
                    if (pName.isNotBlank() && sellPrice > 0L) {
                        results.add(
                            SimpleProductRow(
                                name = pName,
                                category = cols.getOrNull(1)?.trim().orEmpty().ifBlank { "Umum" },
                                costPrice = cols.getOrNull(2)?.trim()?.filter { it.isDigit() }?.toLongOrNull() ?: 0L,
                                sellPrice = sellPrice,
                                stock = cols.getOrNull(4)?.trim()?.filter { it.isDigit() }?.toIntOrNull() ?: 0,
                                unit = cols.getOrNull(5)?.trim().orEmpty().ifBlank { "pcs" },
                                sku = cols.getOrNull(6)?.trim().orEmpty().uppercase(),
                                barcode = cols.getOrNull(7)?.trim().orEmpty(),
                                variantName = cols.getOrNull(8)?.trim().orEmpty(),
                                variantPrice = cols.getOrNull(9)?.trim()?.filter { it.isDigit() }?.toLongOrNull() ?: sellPrice,
                                variantStock = cols.getOrNull(10)?.trim()?.filter { it.isDigit() }?.toIntOrNull() ?: 0,
                                variantBarcode = cols.getOrNull(11)?.trim().orEmpty()
                            )
                        )
                    }
                    lineIndex++
                }
                rawLine = reader.readLine()
            }
        }
        return results
    }

    private fun escapeCsvCell(value: String): String {
        val needsQuotes = value.contains(',') || value.contains(';') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun splitCsvRow(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    out.add(cur.toString())
                    cur.setLength(0)
                }
                else -> cur.append(ch)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }
}
