package com.karyaku.kasir

import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================
// FILE 4 — CART, PAYMENT, QRIS/TRANSFER, REFUND/VOID, HOLD ORDER
// ====================================================================
// Extension functions untuk PosViewModel.
// ====================================================================

// ================================================================
// CART MANAGEMENT
// ================================================================
fun PosViewModel.addToCart(product: ProductEntity, variation: String = "") {
    val currentCart = _cart.value.toMutableList()
    val idx = currentCart.indexOfFirst { it.product.id == product.id && it.selectedVariation == variation }
    
    if (idx != -1) {
        val item = currentCart[idx]
        if (product.isService || item.quantity < item.product.stock) {
            currentCart[idx] = item.copy(quantity = item.quantity + 1)
        }
    } else {
        if (product.isService || product.stock > 0) {
            currentCart.add(CartItem(product, selectedVariation = variation))
        }
    }
    _cart.value = currentCart
}

fun PosViewModel.removeFromCart(product: ProductEntity, variation: String) {
    val currentCart = _cart.value.toMutableList()
    val idx = currentCart.indexOfFirst { it.product.id == product.id && it.selectedVariation == variation }
    if (idx != -1) {
        val item = currentCart[idx]
        if (item.quantity > 1) currentCart[idx] = item.copy(quantity = item.quantity - 1)
        else currentCart.removeAt(idx)
        _cart.value = currentCart
    }
}

fun PosViewModel.deleteItem(product: ProductEntity, variation: String) {
    _cart.value = _cart.value.toMutableList().also { it.removeAll { item -> item.product.id == product.id && item.selectedVariation == variation } }
}

fun PosViewModel.toggleSplitSelection(product: ProductEntity, variation: String) {
    val currentCart = _cart.value.toMutableList()
    val idx = currentCart.indexOfFirst { it.product.id == product.id && it.selectedVariation == variation }
    if (idx != -1) {
        val item = currentCart[idx]
        currentCart[idx] = item.copy(isSelectedForSplit = !item.isSelectedForSplit)
        _cart.value = currentCart
    }
}

fun PosViewModel.updateCartItemNotes(product: ProductEntity, variation: String, notes: String) {
    val currentCart = _cart.value.toMutableList()
    val idx = currentCart.indexOfFirst { it.product.id == product.id && it.selectedVariation == variation }
    if (idx != -1) { currentCart[idx] = currentCart[idx].copy(notes = notes); _cart.value = currentCart }
}

fun PosViewModel.updateCartItemSerial(product: ProductEntity, variation: String, serial: String) {
    val currentCart = _cart.value.toMutableList()
    val idx = currentCart.indexOfFirst { it.product.id == product.id && it.selectedVariation == variation }
    if (idx != -1) { currentCart[idx] = currentCart[idx].copy(serialNumber = serial); _cart.value = currentCart }
}

fun PosViewModel.updateCartItemStaff(product: ProductEntity, variation: String, staff: String) {
    val currentCart = _cart.value.toMutableList()
    val idx = currentCart.indexOfFirst { it.product.id == product.id && it.selectedVariation == variation }
    if (idx != -1) { currentCart[idx] = currentCart[idx].copy(assignedStaff = staff); _cart.value = currentCart }
}

