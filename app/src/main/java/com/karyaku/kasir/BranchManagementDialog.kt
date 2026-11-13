package com.karyaku.kasir

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.BranchEntity
import com.karyaku.kasir.data.local.UserEntity

// ====================================================================
// MODUL D-1 — DIALOG KELOLA CABANG
// ====================================================================
// Container:
//   - List cabang (card per cabang)
//   - FAB tambah cabang
//   - Edit / delete per cabang (lewat tombol di card)
//   - Confirm dialog sebelum delete
// Validasi hapus (masih ada transaksi/produk) ditangani ViewModel —
// dialog hanya menampilkan pesan error dari ViewModel.
// ====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchManagementDialog(
    viewModel: PosViewModel,
    currentUser: UserEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val allBranches by viewModel.allBranches.collectAsStateWithLifecycle()
    val currentBranchId by viewModel.currentBranchId.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }
    var branchToEdit by remember { mutableStateOf<BranchEntity?>(null) }
    var branchToDelete by remember { mutableStateOf<BranchEntity?>(null) }

    // ================================================================
    // PROTEKSI ROLE — hanya OWNER
    // ================================================================
    if (currentUser.role != "OWNER") {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = ErrorRed) },
            title = { Text("Akses Ditolak", fontWeight = FontWeight.Bold) },
            text = { Text("Hanya OWNER yang bisa mengelola cabang.") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Tutup") }
            }
        )
        return
    }

    // ================================================================
    // FORM TAMBAH / EDIT CABANG
    // ================================================================
    if (showForm) {
        BranchFormDialog(
            branchToEdit = branchToEdit,
            onDismiss = {
                showForm = false
                branchToEdit = null
            },
            onSave = { name, address, phone ->
                if (branchToEdit == null) {
                    viewModel.addBranch(name, address, phone) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) {
                            showForm = false
                            branchToEdit = null
                        }
                    }
                } else {
                    viewModel.updateBranch(
                        branchToEdit!!.copy(
                            name = name,
                            address = address,
                            phone = phone
                        )
                    ) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) {
                            showForm = false
                            branchToEdit = null
                        }
                    }
                }
            }
        )
    }

    // ================================================================
    // CONFIRM DELETE
    // ================================================================
    if (branchToDelete != null) {
        AlertDialog(
            onDismissRequest = { branchToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed) },
            title = { Text("Hapus Cabang?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Cabang '${branchToDelete!!.name}' akan dihapus permanen.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Cabang yang masih punya transaksi atau produk tidak bisa dihapus. " +
                            "Pindahkan datanya dulu atau nonaktifkan manual di database.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBranch(branchToDelete!!) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            branchToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("HAPUS", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { branchToDelete = null }) { Text("Batal") }
            }
        )
    }

    // ================================================================
    // MAIN DIALOG
    // ================================================================
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // -------- TOP BAR --------
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kelola Cabang",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${allBranches.size} cabang terdaftar",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }

                    // -------- INFO CARD --------
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = EmeraldGreen.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Cabang yang masih punya transaksi atau produk tidak bisa dihapus. " +
                                    "Pindahkan data atau edit nama saja.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // -------- LIST CABANG --------
                    if (allBranches.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Store,
                                    contentDescription = null,
                                    tint = SlateGrey,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Belum ada cabang.",
                                    fontWeight = FontWeight.Bold,
                                    color = SlateGrey
                                )
                                Text(
                                    "Tambahkan cabang pertama dengan tombol +",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 96.dp // ruang untuk FAB
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(allBranches, key = { it.id }) { branch ->
                                BranchCard(
                                    branch = branch,
                                    isCurrent = branch.id == currentBranchId,
                                    onEdit = {
                                        branchToEdit = branch
                                        showForm = true
                                    },
                                    onDelete = { branchToDelete = branch }
                                )
                            }
                        }
                    }
                }

                // -------- FAB TAMBAH --------
                ExtendedFloatingActionButton(
                    onClick = {
                        branchToEdit = null
                        showForm = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = EmeraldGreen,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Tambah Cabang", fontWeight = FontWeight.Bold) }
                )
            }
        }
    }
}

// ====================================================================
// CARD CABANG
// ====================================================================
@Composable
private fun BranchCard(
    branch: BranchEntity,
    isCurrent: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent)
                EmeraldGreen.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Header baris 1: ikon, nama, badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (branch.isMainBranch)
                                AmberAccent.copy(alpha = 0.15f)
                            else
                                EmeraldGreen.copy(alpha = 0.15f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (branch.isMainBranch)
                            Icons.Default.Storefront
                        else
                            Icons.Default.Store,
                        contentDescription = null,
                        tint = if (branch.isMainBranch) AmberAccentDark else EmeraldGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = branch.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (branch.isMainBranch) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = AmberAccent.copy(alpha = 0.20f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "UTAMA",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberAccentDark,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (isCurrent) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = EmeraldGreen.copy(alpha = 0.20f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(EmeraldGreen, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "AKTIF",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldGreen
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = "ID: #${branch.id}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Info alamat & telepon
            if (branch.address.isNotBlank() || branch.phone.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                if (branch.address.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = branch.address,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (branch.phone.isNotBlank()) {
                    if (branch.address.isNotBlank()) Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = branch.phone,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Aksi
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    enabled = !branch.isMainBranch
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Hapus",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (branch.isMainBranch) {
                Text(
                    text = "Cabang utama tidak bisa dihapus.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// ====================================================================
// FORM TAMBAH / EDIT CABANG
// ====================================================================
@Composable
private fun BranchFormDialog(
    branchToEdit: BranchEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, address: String, phone: String) -> Unit
) {
    var name by remember { mutableStateOf(branchToEdit?.name ?: "") }
    var address by remember { mutableStateOf(branchToEdit?.address ?: "") }
    var phone by remember { mutableStateOf(branchToEdit?.phone ?: "") }
    var errorMessage by remember { mutableStateOf("") }

    val isEdit = branchToEdit != null

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isEdit) Icons.Default.Edit else Icons.Default.Add,
                contentDescription = null,
                tint = EmeraldGreen
            )
        },
        title = {
            Text(
                text = if (isEdit) "Edit Cabang" else "Tambah Cabang",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = ""
                    },
                    label = { Text("Nama Cabang *") },
                    leadingIcon = {
                        Icon(Icons.Default.Store, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    isError = errorMessage.isNotEmpty()
                )
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = ErrorRed,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Alamat") },
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("No. Telepon") },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                if (!isEdit) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Cabang baru bisa langsung diisi produk. Karyawan yang ditambahkan " +
                                "di cabang ini akan otomatis terhubung ke sini.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isBlank()) {
                        errorMessage = "Nama cabang wajib diisi."
                        return@Button
                    }
                    if (trimmedName.length < 2) {
                        errorMessage = "Nama cabang minimal 2 karakter."
                        return@Button
                    }
                    onSave(trimmedName, address.trim(), phone.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isEdit) "SIMPAN" else "TAMBAH",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}