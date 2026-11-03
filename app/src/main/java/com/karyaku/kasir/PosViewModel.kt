package com.karyaku.kasir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.ProductDao
import com.karyaku.kasir.data.local.ProductEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Model untuk item di dalam keranjang
data class CartItem(
    val product: ProductEntity,
    var quantity: Int = 1
)

class PosViewModel(private val productDao: ProductDao) : ViewModel() {

    // State untuk daftar produk dari database
    private val _products = MutableStateFlow<List<ProductEntity>>(emptyList())
    val products: StateFlow<List<ProductEntity>> = _products.asStateFlow()

    // State untuk keranjang belanja
    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    init {
        // Mengambil data produk dari database secara otomatis (Realtime)
        viewModelScope.launch {
            productDao.getAllProducts().collectLatest { productList ->
                _products.value = productList
            }
        }
    }

    // Fungsi saat produk ditekan
    fun addToCart(product: ProductEntity) {
        val currentCart = _cart.value.toMutableList()
        val existingItemIndex = currentCart.indexOfFirst { it.product.id == product.id }

        if (existingItemIndex != -1) {
            // Jika sudah ada, tambah kuantitasnya
            val existingItem = currentCart[existingItemIndex]
            currentCart[existingItemIndex] = existingItem.copy(quantity = existingItem.quantity + 1)
        } else {
            // Jika belum ada, masukkan sebagai item baru
            currentCart.add(CartItem(product))
        }
        _cart.value = currentCart
    }

    // Fungsi mengurangi kuantitas
    fun removeFromCart(product: ProductEntity) {
        val currentCart = _cart.value.toMutableList()
        val existingItemIndex = currentCart.indexOfFirst { it.product.id == product.id }

        if (existingItemIndex != -1) {
            val existingItem = currentCart[existingItemIndex]
            if (existingItem.quantity > 1) {
                currentCart[existingItemIndex] = existingItem.copy(quantity = existingItem.quantity - 1)
            } else {
                currentCart.removeAt(existingItemIndex)
            }
            _cart.value = currentCart
        }
    }

    // Fungsi untuk menghapus item sepenuhnya
    fun deleteItem(product: ProductEntity) {
        val currentCart = _cart.value.toMutableList()
        currentCart.removeAll { it.product.id == product.id }
        _cart.value = currentCart
    }

    // Fungsi menghitung total harga
    fun getTotalPrice(): Long {
        return _cart.value.sumOf { it.product.price * it.quantity }
    }
    
    // Fungsi untuk mengosongkan keranjang setelah bayar
    fun clearCart() {
        _cart.value = emptyList()
    }
}

// Factory untuk membuat ViewModel dengan parameter Database
class PosViewModelFactory(private val productDao: ProductDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PosViewModel(productDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}