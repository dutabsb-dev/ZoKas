package com.karyaku.kasir

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.karyaku.kasir.data.local.ProductEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationBellButton(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge {
                        Text(
                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Lonceng Notifikasi & Pengingat"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationAndReminderCenterDialog(
    notifications: List<AppNotificationItem>,
    activeReminders: List<TriggeredReminder>,
    categorySettings: Map<NotificationCategory, CategoryNotificationSetting>,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onClearAllNotifications: () -> Unit,
    onDismissReminder: (String) -> Unit,
    onRestockProduct: (ProductEntity) -> Unit,
    onPromoProduct: (ProductEntity, Int) -> Unit,
    onWasteProduct: (ProductEntity) -> Unit,
    onOpenReminderRulesDialog: () -> Unit,
    onOpenNotificationSettingsDialog: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Pengingat Aktif, 1 = Riwayat Lonceng
    var selectedCategoryFilter by remember { mutableStateOf<NotificationCategory?>(null) }

    val enabledCategories = remember(categorySettings) {
        NotificationCategory.entries.filter { cat ->
            categorySettings[cat]?.bellEnabled ?: true
        }.toSet()
    }

    val filteredNotifications = remember(notifications, enabledCategories, selectedCategoryFilter) {
        notifications.filter { item ->
            item.category in enabledCategories &&
                (selectedCategoryFilter == null || item.category == selectedCategoryFilter)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pusat Notifikasi & Pengingat",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Pantau stok menipis, kadaluarsa, & aktivitas toko",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onOpenReminderRulesDialog) {
                        Icon(Icons.Default.AlarmAdd, contentDescription = "Aturan Pengingat")
                    }
                    IconButton(onClick = onOpenNotificationSettingsDialog) {
                        Icon(Icons.Default.Tune, contentDescription = "Saklar Notifikasi")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(
                                "Pengingat Aktif (${activeReminders.size})",
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(
                                "Riwayat (${filteredNotifications.size})",
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTab == 0) {
                    if (activeReminders.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Semua stok & masa berlaku aman", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Belum ada produk yang memicu aturan pengingat.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(activeReminders, key = { it.eventKey }) { rem ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (rem.kind == "EXPIRY")
                                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (rem.kind == "EXPIRY") Icons.Default.EventBusy else Icons.Default.Inventory2,
                                                contentDescription = null,
                                                tint = if (rem.kind == "EXPIRY") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = rem.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { onDismissReminder(rem.eventKey) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Abaikan", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = rem.detail,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (rem.kind == "STOCK") {
                                                OutlinedButton(
                                                    onClick = { onRestockProduct(rem.product) },
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Restock +5", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                OutlinedButton(
                                                    onClick = { onPromoProduct(rem.product, rem.suggestedPromoDiscountPercent) },
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Diskon -${rem.suggestedPromoDiscountPercent}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                OutlinedButton(
                                                    onClick = { onWasteProduct(rem.product) },
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Tandai Waste", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategoryFilter == null,
                            onClick = { selectedCategoryFilter = null },
                            label = { Text("Semua", fontSize = 11.sp) }
                        )
                        NotificationCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryFilter == cat,
                                onClick = { selectedCategoryFilter = cat },
                                label = { Text(cat.titleId, fontSize = 11.sp) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onMarkAllRead) { Text("Tandai Dibaca", fontSize = 11.sp) }
                        TextButton(onClick = onClearAllNotifications) { Text("Bersihkan", fontSize = 11.sp) }
                    }

                    if (filteredNotifications.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Belum ada riwayat notifikasi.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(filteredNotifications, key = { it.id }) { item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onMarkRead(item.id) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (item.isRead)
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        else
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                            Text(fmt.format(Date(item.timestamp)), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(item.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ReminderRuleManagerDialog(
    rules: List<ReminderRule>,
    products: List<ProductEntity>,
    onSaveRules: (List<ReminderRule>) -> Unit,
    onDismiss: () -> Unit
) {
    var workingRules by remember(rules) { mutableStateOf(rules) }
    var editingRule by remember { mutableStateOf<ReminderRule?>(null) }
    var showCreateEditor by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Aturan Pengingat Kustom", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Tutup") }
                }
                Text(
                    "Atur target produk, batas stok minimum, dan aturan pengingat kadaluarsa (H-0 sampai H-90+).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(workingRules, key = { it.id }) { rule ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "Target: ${rule.scopeType.labelId} • Pemicu: ${rule.triggerType.labelId}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val expirySummary = if (rule.expiryDaysBefore == 0) "Hari H (H-0)" else "H-${rule.expiryDaysBefore}"
                                    Text(
                                        "Stok <= ${rule.stockThreshold} • Expired <= $expirySummary • ${rule.expiryActionPreset.labelId}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Switch(
                                    checked = rule.isEnabled,
                                    onCheckedChange = { checked ->
                                        workingRules = workingRules.map { if (it.id == rule.id) it.copy(isEnabled = checked) else it }
                                        onSaveRules(workingRules)
                                    }
                                )
                                IconButton(onClick = { editingRule = rule; showCreateEditor = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Aturan")
                                }
                                if (workingRules.size > 1) {
                                    IconButton(onClick = {
                                        workingRules = workingRules.filterNot { it.id == rule.id }
                                        onSaveRules(workingRules)
                                    }) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Hapus Aturan",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { editingRule = null; showCreateEditor = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Aturan Pengingat Baru", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showCreateEditor) {
        ReminderRuleEditorDialog(
            initialRule = editingRule,
            products = products,
            onDismiss = { showCreateEditor = false; editingRule = null },
            onSave = { savedRule ->
                val exists = workingRules.any { it.id == savedRule.id }
                workingRules = if (exists) {
                    workingRules.map { if (it.id == savedRule.id) savedRule else it }
                } else {
                    workingRules + savedRule
                }
                onSaveRules(workingRules)
                showCreateEditor = false
                editingRule = null
            }
        )
    }
}

@Composable
private fun ReminderRuleEditorDialog(
    initialRule: ReminderRule?,
    products: List<ProductEntity>,
    onDismiss: () -> Unit,
    onSave: (ReminderRule) -> Unit
) {
    val allCategories = remember(products) { products.map { it.category }.distinct().filter { it.isNotBlank() } }
    var ruleName by remember(initialRule) { mutableStateOf(initialRule?.name ?: "Pengingat Stok & Expired") }
    var scopeType by remember(initialRule) { mutableStateOf(initialRule?.scopeType ?: ReminderScopeType.ALL) }
    var triggerType by remember(initialRule) { mutableStateOf(initialRule?.triggerType ?: ReminderTriggerType.BOTH) }
    var stockThresholdText by remember(initialRule) { mutableStateOf((initialRule?.stockThreshold ?: 5).toString()) }
    var expiryDaysText by remember(initialRule) { mutableStateOf((initialRule?.expiryDaysBefore ?: 7).toString()) }
    var includeAlreadyExpired by remember(initialRule) { mutableStateOf(initialRule?.includeAlreadyExpired ?: true) }
    var alertEvenIfZeroStock by remember(initialRule) { mutableStateOf(initialRule?.alertEvenIfZeroStock ?: false) }
    var repeatDailyUntilHandled by remember(initialRule) { mutableStateOf(initialRule?.repeatDailyUntilHandled ?: true) }
    var expiryActionPreset by remember(initialRule) { mutableStateOf(initialRule?.expiryActionPreset ?: ExpiryActionPreset.PROMO_DISCOUNT) }
    var customDiscountText by remember(initialRule) { mutableStateOf((initialRule?.customPromoDiscountPercent ?: 20).toString()) }
    var productFilterQuery by remember { mutableStateOf("") }

    val selectedCategories = remember(initialRule) { mutableStateListOf<String>().apply { addAll(initialRule?.categories ?: emptyList()) } }
    val selectedProductIds = remember(initialRule) { mutableStateListOf<Long>().apply { addAll(initialRule?.productIds ?: emptyList()) } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (initialRule == null) "Buat Aturan Pengingat" else "Edit Aturan Pengingat",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = ruleName,
                            onValueChange = { ruleName = it },
                            label = { Text("Nama Aturan") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // PEMILIHAN PRODUK (Susun bertingkat agar tidak terpotong menjadi "P")
                    item {
                        Text("Pemilihan Produk", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(ReminderScopeType.ALL, ReminderScopeType.CATEGORY).forEach { sc ->
                                    FilterChip(
                                        selected = scopeType == sc,
                                        onClick = { scopeType = sc },
                                        modifier = Modifier.weight(1f),
                                        label = {
                                            Text(
                                                text = sc.labelId,
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                            }
                            FilterChip(
                                selected = scopeType == ReminderScopeType.SPECIFIC_PRODUCTS,
                                onClick = { scopeType = ReminderScopeType.SPECIFIC_PRODUCTS },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = {
                                    Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = {
                                    Text(
                                        text = if (selectedProductIds.isNotEmpty())
                                            "Produk Spesifik (${selectedProductIds.size} dipilih)"
                                        else
                                            ReminderScopeType.SPECIFIC_PRODUCTS.labelId,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }

                    if (scopeType == ReminderScopeType.CATEGORY) {
                        if (allCategories.isEmpty()) {
                            item {
                                Text(
                                    "Belum ada kategori produk tersimpan.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(allCategories) { cat ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        if (cat in selectedCategories) selectedCategories.remove(cat) else selectedCategories.add(cat)
                                    },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = cat in selectedCategories,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedCategories.add(cat) else selectedCategories.remove(cat)
                                        }
                                    )
                                    Text(cat, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    if (scopeType == ReminderScopeType.SPECIFIC_PRODUCTS) {
                        item {
                            OutlinedTextField(
                                value = productFilterQuery,
                                onValueChange = { productFilterQuery = it },
                                placeholder = { Text("Cari nama produk...", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        val filteredProds = products.filter {
                            productFilterQuery.isBlank() || it.name.contains(productFilterQuery, ignoreCase = true)
                        }.take(50)
                        items(filteredProds, key = { it.id }) { prod ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (prod.id in selectedProductIds) selectedProductIds.remove(prod.id) else selectedProductIds.add(prod.id)
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = prod.id in selectedProductIds,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedProductIds.add(prod.id) else selectedProductIds.remove(prod.id)
                                    }
                                )
                                Text(
                                    "${prod.name} (Stok: ${prod.stock})",
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // JENIS PEMICU (Disusun bertumpuk penuh agar label tidak terpotong "Mendekati / Lewa...")
                    item {
                        Text("Jenis Pemicu (Trigger)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ReminderTriggerType.entries.forEach { tr ->
                                FilterChip(
                                    selected = triggerType == tr,
                                    onClick = { triggerType = tr },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = {
                                        Text(
                                            text = tr.labelId,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                )
                            }
                        }
                    }

                    if (triggerType == ReminderTriggerType.LOW_STOCK || triggerType == ReminderTriggerType.BOTH) {
                        item {
                            OutlinedTextField(
                                value = stockThresholdText,
                                onValueChange = { stockThresholdText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Batas Stok Minimum (<=)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // PENGATURAN KADALUARSA LEBIH LUAS & CUSTOM
                    if (triggerType == ReminderTriggerType.EXPIRY || triggerType == ReminderTriggerType.BOTH) {
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "Aturan Peringatan Kadaluarsa (Custom)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Pilih preset cepat atau ketik jumlah hari bebas sebelum tanggal expired:",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    val currentDays = expiryDaysText.toIntOrNull() ?: 7
                                    val presetRows = listOf(
                                        listOf(0 to "Hari H", 1 to "H-1", 3 to "H-3", 7 to "H-7"),
                                        listOf(14 to "H-14", 30 to "H-30", 60 to "H-60", 90 to "H-90")
                                    )
                                    presetRows.forEach { rowPresets ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            rowPresets.forEach { (daysVal, label) ->
                                                FilterChip(
                                                    selected = currentDays == daysVal,
                                                    onClick = { expiryDaysText = daysVal.toString() },
                                                    modifier = Modifier.weight(1f),
                                                    label = {
                                                        Text(
                                                            text = label,
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = expiryDaysText,
                                        onValueChange = { expiryDaysText = it.filter { ch -> ch.isDigit() }.take(4) },
                                        label = { Text("Custom Hari Sebelum Expired (0 = Hari H)") },
                                        placeholder = { Text("Contoh: 10, 45, 120") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { includeAlreadyExpired = !includeAlreadyExpired },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = includeAlreadyExpired,
                                            onCheckedChange = { includeAlreadyExpired = it }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Sertakan produk yang sudah lewat expired", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Tetap bunyikan peringatan jika tanggal expired sudah terlewat.", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { alertEvenIfZeroStock = !alertEvenIfZeroStock },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = alertEvenIfZeroStock,
                                            onCheckedChange = { alertEvenIfZeroStock = it }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Ingatkan meski stok tercatat 0", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Berguna untuk audit fisik barang kadaluarsa di rak.", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { repeatDailyUntilHandled = !repeatDailyUntilHandled },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = repeatDailyUntilHandled,
                                            onCheckedChange = { repeatDailyUntilHandled = it }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Ulangi pengingat setiap hari sampai ditindaklanjuti", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Jika diabaikan hari ini, besok tetap muncul lagi sampai stok di-promo/waste.", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                    Text("Tindakan Disarankan Saat Muncul Alert", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        ExpiryActionPreset.entries.forEach { preset ->
                                            FilterChip(
                                                selected = expiryActionPreset == preset,
                                                onClick = {
                                                    expiryActionPreset = preset
                                                    if (preset.defaultDiscount > 0 && (customDiscountText.toIntOrNull() ?: 0) == 0) {
                                                        customDiscountText = preset.defaultDiscount.toString()
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                label = { Text(preset.labelId, fontSize = 11.5.sp, maxLines = 1) }
                                            )
                                        }
                                    }

                                    if (expiryActionPreset == ExpiryActionPreset.PROMO_DISCOUNT || expiryActionPreset == ExpiryActionPreset.PRIORITY_SELL) {
                                        OutlinedTextField(
                                            value = customDiscountText,
                                            onValueChange = { customDiscountText = it.filter { ch -> ch.isDigit() }.take(2) },
                                            label = { Text("Saran Diskon Promo Cepat (%)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val threshold = stockThresholdText.toIntOrNull() ?: 5
                        val daysVal = (expiryDaysText.toIntOrNull() ?: 7).coerceAtLeast(0)
                        val discVal = (customDiscountText.toIntOrNull() ?: expiryActionPreset.defaultDiscount).coerceIn(0, 95)
                        val finalName = ruleName.trim().ifBlank { "Aturan Pengingat" }
                        onSave(
                            ReminderRule(
                                id = initialRule?.id ?: "rule_${System.currentTimeMillis()}",
                                name = finalName,
                                isEnabled = initialRule?.isEnabled ?: true,
                                scopeType = scopeType,
                                categories = selectedCategories.toList(),
                                productIds = selectedProductIds.toList(),
                                triggerType = triggerType,
                                stockThreshold = threshold,
                                expiryDaysBefore = daysVal,
                                includeAlreadyExpired = includeAlreadyExpired,
                                alertEvenIfZeroStock = alertEvenIfZeroStock,
                                repeatDailyUntilHandled = repeatDailyUntilHandled,
                                expiryActionPreset = expiryActionPreset,
                                customPromoDiscountPercent = discVal
                            )
                        )
                    }) {
                        Text("Simpan Aturan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationPreferencesDialog(
    settingsMap: Map<NotificationCategory, CategoryNotificationSetting>,
    onUpdateSetting: (CategoryNotificationSetting) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saklar Notifikasi per Kejadian",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Tutup") }
                }
                Text(
                    "Aktifkan/nonaktifkan lonceng aplikasi, status bar HP, dan pantauan HP Owner secara mandiri.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 14.dp)
                ) {
                    items(NotificationCategory.entries) { cat ->
                        val cfg = settingsMap[cat] ?: CategoryNotificationSetting(cat)
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(cat.titleId, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(cat.descId, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(6.dp))

                                // 3 kolom seimbang dengan weight(1f) agar label "Owner" & switch tidak pernah terpotong
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Lonceng",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            textAlign = TextAlign.Center
                                        )
                                        Switch(
                                            checked = cfg.bellEnabled,
                                            onCheckedChange = { onUpdateSetting(cfg.copy(bellEnabled = it)) },
                                            modifier = Modifier.scale(0.88f)
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Bar HP",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            textAlign = TextAlign.Center
                                        )
                                        Switch(
                                            checked = cfg.systemBarEnabled,
                                            onCheckedChange = { onUpdateSetting(cfg.copy(systemBarEnabled = it)) },
                                            modifier = Modifier.scale(0.88f)
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "HP Owner",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            textAlign = TextAlign.Center
                                        )
                                        Switch(
                                            checked = cfg.remoteOwnerEnabled,
                                            onCheckedChange = { onUpdateSetting(cfg.copy(remoteOwnerEnabled = it)) },
                                            modifier = Modifier.scale(0.88f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Selesai", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
