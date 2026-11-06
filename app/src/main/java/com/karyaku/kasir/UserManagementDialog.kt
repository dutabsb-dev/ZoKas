package com.karyaku.kasir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.karyaku.kasir.data.local.UserEntity

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
                if (userToEdit == null) {
                    onAddUser(name, pin, role)
                } else {
                    onUpdateUser(userToEdit!!.copy(name = name, pin = pin, role = role))
                }
                showForm = false
                userToEdit = null
            }
        )
    }

    if (userToDelete != null) {
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Hapus Karyawan") },
            text = { Text("Apakah Anda yakin ingin menghapus akun '${userToDelete!!.name}'? Karyawan ini tidak akan bisa login lagi.") },
            confirmButton = {
                TextButton(onClick = { 
                    onDeleteUser(userToDelete!!)
                    userToDelete = null 
                }) { Text("Hapus", color = Color.Red, fontWeight = FontWeight.Bold) }
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
                    colors = ButtonDefaults.buttonColors(containerColor = ZokasGreen)
                ) {
                    Text("+ Tambah Karyawan Baru")
                }
                
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
                                    Text(user.role, fontSize = 12.sp, color = if (user.role == "OWNER") ZokasOrange else Color.Gray)
                                }
                                Row {
                                    IconButton(onClick = { userToEdit = user; showForm = true }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = ZokasGreen)
                                    }
                                    // Hanya Owner yang bisa menghapus, dan tidak bisa menghapus dirinya sendiri
                                    if (currentUser.role == "OWNER" && currentUser.id != user.id) {
                                        IconButton(onClick = { userToDelete = user }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
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
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Karyawan") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6) pin = it },
                    label = { Text("PIN (6-digit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Hanya Owner yang bisa mengubah Role
                if (currentUser.role == "OWNER") {
                    Text("Pilih Role:", fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(
                            onClick = { role = "CASHIER" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (role == "CASHIER") ZokasGreen else Color.Gray)
                        ) { Text("Kasir") }
                        Button(
                            onClick = { role = "OWNER" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (role == "OWNER") ZokasOrange else Color.Gray)
                        ) { Text("Owner") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (name.isNotBlank() && pin.length == 6) onSave(name, pin, role) 
                },
                enabled = name.isNotBlank() && pin.length == 6,
                colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)
            ) { Text("SIMPAN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}