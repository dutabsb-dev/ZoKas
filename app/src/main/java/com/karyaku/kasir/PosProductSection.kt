package com.karyaku.kasir

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.karyaku.kasir.data.local.ProductEntity
import java.io.File

private data class ParsedVariantStock(
    val name: String,
    val stock: Int,
    val multiplier: Int = 1
)

private fun extractVariantStocks(product: ProductEntity): List<ParsedVariantStock> {
    val raw = product.variations.trim()
    if (raw.isEmpty() || raw.contains("|")) return emptyList()
    val parts = raw.split(",")
    val result = mutableListOf<ParsedVariantStock>()
    for ((index, part) in parts.withIndex()) {
        val pieces = part.split(":")
        if (pieces.size >= 2) {
            val vName = pieces[0].trim()
            val stockStr = pieces.getOrNull(3)?.trim()
            val vStock = if (!stockStr.isNullOrEmpty()) {
                stockStr.toIntOrNull() ?: 0
            } else {
                if (index == 0) product.stock else 0
            }
            val mult = (pieces.getOrNull(4)?.trim()?.toIntOrNull() ?: 1).coerceAtLeast(1)
            if (vName.isNotEmpty()) {
                result.add(ParsedVariantStock(vName, vStock, mult))
            }
        }
    }
    return result
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosProductSection(
    products: List<ProductEntity>,
    categories: List<String>,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onProductClick: (ProductEntity) -> Unit,
    onProductLongClick: (ProductEntity) -> Unit,
    onBarcodeScan: (String) -> Unit,
    onCameraScanClick: () -> Unit,
    searchFocusRequester: FocusRequester
) {
    val context = LocalContext.current
    val layoutPrefs = remember { context.getSharedPreferences("zokas_ui_prefs", Context.MODE_PRIVATE) }
    var isCompactListMode by remember {
        mutableStateOf(layoutPrefs.getBoolean("compact_product_list", false))
    }

    // State Sortir (Stok, Expired, Harga) & Filter Satuan
    var sortOption by remember { mutableStateOf("DEFAULT") }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedUnitFilter by remember { mutableStateOf("SEMUA") }
    var showUnitMenu by remember { mutableStateOf(false) }

    val availableUnitsInProducts = remember(products) {
        val units = products.map { extractProductBaseUnit(it) }.distinct().sorted()
        listOf("SEMUA") + units
    }

    val sortedAndFilteredProducts = remember(products, sortOption, selectedUnitFilter) {
        val unitFiltered = if (selectedUnitFilter == "SEMUA") {
            products
        } else {
            products.filter { extractProductBaseUnit(it).equals(selectedUnitFilter, ignoreCase = true) }
        }
        when (sortOption) {
            "STOCK_ASC" -> unitFiltered.sortedWith(compareBy<ProductEntity> { it.isService }.thenBy { it.stock })
            "STOCK_DESC" -> unitFiltered.sortedWith(compareBy<ProductEntity> { it.isService }.thenByDescending { it.stock })
            "EXPIRY_ASC" -> unitFiltered.sortedWith(
                compareBy<ProductEntity> { it.expiryDate <= 0L }.thenBy { it.expiryDate }
            )
            "PRICE_ASC" -> unitFiltered.sortedBy { it.price }
            "PRICE_DESC" -> unitFiltered.sortedByDescending { it.price }
            else -> unitFiltered
        }
    }

    val isEnLang = ZokasLocaleManager.isEnglish()
    val sortLabel = when (sortOption) {
        "STOCK_ASC" -> zokasText("Stok Sedikit", "Low Stock, if (isEnLang) en else in")
        "STOCK_DESC" -> zokasText("Stok Banyak", "High Stock, if (isEnLang) en else in")
        "EXPIRY_ASC" -> zokasText("Expired Terdekat", "Nearest Expiry, if (isEnLang) en else in")
        "PRICE_ASC" -> zokasText("Harga Termurah", "Lowest Price, if (isEnLang) en else in")
        "PRICE_DESC" -> zokasText("Harga Termahal", "Highest Price, if (isEnLang) en else in")
        else -> zokasText("Urutkan", "Sort By, if (isEnLang) en else in")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Baris 1: Kategori + Tombol Toggle Grid / List
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = category == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategorySelect(category) },
                        leadingIcon = {
                            Icon(
                                imageVector = resolveCategoryIcon(category),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.primary
                            )
                        },
                        label = {
                            Text(
                                category,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        val nextMode = !isCompactListMode
                        isCompactListMode = nextMode
                        layoutPrefs.edit().putBoolean("compact_product_list", nextMode).apply()
                    },
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCompactListMode) Icons.Default.GridView else Icons.Default.ViewList,
                        contentDescription = zokasText("Ubah Tampilan", "Switch View"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCompactListMode) zokasText("Grid", "Grid") else zokasText("List", "List"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Baris 2: Sortir (Stok, Expired, Harga) & Filter Satuan
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    border = BorderStroke(
                        1.dp,
                        if (sortOption != "DEFAULT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (sortOption != "DEFAULT") MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                        contentColor = if (sortOption != "DEFAULT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = sortLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    val sortItems = listOf(
                        "DEFAULT" to zokasText("Urutan Default", "Default Order"),
                        "STOCK_ASC" to zokasText("Stok Paling Sedikit (Menipis)", "Lowest Stock (Low")),
                        "STOCK_DESC" to zokasText("Stok Paling Banyak", "Highest Stock"),
                        "EXPIRY_ASC" to zokasText("Tanggal Expired Terdekat", "Nearest Expiry Date"),
                        "PRICE_ASC" to zokasText("Harga Termurah", "Lowest Price, if (isEnLang) en else in"),
                        "PRICE_DESC" to zokasText("Harga Termahal", "Highest Price, if (isEnLang) en else in")
                    )
                    sortItems.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    label,
                                    fontSize = 12.sp,
                                    fontWeight = if (sortOption == key) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sortOption == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                sortOption = key
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showUnitMenu = true },
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selectedUnitFilter != "SEMUA") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedUnitFilter != "SEMUA") MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f) else Color.Transparent,
                        contentColor = if (selectedUnitFilter != "SEMUA") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (selectedUnitFilter == "SEMUA") zokasText("Semua Satuan", "All Units, if (isEnLang) en else in") else zokasText("Satuan: $selectedUnitFilter", "Unit: $selectedUnitFilter, if (isEnLang) en else in"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = showUnitMenu,
                    onDismissRequest = { showUnitMenu = false },
                    modifier = Modifier.heightIn(max = 260.dp)
                ) {
                    availableUnitsInProducts.forEach { unitKey ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (unitKey == "SEMUA") zokasText("Semua Satuan", "All Units, if (isEnLang) en else in") else unitKey,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedUnitFilter == unitKey) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                selectedUnitFilter = unitKey
                                showUnitMenu = false
                            }
                        )
                    }
                }
            }
        }

        if (sortedAndFilteredProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotBlank())
                            zokasText("Produk '$searchQuery' tidak ditemukan", "Product '$searchQuery' not found")
                        else
                            zokasText("Belum ada produk sesuai filter ini", "No products match this filter"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = zokasText("Coba reset pencarian, kategori, atau filter satuan.", "Try resetting search, category, or unit filter."),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isNotBlank() || selectedCategory != "Semua" || selectedUnitFilter != "SEMUA") {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) onSearchChange("")
                                if (selectedCategory != "Semua") onCategorySelect("Semua")
                                selectedUnitFilter = "SEMUA"
                                sortOption = "DEFAULT"
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(zokasText("Reset Semua Filter", "Reset All Filters"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (isCompactListMode) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 84.dp, start = 12.dp, end = 12.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(items = sortedAndFilteredProducts, key = { it.id }) { product ->
                    ProductCompactListRow(
                        product = product,
                        onClick = { onProductClick(product) },
                        onLongClick = { onProductLongClick(product) }
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 115.dp),
                contentPadding = PaddingValues(bottom = 84.dp, start = 12.dp, end = 12.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(items = sortedAndFilteredProducts, key = { it.id }) { product ->
                    ProductGridCard(
                        product = product,
                        onClick = { onProductClick(product) },
                        onLongClick = { onProductLongClick(product) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductGridCard(
    product: ProductEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "scale"
    )

    val isExpiringSoon = product.expiryDate > 0 &&
        (product.expiryDate - System.currentTimeMillis()) < (7L * 24 * 60 * 60 * 1000)
    val isOutOfStock = !product.isService && product.stock <= 0
    val isLowStock = !product.isService && product.stock in 1..5
    val variantStocks = remember(product.variations, product.stock) {
        extractVariantStocks(product)
    }
    val productSku = remember(product.barcode) { extractProductSku(product.barcode) }
    val baseUnit = remember(product.barcode) { extractProductBaseUnit(product) }

    val imageFile = remember(product.imagePath) {
        product.imagePath.trim().takeIf { it.isNotEmpty() }?.let { File(it) }
    }
    val hasValidImage = remember(imageFile?.absolutePath) {
        imageFile != null && imageFile.exists() && imageFile.length() > 0L
    }

    val errorColor = MaterialTheme.colorScheme.error
    val containerBg = MaterialTheme.colorScheme.background
    val titleColor = remember(containerBg) { contrastingTextForBackground(containerBg) }
    val priceColor = MaterialTheme.colorScheme.primary
    val categoryLabel = if (productSku.isNotEmpty()) "$productSku • ${product.category}" else product.category

    val isEnLang = ZokasLocaleManager.isEnglish()
    val stockText = remember(product.isService, product.stock, variantStocks, baseUnit, isEnLang) {
        val activeLang = if (isEnLang) "en" else "in"
        if (product.isService) {
            zokasText("Jasa", "Service", activeLang)
        } else if (variantStocks.isNotEmpty()) {
            val vSummary = variantStocks.joinToString(" • ") {
                if (it.multiplier > 1) "${it.name}: ${it.stock} (=${it.stock * it.multiplier} $baseUnit)"
                else "${it.name}: ${it.stock} $baseUnit"
            }
            zokasText("Stok: ${product.stock} $baseUnit ($vSummary)", "Stock: ${product.stock} $baseUnit ($vSummary", activeLang)
        } else {
            zokasText("Stok: ${product.stock} $baseUnit", "Stock: ${product.stock} $baseUnit", activeLang)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 1. Nama produk di atas card dengan bounce running text sampai ujung teks terlihat
        BounceMarqueeText(
            text = product.name,
            style = TextStyle(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                lineHeight = 14.sp
            ),
            color = titleColor,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        )

        // Card utama: badge kategori vertikal di kiri + gambar full tanpa terpotong + stok di bawah dalam card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.95f)
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            border = BorderStroke(
                width = if (isLowStock || isOutOfStock) 1.5.dp else 1.dp,
                color = when {
                    isOutOfStock || isLowStock -> errorColor
                    isExpiringSoon -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                }
            ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Badge kategori di sebelah kiri dengan teks kecil, posisi vertikal & bounce running text
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier
                        .width(20.dp)
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = resolveCategoryIcon(product.category, product.isService),
                                contentDescription = product.category,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .requiredWidth(64.dp)
                                        .graphicsLayer { rotationZ = -90f },
                                    contentAlignment = Alignment.Center
                                ) {
                                    BounceMarqueeText(
                                        text = categoryLabel,
                                        style = TextStyle(
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 10.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                // Area kanan di dalam card: gambar full tidak terpotong + info stok di bagian bawah dalam card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasValidImage) {
                            AsyncImage(
                                model = imageFile,
                                contentDescription = product.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isOutOfStock -> Color(0xFFFEE2E2)
                                            isLowStock -> Color(0xFFFEF2F2)
                                            isExpiringSoon -> Color(0xFFFEF3C7)
                                            else -> Color(0xFFECFDF5)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = resolveCategoryIcon(product.category, product.isService),
                                    contentDescription = product.name,
                                    tint = when {
                                        isOutOfStock || isLowStock -> Color(0xFFB91C1C)
                                        isExpiringSoon -> Color(0xFFB45309)
                                        else -> Color(0xFF047857)
                                    },
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        if (isOutOfStock || isLowStock || isExpiringSoon || product.promoType != "NONE") {
                            val badgeBg = when {
                                isOutOfStock -> Color(0xFFDC2626)
                                isLowStock -> Color(0xFFEA580C)
                                isExpiringSoon -> Color(0xFFD97706)
                                else -> Color(0xFF059669)
                            }
                            val badgeText = when {
                                isOutOfStock -> "HABIS"
                                isLowStock -> "TIPIS"
                                isExpiringSoon -> "EXP"
                                product.promoType == "BOGO" -> "BOGO"
                                else -> "-${product.promoValue}%"
                            }
                            Surface(
                                color = badgeBg,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                    }

                    // 3. Teks stok tampil di bawah tetapi tetap di dalam card
                    Surface(
                        color = if (isOutOfStock || isLowStock)
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BounceMarqueeText(
                            text = stockText,
                            style = TextStyle(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 12.sp
                            ),
                            color = if (isOutOfStock || isLowStock)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // Harga di bawah card (di luar card)
        BounceMarqueeText(
            text = formatRupiah(product.price),
            style = TextStyle(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Black
            ),
            color = priceColor,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductCompactListRow(
    product: ProductEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isOutOfStock = !product.isService && product.stock <= 0
    val isLowStock = !product.isService && product.stock in 1..5
    val variantStocks = remember(product.variations, product.stock) {
        extractVariantStocks(product)
    }
    val productSku = remember(product.barcode) { extractProductSku(product.barcode) }
    val baseUnit = remember(product.barcode) { extractProductBaseUnit(product) }
    val imageFile = remember(product.imagePath) {
        product.imagePath.trim().takeIf { it.isNotEmpty() }?.let { File(it) }
    }
    val hasValidImage = remember(imageFile?.absolutePath) {
        imageFile != null && imageFile.exists() && imageFile.length() > 0L
    }
    val hasEmptyVariant = variantStocks.any { it.stock <= 0 }

    val isEnRow = ZokasLocaleManager.isEnglish()
    val stockRunningText = remember(product.isService, product.stock, variantStocks, baseUnit, isEnRow) {
        val activeLang = if (isEnRow) "en" else "in"
        if (product.isService) {
            zokasText("Layanan Jasa (Tanpa Stok)", "Service Item (No Stock", activeLang)
        } else if (variantStocks.isNotEmpty()) {
            val details = variantStocks.joinToString("  •  ") {
                if (it.multiplier > 1) "${it.name}: ${it.stock} (=${it.stock * it.multiplier} $baseUnit)"
                else "${it.name}: ${it.stock} $baseUnit"
            }
            "${zokasText("Total", "Total", activeLang)}: ${product.stock} $baseUnit  •  $details"
        } else if (isOutOfStock) {
            zokasText("Stok Habis (0 $baseUnit)", "Out of Stock (0 $baseUnit", activeLang)
        } else if (isLowStock) {
            zokasText("STOK MENIPIS: ${product.stock} $baseUnit", "LOW STOCK: ${product.stock} $baseUnit", activeLang)
        } else {
            zokasText("Stok Tersedia: ${product.stock} $baseUnit", "In Stock: ${product.stock} $baseUnit", activeLang)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .heightIn(min = 64.dp, max = 78.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isLowStock || isOutOfStock)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        border = BorderStroke(
            width = if (isLowStock || isOutOfStock) 1.5.dp else 1.dp,
            color = if (isLowStock || isOutOfStock)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isLowStock || isOutOfStock)
                            MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (hasValidImage) {
                    AsyncImage(
                        model = imageFile,
                        contentDescription = product.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = if (productSku.isNotEmpty()) productSku.take(4) else product.name.take(2).uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = if (isLowStock || isOutOfStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (productSku.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = productSku,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = product.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isLowStock || isOutOfStock) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = product.category,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = stockRunningText,
                    fontSize = 11.sp,
                    fontWeight = if (isOutOfStock || isLowStock || variantStocks.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isOutOfStock || isLowStock || hasEmptyVariant)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = formatRupiah(product.price),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = if (isLowStock || isOutOfStock) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                if (product.wholesaleMinQty > 0 && product.wholesalePrice > 0) {
                    Text(
                        text = zokasText("Grosir ≥${product.wholesaleMinQty}: ${formatRupiah(product.wholesalePrice)}", "Wholesale ≥${product.wholesaleMinQty}: ${formatRupiah(product.wholesalePrice)}"),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BounceMarqueeText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    softWrap: Boolean = false,
    textAlign: TextAlign? = null
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE
        )
    )
}

private fun contrastingTextForBackground(bgColor: Color): Color {
    return if (bgColor.luminance() > 0.45f) Color(0xFF0F172A) else Color(0xFFF8FAFC)
}


private fun resolveCategoryIcon(category: String, isService: Boolean = false): androidx.compose.ui.graphics.vector.ImageVector {
    if (isService) return Icons.Default.Build
    val c = category.trim().lowercase(java.util.Locale.getDefault())
    if (c.isEmpty() || c == "semua") return Icons.Default.Category

    return when {
        c.contains("makanan") || c.contains("snack") || c.contains("ringan") ||
            c.contains("roti") || c.contains("kue") || c.contains("nasi") ||
            c.contains("mie") || c.contains("ayam") || c.contains("kuliner") ||
            c.contains("food") -> Icons.Default.Fastfood

        c.contains("minuman") || c.contains("kopi") || c.contains("coffee") ||
            c.contains("teh") || c.contains("tea") || c.contains("susu") ||
            c.contains("jus") || c.contains("juice") || c.contains("es ") ||
            c.contains("air") || c.contains("drink") || c.contains("cafe") -> Icons.Default.LocalCafe

        c.contains("sembako") || c.contains("beras") || c.contains("gula") ||
            c.contains("minyak") || c.contains("bumbu") || c.contains("dapur") ||
            c.contains("bahan") -> Icons.Default.ShoppingBasket

        c.contains("elektronik") || c.contains("listrik") || c.contains("hp") ||
            c.contains("gadget") || c.contains("kabel") || c.contains("lampu") -> Icons.Default.ElectricalServices

        c.contains("pakaian") || c.contains("baju") || c.contains("kaos") ||
            c.contains("fashion") || c.contains("celana") || c.contains("hijab") -> Icons.Default.Checkroom

        c.contains("sepatu") || c.contains("sandal") || c.contains("tas") ||
            c.contains("aksesoris") || c.contains("belanja") -> Icons.Default.ShoppingBag

        c.contains("kebersihan") || c.contains("sabun") || c.contains("shampoo") ||
            c.contains("sampo") || c.contains("deterjen") || c.contains("laundry") ||
            c.contains("tisu") -> Icons.Default.CleaningServices

        c.contains("kesehatan") || c.contains("obat") || c.contains("apotek") ||
            c.contains("medis") || c.contains("vitamin") -> Icons.Default.MedicalServices

        c.contains("kecantikan") || c.contains("kosmetik") || c.contains("skincare") ||
            c.contains("parfum") || c.contains("salon") -> Icons.Default.Spa

        c.contains("otomotif") || c.contains("motor") || c.contains("mobil") ||
            c.contains("oli") || c.contains("bengkel") || c.contains("sparepart") -> Icons.Default.DirectionsCar

        c.contains("buku") || c.contains("atk") || c.contains("tulis") ||
            c.contains("kantor") || c.contains("sekolah") -> Icons.Default.MenuBook

        c.contains("rumah") || c.contains("perabot") || c.contains("bangunan") -> Icons.Default.Home

        else -> Icons.Default.Category
    }
}
