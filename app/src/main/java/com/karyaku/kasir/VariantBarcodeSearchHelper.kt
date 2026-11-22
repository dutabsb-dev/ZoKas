package com.karyaku.kasir

import com.karyaku.kasir.data.local.ProductEntity

object VariantBarcodeSearchHelper {
    fun extractAllVariantBarcodes(variations: String): List<String> {
        if (variations.isBlank()) return emptyList()
        return variations
            .replace("|", ",")
            .split(",")
            .mapNotNull { entry ->
                val parts = entry.split(":")
                parts.getOrNull(5)?.trim()?.takeIf { it.isNotEmpty() }
            }
    }

    fun matchesVariantBarcode(product: ProductEntity, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        return extractAllVariantBarcodes(product.variations).any { code ->
            code.equals(q, ignoreCase = true) || code.contains(q, ignoreCase = true)
        }
    }
}
