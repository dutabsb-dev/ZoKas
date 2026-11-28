package com.karyaku.kasir

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


object DecimalStockTracker {
    private const val PREFS_DEC = "zokas_decimal_stock_prefs"
    private val stockById = java.util.concurrent.ConcurrentHashMap<Long, Double>()
    private val stockByName = java.util.concurrent.ConcurrentHashMap<String, Double>()
    private val variantStockByKey = java.util.concurrent.ConcurrentHashMap<String, Double>()

    fun persistStock(context: Context, productId: Long, productName: String, value: Double) {
        val cleanVal = value.coerceAtLeast(0.0)
        setStock(productId, productName, cleanVal)
        val formatted = String.format(java.util.Locale.US, "%.4f", cleanVal)
        val ed = context.getSharedPreferences(PREFS_DEC, Context.MODE_PRIVATE).edit()
        if (productId > 0L) ed.putString("id_$productId", formatted)
        if (productName.isNotBlank()) ed.putString("name_${productName.trim().lowercase()}", formatted)
        ed.apply()
    }

    fun loadPersistedStock(context: Context, productId: Long, productName: String, fallbackInt: Int): Double {
        if (productId > 0L && stockById.containsKey(productId)) {
            return stockById[productId] ?: fallbackInt.toDouble()
        }
        val prefs = context.getSharedPreferences(PREFS_DEC, Context.MODE_PRIVATE)
        if (productId > 0L) {
            prefs.getString("id_$productId", null)?.toDoubleOrNull()?.let { loaded ->
                stockById[productId] = loaded
                return loaded
            }
        }
        val key = productName.trim().lowercase()
        if (key.isNotBlank()) {
            stockByName[key]?.let { return it }
            prefs.getString("name_$key", null)?.toDoubleOrNull()?.let { loaded ->
                stockByName[key] = loaded
                if (productId > 0L) stockById[productId] = loaded
                return loaded
            }
        }
        return fallbackInt.toDouble()
    }

    fun setStock(productId: Long, productName: String, value: Double) {
        val cleanVal = value.coerceAtLeast(0.0)
        if (productId > 0L) stockById[productId] = cleanVal
        if (productName.isNotBlank()) stockByName[productName.trim().lowercase()] = cleanVal
    }

    fun getStock(productId: Long, productName: String, fallbackInt: Int): Double {
        if (productId > 0L && stockById.containsKey(productId)) {
            return stockById[productId] ?: fallbackInt.toDouble()
        }
        val key = productName.trim().lowercase()
        if (key.isNotBlank() && stockByName.containsKey(key)) {
            return stockByName[key] ?: fallbackInt.toDouble()
        }
        return fallbackInt.toDouble()
    }

    fun deduct(productId: Long, productName: String, currentIntStock: Int, delta: Double): Double {
        val base = getStock(productId, productName, currentIntStock)
        val next = (base - delta).coerceAtLeast(0.0)
        setStock(productId, productName, next)
        return next
    }

    fun setVariantStock(productId: Long, productName: String, variantName: String, value: Double) {
        val cleanVal = value.coerceAtLeast(0.0)
        val vKey = variantName.trim().lowercase()
        if (vKey.isBlank()) return
        if (productId > 0L) variantStockByKey["id_${productId}_$vKey"] = cleanVal
        if (productName.isNotBlank()) variantStockByKey["name_${productName.trim().lowercase()}_$vKey"] = cleanVal
    }

    fun getVariantStock(productId: Long, productName: String, variantName: String, fallbackInt: Int): Double {
        val vKey = variantName.trim().lowercase()
        if (vKey.isBlank()) return fallbackInt.toDouble()
        if (productId > 0L && variantStockByKey.containsKey("id_${productId}_$vKey")) {
            return variantStockByKey["id_${productId}_$vKey"] ?: fallbackInt.toDouble()
        }
        val nameKey = "name_${productName.trim().lowercase()}_$vKey"
        if (productName.isNotBlank() && variantStockByKey.containsKey(nameKey)) {
            return variantStockByKey[nameKey] ?: fallbackInt.toDouble()
        }
        return fallbackInt.toDouble()
    }

    fun deductVariant(productId: Long, productName: String, variantName: String, currentIntStock: Int, delta: Double): Double {
        val base = getVariantStock(productId, productName, variantName, currentIntStock)
        val next = (base - delta).coerceAtLeast(0.0)
        setVariantStock(productId, productName, variantName, next)
        return next
    }
}

data class ProductYieldProfile(
    val productId: Long,
    val rawKg: Double,
    val rawCostPerKg: Long,
    val extraProcessCost: Long,
    val carcassKg: Double,
    val byproductKg: Double,
    val wasteKg: Double,
    val freezerShrinkPct: Double,
    val carcassHppPerKg: Long
)

