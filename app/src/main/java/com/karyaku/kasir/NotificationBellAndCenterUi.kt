package com.karyaku.kasir

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    val activeBg = if (unreadCount > 0) Color(0xFF10B981) else MaterialTheme.colorScheme.surfaceVariant
    val activeIconColor = if (unreadCount > 0) Color.White else MaterialTheme.colorScheme.primary

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = activeBg,
        border = BorderStroke(
            width = 1.dp,
            color = if (unreadCount > 0) Color(0xFF059669) else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        ),
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (unreadCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                contentDescription = zokasText("Lonceng Notifikasi & Pengingat", "Notification & Reminder Bell"),
                tint = activeIconColor,
                modifier = Modifier.size(18.dp)
            )
            if (unreadCount > 0) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF047857),
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f),
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
                            text = "Pusat Pengingat & Notifikasi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${activeReminders.size} pengingat aktif • ${filteredNotifications.count { !it.isRead }} belum dibaca",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onOpenReminderRulesDialog) {
                        Icon(Icons.Default.AlarmAdd, contentDescription = "Atur Pengingat Kustom")
                    }
                    IconButton(onClick = onOpenNotificationSettingsDialog) {
                        Icon(Icons.Default.Tune, contentDescription = "Pengaturan Notifikasi")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Pengingat (${activeReminders.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Lonceng (${filteredNotifications.count { !it.isRead }})", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTab == 0) {
                    if (activeReminders.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Tidak ada pengingat stok atau expired saat ini.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(onClick = onOpenReminderRulesDialog) {
                                    Text("Kelola Aturan Pengingat")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(activeReminders, key = { it.eventKey }) { rem ->
                                val isExpiry = rem.kind == "EXPIRY"
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isExpiry) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isExpiry) Icons.Default.EventBusy else Icons.Default.Inventory2,
                                                contentDescription = null,
                                                tint = if (isExpiry) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(rem.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(rem.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(10.dp))

                                        if (!isExpiry) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = { onRestockProduct(rem.product) },
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Text("Restock / Audit", fontSize = 12.sp, maxLines = 1)
                                                }
                                                OutlinedButton(
                                                    onClick = { onDismissReminder(rem.eventKey) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Text("Abaikan", fontSize = 12.sp)
                                                }
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Button(
                                                    onClick = { onPromoProduct(rem.product, rem.suggestedPromoDiscountPercent) },
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                                ) {
                                                    Text("Promo -${rem.suggestedPromoDiscountPercent}%", fontSize = 11.sp, maxLines = 1)
                                                }
                                                OutlinedButton(
                                                    onClick = { onWasteProduct(rem.product) },
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                                ) {
                                                    Text("Tandai Waste", fontSize = 11.sp, maxLines = 1)
                                                }
                                                TextButton(
                                                    onClick = { onDismissReminder(rem.eventKey) },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                                ) {
                                                    Text("Abaikan", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Filter kategori cepat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategoryFilter == null,
                            onClick = { selectedCategoryFilter = null },
                            label = { Text("Semua", fontSize = 11.sp) }
                        )
                        enabledCategories.take(3).forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryFilter == cat,
                                onClick = { selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat },
                                label = { Text(cat.titleId, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    if (filteredNotifications.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Belum ada notifikasi untuk kategori aktif.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredNotifications, key = { it.id }) { item ->
                                val timeStr = remember(item.timestamp) {
                                    SimpleDateFormat("dd/MM HH:mm", Locale("id", "ID")).format(Date(item.timestamp))
                                }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onMarkRead(item.id) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (!item.isRead) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "[${item.category.titleId}] ${item.title}",
                                                fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(timeStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(item.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onMarkAllRead) { Text("Tandai Semua Dibaca") }
                        TextButton(onClick = onClearAllNotifications) { Text("Bersihkan", color = MaterialTheme.colorScheme.error) }
                    }
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.84f),
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
                    "Atur target produk, batas stok minimum, dan peringatan expired date (H-7, H-3, H-1).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    Text(
                                        "Stok <= ${rule.stockThreshold} • Expired <= H-${rule.expiryDaysBefore}",
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
                    Text("Tambah Aturan Pengingat Baru")
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
    var expiryDays by remember(initialRule) { mutableStateOf(initialRule?.expiryDaysBefore ?: 7) }
    val selectedCategories = remember(initialRule) { mutableStateListOf<String>().apply { addAll(initialRule?.categories ?: emptyList()) } }
    val selectedProductIds = remember(initialRule) { mutableStateListOf<Long>().apply { addAll(initialRule?.productIds ?: emptyList()) } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (initialRule == null) "Buat Aturan Pengingat" else "Edit Aturan Pengingat",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        OutlinedTextField(
                            value = ruleName,
                            onValueChange = { ruleName = it },
                            label = { Text("Nama Aturan") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Text("Pemilihan Produk", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ReminderScopeType.entries.forEach { sc ->
                                FilterChip(
                                    selected = scopeType == sc,
                                    onClick = { scopeType = sc },
                                    label = { Text(sc.labelId, fontSize = 11.sp, maxLines = 1) }
                                )
                            }
                        }
                    }
                    if (scopeType == ReminderScopeType.CATEGORY) {
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
                    if (scopeType == ReminderScopeType.SPECIFIC_PRODUCTS) {
                        items(products.take(40), key = { it.id }) { prod ->
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
                                Text("${prod.name} (Stok: ${prod.stock})", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    item {
                        Text("Jenis Pemicu (Trigger)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ReminderTriggerType.entries.forEach { tr ->
                                FilterChip(
                                    selected = triggerType == tr,
                                    onClick = { triggerType = tr },
                                    label = { Text(tr.labelId, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = stockThresholdText,
                            onValueChange = { stockThresholdText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Batas Stok Minimum (<=)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Text("Peringatan Expired Date", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 3, 7, 14).forEach { days ->
                                FilterChip(
                                    selected = expiryDays == days,
                                    onClick = { expiryDays = days },
                                    label = { Text("H-$days", fontSize = 12.sp) }
                                )
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
                                expiryDaysBefore = expiryDays
                            )
                        )
                    }) {
                        Text("Simpan Aturan")
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
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Saklar Notifikasi per Kejadian", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Tutup") }
                }
                Text(
                    "Aktifkan/nonaktifkan lonceng aplikasi, status bar HP, dan pantauan HP Owner secara mandiri.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(NotificationCategory.entries) { cat ->
                        val cfg = settingsMap[cat] ?: CategoryNotificationSetting(cat)
                        Card(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(cat.titleId, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(cat.descId, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Lonceng", fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Switch(
                                            checked = cfg.bellEnabled,
                                            onCheckedChange = { onUpdateSetting(cfg.copy(bellEnabled = it)) }
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Bar HP", fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Switch(
                                            checked = cfg.systemBarEnabled,
                                            onCheckedChange = { onUpdateSetting(cfg.copy(systemBarEnabled = it)) }
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Owner", fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Switch(
                                            checked = cfg.remoteOwnerEnabled,
                                            onCheckedChange = { onUpdateSetting(cfg.copy(remoteOwnerEnabled = it)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Selesai")
                }
            }
        }
    }
}
