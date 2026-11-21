package com.karyaku.kasir

import com.karyaku.kasir.data.local.ProductEntity
import java.text.NumberFormat
import java.util.Locale

object ProductVariantExcelFormatter {

    data class ParsedVariantDetail(
        val name: String,
        val price: Long,
        val costPrice: Long,
        val stock: Int,
        val packMultiplier: Int,
        val barcode: String
    )

    private val rupiahFormatter: NumberFormat by lazy {
        NumberFormat.getNumberInstance(Locale("id", "ID"))
    }

    fun formatRupiahPlain(amount: Long): String {
        return "Rp ${rupiahFormatter.format(amount)}"
    }

    fun parseDetailedVariants(product: ProductEntity): List<ParsedVariantDetail> {
        val raw = product.variations.trim()
        if (raw.isEmpty() || raw.contains("|")) return emptyList()
        val baseUnit = extractProductBaseUnit(product)

        return raw.split(",")
            .mapIndexedNotNull { index, part ->
                val pieces = part.split(":")
                val name = pieces.getOrNull(0)?.trim().orEmpty()
                if (name.isEmpty()) return@mapIndexedNotNull null

                val price = pieces.getOrNull(1)?.trim()?.toLongOrNull() ?: product.price
                val cost = pieces.getOrNull(2)?.trim()?.toLongOrNull() ?: product.costPrice
                val stockRaw = pieces.getOrNull(3)?.trim()
                val stock = if (!stockRaw.isNullOrEmpty()) {
                    stockRaw.toIntOrNull() ?: 0
                } else {
                    if (index == 0) product.stock else 0
                }
                val mult = (pieces.getOrNull(4)?.trim()?.toIntOrNull() ?: 1).coerceAtLeast(1)
                val varBarcode = pieces.getOrNull(5)?.trim().orEmpty()

                ParsedVariantDetail(
                    name = name,
                    price = price,
                    costPrice = cost,
                    stock = stock,
                    packMultiplier = mult,
                    barcode = varBarcode
                )
            }
            .also { list ->
                if (list.isNotEmpty() && baseUnit.isBlank()) {
                    // no-op; baseUnit kept for reference
                }
            }
    }

    /**
     * Format rapi & informatif untuk kolom Excel / ringkasan varian produk.
     * Contoh:
     * "Botol 190ml [Jual: Rp 5.500 | Beli: Rp 4.600 | Stok: 140 pcs | Isi: 1 pcs | Barcode: 899...]; Botol 115ml [Jual: Rp 3.500 | Beli: Rp 2.900 | Stok: 0 pcs | Isi: 1 pcs]"
     */
    fun formatVariantsForExcel(product: ProductEntity): String {
        val variants = parseDetailedVariants(product)
        if (variants.isEmpty()) {
            return if (product.variations.isBlank()) "-" else product.variations
        }
        val baseUnit = extractProductBaseUnit(product).ifBlank { "pcs" }

        return variants.joinToString(separator = " ; ") { v ->
            val barcodePart = if (v.barcode.isNotBlank()) " | Barcode: ${v.barcode}" else ""
            "${v.name} [Jual: ${formatRupiahPlain(v.price)} | Beli: ${formatRupiahPlain(v.costPrice)} | Stok: ${v.stock} $baseUnit | Isi: ${v.packMultiplier} $baseUnit$barcodePart]"
        }
    }

    /**
     * Format multi-baris rapi untuk tampilan detail / catatan laporan.
     */
    fun formatVariantsMultiline(product: ProductEntity): String {
        val variants = parseDetailedVariants(product)
        if (variants.isEmpty()) {
            return if (product.variations.isBlank()) "Tidak ada varian" else product.variations
        }
        val baseUnit = extractProductBaseUnit(product).ifBlank { "pcs" }

        return variants.mapIndexed { idx, v ->
            val barcodeText = if (v.barcode.isNotBlank()) v.barcode else "-"
            "${idx + 1}. ${v.name} — Jual: ${formatRupiahPlain(v.price)}, Beli: ${formatRupiahPlain(v.costPrice)}, Stok: ${v.stock} $baseUnit, Isi/Kemasan: ${v.packMultiplier} $baseUnit, Barcode: $barcodeText"
        }.joinToString(separator = "\n")
    }
}
