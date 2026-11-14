package com.karyaku.kasir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File

@Composable
fun CfdContent(
    viewModel: PosViewModel,
    settingsManager: SettingsManager,
    onDismiss: (() -> Unit)? = null
) {
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val selectedTable by viewModel.selectedTable.collectAsStateWithLifecycle()
    
    val subtotal = cart.sumOf { cartItem ->
        if (cartItem.usedMembershipId != null) 0L
        else if (cartItem.product.wholesaleMinQty > 0 && cartItem.quantity >= cartItem.product.wholesaleMinQty && cartItem.product.wholesalePrice > 0) {
            cartItem.product.wholesalePrice * cartItem.quantity
        } else {
            getPriceForVariant(cartItem.product, cartItem.selectedVariation) * cartItem.quantity
        }
    }
    
    val taxAmount = ((subtotal - viewModel.discount.value) * (settingsManager.taxPercent / 100.0)).toLong()
    val serviceChargeAmount = ((subtotal - viewModel.discount.value) * (settingsManager.serviceChargePercent / 100.0)).toLong()
    val totalPrice = (subtotal + taxAmount + serviceChargeAmount - viewModel.discount.value).coerceAtLeast(0)

    val listState = rememberLazyListState()
    val totalQty = cart.sumOf { cartItem -> cartItem.quantity }

    LaunchedEffect(cart.size, totalQty) {
        if (cart.isNotEmpty()) {
            kotlinx.coroutines.delay(50)
            listState.animateScrollToItem(cart.size - 1)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp), // Padding dikurangi
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val logoFile = remember(settingsManager.storeLogoPath) {
                val path = settingsManager.storeLogoPath
                if (!path.isNullOrBlank()) File(path) else null
            }

            if (logoFile != null && logoFile.exists()) {
                AsyncImage(model = logoFile, contentDescription = "Store Logo", contentScale = ContentScale.Fit, modifier = Modifier.size(72.dp).padding(bottom = 8.dp))
            }

            Text(text = settingsManager.storeName ?: "ZoKas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            
            Spacer(modifier = Modifier.height(4.dp))
            if (selectedTable != "Take-Away") {
                Surface(color = AmberAccent.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                    Text("Meja: $selectedTable", fontWeight = FontWeight.Bold, color = AmberAccentDark, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(text = "Total Belanja Anda", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = formatRupiah(totalPrice), 
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 48.sp), // Ukuran font total dikurangi
                fontWeight = FontWeight.Black, 
                color = EmeraldGreen, 
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (cart.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Belum ada item", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(cart, key = { cartItem -> cartItem.product.id to cartItem.selectedVariation }) { cartItem ->
                            CfdItemRow(cartItem = cartItem)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Text("Terima Kasih", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("Silakan lakukan pembayaran di kasir", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            
            if (onDismiss != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss, 
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), 
                    shape = RoundedCornerShape(12.dp), 
                    modifier = Modifier.fillMaxWidth(0.6f).height(44.dp) // Tinggi tombol disesuaikan
                ) {
                    Text("Tutup Layar Pelanggan", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun CfdItemRow(cartItem: CartItem) {
    val unitPrice = when {
        cartItem.usedMembershipId != null -> 0L
        cartItem.product.wholesaleMinQty > 0 && cartItem.quantity >= cartItem.product.wholesaleMinQty && cartItem.product.wholesalePrice > 0 -> cartItem.product.wholesalePrice
        else -> getPriceForVariant(cartItem.product, cartItem.selectedVariation)
    }
    val lineTotal = unitPrice * cartItem.quantity

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Gambar diperkecil sedikit
            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                if (cartItem.product.imagePath.isNotEmpty()) {
                    AsyncImage(model = File(cartItem.product.imagePath), contentDescription = cartItem.product.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // maxLines = 2 agar nama panjang tidak terpotong (Indomi...)
                Text(cartItem.product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (cartItem.selectedVariation.isNotEmpty()) {
                    Text("Varian: ${cartItem.selectedVariation}", fontSize = 12.sp, color = EmeraldGreen, fontWeight = FontWeight.SemiBold)
                }
                Text("${cartItem.quantity} × ${formatRupiah(unitPrice)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Hanya menampilkan subtotal harga, badge "1x" dihapus agar hemat tempat
            Text(formatRupiah(lineTotal), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}