data class YieldBatchRecord(
    val id: String,
    val productId: Long,
    val timestamp: Long,
    val rawName: String,
    val rawKg: Double,
    val rawCostPerKg: Long,
    val extraProcessCost: Long,
    val carcassKg: Double,
    val byproductKg: Double,
    val wasteKg: Double,
    val carcassHppPerKg: Long
)

object YieldAndLossEngine {
    private const val PREFS_NAME = "zokas_yield_loss_prefs"
    private const val KEY_BATCHES = "yield_batches_json"
    private const val KEY_FREEZER_LOSS = "freezer_shrink_loss_rp"
    private const val KEY_BELOW_HPP_LOSS = "below_hpp_loss_rp"
    private const val KEY_WASTE_LOSS = "waste_loss_rp"

    private val productHppById = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    @Volatile private var freezerLossMem: Long = 0L
    @Volatile private var belowHppLossMem: Long = 0L
    @Volatile private var wasteLossMem: Long = 0L

    fun getProductCarcassHpp(productId: Long, fallback: Long = 0L): Long {
        val h = productHppById[productId] ?: 0L
        return if (h > 0L) h else fallback
    }

    fun getCarcassHppPerKg(fallback: Long = 0L): Long {
        return fallback
    }

    fun saveProductProfile(context: Context, profile: ProductYieldProfile) {
        if (profile.productId <= 0L) return
        if (profile.carcassHppPerKg > 0L) {
            productHppById[profile.productId] = profile.carcassHppPerKg
        }
        val obj = JSONObject().apply {
            put("productId", profile.productId)
            put("rawKg", profile.rawKg)
            put("rawCostPerKg", profile.rawCostPerKg)
            put("extraProcessCost", profile.extraProcessCost)
            put("carcassKg", profile.carcassKg)
            put("byproductKg", profile.byproductKg)
            put("wasteKg", profile.wasteKg)
            put("freezerShrinkPct", profile.freezerShrinkPct)
            put("carcassHppPerKg", profile.carcassHppPerKg)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("profile_prod_${profile.productId}", obj.toString())
            .apply()
    }

    fun loadProductProfile(context: Context, productId: Long, defaultCostPrice: Long): ProductYieldProfile {
        if (productId > 0L) {
            val rawJson = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString("profile_prod_$productId", null)
            if (!rawJson.isNullOrBlank()) {
                try {
                    val o = JSONObject(rawJson)
                    val hpp = o.optLong("carcassHppPerKg", defaultCostPrice)
                    if (hpp > 0L) productHppById[productId] = hpp
                    return ProductYieldProfile(
                        productId = productId,
                        rawKg = o.optDouble("rawKg", 0.0),
                        rawCostPerKg = o.optLong("rawCostPerKg", defaultCostPrice),
                        extraProcessCost = o.optLong("extraProcessCost", 0L),
                        carcassKg = o.optDouble("carcassKg", 0.0),
                        byproductKg = o.optDouble("byproductKg", 0.0),
                        wasteKg = o.optDouble("wasteKg", 0.0),
                        freezerShrinkPct = o.optDouble("freezerShrinkPct", 0.0),
                        carcassHppPerKg = hpp
                    )
                } catch (_: Exception) { }
            }
        }
        return ProductYieldProfile(
            productId = productId,
            rawKg = 0.0,
            rawCostPerKg = defaultCostPrice,
            extraProcessCost = 0L,
            carcassKg = 0.0,
            byproductKg = 0.0,
            wasteKg = 0.0,
            freezerShrinkPct = 0.0,
            carcassHppPerKg = defaultCostPrice
        )
    }

    fun computeWeightedAverageHpp(
        oldStock: Double,
        oldHpp: Long,
        addedStock: Double,
        addedUnitCost: Long
    ): Long {
        val safeOldStock = oldStock.coerceAtLeast(0.0)
        val safeAddedStock = addedStock.coerceAtLeast(0.0)
        val totalStock = safeOldStock + safeAddedStock
        if (totalStock <= 1e-6) return addedUnitCost.coerceAtLeast(oldHpp).coerceAtLeast(0L)
        if (safeOldStock <= 1e-6) return addedUnitCost.coerceAtLeast(0L)
        if (safeAddedStock <= 1e-6) return oldHpp.coerceAtLeast(0L)
        val oldVal = safeOldStock * oldHpp.coerceAtLeast(0L).toDouble()
        val addedVal = safeAddedStock * addedUnitCost.coerceAtLeast(0L).toDouble()
        return Math.round((oldVal + addedVal) / totalStock)
    }

    fun calculateBatch(
        productId: Long,
        rawName: String,
        rawKg: Double,
        rawCostPerKg: Long,
        extraProcessCost: Long,
        carcassKg: Double,
        byproductKg: Double,
        wasteKg: Double
    ): YieldBatchRecord {
        val totalRawCost = Math.round(rawKg.coerceAtLeast(0.0) * rawCostPerKg.coerceAtLeast(0L))
        val totalModal = totalRawCost + extraProcessCost.coerceAtLeast(0L)
        val hppPerKg = if (carcassKg > 1e-6) {
            Math.round(totalModal.toDouble() / carcassKg)
        } else {
            0L
        }
        return YieldBatchRecord(
            id = "YLD_${productId}_${System.currentTimeMillis()}",
            productId = productId,
            timestamp = System.currentTimeMillis(),
            rawName = rawName,
            rawKg = rawKg.coerceAtLeast(0.0),
            rawCostPerKg = rawCostPerKg.coerceAtLeast(0L),
            extraProcessCost = extraProcessCost.coerceAtLeast(0L),
            carcassKg = carcassKg.coerceAtLeast(0.0),
            byproductKg = byproductKg.coerceAtLeast(0.0),
            wasteKg = wasteKg.coerceAtLeast(0.0),
            carcassHppPerKg = hppPerKg
        )
    }

    fun saveBatchRecord(context: Context, record: YieldBatchRecord) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY_BATCHES, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        val obj = JSONObject().apply {
            put("id", record.id)
            put("productId", record.productId)
            put("timestamp", record.timestamp)
            put("rawName", record.rawName)
            put("rawKg", record.rawKg)
            put("rawCostPerKg", record.rawCostPerKg)
            put("extraProcessCost", record.extraProcessCost)
            put("carcassKg", record.carcassKg)
            put("byproductKg", record.byproductKg)
            put("wasteKg", record.wasteKg)
            put("carcassHppPerKg", record.carcassHppPerKg)
        }
        val nextArr = JSONArray().apply {
            put(obj)
            for (i in 0 until minOf(arr.length(), 49)) {
                put(arr.get(i))
            }
        }
        if (record.productId > 0L && record.carcassHppPerKg > 0L) {
            productHppById[record.productId] = record.carcassHppPerKg
        }
        prefs.edit()
            .putString(KEY_BATCHES, nextArr.toString())
            .apply()
    }

