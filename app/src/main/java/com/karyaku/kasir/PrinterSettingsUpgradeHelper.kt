package com.karyaku.kasir

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PrinterPaperSize(
    val mm: Int,
    val charWidth: Int,
    val logoWidthPx: Int,
    val label: String
) {
    MM_58(58, 32, 192, "58mm (32 Kolom)"),
    MM_80(80, 48, 288, "80mm (48 Kolom)");

    companion object {
        fun fromMm(mm: Int): PrinterPaperSize {
            return if (mm == 80) MM_80 else MM_58
        }
    }
}

data class PrinterUpgradeConfig(
    val paperSize: PrinterPaperSize = PrinterPaperSize.MM_58,
    val printLogo: Boolean = true,
    val autoCutPaper: Boolean = false,
    val autoOpenDrawerOnCash: Boolean = false,
    val feedLines: Int = 0
)

object PrinterUpgradePrefs {
    private const val PREFS_NAME = "zokas_printer_upgrade_prefs"

    fun load(context: Context): PrinterUpgradeConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mm = prefs.getInt("paper_width_mm", 58)
        return PrinterUpgradeConfig(
            paperSize = PrinterPaperSize.fromMm(mm),
            printLogo = prefs.getBoolean("print_logo", true),
            autoCutPaper = prefs.getBoolean("auto_cut_paper", false),
            autoOpenDrawerOnCash = prefs.getBoolean("auto_open_drawer_on_cash", false),
            feedLines = prefs.getInt("feed_lines", 0).coerceIn(0, 3)
        )
    }

    fun save(context: Context, config: PrinterUpgradeConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("paper_width_mm", config.paperSize.mm)
            .putBoolean("print_logo", config.printLogo)
            .putBoolean("auto_cut_paper", config.autoCutPaper)
            .putBoolean("auto_open_drawer_on_cash", config.autoOpenDrawerOnCash)
            .putInt("feed_lines", config.feedLines.coerceIn(1, 5))
            .apply()
    }

    fun buildTestReceiptBytes(context: Context, storeName: String, logoPath: String?): ByteArray {
        val cfg = load(context)
        val width = cfg.paperSize.charWidth
        val out = ByteArrayOutputStream()
        out.write(EscPosCommands.INIT)

        if (cfg.printLogo) {
            val logoBytes = EscPosCommands.printDefaultOrCustomLogo(
                context = context,
                logoPath = logoPath,
                targetWidthPx = cfg.paperSize.logoWidthPx
            )
            if (logoBytes.isNotEmpty()) {
                out.write(logoBytes)
            }
        }

        val nowStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val sb = StringBuilder()
        val title = storeName.ifBlank { "ZoKas Store" }.uppercase(Locale.getDefault())
        sb.appendLine(centerForThermal(title, width))
        sb.appendLine(centerForThermal("TES CETAK (${cfg.paperSize.label})", width))
        sb.appendLine("=".repeat(width))
        sb.appendLine("Waktu : $nowStr")
        sb.appendLine("Kertas: ${cfg.paperSize.mm}mm ($width karakter)")
        sb.appendLine("-".repeat(width))
        sb.appendLine(leftRightForThermal("1x Kopi Hitam", "Rp 6.000", width))
        sb.appendLine(leftRightForThermal("2x Roti Bakar", "Rp 20.000", width))
        sb.appendLine("=".repeat(width))
        sb.appendLine(leftRightForThermal("TOTAL", "Rp 26.000", width))
        sb.appendLine(centerForThermal("Printer siap digunakan!", width))

        out.write(EscPosCommands.ALIGN_LEFT)
        out.write(sb.toString().toByteArray(charset("GBK")))
        val fLines = cfg.feedLines.coerceIn(0, 3)
        if (fLines > 0) out.write(byteArrayOf(0x1B, 0x64, fLines.toByte()))
        if (cfg.autoCutPaper) {
            out.write(EscPosCommands.CUT_PAPER)
        }
        return out.toByteArray()
    }

    private fun centerForThermal(text: String, width: Int): String {
        val clean = text.trim()
        if (clean.length >= width) return clean.take(width)
        val pad = (width - clean.length) / 2
        return " ".repeat(pad) + clean
    }

    private fun leftRightForThermal(left: String, right: String, width: Int): String {
        val r = right.trim()
        val maxL = (width - r.length - 1).coerceAtLeast(1)
        val l = if (left.length > maxL) left.take(maxL) else left
        val spaces = (width - l.length - r.length).coerceAtLeast(1)
        return l + " ".repeat(spaces) + r
    }
}

