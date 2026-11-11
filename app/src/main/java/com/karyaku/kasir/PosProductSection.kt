package com.karyaku.kasir

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.karyaku.kasir.data.local.ProductEntity

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
    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery, 
            onValueChange = onSearchChange, 
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .height(50.dp)
                .focusRequester(searchFocusRequester), 
            placeholder = { Text("Cari produk atau scan...", fontSize = 14.sp) }, 
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp)) }, 
            trailingIcon = { 
                IconButton(onClick = onCameraScanClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan", tint = EmeraldGreen, modifier = Modifier.size(18.dp)) 
                }
            }, 
            singleLine = true, 
            shape = RoundedCornerShape(12.dp), 
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmeraldGreen, 
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
            ), 
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), 
            keyboardActions = KeyboardActions(onDone = { 
                if (searchQuery.isNotBlank()) { 
                    onBarcodeScan(searchQuery)
                    onSearchChange("") 
                } 
            })
        )

        // Category Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), 
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                FilterChip(
                    selected = category == selectedCategory, 
                    onClick = { onCategorySelect(category) }, 
                    label = { Text(category, fontWeight = if(category == selectedCategory) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp) }, 
                    shape = RoundedCornerShape(12.dp), 
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldGreen, 
                        selectedLabelColor = Color.White, 
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }

        // Grid Produk
        if (products.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                Text("Produk tidak ditemukan.", color = SlateGrey, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) 
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp), 
                horizontalArrangement = Arrangement.spacedBy(12.dp), 
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = products, key = { it.id }) { product ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.95f else 1f, 
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f), 
                        label = "scale"
                    )
                    
                    val isExpiringSoon = product.expiryDate > 0 && (product.expiryDate - System.currentTimeMillis()) < (7L * 24 * 60 * 60 * 1000)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.85f)
                            .scale(scale)
                            .shadow(4.dp, RoundedCornerShape(12.dp), spotColor = EmeraldGreenDark)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                interactionSource = interactionSource, 
                                indication = null, 
                                onClick = { onProductClick(product) }, 
                                onLongClick = { onProductLongClick(product) }
                            ),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // TAMPILKAN GAMBAR
                            if (product.imagePath.isNotEmpty()) {
                                AsyncImage(
                                    model = java.io.File(product.imagePath),
                                    contentDescription = product.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Gradient tipis agar teks di bawah terbaca
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                                startY = 100f
                                            )
                                        )
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                colors = if (isExpiringSoon) listOf(ErrorRed, Color(0xFFB71C1C)) else listOf(EmeraldGreen, EmeraldGreenDark)
                                            )
                                        )
                                )
                            }

                            Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Surface(color = Color.White.copy(alpha = 0.3f), shape = RoundedCornerShape(6.dp)) { 
                                        Text(text = product.category, fontSize = 9.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) 
                                    }
                                    if (product.promoType != "NONE") {
                                        Surface(color = AmberAccent, shape = RoundedCornerShape(6.dp)) { 
                                            Text(text = if (product.promoType == "BOGO") "BOGO" else "DISC ${product.promoValue}%", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) 
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.weight(1f))
                                
                                Column {
                                    Text(text = product.name, fontWeight = FontWeight.ExtraBold, maxLines = 2, style = MaterialTheme.typography.titleSmall, color = Color.White, lineHeight = 16.sp, overflow = TextOverflow.Ellipsis)
                                    if (isExpiringSoon) { Text("EXPIRED SEGERA", fontSize = 9.sp, color = Color.Yellow, fontWeight = FontWeight.Bold) }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = formatRupiah(product.price), style = MaterialTheme.typography.titleMedium, color = AmberAccent, fontWeight = FontWeight.Black)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (product.isService) {
                                            Icon(Icons.Default.Build, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(10.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(text = "Layanan", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                                        } else {
                                            Icon(Icons.Default.Inventory, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(10.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(text = "Stok: ${product.stock}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}