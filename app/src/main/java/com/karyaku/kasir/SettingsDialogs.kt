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
import com.karyaku.kasir.data.local.RawMaterialEntity
import com.karyaku.kasir.data.local.ShiftEntity
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