    fun getBatchesForProduct(context: Context, productId: Long): List<YieldBatchRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY_BATCHES, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        val out = mutableListOf<YieldBatchRecord>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val pid = o.optLong("productId", 0L)
            if (productId <= 0L || pid == productId) {
                out.add(
                    YieldBatchRecord(
                        id = o.optString("id", ""),
                        productId = pid,
                        timestamp = o.optLong("timestamp", 0L),
                        rawName = o.optString("rawName", ""),
                        rawKg = o.optDouble("rawKg", 0.0),
                        rawCostPerKg = o.optLong("rawCostPerKg", 0L),
                        extraProcessCost = o.optLong("extraProcessCost", 0L),
                        carcassKg = o.optDouble("carcassKg", 0.0),
                        byproductKg = o.optDouble("byproductKg", 0.0),
                        wasteKg = o.optDouble("wasteKg", 0.0),
                        carcassHppPerKg = o.optLong("carcassHppPerKg", 0L)
                    )
                )
            }
        }
        return out
    }

    fun addFreezerShrinkageLoss(lossRp: Long) {
        if (lossRp > 0L) {
            freezerLossMem += lossRp
        }
    }

    fun recordBelowHppSaleLoss(lossRp: Long) {
        if (lossRp > 0L) {
            belowHppLossMem += lossRp
        }
    }

    fun recordWasteLoss(lossRp: Long) {
        if (lossRp > 0L) {
            wasteLossMem += lossRp
        }
    }

    fun persistAndReadTotals(context: Context): Triple<Long, Long, Long> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedFreezer = prefs.getLong(KEY_FREEZER_LOSS, 0L) + freezerLossMem
        val savedBelowHpp = prefs.getLong(KEY_BELOW_HPP_LOSS, 0L) + belowHppLossMem
        val savedWaste = prefs.getLong(KEY_WASTE_LOSS, 0L) + wasteLossMem

        freezerLossMem = 0L
        belowHppLossMem = 0L
        wasteLossMem = 0L

        prefs.edit()
            .putLong(KEY_FREEZER_LOSS, savedFreezer)
            .putLong(KEY_BELOW_HPP_LOSS, savedBelowHpp)
            .putLong(KEY_WASTE_LOSS, savedWaste)
            .apply()

        return Triple(savedFreezer, savedBelowHpp, savedWaste)
    }
}


data class DpPaymentEntry(
    val timestamp: Long,
    val amount: Long,
    val method: String,
    val note: String,
    val qtySummary: String
)

object DpLedgerTracker {
    private const val PREFS_NAME = "zokas_dp_bon_ledger_v1"

