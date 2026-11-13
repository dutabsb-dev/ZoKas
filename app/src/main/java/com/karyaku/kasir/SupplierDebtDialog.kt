package com.karyaku.kasir

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.karyaku.kasir.data.local.PurchaseEntity
import com.karyaku.kasir.data.local.PurchasePaymentEntity
import com.karyaku.kasir.data.local.UserEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ====================================================================
// MODUL E — HUTANG SUPPLIER CICILAN
// ====================================================================
// Container utama: daftar hutang per supplier, expandable.
// Tap PO → form bayar cicilan.
// Tombol "Riwayat" → riwayat pembayaran per PO.
// ====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierDebtDialog(
    viewModel: PosViewModel,
    currentUser: UserEntity,
    onDismiss: () -> Unit
) {
    val outstandingPurchases by viewModel.outstandingPurchases.collectAsStateWithLifecycle()
    val totalDebt by viewModel.totalOutstandingDebt.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()

    var purchaseToPay by remember { mutableStateOf<PurchaseEntity?>(null) }
    var purchaseToViewHistory by remember { mutableStateOf<PurchaseEntity?>(null) }

    // Supplier ID yang sedang di-expand. Default: semua expanded saat
    // pertama buka (agar user langsung lihat detail).
    val expandedSuppliers = remember { mutableStateMapOf<Long, Boolean>() }

    // Auto-expand semua supplier saat data pertama kali masuk.
    LaunchedEffect(outstandingPurchases.size) {
        if (expandedSuppliers.isEmpty() && outstandingPurchases.isNotEmpty()) {
            outstandingPurchases.map { it.supplierId }.distinct().forEach {
                expandedSuppliers[it] = true
            }
        }
    }

    val grouped = remember(outstandingPurchases) {
        outstandingPurchases.groupBy { it.supplierId }
    }

    if (purchaseToPay != null) {
        PurchasePaymentFormDialog(
            purchase = purchaseToPay!!,
            currentUser = currentUser,
            viewModel = viewModel,
            onDismiss = { purchaseToPay = null }
        )
    }

    if (purchaseToViewHistory != null) {
        PurchasePaymentHistoryDialog(
            purchase = purchaseToViewHistory!!,
            viewModel = viewModel,
            onDismiss = { purchaseToViewHistory = null }
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

                // ----------------------------------------------------
                // TOP BAR
                // ----------------------------------------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Buku Hutang Supplier",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (outstandingPurchases.isEmpty())
                                "Tidak ada hutang"
                            else
                                "${grouped.size} supplier • ${outstandingPurchases.size} PO belum lunas",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                // ----------------------------------------------------
                // TOTAL OUTSTANDING CARD
                // ----------------------------------------------------
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.RequestQuote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "TOTAL HUTANG",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatRupiah(totalDebt),
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ----------------------------------------------------
                // LIST PO GROUPED BY SUPPLIER
                // ----------------------------------------------------
                if (outstandingPurchases.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Semua hutang sudah lunas!",
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                            Text(
                                "Tidak ada hutang ke supplier.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        grouped.forEach { (supplierId, purchases) ->
                            val supplierName = purchases.firstOrNull()?.supplierName ?: "Supplier #$supplierId"
                            val supplierTotalDebt = purchases.sumOf { it.totalAmount - it.paidAmount }
                            val supplier = suppliers.find { it.id == supplierId }
                            val isExpanded = expandedSuppliers[supplierId] == true

                            item(key = "supplier_$supplierId") {
                                SupplierHeaderCard(
                                    supplierName = supplierName,
                                    contactPhone = supplier?.phone ?: "",
                                    totalDebt = supplierTotalDebt,
                                    poCount = purchases.size,
                                    isExpanded = isExpanded,
                                    onToggle = {
                                        expandedSuppliers[supplierId] = !isExpanded
                                    }
                                )
                            }

                            if (isExpanded) {
                                items(
                                    items = purchases,
                                    key = { "po_${it.id}" }
                                ) { po ->
                                    PurchaseDebtCard(
                                        purchase = po,
                                        onPay = { purchaseToPay = po },
                                        onHistory = { purchaseToViewHistory = po }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================
// HEADER SUPPLIER (expandable)
// ====================================================================
@Composable
private fun SupplierHeaderCard(
    supplierName: String,
    contactPhone: String,
    totalDebt: Long,
    poCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(EmeraldGreen.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    supplierName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$poCount PO",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (contactPhone.isNotBlank()) {
                        Text(
                            " • $contactPhone",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatRupiah(totalDebt),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = ErrorRed
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ====================================================================
// CARD PO (progress + tombol Bayar + Riwayat)
// ====================================================================
@Composable
private fun PurchaseDebtCard(
    purchase: PurchaseEntity,
    onPay: () -> Unit,
    onHistory: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yy HH:mm", Locale.getDefault()) }
    val outstanding = (purchase.totalAmount - purchase.paidAmount).coerceAtLeast(0L)
    val progress = if (purchase.totalAmount > 0)
        (purchase.paidAmount.toFloat() / purchase.totalAmount.toFloat()).coerceIn(0f, 1f)
    else 0f

    val statusColor = when (purchase.status) {
        "PARTIAL" -> AmberAccent
        "UNPAID" -> ErrorRed
        else -> EmeraldGreen
    }
    val statusText = when (purchase.status) {
        "PARTIAL" -> "CICILAN"
        "UNPAID" -> "BELUM BAYAR"
        else -> "LUNAS"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Header PO
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PO #${purchase.id}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Invoice: ${purchase.invoiceNumber.ifBlank { "-" }}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateFormat.format(Date(purchase.timestamp)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Total dan sisa
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Total PO",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatRupiah(purchase.totalAmount),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Sisa Hutang",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatRupiah(outstanding),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ErrorRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = statusColor,
                trackColor = statusColor.copy(alpha = 0.15f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Terbayar: ${formatRupiah(purchase.paidAmount)} dari ${formatRupiah(purchase.totalAmount)} (${(progress * 100).toInt()}%)",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Tombol aksi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onHistory,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SlateGrey.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = SlateGrey,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Riwayat", color = SlateGrey, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onPay,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    enabled = outstanding > 0
                ) {
                    Icon(
                        Icons.Default.Payment,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bayar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ====================================================================
// FORM BAYAR CICILAN
// ====================================================================
@Composable
private fun PurchasePaymentFormDialog(
    purchase: PurchaseEntity,
    currentUser: UserEntity,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var amountInput by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf("CASH") }
    var note by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val outstanding = (purchase.totalAmount - purchase.paidAmount).coerceAtLeast(0L)
    val amount = amountInput.toLongOrNull() ?: 0L

    // Validasi live.
    val isAmountValid = amount in 1..outstanding

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Payment, contentDescription = null, tint = EmeraldGreen) },
        title = {
            Text(
                text = "Bayar Hutang",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Info PO
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            purchase.supplierName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "PO #${purchase.id} • ${purchase.invoiceNumber.ifBlank { "-" }}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total PO", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatRupiah(purchase.totalAmount), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sudah dibayar", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatRupiah(purchase.paidAmount), fontSize = 12.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sisa hutang", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(formatRupiah(outstanding), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Input nominal
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = {
                        if (it.length <= 12 && it.all { c -> c.isDigit() }) {
                            amountInput = it
                            errorMessage = ""
                        }
                    },
                    label = { Text("Nominal Pembayaran (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    isError = errorMessage.isNotEmpty()
                )

                if (errorMessage.isNotEmpty()) {
                    Text(
                        errorMessage,
                        color = ErrorRed,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick-fill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedChipButton(
                        label = "25%",
                        onClick = {
                            amountInput = ((outstanding * 25) / 100).coerceAtLeast(1).toString()
                            errorMessage = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedChipButton(
                        label = "50%",
                        onClick = {
                            amountInput = ((outstanding * 50) / 100).coerceAtLeast(1).toString()
                            errorMessage = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedChipButton(
                        label = "LUNAS",
                        onClick = {
                            amountInput = outstanding.toString()
                            errorMessage = ""
                        },
                        modifier = Modifier.weight(1f),
                        accentColor = EmeraldGreen
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Metode
                Text(
                    "Metode Pembayaran",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("CASH", "Bank Transfer", "QRIS").forEach { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = {
                                Text(
                                    method,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedMethod == method) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Catatan
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Catatan (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isAmountValid) {
                        errorMessage = "Nominal harus antara Rp 1 dan ${formatRupiah(outstanding)}."
                        return@Button
                    }
                    viewModel.recordSupplierPayment(
                        purchase = purchase,
                        amount = amount,
                        paymentMethod = selectedMethod,
                        note = note,
                        user = currentUser,
                        onResult = { success, msg ->
                            if (success) {
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                errorMessage = msg
                            }
                        }
                    )
                },
                enabled = isAmountValid,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("BAYAR", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

// ====================================================================
// RIWAYAT PEMBAYARAN PER PO
// ====================================================================
@Composable
private fun PurchasePaymentHistoryDialog(
    purchase: PurchaseEntity,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val paymentsFlow = remember(purchase.id) { viewModel.getPaymentsForPurchase(purchase.id) }
    val payments by paymentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val dateFormat = remember { SimpleDateFormat("dd MMM yy HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.History, contentDescription = null, tint = EmeraldGreen) },
        title = {
            Text(
                text = "Riwayat Pembayaran",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "PO #${purchase.id} • ${purchase.supplierName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Total: ${formatRupiah(purchase.totalAmount)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (payments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Belum ada pembayaran tercatat.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(payments, key = { it.id }) { payment ->
                            PaymentHistoryItem(payment = payment, dateFormat = dateFormat)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Total terbayar",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            formatRupiah(payments.sumOf { it.amount }),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
private fun PaymentHistoryItem(
    payment: PurchasePaymentEntity,
    dateFormat: SimpleDateFormat
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatRupiah(payment.amount),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = EmeraldGreen
                )
                Surface(
                    color = AmberAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        payment.paymentMethod,
                        fontSize = 10.sp,
                        color = AmberAccentDark,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                dateFormat.format(Date(payment.timestamp)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "oleh: ${payment.userName}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (payment.note.isNotBlank()) {
                Text(
                    "Catatan: ${payment.note}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

// ====================================================================
// HELPER: Tombol chip outline
// ====================================================================
@Composable
private fun OutlinedChipButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = SlateGrey
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
    }
}