package com.karyaku.kasir

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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.CustomerEntity
import com.karyaku.kasir.data.local.UserEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ====================================================================
// MODUL G — DASHBOARD CRM (KELOLA PELANGGAN)
// ====================================================================
// Fitur:
//   - Stats: total pelanggan, total spending, breakdown per tier
//   - Top 5 spenders
//   - Filter tier + search
//   - List pelanggan (card reuse dari CustomerCardV2)
//   - FAB tambah pelanggan
//   - Edit pelanggan (form reuse)
// ====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerManagementDialog(
    viewModel: PosViewModel,
    currentUser: UserEntity,
    onDismiss: () -> Unit
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTierFilter by remember { mutableStateOf<CustomerTier?>(null) }
    var showAddForm by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<CustomerEntity?>(null) }

    // ---------- STATS ----------
    val totalCustomers = customers.size
    val totalSpending = remember(customers) { customers.sumOf { it.totalSpending } }
    val tierCounts = remember(customers) {
        CustomerTier.entries.associateWith { tier ->
            customers.count { CustomerTier.fromPoints(it.points) == tier }
        }
    }

    // ---------- TOP SPENDERS ----------
    val topSpenders = remember(customers) {
        customers.sortedByDescending { it.totalSpending }
            .take(5)
            .filter { it.totalSpending > 0 }
    }

    // ---------- FILTERED ----------
    val filtered = remember(customers, searchQuery, selectedTierFilter) {
        customers.filter { c ->
            val matchSearch = searchQuery.isBlank() ||
                c.name.contains(searchQuery, ignoreCase = true) ||
                c.phone.contains(searchQuery, ignoreCase = true)
            val matchTier = selectedTierFilter == null ||
                CustomerTier.fromPoints(c.points) == selectedTierFilter
            matchSearch && matchTier
        }
    }

    // ---------- SUB-DIALOGS ----------
    if (showAddForm) {
        CustomerFormDialog(
            customerToEdit = null,
            onDismiss = { showAddForm = false },
            onSave = { name, phone, birthDate ->
                viewModel.addCustomer(name, phone, birthDate)
                showAddForm = false
            }
        )
    }

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

    // ---------- MAIN DIALOG ----------
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // ============================================
                    // TOP BAR
                    // ============================================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CRM & Pelanggan",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$totalCustomers pelanggan terdaftar",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // ============================================
                        // STATS CARD
                        // ============================================
                        item {
                            StatsOverviewCard(
                                totalCustomers = totalCustomers,
                                totalSpending = totalSpending,
                                tierCounts = tierCounts
                            )
                        }

                        // ============================================
                        // TOP SPENDERS
                        // ============================================
                        if (topSpenders.isNotEmpty()) {
                            item {
                                Text(
                                    "🏆 Top 5 Pelanggan Setia",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                )
                            }
                            itemsIndexed(topSpenders) { index, customer ->
                                TopSpenderCard(
                                    rank = index + 1,
                                    customer = customer
                                )
                            }
                        }

                        // ============================================
                        // SEARCH + FILTER
                        // ============================================
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Daftar Pelanggan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Cari nama / HP...", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
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
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = selectedTierFilter == null,
                                    onClick = { selectedTierFilter = null },
                                    label = { Text("Semua (${totalCustomers})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AmberAccent,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                CustomerTier.entries.forEach { tier ->
                                    val count = tierCounts[tier] ?: 0
                                    FilterChip(
                                        selected = selectedTierFilter == tier,
                                        onClick = {
                                            selectedTierFilter = if (selectedTierFilter == tier) null else tier
                                        },
                                        label = { Text("${tier.label} ($count)", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = tierColor(tier),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        // ============================================
                        // LIST PELANGGAN
                        // ============================================
                        if (filtered.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.PersonOff,
                                            contentDescription = null,
                                            tint = SlateGrey,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = if (customers.isEmpty())
                                                "Belum ada pelanggan"
                                            else
                                                "Tidak ada yang cocok",
                                            fontWeight = FontWeight.Bold,
                                            color = SlateGrey
                                        )
                                        Text(
                                            text = if (customers.isEmpty())
                                                "Tap + di bawah untuk menambah pelanggan pertama"
                                            else
                                                "Coba ubah kata kunci atau filter tier",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(filtered, key = { _, c -> c.id }) { _, customer ->
                                CustomerCardV2(
                                    customer = customer,
                                    isSelected = false,
                                    dateFormat = dateFormat,
                                    onClick = { customerToEdit = customer },
                                    onEdit = { customerToEdit = customer },
                                    onRedeemPoints = null
                                )
                            }
                        }
                    }
                }

                // ============================================
                // FAB TAMBAH
                // ============================================
                ExtendedFloatingActionButton(
                    onClick = { showAddForm = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = EmeraldGreen,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                    text = { Text("Tambah Pelanggan", fontWeight = FontWeight.Bold) }
                )
            }
        }
    }
}

// ====================================================================
// STATS OVERVIEW CARD
// ====================================================================
@Composable
private fun StatsOverviewCard(
    totalCustomers: Int,
    totalSpending: Long,
    tierCounts: Map<CustomerTier, Int>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = EmeraldGreen),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "TOTAL PELANGGAN",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "$totalCustomers",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "TOTAL BELANJA",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        formatRupiah(totalSpending),
                        color = AmberAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            // Tier breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CustomerTier.entries.forEach { tier ->
                    val count = tierCounts[tier] ?: 0
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(tierColor(tier), CircleShape)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            tier.label,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "$count",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ====================================================================
// TOP SPENDER CARD
// ====================================================================
@Composable
private fun TopSpenderCard(rank: Int, customer: CustomerEntity) {
    val tier = CustomerTier.fromPoints(customer.points)
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> SlateGrey
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(rankColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "#$rank",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = rankColor
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
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = tierColor(tier).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            tier.label,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = tierColor(tier),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    "${customer.points} poin",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatRupiah(customer.totalSpending),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = EmeraldGreen
                )
            }
        }
    }
}