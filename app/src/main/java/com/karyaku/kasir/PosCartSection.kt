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

// ====================================================================
// HASIL SIMULASI & PERBAIKAN FITUR TIDAK BEKERJA — PosCartSection-v2.kt:
//   1. FIX BUG #4 (Edit Catatan, Serial Number & Staf pada Multi-Varian):
//      Parameter callback onEditNotes, onEditSerial, dan onAssignStaff
//      diubah dari (ProductEntity) -> Unit menjadi (CartItem) -> Unit
//      agar varian spesifik yang diklik di keranjang langsung diteruskan.
//   2. FIX BUG #5 (Tombol Voucher Tidak Muncul di Keranjang):
//      Parameter onVoucherClick, appliedVoucher, dan voucherDiscount
//      sebelumnya sudah dikirim dari PosScreen namun belum dirender di UI
//      ringkasan keranjang. Kini ditambahkan baris Voucher interaktif
//      lengkap dengan indikator kode aktif dan potongan harganya.
// ====================================================================

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
    onDelete: (CartItem) -> Unit,
    onDiscountClick: () -> Unit,
    onCheckout: () -> Unit,
    onEditNotes: (CartItem) -> Unit,
    onEditSerial: (CartItem) -> Unit,
    onAssignStaff: (CartItem) -> Unit,
    onApplyMembership: (CartItem, CustomerMembershipEntity) -> Unit,
    onRemoveMembership: (CartItem) -> Unit,
    lang: String,
    isTaxInclusive: Boolean = false
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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).imePadding()) {

        // Tombol Atas (Tipe Pesanan)
        if (enableOrderTypes) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isTableModeEnabled && selectedOrderType == "Dine-In") {
                OutlinedButton(
                    onClick = onTableClick,
                    modifier = Modifier.weight(1f).height(40.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text(
                        selectedTable,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            if (enablePreOrder) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f).height(40.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
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

            OutlinedButton(
                onClick = onHoldClick,
                modifier = Modifier.weight(1f).height(40.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    "Parkir",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp
                )
            }
            OutlinedButton(
                onClick = onViewHoldClick,
                modifier = Modifier.weight(1f).height(40.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    "Lihat",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Baris Pelanggan & Split Bill
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (enableCrm) {
                TextButton(onClick = onCustomerClick, contentPadding = PaddingValues(0.dp)) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (selectedCustomer != null) "${selectedCustomer.name} (${selectedCustomer.points} Poin)" else "Pelanggan Umum",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 12.sp
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Split Bill",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = isSplitBillMode,
                    onCheckedChange = { onToggleSplitMode() },
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        // Daftar Item di Keranjang (dengan Empty State Ilustratif jika keranjang kosong)
        if (cart.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                androidx.compose.foundation.shape.CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ShoppingCartCheckout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(cart) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSplitBillMode) {
                            Checkbox(
                                checked = item.isSelectedForSplit,
                                onCheckedChange = { onToggleSplitSelection(item) },
                                modifier = Modifier.scale(0.8f),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.product.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (item.selectedVariation.isNotEmpty()) {
                                Text(
                                    "Varian: ${item.selectedVariation}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (item.serialNumber.isNotEmpty()) {
                                Text(
                                    "SN: ${item.serialNumber}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (item.assignedStaff.isNotEmpty()) {
                                Text(
                                    "Staf: ${item.assignedStaff}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val isWholesale = item.product.wholesaleMinQty > 0 && item.quantity >= item.product.wholesaleMinQty && item.product.wholesalePrice > 0
                            val displayPrice = if (item.usedMembershipId != null) 0L else if (isWholesale) item.product.wholesalePrice else getPriceForVariant(item.product, item.selectedVariation)

                            Text(
                                formatRupiah(displayPrice),
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
                                Text(
                                    "Cat: ${item.notes}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (item.usedMembershipId != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Paket: ${item.usedMembershipName}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { onRemoveMembership(item) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Batal Paket",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            } else if (enableMembership && customerMemberships.isNotEmpty()) {
                                var expanded by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(
                                        onClick = { expanded = true },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text(
                                            "Gunakan Paket",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        customerMemberships.forEach { cm ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        "${cm.membershipName} (Sisa: ${cm.remainingQuota})",
                                                        fontSize = 12.sp
                                                    )
                                                },
                                                onClick = {
                                                    onApplyMembership(item, cm)
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Tombol Aksi Item (Meneruskan CartItem penuh agar akurat per varian)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onEditNotes(item) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Catatan",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (item.product.isService) {
                                IconButton(onClick = { onAssignStaff(item) }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = "Assign Staf",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (item.product.category == "Elektronik" || item.product.isService) {
                                IconButton(onClick = { onEditSerial(item) }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Default.List,
                                        contentDescription = "Input SN",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            IconButton(onClick = { onRemove(item) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Default.Remove,
                                    contentDescription = "Kurang",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "${item.quantity}",
                                modifier = Modifier.padding(horizontal = 4.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { onAdd(item) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Tambah",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(onClick = { onDelete(item) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Hapus",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        }
        // Ringkasan Total
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Subtotal:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatRupiah(subtotal),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Diskon:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(
                    onClick = onDiscountClick,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(
                        "Ubah",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                "- ${formatRupiah(discount)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
        // FIX BUG #5: Baris Voucher Interaktif di Keranjang
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (appliedVoucher.isNotBlank()) "Voucher ($appliedVoucher):" else "Voucher:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onVoucherClick,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(
                        text = if (appliedVoucher.isNotBlank()) "Ganti" else "Pakai Kode",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                "- ${formatRupiah(voucherDiscount)}",
                fontSize = 12.sp,
                color = if (voucherDiscount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (taxAmount > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (isTaxInclusive) "Pajak (Termasuk di Harga):" else "Pajak:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isTaxInclusive) "(${formatRupiah(taxAmount)})" else "+ ${formatRupiah(taxAmount)}",
                    fontSize = 12.sp,
                    color = if (isTaxInclusive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary
                )
            }
        }
        if (serviceCharge > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Service Charge:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "+ ${formatRupiah(serviceCharge)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Total:",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                formatRupiah(totalPrice),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tombol Bayar
        Button(
            onClick = onCheckout,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = cart.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("BAYAR (F2)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
