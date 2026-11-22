package com.karyaku.kasir

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.karyaku.kasir.data.local.UserEntity
import java.io.File

@Composable
fun AccountPhotoSettingsDialog(
    users: List<UserEntity>,
    currentUser: UserEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var photoTick by remember { mutableIntStateOf(0) }
    var pickingForUser by remember { mutableStateOf<UserEntity?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val target = pickingForUser
        pickingForUser = null
        if (uri != null && target != null) {
            val saved = LoginProfilePhotoHelper.saveUserPhotoFromUri(context, target.id, uri)
            if (saved != null) {
                photoTick++
                Toast.makeText(context, "Foto profil ${target.name} diperbarui", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val visibleUsers = remember(users, currentUser) {
        if (currentUser.role == "OWNER") users else users.filter { it.id == currentUser.id }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Foto Profil Akun Login",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (currentUser.role == "OWNER")
                        "Sebagai OWNER, Anda dapat mengatur foto profil untuk semua akun terdaftar."
                    else
                        "Anda hanya dapat mengubah foto profil akun Anda sendiri (${currentUser.name}).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleUsers, key = { it.id }) { targetUser ->
                        val photoPath = remember(targetUser.id, photoTick) {
                            LoginProfilePhotoHelper.getUserPhotoPath(context, targetUser.id)
                        }
                        val canEditThisUser = currentUser.role == "OWNER" || targetUser.id == currentUser.id

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (photoPath != null) {
                                        AsyncImage(
                                            model = File(photoPath),
                                            contentDescription = "Foto ${targetUser.name}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = targetUser.name.firstOrNull()?.uppercase() ?: "?",
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = targetUser.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = targetUser.role,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (canEditThisUser) {
                                    IconButton(
                                        onClick = {
                                            pickingForUser = targetUser
                                            imagePickerLauncher.launch("image/*")
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddAPhoto,
                                            contentDescription = "Pilih Foto",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (photoPath != null) {
                                        IconButton(
                                            onClick = {
                                                LoginProfilePhotoHelper.clearUserPhoto(context, targetUser.id)
                                                photoTick++
                                                Toast.makeText(context, "Foto ${targetUser.name} dikembalikan ke default", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Hapus Foto",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Terkunci",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("SELESAI", fontWeight = FontWeight.Bold)
            }
        }
    )
}
