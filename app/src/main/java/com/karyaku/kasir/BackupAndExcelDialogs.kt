package com.karyaku.kasir

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karyaku.kasir.data.local.ProductEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScheduledBackupDriveDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var cfg by remember { mutableStateOf(AutoBackupDriveManager.loadSettings(context)) }
    var statusBanner by remember { mutableStateOf(cfg.lastBackupStatus) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Auto-Backup & Google Drive", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Jadwalkan backup otomatis ke penyimpanan HP & kirim/simpan cepat ke Google Drive tanpa pengaturan rumit.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            cfg = cfg.copy(isEnabled = !cfg.isEnabled)
                            AutoBackupDriveManager.saveSettings(context, cfg)
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aktifkan Jadwal Auto-Backup", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            if (cfg.isEnabled) "Jadwal aktif sesuai pilihan di bawah" else "Jadwal otomatis sedang nonaktif",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = cfg.isEnabled,
                        onCheckedChange = { checked ->
                            cfg = cfg.copy(isEnabled = checked)
                            AutoBackupDriveManager.saveSettings(context, cfg)
                        }
                    )
                }

                Text("Pilih Waktu / Pola Jadwal:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BackupScheduleMode.entries.forEach { mode ->
                        FilterChip(
                            selected = cfg.scheduleMode == mode,
                            onClick = {
                                cfg = cfg.copy(scheduleMode = mode)
                                AutoBackupDriveManager.saveSettings(context, cfg)
                            },
                            label = { Text(mode.label, fontSize = 11.sp) }
                        )
                    }
                }

                Text(
                    cfg.scheduleMode.desc,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            cfg = cfg.copy(openGoogleDriveAfterBackup = !cfg.openGoogleDriveAfterBackup)
                            AutoBackupDriveManager.saveSettings(context, cfg)
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = cfg.openGoogleDriveAfterBackup,
                        onCheckedChange = { checked ->
                            cfg = cfg.copy(openGoogleDriveAfterBackup = checked)
                            AutoBackupDriveManager.saveSettings(context, cfg)
                        }
                    )
                    Text(
                        "Buka / kirim file ke Google Drive setelah backup dibuat",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Status Terakhir:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(statusBanner, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (cfg.lastBackupTimeMs > 0L) {
                            val tStr = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(cfg.lastBackupTimeMs))
                            Text("Waktu: $tStr", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Button(
                    onClick = {
                        val (ok, msg) = AutoBackupDriveManager.runScheduledOrManualBackup(
                            context = context,
                            triggerLabel = "Backup Sekarang",
                            forceDriveShare = cfg.openGoogleDriveAfterBackup
                        )
                        cfg = AutoBackupDriveManager.loadSettings(context)
                        statusBanner = msg
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        if (!ok) {
                            statusBanner = msg
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Backup Sekarang & Kirim ke Google Drive", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("Selesai", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun SimpleProductExcelDialog(
    products: List<ProductEntity>,
    onImportRows: (List<SimpleProductRow>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val createTemplateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(SimpleProductExcelManager.buildSimpleTemplateCsv().toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Template CSV simpel berhasil disimpan (1 kolom = 1 data).", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyimpan template: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportSimpleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            val (_, msg) = SimpleProductExcelManager.exportProductsToSimpleCsvUri(context, uri, products)
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    val importSimpleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val parsed = SimpleProductExcelManager.parseSimpleCsvUri(context, uri)
            if (parsed.isEmpty()) {
                Toast.makeText(context, "Tidak ada baris produk valid ditemukan di file.", Toast.LENGTH_LONG).show()
            } else {
                onImportRows(parsed)
                Toast.makeText(context, "${parsed.size} baris produk siap diproses.", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.TableChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Excel / CSV Produk Simpel", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Setiap informasi dipisah ke kolom sendiri (Nama, Kategori, Harga Beli, Harga Jual, Stok, Satuan, SKU, Barcode Kemasan, serta Varian di kolom terpisah) agar mudah diedit di Excel/Google Sheets tanpa salah tanda pemisah.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        createTemplateLauncher.launch("ZoKas_Template_Produk_Simpel.csv")
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Unduh Template Kosong + Contoh", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        exportSimpleLauncher.launch("ZoKas_Produk_${System.currentTimeMillis()}.csv")
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ekspor Produk Saat Ini (${products.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        importSimpleLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/*", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Impor Produk dari File CSV/Excel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}
