package com.karyaku.kasir

import android.content.Context
import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.karyaku.kasir.data.local.UserEntity

private data class BusinessTypePreset(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val recommendedModules: List<String>,
    val enableTableMode: Boolean,
    val enableOrderTypes: Boolean,
    val enableWholesale: Boolean,
    val enablePreOrder: Boolean,
    val enableMembership: Boolean,
    val enableReservation: Boolean,
    val enableSupplier: Boolean,
    val enableCrm: Boolean
)

private val businessTypePresets = listOf(
    BusinessTypePreset(
        key = "KULINER",
        title = "Kuliner (Restoran, Cafe, Warung)",
        subtitle = "Cocok untuk rumah makan, kedai kopi, bakery, & minuman.",
        icon = Icons.Default.Restaurant,
        recommendedModules = listOf(
            "Mode Meja Restoran (Dine-In & Gabung Meja)",
            "Tipe Pesanan (Dine-In, Take-Away, Delivery)",
            "Layar Dapur (KDS) & Printer Dapur",
            "Resep Produk (BOM) & Pemotongan Bahan Baku"
        ),
        enableTableMode = true,
        enableOrderTypes = true,
        enableWholesale = false,
        enablePreOrder = false,
        enableMembership = false,
        enableReservation = false,
        enableSupplier = true,
        enableCrm = false
    ),
    BusinessTypePreset(
        key = "RETAIL",
        title = "Retail (Toko, Minimarket, Grosir)",
        subtitle = "Cocok untuk toko kelontong, minimarket, elektronik, & bangunan.",
        icon = Icons.Default.Storefront,
        recommendedModules = listOf(
            "Harga Grosir Otomatis (Wholesale Min. Qty)",
            "Kode Unik Produk (SKU) & Scan Barcode Cepat",
            "Supplier & Pembelian Stok (Purchase Order)",
            "Multi-Varian Satuan (Biji, Renteng, Dus)"
        ),
        enableTableMode = false,
        enableOrderTypes = false,
        enableWholesale = true,
        enablePreOrder = false,
        enableMembership = false,
        enableReservation = false,
        enableSupplier = true,
        enableCrm = false
    ),
    BusinessTypePreset(
        key = "JASA",
        title = "Jasa (Salon, Laundry, Bengkel, Klinik)",
        subtitle = "Cocok untuk barbershop, klinik kecantikan, cuci kendaraan, & servis.",
        icon = Icons.Default.ContentCut,
        recommendedModules = listOf(
            "Paket Langganan / Kuota (Membership)",
            "Reservasi & Jadwal Booking Pelanggan",
            "Penugasan Staf / Terapis & Komisi per Layanan",
            "Modul CRM & Riwayat Pelanggan Setia"
        ),
        enableTableMode = false,
        enableOrderTypes = false,
        enableWholesale = false,
        enablePreOrder = false,
        enableMembership = true,
        enableReservation = true,
        enableSupplier = false,
        enableCrm = true
    ),
    BusinessTypePreset(
        key = "MUSIMAN",
        title = "Musiman (Toko Kue, Hampers, Event)",
        subtitle = "Cocok untuk usaha PO kue kering, katering, bazar, & musiman.",
        icon = Icons.Default.EventAvailable,
        recommendedModules = listOf(
            "Pre-Order (Atur Tanggal Pengambilan Pesanan)",
            "Voucher Promo & Diskon Happy Hour",
            "Parkir Pesanan (Hold Order) & Pembayaran DP/Bon",
            "Modul CRM Pelanggan untuk Repeat Order"
        ),
        enableTableMode = false,
        enableOrderTypes = true,
        enableWholesale = false,
        enablePreOrder = true,
        enableMembership = false,
        enableReservation = false,
        enableSupplier = false,
        enableCrm = true
    )
)

