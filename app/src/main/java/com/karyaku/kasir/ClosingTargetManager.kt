package com.karyaku.kasir

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.karyaku.kasir.data.local.CashFlowEntity
import com.karyaku.kasir.data.local.ShiftEntity
import com.karyaku.kasir.data.local.TransactionEntity

data class ShiftTargetConfig(
    val isEnabled: Boolean = false,
    val targetOmzet: Long = 0L,
    val targetTrxCount: Int = 0
)

object ClosingTargetManager {
    private const val PREFS_NAME = "zokas_closing_target_v1"

    fun loadTarget(context: Context): ShiftTargetConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ShiftTargetConfig(
            isEnabled = prefs.getBoolean("target_enabled", false),
            targetOmzet = prefs.getLong("target_omzet", 0L).coerceAtLeast(0L),
            targetTrxCount = prefs.getInt("target_trx_count", 0).coerceAtLeast(0)
        )
    }

    fun saveTarget(context: Context, config: ShiftTargetConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("target_enabled", config.isEnabled)
            .putLong("target_omzet", config.targetOmzet.coerceAtLeast(0L))
            .putInt("target_trx_count", config.targetTrxCount.coerceAtLeast(0))
            .apply()
    }
}

@Composable
fun ShiftTargetSetupDialog(
    onDismiss: () -> Unit,
    onSaved: (ShiftTargetConfig) -> Unit
) {
    val context = LocalContext.current
    val initial = remember { ClosingTargetManager.loadTarget(context) }

    var enabled by remember { mutableStateOf(initial.isEnabled) }
    var omzetInput by remember {
        mutableStateOf(if (initial.targetOmzet > 0L) initial.targetOmzet.toString() else "")
    }
    var trxInput by remember {
        mutableStateOf(if (initial.targetTrxCount > 0) initial.targetTrxCount.toString() else "")
    }

    val quickOmzetPresets = listOf(500_000L, 1_000_000L, 2_500_000L, 5_000_000L)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.TrackChanges,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Target Shift Sebelum Tutup Kasir",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Atur target omzet atau jumlah transaksi untuk shift ini. Progres target akan tampil sebelum proses Tutup Kasir.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aktifkan Target Shift",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }

                if (enabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickOmzetPresets.forEach { preset ->
                            val label = if (preset >= 1_000_000L) {
                                "${(preset / 1_000_000L)}Jt"
                            } else {
                                "${(preset / 1_000L)}rb"
                            }
                            OutlinedButton(
                                onClick = { omzetInput = preset.toString() },
                                modifier = Modifier.weight(1f).height(34.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = omzetInput,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }
                            if (digits.length <= 12) omzetInput = digits
                        },
                        label = { Text("Target Omzet Shift (Rp)") },
                        placeholder = { Text("0 (opsional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = trxInput,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }
                            if (digits.length <= 6) trxInput = digits
                        },
                        label = { Text("Target Jumlah Transaksi") },
                        placeholder = { Text("0 (opsional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cfg = ShiftTargetConfig(
                        isEnabled = enabled,
                        targetOmzet = omzetInput.filter { it.isDigit() }.toLongOrNull() ?: 0L,
                        targetTrxCount = trxInput.filter { it.isDigit() }.toIntOrNull() ?: 0
                    )
                    ClosingTargetManager.saveTarget(context, cfg)
                    onSaved(cfg)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("SIMPAN TARGET", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun PreCloseTargetCheckpointDialog(
    shift: ShiftEntity,
    transactions: List<TransactionEntity>,
    cashFlows: List<CashFlowEntity>,
    onEditTarget: () -> Unit,
    onProceedCloseShift: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val targetCfg = remember { ClosingTargetManager.loadTarget(context) }

    val shiftSuccessTxs = remember(transactions, shift.startTime) {
        transactions.filter { it.timestamp >= shift.startTime && it.status == "SUCCESS" }
    }
    val actualOmzet = remember(shiftSuccessTxs) {
        shiftSuccessTxs.sumOf { it.totalPrice }
    }
    val actualTrxCount = shiftSuccessTxs.size

    val omzetRatio = if (targetCfg.targetOmzet > 0L) {
        (actualOmzet.toFloat() / targetCfg.targetOmzet.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val trxRatio = if (targetCfg.targetTrxCount > 0) {
        (actualTrxCount.toFloat() / targetCfg.targetTrxCount.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val omzetMet = targetCfg.targetOmzet <= 0L || actualOmzet >= targetCfg.targetOmzet
    val trxMet = targetCfg.targetTrxCount <= 0 || actualTrxCount >= targetCfg.targetTrxCount
    val allTargetsMet = omzetMet && trxMet

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (allTargetsMet) Icons.Default.CheckCircle else Icons.Default.Flag,
                            contentDescription = null,
                            tint = if (allTargetsMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Evaluasi Target Sebelum Tutup Kasir",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Surface(
                    color = if (allTargetsMet)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (allTargetsMet)
                            "Target shift tercapai! Anda siap melanjutkan ke proses Tutup Kasir."
                        else
                            "Target shift belum tercapai sepenuhnya. Anda tetap bisa lanjut Tutup Kasir atau menyesuaikan target.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                if (targetCfg.targetOmzet > 0L) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Capaian Omzet Shift", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${(omzetRatio * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (omzetMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { omzetRatio },
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                        Text(
                            "Tercapai: ${formatRupiah(actualOmzet)} / Target: ${formatRupiah(targetCfg.targetOmzet)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (targetCfg.targetTrxCount > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Capaian Jumlah Transaksi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${(trxRatio * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (trxMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { trxRatio },
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                        Text(
                            "Tercapai: $actualTrxCount transaksi / Target: ${targetCfg.targetTrxCount} transaksi",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (targetCfg.targetOmzet <= 0L && targetCfg.targetTrxCount <= 0) {
                    Text(
                        text = "Belum ada angka target omzet/transaksi yang ditetapkan. Omzet shift saat ini: ${formatRupiah(actualOmzet)} ($actualTrxCount transaksi).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onEditTarget,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Atur Target", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onProceedCloseShift,
                        modifier = Modifier.weight(1.3f).height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lanjut Tutup Kasir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