    fun appendEntry(context: Context, txId: Long, entry: DpPaymentEntry) {
        if (txId <= 0L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString("tx_$txId", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        arr.put(JSONObject().apply {
            put("timestamp", entry.timestamp)
            put("amount", entry.amount)
            put("method", entry.method)
            put("note", entry.note)
            put("qtySummary", entry.qtySummary)
        })
        prefs.edit().putString("tx_$txId", arr.toString()).apply()
    }

    fun getEntries(context: Context, txId: Long): List<DpPaymentEntry> {
        if (txId <= 0L) return emptyList()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString("tx_$txId", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        val list = mutableListOf<DpPaymentEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(
                DpPaymentEntry(
                    timestamp = o.optLong("timestamp", 0L),
                    amount = o.optLong("amount", 0L),
                    method = o.optString("method", "DP"),
                    note = o.optString("note", ""),
                    qtySummary = o.optString("qtySummary", "")
                )
            )
        }
        return list
    }

    fun getTotalDpPaid(context: Context, txId: Long): Long {
        return getEntries(context, txId).sumOf { it.amount }
    }
}

fun PosViewModel.processPayment(
    cashReceived: Long, userId: Long, userName: String, isSplitBill: Boolean, method: String, 
    maxDiscountCap: Long, birthdayDiscountPercent: Double, taxPercent: Double, 
    serviceChargePercent: Double, manualDiscount: Long, voucherDiscount: Long, voucherCode: String,
    onSuccess: (TransactionEntity) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val cartToProcess = if (isSplitBill) _cart.value.filter { it.isSelectedForSplit } else _cart.value
            if (cartToProcess.isEmpty()) return@launch

            // 1. Hitung Subtotal
            val subtotal = cartToProcess.sumOf { 
                val q = it.effectiveQty
                if (it.usedMembershipId != null) 0L
                else if (it.product.wholesaleMinQty > 0 && q >= it.product.wholesaleMinQty && it.product.wholesalePrice > 0) Math.round(it.product.wholesalePrice * q)
                else Math.round(getPriceForVariant(it.product, it.selectedVariation) * q)
            }
            
            // 2. Hitung Diskon, Pajak, Service
            var totalDiscount = manualDiscount + voucherDiscount
            if (maxDiscountCap > 0 && totalDiscount > maxDiscountCap) totalDiscount = maxDiscountCap
            
            val taxableAmount = (subtotal - totalDiscount).coerceAtLeast(0)
            val taxAmount = (taxableAmount * (taxPercent / 100.0)).toLong()
            val serviceChargeAmount = (taxableAmount * (serviceChargePercent / 100.0)).toLong()
            val finalTotalPrice = (subtotal + taxAmount + serviceChargeAmount - totalDiscount).coerceAtLeast(0)
            
            val totalCommission = cartToProcess.sumOf { it.product.commissionPerItem * it.quantity }

            val computedTotalCost = cartToProcess.sumOf { ci ->
                val qAct = ci.effectiveQty
                val unitHpp = if (ci.product.costPrice > 0L) ci.product.costPrice else YieldAndLossEngine.getProductCarcassHpp(ci.product.id, 0L)
                Math.round(unitHpp.toDouble() * qAct)
            }

            // 3. Simpan Transaksi Utama (Dukung SUCCESS, BON, & DP)
            val isBonOrDp = method.equals("BON", ignoreCase = true) || method.startsWith("DP", ignoreCase = true)
            val txStatus = if (isBonOrDp) "UNPAID" else "SUCCESS"
            val effectiveCashReceived = if (isBonOrDp) cashReceived.coerceIn(0L, finalTotalPrice) else cashReceived
            val effectiveChange = if (!isBonOrDp && cashReceived > finalTotalPrice) cashReceived - finalTotalPrice else 0L
            val createdTs = System.currentTimeMillis()

            val newTransaction = TransactionEntity(
                timestamp = createdTs, totalPrice = finalTotalPrice,
                totalCost = computedTotalCost,
                paymentMethod = method, status = txStatus, cashierName = userName, 
                customerName = _selectedCustomer.value?.name ?: "Pelanggan Umum",
                orderType = _selectedOrderType.value, tableName = _selectedTable.value, 
                branchId = _currentBranchId.value, 
                cashReceived = effectiveCashReceived, changeAmount = effectiveChange,
                taxAmount = taxAmount, serviceCharge = serviceChargeAmount, voucherCode = voucherCode,
                totalCommission = totalCommission
            )

            val transactionId = transactionDao.insertTransaction(newTransaction)
            val savedTransaction = newTransaction.copy(id = transactionId)

            if (isBonOrDp) {
                val qtySummaryText = cartToProcess.joinToString(", ") { ci ->
                    "${formatQtyDisplay(ci.effectiveQty)}x ${ci.product.name}"
                }
                val appCtx = database.openHelper.readableDatabase.let { null } ?: runCatching {
                    val field = PosViewModel::class.java.declaredFields.firstOrNull {
                        android.content.Context::class.java.isAssignableFrom(it.type)
                    }?.apply { isAccessible = true }
                    field?.get(this) as? android.content.Context
                }.getOrNull()
                if (appCtx != null) {
                    DpLedgerTracker.appendEntry(
                        appCtx,
                        transactionId,
                        DpPaymentEntry(
                            timestamp = createdTs,
                            amount = effectiveCashReceived,
                            method = method,
                            note = if (effectiveCashReceived > 0L) "DP/Uang Muka Awal" else "Kas Bon Tercatat",
                            qtySummary = qtySummaryText
                        )
                    )
                }
                if (effectiveCashReceived > 0L) {
                    cashFlowDao.insertCashFlow(
                        CashFlowEntity(
                            shiftId = _activeShift.value?.id ?: 0L,
                            branchId = _currentBranchId.value,
                            type = "IN",
                            amount = effectiveCashReceived,
                            note = "DP Struk #$transactionId (${savedTransaction.customerName}) [$qtySummaryText]",
                            userId = userId,
                            userName = userName
                        )
                    )
                }
            }
            val itemsToInsert = mutableListOf<TransactionItemEntity>()

            // 4. Proses Item & Paket Tambahan
            for (cartItem in cartToProcess) {
                val itemPrice = if (cartItem.usedMembershipId != null) 0L else getPriceForVariant(cartItem.product, cartItem.selectedVariation)
                val unitHppRef = if (cartItem.product.costPrice > 0L) cartItem.product.costPrice else YieldAndLossEngine.getProductCarcassHpp(cartItem.product.id, 0L)
                
                val qDouble = cartItem.effectiveQty
                val qtyLabelSuffix = if (Math.abs(qDouble - Math.rint(qDouble)) > 1e-6) {
                    " [Qty: ${formatQtyDisplay(qDouble)}]"
                } else ""
                val mergedNotes = (cartItem.notes + qtyLabelSuffix).trim()
                val itemSubLong = Math.round(itemPrice * qDouble)
                if (cartItem.usedMembershipId == null && unitHppRef > 0L && itemPrice < unitHppRef) {
                    val lossBelowHpp = Math.round((unitHppRef - itemPrice).toDouble() * qDouble)
                    YieldAndLossEngine.recordBelowHppSaleLoss(lossBelowHpp)
                }

                // Simpan Detail Transaksi
                itemsToInsert.add(TransactionItemEntity(
                    transactionId = transactionId, productId = cartItem.product.id, productName = cartItem.product.name,
                    quantity = cartItem.quantity, price = itemPrice, costPrice = cartItem.product.costPrice, 
                    notes = mergedNotes, serialNumber = cartItem.serialNumber, assignedStaff = cartItem.assignedStaff, 
                    subtotal = itemSubLong, isService = cartItem.product.isService
                ))

                val isReceiptOnlyActive = runCatching {
                    val ctxField = PosViewModel::class.java.declaredFields.firstOrNull {
                        android.content.Context::class.java.isAssignableFrom(it.type)
                    }?.apply { isAccessible = true }
                    val ctx = ctxField?.get(this) as? android.content.Context
                    if (ctx != null) {
                        val cfg = FeatureVisibilityManager.getConfig(ctx)
                        val rawJson = ctx.getSharedPreferences("zokas_feature_visibility_v1", android.content.Context.MODE_PRIVATE)
                            .getString("feature_visibility_json", "") ?: ""
                        cfg.receiptOnlyMode || rawJson.contains("\"receiptOnlyMode\":true")
                    } else {
                        false
                    }
                }.getOrDefault(false)

                // A. Potong Stok Produk secara presisi desimal (dilewati pada Mode Hanya Nota agar stok tidak berkurang)
                val freshProduct = if (!isReceiptOnlyActive) productDao.getProductById(cartItem.product.id) else null
                if (freshProduct != null && !freshProduct.isService) {
                    val packMult = getVariantPackMultiplier(freshProduct.variations, cartItem.selectedVariation)
                    val exactDecimalDeduct = (qDouble * packMult.toDouble()).coerceAtLeast(0.0)
                    val intDeductForVariant = if (qDouble > 0.0) Math.ceil(exactDecimalDeduct - 1e-6).toInt().coerceAtLeast(1) else 0

                    val nextDecStock = DecimalStockTracker.deduct(
                        productId = freshProduct.id,
                        productName = freshProduct.name,
                        currentIntStock = freshProduct.stock,
                        delta = exactDecimalDeduct
                    )
                    val newIntStock = Math.floor(nextDecStock + 1e-6).toInt().coerceAtLeast(0)
                    if (cartItem.selectedVariation.isNotBlank()) {
                        val newVariationStr = deductVariantStock(freshProduct.variations, cartItem.selectedVariation, intDeductForVariant)
                        productDao.updateProduct(freshProduct.copy(variations = newVariationStr, stock = newIntStock))
                    } else {
                        productDao.updateProduct(freshProduct.copy(stock = newIntStock))
                    }
                }

                // B. PAKET 2: Potong Kuota Membership
                if (cartItem.usedMembershipId != null) {
                    val cm = _customerMemberships.value.find { it.id == cartItem.usedMembershipId }
                    if (cm != null && cm.remainingQuota > 0) {
                        membershipDao.updateRemainingQuota(cm.id, cm.remainingQuota - 1)
                    }
                }

                // C. PAKET 1: Potong Stok Bahan Baku (BOM) memakai kuantitas aktual (dilewati pada Mode Hanya Nota)
                val bomList = if (!isReceiptOnlyActive) productDao.getBomForProduct(cartItem.product.id) else emptyList()
                if (bomList.isNotEmpty()) {
                    bomList.forEach { bom ->
                        val material = _rawMaterials.value.find { it.id == bom.rawMaterialId }
                        if (material != null) {
                            val qtyUsed = bom.quantityUsed * qDouble
                            productDao.updateRawMaterialStock(material.id, material.stock - qtyUsed)
                        }
                    }
                }
            }
            
            transactionItemDao.insertTransactionItems(itemsToInsert)

            // 5. PAKET 2: Update Loyalty Points & Profil Pelanggan
            val customer = _selectedCustomer.value
            if (customer != null) {
                val pointsEarned = (finalTotalPrice / 10000).toInt() // 1 Poin per Rp 10.000
                val finalPoints = (customer.points - _pointsUsed.value + pointsEarned).coerceAtLeast(0)
                
                customerDao.updateCustomerStats(
                    customerId = customer.id,
                    visitTime = System.currentTimeMillis(),
                    spent = finalTotalPrice,
                    newPoints = finalPoints
                )
            }

            // 6. PAKET 1: Buat Tiket Dapur (KDS)
            // Hanya buat tiket jika ada produk fisik (bukan jasa)
            val kitchenItems = cartToProcess.filter { !it.product.isService }
            if (kitchenItems.isNotEmpty()) {
                val itemsJson = JSONArray().apply {
                    kitchenItems.forEach { item ->
                        val displayItemName = if (item.selectedVariation.isNotBlank()) {
                            "${item.product.name} (${item.selectedVariation})"
                        } else {
                            item.product.name
                        }
                        put(JSONObject().apply {
                            put("name", displayItemName)
                            put("qty", item.quantity)
                            put("notes", item.notes)
                        })
                    }
                }.toString()
                
                kitchenTicketDao.insertTicket(KitchenTicketEntity(
                    transactionId = transactionId,
                    branchId = _currentBranchId.value,
                    tableName = newTransaction.tableName,
                    orderType = newTransaction.orderType,
                    customerName = newTransaction.customerName,
                    status = "PENDING",
                    itemsJson = itemsJson
                ))
            }

            // 7. Tambah Komisi Kasir
            if (totalCommission > 0) {
                userDao.addCommission(userId, totalCommission)
            }

            // 8. Selesai
            withContext(Dispatchers.Main) {
                if (isSplitBill) _cart.value = _cart.value.filterNot { it.isSelectedForSplit } else _cart.value = emptyList()
                onSuccess(savedTransaction)
            }
        } catch (e: Exception) { 
            e.printStackTrace() 
        }
    }
}