@Composable
fun PrinterUpgradeSettingsSection(
    savedPrinterMac: String?,
    storeName: String = "ZoKas Store",
    logoPath: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cfg by remember { mutableStateOf(PrinterUpgradePrefs.load(context)) }
    var isTestingPrint by remember { mutableStateOf(false) }

    fun updateCfg(next: PrinterUpgradeConfig) {
        cfg = next
        PrinterUpgradePrefs.save(context, next)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            text = "Ukuran Kertas & Fitur Printer",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary
        )

        // Pilihan Ukuran Kertas (58mm vs 80mm)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrinterPaperSize.entries.forEach { sizeOption ->
                val selected = cfg.paperSize == sizeOption
                OutlinedButton(
                    onClick = { updateCfg(cfg.copy(paperSize = sizeOption)) },
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 40.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = sizeOption.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }

        // Saklar Cetak Logo di Struk
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { updateCfg(cfg.copy(printLogo = !cfg.printLogo)) }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Cetak Logo Toko di Struk", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("Gunakan logo toko atau logo default ZoKas", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = cfg.printLogo,
                onCheckedChange = { updateCfg(cfg.copy(printLogo = it)) }
            )
        }

        // Saklar Potong Kertas Otomatis
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { updateCfg(cfg.copy(autoCutPaper = !cfg.autoCutPaper)) }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Potong Kertas Otomatis (Auto-Cut)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("Kirim perintah potong setelah struk selesai", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = cfg.autoCutPaper,
                onCheckedChange = { updateCfg(cfg.copy(autoCutPaper = it)) }
            )
        }

        // Saklar Buka Laci Kasir Otomatis Saat Bayar Tunai
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { updateCfg(cfg.copy(autoOpenDrawerOnCash = !cfg.autoOpenDrawerOnCash)) }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Buka Laci Kasir Saat Tunai", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("Kirim sinyal laci kasir otomatis pada bayar CASH", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = cfg.autoOpenDrawerOnCash,
                onCheckedChange = { updateCfg(cfg.copy(autoOpenDrawerOnCash = it)) }
            )
        }

        // Pengaturan Jarak Feed Kertas (1 - 5 baris)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Jarak Feed Kertas: ${cfg.feedLines} baris", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { updateCfg(cfg.copy(feedLines = (cfg.feedLines - 1).coerceIn(0, 3))) },
                    enabled = cfg.feedLines > 0,
                    modifier = Modifier.size(34.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Kurangi Feed", modifier = Modifier.size(16.dp))
                }
                OutlinedButton(
                    onClick = { updateCfg(cfg.copy(feedLines = (cfg.feedLines + 1).coerceIn(0, 3))) },
                    enabled = cfg.feedLines < 3,
                    modifier = Modifier.size(34.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah Feed", modifier = Modifier.size(16.dp))
                }
            }
        }

        // Tombol Uji Cetak Printer Kasir
        OutlinedButton(
            onClick = {
                val mac = savedPrinterMac?.trim().orEmpty()
                if (mac.isBlank()) {
                    Toast.makeText(context, "Pilih Printer Kasir terlebih dahulu.", Toast.LENGTH_SHORT).show()
                    return@OutlinedButton
                }
                isTestingPrint = true
                scope.launch {
                    val manager = BluetoothPrinterManager(context)
                    val bytes = PrinterUpgradePrefs.buildTestReceiptBytes(context, storeName, logoPath)
                    val ok = manager.printData(mac, bytes)
                    isTestingPrint = false
                    Toast.makeText(
                        context,
                        if (ok) "Tes cetak berhasil dikirim ke $mac!" else "Gagal tes cetak. Periksa koneksi & kertas printer.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            enabled = !isTestingPrint,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 42.dp),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isTestingPrint) "Mengirim Tes Cetak..." else "Tes Cetak Printer Kasir",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
