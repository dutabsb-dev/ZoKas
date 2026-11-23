package com.karyaku.kasir

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TutorialStepItem(
    val stepNumber: Int,
    val title: String,
    val detail: String,
    val tip: String = ""
)

data class MenuTutorialContent(
    val menuKey: String,
    val title: String,
    val subtitle: String,
    val steps: List<TutorialStepItem>,
    val checklist: List<String>
)

object SettingsTutorialCatalog {
    fun getTutorial(menuKey: String): MenuTutorialContent {
        return when (menuKey) {
            "backup" -> MenuTutorialContent(
                menuKey = "backup",
                title = "Tutorial Interaktif: Data & Backup",
                subtitle = "Amankan transaksi, produk, stok, pelanggan & pengaturan agar tidak hilang saat ganti HP atau reinstall.",
                steps = listOf(
                    TutorialStepItem(
                        stepNumber = 1,
                        title = "Buat Backup Manual Secara Berkala",
                        detail = "Tekan tombol 'Backup' untuk menyimpan salinan data toko ke file cadangan. Disarankan dilakukan setiap tutup kasir atau setelah menambah banyak produk.",
                        tip = "Simpan file backup di folder yang mudah ditemukan atau unggah ke penyimpanan awan pribadi."
                    ),
                    TutorialStepItem(
                        stepNumber = 2,
                        title = "Atur Jadwal Auto-Backup & Google Drive",
                        detail = "Gunakan tombol 'Jadwal Auto-Backup & Google Drive' agar pencadangan berjalan rutin dan tersinkronisasi tanpa perlu diingat manual.",
                        tip = "Pastikan koneksi internet aktif saat proses unggah ke Google Drive."
                    ),
                    TutorialStepItem(
                        stepNumber = 3,
                        title = "Pulihkan Data dengan Restore",
                        detail = "Jika pindah perangkat atau habis instal ulang aplikasi, tekan 'Restore' lalu pilih file backup terbaru. Semua data produk, riwayat transaksi, dan pengaturan akan kembali.",
                        tip = "Periksa kembali nama toko, printer, dan metode pembayaran setelah proses Restore selesai."
                    )
                ),
                checklist = listOf(
                    "Sudah membuat file backup terbaru hari ini",
                    "File backup tersimpan aman (tidak hanya di folder sementara)",
                    "Jadwal Auto-Backup / Google Drive sudah dicek",
                    "Siap melakukan Restore bila pindah perangkat"
                )
            )
            "appearance" -> MenuTutorialContent(
                menuKey = "appearance",
                title = "Tutorial: Tampilan, Tema & Bahasa",
                subtitle = "Sesuaikan warna tema, mode gelap/terang, ukuran teks, dan bahasa antarmuka ZoKas.",
                steps = listOf(
                    TutorialStepItem(
                        stepNumber = 1,
                        title = "Pilih Tema Aplikasi (50 Tema)",
                        detail = "Ketuk 'Pilih Tema' untuk mencari dan memilih palet warna serta tingkat transparansi yang nyaman di mata kasir.",
                        tip = "Gunakan 'Reset Default' bila ingin kembali ke tampilan awal."
                    ),
                    TutorialStepItem(
                        stepNumber = 2,
                        title = "Atur Mode Gelap, Ukuran Font & Bahasa",
                        detail = "Geser slider ukuran font sesuai kenyamanan layar HP/tablet, nyalakan Mode Gelap untuk ruangan redup, dan pilih bahasa Indonesia atau English."
                    )
                ),
                checklist = listOf(
                    "Teks dan tombol mudah dibaca tanpa kekecilan/kebesaran",
                    "Warna tema tidak menyulitkan pembacaan status stok"
                )
            )
            "account" -> MenuTutorialContent(
                menuKey = "account",
                title = "Tutorial: Manajemen Akun & Keamanan",
                subtitle = "Kelola PIN kasir, data karyawan, riwayat shift, dan login biometrik.",
                steps = listOf(
                    TutorialStepItem(
                        stepNumber = 1,
                        title = "Ubah PIN & Kelola Karyawan",
                        detail = "Gunakan 'Ubah PIN' untuk memperbarui kode akses akun aktif, dan 'Kelola Karyawan' (khusus OWNER) untuk mengatur akun kasir/staf.",
                        tip = "Jangan gunakan PIN yang mudah ditebak untuk akun OWNER."
                    ),
                    TutorialStepItem(
                        stepNumber = 2,
                        title = "Daftarkan & Aktifkan Sidik Jari / Biometrik",
                        detail = "Daftarkan biometrik lalu aktifkan saklar 'Login Sidik Jari' agar kasir bisa masuk lebih cepat dan aman."
                    )
                ),
                checklist = listOf(
                    "PIN akun sudah diganti dari bawaan awal",
                    "Daftar karyawan dan hak akses sudah sesuai"
                )
            )
            "business_type" -> MenuTutorialContent(
                menuKey = "business_type",
                title = "Tutorial: Tipe Usaha",
                subtitle = "Sesuaikan perilaku fitur kasir dengan jenis usaha Anda (Kuliner, Retail, Jasa, atau Musiman).",
                steps = listOf(
                    TutorialStepItem(
                        stepNumber = 1,
                        title = "Pilih Tipe Usaha yang Sesuai",
                        detail = "Pilih KULINER bila memakai tampilan Dapur (KDS) dan mode meja, atau RETAIL / JASA / MUSIMAN sesuai alur kerja toko Anda.",
                        tip = "Mengganti tipe usaha tidak menghapus data transaksi maupun produk."
                    )
                ),
                checklist = listOf(
                    "Tipe usaha sudah sesuai alur kerja kasir & dapur"
                )
            )
            "modules" -> MenuTutorialContent(
                menuKey = "modules",
                title = "Tutorial: Modul Toko",
                subtitle = "Aktifkan hanya modul yang dibutuhkan toko agar layar tetap ringkas dan efisien.",
                steps = listOf(
                    TutorialStepItem(
                        stepNumber = 1,
                        title = "Aktifkan Modul Operasional",
                        detail = "Nyalakan saklar modul seperti Multi-Cabang, Happy Hour, HR, CRM, Mode Meja, Harga Grosir, Pre-Order, Supplier/PO, Keuangan, atau Bahan Baku/BOM sesuai kebutuhan."
                    ),
                    TutorialStepItem(
                        stepNumber = 2,
                        title = "Kelola Data Modul Terkait",
                        detail = "Setelah modul diaktifkan, gunakan menu pengelolaannya (misal Kelola Cabang, Transfer Stok, Jadwal Reservasi, Buku Piutang/Hutang) untuk mengisi data."
                    )
                ),
                checklist = listOf(
                    "Modul yang tidak dipakai dimatikan agar tidak membingungkan kasir",
                    "Kurs USD atau jam Happy Hour sudah diisi bila diaktifkan"
                )
            )
            "printer" -> MenuTutorialContent(
                menuKey = "printer",
                title = "Tutorial: Pengaturan Printer & Kertas",
                subtitle = "Hubungkan printer kasir/dapur Bluetooth, pilih ukuran kertas 58mm/80mm, dan uji cetak.",
                steps = listOf(
                    TutorialStepItem(
                        stepNumber = 1,
                        title = "Pair & Pilih Printer Bluetooth",
                        detail = "Pastikan printer sudah di-pair di Bluetooth HP, lalu tekan 'Pilih Printer' (atau 'Pilih Printer Dapur') dan pilih perangkatnya."
                    ),
                    TutorialStepItem(
                        stepNumber = 2,
                        title = "Pilih Ukuran Kertas (58mm / 80mm) & Tes Cetak",
                        detail = "Pilih kertas 58mm atau 80mm, atur cetak logo, potong kertas otomatis, buka laci kasir saat tunai, dan jarak feed, lalu tekan 'Tes Cetak Printer Kasir'."
                    )
                ),
                checklist = listOf(
                    "Printer berstatus Terhubung dengan alamat MAC yang benar",
                    "Ukuran kertas (58mm/80mm) dan jarak feed sudah sesuai hasil tes cetak"
                )
            )
            "store_profile" -> MenuTutorialContent(
                menuKey = "store_profile",
                title = "Tutorial: Profil Toko, Pajak & Pembulatan",
                subtitle = "Atur identitas struk, logo toko, tarif pajak/service charge, dan pembulatan tunai.",
                steps = listOf(
                    TutorialStepItem(
                        stepNumber = 1,
                        title = "Lengkapi Identitas & Logo Toko",
                        detail = "Ketuk kotak logo untuk mengunggah logo toko, lalu isi Nama Toko, Alamat, No. Telp, Pesan Struk, dan pesan layar pelanggan (CFD)."
                    ),
                    TutorialStepItem(
                        stepNumber = 2,
                        title = "Atur Pajak, Service Charge & Pembulatan Tunai",
                        detail = "Pilih preset pajak (0%, 10% PB1, 11%/12% PPN) atau isi manual, tentukan apakah harga sudah termasuk pajak (Inclusive), dan pilih pembulatan tunai (Rp 100 / Rp 500)."
                    )
                ),
                checklist = listOf(
                    "Nama toko dan footer struk sudah tampil rapi di struk",
                    "Tarif pajak dan pembulatan tunai sudah sesuai kebijakan toko"
                )
            )
            "discount" -> MenuTutorialContent(
                menuKey = "discount",
                title = "Tutorial: Happy Hour & Diskon",
                subtitle = "Atur rentang waktu promo otomatis, persentase diskon, dan batas maksimal diskon.",
                steps = listOf(
                    TutorialStepItem(
                        stepNumber = 1,
                        title = "Tentukan Jam Mulai, Selesai & Besaran Diskon",
                        detail = "Isi jam mulai dan selesai (format HH:mm), persentase diskon (%), serta batas maksimal diskon (Rp) agar promo tidak melebihi anggaran."
                    )
                ),
                checklist = listOf(
                    "Jam mulai & selesai promo sudah benar",
                    "Batas maksimal diskon (Rp) sudah diatur bila perlu"
                )
            )
            "payment_method" -> MenuTutorialContent(
                menuKey = "payment_method",
                title = "Tutorial: Metode Pembayaran",
                subtitle = "Aktifkan Tunai (CASH), QRIS, Transfer Bank Manual, dan Mesin EDC.",
                steps = listOf(
                    TutorialStepItem(
                        stepNumber = 1,
                        title = "Centang Metode & Isi Detailnya",
                        detail = "Pilih metode pembayaran yang diterima. Jika QRIS dipilih, scan atau tempel payload QRIS (000201...). Jika Transfer Bank dipilih, isi nama bank dan nomor rekening."
                    ),
                    TutorialStepItem(
                        stepNumber = 2,
                        title = "Aktifkan EDC Bila Perlu",
                        detail = "Nyalakan saklar 'Pembayaran Mesin EDC (Kartu)' dan pilih jalur koneksi (TCP/Wi-Fi, Intent, atau Auto) jika menggunakan integrasi EDC."
                    )
                ),
                checklist = listOf(
                    "Minimal satu metode pembayaran aktif",
                    "Payload QRIS / rekening bank sudah terisi jika diaktifkan"
                )
            )
            "owner_monitor" -> MenuTutorialContent(
                menuKey = "owner_monitor",
                title = "Tutorial: Pantauan HP Owner (Bot Telegram)",
                subtitle = "Terima laporan transaksi, tutup shift, dan peringatan void/refund di HP Owner tanpa server.",
                steps = listOf(
                    TutorialStepItem(
                        stepNumber = 1,
                        title = "Buat Bot & Ambil Token via @BotFather",
                        detail = "Buka @BotFather di Telegram, kirim /newbot, ikuti instruksi pembuatan bot, lalu salin Bot Token ke kolom yang tersedia."
                    ),
                    TutorialStepItem(
                        stepNumber = 2,
                        title = "Ambil Chat ID Owner & Tekan START di Bot",
                        detail = "Cek Chat ID HP Owner lewat @userinfobot, buka chat bot Anda lalu tekan START agar bot diizinkan mengirim pesan, lalu tekan 'Tes Kirim' dan 'SIMPAN'."
                    )
                ),
                checklist = listOf(
                    "Bot Token dan Chat ID HP Owner sudah terisi",
                    "Chat dengan bot sudah diaktifkan (START) dan 'Tes Kirim' berhasil"
                )
            )
            else -> MenuTutorialContent(
                menuKey = "multi_device_sync",
                title = "Tutorial: Sinkronisasi Multi-Perangkat",
                subtitle = "Kirim dan gabungkan paket data transaksi antar perangkat kasir dan HP Owner.",
                steps = listOf(
                    TutorialStepItem(
                        stepNumber = 1,
                        title = "Samakan Email Pemilik & Nama Terminal",
                        detail = "Pastikan email pemilik terdaftar sama di semua perangkat dan beri nama terminal/cabang kasir yang jelas."
                    ),
                    TutorialStepItem(
                        stepNumber = 2,
                        title = "Kirim / Ekspor & Impor Gabung",
                        detail = "Gunakan 'Kirim Paket Laporan ke HP Owner' atau 'Ekspor File' (.json) dari perangkat kasir, lalu pilih 'Impor & Gabung' di perangkat Owner."
                    )
                ),
                checklist = listOf(
                    "Nama terminal/cabang kasir sudah jelas",
                    "File paket data (.json) diimpor ke perangkat Owner yang tepat"
                )
            )
        }
    }
}

