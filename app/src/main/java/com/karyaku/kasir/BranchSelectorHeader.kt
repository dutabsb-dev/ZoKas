package com.karyaku.kasir

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * MODUL D-1 — Header Dropdown Cabang
 *
 * Komponen kecil untuk header MainActivity. Menampilkan cabang aktif
 * dan dropdown untuk pindah cabang.
 *
 * Perilaku:
 * - CASHIER (isBranchSwitchLocked = true):
 *   Tombol disabled + ikon lock. Tap → Toast "Hubungi OWNER".
 * - OWNER (isBranchSwitchLocked = false):
 *   Tap → dropdown daftar cabang.
 *   - Jika ada shift aktif di cabang sekarang, dropdown menampilkan
 *     warning dan item lain di-disable. User wajib tutup kasir dulu.
 *   - Tap cabang lain → panggil viewModel.switchBranch().
 *   - Menu "Kelola Cabang" → callback ke parent (MainActivity).
 * - Jika hanya 1 cabang: tombol disabled + Toast "Hanya ada 1 cabang".
 *
 * Layout: [Icon] Nama Cabang [▼/lock]
 */
@Composable
fun BranchSelectorHeader(
    viewModel: PosViewModel,
    modifier: Modifier = Modifier,
    onManageBranchesClick: () -> Unit
) {
    val context = LocalContext.current
    val currentBranch by viewModel.currentBranch.collectAsStateWithLifecycle()
    val currentBranchId by viewModel.currentBranchId.collectAsStateWithLifecycle()
    val allBranches by viewModel.allBranches.collectAsStateWithLifecycle()
    val isLocked by viewModel.isBranchSwitchLocked.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()

    var expanded by remember { mutableStateOf(false) }

    val hasMultipleBranches = allBranches.size > 1
    val hasActiveShift = activeShift != null
    val canOpenDropdown = !isLocked && hasMultipleBranches

    Box(modifier = modifier) {
        // ============================================================
        // TRIGGER BUTTON
        // ============================================================
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (canOpenDropdown)
                EmeraldGreen.copy(alpha = 0.10f)
            else
                MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable {
                when {
                    isLocked -> Toast.makeText(
                        context,
                        "Hanya OWNER yang bisa pindah cabang.",
                        Toast.LENGTH_SHORT
                    ).show()
                    !hasMultipleBranches -> Toast.makeText(
                        context,
                        "Hanya ada 1 cabang.",
                        Toast.LENGTH_SHORT
                    ).show()
                    else -> expanded = true
                }
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (currentBranch?.isMainBranch == true)
                        Icons.Default.Storefront
                    else
                        Icons.Default.Store,
                    contentDescription = null,
                    tint = if (canOpenDropdown) EmeraldGreen else SlateGrey,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = currentBranch?.name ?: "Cabang",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canOpenDropdown) EmeraldGreen else SlateGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (canOpenDropdown)
                        Icons.Default.ArrowDropDown
                    else
                        Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (canOpenDropdown) EmeraldGreen else SlateGrey,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // ============================================================
        // DROPDOWN MENU
        // ============================================================
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Text(
                text = "PILIH CABANG",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            // Warning shift aktif
            if (hasActiveShift) {
                Surface(
                    color = AmberAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = AmberAccentDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tutup kasir dulu sebelum pindah cabang",
                            fontSize = 10.sp,
                            color = AmberAccentDark,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 12.sp
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            }

            // Daftar cabang
            allBranches.forEach { branch ->
                val isCurrent = branch.id == currentBranchId
                val isSelectable = isCurrent || !hasActiveShift

                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        color = if (isCurrent)
                                            EmeraldGreen.copy(alpha = 0.15f)
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant,
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
                                    tint = if (isCurrent) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = branch.name,
                                    fontSize = 13.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent)
                                        EmeraldGreen
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (branch.isMainBranch) {
                                        Text(
                                            text = "Utama",
                                            fontSize = 10.sp,
                                            color = AmberAccentDark,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (branch.address.isNotBlank()) {
                                        if (branch.isMainBranch) Text(" • ", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = branch.address,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (isCurrent) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    onClick = {
                        if (isCurrent) {
                            expanded = false
                            return@DropdownMenuItem
                        }
                        if (hasActiveShift) {
                            Toast.makeText(
                                context,
                                "Tutup kasir dulu sebelum pindah cabang.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@DropdownMenuItem
                        }
                        viewModel.switchBranch(branch.id) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) expanded = false
                        }
                    },
                    enabled = isSelectable
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(EmeraldGreen.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Kelola Cabang",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldGreen
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onManageBranchesClick()
                }
            )
        }
    }
}