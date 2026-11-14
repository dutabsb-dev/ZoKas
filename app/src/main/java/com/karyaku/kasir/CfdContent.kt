package com.karyaku.kasir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import coil.compose.AsyncImage
import com.karyaku.kasir.CartItem
import java.io.File

@Composable
fun CfdContent(
    viewModel: PosViewModel,
    settingsManager: SettingsManager,
    onDismiss: (() -> Unit)? = null
) {
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val selectedTable by viewModel.selectedTable.collectAsStateWithLifecycle()
    
    val subtotal = cart.sumOf {
        if (it.usedMembershipId != null) {
            0L
        } else if (it.product.wholesaleMinQty > 0 && it.quantity >= it.product.wholesaleMinQty && it.product.wholesalePrice > 0) {
            it.product.wholesalePrice * it.quantity
        } else {
            getPriceForVariant(it.product, it.selectedVariation) * it.quantity
        }
    }
    val taxAmount = ((subtotal - viewModel.discount.value) * (settingsManager.taxPercent / 100.0)).toLong()
    val serviceChargeAmount = ((subtotal - viewModel.discount.value) * (settingsManager.serviceChargePercent / 100.0)).toLong()
    val totalPrice = (subtotal + taxAmount + serviceChargeAmount - viewModel.discount.value).coerceAtLeast(0)

    val listState = rememberLazyListState()
    val totalQty = cart.sumOf { it.quantity }

    LaunchedEffect(cart.size, totalQty) {
        if (cart.isNotEmpty()) {
            kotlinx.coroutines.delay(50)
            listState.animateScrollToItem(cart.size - 1)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val logoFile = remember(settingsManager.storeLogoPath) {
                val path = settingsManager.storeLogoPath
                if (!path.isNullOrBlank()) File(path) else null
            }

            if (logoFile != null && logoFile.exists()) {
                AsyncImage(
                    model = logoFile,
                    contentDescription = "Store Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(90.dp).padding(bottom = 12.dp)
                )
            }

            Text(
                text = settingsManager.storeName ?: "ZoKas",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (selectedTable != "Take-Away") {
                Surface(color = AmberAccent.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                    Text("Meja: $selectedTable", fontWeight = FontWeight.Bold, color = AmberAccentDark, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "Total Belanja Anda",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatRupiah(totalPrice),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
                fontWeight = FontWeight.Black,
                color = EmeraldGreen,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(thickness = 2.dp)
            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (cart.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(80.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Belum ada item", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(cart, key = { it.product.id to it.selectedVariation }) { item ->
                            CfdItemRow(item = item)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(thickness = 2.dp)
            Spacer(modifier = Modifier.height(16.dp))

            Text("Terima Kasih", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("Silakan lakukan pembayaran di kasir", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            
            if (onDismiss != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(0.5f).height(48.dp)
                ) {
                    Text("Tutup Layar Pelanggan", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CfdItemRow(item: CartItem) {
    val unitPrice = when {
        item.usedMembershipId != null -> 0L
        item.product.wholesaleMinQty > 0 && item.quantity >= item.product.wholesaleMinQty && item.product.wholesalePrice > 0 -> item.product.wholesalePrice
        else -> getPriceForVariant(item.product, item.selectedVariation)
    }
    val lineTotal = unitPrice * item.quantity

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (item.product.imagePath.isNotEmpty()) {
                    AsyncImage(model = File(item.product.imagePath), contentDescription = item.product.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.selectedVariation.isNotEmpty()) {
                    Text("Varian: ${item.selectedVariation}", fontSize = 14.sp, color = EmeraldGreen, fontWeight = FontWeight.SemiBold)
                }
                Text("${item.quantity} × ${formatRupiah(unitPrice)}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(color = EmeraldGreen.copy(alpha = 0.12f), shape = CircleShape) {
                Text("${item.quantity}x", fontSize = 14.sp, fontWeight = FontWeight.Black, color = EmeraldGreen, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(formatRupiah(lineTotal), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}