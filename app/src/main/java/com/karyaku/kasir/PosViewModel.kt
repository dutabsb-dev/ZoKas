package com.karyaku.kasir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
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
    private val transactionDao: TransactionDao,
    private val userDao: UserDao
) : ViewModel() {

    private val _products = MutableStateFlow<List<ProductEntity>>(emptyList())
    val products: StateFlow<List<ProductEntity>> = _products.asStateFlow()

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val transactions: StateFlow<List<TransactionEntity>> = _transactions.asStateFlow()

    init {
        viewModelScope.launch {
            // Buat Akun Default jika belum ada user sama sekali
            val users = userDao.getAllUsersOnce()
            if (users.isEmpty()) {
                val ownerId = userDao.insertUser(UserEntity(name = "Owner", pin = "123456", role = "OWNER"))
                val cashierId = userDao.insertUser(UserEntity(name = "Kasir 1", pin = "111111", role = "CASHIER"))
            }
        }
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

    // FUNGSI LOGIN
    suspend fun login(pin: String): UserEntity? {
        val user = userDao.loginByPin(pin)
        if (user != null) {
            // Catat ke Audit Trail
            userDao.insertAuditLog(AuditLogEntity(userId = user.id, userName = user.name, action = "LOGIN_SUCCESS"))
        }
        return user
    }

    fun addProduct(name: String, price: Long, stock: Int, userId: Long, userName: String) {
        viewModelScope.launch {
            val newProduct = ProductEntity(name = name, price = price, stock = stock)
            productDao.insertProduct(newProduct)
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "ADD_PRODUCT: $name"))
        }
    }

    fun updateProduct(product: ProductEntity, userId: Long, userName: String) {
        viewModelScope.launch {
            productDao.insertProduct(product)
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "EDIT_PRODUCT: ${product.name}"))
        }
    }

    fun deleteProduct(product: ProductEntity, userId: Long, userName: String) {
        viewModelScope.launch {
            productDao.deleteProduct(product)
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "DELETE_PRODUCT: ${product.name}"))
        }
    }

    fun addToCart(product: ProductEntity) {
        val currentCart = _cart.value.toMutableList()
        val existingItemIndex = currentCart.indexOfFirst { it.product.id == product.id }

        if (existingItemIndex != -1) {
            val existingItem = currentCart[existingItemIndex]
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

    fun processPayment(cashReceived: Long, userId: Long, userName: String, onSuccess: (Long) -> Unit) {
        val total = getTotalPrice()
        val change = if (cashReceived >= total) cashReceived - total else 0

        val transaction = TransactionEntity(
            totalPrice = total,
            cashReceived = cashReceived,
            changeAmount = change
        )

        viewModelScope.launch {
            _cart.value.forEach { cartItem ->
                val sisaStok = (cartItem.product.stock - cartItem.quantity).coerceAtLeast(0)
                productDao.updateStock(cartItem.product.id, sisaStok)
            }
            val newId = transactionDao.insertTransaction(transaction)
            userDao.insertAuditLog(AuditLogEntity(userId = userId, userName = userName, action = "CHECKOUT: Rp $total"))
            _cart.value = emptyList()
            onSuccess(newId)
        }
    }
}

class PosViewModelFactory(
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao,
    private val userDao: UserDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PosViewModel(productDao, transactionDao, userDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}