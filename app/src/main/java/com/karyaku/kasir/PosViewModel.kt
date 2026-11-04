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

class PosViewModel(
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val _products = MutableStateFlow<List<ProductEntity>>(emptyList())
    val products: StateFlow<List<ProductEntity>> = _products.asStateFlow()

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val transactions: StateFlow<List<TransactionEntity>> = _transactions.asStateFlow()

    init {
        viewModelScope.launch {
            productDao.getAllProducts().collectLatest { productList ->
                _products.value = productList
            }
        }
        viewModelScope.launch {
            transactionDao.getAllTransactions().collectLatest { txList ->
                _transactions.value = txList
            }
        }
    }

    fun addProduct(name: String, price: Long, stock: Int) {
        viewModelScope.launch {
            val newProduct = ProductEntity(name = name, price = price, stock = stock)
            productDao.insertProduct(newProduct)
        }
    }

    // FUNGSI BARU: Hapus produk
    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            productDao.deleteProduct(product)
        }
    }

    fun addToCart(product: ProductEntity) {
        val currentCart = _cart.value.toMutableList()
        val existingItemIndex = currentCart.indexOfFirst { it.product.id == product.id }

        if (existingItemIndex != -1) {
            val existingItem = currentCart[existingItemIndex]
            // Cek agar tidak melebihi stok yang ada
            if (existingItem.quantity < existingItem.product.stock) {
                currentCart[existingItemIndex] = existingItem.copy(quantity = existingItem.quantity + 1)
            }
        } else {
            if (product.stock > 0) {
                currentCart.add(CartItem(product))
            }
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

    // FUNGSI YANG DIPERBAIKI: Mengurangi stok saat bayar
    fun processPayment(cashReceived: Long, onSuccess: (Long) -> Unit) {
        val total = getTotalPrice()
        val change = if (cashReceived >= total) cashReceived - total else 0

        val transaction = TransactionEntity(
            totalPrice = total,
            cashReceived = cashReceived,
            changeAmount = change
        )

        viewModelScope.launch {
            // 1. KURANGI STOK PRODUK
            _cart.value.forEach { cartItem ->
                val sisaStok = (cartItem.product.stock - cartItem.quantity).coerceAtLeast(0)
                productDao.updateStock(cartItem.product.id, sisaStok)
            }

            // 2. Simpan Transaksi
            val newId = transactionDao.insertTransaction(transaction)
            
            // 3. Kosongkan keranjang
            _cart.value = emptyList()
            
            onSuccess(newId)
        }
    }
}

class PosViewModelFactory(
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PosViewModel(productDao, transactionDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}