@Composable
fun SettingsMenuHelpIconButton(
    menuKey: String,
    onOpenQuickAction: ((String) -> Unit)? = null
) {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showDialog = true },
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.HelpOutline,
            contentDescription = "Tutorial Menu",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }

    if (showDialog) {
        val tutorial = remember(menuKey) { SettingsTutorialCatalog.getTutorial(menuKey) }
        var activeStepIndex by remember { mutableStateOf(0) }
        val checkedStates = remember(menuKey) {
            mutableStateListOf<Boolean>().apply {
                repeat(tutorial.checklist.size) { add(false) }
            }
        }
        val currentStep = tutorial.steps.getOrElse(activeStepIndex) { tutorial.steps.first() }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Column {
                    Text(
                        text = tutorial.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = tutorial.subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pemilih Langkah Interaktif
                    if (tutorial.steps.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            tutorial.steps.forEachIndexed { idx, st ->
                                FilterChip(
                                    selected = activeStepIndex == idx,
                                    onClick = { activeStepIndex = idx },
                                    label = {
                                        Text(
                                            text = "Langkah ${st.stepNumber}",
                                            fontSize = 11.sp,
                                            fontWeight = if (activeStepIndex == idx) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Kartu Langkah Aktif
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${currentStep.stepNumber}. ${currentStep.title}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = currentStep.detail,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (currentStep.tip.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            Icons.Default.Lightbulb,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(15.dp).padding(top = 1.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = currentStep.tip,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Navigasi Prev / Next Langkah
                    if (tutorial.steps.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = { if (activeStepIndex > 0) activeStepIndex-- },
                                enabled = activeStepIndex > 0,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Sebelumnya", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { if (activeStepIndex < tutorial.steps.lastIndex) activeStepIndex++ },
                                enabled = activeStepIndex < tutorial.steps.lastIndex,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Berikutnya", fontSize = 11.sp)
                                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Checklist Interaktif
                    if (tutorial.checklist.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = "Checklist Kesiapan:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        tutorial.checklist.forEachIndexed { idx, item ->
                            val isDone = checkedStates.getOrElse(idx) { false }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { checkedStates[idx] = !isDone }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isDone,
                                    onCheckedChange = { checkedStates[idx] = it },
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item,
                                    fontSize = 11.5.sp,
                                    color = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Tombol Pintasan Aksi untuk Menu Backup
                    if (menuKey == "backup" && onOpenQuickAction != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showDialog = false
                                    onOpenQuickAction("backup_now")
                                },
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Backup Sekarang", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    showDialog = false
                                    onOpenQuickAction("restore_now")
                                },
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Restore Data", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("MENGERTI", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
