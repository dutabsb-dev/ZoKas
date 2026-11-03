package com.karyaku.kasir

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(viewModel: PosViewModel) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val totalPrice = cart.sumOf { it.product.price * it.quantity }
    
    var showCartOnPhone by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kasir Karyaku") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        // TOMBOL MELAYANG BARU UNTUK TES DUMMY
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.addDummyProduct() }) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Produk Dummy")
            }
        },
        bottomBar = {
            if (cart.isNotEmpty() && !showCartOnPhone) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.height(72.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total (${cart.sumOf { it.quantity }} item)", fontSize = 12.sp)
                            Text("Rp $totalPrice", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Button(onClick = { showCartOnPhone = true }) {
                            Text("Lihat Keranjang")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            val isTablet = maxWidth > 600.dp

            if (isTablet) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
                        ProductCatalog(products = products, onProductClick = { viewModel.addToCart(it) })
                    }
                    Divider(modifier = Modifier.fillMaxHeight().width(1.dp))
                    Box(modifier = Modifier.weight(0.4f).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                        CartList(
                            cart = cart, 
                            totalPrice = totalPrice,
                            onAdd = { viewModel.addToCart(it) },
                            onRemove = { viewModel.removeFromCart(it) },
                            onDelete = { viewModel.deleteItem(it) },
                            onCheckout = { viewModel.clearCart() }
                        )
                    }
                }
            } else {
                if (showCartOnPhone) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TextButton(onClick = { showCartOnPhone = false }, modifier = Modifier.padding(8.dp)) {
                            Text("< Kembali ke Katalog")
                        }
                        CartList(
                            cart = cart,
                            totalPrice = totalPrice,
                            onAdd = { viewModel.addToCart(it) },
                            onRemove = { viewModel.removeFromCart(it) },
                            onDelete = { viewModel.deleteItem(it) },
                            onCheckout = { 
                                viewModel.clearCart()
                                showCartOnPhone = false 
                            }
                        )
                    }
                } else {
                    ProductCatalog(products = products, onProductClick = { viewModel.addToCart(it) })
                }
            }
        }
    }
}

@Composable
fun ProductCatalog(products: List<com.karyaku.kasir.data.local.ProductEntity>, onProductClick: (com.karyaku.kasir.data.local.ProductEntity) -> Unit) {
    if (products.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Belum ada produk. Tekan tombol + di pojok kanan bawah untuk menambah data dummy.")
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(products) { product ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable { onProductClick(product) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = product.name,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Rp ${product.price}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(text = "Stok: ${product.stock}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun CartList(
    cart: List<CartItem>,
    totalPrice: Long,
    onAdd: (com.karyaku.kasir.data.local.ProductEntity) -> Unit,
    onRemove: (com.karyaku.kasir.data.local.ProductEntity) -> Unit,
    onDelete: (com.karyaku.kasir.data.local.ProductEntity) -> Unit,
    onCheckout: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Keranjang Belanja", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cart) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.product.name, fontWeight = FontWeight.Bold)
                            Text("Rp ${item.product.price}", style = MaterialTheme.typography.bodyMedium)
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onRemove(item.product) }) {
                                Icon(Icons.Default.Remove, contentDescription = "Kurang")
                            }
                            Text(
                                text = "${item.quantity}",
                                modifier = Modifier.padding(horizontal = 8.dp),
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { onAdd(item.product) }) {
                                Icon(Icons.Default.Add, contentDescription = "Tambah")
                            }
                            IconButton(onClick = { onDelete(item.product) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Total:", style = MaterialTheme.typography.titleLarge)
            Text("Rp $totalPrice", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCheckout,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = cart.isNotEmpty()
        ) {
            Text("PROSES PEMBAYARAN", fontSize = 16.sp)
        }
    }
}