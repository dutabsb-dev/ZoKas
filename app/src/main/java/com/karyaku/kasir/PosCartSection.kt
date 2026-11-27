package com.karyaku.kasir

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karyaku.kasir.data.local.CustomerEntity
import com.karyaku.kasir.data.local.CustomerMembershipEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


object LiveCartFractionState {
    @Volatile var hasOverride: Boolean = false
    @Volatile var liveSubtotal: Long = 0L
    @Volatile var liveTotal: Long = 0L
    @Volatile var updatedAtMs: Long = 0L

    fun publish(sub: Long, tot: Long) {
        liveSubtotal = sub
        liveTotal = tot
        hasOverride = true
        updatedAtMs = System.currentTimeMillis()
    }

    fun clear() {
        hasOverride = false
        liveSubtotal = 0L
        liveTotal = 0L
        updatedAtMs = 0L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosCartSection(
    cart: List<CartItem>,
    subtotal: Long,
    discount: Long,
    taxAmount: Long,
    serviceCharge: Long,
    totalPrice: Long,
    selectedCustomer: CustomerEntity?,
    customerMemberships: List<CustomerMembershipEntity>,
    pointsUsed: Int,
    selectedTable: String,
    selectedOrderType: String,
    pickupDate: Long,
    appliedVoucher: String,
    voucherDiscount: Long,
    isSplitBillMode: Boolean,
    isTableModeEnabled: Boolean,
    enableOrderTypes: Boolean,
    enablePreOrder: Boolean,
    enableMembership: Boolean,
    enableCrm: Boolean,
    onOrderTypeChange: (String) -> Unit,
    onPickupDateChange: (Long) -> Unit,
    onToggleSplitMode: () -> Unit,
    onToggleSplitSelection: (CartItem) -> Unit,
    onTableClick: () -> Unit,
    onVoucherClick: () -> Unit,
    onHoldClick: () -> Unit,
    onViewHoldClick: () -> Unit,
    onCustomerClick: () -> Unit,
    onAdd: (CartItem) -> Unit,
    onRemove: (CartItem) -> Unit,
    onSetQuantity: ((CartItem, Double) -> Unit)? = null,
    onDelete: (CartItem) -> Unit,
    onDiscountClick: () -> Unit,
    onCheckout: () -> Unit,
    onEditNotes: (CartItem) -> Unit,
    onEditSerial: (CartItem) -> Unit,
    onAssignStaff: (CartItem) -> Unit,
    onApplyMembership: (CartItem, CustomerMembershipEntity) -> Unit,
    onRemoveMembership: (CartItem) -> Unit,
    lang: String,
    isTaxInclusive: Boolean = false,
    roundingDiff: Long = 0L,
    onLiveTotalChange: ((Long, Long) -> Unit)? = null
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = if (pickupDate > 0) pickupDate else System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onPickupDateChange(datePickerState.selectedDateMillis ?: 0L)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    var fracSyncTick by remember { mutableIntStateOf(0) }
    val computedLiveSubtotal = remember(cart, fracSyncTick, subtotal) {
        if (cart.isEmpty()) 0L
        else if (fracSyncTick == 0) subtotal
        else cart.sumOf { ci ->
            val q = ci.effectiveQty
            if (ci.usedMembershipId != null) 0L
            else if (ci.product.wholesaleMinQty > 0 && q >= ci.product.wholesaleMinQty && ci.product.wholesalePrice > 0)
                Math.round(ci.product.wholesalePrice * q)
            else
                Math.round(getPriceForVariant(ci.product, ci.selectedVariation) * q)
        }
    }
    val computedLiveTotal = remember(
        computedLiveSubtotal, fracSyncTick, totalPrice, subtotal,
        discount, voucherDiscount, taxAmount, serviceCharge, roundingDiff, isTaxInclusive
    ) {
        if (cart.isEmpty()) 0L
        else if (fracSyncTick == 0) totalPrice
        else {
            val baseAfterDisc = (computedLiveSubtotal - discount - voucherDiscount).coerceAtLeast(0L)
            val extraTax = if (isTaxInclusive) 0L else taxAmount
            (baseAfterDisc + extraTax + serviceCharge + roundingDiff).coerceAtLeast(0L)
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp).imePadding()) {

        if (enableOrderTypes) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val types = listOf("Dine-In", "Take-Away", "Delivery")
                types.forEach { type ->
                    FilterChip(
                        selected = selectedOrderType == type,
                        onClick = { onOrderTypeChange(type) },
                        label = {
                            Text(
                                type,
                                fontSize = 11.sp,
                                fontWeight = if (selectedOrderType == type) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedOrderType == type)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        if ((isTableModeEnabled && selectedOrderType == "Dine-In") || enablePreOrder) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isTableModeEnabled && selectedOrderType == "Dine-In") {
                    OutlinedButton(
                        onClick = onTableClick,
                        modifier = Modifier.weight(1f).height(34.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text(selectedTable, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp, maxLines = 1)
                    }
                }
                if (enablePreOrder) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f).height(34.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(
                            text = if (pickupDate > 0) dateFormat.format(Date(pickupDate)) else "Pre-Order",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Baris Pelanggan & Split Bill (ringkas)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCustomerClick, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(30.dp)) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (selectedCustomer != null) {
                        if (enableCrm) "${selectedCustomer.name} (${selectedCustomer.points} ${zokasText("Poin", "Pts", lang)})"
                        else selectedCustomer.name
                    } else zokasText("Pelanggan Umum", "Walk-in Customer", lang),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 11.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Split Bill", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = isSplitBillMode,
                    onCheckedChange = { onToggleSplitMode() },
                    modifier = Modifier.scale(0.75f),
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                )
            }
        }

        if (cart.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
                    Icon(
                        Icons.Default.ShoppingCartCheckout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (lang == "in") "Keranjang Masih Kosong" else "Cart is Empty",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (lang == "in") "Ketuk produk di katalog atau scan barcode untuk mulai transaksi." else "Tap a product or scan barcode to start.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(cart) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        var qtyTick by remember(item.product.id, item.selectedVariation) { mutableIntStateOf(0) }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSplitBillMode) {
                                Checkbox(
                                    checked = item.isSelectedForSplit,
                                    onCheckedChange = { onToggleSplitSelection(item) },
                                    modifier = Modifier.scale(0.75f),
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.product.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (item.selectedVariation.isNotEmpty()) {
                                    Text(
                                        "Varian: ${item.selectedVariation}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (item.serialNumber.isNotEmpty()) {
                                    Text("SN: ${item.serialNumber}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                }
                                if (item.assignedStaff.isNotEmpty()) {
                                    Text("Staf: ${item.assignedStaff}", fontSize = 10.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                                }

                                val effectiveTick = qtyTick
                                val qEff = remember(item.qtyFraction, item.quantity, effectiveTick) { item.effectiveQty }
                                val isWholesale = item.product.wholesaleMinQty > 0 && qEff >= item.product.wholesaleMinQty && item.product.wholesalePrice > 0
                                val displayPrice = if (item.usedMembershipId != null) 0L else if (isWholesale) item.product.wholesalePrice else getPriceForVariant(item.product, item.selectedVariation)
                                val lineTotalPreview = Math.round(displayPrice * qEff)

                                Text(
                                    text = "${formatRupiah(displayPrice)} • ${formatQtyDisplay(qEff)} = ${formatRupiah(lineTotalPreview)}",
                                    fontSize = 11.sp,
                                    color = if (item.usedMembershipId != null || isWholesale || item.selectedVariation.isNotEmpty())
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (item.usedMembershipId != null || isWholesale || item.selectedVariation.isNotEmpty())
                                        FontWeight.Bold
                                    else
                                        FontWeight.Normal
                                )

                                if (item.notes.isNotEmpty()) {
                                    Text("Cat: ${item.notes}", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                if (!item.product.isService) {
                                    Row(
                                        modifier = Modifier.padding(top = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf(0.25 to "¼", 0.5 to "½", 0.75 to "¾", 1.0 to "1").forEach { (qVal, qLbl) ->
                                            val isCur = Math.abs(item.effectiveQty - qVal) < 1e-4
                                            Surface(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .clickable {
                                                        item.qtyFraction = qVal
                                                        item.quantity = Math.ceil(qVal - 1e-6).toInt().coerceAtLeast(1)
                                                        qtyTick++
                                                        fracSyncTick++
                                                        onSetQuantity?.invoke(item, qVal)
                                                    },
                                                color = if (isCur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = qLbl,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCur) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (item.usedMembershipId != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Paket: ${item.usedMembershipName}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        IconButton(onClick = { onRemoveMembership(item) }, modifier = Modifier.size(18.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Batal Paket", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                } else if (enableMembership && customerMemberships.isNotEmpty()) {
                                    var expanded by remember { mutableStateOf(false) }
                                    Box {
                                        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(22.dp)) {
                                            Text("Gunakan Paket", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                            customerMemberships.forEach { cm ->
                                                DropdownMenuItem(
                                                    text = { Text("${cm.membershipName} (Sisa: ${cm.remainingQuota})", fontSize = 12.sp) },
                                                    onClick = { onApplyMembership(item, cm); expanded = false }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onEditNotes(item) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Catatan", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                }
                                if (item.product.isService) {
                                    IconButton(onClick = { onAssignStaff(item) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Person, contentDescription = "Assign Staf", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(15.dp))
                                    }
                                }
                                if (item.product.category == "Elektronik" || item.product.isService) {
                                    IconButton(onClick = { onEditSerial(item) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.List, contentDescription = "Input SN", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                                    }
                                }
                                var showFractionMenu by remember(item.product.id, item.selectedVariation) { mutableStateOf(false) }
                                IconButton(onClick = { onRemove(item) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Remove, contentDescription = "Kurang", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp))
                                }
                                Box {
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { showFractionMenu = true },
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = formatQtyDisplay(item.effectiveQty),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showFractionMenu,
                                        onDismissRequest = { showFractionMenu = false }
                                    ) {
                                        listOf(
                                            0.25 to "¼ (0.25)",
                                            0.5 to "½ (0.5)",
                                            0.75 to "¾ (0.75)",
                                            1.0 to "1",
                                            1.5 to "1½ (1.5)",
                                            2.0 to "2"
                                        ).forEach { (valQty, lbl) ->
                                            DropdownMenuItem(
                                                text = { Text(lbl, fontSize = 12.sp) },
                                                onClick = {
                                                    item.qtyFraction = valQty
                                                    item.quantity = Math.ceil(valQty - 1e-6).toInt().coerceAtLeast(1)
                                                    qtyTick++
                                                    fracSyncTick++
                                                    onSetQuantity?.invoke(item, valQty)
                                                    showFractionMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = { onAdd(item) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = "Tambah", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp))
                                }
                                IconButton(onClick = { LiveCartFractionState.clear(); onDelete(item) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Subtotal:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatRupiah(computedLiveSubtotal), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(zokasText("Diskon:", "Discount:", lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onDiscountClick, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.height(22.dp)) {
                    Text(zokasText("Ubah", "Edit", lang), color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("- ${formatRupiah(discount)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (appliedVoucher.isNotBlank()) "${zokasText("Voucher", "Voucher", lang)} ($appliedVoucher):" else "${zokasText("Voucher", "Voucher", lang)}:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onVoucherClick, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.height(22.dp)) {
                    Text(
                        text = if (appliedVoucher.isNotBlank()) zokasText("Ganti", "Change", lang) else zokasText("Pakai Kode", "Use Code", lang),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                "- ${formatRupiah(voucherDiscount)}",
                fontSize = 11.sp,
                color = if (voucherDiscount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (taxAmount > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (isTaxInclusive) zokasText("Pajak (Termasuk di Harga):", "Tax (Included in Price):", lang) else zokasText("Pajak:", "Tax:", lang),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isTaxInclusive) "(${formatRupiah(taxAmount)})" else "+ ${formatRupiah(taxAmount)}",
                    fontSize = 11.sp,
                    color = if (isTaxInclusive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary
                )
            }
        }
        if (serviceCharge > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Service Charge:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+ ${formatRupiah(serviceCharge)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        if (roundingDiff != 0L) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(zokasText("Pembulatan Tunai:", "Cash Rounding:", lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatRupiah(roundingDiff), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        LaunchedEffect(computedLiveSubtotal, computedLiveTotal, fracSyncTick, cart.size) {
            if (cart.isEmpty()) {
                LiveCartFractionState.clear()
            } else if (fracSyncTick > 0) {
                LiveCartFractionState.publish(computedLiveSubtotal, computedLiveTotal)
                onLiveTotalChange?.invoke(computedLiveSubtotal, computedLiveTotal)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Total:", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(formatRupiah(computedLiveTotal), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = onCheckout,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            enabled = cart.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(zokasText("BAYAR (F2)", "PAY (F2", lang), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
