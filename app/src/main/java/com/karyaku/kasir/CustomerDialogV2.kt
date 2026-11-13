package com.karyaku.kasir

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.CustomerEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ====================================================================
// MODUL G-3 — DIALOG PELANGGAN V2
// ====================================================================
// Fitur baru:
//   - Filter tier (Regular/Silver/Gold/Platinum)
//   - Search by nama/HP
//   - Badge tier berwarna
//   - Indikator 🎂 untuk ulang tahun hari ini
//   - Info last visit & total spending
//   - Form tambah dengan date picker tanggal lahir
//   - Edit profil via tombol di card
// ====================================================================

@Composable
fun CustomerDialogV2(
    viewModel: PosViewModel,
    customers: List<CustomerEntity>,
    selectedCustomer: CustomerEntity?,
    onSelect: (CustomerEntity?) -> Unit,
    onAddCustomer: (String, String, Long) -> Unit,
    onRedeemPoints: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var showAddForm by remember { mutableStateOf(false) }
    var showRedeemForm by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<CustomerEntity?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTierFilter by remember { mutableStateOf<CustomerTier?>(null) } // null = Semua

    // Filter
    val filteredCustomers = remember(customers, searchQuery, selectedTierFilter) {
        customers.filter { c ->
            val matchSearch = searchQuery.isBlank() ||
                c.name.contains(searchQuery, ignoreCase = true) ||
                c.phone.contains(searchQuery, ignoreCase = true)
            val matchTier = selectedTierFilter == null ||
                CustomerTier.fromPoints(c.points) == selectedTierFilter
            matchSearch && matchTier
        }
    }

    // ============================================================
    // SUB-DIALOG: TAMBAH PELANGGAN
    // ============================================================
    if (showAddForm) {
        CustomerFormDialog(
            customerToEdit = null,
            onDismiss = { showAddForm = false },
            onSave = { name, phone, birthDate ->
                onAddCustomer(name, phone, birthDate)
                showAddForm = false
            }
        )
    }

    // ============================================================
    // SUB-DIALOG: EDIT PELANGGAN
    // ============================================================
    if (customerToEdit != null) {
        CustomerFormDialog(
            customerToEdit = customerToEdit,
            onDismiss = { customerToEdit = null },
            onSave = { name, phone, birthDate ->
                viewModel.updateCustomerProfile(
                    customerId = customerToEdit!!.id,
                    name = name,
                    phone = phone,
                    birthDate = birthDate
                ) { _, _ -> }
                customerToEdit = null
            }
        )
    }

    // ============================================================
    // SUB-DIALOG: REDEEM POIN
    // ============================================================
    if (showRedeemForm && selectedCustomer != null) {
        RedeemPointsDialog(
            customer = selectedCustomer,
            onRedeem = { points ->
                onRedeemPoints(points)
                showRedeemForm = false
                onDismiss()
            },
            onDismiss = { showRedeemForm = false }
        )
    }

    // ============================================================
    // MAIN DIALOG
    // ============================================================
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.People, contentDescription = null, tint = AmberAccent) },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pelanggan", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAddForm = true }) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah", fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
            ) {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari nama / HP...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tier filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedTierFilter == null,
                        onClick = { selectedTierFilter = null },
                        label = { Text("Semua", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberAccent,
                            selectedLabelColor = Color.White
                        )
                    )
                    CustomerTier.entries.forEach { tier ->
                        FilterChip(
                            selected = selectedTierFilter == tier,
                            onClick = {
                                selectedTierFilter = if (selectedTierFilter == tier) null else tier
                            },
                            label = { Text(tier.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tierColor(tier),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(8.dp))

                // Quick: pilih pelanggan umum
                OutlinedButton(
                    onClick = { onSelect(null); onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pelanggan Umum", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // List pelanggan
                if (filteredCustomers.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PersonOff,
                                contentDescription = null,
                                tint = SlateGrey,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (customers.isEmpty()) "Belum ada pelanggan." else "Tidak ada yang cocok.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredCustomers, key = { it.id }) { customer ->
                            CustomerCardV2(
                                customer = customer,
                                isSelected = selectedCustomer?.id == customer.id,
                                dateFormat = dateFormat,
                                onClick = { onSelect(customer); onDismiss() },
                                onEdit = { customerToEdit = customer },
                                onRedeemPoints = if (customer.points > 0) {
                                    { onSelect(customer); showRedeemForm = true }
                                } else null
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

// ====================================================================
// CARD PELANGGAN V2
// ====================================================================
@Composable
private fun CustomerCardV2(
    customer: CustomerEntity,
    isSelected: Boolean,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onRedeemPoints: (() -> Unit)?
) {
    val tier = CustomerTier.fromPoints(customer.points)
    val isBirthday = isBirthdayToday(customer)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                AmberAccent.copy(alpha = 0.15f)
            else if (isBirthday)
                EmeraldGreen.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(10.dp),
        border = if (isBirthday) BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.4f)) else null
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar + badge tier
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(tierColor(tier).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = customer.name.firstOrNull()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = tierColor(tier)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            customer.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isBirthday) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("🎂", fontSize = 12.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Tier badge
                        Surface(
                            color = tierColor(tier).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = tier.label,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = tierColor(tier),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${customer.points} Poin",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Ikon edit
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = SlateGrey,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Info tambahan: HP, last visit, total spending
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (customer.phone != "-" && customer.phone.isNotBlank()) {
                    Text(
                        "📞 ${customer.phone}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (customer.totalSpending > 0) {
                    Text(
                        "💰 ${formatRupiah(customer.totalSpending)}",
                        fontSize = 10.sp,
                        color = EmeraldGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (customer.lastVisitDate > 0L) {
                Text(
                    "Kunjungan terakhir: ${dateFormat.format(Date(customer.lastVisitDate))}",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tombol redeem poin
            if (onRedeemPoints != null) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = onRedeemPoints,
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tukar Poin", fontSize = 10.sp, color = AmberAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ====================================================================
// FORM TAMBAH / EDIT PELANGGAN
// ====================================================================
@Composable
private fun CustomerFormDialog(
    customerToEdit: CustomerEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, birthDate: Long) -> Unit
) {
    var name by remember { mutableStateOf(customerToEdit?.name ?: "") }
    var phone by remember { mutableStateOf(customerToEdit?.phone?.takeIf { it != "-" } ?: "") }
    var birthDate by remember { mutableStateOf(customerToEdit?.birthDate ?: 0L) }
    var errorMessage by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val isEdit = customerToEdit != null

    if (showDatePicker) {
        val initialMillis = if (birthDate > 0L) birthDate else System.currentTimeMillis()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    birthDate = datePickerState.selectedDateMillis ?: 0L
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

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (isEdit) Icons.Default.Person else Icons.Default.PersonAdd,
                contentDescription = null,
                tint = AmberAccent
            )
        },
        title = { Text(if (isEdit) "Edit Pelanggan" else "Tambah Pelanggan", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = "" },
                    label = { Text("Nama *") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    isError = errorMessage.isNotEmpty()
                )
                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = ErrorRed, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("No. HP") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tanggal lahir
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (birthDate > 0L) EmeraldGreen else MaterialTheme.colorScheme.outline)
                ) {
                    Icon(
                        Icons.Default.Cake,
                        contentDescription = null,
                        tint = if (birthDate > 0L) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (birthDate > 0L) dateFormat.format(Date(birthDate)) else "Set Tanggal Lahir (opsional)",
                        color = if (birthDate > 0L) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                    if (birthDate > 0L) {
                        IconButton(
                            onClick = { birthDate = 0L },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus", modifier = Modifier.size(14.dp))
                        }
                    }
                }

                if (birthDate > 0L) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "🎂 Pelanggan akan dapat diskon otomatis di hari ulang tahunnya.",
                        fontSize = 10.sp,
                        color = EmeraldGreen,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "Nama wajib diisi."
                        return@Button
                    }
                    onSave(name.trim(), phone.trim().ifBlank { "-" }, birthDate)
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (isEdit) "SIMPAN" else "TAMBAH", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// ====================================================================
// DIALOG REDEEM POIN
// ====================================================================
@Composable
private fun RedeemPointsDialog(
    customer: CustomerEntity,
    onRedeem: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var pointsToRedeem by remember { mutableStateOf("") }
    val pointsInt = pointsToRedeem.toIntOrNull() ?: 0
    val isAmountValid = pointsInt in 1..customer.points
    val rupiahValue = pointsInt * 1000L

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Star, contentDescription = null, tint = AmberAccent) },
        title = { Text("Tukar Poin", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    customer.name,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Poin tersedia: ${customer.points}",
                    fontSize = 12.sp,
                    color = EmeraldGreen,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = pointsToRedeem,
                    onValueChange = { input ->
                        if (input.length <= 6 && input.all { it.isDigit() }) {
                            pointsToRedeem = input
                        }
                    },
                    label = { Text("Jumlah Poin") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    isError = pointsToRedeem.isNotEmpty() && !isAmountValid
                )

                if (pointsToRedeem.isNotEmpty() && !isAmountValid) {
                    Text(
                        "Maksimal ${customer.points} poin.",
                        color = ErrorRed,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                } else if (isAmountValid) {
                    Text(
                        "= ${formatRupiah(rupiahValue)} diskon",
                        color = EmeraldGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = AmberAccent.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "1 poin = Rp 1.000. Diskon akan otomatis diterapkan di keranjang.",
                        fontSize = 10.sp,
                        color = AmberAccentDark,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isAmountValid) onRedeem(pointsInt)
                },
                enabled = isAmountValid,
                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("TUKAR", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// ====================================================================
// HELPER: Warna Tier
// ====================================================================
@Composable
private fun tierColor(tier: CustomerTier): Color = when (tier) {
    CustomerTier.REGULAR -> SlateGrey
    CustomerTier.SILVER -> Color(0xFF78909C)
    CustomerTier.GOLD -> AmberAccentDark
    CustomerTier.PLATINUM -> EmeraldGreenDark
}