fun PosViewModel.printReceipt(
    context: Context, 
    transaction: TransactionEntity, 
    settingsManager: SettingsManager, 
    cartSnapshot: List<CartItem>
) {
    val macAddress = settingsManager.printerMacAddress ?: return
    
    val receiptItems = cartSnapshot.map { cartItem ->
        ReceiptItem(
            name = cartItem.product.name, 
            qty = cartItem.quantity, 
            price = getPriceForVariant(cartItem.product, cartItem.selectedVariation).toDouble(), 
            variation = cartItem.selectedVariation + (if (cartItem.notes.isNotBlank()) " | ${cartItem.notes}" else "")
        )
    }

    val strukText = ReceiptFormatter.buildReceipt(
        storeName = settingsManager.storeName ?: "ZoKas", 
        storeAddress = settingsManager.storeAddress ?: "", 
        storePhone = settingsManager.storePhone ?: "", 
        storeFooter = settingsManager.storeFooter ?: "Terima kasih!",
        transactionId = transaction.id, 
        dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(transaction.timestamp)),
        orderType = transaction.orderType ?: "Dine-In", 
        cashierName = transaction.cashierName ?: "Kasir", 
        customerName = transaction.customerName ?: "Pelanggan Umum",
        items = receiptItems, 
        subtotal = (transaction.totalPrice - transaction.taxAmount - transaction.serviceCharge).toDouble(), 
        total = transaction.totalPrice.toDouble(), 
        payment = transaction.cashReceived.toDouble(), 
        change = transaction.changeAmount.toDouble()
    )

    viewModelScope.launch(Dispatchers.IO) {
        val printerManager = BluetoothPrinterManager(context)
        val cfg = PrinterUpgradePrefs.load(context)
        val outputStream = java.io.ByteArrayOutputStream()
        outputStream.write(EscPosCommands.INIT)

        if (cfg.printLogo) {
            val logoBytes = EscPosCommands.printDefaultOrCustomLogo(
                context = context,
                logoPath = settingsManager.storeLogoPath,
                targetWidthPx = cfg.paperSize.logoWidthPx
            )
            if (logoBytes.isNotEmpty()) {
                outputStream.write(logoBytes)
            }
        }

        val adaptedText = if (cfg.paperSize == PrinterPaperSize.MM_80) {
            strukText.replace("=".repeat(32), "=".repeat(48)).replace("-".repeat(32), "-".repeat(48))
        } else {
            strukText
        }

        val cleanReceiptText = adaptedText.trimEnd()
        outputStream.write(EscPosCommands.ALIGN_LEFT)
        outputStream.write(cleanReceiptText.toByteArray(charset("GBK")))
        val effectiveFeed = cfg.feedLines.coerceIn(0, 3)
        if (effectiveFeed > 0) {
            outputStream.write(byteArrayOf(0x1B, 0x64, effectiveFeed.toByte()))
        }
        if (cfg.autoCutPaper) {
            outputStream.write(EscPosCommands.CUT_PAPER)
        }

        if (cfg.autoOpenDrawerOnCash && transaction.paymentMethod.equals("CASH", ignoreCase = true)) {
            printerManager.openCashDrawer(macAddress)
        }
        printerManager.printData(macAddress, outputStream.toByteArray())
    }
}

