package com.karyaku.kasir

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ====================================================================
// SISTEM TUTORIAL PROAKTIF & KONTEKSTUAL INTERAKTIF (ProactiveGuideDialog.kt)
//   - Fix Layout Teks Terpotong:
//     1. Badge judul di header memakai weight(1f, fill = false) agar tombol
//        "Lewati" di kanan selalu utuh 1 baris (tidak terpotong menjadi "Lew..").
//     2. Tombol aksi akhir menggunakan label ringkas "Mulai" (1 baris penuh)
//        dengan defaultMinSize(minHeight = 46.dp) & softWrap = false agar
//        tidak pernah terpotong vertikal meski ukuran font diperbesar.
//     3. Konten dialog mendukung verticalScroll agar aman di layar kecil.
// ====================================================================

data class GuidedStep(
    val title: String,
    val description: String,
    val tip: String = "",
    val icon: ImageVector = Icons.Default.Lightbulb
)

object ProactiveGuideManager {
    private const val PREFS_NAME = "zokas_proactive_guides"

    const val KEY_POS_SCREEN = "guide_pos_screen_v1"
    const val KEY_PRODUCT_BOM = "guide_product_bom_v1"
    const val KEY_SETTINGS_SCREEN = "guide_settings_screen_v1"
    const val KEY_CRM_MODULE = "guide_crm_module_v1"
    const val KEY_HR_MODULE = "guide_hr_module_v1"

    fun hasSeenGuide(context: Context, key: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(key, false)
    }

    fun markGuideSeen(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(key, true).apply()
    }