private const val DETAILED_ZOKAS_POLICY_TEXT = """
KEBIJAKAN PRIVASI, PERLINDUNGAN DATA & KETENTUAN PENGGUNAAN ZOKAS
Dikembangkan oleh: DBSB Studio (dbsb-studio.my.id)
Pembaruan Terakhir: November 2026

Harap baca seluruh klausul di bawah ini dengan saksama sebelum Anda mendaftarkan email dan menggunakan aplikasi ZoKas. Dengan mencentang persetujuan dan melanjutkan penggunaan aplikasi, Anda menyatakan telah memahami dan menyetujui seluruh ketentuan berikut:

PASAL 1 — PRINSIP ARSITEKTUR OFFLINE-FIRST & KEDAULATAN DATA
1.1 ZoKas dirancang sebagai aplikasi Point of Sale (POS) dan manajemen usaha berbasis offline-first. Seluruh data operasional bisnis—termasuk katalog produk, variasi harga, stok, resep/BOM, catatan shift kasir, riwayat transaksi, data pelanggan (CRM), data karyawan (HR), serta log audit—disimpan secara lokal di penyimpanan internal perangkat Anda (database Room).
1.2 DBSB Studio tidak mengoperasikan server pusat untuk menampung atau menjual data transaksi harian toko Anda. Anda memiliki kendali penuh atas data di perangkat Anda sendiri, termasuk melalui fitur Backup & Restore manual maupun integrasi mandiri ke layanan milik Anda.

PASAL 2 — PENDAFTARAN EMAIL PENGGUNA (GMAIL, YAHOO, DLL.)
2.1 Pada tahap awal instalasi, aplikasi meminta Anda memasukkan alamat email aktif (seperti Gmail, Yahoo Mail, Outlook, atau email domain usaha) sebagai identitas akun pengguna pada aplikasi.
2.2 Alamat email tersebut disimpan secara lokal di perangkat Anda untuk keperluan identitas akun, pencocokan koneksi antarperangkat (peer-to-peer / CFD), serta referensi dukungan/pencadangan yang Anda kelola sendiri.
2.3 Anda wajib memastikan alamat email yang dimasukkan adalah email sah yang Anda kuasai dan bertanggung jawab atas akurasi pengisiannya.

PASAL 3 — PENGGUNAAN IZIN PERANGKAT (KAMERA, JARINGAN, BLUETOOTH, BIOMETRIK)
3.1 Izin Kamera (CAMERA): Digunakan secara khusus untuk memindai barcode/SKU produk, memindai QR Code konfigurasi QRIS statis, atau memindai QR koneksi Layar Pelanggan (CFD). Proses pemindaian berjalan secara lokal di perangkat dan tidak mengirimkan tangkapan kamera ke server eksternal.
3.2 Izin Jaringan & Wi-Fi / Bluetooth / Perangkat Terdekat: Digunakan untuk komunikasi lokal antarperangkat (misalnya Layar Pelanggan Nirkabel / CFD atau printer thermal Bluetooth) serta akses jaringan apabila Anda menggunakan fitur eksternal atau tautan resmi.
3.3 Izin Biometrik & PIN: PIN keamanan dienkripsi/di-hash secara lokal, dan autentikasi sidik jari/wajah ditangani melalui sistem keamanan biometrik bawaan Android di perangkat Anda. Kami tidak menyimpan data sidik jari mentah Anda.

PASAL 4 — INTEGRASI LAYANAN PIHAK KETIGA (OPSIONAL)
4.1 Apabila Anda mengaktifkan fitur eksternal opsional (seperti notifikasi bot Telegram milik Anda sendiri, penyimpanan Google Sheets/Drive pribadi, atau berbagi struk/laporan via aplikasi pihak ketiga seperti WhatsApp), pengiriman data terjadi atas inisiatif dan konfigurasi Anda langsung ke layanan terkait.
4.2 Kebijakan privasi serta keamanan masing-masing layanan pihak ketiga tunduk pada ketentuan penyedia layanan tersebut.

PASAL 5 — TANGGUNG JAWAB OPERASIONAL, PAJAK & KEUANGAN
5.1 Fitur perhitungan harga, diskon, pembulatan tunai, pajak (PB1/PPN), laba/rugi, stok opname, dan laporan shift pada ZoKas merupakan alat bantu operasional dan pencatatan usaha.
5.2 Anda bertanggung jawab penuh atas ketepatan input data harga, stok fisik, kewajiban perpajakan usaha Anda, serta pembuatan backup data secara berkala agar data tidak hilang apabila perangkat rusak, hilang, tereset, atau aplikasi dihapus.

PASAL 6 — BATASAN TANGGUNG JAWAB & PEMBARUAN KEBIJAKAN
6.1 Aplikasi disediakan sebagaimana adanya ("as is") sesuai kemampuan perangkat dan sistem operasi Android yang digunakan. DBSB Studio tidak bertanggung jawab atas kerugian tidak langsung, kehilangan data akibat kelalaian backup, atau gangguan akibat masalah perangkat keras/jaringan pengguna.
6.2 Kebijakan ini dapat diperbarui sewaktu-waktu seiring pengembangan fitur atau penyesuaian regulasi. Versi terbaru akan diinformasikan melalui aplikasi atau situs resmi dbsb-studio.my.id.

PASAL 7 — KONTAK PENGEMBANG
Untuk pertanyaan, masukan, atau permintaan informasi terkait kebijakan privasi dan aplikasi ZoKas, Anda dapat menghubungi DBSB Studio melalui situs resmi https://dbsb-studio.my.id.
"""