// ================================================================
// HOLD ORDER (PARKIR PESANAN)
// ================================================================
fun PosViewModel.saveHoldOrder(customerName: String) {
    val currentCart = _cart.value
    if (currentCart.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val cartJson = JSONArray().apply {
                currentCart.forEach { item ->
                    put(JSONObject().apply {
                        put("productId", item.product.id)
                        put("name", item.product.name)
                        put("price", item.product.price)
                        put("quantity", item.quantity)
                        put("variation", item.selectedVariation)
                        put("notes", item.notes)
                        put("serialNumber", item.serialNumber)
                        put("assignedStaff", item.assignedStaff)
                    })
                }
            }.toString()

            holdOrderDao.insertHoldOrder(HoldOrderEntity(branchId = _currentBranchId.value, tableName = _selectedTable.value, customerName = customerName, cartJson = cartJson))
            withContext(Dispatchers.Main) {
                _cart.value = emptyList()
                _selectedCustomer.value = null
                _selectedTable.value = "Take-Away"
            }
        } catch (e: Exception) { Log.e("HoldOrder", "Gagal simpan: ${e.message}") }
    }
}

fun PosViewModel.resumeHoldOrder(order: HoldOrderEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray(order.cartJson)
            val newCart = mutableListOf<CartItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val product = productDao.getProductById(obj.getLong("productId"))
                if (product != null) {
                    newCart.add(
                        CartItem(
                            product = product,
                            quantity = obj.getInt("quantity"),
                            selectedVariation = obj.optString("variation", ""),
                            notes = obj.optString("notes", ""),
                            serialNumber = obj.optString("serialNumber", ""),
                            assignedStaff = obj.optString("assignedStaff", "")
                        )
                    )
                }
            }
            withContext(Dispatchers.Main) {
                _cart.value = newCart
                _selectedTable.value = order.tableName
                _selectedCustomer.value = if (order.customerName != "Pelanggan Umum") _customers.value.find { it.name == order.customerName } else null
            }
            holdOrderDao.deleteHoldOrder(order.id)
        } catch (e: Exception) { Log.e("HoldOrder", "Gagal resume: ${e.message}") }
    }
}