    fun resetAllGuides(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun getStepsForFeature(key: String): Pair<String, List<GuidedStep>> {
        return when (key) {
            KEY_POS_SCREEN -> "Panduan Layar Kasir" to listOf(
                GuidedStep(
                    title = "1. Pilih Barang & Cek Stok Varian",
                    description = "Ketuk kartu produk untuk menambah ke keranjang. Jika produk memiliki varian, rincian stok tiap varian akan berjalan otomatis di bawah harga.",
                    tip = "Gunakan tombol 'List / Grid' di kanan kategori untuk mengganti tampilan katalog.",
                    icon = Icons.Default.TouchApp
                ),
                GuidedStep(
                    title = "2. Cari Cepat via Kode Unik (SKU) & Barcode",
                    description = "Ketuk ikon Kaca Pembesar di atas untuk mengetik Nama Produk, Kode Unik (SKU), atau memindai Barcode lewat kamera.",
                    tip = "Ketik Kode Unik lalu tekan 'Tambah Kode' untuk memasukkan barang tanpa menyentuh katalog.",
                    icon = Icons.Default.QrCodeScanner
                ),
                GuidedStep(
                    title = "3. Catatan Pesanan, Parkir & Bayar",
                    description = "Di dalam Keranjang, ketuk ikon Pensil untuk menambah catatan khusus pembeli, 'Parkir' untuk menyimpan pesanan sementara, atau 'BAYAR' untuk memproses pembayaran.",
                    tip = "Untuk Transfer Bank Manual, cukup centang kotak konfirmasi setelah dana masuk.",
                    icon = Icons.Default.Payments
                )
            )
            KEY_PRODUCT_BOM -> "Panduan Produk & BOM" to listOf(
                GuidedStep(
                    title = "1. Tambah Produk & Kode Unik (SKU)",
                    description = "Tekan tombol '+ Tambah Produk' di kanan bawah. Isi Nama, Harga, Stok, serta Kode Unik (SKU) atau ketuk ikon Bintang untuk membuat kode otomatis.",
                    tip = "Kode Unik mempermudah kasir mencari barang tanpa barcode kemasan.",
                    icon = Icons.Default.AddBox
                ),
                GuidedStep(
                    title = "2. Atur Bahan Baku & Resep (BOM) Langsung",
                    description = "Saat mengedit produk, Anda tidak perlu lagi keluar ke Pengaturan. Gunakan kotak 'Resep (BOM) & Bahan Baku' di bagian atas form Edit Produk.",
                    tip = "Stok bahan mentah akan otomatis terpotong setiap kali produk terjual.",
                    icon = Icons.Default.Science
                ),
                GuidedStep(
                    title = "3. Menu Cepat (Tekan Lama Produk)",
                    description = "Tekan dan tahan pada kartu produk mana saja untuk membuka menu cepat: Restock barang jadi, catat Barang Rusak (Waste), atau simpan label Barcode.",
                    tip = "Pengurangan stok Waste otomatis menyesuaikan varian yang dipilih.",
                    icon = Icons.Default.Inventory
                )
            )
            KEY_SETTINGS_SCREEN -> "Panduan Pengaturan" to listOf(
                GuidedStep(
                    title = "1. Atur Profil Toko & Pajak Indonesia",
                    description = "Buka kartu 'Profil Toko' untuk mengisi nama, alamat, logo struk, serta memilih tarif pajak standar Indonesia (PB1 10%, PPN 11%/12%, atau Pajak Inklusif).",
                    tip = "Aktifkan 'Harga Termasuk Pajak' jika harga menu sudah final.",
                    icon = Icons.Default.Store
                ),
                GuidedStep(
                    title = "2. Aktifkan Modul Sesuai Kebutuhan",
                    description = "Buka 'Modul Toko' untuk menyalakan/mematikan fitur tambahan seperti CRM Pelanggan, Kepegawaian (HR), Supplier (PO), atau Meja Restoran.",
                    tip = "Matikan modul yang tidak terpakai agar tampilan kasir tetap ringkas.",
                    icon = Icons.Default.Tune
                ),
                GuidedStep(
                    title = "3. Metode Pembayaran & Backup Rutin",
                    description = "Atur QRIS Statis dan Nomor Rekening Transfer di satu tempat pada kartu 'Metode Pembayaran', lalu rutin lakukan 'Backup' di kartu Data & Backup.",
                    tip = "Semua data tersimpan offline di HP Anda—backup rutin menjaga keamanan data.",
                    icon = Icons.Default.Backup
                )
            )
            KEY_CRM_MODULE -> "Modul CRM & Pelanggan" to listOf(
                GuidedStep(
                    title = "1. Kelola Member & Diskon Ulang Tahun",
                    description = "Kartu 'CRM & Pelanggan' kini muncul di halaman Pengaturan. Anda dapat mendaftarkan pelanggan setia dan mengatur Diskon Ulang Tahun otomatis.",
                    tip = "Poin belanja pelanggan bertambah otomatis setiap transaksi selesai.",
                    icon = Icons.Default.People
                ),
                GuidedStep(
                    title = "2. Pilih Pelanggan Saat Transaksi di Kasir",
                    description = "Di layar Kasir, ketuk tombol 'Pelanggan Umum' di atas keranjang untuk memilih member atau mendaftarkan pelanggan baru saat checkout.",
                    tip = "Poin member juga dapat ditukarkan menjadi potongan harga langsung.",
                    icon = Icons.Default.CardGiftcard
                )
            )
            KEY_HR_MODULE -> "Modul Kepegawaian (HR)" to listOf(
                GuidedStep(
                    title = "1. Pantau Absensi & Hitung Gaji (Payroll)",
                    description = "Kartu 'Kepegawaian (HR)' kini tersedia di Pengaturan. Gunakan menu 'Absensi' untuk melihat kehadiran staf dan 'Gaji (Payroll)' untuk slip gaji otomatis.",
                    tip = "Gaji pokok akan otomatis dijumlahkan dengan komisi penjualan kasir.",
                    icon = Icons.Default.Badge
                )
            )
            else -> "Panduan Fitur ZoKas" to listOf(
                GuidedStep(
                    title = "Selamat Datang di ZoKas",
                    description = "Gunakan buku panduan di pojok kanan atas (?) kapan saja untuk mempelajari setiap fitur langkah demi langkah.",
                    icon = Icons.Default.School
                )
            )
        }
    }
}

@Composable
fun ProactiveGuideDialog(
    featureKey: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val (featureTitle, steps) = remember(featureKey) {
        ProactiveGuideManager.getStepsForFeature(featureKey)
    }
    var currentStepIndex by remember(featureKey) { mutableStateOf(0) }
    val step = steps.getOrNull(currentStepIndex) ?: return

    val finishAndRemember: () -> Unit = {
        ProactiveGuideManager.markGuideSeen(context, featureKey)
        onDismiss()
    }

    Dialog(
        onDismissRequest = finishAndRemember,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Blokir klik tembus */ },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Progress & Tombol Lewati (Anti-terpotong)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f, fill = false),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "$featureTitle (${currentStepIndex + 1}/${steps.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = finishAndRemember,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text(
                                text = "Lewati",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dots Progress Indicator
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        steps.forEachIndexed { idx, _ ->
                            Box(
                                modifier = Modifier
                                    .height(6.dp)
                                    .width(if (idx == currentStepIndex) 20.dp else 6.dp)
                                    .background(
                                        color = if (idx == currentStepIndex)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Icon Ilustrasi
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = step.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = step.title,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = step.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    if (step.tip.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.TipsAndUpdates,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = step.tip,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Tombol Navigasi Langkah (Anti-terpotong)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (currentStepIndex > 0) {
                            OutlinedButton(
                                onClick = { currentStepIndex-- },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 46.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Kembali",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        Button(
                            onClick = {
                                if (currentStepIndex < steps.size - 1) {
                                    currentStepIndex++
                                } else {
                                    finishAndRemember()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 46.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (currentStepIndex < steps.size - 1) "Lanjut" else "Selesai",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
