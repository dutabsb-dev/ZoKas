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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.karyaku.kasir.data.local.ProductEntity
import java.io.File

// ====================================================================
// UPDATE PosProductSection.kt:
//   1. RUNNING TEXT (MARQUEE) DETAIL STOK VARIAN:
//      - Menggunakan Modifier.basicMarquee() pada teks detail stok di
//        ProductGridCard dan ProductCompactListRow agar seluruh rincian
//        stok varian yang panjang berjalan otomatis (running text) dan
//        terbaca utuh tanpa terpotong titik-titik (...).
//   2. FIX BUG LAYOUT MODE LIST MELEBAR VERTIKAL:
//      - Mengunci tinggi maksimum dan struktur Row pada ProductCompactListRow
//        serta mengganti tumpukan chip varian dengan baris running text yang
//        stabil sehingga tidak pernah melebar memenuhi layar saat di-scroll.
// ====================================================================

private data class ParsedVariantStock(
    val name: String,
    val stock: Int
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
            if (vName.isNotEmpty()) {
                result.add(ParsedVariantStock(vName, vStock))
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Baris Kategori + Tombol Toggle Grid / List Padat
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
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

            // Tombol Toggle Mode Grid vs Mode List Padat
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
                        contentDescription = "Ubah Tampilan",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCompactListMode) "Grid" else "List",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Empty State Ilustratif
        if (products.isEmpty()) {
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
                            "Produk '$searchQuery' tidak ditemukan"
                        else
                            "Belum ada produk di kategori ini",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (searchQuery.isNotBlank())
                            "Coba kata kunci lain atau tekan Reset pencarian."
                        else
                            "Tambahkan produk melalui tab Produk atau pilih kategori 'Semua'.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isNotBlank() || selectedCategory != "Semua") {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) onSearchChange("")
                                if (selectedCategory != "Semua") onCategorySelect("Semua")
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tampilkan Semua Produk", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (isCompactListMode) {
            // MODE LIST PADAT (Anti-Stretch & dilengkapi Running Text Stok Varian)
            LazyColumn(
                contentPadding = PaddingValues(bottom = 84.dp, start = 12.dp, end = 12.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(items = products, key = { it.id }) { product ->
                    ProductCompactListRow(
                        product = product,
                        onClick = { onProductClick(product) },
                        onLongClick = { onProductLongClick(product) }
                    )
                }
            }
        } else {
            // MODE GRID RINGKAS & CERAH (dengan Running Text Stok Varian)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 115.dp),
                contentPadding = PaddingValues(bottom = 84.dp, start = 12.dp, end = 12.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(items = products, key = { it.id }) { product ->
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
    val variantStocks = remember(product.variations, product.stock) {
        extractVariantStocks(product)
    }
    val productSku = remember(product.barcode) { extractProductSku(product.barcode) }

    val imageFile = remember(product.imagePath) {
        if (product.imagePath.isNotEmpty()) File(product.imagePath) else null
    }
    val hasValidImage = imageFile != null && imageFile.exists()

    val errorColor = MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.92f)
            .scale(scale)
            .shadow(3.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isOutOfStock -> errorColor.copy(alpha = 0.6f)
                isExpiringSoon -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasValidImage) {
                AsyncImage(
                    model = imageFile,
                    contentDescription = product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.62f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = when {
                                    isOutOfStock -> listOf(Color(0xFF475569), Color(0xFF1E293B))
                                    isExpiringSoon -> listOf(Color(0xFFD97706), Color(0xFF92400E))
                                    else -> listOf(Color(0xFF10B981), Color(0xFF047857))
                                }
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (productSku.isNotEmpty()) "$productSku • ${product.category}" else product.category,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (product.promoType != "NONE") {
                        Surface(
                            color = Color(0xFFF59E0B),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (product.promoType == "BOGO") "BOGO" else "-${product.promoValue}%",
                                fontSize = 9.sp,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isOutOfStock) {
                        Surface(
                            color = Color(0xFFEF4444),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "HABIS",
                                fontSize = 8.sp,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = product.name,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        fontSize = 13.sp,
                        color = Color.White,
                        lineHeight = 15.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isExpiringSoon) {
                        Text(
                            "SEGERA EXPIRED",
                            fontSize = 8.sp,
                            color = Color(0xFFFDE047),
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatRupiah(product.price),
                        fontSize = 14.sp,
                        color = Color(0xFFFBBF24),
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // RUNNING TEXT (MARQUEE) UNTUK RINCIAN STOK VARIAN & TOTAL
                    val stockRunningText = remember(product.isService, product.stock, variantStocks) {
                        if (product.isService) {
                            "Jasa / Layanan"
                        } else if (variantStocks.isNotEmpty()) {
                            "Stok Total: ${product.stock}  •  " + variantStocks.joinToString("  •  ") { "${it.name}: ${it.stock}" }
                        } else {
                            "Stok: ${product.stock}"
                        }
                    }
                    val hasEmptyVariant = variantStocks.any { it.stock <= 0 }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (product.isService) Icons.Default.Build else Icons.Default.Inventory,
                            contentDescription = null,
                            tint = if (isOutOfStock || hasEmptyVariant) Color(0xFFFCA5A5) else Color.White.copy(alpha = 0.88f),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stockRunningText,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOutOfStock || hasEmptyVariant) Color(0xFFFCA5A5) else Color.White.copy(alpha = 0.92f),
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }
                }
            }
        }
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
    val variantStocks = remember(product.variations, product.stock) {
        extractVariantStocks(product)
    }
    val productSku = remember(product.barcode) { extractProductSku(product.barcode) }
    val imageFile = remember(product.imagePath) {
        if (product.imagePath.isNotEmpty()) File(product.imagePath) else null
    }
    val hasValidImage = imageFile != null && imageFile.exists()
    val hasEmptyVariant = variantStocks.any { it.stock <= 0 }

    val stockRunningText = remember(product.isService, product.stock, variantStocks) {
        if (product.isService) {
            "Layanan Jasa (Tanpa Stok)"
        } else if (variantStocks.isNotEmpty()) {
            "Total: ${product.stock}  •  " + variantStocks.joinToString("  •  ") { "${it.name}: ${it.stock}" }
        } else if (isOutOfStock) {
            "Stok Habis (0)"
        } else {
            "Stok Tersedia: ${product.stock}"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .heightIn(min = 64.dp, max = 78.dp) // Kunci tinggi maksimum agar tidak pernah stretch satu layar
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        border = BorderStroke(
            1.dp,
            if (isOutOfStock) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
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
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
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
                        color = MaterialTheme.colorScheme.primary,
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
                        color = MaterialTheme.colorScheme.onSurface,
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

                // Running Text (Marquee) untuk detail stok di Mode List
                Text(
                    text = stockRunningText,
                    fontSize = 11.sp,
                    fontWeight = if (isOutOfStock || variantStocks.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isOutOfStock || hasEmptyVariant)
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
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                if (product.wholesaleMinQty > 0 && product.wholesalePrice > 0) {
                    Text(
                        text = "Grosir ≥${product.wholesaleMinQty}: ${formatRupiah(product.wholesalePrice)}",
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
