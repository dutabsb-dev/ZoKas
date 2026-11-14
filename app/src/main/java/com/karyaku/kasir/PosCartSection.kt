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
import com.karyaku.kasir.data.local.ProductEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onEditNotes: (ProductEntity) -> Unit, 
    onEditSerial: (ProductEntity) -> Unit, 
    onAssignStaff: (ProductEntity) -> Unit,
    onApplyMembership: (CartItem, CustomerMembershipEntity) -> Unit,
    onRemoveMembership: (CartItem) -> Unit,
    lang: String
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
                        label = { Text(type, fontSize = 11.sp, fontWeight = if (selectedOrderType == type) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldGreen, selectedLabelColor = MaterialTheme.colorScheme.onPrimary)
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
                    border = BorderStroke(1.dp, AmberAccent)
                ) { 
                    Text(selectedTable, fontWeight = FontWeight.Bold, color = AmberAccent, fontSize = 11.sp, maxLines = 1) 
                }
            }
            
            if (enablePreOrder) {
                OutlinedButton(
                    onClick = { showDatePicker = true }, 
                    modifier = Modifier.weight(1f).height(40.dp), 
                    contentPadding = PaddingValues(0.dp), 
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ErrorRed)
                ) { 
                    Text(
                        text = if (pickupDate > 0) dateFormat.format(Date(pickupDate)) else "Pre-Order",
                        fontWeight = FontWeight.Bold, color = ErrorRed, fontSize = 11.sp, maxLines = 1
                    ) 
                }
            }

            OutlinedButton(
                onClick = onHoldClick, 
                modifier = Modifier.weight(1f).height(40.dp), 
                contentPadding = PaddingValues(0.dp), 
                shape = RoundedCornerShape(12.dp)
            ) { 
                Text("Parkir", fontWeight = FontWeight.Bold, color = EmeraldGreen, fontSize = 11.sp) 
            }
            OutlinedButton(
                onClick = onViewHoldClick, 
                modifier = Modifier.weight(1f).height(40.dp), 
                contentPadding = PaddingValues(0.dp), 
                shape = RoundedCornerShape(12.dp)
            ) { 
                Text("Lihat", fontWeight = FontWeight.Bold, color = EmeraldGreen, fontSize = 11.sp) 
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Baris Pelanggan & Split Bill
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCustomerClick, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.Person, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (selectedCustomer != null) "${selectedCustomer.name} (${selectedCustomer.points} Poin)" else "Pelanggan Umum", 
                    fontWeight = FontWeight.Bold, color = AmberAccent, fontSize = 12.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Split Bill", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = isSplitBillMode, 
                    onCheckedChange = { onToggleSplitMode() }, 
                    modifier = Modifier.scale(0.8f), 
                    colors = SwitchDefaults.colors(checkedTrackColor = EmeraldGreen)
                )
            }
        }

        // Daftar Item di Keranjang
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(cart) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(), 
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), 
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
                                colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen)
                            ) 
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (item.selectedVariation.isNotEmpty()) {
                                Text("Varian: ${item.selectedVariation}", fontSize = 10.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                            }
                            if (item.serialNumber.isNotEmpty()) {
                                Text("SN: ${item.serialNumber}", fontSize = 10.sp, color = SlateGrey, fontWeight = FontWeight.Bold)
                            }
                            if (item.assignedStaff.isNotEmpty()) {
                                Text("Staf: ${item.assignedStaff}", fontSize = 10.sp, color = AmberAccent, fontWeight = FontWeight.Bold)
                            }
                            
                            val isWholesale = item.product.wholesaleMinQty > 0 && item.quantity >= item.product.wholesaleMinQty && item.product.wholesalePrice > 0
                            val displayPrice = if (item.usedMembershipId != null) 0L else if (isWholesale) item.product.wholesalePrice else getPriceForVariant(item.product, item.selectedVariation) // FIX VARIAN HARGA
                            
                            Text(formatRupiah(displayPrice), fontSize = 11.sp, color = if(item.usedMembershipId != null || isWholesale || item.selectedVariation.isNotEmpty()) EmeraldGreen else SlateGrey, fontWeight = if(item.usedMembershipId != null || isWholesale || item.selectedVariation.isNotEmpty()) FontWeight.Bold else FontWeight.Normal)
                            
                            if (item.notes.isNotEmpty()) { 
                                Text("Cat: ${item.notes}", fontSize = 10.sp, color = ErrorRed, maxLines = 1, overflow = TextOverflow.Ellipsis) 
                            }

                            if (item.usedMembershipId != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Paket: ${item.usedMembershipName}", fontSize = 10.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { onRemoveMembership(item) }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Batal Paket", tint = ErrorRed, modifier = Modifier.size(12.dp))
                                    }
                                }
                            } else if (enableMembership && customerMemberships.isNotEmpty()) {
                                var expanded by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(24.dp)) {
                                        Text("Gunakan Paket", fontSize = 10.sp, color = EmeraldGreen)
                                    }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        customerMemberships.forEach { cm ->
                                            DropdownMenuItem(
                                                text = { Text("${cm.membershipName} (Sisa: ${cm.remainingQuota})", fontSize = 12.sp) },
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
                        
                        // Tombol Aksi
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onEditNotes(item.product) }, modifier = Modifier.size(28.dp)) { 
                                Icon(Icons.Default.Edit, contentDescription = "Edit Catatan", tint = EmeraldGreen, modifier = Modifier.size(16.dp)) 
                            }
                            if (item.product.isService) {
                                IconButton(onClick = { onAssignStaff(item.product) }, modifier = Modifier.size(28.dp)) { 
                                    Icon(Icons.Default.Person, contentDescription = "Assign Staf", tint = AmberAccent, modifier = Modifier.size(16.dp)) 
                                }
                            }
                            if (item.product.category == "Elektronik" || item.product.isService) {
                                IconButton(onClick = { onEditSerial(item.product) }, modifier = Modifier.size(28.dp)) { 
                                    Icon(Icons.Default.List, contentDescription = "Input SN", tint = SlateGrey, modifier = Modifier.size(16.dp)) 
                                }
                            }
                            IconButton(onClick = { onRemove(item) }, modifier = Modifier.size(28.dp)) { 
                                Icon(Icons.Default.Remove, contentDescription = "Kurang", modifier = Modifier.size(16.dp)) 
                            }
                            Text(text = "${item.quantity}", modifier = Modifier.padding(horizontal = 4.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            IconButton(onClick = { onAdd(item) }, modifier = Modifier.size(28.dp)) { 
                                Icon(Icons.Default.Add, contentDescription = "Tambah", modifier = Modifier.size(16.dp)) 
                            }
                            IconButton(onClick = { onDelete(item) }, modifier = Modifier.size(28.dp)) { 
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = ErrorRed, modifier = Modifier.size(16.dp)) 
                            }
                        }
                    }
                }
            }
        }

        // Ringkasan Total
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Subtotal:", fontSize = 12.sp, color = SlateGrey)
            Text(formatRupiah(subtotal), fontSize = 12.sp, color = SlateGrey)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Diskon:", fontSize = 12.sp, color = SlateGrey)
                TextButton(onClick = onDiscountClick, contentPadding = PaddingValues(horizontal = 4.dp)) { 
                    Text("Ubah", color = AmberAccent, fontSize = 11.sp) 
                }
            }
            Text("- ${formatRupiah(discount)}", fontSize = 12.sp, color = ErrorRed)
        }
        if (taxAmount > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pajak:", fontSize = 12.sp, color = SlateGrey)
                Text("+ ${formatRupiah(taxAmount)}", fontSize = 12.sp, color = AmberAccent)
            }
        }
        if (serviceCharge > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Service Charge:", fontSize = 12.sp, color = SlateGrey)
                Text("+ ${formatRupiah(serviceCharge)}", fontSize = 12.sp, color = AmberAccent)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Total:", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(formatRupiah(totalPrice), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Tombol Bayar
        Button(
            onClick = onCheckout, 
            modifier = Modifier.fillMaxWidth().height(48.dp), 
            enabled = cart.isNotEmpty(), 
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), 
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("BAYAR (F2)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}