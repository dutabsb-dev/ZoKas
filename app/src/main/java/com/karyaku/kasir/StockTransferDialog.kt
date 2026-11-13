package com.karyaku.kasir

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.BranchEntity
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.StockTransferEntity
import com.karyaku.kasir.data.local.StockTransferItemEntity
import com.karyaku.kasir.data.local.UserEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ====================================================================
// MODUL D-2 — DIALOG TRANSFER STOK ANTAR CABANG
// ====================================================================
// 2 Tab:
//   1. Buat Transfer: pilih cabang tujuan, pilih produk, input qty.
//   2. Riwayat: list transfer yang melibatkan cabang aktif.
// Semua eksekusi lewat viewModel.transferStock() (transaksional).
// ====================================================================

data class TransferCartItem(
    val product: ProductEntity,
    val quantity: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockTransferDialog(
    viewModel: PosViewModel,
    currentUser: UserEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val allBranches by viewModel.allBranches.collectAsStateWithLifecycle()
    val currentBranch by viewModel.currentBranch.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val transfers by viewModel.transfersForCurrentBranch.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }

    // Form state (hanya untuk Tab 0)
    var toBranchId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    val transferCart = remember { mutableStateListOf<TransferCartItem>() }
    var showProductPicker by remember { mutableStateOf(false) }
    var transferToViewDetail by remember { mutableStateOf<StockTransferEntity?>(null) }

    val otherBranches = allBranches.filter { it.id != currentBranch?.id }
    val canTransfer = allBranches.size > 1

    if (showProductPicker) {
        ProductPickerForTransferDialog(
            products = products.filter { it.id != 0L && !it.isService && it.stock > 0 },
            alreadySelectedIds = transferCart.map { it.product.id }.toSet(),
            onPick = { product ->
                transferCart.add(TransferCartItem(product = product, quantity = 1))
                showProductPicker = false
            },
            onDismiss = { showProductPicker = false }
        )
    }

    if (transferToViewDetail != null) {
        TransferDetailDialog(
            transfer = transferToViewDetail!!,
            viewModel = viewModel,
            onDismiss = { transferToViewDetail = null }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ---------------- TOP BAR ----------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Transfer Stok",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Dari: ${currentBranch?.name ?: "-"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                // ---------------- TABS ----------------
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = EmeraldGreen
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Buat Transfer", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Riwayat (${transfers.size})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                    )
                }

                // ---------------- CONTENT ----------------
                if (selectedTab == 0) {
                    if (!canTransfer) {
                        NoOtherBranchesInfo()
                    } else {
                        TransferFormTab(
                            branches = otherBranches,
                            toBranchId = toBranchId,
                            onToBranchChange = { toBranchId = it },
                            transferCart = transferCart,
                            onRemoveItem = { transferCart.remove(it) },
                            onUpdateQty = { idx, newQty ->
                                if (idx in transferCart.indices) {
                                    transferCart[idx] = transferCart[idx].copy(quantity = newQty)
                                }
                            },
                            onOpenProductPicker = { showProductPicker = true },
                            note = note,
                            onNoteChange = { note = it },
                            onSubmit = {
                                val targetId = toBranchId
                                if (targetId == null) {
                                    Toast.makeText(context, "Pilih cabang tujuan dulu.", Toast.LENGTH_SHORT).show()
                                    return@TransferFormTab
                                }
                                val invalid = transferCart.firstOrNull { it.quantity <= 0 || it.quantity > it.product.stock }
                                if (invalid != null) {
                                    Toast.makeText(
                                        context,
                                        "Stok '${invalid.product.name}' tidak cukup (tersedia: ${invalid.product.stock}).",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@TransferFormTab
                                }
                                if (transferCart.isEmpty()) {
                                    Toast.makeText(context, "Pilih minimal 1 produk.", Toast.LENGTH_SHORT).show()
                                    return@TransferFormTab
                                }
                                viewModel.transferStock(
                                    toBranchId = targetId,
                                    items = transferCart.map { it.product to it.quantity },
                                    note = note,
                                    user = currentUser
                                ) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    if (success) {
                                        transferCart.clear()
                                        toBranchId = null
                                        note = ""
                                        selectedTab = 1 // pindah ke riwayat
                                    }
                                }
                            }
                        )
                    }
                } else {
                    TransferHistoryTab(
                        transfers = transfers,
                        onViewDetail = { transferToViewDetail = it }
                    )
                }
            }
        }
    }
}

