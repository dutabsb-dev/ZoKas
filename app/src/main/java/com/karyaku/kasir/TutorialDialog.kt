package com.karyaku.kasir

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ====================================================================
// SESI 5 & SESI 6 (POIN #3) — PUSAT TUTORIAL LENGKAP PENGGUNA AWAM:
//   - Dirancang khusus untuk pengguna awam dan orang tua dengan gaya
//     langkah-demi-langkah (1, 2, 3...) + Catatan/Tips penting persis
//     seperti Tutorial QRIS yang disukai pengguna.
//   - Mencakup Sesi 5 (Tutorial Dasar: Login, Kasir, Pembayaran, Cetak, EDC)
//     dan Sesi 6 (Tutorial Lanjutan: Produk & BOM, Cabang, HR, CRM,
//     Keuangan, Supplier & Backup).
//   - Seluruh warna menggunakan MaterialTheme.colorScheme.* (100% Dark Mode Ready).
// ====================================================================

data class TutorialGuideTopic(
    val id: String,
    val category: String, // "DASAR" atau "LANJUTAN"
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val steps: List<String>,
    val importantNote: String
)

private val allTutorialTopics = listOf(
    // =================================================================
    // SESI 5 — TUTORIAL DASAR (LOGIN, KASIR, PEMBAYARAN, EDC, CETAK)
    // =================================================================
    TutorialGuideTopic(
        id = "login_shift",
        category = "DASAR",
        title = "1. Cara Login & Buka Kasir (Shift)",
        subtitle = "Langkah awal memulai jualan setiap pagi/pergantian jaga.",
        icon = Icons.Default.LockOpen,
        steps = listOf(
            "Buka aplikasi ZoKas, lalu pilih nama akun Anda (Owner atau Kasir) dan masukkan PIN rahasia Anda.",
            "Jika Anda sudah mendaftarkan Sidik Jari di Pengaturan, cukup tempelkan jari pada sensor HP untuk masuk cepat.",
            "Saat pertama kali masuk ke halaman Kasir, akan muncul kotak 'Buka Kasir'.",
            "Ketik jumlah uang kembalian (Modal Awal) yang ada di laci uang saat ini (contoh: 200000), lalu tekan tombol 'MULAI'.",
            "Setelah selesai berjualan di akhir hari, ketuk ikon titik tiga di pojok kanan atas halaman Kasir -> pilih 'Tutup Kasir (Close Harian)', masukkan jumlah uang fisik di laci, lalu konfirmasi."
        ),
        importantNote = "Catatan: Selalu lakukan 'Tutup Kasir' setiap selesai jaga agar laporan uang fisik dan uang sistem tercatat rapi tanpa selisih."
    ),
    TutorialGuideTopic(
        id = "kasir_keranjang",
        category = "DASAR",
        title = "2. Cara Melayani Pesanan di Kasir",
        subtitle = "Memilih barang, mengatur jumlah, catatan pesanan, dan parkir pesanan.",
        icon = Icons.Default.ShoppingCart,
        steps = listOf(
            "Pada halaman 'Kasir', ketuk gambar atau nama produk untuk memasukkannya ke dalam keranjang belanja.",
            "Jika produk memiliki pilihan rasa/ukuran (Varian), pilih salah satu varian yang diminta pembeli.",
            "Untuk mencari barang dengan cepat, ketuk ikon Kaca Pembesar (Cari) di atas atau gunakan tombol Kamera untuk scan Barcode barang.",
            "Di bagian Keranjang: gunakan tombol (+) untuk menambah jumlah, (-) untuk mengurangi, atau ikon Tempat Sampah untuk menghapus barang.",
            "Ketuk ikon Pensil pada barang di keranjang jika pembeli meminta catatan khusus (contoh: 'Jangan pedas' atau 'Es sedikit').",
            "Jika pembeli ingin menambah barang lagi nanti, tekan tombol 'Parkir' untuk menyimpan pesanan sementara, dan tekan 'Lihat' untuk memanggilnya kembali."
        ),
        importantNote = "Tips Layar HP: Di layar HP, ketuk tombol hijau mengambang di kanan bawah ('Item | Rp...') untuk membuka rincian keranjang dan tombol Bayar."
    ),
    TutorialGuideTopic(
        id = "pembayaran_qris_bank",
        category = "DASAR",
        title = "3. Cara Menerima Pembayaran (Tunai, QRIS, Transfer)",
        subtitle = "Menyelesaikan transaksi dan menghitung uang kembalian otomatis.",
        icon = Icons.Default.Payments,
        steps = listOf(
            "Setelah pesanan di keranjang benar, tekan tombol 'BAYAR' di bagian paling bawah keranjang.",
            "Untuk Pembayaran TUNAI (CASH): Pilih tombol 'CASH', ketik nominal uang yang diberikan pembeli pada kolom 'Uang Diterima'. Aplikasi otomatis menampilkan nominal Kembalian.",
            "Untuk Pembayaran QRIS: Pilih tombol 'QRIS', tunjukkan kode QR di layar kepada pembeli untuk di-scan dari HP mereka.",
            "Untuk Pembayaran TRANSFER BANK: Pilih tombol 'Bank Transfer', sebutkan nomor rekening dan nominal yang tertera di layar kepada pembeli. Setelah uang masuk, centang kotak 'Saya sudah menerima transfer sesuai nominal'.",
            "Terakhir, tekan tombol 'SELESAIKAN PEMBAYARAN' di bagian bawah untuk menyimpan transaksi."
        ),
        importantNote = "Catatan: Anda bisa mengatur metode pembayaran apa saja yang aktif (serta mengisi nomor rekening & kode QRIS) melalui menu Pengaturan -> Metode Pembayaran."
    ),
    TutorialGuideTopic(
        id = "pembayaran_edc_interaktif",
        category = "DASAR",
        title = "4. Cara Pakai Pembayaran Mesin EDC (Semua Jalur)",
        subtitle = "Menghubungkan mesin EDC via Bluetooth, USB, Wi-Fi/TCP, atau Intent ECR.",
        icon = Icons.Default.CreditCard,
        steps = listOf(
            "Pada layar 'Pembayaran' setelah menekan tombol 'BAYAR', geser deretan tombol metode pembayaran lalu pilih 'EDC'.",
            "Aplikasi otomatis membentuk perintah ECR (ISO-8583 & JSON) beserta nomor Trace unik dan mengirimkannya ke jalur EDC yang dipilih.",
            "Pilih jalur koneksi EDC yang sesuai di perangkat Anda: 'OTOMATIS (Semua Jalur)', 'Bluetooth (SPP/RFCOMM)', 'USB Host / Serial', 'Wi-Fi / LAN (TCP Socket)' (isi IP & Port EDC), atau 'Aplikasi / Intent ECR'.",
            "Gesek atau tempelkan kartu Debit/Kredit pembeli pada mesin EDC. Jika mesin EDC terhubung, respon persetujuan (RC 00) dan No. Trace akan terisi otomatis; atau tekan 'Kirim Ulang' jika perlu.",
            "Pastikan 'No. Trace / Approval EDC' sesuai struk EDC, centang 'Transaksi mesin EDC telah disetujui (Approved)', lalu tekan 'SELESAIKAN PEMBAYARAN'."
        ),
        importantNote = "Tips EDC: Gunakan mode 'OTOMATIS (Semua Jalur)' agar ZoKas mencoba Bluetooth, USB, Wi-Fi/TCP, dan Intent ECR secara berurutan tanpa perlu pengaturan rumit."
    ),
    TutorialGuideTopic(
        id = "printer_struk",
        category = "DASAR",
        title = "5. Cara Pasang Printer Bluetooth & Cetak Struk",
        subtitle = "Menghubungkan printer kasir/dapur dan membagikan struk WhatsApp.",
        icon = Icons.Default.Print,
        steps = listOf(
            "Nyalakan alat printer Bluetooth Anda, lalu buka Pengaturan Bluetooth di HP/Tablet Anda dan pasangkan (Pair) terlebih dahulu.",
            "Buka aplikasi ZoKas -> pindah ke tab 'Pengaturan' (ikon roda gigi di bawah) -> ketuk kartu 'Printer Kasir'.",
            "Tekan tombol 'Pilih Printer Kasir', lalu pilih nama printer Bluetooth Anda dari daftar yang muncul hingga tertulis 'Terhubung'.",
            "Jika usaha Anda adalah Restoran/Kuliner dan memakai printer terpisah di dapur, atur juga pada bagian 'Printer Dapur'.",
            "Setiap transaksi selesai, struk akan tercetak otomatis. Anda juga bisa menekan 'Cetak Ulang' atau 'Kirim WhatsApp / Bagikan PDF' pada layar struk."
        ),
        importantNote = "Tips: Jika struk gagal tercetak, pastikan kertas thermal tidak terbalik dan lampu indikator baterai printer tidak berkedip merah."
    ),

    // =================================================================
    // SESI 6 — TUTORIAL LANJUTAN (PRODUK & BOM, CABANG, HR, CRM, KEUANGAN, BACKUP)
    // =================================================================
    TutorialGuideTopic(
        id = "produk_bom_bahan",
        category = "LANJUTAN",
        title = "6. Cara Tambah Produk, Resep (BOM) & Bahan Baku",
        subtitle = "Mengelola daftar menu, stok barang, dan pemotongan bahan mentah otomatis.",
        icon = Icons.Default.Inventory,
        steps = listOf(
            "Buka tab 'Produk' di menu bawah, lalu tekan tombol '+ Tambah Produk' di kanan bawah (atau ketuk produk yang sudah ada untuk mengedit).",
            "Isi Nama Produk, Kategori, Harga Beli (Modal), Harga Jual, dan Stok Awal. Jika produk berupa layanan (seperti potong rambut/servis), centang 'Produk Jasa'.",
            "Mengatur Bahan Baku & Resep (BOM) langsung dari Edit Produk: Di dalam form produk, lihat kotak 'Resep (BOM) & Bahan Baku'.",
            "Ketuk tombol 'Kelola Bahan Baku' untuk mendaftarkan bahan mentah (contoh: Kopi Bubuk 1000 gram, Susu 5000 ml, Gula).",
            "Setelah bahan baku terdaftar, ketuk tombol 'Atur Resep (BOM)', pilih bahan yang dipakai untuk 1 porsi produk ini (contoh: Kopi 15 gram), tekan 'Tambah', lalu tekan 'SIMPAN RESEP'."
        ),
        importantNote = "Keuntungan BOM: Setiap kali produk tersebut terjual di kasir, stok bahan baku mentah akan otomatis berkurang sesuai takaran resep!"
    ),
    TutorialGuideTopic(
        id = "modul_crm_member",
        category = "LANJUTAN",
        title = "7. Cara Pakai Modul Pelanggan (CRM) & Paket Membership",
        subtitle = "Mengaktifkan fitur member, poin belanja, diskon ulang tahun & paket kuota.",
        icon = Icons.Default.People,
        steps = listOf(
            "Fitur Pelanggan (CRM) bersifat opsional. Untuk mengaktifkannya, buka tab 'Pengaturan' -> ketuk 'Modul Toko' -> nyalakan saklar 'Modul CRM (Pelanggan)'.",
            "Setelah aktif, akan muncul kartu 'CRM & Pelanggan' di Pengaturan. Di dalamnya Anda bisa mengatur Diskon Ulang Tahun otomatis dan mengelola daftar pelanggan.",
            "Saat bertransaksi di Kasir, ketuk tulisan 'Pelanggan Umum' di atas daftar keranjang untuk memilih pelanggan tetap atau mendaftarkan pelanggan baru.",
            "Untuk usaha Jasa/Salon yang menjual paket kuota (contoh: Paket Cuci Mobil 5x), nyalakan saklar 'Paket Langganan (Membership)' di dalam 'Modul Toko'.",
            "Saat pelanggan yang memiliki paket berbelanja di kasir, ketuk tombol 'Gunakan Paket' pada item di keranjang agar harga otomatis menjadi Rp 0 (memotong kuota paket)."
        ),
        importantNote = "Catatan: Jika toko Anda hanya melayani jual-beli cepat tanpa mendata nama pembeli, matikan saja saklar Modul CRM agar tampilan kasir lebih ringkas."
    ),
    TutorialGuideTopic(
        id = "supplier_keuangan",
        category = "LANJUTAN",
        title = "8. Cara Catat Supplier (PO), Hutang, Piutang & Pengeluaran",
        subtitle = "Pembukuan uang keluar, kasbon pelanggan, dan belanja stok barang.",
        icon = Icons.Default.AccountBalanceWallet,
        steps = listOf(
            "Buka tab 'Pengaturan' -> ketuk 'Modul Toko' -> nyalakan saklar 'Supplier & Pembelian (PO)' serta 'Modul Keuangan (Finance)'.",
            "Belanja Stok (PO): Di dalam Modul Toko, ketuk 'Kelola Supplier' untuk menambah nama pemasok, lalu ketuk 'Buat Pembelian (PO)' saat barang datang agar stok produk otomatis bertambah.",
            "Jika belanja PO belum dibayar lunas (Tempo), tagihan akan masuk ke menu 'Buku Hutang (Supplier)' dan bisa Anda cicil/lunasi kapan saja.",
            "Buku Piutang (Bon Pelanggan): Jika ada pelanggan yang berhutang/kasbon, daftar tagihannya dapat dilihat dan dilunasi melalui menu 'Buku Piutang (Bon)'.",
            "Biaya Operasional: Ketuk 'Pengeluaran Operasional' untuk mencatat biaya listrik, sewa toko, plastik, atau makan harian agar laba bersih di laporan akurat."
        ),
        importantNote = "Tips Laporan: Semua pemasukan, HPP (modal), dan pengeluaran operasional akan dihitung otomatis menjadi Laba Bersih di tab 'Laporan'."
    ),
    TutorialGuideTopic(
        id = "hr_karyawan_gaji",
        category = "LANJUTAN",
        title = "9. Cara Kelola Karyawan, Absensi & Gaji (HR)",
        subtitle = "Menambah akun staf/kasir, mencatat kehadiran, dan cetak slip gaji.",
        icon = Icons.Default.Badge,
        steps = listOf(
            "Untuk menambah akun Kasir atau Staf: buka tab 'Pengaturan' -> ketuk 'Manajemen Akun' -> tekan tombol 'Kelola Karyawan'.",
            "Tambahkan nama staf, PIN login mereka, pilih peran (CASHIER untuk kasir, STAFF untuk absensi/dapur), dan isi gaji pokok.",
            "Untuk fitur absensi & penggajian lengkap: buka 'Modul Toko' di Pengaturan -> nyalakan saklar 'Modul Kepegawaian (HR)'.",
            "Kartu 'Kepegawaian (HR)' akan muncul di halaman Pengaturan dengan dua tombol utama: 'Absensi' (melihat jam masuk/pulang staf) dan 'Gaji (Payroll)'.",
            "Di menu 'Gaji (Payroll)', aplikasi otomatis menjumlahkan Gaji Pokok + Komisi Penjualan, dikurangi Kasbon/Potongan, dan siap dicetak menjadi Slip Gaji."
        ),
        importantNote = "Tips Absensi Cepat: Karyawan dengan peran 'STAFF' yang login di halaman awal akan otomatis tercatat absensinya lalu keluar otomatis dalam 3 detik."
    ),
    TutorialGuideTopic(
        id = "cabang_backup_excel",
        category = "LANJUTAN",
        title = "10. Cara Multi-Cabang, Transfer Stok & Backup Data",
        subtitle = "Mengelola lebih dari 1 cabang toko dan mengamankan data di HP.",
        icon = Icons.Default.Storefront,
        steps = listOf(
            "Multi-Cabang: Di bagian atas layar (di bawah nama toko) atau di kartu 'Multi-Cabang' pada Pengaturan, Anda dapat menambah cabang baru dan berpindah cabang aktif.",
            "Jika memiliki minimal 2 cabang, gunakan tombol 'Transfer Stok' untuk memindahkan stok barang dari gudang/cabang utama ke cabang lain secara tercatat.",
            "Ekspor/Impor Excel: Buka kartu 'Data & Backup' di Pengaturan -> tekan 'Ekspor Excel' untuk mengunduh daftar produk ke file Excel, atau 'Impor Excel' untuk memasukkan ratusan produk sekaligus.",
            "Backup Database (Sangat Penting): Di kartu 'Data & Backup', tekan tombol 'Backup' secara rutin (misal seminggu sekali) dan simpan file cadangan ke Google Drive atau memori HP.",
            "Jika Anda ganti HP baru, cukup instal ZoKas, buka kartu 'Data & Backup' -> tekan 'Restore' -> pilih file backup terakhir Anda."
        ),
        importantNote = "Peringatan Penting: Karena ZoKas bekerja 100% Offline (data tersimpan di dalam HP Anda), biasakan menekan tombol 'Backup' secara berkala agar data aman jika HP rusak/hilang."
    )
)

