package com.karyaku.kasir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.ProductDao
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.TransactionDao
import com.karyaku.kasir.data.local.TransactionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class CartItem(
    val product: ProductEntity,
    var quantity: Int = 1
)

// Tambahkan TransactionDao di parameter ViewModel
class PosViewModel(
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val _products = MutableStateFlow<List<ProductEntity>>(emptyList())
    val products: StateFlow<List<ProductEntity>> = _products.asStateFlow()

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    init {
        viewModelScope.launch {
            productDao.getAllProducts().collectLatest { productList ->
                _products.value = productList
            }
        }
    }

    fun addDummyProduct() {
        viewModelScope.launch {
            val dummyNames = listOf("Kopi Susu", "Roti Bakar", "Nasi Goreng", "Es Teh Manis", "Kentang Goreng")
            val randomName = dummyNames.random()
            val dummyProduct = ProductEntity(
                name = "$randomName ${(1..100).random()}",
                price = listOf(15000L, 20000L, 25000L, 5000L).random(),
                stock = 50
            )
            productDao.insertProduct(dummyProduct)
        }
    }

    fun addToCart(product: ProductEntity) {
        val currentCart = _cart.value.toMutableList()
        val existingItemIndex = currentCart.indexOfFirst { it.product.id == product.id }

        if (existingItemIndex != -1) {
            val existingItem = currentCart[existingItemIndex]
            currentCart[existingItemIndex] = existingItem.copy(quantity = existingItem.quantity + 1)
        } else {
            currentCart.add(CartItem(product))
        }
        _cart.value = currentCart
    }

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

    fun deleteItem(product: ProductEntity) {
        val currentCart = _cart.value.toMutableList()
        currentCart.removeAll { it.product.id == product.id }
        _cart.value = currentCart
    }

    fun getTotalPrice(): Long {
        return _cart.value.sumOf { it.product.price * it.quantity }
    }

    // FUNGSI BARU: Proses Pembayaran dan Simpan ke Database
    fun processPayment(cashReceived: Long, onSuccess: () -> Unit) {
        val total = getTotalPrice()
        val change = if (cashReceived >= total) cashReceived - total else 0

        val transaction = TransactionEntity(
            totalPrice = total,
            cashReceived = cashReceived,
            changeAmount = change
        )

        viewModelScope.launch {
            // Simpan ke database
            transactionDao.insertTransaction(transaction)
            
            // Kosongkan keranjang
            _cart.value = emptyList()
            
            // Kembali ke UI
            onSuccess()
        }
    }
}

class PosViewModelFactory(
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao // Tambahkan ini
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PosViewModel(productDao, transactionDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}