// ================================================================
// PELUNASAN BON, VOID, REFUND, QRIS, MERGE
// ================================================================
fun PosViewModel.payUnpaidTransaction(tx: TransactionEntity, cashReceived: Long, paidMethod: String = "CASH", user: UserEntity? = null, onSuccess: () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val priorPaid = tx.cashReceived.coerceAtLeast(0L)
            val remainingDue = (tx.totalPrice - priorPaid).coerceAtLeast(0L)
            val settledAmount = if (cashReceived >= remainingDue) remainingDue else cashReceived.coerceAtLeast(0L)
            val changeAmount = if (cashReceived > remainingDue) cashReceived - remainingDue else 0L
            val totalPaidAll = priorPaid + settledAmount
            val settleTs = System.currentTimeMillis()
            val combinedMethod = if (priorPaid > 0L && !tx.paymentMethod.contains("Lunas", ignoreCase = true)) {
                "${tx.paymentMethod} + Lunas $paidMethod"
            } else {
                "Lunas ($paidMethod)"
            }
            transactionDao.updateTransactionStatus(tx.id, totalPaidAll, changeAmount, combinedMethod, settleTs)

            val appCtx = runCatching {
                val field = PosViewModel::class.java.declaredFields.firstOrNull {
                    android.content.Context::class.java.isAssignableFrom(it.type)
                }?.apply { isAccessible = true }
                field?.get(this) as? android.content.Context
            }.getOrNull()
            if (appCtx != null) {
                val txItems = transactionItemDao.getItemsByTransaction(tx.id).first()
                val qSum = txItems.joinToString(", ") { "${it.quantity}x ${it.productName}" }
                DpLedgerTracker.appendEntry(
                    appCtx,
                    tx.id,
                    DpPaymentEntry(
                        timestamp = settleTs,
                        amount = settledAmount,
                        method = "Pelunasan $paidMethod",
                        note = "Pelunasan Sisa Tagihan",
                        qtySummary = qSum
                    )
                )
            }
            
            cashFlowDao.insertCashFlow(CashFlowEntity(
                shiftId = _activeShift.value?.id ?: 0L, branchId = _currentBranchId.value, type = "IN", 
                amount = settledAmount, note = "Pelunasan BON/DP #${tx.id} (${tx.customerName})", 
                userId = user?.id ?: 0L, userName = user?.name ?: "SYSTEM"
            ))
            
            if (user != null) {
                userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "PELUNASAN BON/DP #${tx.id}: ${formatRupiah(settledAmount)} via $paidMethod", actionType = "TRANSACTION"))
            }
            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.voidTransaction(transaction: TransactionEntity, pin: String, currentUser: UserEntity, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            if (hashPin(pin) != currentUser.pin && currentUser.role != "OWNER") {
                withContext(Dispatchers.Main) { onResult(false, "PIN salah atau akses ditolak.") }
                return@launch
            }
            transactionDao.voidTransaction(transaction.id)
            
            val items = transactionItemDao.getItemsByTransaction(transaction.id).first()
            items.forEach { item ->
                val pid = item.productId
                if (pid != null) {
                    val prod = productDao.getProductById(pid)
                    if (prod != null) productDao.updateStock(prod.id, prod.stock + item.quantity)
                }
            }
            
            userDao.insertAuditLog(AuditLogEntity(userId = currentUser.id, userName = currentUser.name, action = "VOID TRANSAKSI #${transaction.id}", actionType = "TRANSACTION"))
            withContext(Dispatchers.Main) { onResult(true, "Transaksi berhasil di-void.") }
        } catch (e: Exception) { withContext(Dispatchers.Main) { onResult(false, "Gagal: ${e.message}") } }
    }
}