// ================================================================
// HOLD ORDER (PARKIR MEJA)
// ================================================================
fun PosViewModel.saveHoldOrder(customerName: String) {
    if (_cart.value.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            _cart.value.forEach { item ->
                val jsonObj = JSONObject()
                jsonObj.put("productId", item.product.id)
                jsonObj.put("quantity", item.quantity)
                jsonObj.put("variation", item.selectedVariation)
                jsonObj.put("notes", item.notes)
                jsonObj.put("serial", item.serialNumber)
                jsonObj.put("staff", item.assignedStaff)
                jsonObj.put("membershipId", item.usedMembershipId ?: 0L)
                jsonObj.put("membershipName", item.usedMembershipName ?: "")
                jsonArray.put(jsonObj)
            }
            val holdOrder = HoldOrderEntity(tableName = _selectedTable.value, customerName = customerName, cartJson = jsonArray.toString())
            holdOrderDao.insertHoldOrder(holdOrder)
            withContext(Dispatchers.Main) {
                _cart.value = emptyList()
                _selectedTable.value = "Take-Away"
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.resumeHoldOrder(order: HoldOrderEntity) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val newCart = mutableListOf<CartItem>()
            val jsonArray = JSONArray(order.cartJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val productId = obj.getLong("productId")
                val qty = obj.getInt("quantity")
                val variation = obj.optString("variation", "")
                val notes = obj.optString("notes", "")
                val serial = obj.optString("serial", "")
                val staff = obj.optString("staff", "")
                val memId = obj.optLong("membershipId", 0L).takeIf { it > 0 }
                val memName = obj.optString("membershipName", "").takeIf { it.isNotEmpty() }
                val product = _products.value.find { it.id == productId }
                if (product != null) newCart.add(CartItem(product, qty, selectedVariation = variation, notes = notes, serialNumber = serial, assignedStaff = staff, usedMembershipId = memId, usedMembershipName = memName))
            }
            holdOrderDao.deleteHoldOrder(order.id)
            withContext(Dispatchers.Main) {
                _cart.value = newCart
                _selectedTable.value = order.tableName
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.mergeOrder(tableA: String, tableB: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val orderA = _holdOrders.value.find { it.tableName == tableA }
            val orderB = _holdOrders.value.find { it.tableName == tableB }
            
            if (orderA != null && orderB != null) {
                val jsonA = JSONArray(orderA.cartJson)
                val jsonB = JSONArray(orderB.cartJson)
                for (i in 0 until jsonB.length()) { jsonA.put(jsonB.getJSONObject(i)) }
                
                holdOrderDao.deleteHoldOrder(orderB.id)
                holdOrderDao.insertHoldOrder(orderA.copy(tableName = tableA, cartJson = jsonA.toString()))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

// ================================================================
// QRIS & TRANSFER MANUAL
// ================================================================
fun PosViewModel.generateQrisPayment(amount: Long, staticPayload: String) {
    if (staticPayload.isBlank()) return
    val dynamicQris = QrisHelper.generateDynamicQris(staticPayload, amount)
    _qrisDynamicString.value = dynamicQris
    _isQrisPaid.value = false
}

fun PosViewModel.resetQrisPayment() {
    _qrisDynamicString.value = ""
    _isQrisPaid.value = false
}

fun PosViewModel.generateTransferPayment(baseAmount: Long) {
    val uniqueCode = Random.nextInt(1, 999)
    _uniqueTransferAmount.value = baseAmount + uniqueCode
    _isTransferPaid.value = false
}

fun PosViewModel.resetTransferPayment() {
    _uniqueTransferAmount.value = 0
    _isTransferPaid.value = false
}

// ================================================================
// PRICING & PAYMENT
// ================================================================
fun PosViewModel.getTotalPrice(): Long {
    val subtotal = _cart.value.sumOf { item ->
        if (item.usedMembershipId != null) {
            0L
        } else if (item.product.wholesaleMinQty > 0 && item.quantity >= item.product.wholesaleMinQty && item.product.wholesalePrice > 0) {
            item.product.wholesalePrice * item.quantity
        } else {
            item.product.price * item.quantity
        }
    }
    val tax = (subtotal - _discount.value - _voucherDiscount.value) * (_taxPercent.value / 100.0)
    val service = (subtotal - _discount.value - _voucherDiscount.value) * (_serviceChargePercent.value / 100.0)
    return (subtotal + tax + service - _discount.value - _voucherDiscount.value).coerceAtLeast(0.0).toLong()
}

fun PosViewModel.processPayment(
    cashReceived: Long,
    userId: Long,
    userName: String,
    isSplitBill: Boolean = false,
    method: String = "CASH",
    maxDiscountCap: Long = 0L,
    onSuccess: (TransactionEntity) -> Unit
) {
    val itemsToProcess = if (isSplitBill) _cart.value.filter { it.isSelectedForSplit } else _cart.value
    if (itemsToProcess.isEmpty()) return

    var subtotal = 0L
    var totalCommission = 0L
    var promoDiscount = 0L

    itemsToProcess.forEach { item ->
        val itemPrice = if (item.usedMembershipId != null) {
            0L
        } else if (item.product.wholesaleMinQty > 0 && item.quantity >= item.product.wholesaleMinQty && item.product.wholesalePrice > 0) {
            item.product.wholesalePrice
        } else {
            item.product.price
        }
        val itemSubtotal = itemPrice * item.quantity
        subtotal += itemSubtotal
        totalCommission += (item.product.commissionPerItem * item.quantity)

        if (item.product.promoType == "BOGO" && item.quantity >= 2) {
            val freeItems = item.quantity / 2
            promoDiscount += freeItems * itemPrice
        } else if (item.product.promoType == "TIERED" && item.quantity >= item.product.promoMinQty) {
            promoDiscount += (itemSubtotal * item.product.promoValue) / 100
        }
    }

    var tierDiscount = 0L
    val customer = _selectedCustomer.value
    if (customer != null) {
        if (customer.points >= 1000) tierDiscount = (subtotal * 0.10).toLong()
        else if (customer.points >= 500) tierDiscount = (subtotal * 0.05).toLong()
        else if (customer.points >= 100) tierDiscount = (subtotal * 0.02).toLong()
    }

    var currentDiscount = if (isSplitBill) promoDiscount else (_discount.value + _voucherDiscount.value + tierDiscount + promoDiscount)
    
    if (maxDiscountCap > 0 && currentDiscount > maxDiscountCap) {
        currentDiscount = maxDiscountCap
    }

    val taxAmount = (subtotal - currentDiscount) * (_taxPercent.value / 100.0)
    val serviceChargeAmount = (subtotal - currentDiscount) * (_serviceChargePercent.value / 100.0)
    val total = (subtotal + taxAmount + serviceChargeAmount - currentDiscount).coerceAtLeast(0.0).toLong()

    val status = if (method == "BON") "UNPAID" else "SUCCESS"
    val change = if (method == "BON") 0L else if (cashReceived >= total) cashReceived - total else 0L
    val customerName = customer?.name ?: "Pelanggan Umum"
    val totalCost = itemsToProcess.sumOf { it.product.costPrice * it.quantity }

    val transaction = TransactionEntity(
        totalPrice = total,
        cashReceived = if (method == "BON") 0L else cashReceived,
        changeAmount = change,
        customerName = customerName,
        totalCost = totalCost,
        tableName = _selectedTable.value,
        orderType = _selectedOrderType.value,
        serviceCharge = serviceChargeAmount.toLong(),
        pickupDate = _pickupDate.value,
        voucherCode = if (isSplitBill) "" else _appliedVoucher.value,
        taxAmount = taxAmount.toLong(),
        paymentMethod = method,
        status = status,
        cashierName = userName,
        totalCommission = totalCommission,
        branchId = _currentBranchId.value,
        isSynced = false,
        updatedAt = System.currentTimeMillis()
    )

    viewModelScope.launch(Dispatchers.IO) {
        try {
            database.withTransaction {
                val newId = transactionDao.insertTransaction(transaction)

                val itemEntities = itemsToProcess.map { cartItem ->
                    val finalPrice = if (cartItem.usedMembershipId != null) {
                        0L
                    } else if (cartItem.product.wholesaleMinQty > 0 && cartItem.quantity >= cartItem.product.wholesaleMinQty && cartItem.product.wholesalePrice > 0) {
                        cartItem.product.wholesalePrice
                    } else {
                        cartItem.product.price
                    }
                    TransactionItemEntity(
                        transactionId = newId,
                        productId = cartItem.product.id,
                        productName = cartItem.product.name,
                        price = finalPrice,
                        quantity = cartItem.quantity,
                        subtotal = finalPrice * cartItem.quantity,
                        costPrice = cartItem.product.costPrice,
                        notes = if (cartItem.selectedVariation.isNotEmpty()) "Varian: ${cartItem.selectedVariation} | ${cartItem.notes}" else cartItem.notes,
                        serialNumber = cartItem.serialNumber,
                        assignedStaff = cartItem.assignedStaff,
                        isService = cartItem.product.isService
                    )
                }
                transactionItemDao.insertTransactionItems(itemEntities)

                itemsToProcess.forEach { item ->
                    if (!item.product.isService && !item.product.isCombo) {
                        val product = _products.value.find { it.id == item.product.id }
                        if (product != null) {
                            val sisa = (product.stock - item.quantity).coerceAtLeast(0)
                            productDao.updateStock(product.id, sisa)
                        }
                        
                        val boms = productDao.getBomForProduct(item.product.id)
                        boms.forEach { bom ->
                            val material = _rawMaterials.value.find { it.id == bom.rawMaterialId }
                            if (material != null) {
                                val newStock = (material.stock - (bom.quantityUsed * item.quantity)).coerceAtLeast(0.0)
                                productDao.updateRawMaterialStock(material.id, newStock)
                            }
                        }
                    }
                    
                    if (item.usedMembershipId != null) {
                        val cm = _customerMemberships.value.find { it.id == item.usedMembershipId }
                        if (cm != null) {
                            val newQuota = (cm.remainingQuota - item.quantity).coerceAtLeast(0)
                            membershipDao.updateRemainingQuota(cm.id, newQuota)
                        }
                    }
                }

                if (!isSplitBill && _selectedCustomer.value != null && method != "BON") {
                    val earnedPoints = (total / 10000).toInt()
                    val finalPoints = (customer!!.points - _pointsUsed.value + earnedPoints).coerceAtLeast(0)
                    customerDao.updateCustomerPoints(customer.id, finalPoints)
                }

                if (totalCommission > 0) { userDao.addCommission(userId, totalCommission) }

                userDao.insertAuditLog(
                    AuditLogEntity(
                        userId = userId, userName = userName, 
                        action = "TRANSAKSI ($method) Cabang #${_currentBranchId.value}: ${formatRupiah(total)} ($customerName)",
                        actionType = "TRANSACTION"
                    )
                )
            }

            withContext(Dispatchers.Main) {
                if (isSplitBill) {
                    _cart.value = _cart.value.filter { !it.isSelectedForSplit }
                } else {
                    _cart.value = emptyList()
                    _discount.value = 0
                    _voucherDiscount.value = 0
                    _appliedVoucher.value = ""
                    _pointsUsed.value = 0
                    _selectedCustomer.value = null
                    _pickupDate.value = 0 
                    _customerMemberships.value = emptyList()
                }
                resetQrisPayment()
                resetTransferPayment()
                onSuccess(transaction.copy(id = transaction.id)) 
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun PosViewModel.payUnpaidTransaction(transaction: TransactionEntity, cashReceived: Long, onSuccess: () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val change = (cashReceived - transaction.totalPrice).coerceAtLeast(0L)
            val shift = _activeShift.value
            
            database.withTransaction {
                transactionDao.updateTransactionStatus(
                    transactionId = transaction.id,
                    cashReceived = cashReceived,
                    changeAmount = change,
                    paidMethod = "CASH"
                )

                if (shift != null) {
                    cashFlowDao.insertCashFlow(
                        CashFlowEntity(
                            shiftId = shift.id,
                            type = "IN",
                            amount = transaction.totalPrice,
                            note = "Pelunasan BON #${transaction.id} (${transaction.customerName})",
                            userId = shift.userId,
                            userName = shift.userName
                        )
                    )
                }

                userDao.insertAuditLog(
                    AuditLogEntity(
                        userId = shift?.userId ?: 0L,
                        userName = shift?.userName ?: "SYSTEM",
                        action = "PELUNASAN BON #${transaction.id} (${transaction.customerName}) sebesar ${formatRupiah(transaction.totalPrice)}",
                        actionType = "FINANCE"
                    )
                )
            }

            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun PosViewModel.refundTransaction(transaction: TransactionEntity, pin: String, currentUser: UserEntity, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val hashedPin = hashPin(pin)
            val supervisor = userDao.loginByPin(hashedPin)
            if (supervisor == null || (supervisor.role != "OWNER" && supervisor.role != "SUPERVISOR")) {
                withContext(Dispatchers.Main) { onResult(false, "PIN Salah atau bukan Supervisor/Owner!") }
                return@launch
            }

            val itemsToRestore = transactionItemDao.getItemsByTransaction(transaction.id).first()
            
            database.withTransaction {
                itemsToRestore.forEach { item ->
                    if (!item.isService) {
                        val product = _products.value.find { it.id == item.productId }
                        if (product != null) { 
                            productDao.updateStock(product.id, product.stock + item.quantity) 
                        }
                    }
                }

                transactionDao.refundTransaction(transaction.id)
                userDao.insertAuditLog(AuditLogEntity(
                    userId = currentUser.id, userName = currentUser.name, 
                    action = "REFUND TRANSAKSI #${transaction.id} (Oleh: ${supervisor.name})",
                    actionType = "TRANSACTION"
                ))
            }
            
            withContext(Dispatchers.Main) { onResult(true, "Refund berhasil!") }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal refund: ${e.message}") }
        }
    }
}

fun PosViewModel.voidTransaction(transaction: TransactionEntity, pinSupervisor: String, currentUser: UserEntity, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val hashedPin = hashPin(pinSupervisor)
            val supervisor = userDao.loginByPin(hashedPin)
            if (supervisor == null || (supervisor.role != "OWNER" && supervisor.role != "SUPERVISOR")) {
                withContext(Dispatchers.Main) { onResult(false, "PIN Salah atau bukan Supervisor!") }
                return@launch
            }

            val itemsToRestore = transactionItemDao.getItemsByTransaction(transaction.id).first()
            
            database.withTransaction {
                itemsToRestore.forEach { item ->
                    if (!item.isService) {
                        val product = _products.value.find { it.id == item.productId }
                        if (product != null) { 
                            productDao.updateStock(product.id, product.stock + item.quantity) 
                        }
                    }
                }

                transactionDao.voidTransaction(transaction.id)
                userDao.insertAuditLog(AuditLogEntity(
                    userId = currentUser.id, userName = currentUser.name, 
                    action = "VOID TRANSAKSI #${transaction.id} (Oleh Supervisor: ${supervisor.name})",
                    actionType = "TRANSACTION"
                ))
            }
            
            withContext(Dispatchers.Main) { onResult(true, "Transaksi berhasil di-void.") }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal void: ${e.message}") }
        }
    }
}

fun PosViewModel.logCashDrawerOpen(user: UserEntity) {
    viewModelScope.launch(Dispatchers.IO) { 
        try {
            userDao.insertAuditLog(AuditLogEntity(
                userId = user.id, userName = user.name, 
                action = "BUKA LACI MANUAL (No Sale)",
                actionType = "SYSTEM"
            )) 
        } catch (e: Exception) { e.printStackTrace() }
    }
}