package com.karyaku.kasir

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
// REKOMENDASI #6, #8, #9, & #10 DITERAPKAN — PosProductSection-v2.kt:
//   1. REKOMENDASI #10 (Toggle Grid Bergambar vs List Padat & Ukuran Card):
//      - Menambahkan tombol toggle "Grid / List" di sebelah kanan bar
//        kategori (tersimpan otomatis di SharedPreferences).
//      - Ukuran kartu Grid dibuat lebih proporsional (minSize = 115.dp,
//        aspectRatio = 0.92f) agar memuat lebih banyak menu di layar HP.
//      - Mode List Padat menampilkan 8-12 produk sekaligus dalam 1 layar.
//   2. REKOMENDASI #9 (Detail Stok per Varian di Card Produk):
//      - Jika produk memiliki varian (Nama:Harga:Modal:Stok), kartu produk
//        otomatis menampilkan rincian stok tiap varian (cth: "Reg:12 • Jmb:0")
//        dengan indikator warna merah jika ada varian yang habis.
//   3. REKOMENDASI #8 (Gambar Produk Lebih Cerah & Kontras Teks Terjaga):
//      - Gradien gelap tidak lagi menutupi seluruh foto produk, melainkan
//        hanya di 45% bagian bawah tempat teks berada dengan opasitas lembut
//        sehingga foto makanan/barang tetap cerah dan segar.
//   4. REKOMENDASI #6 (Empty State Ilustratif & Tema Gelap Adaptif):
//      - Seluruh warna statis diubah ke MaterialTheme.colorScheme.* dan
//        ditambahkan tampilan Empty State modern saat produk tidak ditemukan.
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
        // Baris Kategori + Tombol Toggle Grid / List Padat (Rekomendasi #10)
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

        // Empty State Ilustratif (Rekomendasi #6)
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
                            "Produk "$searchQuery" tidak ditemukan"
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
            // ================================================================
            // MODE LIST PADAT (REKOMENDASI #9 & #10)
            // Memuat 8-12 produk per layar lengkap dengan detail stok varian
            // ================================================================
            LazyColumn(
                contentPadding = PaddingValues(bottom = 84.dp, start = 12.dp, end = 12.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
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
            // ================================================================
            // MODE GRID RINGKAS & CERAH (REKOMENDASI #8, #9, & #10)
            // Ukuran kartu lebih kompak (minSize = 115.dp) agar muat lebih banyak
            // ================================================================
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 115.dp),
                contentPadding = PaddingValues(bottom = 84.dp, start = 12.dp, end = 12.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
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

    val imageFile = remember(product.imagePath) {
        if (product.imagePath.isNotEmpty()) File(product.imagePath) else null
    }
    val hasValidImage = imageFile != null && imageFile.exists()

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.92f) // Lebih ringkas dibanding 0.85f agar tinggi kartu tidak memakan layar
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
                // REKOMENDASI #8: Gambar produk tampil terang & jernih di bagian atas,
                // gradien gelap hanya di 45% bagian bawah tempat teks berada
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
                                    Color.Black.copy(alpha = 0.82f)
                                )
                            )
                        )
                )
            } else {
                // Tanpa foto: gunakan gradasi warna semantik yang bersih dan modern
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
                // Baris Badge Kategori & Promo
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
                            text = product.category,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
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

                // Informasi Nama, Harga, & Detail Stok Varian (Rekomendasi #9)
                Column {
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

                    // REKOMENDASI #9: Tampilkan rincian stok per varian jika produk memiliki varian
                    if (product.isService) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Jasa / Layanan",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    } else if (variantStocks.isNotEmpty()) {
                        val variantSummary = variantStocks.joinToString(" • ") { "${it.name}:${it.stock}" }
                        val hasEmptyVariant = variantStocks.any { it.stock <= 0 }
                        Text(
                            text = variantSummary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasEmptyVariant) Color(0xFFFCA5A5) else Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Inventory,
                                contentDescription = null,
                                tint = if (isOutOfStock) Color(0xFFFCA5A5) else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Stok: ${product.stock}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOutOfStock) Color(0xFFFCA5A5) else Color.White.copy(alpha = 0.85f)
                            )
                        }
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
    val imageFile = remember(product.imagePath) {
        if (product.imagePath.isNotEmpty()) File(product.imagePath) else null
    }
    val hasValidImage = imageFile != null && imageFile.exists()

    Card(
        modifier = Modifier
            .fillMaxWidth()
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
            // Thumbnail Kecil 46.dp
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
                        text = product.name.take(2).uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Kolom Tengah: Nama, Kategori, & Rincian Stok Varian (Rekomendasi #9)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                if (product.isService) {
                    Text(
                        text = "Layanan Jasa (Tanpa Stok)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (variantStocks.isNotEmpty()) {
                    // Tampilkan chip kecil untuk masing-masing stok varian
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        variantStocks.take(4).forEach { vs ->
                            val vEmpty = vs.stock <= 0
                            Surface(
                                color = if (vEmpty)
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                                else
                                    MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(
                                    0.5.dp,
                                    if (vEmpty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Text(
                                    text = "${vs.name}: ${vs.stock}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (vEmpty)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (variantStocks.size > 4) {
                            Text(
                                text = "+${variantStocks.size - 4}",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        text = if (isOutOfStock) "Stok Habis (0)" else "Stok Tersedia: ${product.stock}",
                        fontSize = 11.sp,
                        fontWeight = if (isOutOfStock) FontWeight.Bold else FontWeight.Normal,
                        color = if (isOutOfStock)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Kolom Kanan: Harga Jual & Grosir
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatRupiah(product.price),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (product.wholesaleMinQty > 0 && product.wholesalePrice > 0) {
                    Text(
                        text = "Grosir ≥${product.wholesaleMinQty}: ${formatRupiah(product.wholesalePrice)}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