fun PosViewModel.refundTransaction(transaction: TransactionEntity, pin: String, currentUser: UserEntity, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            if (hashPin(pin) != currentUser.pin && currentUser.role != "OWNER") {
                withContext(Dispatchers.Main) { onResult(false, "PIN salah.") }
                return@launch
            }
            transactionDao.refundTransaction(transaction.id)
            
            val items = transactionItemDao.getItemsByTransaction(transaction.id).first()
            items.forEach { item ->
                val pid = item.productId
                if (pid != null) {
                    val prod = productDao.getProductById(pid)
                    if (prod != null) productDao.updateStock(prod.id, prod.stock + item.quantity)
                }
            }
            
            userDao.insertAuditLog(AuditLogEntity(userId = currentUser.id, userName = currentUser.name, action = "REFUND TRANSAKSI #${transaction.id}", actionType = "TRANSACTION"))
            withContext(Dispatchers.Main) { onResult(true, "Transaksi berhasil di-refund.") }
        } catch (e: Exception) { withContext(Dispatchers.Main) { onResult(false, "Gagal: ${e.message}") } }
    }
}

fun PosViewModel.generateQrisPayment(amount: Long, staticPayload: String) {
    _qrisDynamicString.value = QrisHelper.generateDynamicQris(staticPayload, amount)
    _isQrisPaid.value = false
}

fun PosViewModel.generateTransferPayment(baseAmount: Long) {
    _uniqueTransferAmount.value = baseAmount + (1..999).random()
    _isTransferPaid.value = false
}

fun PosViewModel.resetQrisPayment() {
    _isQrisPaid.value = false
    _qrisDynamicString.value = ""
}

fun PosViewModel.resetTransferPayment() {
    _isTransferPaid.value = false
    _uniqueTransferAmount.value = 0L
}

fun PosViewModel.mergeOrder(tableA: String, tableB: String) {
    val holdsB = _holdOrders.value.filter { it.tableName == tableB }
    if (holdsB.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            holdsB.forEach { orderB ->
                holdOrderDao.insertHoldOrder(orderB.copy(tableName = tableA, id = 0, branchId = _currentBranchId.value))
                holdOrderDao.deleteHoldOrder(orderB.id)
            }
            withContext(Dispatchers.Main) { _selectedTable.value = tableA }
        } catch (e: Exception) { Log.e("MergeOrder", "Gagal: ${e.message}") }
    }
}

fun PosViewModel.deleteProductCleanly(product: ProductEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            productDao.deleteProduct(product)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
