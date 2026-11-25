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

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Pengaturan Google Drive",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
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
                                "Otomatis arahkan ke Google Drive saat backup dibuat",
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            "Catatan: Backup disimpan otomatis ke memori HP terlebih dahulu agar aman saat offline, lalu diteruskan ke aplikasi Google Drive milik akun Anda.",
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Backup Sekarang & Kirim ke Google Drive",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
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
        ActivityResultContracts.CreateDocument(SimpleProductExcelManager.XLSX_MIME_TYPE)
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(SimpleProductExcelManager.buildSimpleTemplateXlsxBytes())
                }
                Toast.makeText(context, "Template Excel (.xlsx) simpel berhasil disimpan.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyimpan template: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportSimpleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(SimpleProductExcelManager.XLSX_MIME_TYPE)
    ) { uri: Uri? ->
        if (uri != null) {
            val (_, msg) = SimpleProductExcelManager.exportProductsToSimpleXlsxUri(context, uri, products)
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    val importSimpleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val parsed = SimpleProductExcelManager.parseSimpleSpreadsheetUri(context, uri)
            if (parsed.isEmpty()) {
                Toast.makeText(context, "Tidak ada baris produk valid ditemukan di file Excel (.xlsx).", Toast.LENGTH_LONG).show()
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
            Text(zokasText("Excel Produk Simpel (.xlsx)", "Simple Product Excel (.xlsx)"), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    zokasText("Format Excel (.xlsx) dengan 1 kolom = 1 data (Nama, Kategori, Harga Beli, Harga Jual, Stok, Satuan, SKU, Barcode Kemasan, serta Varian di kolom terpisah) agar mudah diedit tanpa typo.", "Excel (.xlsx) format with 1 column = 1 field (Name, Category, Cost Price, Selling Price, Stock, Unit, SKU, Packaging Barcode, and Variants in separate columns) for easy typo-free editing."),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        createTemplateLauncher.launch("ZoKas_Template_Produk_Simpel.xlsx")
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        zokasText("Unduh Template Kosong + Contoh (.xlsx)", "Download Template + Sample (.xlsx)"),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        exportSimpleLauncher.launch("ZoKas_Produk_Simpel_${System.currentTimeMillis()}.xlsx")
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        zokasText("Ekspor Produk Saat Ini (${products.size}) (.xlsx)", "Export Current Products (${products.size}) (.xlsx)"),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        importSimpleLauncher.launch(
                            arrayOf(
                                SimpleProductExcelManager.XLSX_MIME_TYPE,
                                "application/vnd.ms-excel",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        zokasText("Impor Produk dari File Excel (.xlsx)", "Import Products from Excel (.xlsx)"),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(zokasText("Tutup", "Close"))
            }
        }
    )
}