fun isValidUserEmail(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.length > 120) return false
    return Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()
}

@Composable
fun SetupWizardScreen(
    settingsManager: SettingsManager,
    viewModel: PosViewModel,
    onSetupComplete: (UserEntity) -> Unit
) {
    val context = LocalContext.current
    val accountPrefs = remember {
        context.getSharedPreferences("zokas_account_prefs", Context.MODE_PRIVATE)
    }

    var loginEmail by remember {
        mutableStateOf(accountPrefs.getString("registered_email", "") ?: "")
    }
    var emailError by remember { mutableStateOf("") }
    var hasAcceptedPolicy by remember {
        mutableStateOf(accountPrefs.getBoolean("policy_accepted", false))
    }
    var isEmailLoggedIn by remember {
        mutableStateOf(
            accountPrefs.getBoolean("email_step_completed", false) &&
                isValidUserEmail(accountPrefs.getString("registered_email", "") ?: "") &&
                accountPrefs.getBoolean("policy_accepted", false)
        )
    }
    var showFullPolicyDialog by remember { mutableStateOf(false) }

    var storeName by remember { mutableStateOf(settingsManager.storeName ?: "") }
    var storeAddress by remember { mutableStateOf(settingsManager.storeAddress ?: "") }
    var selectedBusinessType by remember { mutableStateOf("RETAIL") }
    var applyRecommendedModules by remember { mutableStateOf(true) }

    var ownerName by remember { mutableStateOf("") }
    var ownerPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    val activePreset = remember(selectedBusinessType) {
        businessTypePresets.find { it.key == selectedBusinessType } ?: businessTypePresets.first()
    }

    if (showFullPolicyDialog) {
        Dialog(
            onDismissRequest = { showFullPolicyDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.86f),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kebijakan Privasi & Ketentuan",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "ZoKas • DBSB Studio (dbsb-studio.my.id)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { showFullPolicyDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = DETAILED_ZOKAS_POLICY_TEXT.trim(),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            hasAcceptedPolicy = true
                            showFullPolicyDialog = false
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SAYA SUDAH BACA & SETUJU", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // =========================================================================
    // TAHAP 0 (WAJIB SEBELUM ISI DATA TOKO): LOGIN EMAIL & PERSETUJUAN KEBIJAKAN DETAIL
    // =========================================================================
    if (!isEmailLoggedIn) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Masuk dengan Email Anda",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Daftarkan email aktif (Gmail, Yahoo, Outlook, dll.) dan setujui Kebijakan Privasi lengkap sebelum menyiapkan toko Anda.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. Alamat Email Pengguna",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = loginEmail,
                        onValueChange = {
                            loginEmail = it
                            emailError = ""
                        },
                        label = { Text("Email (cth: nama@gmail.com / nama@yahoo.com) *") },
                        placeholder = { Text("contoh@gmail.com") },
                        leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                        trailingIcon = {
                            if (loginEmail.isNotEmpty()) {
                                IconButton(onClick = { loginEmail = ""; emailError = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Hapus")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        isError = emailError.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (emailError.isNotEmpty()) {
                        Text(
                            text = emailError,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Mendukung Gmail, Yahoo Mail, Outlook, iCloud, maupun email usaha lainnya.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // KOTAK KEBIJAKAN PRIVASI DETAIL & JELAS SEBELUM SETUJU
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "2. Kebijakan Privasi & Ketentuan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { showFullPolicyDialog = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.OpenInFull, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Baca Penuh", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = DETAILED_ZOKAS_POLICY_TEXT.trim(),
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { hasAcceptedPolicy = !hasAcceptedPolicy }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = hasAcceptedPolicy,
                            onCheckedChange = { hasAcceptedPolicy = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Saya telah membaca secara lengkap dan menyetujui seluruh Kebijakan Privasi, penyimpanan data lokal offline-first, serta Ketentuan Penggunaan ZoKas.",
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val cleanEmail = loginEmail.trim()
                    if (!isValidUserEmail(cleanEmail)) {
                        emailError = "Masukkan format email yang valid (contoh: nama@gmail.com atau nama@yahoo.com)."
                        return@Button
                    }
                    if (!hasAcceptedPolicy) {
                        Toast.makeText(
                            context,
                            "Silakan baca dan centang persetujuan Kebijakan Privasi terlebih dahulu.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }
                    accountPrefs.edit()
                        .putString("registered_email", cleanEmail)
                        .putBoolean("policy_accepted", true)
                        .putBoolean("email_step_completed", true)
                        .putLong("email_registered_at", System.currentTimeMillis())
                        .apply()
                    isEmailLoggedIn = true
                },
                enabled = loginEmail.isNotBlank() && hasAcceptedPolicy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MASUK & LANJUT ISI DATA TOKO",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
        return
    }

    // =========================================================================
    // TAHAP LANJUTAN (SEPERTI BIASA): IDENTITAS TOKO, JENIS USAHA & AKUN OWNER
    // =========================================================================
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Badge Email Terdaftar + Tombol Ganti Email
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Email: ${loginEmail.trim()}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(
                    onClick = { isEmailLoggedIn = false },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Ganti", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.RocketLaunch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Selamat Datang di ZoKas",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Lengkapi profil usaha Anda agar sistem menyesuaikan fitur secara otomatis.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // STEP 1: IDENTITAS TOKO
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "1. Identitas Toko / Usaha",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("Nama Toko / Usaha *") },
                    placeholder = { Text("Contoh: Toko Berkah Jaya") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = storeAddress,
                    onValueChange = { storeAddress = it },
                    label = { Text("Alamat Singkat (Opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // STEP 2: PILIH JENIS USAHA & REKOMENDASI MODUL DINAMIS
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "2. Pilih Jenis Usaha Anda",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Rekomendasi modul akan menyesuaikan otomatis dengan kategori usaha yang Anda pilih:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                businessTypePresets.forEach { preset ->
                    val isSelected = preset.key == selectedBusinessType
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedBusinessType = preset.key },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else
                            MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = preset.icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = preset.subtitle,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedBusinessType = preset.key },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Rekomendasi Modul untuk ${activePreset.key}:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        activePreset.recommendedModules.forEach { modDesc ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = modDesc,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { applyRecommendedModules = !applyRecommendedModules },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = applyRecommendedModules,
                                onCheckedChange = { applyRecommendedModules = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = "Aktifkan otomatis rekomendasi modul ${activePreset.key}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // STEP 3: AKUN PEMILIK (OWNER)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "3. Akun Pemilik Toko (Owner)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = { Text("Nama Pemilik *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = ownerPin,
                        onValueChange = { raw -> if (raw.length <= 6 && raw.all { it.isDigit() }) ownerPin = raw },
                        label = { Text("PIN (4-6 Angka) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { raw -> if (raw.length <= 6 && raw.all { it.isDigit() }) confirmPin = raw },
                        label = { Text("Ulangi PIN *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Button(
            onClick = {
                if (storeName.isBlank()) {
                    Toast.makeText(context, "Nama toko wajib diisi!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (ownerName.isBlank()) {
                    Toast.makeText(context, "Nama pemilik wajib diisi!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (ownerPin.length < 4) {
                    Toast.makeText(context, "PIN minimal 4 digit angka!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (ownerPin != confirmPin) {
                    Toast.makeText(context, "Konfirmasi PIN tidak cocok!", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                settingsManager.storeName = storeName.trim()
                settingsManager.storeAddress = storeAddress.trim()
                settingsManager.businessType = activePreset.key

                if (applyRecommendedModules) {
                    settingsManager.isTableModeEnabled = activePreset.enableTableMode
                    settingsManager.enableOrderTypes = activePreset.enableOrderTypes
                    settingsManager.enableWholesale = activePreset.enableWholesale
                    settingsManager.enablePreOrder = activePreset.enablePreOrder
                    settingsManager.enableMembership = activePreset.enableMembership
                    settingsManager.enableReservation = activePreset.enableReservation
                    settingsManager.enableSupplier = activePreset.enableSupplier
                    settingsManager.enableCrmModule = activePreset.enableCrm
                } else {
                    settingsManager.isTableModeEnabled = activePreset.key == "KULINER"
                }

                settingsManager.enableHrModule = false
                settingsManager.isSetupCompleted = true

                val adminEntity = UserEntity(
                    id = 1L,
                    name = ownerName.trim(),
                    pin = hashPin(ownerPin),
                    role = "OWNER",
                    totalCommission = 0L,
                    branchId = 1L
                )
                viewModel.addUser(ownerName.trim(), ownerPin, "OWNER", 0L, adminEntity)
                onSetupComplete(adminEntity)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "SIMPAN & MULAI GUNAKAN ZOKAS",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
