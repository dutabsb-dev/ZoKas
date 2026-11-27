package com.karyaku.kasir

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.AuditLogEntity
import com.karyaku.kasir.data.local.ProductBomEntity
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.RawMaterialEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class VariantBomEntry(
    val rawMaterialId: Long,
    val quantityUsed: Double
)

object VariantBomManager {
    private const val PREFS_NAME = "zokas_variant_bom_prefs_v1"

    private fun normalizeVariantKey(variantName: String?): String {
        return variantName?.trim().orEmpty()
    }

    private fun prefKey(productId: Long, variantName: String?): String {
        val vKey = normalizeVariantKey(variantName).lowercase()
        return if (vKey.isEmpty()) "prod_${productId}_base" else "prod_${productId}_var_$vKey"
    }

    fun getVariantNamesForProduct(product: ProductEntity): List<String> {
        val raw = product.variations.trim()
        if (raw.isEmpty() || raw.contains("|")) return emptyList()
        return raw.split(",")
            .mapNotNull { part ->
                val vName = part.split(":").firstOrNull()?.trim().orEmpty()
                vName.takeIf { it.isNotEmpty() }
            }
            .distinct()
    }

    fun saveRecipe(
        context: Context,
        productId: Long,
        variantName: String?,
        entries: List<VariantBomEntry>
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = prefKey(productId, variantName)
        if (entries.isEmpty()) {
            prefs.edit().remove(key).apply()
            return
        }
        val arr = JSONArray()
        entries.forEach { item ->
            if (item.rawMaterialId > 0L && item.quantityUsed > 0.0) {
                arr.put(
                    JSONObject().apply {
                        put("rawMaterialId", item.rawMaterialId)
                        put("quantityUsed", item.quantityUsed)
                    }
                )
            }
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    fun getRecipe(
        context: Context,
        productId: Long,
        variantName: String?
    ): List<VariantBomEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(prefKey(productId, variantName), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<VariantBomEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val rmId = obj.optLong("rawMaterialId", 0L)
                val qty = obj.optDouble("quantityUsed", 0.0)
                if (rmId > 0L && qty > 0.0) {
                    list.add(VariantBomEntry(rawMaterialId = rmId, quantityUsed = qty))
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun hasRecipe(context: Context, productId: Long, variantName: String?): Boolean {
        return getRecipe(context, productId, variantName).isNotEmpty()
    }

    fun clearAllRecipesForProduct(context: Context, product: ProductEntity) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.remove(prefKey(product.id, null))
        getVariantNamesForProduct(product).forEach { vName ->
            editor.remove(prefKey(product.id, vName))
        }
        // Bersihkan juga kunci residual milik productId
        val prefix = "prod_${product.id}_"
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { k ->
            editor.remove(k)
        }
        editor.apply()
    }

    fun calculateRecipeCost(
        entries: List<VariantBomEntry>,
        rawMaterials: List<RawMaterialEntity>
    ): Long {
        if (entries.isEmpty()) return 0L
        val total = entries.sumOf { entry ->
            val mat = rawMaterials.find { it.id == entry.rawMaterialId }
            if (mat != null && entry.quantityUsed > 0.0) {
                mat.costPerUnit.toDouble() * entry.quantityUsed
            } else {
                0.0
            }
        }
        return Math.round(total).coerceAtLeast(0L)
    }

    fun calculateBatchPairCost(boms: List<Pair<RawMaterialEntity, Double>>): Long {
        if (boms.isEmpty()) return 0L
        val total = boms.sumOf { (mat, qty) ->
            if (qty > 0.0) mat.costPerUnit.toDouble() * qty else 0.0
        }
        return Math.round(total).coerceAtLeast(0L)
    }

    /**
     * Memperbarui field costPrice (index 2) pada string variations untuk varian target
     * tanpa mengubah harga jual, stok, multiplier kemasan, maupun barcode varian.
     */
    fun updateVariantCostInVariationsString(
        variationsStr: String,
        targetVariantName: String,
        newCostPrice: Long
    ): String {
        val cleanTarget = targetVariantName.trim()
        if (variationsStr.isBlank() || cleanTarget.isEmpty() || variationsStr.contains("|")) {
            return variationsStr
        }
        val parts = variationsStr.split(",")
        val updatedParts = parts.map { part ->
            val pieces = part.split(":")
            if (pieces.size >= 2 && pieces[0].trim().equals(cleanTarget, ignoreCase = true)) {
                val vName = pieces[0].trim()
                val vPrice = pieces[1].trim()
                val vStock = pieces.getOrNull(3)?.trim().orEmpty()
                val vMult = pieces.getOrNull(4)?.trim().orEmpty()
                val vBarcode = pieces.getOrNull(5)?.trim().orEmpty()
                buildString {
                    append("$vName:$vPrice:$newCostPrice")
                    if (vStock.isNotEmpty() || vMult.isNotEmpty() || vBarcode.isNotEmpty()) {
                        append(":${vStock.ifEmpty { "0" }}")
                    }
                    if (vMult.isNotEmpty() || vBarcode.isNotEmpty()) {
                        append(":${vMult.ifEmpty { "1" }}")
                    }
                    if (vBarcode.isNotEmpty()) {
                        append(":$vBarcode")
                    }
                }
            } else {
                part
            }
        }
        return updatedParts.joinToString(",")
    }

    /**
     * Sinkronisasi otomatis harga beli (costPrice) pada ProductEntity & seluruh variannya
     * berdasarkan resep BOM yang aktif (baik untuk produk utama maupun per varian).
     */
    fun syncProductAndVariantCostsFromRecipes(
        context: Context,
        product: ProductEntity,
        rawMaterials: List<RawMaterialEntity>,
        legacyProductBoms: List<ProductBomEntity> = emptyList()
    ): ProductEntity {
        val variantNames = getVariantNamesForProduct(product)
        if (variantNames.isEmpty()) {
            val baseRecipe = getRecipe(context, product.id, null).ifEmpty {
                legacyProductBoms.map { VariantBomEntry(it.rawMaterialId, it.quantityUsed) }
            }
            if (baseRecipe.isEmpty()) return product
            val newCost = calculateRecipeCost(baseRecipe, rawMaterials)
            return if (newCost > 0L) product.copy(costPrice = newCost) else product
        }

        var updatedVariations = product.variations
        var firstVariantRecipeCost: Long? = null

        variantNames.forEach { vName ->
            val vRecipe = getRecipe(context, product.id, vName)
            if (vRecipe.isNotEmpty()) {
                val vCost = calculateRecipeCost(vRecipe, rawMaterials)
                if (vCost > 0L) {
                    updatedVariations = updateVariantCostInVariationsString(updatedVariations, vName, vCost)
                    if (firstVariantRecipeCost == null) {
                        firstVariantRecipeCost = vCost
                    }
                }
            }
        }

        // Jika ada resep tingkat produk utama (fallback umum), terapkan ke varian yang belum punya resep khusus
        val baseRecipe = getRecipe(context, product.id, null).ifEmpty {
            legacyProductBoms.map { VariantBomEntry(it.rawMaterialId, it.quantityUsed) }
        }
        val baseCost = if (baseRecipe.isNotEmpty()) calculateRecipeCost(baseRecipe, rawMaterials) else 0L
        if (baseCost > 0L) {
            variantNames.forEach { vName ->
                if (!hasRecipe(context, product.id, vName)) {
                    updatedVariations = updateVariantCostInVariationsString(updatedVariations, vName, baseCost)
                }
            }
        }

        val resolvedMainCost = when {
            firstVariantRecipeCost != null && firstVariantRecipeCost!! > 0L -> firstVariantRecipeCost!!
            baseCost > 0L -> baseCost
            else -> product.costPrice
        }

        return product.copy(
            costPrice = resolvedMainCost,
            variations = updatedVariations
        )
    }

    /**
     * Mengambil komposisi resep aktif yang akan dipotong saat transaksi untuk item + varian tertentu.
     */
    fun resolveBomEntriesForSale(
        context: Context,
        productId: Long,
        selectedVariation: String,
        legacyProductBoms: List<ProductBomEntity>
    ): List<VariantBomEntry> {
        val cleanVar = selectedVariation.trim()
        if (cleanVar.isNotEmpty()) {
            val variantEntries = getRecipe(context, productId, cleanVar)
            if (variantEntries.isNotEmpty()) {
                return variantEntries
            }
        }
        val baseEntries = getRecipe(context, productId, null)
        if (baseEntries.isNotEmpty()) {
            return baseEntries
        }
        return legacyProductBoms.map {
            VariantBomEntry(rawMaterialId = it.rawMaterialId, quantityUsed = it.quantityUsed)
        }
    }
}

private fun PosViewModel.resolveAppContextForBom(): Context? {
    return runCatching {
        val field = PosViewModel::class.java.declaredFields.firstOrNull {
            Context::class.java.isAssignableFrom(it.type)
        }?.apply { isAccessible = true }
        field?.get(this) as? Context
    }.getOrNull() ?: runCatching {
        val dbField = PosViewModel::class.java.declaredFields.firstOrNull {
            it.name == "database"
        }?.apply { isAccessible = true }
        val dbObj = dbField?.get(this)
        val openHelper = dbObj?.javaClass?.getMethod("getOpenHelper")?.invoke(dbObj)
        val writableDb = openHelper?.javaClass?.getMethod("getWritableDatabase")?.invoke(openHelper)
        val path = writableDb?.javaClass?.getMethod("getPath")?.invoke(writableDb) as? String
        if (!path.isNullOrBlank()) {
            val appClass = Class.forName("android.app.ActivityThread")
            val currentApp = appClass.getMethod("currentApplication").invoke(null) as? Context
            currentApp
        } else null
    }.getOrNull()
}

private suspend fun PosViewModel.rebuildRoomProductBomsUnion(
    context: Context,
    product: ProductEntity
) {
    val aggregated = linkedMapOf<Long, Double>()
    val baseEntries = VariantBomManager.getRecipe(context, product.id, null)
    baseEntries.forEach { entry ->
        aggregated[entry.rawMaterialId] = (aggregated[entry.rawMaterialId] ?: 0.0) + entry.quantityUsed
    }
    val variantNames = VariantBomManager.getVariantNamesForProduct(product)
    variantNames.forEach { vName ->
        VariantBomManager.getRecipe(context, product.id, vName).forEach { entry ->
            if (!aggregated.containsKey(entry.rawMaterialId)) {
                aggregated[entry.rawMaterialId] = entry.quantityUsed
            }
        }
    }
    productDao.deleteBomForProduct(product.id)
    aggregated.forEach { (rmId, qty) ->
        productDao.insertProductBom(
            ProductBomEntity(
                productId = product.id,
                rawMaterialId = rmId,
                quantityUsed = qty
            )
        )
    }
}

fun PosViewModel.saveBomBatch(
    productId: Long,
    boms: List<Pair<RawMaterialEntity, Double>>,
    onSuccess: () -> Unit
) {
    saveBomBatchForTarget(
        productId = productId,
        targetVariantName = null,
        boms = boms,
        onSuccess = { _ -> onSuccess() }
    )
}

fun PosViewModel.saveBomBatchForTarget(
    productId: Long,
    targetVariantName: String?,
    boms: List<Pair<RawMaterialEntity, Double>>,
    onSuccess: (Long) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val appCtx = resolveAppContextForBom()
            val freshProduct = productDao.getProductById(productId)
                ?: _products.value.find { it.id == productId }
            val entries = boms.map { (mat, qty) ->
                VariantBomEntry(rawMaterialId = mat.id, quantityUsed = qty)
            }
            val calculatedCost = VariantBomManager.calculateBatchPairCost(boms)

            if (appCtx != null) {
                VariantBomManager.saveRecipe(appCtx, productId, targetVariantName, entries)
                if (freshProduct != null) {
                    rebuildRoomProductBomsUnion(appCtx, freshProduct)
                    val syncedProduct = VariantBomManager.syncProductAndVariantCostsFromRecipes(
                        context = appCtx,
                        product = freshProduct,
                        rawMaterials = _rawMaterials.value
                    )
                    productDao.saveProduct(
                        syncedProduct.copy(
                            isSynced = false,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            } else {
                productDao.deleteBomForProduct(productId)
                boms.forEach { (material, qty) ->
                    productDao.insertProductBom(
                        ProductBomEntity(
                            productId = productId,
                            rawMaterialId = material.id,
                            quantityUsed = qty
                        )
                    )
                }
                if (freshProduct != null && calculatedCost > 0L) {
                    val updatedVar = if (!targetVariantName.isNullOrBlank()) {
                        VariantBomManager.updateVariantCostInVariationsString(
                            freshProduct.variations,
                            targetVariantName,
                            calculatedCost
                        )
                    } else {
                        freshProduct.variations
                    }
                    val updatedMainCost = if (targetVariantName.isNullOrBlank()) calculatedCost else freshProduct.costPrice
                    productDao.saveProduct(
                        freshProduct.copy(
                            costPrice = updatedMainCost,
                            variations = updatedVar,
                            isSynced = false,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            val targetLabel = if (targetVariantName.isNullOrBlank()) "Produk Utama" else "Varian: $targetVariantName"
            userDao.insertAuditLog(
                AuditLogEntity(
                    userId = 0L,
                    userName = "Sistem (Auto-HPP BOM)",
                    action = "SIMPAN RESEP BOM (${freshProduct?.name ?: "#$productId"} - $targetLabel) -> HPP ${formatRupiah(calculatedCost)}",
                    actionType = "INVENTORY"
                )
            )

            withContext(Dispatchers.Main) { onSuccess(calculatedCost) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun PosViewModel.deleteBomProduct(productId: Long, onSuccess: () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val appCtx = resolveAppContextForBom()
            val freshProduct = productDao.getProductById(productId)
                ?: _products.value.find { it.id == productId }
            if (appCtx != null && freshProduct != null) {
                VariantBomManager.clearAllRecipesForProduct(appCtx, freshProduct)
            }
            productDao.deleteBomForProduct(productId)
            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun PosViewModel.deleteBomForTarget(
    productId: Long,
    targetVariantName: String?,
    onSuccess: () -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val appCtx = resolveAppContextForBom()
            val freshProduct = productDao.getProductById(productId)
                ?: _products.value.find { it.id == productId }
            if (appCtx != null) {
                VariantBomManager.saveRecipe(appCtx, productId, targetVariantName, emptyList())
                if (freshProduct != null) {
                    rebuildRoomProductBomsUnion(appCtx, freshProduct)
                } else {
                    productDao.deleteBomForProduct(productId)
                }
            } else {
                productDao.deleteBomForProduct(productId)
            }
            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

suspend fun PosViewModel.getBomForProductOnce(productId: Long): List<ProductBomEntity> {
    return try {
        productDao.getBomForProduct(productId)
    } catch (e: Exception) {
        emptyList()
    }
}

suspend fun PosViewModel.getBomForTargetOnce(
    productId: Long,
    targetVariantName: String?
): List<ProductBomEntity> {
    return try {
        val appCtx = resolveAppContextForBom()
        if (appCtx != null) {
            val stored = VariantBomManager.getRecipe(appCtx, productId, targetVariantName)
            if (stored.isNotEmpty()) {
                return stored.mapIndexed { idx, e ->
                    ProductBomEntity(
                        id = (idx + 1).toLong(),
                        productId = productId,
                        rawMaterialId = e.rawMaterialId,
                        quantityUsed = e.quantityUsed
                    )
                }
            }
            // Fallback ke legacy Room BOM hanya untuk target utama jika belum pernah dimigrasi
            if (targetVariantName.isNullOrBlank()) {
                val product = productDao.getProductById(productId)
                val hasAnyVariantRecipe = product != null &&
                    VariantBomManager.getVariantNamesForProduct(product).any { vName ->
                        VariantBomManager.hasRecipe(appCtx, productId, vName)
                    }
                if (!hasAnyVariantRecipe) {
                    return productDao.getBomForProduct(productId)
                }
            }
            emptyList()
        } else {
            productDao.getBomForProduct(productId)
        }
    } catch (e: Exception) {
        emptyList()
    }
}