// ====================================================================
// TAB 1 — FORM BUAT TRANSFER
// ====================================================================
@Composable
private fun TransferFormTab(
    branches: List<BranchEntity>,
    toBranchId: Long?,
    onToBranchChange: (Long) -> Unit,
    transferCart: List<TransferCartItem>,
    onRemoveItem: (TransferCartItem) -> Unit,
    onUpdateQty: (Int, Int) -> Unit,
    onOpenProductPicker: () -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    var showBranchDropdown by remember { mutableStateOf(false) }
    val selectedBranch = branches.find { it.id == toBranchId }
    val totalQty = transferCart.sumOf { it.quantity }
    val isValid = toBranchId != null && transferCart.isNotEmpty() && transferCart.all { it.quantity in 1..it.product.stock }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ----- Cabang Tujuan -----
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "CABANG TUJUAN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box {
                        OutlinedButton(
                            onClick = { showBranchDropdown = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selectedBranch != null) EmeraldGreen else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Icon(
                                Icons.Default.Store,
                                contentDescription = null,
                                tint = if (selectedBranch != null) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedBranch?.name ?: "Pilih cabang tujuan...",
                                color = if (selectedBranch != null) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selectedBranch != null) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = if (selectedBranch != null) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showBranchDropdown,
                            onDismissRequest = { showBranchDropdown = false }
                        ) {
                            branches.forEach { branch ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(branch.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            if (branch.address.isNotBlank()) {
                                                Text(
                                                    branch.address,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onToBranchChange(branch.id)
                                        showBranchDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ----- Daftar Produk yang Dipilih -----
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PRODUK YANG DITRANSFER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (transferCart.isNotEmpty()) {
                    Text(
                        "${transferCart.size} item • $totalQty pcs",
                        fontSize = 10.sp,
                        color = EmeraldGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (transferCart.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Inventory,
                            contentDescription = null,
                            tint = SlateGrey,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Belum ada produk dipilih",
                            fontWeight = FontWeight.Bold,
                            color = SlateGrey
                        )
                        Text(
                            "Tap tombol + di bawah untuk pilih produk",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(transferCart.size) { idx ->
                val item = transferCart[idx]
                TransferCartItemRow(
                    item = item,
                    onRemove = { onRemoveItem(item) },
                    onQtyChange = { newQty -> onUpdateQty(idx, newQty) }
                )
            }
        }

        // ----- Tombol Pilih Produk -----
        item {
            OutlinedButton(
                onClick = onOpenProductPicker,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, EmeraldGreen),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("TAMBAH PRODUK", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // ----- Catatan -----
        item {
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                label = { Text("Catatan (opsional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // ----- Info Box -----
        item {
            Surface(
                color = EmeraldGreen.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Produk yang belum ada di cabang tujuan akan otomatis dibuatkan dengan data yang sama (nama, harga, barcode, dll). Stok source berkurang dan stok tujuan bertambah secara langsung.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 13.sp
                    )
                }
            }
        }

        // ----- Submit -----
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "PROSES TRANSFER",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ====================================================================
// ROW ITEM PRODUK YANG AKAN DITRANSFER
// ====================================================================
@Composable
private fun TransferCartItemRow(
    item: TransferCartItem,
    onRemove: () -> Unit,
    onQtyChange: (Int) -> Unit
) {
    var qtyText by remember(item.product.id) { mutableStateOf(item.quantity.toString()) }
    val isExceed = (qtyText.toIntOrNull() ?: 0) > item.product.stock

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isExceed) ErrorRed.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.product.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Stok di cabang ini: ${item.product.stock}",
                        fontSize = 10.sp,
                        color = if (isExceed) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isExceed) FontWeight.Bold else FontWeight.Normal
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = ErrorRed, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Qty:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))

                // Minus
                OutlinedButton(
                    onClick = {
                        val current = qtyText.toIntOrNull() ?: 1
                        if (current > 1) {
                            val newVal = current - 1
                            qtyText = newVal.toString()
                            onQtyChange(newVal)
                        }
                    },
                    modifier = Modifier.size(32.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { input ->
                        if (input.length <= 6 && input.all { it.isDigit() }) {
                            qtyText = input
                            onQtyChange(input.toIntOrNull() ?: 0)
                        }
                    },
                    modifier = Modifier.width(70.dp),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    isError = isExceed
                )
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedButton(
                    onClick = {
                        val current = qtyText.toIntOrNull() ?: 0
                        if (current < item.product.stock) {
                            val newVal = current + 1
                            qtyText = newVal.toString()
                            onQtyChange(newVal)
                        }
                    },
                    modifier = Modifier.size(32.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isExceed) {
                    Text(
                        "Qty > stok!",
                        fontSize = 10.sp,
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ====================================================================
// TAB 2 — RIWAYAT TRANSFER
// ====================================================================
@Composable
private fun TransferHistoryTab(
    transfers: List<StockTransferEntity>,
    onViewDetail: (StockTransferEntity) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yy HH:mm", Locale.getDefault()) }

    if (transfers.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = SlateGrey,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Belum ada riwayat transfer",
                    fontWeight = FontWeight.Bold,
                    color = SlateGrey
                )
                Text(
                    "Transfer stok antar cabang akan tampil di sini",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(transfers, key = { it.id }) { transfer ->
                TransferHistoryCard(
                    transfer = transfer,
                    dateFormat = dateFormat,
                    onClick = { onViewDetail(transfer) }
                )
            }
        }
    }
}

@Composable
private fun TransferHistoryCard(
    transfer: StockTransferEntity,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(EmeraldGreen.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            transfer.fromBranchName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            transfer.toBranchName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = EmeraldGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Text(
                        dateFormat.format(Date(transfer.timestamp)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${transfer.totalItems} item • ${transfer.totalQuantity} pcs",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "oleh: ${transfer.userName}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (transfer.note.isNotBlank()) {
                Text(
                    "Catatan: ${transfer.note}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

// ====================================================================
// DIALOG: PRODUCT PICKER (untuk pilih produk yang akan ditransfer)
// ====================================================================
@Composable
private fun ProductPickerForTransferDialog(
    products: List<ProductEntity>,
    alreadySelectedIds: Set<Long>,
    onPick: (ProductEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = products.filter { p ->
        p.id !in alreadySelectedIds && (
            query.isBlank() || p.name.contains(query, ignoreCase = true) || p.barcode.contains(query, ignoreCase = true)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Inventory, contentDescription = null, tint = EmeraldGreen) },
        title = { Text("Pilih Produk", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Cari nama / barcode...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (alreadySelectedIds.isNotEmpty() && query.isBlank())
                                "Semua produk sudah dipilih."
                            else
                                "Tidak ada produk ditemukan.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filtered, key = { it.id }) { product ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onPick(product) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            product.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "Stok: ${product.stock} • ${formatRupiah(product.price)}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
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
// DIALOG: DETAIL RIWAYAT TRANSFER
// ====================================================================
@Composable
private fun TransferDetailDialog(
    transfer: StockTransferEntity,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val itemsFlow = remember(transfer.id) { viewModel.getTransferItems(transfer.id) }
    val items by itemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val dateFormat = remember { SimpleDateFormat("dd MMM yy HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Receipt, contentDescription = null, tint = EmeraldGreen) },
        title = { Text("Detail Transfer #${transfer.id}", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${transfer.fromBranchName} → ${transfer.toBranchName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = EmeraldGreen
                )
                Text(
                    dateFormat.format(Date(transfer.timestamp)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "oleh: ${transfer.userName}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (transfer.note.isNotBlank()) {
                    Text(
                        "Catatan: ${transfer.note}",
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (items.isEmpty()) {
                    Text(
                        "Memuat item...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(items, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.productName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (item.barcode.isNotBlank()) {
                                        Text(
                                            "Barcode: ${item.barcode}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    "${item.quantity} pcs",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = EmeraldGreen
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Total",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${transfer.totalItems} item • ${transfer.totalQuantity} pcs",
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

// ====================================================================
// INFO: Butuh minimal 2 cabang
// ====================================================================
@Composable
private fun NoOtherBranchesInfo() {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = AmberAccent,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Butuh Minimal 2 Cabang",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Tambahkan cabang baru lewat menu Kelola Cabang untuk mengaktifkan fitur transfer stok.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}