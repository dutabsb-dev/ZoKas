package com.karyaku.kasir

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karyaku.kasir.data.local.ExpenseEntity
import com.karyaku.kasir.data.local.MembershipEntity
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.PurchaseEntity
import com.karyaku.kasir.data.local.PurchaseItemEntity
import com.karyaku.kasir.data.local.RawMaterialEntity
import com.karyaku.kasir.data.local.ReservationEntity
import com.karyaku.kasir.data.local.ShiftEntity
import com.karyaku.kasir.data.local.SupplierEntity
import com.karyaku.kasir.data.local.TransactionEntity
import com.karyaku.kasir.data.local.UserEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChangePinDialog(
    user: UserEntity, 
    onDismiss: () -> Unit, 
    onSave: (String) -> Unit
) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah PIN Keamanan") },
        text = {
            Column {
                Text("Masukkan PIN baru (6 digit):")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPin, 
                    onValueChange = { if (it.length <= 6) { newPin = it; error = "" } }, 
                    label = { Text("PIN Baru") }, 
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), 
                    visualTransformation = PasswordVisualTransformation(), 
                    modifier = Modifier.fillMaxWidth(), 
                    singleLine = true, 
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin, 
                    onValueChange = { if (it.length <= 6) { confirmPin = it; error = "" } }, 
                    label = { Text("Konfirmasi PIN Baru") }, 
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), 
                    visualTransformation = PasswordVisualTransformation(), 
                    modifier = Modifier.fillMaxWidth(), 
                    singleLine = true, 
                    isError = error.isNotEmpty(), 
                    shape = RoundedCornerShape(12.dp)
                )
                if (error.isNotEmpty()) { 
                    Text(error, color = ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) 
                }
            }
        },
        confirmButton = { 
            Button(onClick = { 
                if (newPin.length == 6 && newPin == confirmPin) { onSave(newPin) } 
                else { error = "PIN harus 6 digit dan sama!" } 
            }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)) { Text("SIMPAN PIN") } 
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun UserManagementDialog(
    users: List<UserEntity>,
    currentUser: UserEntity,
    onDismiss: () -> Unit,
    onAddUser: (String, String, String) -> Unit,
    onUpdateUser: (UserEntity) -> Unit,
    onDeleteUser: (UserEntity) -> Unit
) {
    var showForm by remember { mutableStateOf(false) }
    var userToEdit by remember { mutableStateOf<UserEntity?>(null) }
    var userToDelete by remember { mutableStateOf<UserEntity?>(null) }

    if (showForm) {
        UserFormDialog(
            userToEdit = userToEdit,
            currentUser = currentUser,
            onDismiss = { showForm = false; userToEdit = null },
            onSave = { name, pin, role ->
                if (userToEdit == null) onAddUser(name, pin, role)
                else onUpdateUser(userToEdit!!.copy(name = name, pin = pin, role = role))
                showForm = false
                userToEdit = null
            }
        )
    }

    if (userToDelete != null) {
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Hapus Karyawan") },
            text = { Text("Apakah Anda yakin ingin menghapus akun '${userToDelete!!.name}'?") },
            confirmButton = {
                TextButton(onClick = { onDeleteUser(userToDelete!!); userToDelete = null }) { 
                    Text("Hapus", color = Color.Red, fontWeight = FontWeight.Bold) 
                }
            },
            dismissButton = { TextButton(onClick = { userToDelete = null }) { Text("Batal") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kelola Karyawan") },
        text = {
            Column {
                Button(
                    onClick = { userToEdit = null; showForm = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) { Text("+ Tambah Karyawan Baru") }
                
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(users) { user ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user.name, fontWeight = FontWeight.Bold)
                                    Text(user.role, fontSize = 12.sp, color = if (user.role == "OWNER") AmberAccent else SlateGrey)
                                }
                                Row {
                                    IconButton(onClick = { userToEdit = user; showForm = true }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = EmeraldGreen)
                                    }
                                    if (currentUser.role == "OWNER" && currentUser.id != user.id) {
                                        IconButton(onClick = { userToDelete = user }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = ErrorRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
fun UserFormDialog(
    userToEdit: UserEntity?,
    currentUser: UserEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(userToEdit?.name ?: "") }
    var pin by remember { mutableStateOf(userToEdit?.pin ?: "") }
    var role by remember { mutableStateOf(userToEdit?.role ?: "CASHIER") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (userToEdit == null) "Tambah Karyawan" else "Edit Karyawan") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, 
                    label = { Text("Nama Karyawan") }, 
                    modifier = Modifier.fillMaxWidth(), singleLine = true, 
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin, onValueChange = { if (it.length <= 6) pin = it }, 
                    label = { Text("PIN (6-digit)") }, 
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), 
                    visualTransformation = PasswordVisualTransformation(), 
                    modifier = Modifier.fillMaxWidth(), singleLine = true, 
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                if (currentUser.role == "OWNER") {
                    Text("Pilih Role:", fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(
                            onClick = { role = "CASHIER" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (role == "CASHIER") EmeraldGreen else SlateGrey),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Kasir") }
                        Button(
                            onClick = { role = "OWNER" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (role == "OWNER") AmberAccent else SlateGrey),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Owner") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank() && pin.length == 6) onSave(name, pin, role) },
                enabled = name.isNotBlank() && pin.length == 6,
                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                shape = RoundedCornerShape(12.dp)
            ) { Text("SIMPAN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun ShiftHistoryDialog(
    shifts: List<ShiftEntity>,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Riwayat Shift Kasir") },
        text = {
            if (shifts.isEmpty()) {
                Text("Belum ada riwayat shift.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(shifts) { shift ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Kasir: ${shift.userName}", fontWeight = FontWeight.Bold)
                                Text("Mulai: ${dateFormat.format(Date(shift.startTime))}", fontSize = 12.sp)
                                if (shift.endTime != null) {
                                    Text("Selesai: ${dateFormat.format(Date(shift.endTime))}", fontSize = 12.sp)
                                    Text("Modal: ${formatRupiah(shift.openingCash)}", fontSize = 12.sp)
                                    Text("Sistem: ${formatRupiah(shift.expectedCash)}", fontSize = 12.sp)
                                    Text("Fisik: ${formatRupiah(shift.closingCash ?: 0)}", fontSize = 12.sp)
                                    val selisih = shift.discrepancy ?: 0
                                    Text("Selisih: ${formatRupiah(selisih)}", color = if (selisih < 0) ErrorRed else EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                } else {
                                    Text("Status: MASIH BERJALAN", color = AmberAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
fun RawMaterialManagementDialog(
    rawMaterials: List<RawMaterialEntity>,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("gram") }
    var cost by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kelola Bahan Baku") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Bahan (cth: Biji Kopi)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = stock, onValueChange = { if (it.length <= 10) stock = it }, label = { Text("Stok Awal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Satuan") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = cost, onValueChange = { if (it.length <= 10) cost = it }, label = { Text("Harga Modal / Satuan (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Daftar Bahan Baku:", fontWeight = FontWeight.Bold)
                rawMaterials.forEach { material ->
                    Text("- ${material.name}: ${material.stock} ${material.unit}", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = stock.toDoubleOrNull() ?: 0.0
                    val c = cost.toLongOrNull() ?: 0L
                    if (name.isNotBlank()) {
                        viewModel.addRawMaterial(name, s, unit, c)
                        name = ""
                        stock = ""
                        cost = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(12.dp)
            ) { Text("TAMBAH BAHAN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
fun MembershipManagementDialog(
    memberships: List<MembershipEntity>,
    onAddMembership: (String, Long, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var quota by remember { mutableStateOf("") }
    var validDays by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kelola Paket Langganan") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Paket (cth: Cuci Motor 10x)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = price, onValueChange = { if (it.length <= 10) price = it }, label = { Text("Harga Paket (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = quota, onValueChange = { if (it.length <= 5) quota = it }, label = { Text("Kuota (x)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = validDays, onValueChange = { if (it.length <= 5) validDays = it }, label = { Text("Masa Aktif (Hari)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Daftar Paket:", fontWeight = FontWeight.Bold)
                memberships.forEach { m ->
                    Text("- ${m.name}: ${m.quotaCount}x (Rp ${m.price})", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = price.toLongOrNull() ?: 0L
                    val q = quota.toIntOrNull() ?: 0
                    val vd = validDays.toIntOrNull() ?: 30
                    if (name.isNotBlank() && p > 0 && q > 0) {
                        onAddMembership(name, p, q, vd)
                        name = ""; price = ""; quota = ""; validDays = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(12.dp)
            ) { Text("TAMBAH PAKET") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
fun SupplierManagementDialog(
    suppliers: List<SupplierEntity>,
    onAddSupplier: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kelola Supplier") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Supplier / PT") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = contact, onValueChange = { contact = it }, label = { Text("Nama Kontak (Sales)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("No. HP / Telp") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Alamat") }, modifier = Modifier.fillMaxWidth(), maxLines = 2, shape = RoundedCornerShape(12.dp))
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Daftar Supplier:", fontWeight = FontWeight.Bold)
                suppliers.forEach { s ->
                    Text("- ${s.name} (${s.phone})", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onAddSupplier(name, contact, phone, address)
                        name = ""; contact = ""; phone = ""; address = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(12.dp)
            ) { Text("TAMBAH SUPPLIER") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
fun PurchaseOrderDialog(
    suppliers: List<SupplierEntity>,
    products: List<ProductEntity>,
    onCreatePO: (SupplierEntity, String, List<PurchaseItemEntity>, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSupplier by remember { mutableStateOf<SupplierEntity?>(null) }
    var invoiceNumber by remember { mutableStateOf("") }
    var isPaid by remember { mutableStateOf(false) }
    var updateCostPrice by remember { mutableStateOf(true) }
    
    var selectedProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var qty by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("") }
    
    val poItems = remember { mutableStateListOf<PurchaseItemEntity>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat Pembelian (PO)") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Pilih Supplier:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                suppliers.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedSupplier = s }) {
                        RadioButton(selected = selectedSupplier?.id == s.id, onClick = { selectedSupplier = s })
                        Text(s.name)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = invoiceNumber, onValueChange = { invoiceNumber = it }, label = { Text("No. Invoice / Nota") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Tambah Barang:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                
                var expandedProd by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expandedProd = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedProduct?.name ?: "Pilih Produk...")
                    }
                    DropdownMenu(expanded = expandedProd, onDismissRequest = { expandedProd = false }) {
                        products.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                onClick = { 
                                    selectedProduct = p
                                    costPrice = p.costPrice.toString()
                                    expandedProd = false 
                                }
                            )
                        }
                    }
                }
                
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qty, onValueChange = { if (it.length <= 5) qty = it }, label = { Text("Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = costPrice, onValueChange = { if (it.length <= 10) costPrice = it }, label = { Text("Harga Modal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                }
                
                Button(
                    onClick = {
                        val p = selectedProduct
                        val q = qty.toIntOrNull() ?: 0
                        val c = costPrice.toLongOrNull() ?: 0L
                        if (p != null && q > 0 && c > 0) {
                            poItems.add(PurchaseItemEntity(purchaseId = 0, productId = p.id, productName = p.name, costPrice = c, quantity = q, subtotal = c * q))
                            selectedProduct = null; qty = ""; costPrice = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = selectedProduct != null && qty.isNotBlank() && costPrice.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent)
                ) { Text("Tambahkan ke Daftar") }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Daftar Belanja:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                poItems.forEachIndexed { index, item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${item.quantity}x ${item.productName} @ ${item.costPrice}", fontSize = 12.sp)
                        IconButton(onClick = { poItems.removeAt(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = ErrorRed)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isPaid = !isPaid }) {
                    Checkbox(checked = isPaid, onCheckedChange = { isPaid = it })
                    Text("Lunas (Bayar Cash)", fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { updateCostPrice = !updateCostPrice }) {
                    Checkbox(checked = updateCostPrice, onCheckedChange = { updateCostPrice = it })
                    Text("Update Harga Modal di Produk", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedSupplier != null && poItems.isNotEmpty()) {
                        onCreatePO(selectedSupplier!!, invoiceNumber, poItems.toList(), isPaid, updateCostPrice)
                    }
                },
                enabled = selectedSupplier != null && poItems.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(12.dp)
            ) { Text("SIMPAN PO") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun ReservationManagementDialog(
    reservations: List<ReservationEntity>,
    onAddReservation: (String, String, Long, String, String) -> Unit,
    onUpdateStatus: (Long, String) -> Unit,
    onDeleteReservation: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    
    var customerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var resourceName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var dateTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateTime)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selected = datePickerState.selectedDateMillis ?: dateTime
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = selected }
                    val currentCal = java.util.Calendar.getInstance().apply { timeInMillis = dateTime }
                    cal.set(java.util.Calendar.HOUR_OF_DAY, currentCal.get(java.util.Calendar.HOUR_OF_DAY))
                    cal.set(java.util.Calendar.MINUTE, currentCal.get(java.util.Calendar.MINUTE))
                    dateTime = cal.timeInMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Batal") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = java.util.Calendar.getInstance().apply { timeInMillis = dateTime }.get(java.util.Calendar.HOUR_OF_DAY),
            initialMinute = java.util.Calendar.getInstance().apply { timeInMillis = dateTime }.get(java.util.Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Pilih Jam") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateTime }
                    cal.set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                    cal.set(java.util.Calendar.MINUTE, timePickerState.minute)
                    dateTime = cal.timeInMillis
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Batal") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (showAddForm) "Buat Reservasi Baru" else "Daftar Reservasi") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (showAddForm) {
                    OutlinedTextField(value = customerName, onValueChange = { customerName = it }, label = { Text("Nama Pelanggan") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("No. HP") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = resourceName, onValueChange = { resourceName = it }, label = { Text("Meja / Terapis / Ruangan") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                            Text(dateFormat.format(Date(dateTime)), fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                            Text(timeFormat.format(Date(dateTime)), fontSize = 12.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Catatan (Opsional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 2, shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Text("Jadwal Mendatang:", fontWeight = FontWeight.Bold)
                    if (reservations.isEmpty()) {
                        Text("Belum ada reservasi.", fontSize = 12.sp, color = SlateGrey)
                    }
                    reservations.forEach { res ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(res.customerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    val statusColor = when (res.status) {
                                        "ARRIVED" -> EmeraldGreen
                                        "CANCELLED" -> ErrorRed
                                        else -> AmberAccent
                                    }
                                    Surface(color = statusColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                        Text(res.status, fontSize = 10.sp, color = statusColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text("${dateFormat.format(Date(res.dateTime))} @ ${timeFormat.format(Date(res.dateTime))}", fontSize = 12.sp)
                                Text("Res: ${res.resourceName} | Telp: ${res.phone}", fontSize = 12.sp)
                                if (res.notes.isNotEmpty()) Text("Cat: ${res.notes}", fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (res.status == "BOOKED") {
                                        TextButton(onClick = { onUpdateStatus(res.id, "ARRIVED") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                                            Text("Hadir", color = EmeraldGreen, fontSize = 12.sp)
                                        }
                                        TextButton(onClick = { onUpdateStatus(res.id, "CANCELLED") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                                            Text("Batal", color = ErrorRed, fontSize = 12.sp)
                                        }
                                    }
                                    IconButton(onClick = { onDeleteReservation(res.id) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = ErrorRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showAddForm) {
                Button(
                    onClick = {
                        if (customerName.isNotBlank() && resourceName.isNotBlank()) {
                            onAddReservation(customerName, phone, dateTime, resourceName, notes)
                            customerName = ""; phone = ""; resourceName = ""; notes = ""
                            showAddForm = false
                        }
                    },
                    enabled = customerName.isNotBlank() && resourceName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("SIMPAN RESERVASI") }
            } else {
                Button(onClick = { showAddForm = true }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen), shape = RoundedCornerShape(12.dp)) {
                    Text("+ Buat Reservasi")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { 
                if (showAddForm) showAddForm = false else onDismiss() 
            }) { Text(if (showAddForm) "Kembali" else "Tutup") }
        }
    )
}

// --- MODUL FINANCE BARU ---

@Composable
fun ExpenseManagementDialog(
    expenses: List<ExpenseEntity>,
    onAddExpense: (String, Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var category by remember { mutableStateOf("Operasional") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengeluaran Operasional") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Kategori (Gaji, Listrik, dll)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = amount, onValueChange = { if (it.length <= 10) amount = it }, label = { Text("Nominal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Keterangan") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Riwayat Pengeluaran:", fontWeight = FontWeight.Bold)
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(expenses) { exp ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("- ${exp.category} (${exp.note})", fontSize = 12.sp)
                            Text(formatRupiah(exp.amount), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toLongOrNull() ?: 0L
                    if (amt > 0 && category.isNotBlank()) {
                        onAddExpense(category, amt, note)
                        amount = ""
                        note = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(12.dp)
            ) { Text("SIMPAN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
fun ReceivableDialog(
    transactions: List<TransactionEntity>,
    onPay: (TransactionEntity, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var cashInput by remember { mutableStateOf("") }
    val unpaid = transactions.filter { it.status == "UNPAID" }
    
    if (selectedTx != null) {
        AlertDialog(
            onDismissRequest = { selectedTx = null },
            title = { Text("Pelunasan Bon") },
            text = {
                Column {
                    Text("Pelanggan: ${selectedTx!!.customerName}", fontWeight = FontWeight.Bold)
                    Text("Total Tagihan: ${formatRupiah(selectedTx!!.totalPrice)}", color = ErrorRed, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = cashInput, onValueChange = { cashInput = it }, label = { Text("Uang Diterima (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cash = cashInput.toLongOrNull() ?: 0L
                    if (cash >= selectedTx!!.totalPrice) {
                        onPay(selectedTx!!, cash)
                        selectedTx = null
                        cashInput = ""
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)) { Text("BAYAR") }
            },
            dismissButton = { TextButton(onClick = { selectedTx = null }) { Text("Batal") } }
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buku Piutang (Bon)") },
        text = {
            if (unpaid.isEmpty()) {
                Text("Tidak ada bon yang belum dibayar.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(unpaid) { tx ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedTx = tx },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(tx.customerName, fontWeight = FontWeight.Bold)
                                    Text(SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(tx.timestamp)), fontSize = 10.sp)
                                }
                                Text(formatRupiah(tx.totalPrice), fontWeight = FontWeight.Bold, color = ErrorRed)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
fun PayableDialog(
    purchases: List<PurchaseEntity>,
    onPay: (PurchaseEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val unpaid = purchases.filter { it.status == "UNPAID" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buku Hutang (PO)") },
        text = {
            if (unpaid.isEmpty()) {
                Text("Tidak ada hutang ke supplier.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(unpaid) { po ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(po.supplierName, fontWeight = FontWeight.Bold)
                                    Text("Nota: ${po.invoiceNumber}", fontSize = 10.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(formatRupiah(po.totalAmount), fontWeight = FontWeight.Bold, color = ErrorRed)
                                    TextButton(onClick = { onPay(po) }) {
                                        Text("LUNASI", fontSize = 10.sp, color = EmeraldGreen)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}