// ====================================================================
// CARD PINTASAN TUTORIAL DI HALAMAN PENGATURAN
// ====================================================================
@Composable
fun SettingsTutorialCard(
    onOpenTutorial: () -> Unit,
    language: String = "in"
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTutorial() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (language == "in") "Buku Panduan & Tutorial Lengkap" else "Complete Guide & Tutorials",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (language == "in")
                        "Cara pakai semua fitur & EDC langkah demi langkah"
                    else
                        "Step-by-step guide for all features & EDC",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ====================================================================
// DIALOG UTAMA PUSAT TUTORIAL INTERAKTIF (SESI 5 & SESI 6)
// ====================================================================
@Composable
fun TutorialCenterDialog(
    initialCategory: String = "SEMUA",
    onDismiss: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf(initialCategory) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedTopicId by remember { mutableStateOf<String?>(allTutorialTopics.firstOrNull()?.id) }
    var selectedTopicDetail by remember { mutableStateOf<TutorialGuideTopic?>(null) }
    var edcInteractiveChannel by remember { mutableStateOf(EdcChannel.AUTO) }

    val filteredTopics = remember(selectedFilter, searchQuery) {
        allTutorialTopics.filter { topic ->
            val matchCat = selectedFilter == "SEMUA" || topic.category == selectedFilter
            val matchQuery = searchQuery.isBlank() ||
                topic.title.contains(searchQuery, ignoreCase = true) ||
                topic.subtitle.contains(searchQuery, ignoreCase = true) ||
                topic.steps.any { it.contains(searchQuery, ignoreCase = true) }
            matchCat && matchQuery
        }
    }

    if (selectedTopicDetail != null) {
        val topic = selectedTopicDetail!!
        AlertDialog(
            onDismissRequest = { selectedTopicDetail = null },
            icon = {
                Icon(
                    topic.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    topic.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        topic.subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    topic.steps.forEachIndexed { idx, step ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                "${idx + 1}. ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                step,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            topic.importantNote,
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedTopicDetail = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Mengerti", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                zokasText("Pusat Panduan & Tutorial ZoKas", "ZoKas Guide & Tutorial Center"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                zokasText("Panduan mudah langkah demi langkah untuk semua fitur", "Easy step-by-step guide for all features"),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(zokasText("Cari panduan (cth: EDC, QRIS, resep, printer, gaji)...", "Search guide (e.g. EDC, QRIS, BOM, printer, payroll)..."), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Hapus", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filters = listOf(
                        "SEMUA" to zokasText("Semua Panduan (${allTutorialTopics.size})", "All Guides (${allTutorialTopics.size}")),
                        "DASAR" to zokasText("1. Panduan Dasar Kasir (5)", "1. Basic Cashier Guide (5")),
                        "LANJUTAN" to zokasText("2. Panduan Fitur Lanjutan (5)", "2. Advanced Features Guide (5"))
                    )
                    filters.forEach { (key, label) ->
                        val selected = selectedFilter == key
                        FilterChip(
                            selected = selected,
                            onClick = { selectedFilter = key },
                            label = {
                                Text(
                                    label,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected)
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTopics, key = { it.id }) { topic ->
                        val isExpanded = expandedTopicId == topic.id
                        val accentColor = if (topic.category == "DASAR")
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.tertiary

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            ),
                            border = if (isExpanded) BorderStroke(1.dp, accentColor) else null,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedTopicId = if (isExpanded) null else topic.id
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            topic.icon,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = accentColor.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (topic.category == "DASAR") zokasText("DASAR", "BASIC") else zokasText("LANJUTAN", "ADVANCED"),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = accentColor,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            topic.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            topic.subtitle,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Buka Panduan",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically(),
                                    exit = shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                    ) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(bottom = 10.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )

                                        topic.steps.forEachIndexed { index, stepText ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .clip(CircleShape)
                                                        .background(accentColor),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "${index + 1}",
                                                        color = if (topic.category == "DASAR")
                                                            MaterialTheme.colorScheme.onPrimary
                                                        else
                                                            MaterialTheme.colorScheme.onTertiary,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = stepText,
                                                    fontSize = 13.sp,
                                                    lineHeight = 18.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        // Simulasi interaktif jalur EDC di dalam topik tutorial EDC
                                        if (topic.id == "pembayaran_edc_interaktif") {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            val sampleCmd = remember(edcInteractiveChannel) {
                                                EdcConnectionManager.buildAutomaticEdcCommand(
                                                    amount = 50000L,
                                                    channel = edcInteractiveChannel,
                                                    customTrace = "123456"
                                                )
                                            }
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "Simulasi Interaktif Jalur & Command EDC (Contoh Rp 50.000):",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .horizontalScroll(rememberScrollState()),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        EdcChannel.entries.forEach { ch ->
                                                            FilterChip(
                                                                selected = edcInteractiveChannel == ch,
                                                                onClick = { edcInteractiveChannel = ch },
                                                                label = { Text(ch.label, fontSize = 10.5.sp) }
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "Fungsi Jalur: ${edcInteractiveChannel.desc}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Contoh Command Otomatis: ${sampleCmd.isoStyleMessage}",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Icon(
                                                    Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = accentColor,
                                                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = topic.importantNote,
                                                    fontSize = 11.sp,
                                                    fontStyle = FontStyle.Italic,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(onClick = { selectedTopicDetail = topic }) {
                                                Icon(
                                                    Icons.Default.OpenInNew,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = accentColor
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    zokasText("Buka Tampilan Fokus", "Open Focus View"),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = accentColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(zokasText("Mengerti & Tutup Panduan", "Got It & Close Guide"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
