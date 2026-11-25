package com.karyaku.kasir

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.karyaku.kasir.data.local.UserEntity
import java.io.File
import java.io.FileOutputStream

private val supportedLanguages = listOf(
    "id" to "Bahasa Indonesia",
    "en" to "English (Inggris)",
    "jv" to "Basa Jawa",
    "su" to "Basa Sunda",
    "mad" to "Bh\u00e2sa Madhur\u00e2"
)

private fun wizardText(
    lang: String,
    id: String,
    en: String,
    jv: String,
    su: String,
    mad: String
): String {
    return when (LanguageManager.normalizeLang(lang)) {
        "en" -> en
        "jv" -> jv
        "su" -> su
        "mad" -> mad
        else -> id
    }
}


private fun translateCatalogBusinessName(rawName: String, lang: String): String {
    if (LanguageManager.normalizeLang(lang) != "en") return rawName
    val exactMap = mapOf(
        "Warung Sembako / Kelontong" to "Grocery & Sundry Stall",
        "Warung Soto & Gulai" to "Soto & Gulai Stall",
        "Usaha Catering Harian" to "Daily Catering Service",
        "Pedagang Ayam Potong Pasar" to "Market Fresh Chicken Vendor",
        "Toko Alat Tulis & Fotokopi" to "Stationery & Copy Shop",
        "Bengkel Motor" to "Motorcycle Repair Shop",
        "Bengkel Mobil" to "Auto Repair Shop",
        "Kedai Kopi & Minuman" to "Coffee & Beverage Shop",
        "Toko Roti & Kue" to "Bakery & Cake Shop",
        "Toko Bangunan / Material" to "Building & Hardware Store",
        "Toko Pakaian / Butik" to "Clothing & Boutique Store",
        "Salon & Perawatan" to "Salon & Beauty Care",
        "Salon Kecantikan & Spa" to "Beauty Salon & Spa",
        "Repair HP & Elektronik" to "Phone & Electronics Repair",
        "Jahit & Permak Apparel" to "Tailoring & Apparel Alteration",
        "Service Cetak / Sablon / Percetakan" to "Printing, Screen Print & Press Service",
        "Shop Es Teh / Beverage Segar" to "Iced Tea & Fresh Drink Shop",
        "Stall Martabak & Terang Bulan" to "Martabak & Sweet Pancake Stall",
        "Vendor Daging Sapi Market" to "Market Fresh Beef Vendor",
        "Vendor Ikan Segar / Laut Market" to "Market Fresh Fish & Seafood Vendor",
        "Laundry Kiloan / Satuan" to "Laundry & Per-Piece Wash",
        "Barbershop / Pangkas Rambut" to "Barbershop"
    )
    exactMap[rawName]?.let { return it }

    var out = rawName
    val wordPairs = listOf(
        "Warung" to "Stall",
        "Kedai" to "Shop",
        "Toko" to "Store",
        "Usaha" to "Business",
        "Pedagang" to "Vendor",
        "Bengkel" to "Workshop",
        "Jasa" to "Service",
        "Layanan" to "Service",
        "Ayam Potong" to "Fresh Chicken",
        "Ayam" to "Chicken",
        "Pasar" to "Market",
        "Harian" to "Daily",
        "Alat Tulis" to "Stationery",
        "Fotokopi" to "Copy Center",
        "Motor" to "Motorcycle",
        "Mobil" to "Car",
        "Kopi" to "Coffee",
        "Minuman" to "Beverage",
        "Makanan" to "Food",
        "Roti & Kue" to "Bakery & Pastry",
        "Sembako" to "Groceries",
        "Kelontong" to "Sundries",
        "Bangunan" to "Building Materials",
        "Pakaian" to "Apparel",
        "Salon Kecantikan" to "Beauty Salon",
        "Salon" to "Salon",
        "Cuci" to "Wash",
        "Servis" to "Repair"
    )
    for ((idW, enW) in wordPairs) {
        out = out.replace(idW, enW, ignoreCase = true)
    }
    return out
}

private fun translateCatalogSectorGroup(rawSector: String, lang: String): String {
    if (LanguageManager.normalizeLang(lang) != "en") return rawSector
    val s = rawSector.trim().lowercase()
    return when {
        s.contains("kuliner") || s.contains("minuman") || s.contains("makanan") -> "Food & Beverages"
        s.contains("pasar") || s.contains("bahan segar") || s.contains("sembako") -> "Market & Fresh Produce"
        s.contains("retail") || s.contains("toko") -> "Retail & Stores"
        s.contains("jasa") || s.contains("perbaikan") || s.contains("bengkel") -> "Services & Repairs"
        else -> zokasText(rawSector, "General Business", lang)
    }
}

private fun translateOptionalModuleTitle(rawTitle: String, lang: String): String {
    if (LanguageManager.normalizeLang(lang) != "en") return rawTitle
    return when (rawTitle.trim()) {
        "Catatan Kas & Bon / Hutang" -> "Cash Ledger & Credit / Payables"
        "Harga Grosir / Bertingkat" -> "Wholesale / Tiered Pricing"
        "Pemasok & Kulakan (PO)" -> "Suppliers & Purchase Orders (PO)"
        "Mode Meja & Pesanan" -> "Table Mode & Order Types"
        "Jadwal / Reservasi" -> "Bookings & Reservations"
        "Data Pelanggan & Poin" -> "Customer Profiles & Points"
        "Modul Kepegawaian (HR)" -> "Human Resources (HR)"
        "Resep / BOM & Bahan Baku" -> "Recipes (BOM) & Raw Materials"
        else -> zokasText(rawTitle, null, lang)
    }
}

private fun translateOptionalModuleDesc(rawDesc: String, lang: String): String {
    if (LanguageManager.normalizeLang(lang) != "en") return rawDesc
    val d = rawDesc.trim().lowercase()
    return when {
        d.contains("kas") && (d.contains("bon") || d.contains("hutang")) ->
            "Track cash in/out, unpaid customer credit, and supplier bills."
        d.contains("grosir") || d.contains("timbangan") ->
            "Special pricing for specific quantities or weighed items."
        d.contains("pemasok") || d.contains("kulakan") || d.contains("po") ->
            "Record supplier purchases and restock inventory cleanly."
        d.contains("meja") ->
            "Enable table selection, dine-in/take-away, and kitchen flow."
        d.contains("reservasi") || d.contains("jadwal") ->
            "Manage customer appointment times and table reservations."
        d.contains("pelanggan") || d.contains("poin") ->
            "Save regular customer records, loyalty points, and birthday promos."
        else -> zokasText(rawDesc, null, lang)
    }
}

private fun modeDescriptionForLang(lang: String, modeKey: String): String {
    return when (modeKey) {
        "AWAM" -> wizardText(
            lang,
            "Mode Awam - Paling mudah & praktis, tanpa ribet mikirin pembukuan.",
            "Beginner Mode - Simplest & most practical, no complex accounting clutter.",
            "Mode Awam - Paling gampang & praktis, ora ribet mikir pembukuan.",
            "Modeu Awam - Pang gampilna & praktis, teu kedah pusing mikiran pembukuan.",
            "Mode Awam - Paleng ghampang & praktis, ta' rembi' mekker pembukuan."
        )
        "MUDAH" -> wizardText(
            lang,
            "Mode Mudah - Ramah belajar teknologi, tampil bertahap & mudah dipahami.",
            "Easy Mode - Beginner-friendly guidance, gradual & easy to understand.",
            "Mode Gampang - Cocog kanggo sinau teknologi, katon bertahap lan gampang dimangerteni.",
            "Modeu Gampang - Merenah diajar teknologi, tetela bertahap sareng gampil kahartos.",
            "Mode Ghampang - Cocog kaangguy ajar teknologi, katon bertahap ban ghampang epahame."
        )
        "STANDAR" -> wizardText(
            lang,
            "Mode Standar - Fitur usaha harian seimbang untuk stok, kas, & laporan.",
            "Standard Mode - Balanced daily business tools for stock, cash, & reports.",
            "Mode Standar - Fitur usaha saben dina seimbang kanggo stok, kas, lan laporan.",
            "Modeu Standar - Fitur usaha sapopoe saimbang pikeun stok, kas, sareng laporan.",
            "Mode Standar - Fitur usaha sabbhan are saembang kaangguy stok, kas, ban laporan."
        )
        else -> wizardText(
            lang,
            "Mode Lengkap - Tampilan & seluruh fitur penuh tersedia sejak awal.",
            "Full Mode - All screens & complete features available from the start.",
            "Mode Lengkap - Tampilan lan kabeh fitur wutuh kasedhiya wiwit awal.",
            "Modeu Lengkap - Tampilan sareng sadaya fitur lengkep sayogi ti mimiti.",
            "Mode Lengkel - Tampilan ban sakabbhina fitur jangkep bada molae awwal."
        )
    }
}

@Composable
fun SetupWizardScreen(
    settingsManager: SettingsManager,
    viewModel: PosViewModel,
    onSetupComplete: (UserEntity) -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity

    var acceptedPolicy by remember { mutableStateOf(false) }
    var showFullPolicyExpanded by remember { mutableStateOf(true) }
    var selectedLang by remember { mutableStateOf(LanguageManager.normalizeLang(settingsManager.language)) }

    var businessSearch by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedBusiness by remember {
        mutableStateOf(BusinessCatalog500.allBusinesses.first { it.name == "Warung Sembako / Kelontong" })
    }
    val filteredBusinesses = remember(businessSearch) {
        if (businessSearch.isBlank()) BusinessCatalog500.allBusinesses.take(60)
        else BusinessCatalog500.allBusinesses.filter {
            it.name.contains(businessSearch.trim(), ignoreCase = true) ||
                it.sectorGroup.contains(businessSearch.trim(), ignoreCase = true)
        }.take(60)
    }

    val moduleOptions = remember(selectedBusiness.mappedBusinessType) {
        BusinessCatalog500.optionalModulesFor(selectedBusiness.mappedBusinessType)
    }
    val enabledOptionalModules = remember { mutableStateMapOf<String, Boolean>() }
    var selectedMode by remember { mutableStateOf("AWAM") }

    var storeNameInput by remember { mutableStateOf(settingsManager.storeName ?: "") }
    var storeAddressInput by remember { mutableStateOf(settingsManager.storeAddress ?: "") }
    var storePhoneInput by remember { mutableStateOf(settingsManager.storePhone ?: "") }
    var storeFooterInput by remember {
        mutableStateOf(settingsManager.storeFooter ?: "Terima Kasih atas Kunjungan Anda")
    }
    var storeLogoPathInput by remember { mutableStateOf(settingsManager.storeLogoPath ?: "") }

    val logoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val outFile = File(context.filesDir, "store_logo_setup.jpg")
                FileOutputStream(outFile).use { out ->
                    inputStream?.copyTo(out)
                }
                inputStream?.close()
                storeLogoPathInput = outFile.absolutePath
                Toast.makeText(
                    context,
                    wizardText(
                        selectedLang,
                        "Logo toko berhasil dipilih!",
                        "Store logo selected!",
                        "Logo toko kasil dipilih!",
                        "Logo toko hasil dipilih!",
                        "Logo toko lastare epelee!"
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, wizardText(selectedLang, "Gagal memuat logo: ${e.message}", "Failed to load logo: ${e.message}", "Gagal ngemot logo: ${e.message}", "Gagal ngamuat logo: ${e.message}", "Gagal mamasok logo: ${e.message}"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    var username by remember { mutableStateOf("") }
    var pinCode by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    var loginMethod by remember { mutableStateOf("PIN") }
    var biometricReady by remember { mutableStateOf(false) }

    val paymentSelection = remember { mutableStateMapOf("CASH" to true, "QRIS" to false, "TRANSFER" to false) }
    var qrisInput by remember { mutableStateOf(settingsManager.qrisStaticPayload ?: "") }
    var qrisAutoDetectInput by remember { mutableStateOf(settingsManager.enableQrisAutoDetect) }
    var showQrisHelpInSetup by remember { mutableStateOf(false) }

    var bankNameInput by remember { mutableStateOf(settingsManager.bankName ?: "") }
    var bankAccInput by remember { mutableStateOf(settingsManager.bankAccountNumber ?: "") }
    var bankHolderInput by remember { mutableStateOf(settingsManager.bankAccountName ?: "") }

    val qrisScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val rawQr = result.contents?.trim()
        if (!rawQr.isNullOrEmpty()) {
            qrisInput = rawQr
            Toast.makeText(
                context,
                wizardText(
                    selectedLang,
                    if (QrisHelper.isValidQris(rawQr)) "QRIS Statis valid & berhasil diisi!" else "Kode QR terbaca. Pastikan payload QRIS valid.",
                    if (QrisHelper.isValidQris(rawQr)) "Valid Static QRIS scanned & filled!" else "QR code read. Ensure valid QRIS payload.",
                    "Kode QR kawaca lan kasil dilebokake!",
                    "Kodeu QR kabaca sareng hasil dieusian!",
                    "Kode QR kabaca ban lastare eesse'e!"
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = LanguageManager.tr("wizard_title", selectedLang),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )

        // 1. KEBIJAKAN PENGGUNA & PRIVASI (PANJANG & LENGKAP)
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showFullPolicyExpanded = !showFullPolicyExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = LanguageManager.tr("wizard_sec_policy", selectedLang),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (showFullPolicyExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val fullPolicyText = remember(selectedLang) {
                        wizardText(
                            selectedLang,
                            id = (
                                "KEBIJAKAN PENGGUNA & PRIVASI APLIKASI ZOKAS (POS)\n\n" +
                                "PASAL 1 - ARSITEKTUR OFFLINE-FIRST & PENYIMPANAN DATA\n" +
                                "1.1 ZoKas dirancang sebagai aplikasi Point of Sale (POS) berbasis offline-first yang berjalan secara lokal di perangkat Android pengguna.\n" +
                                "1.2 Seluruh data operasional usaha - meliputi katalog produk, varian, harga modal/jual, stok barang, bahan baku/resep (BOM), riwayat transaksi, shift kasir, catatan kas masuk/keluar, pelanggan, dan pengaturan toko - disimpan di penyimpanan lokal perangkat Anda.\n" +
                                "1.3 Aplikasi tetap dapat dioperasikan untuk transaksi kasir harian meskipun koneksi internet sedang terputus atau tidak tersedia.\n\n" +
                                "PASAL 2 - PRIVASI DATA & KENDALI PEMILIK USAHA\n" +
                                "2.1 Pengembang (DBSB Studio) tidak mewajibkan pengiriman database usaha ke server komersial milik pengembang untuk menjalankan fitur kasir utama.\n" +
                                "2.2 Pemilik usaha memiliki kendali penuh atas data yang dicadangkan (backup), diekspor ke format dokumen (Excel/PDF/JSON), maupun dikirim melalui kanal pilihan pemilik (seperti Google Drive, WhatsApp, atau Bot Telegram pribadi milik pemilik).\n" +
                                "2.3 Akses kamera, penyimpanan/galeri, Bluetooth printer, biometrik, dan notifikasi sistem hanya digunakan sesuai fitur yang diaktifkan atau diminta secara eksplisit oleh pengguna.\n\n" +
                                "PASAL 3 - KEAMANAN AKUN, PIN, BIOMETRIK & HAK AKSES\n" +
                                "3.1 Pengguna bertanggung jawab menjaga kerahasiaan PIN akun (Owner, Supervisor, Kasir, maupun Staf) dan tidak membagikannya kepada pihak yang tidak berwenang.\n" +
                                "3.2 PIN disimpan menggunakan hashing kriptografis di perangkat dan fitur otentikasi biometrik memanfaatkan layanan keamanan bawaan sistem operasi Android.\n" +
                                "3.3 Tindakan sensitif seperti void/refund transaksi, penghapusan massal, reset laporan, serta penghentian mode layar pelanggan (CFD) dilindungi verifikasi otorisasi agar risiko kecurangan atau kesalahan input dapat ditekan.\n\n" +
                                "PASAL 4 - METODE PEMBAYARAN (TUNAI, QRIS, TRANSFER MANUAL & EDC)\n" +
                                "4.1 Pada metode QRIS Statis, pengguna bertanggung jawab memastikan payload/kode QRIS yang dipindai atau dimasukkan merupakan QRIS resmi milik usaha yang bersangkutan.\n" +
                                "4.2 Pada metode Transfer Bank Manual, kasir/pengguna wajib memastikan dana benar-benar telah diterima di rekening tujuan sebelum menyelesaikan transaksi.\n" +
                                "4.3 Pada integrasi mesin EDC/jaringan lokal, keakuratan nomor referensi/trace dan status persetujuan pada struk mesin EDC menjadi tanggung jawab operator di titik pembayaran.\n\n" +
                                "PASAL 5 - MULTI-CABANG, TRANSFER STOK & PENCADANGAN (BACKUP)\n" +
                                "5.1 Pada pengoperasian multi-cabang atau multi-perangkat, setiap perangkat mencatat data pada cabang/terminal masing-masing dan penggabungan data dilakukan melalui paket sinkronisasi atau alur yang dipilih pengguna.\n" +
                                "5.2 Karena data utama berada pada perangkat lokal, pengguna wajib melakukan pencadangan (Backup) berkala ke memori eksternal atau layanan cloud pribadi (seperti Google Drive) guna mencegah kehilangan data akibat kerusakan fisik, kehilangan perangkat, penghapusan data aplikasi, atau instal ulang sistem.\n" +
                                "5.3 Pengembang tidak bertanggung jawab atas kehilangan data akibat kelalaian pencadangan rutin, manipulasi file sistem/database oleh pihak luar, atau kesalahan konfigurasi perangkat pengguna.\n\n" +
                                "PASAL 6 - KEWAJIBAN PENGGUNA & KEPATUHAN HUKUM\n" +
                                "6.1 Pengguna dilarang menggunakan aplikasi untuk aktivitas yang melanggar hukum atau peraturan perpajakan/perdagangan yang berlaku di wilayah operasional usaha.\n" +
                                "6.2 Perhitungan pajak, service charge, pembulatan tunai, serta pelaporan keuangan dalam aplikasi merupakan alat bantu operasional; pengguna tetap dianjurkan melakukan rekonsiliasi berkala sesuai kebijakan akuntansi usaha masing-masing.\n\n" +
                                "PASAL 7 - PERUBAHAN KEBIJAKAN & PERSETUJUAN\n" +
                                "7.1 Kebijakan pengguna dan privasi ini dapat diperbarui seiring pengembangan fitur aplikasi ZoKas.\n" +
                                "7.2 Dengan mencentang kotak persetujuan di bawah dan menekan tombol simpan/mulai, Anda menyatakan telah membaca, memahami, dan menyetujui seluruh ketentuan kebijakan pengguna dan privasi ZoKas."
                            ),
                            en = (
                                "ZOKAS POS USER TERMS & PRIVACY POLICY\n\n" +
                                "ARTICLE 1 - OFFLINE-FIRST ARCHITECTURE & LOCAL DATA STORAGE\n" +
                                "1.1 ZoKas is built as an offline-first Point of Sale (POS) application operating locally on the user's Android device.\n" +
                                "1.2 All business operational data - including product catalogs, variants, cost/selling prices, inventory stock, raw materials/recipes (BOM), transaction history, cashier shifts, cash flow logs, customers, and store settings - is stored locally on your device.\n" +
                                "1.3 Core daily cashier operations continue to function even when internet connectivity is unavailable or unstable.\n\n" +
                                "ARTICLE 2 - DATA PRIVACY & BUSINESS OWNER CONTROL\n" +
                                "2.1 The developer (DBSB Studio) does not require uploading your store database to developer-owned commercial servers to run core POS features.\n" +
                                "2.2 The business owner retains full control over data backups, document exports (Excel/PDF/JSON), and sharing through channels chosen by the owner (such as Google Drive, WhatsApp, or the owner's own Telegram bot).\n" +
                                "2.3 Permissions for camera, storage/gallery, Bluetooth printers, biometrics, and system notifications are used strictly for features triggered or enabled by the user.\n\n" +
                                "ARTICLE 3 - ACCOUNT SECURITY, PIN, BIOMETRICS & ACCESS CONTROL\n" +
                                "3.1 Users are responsible for keeping account PINs (Owner, Supervisor, Cashier, and Staff) confidential and preventing unauthorized access.\n" +
                                "3.2 PINs are stored using cryptographic hashing on-device, and biometric authentication relies on Android's native security framework.\n" +
                                "3.3 Sensitive actions such as transaction void/refund, batch deletion, report resets, and stopping Customer Facing Display (CFD) mode are guarded by authorization checks to reduce error and misuse.\n\n" +
                                "ARTICLE 4 - PAYMENT METHODS (CASH, QRIS, MANUAL TRANSFER & EDC)\n" +
                                "4.1 For Static QRIS, users are responsible for ensuring the scanned or entered QRIS payload belongs to the legitimate merchant account.\n" +
                                "4.2 For Manual Bank Transfer, cashiers/users must verify incoming funds in the destination account before completing the order.\n" +
                                "4.3 For EDC or local network integrations, verifying trace/approval numbers and approval status on the EDC slip remains the operator's responsibility at checkout.\n\n" +
                                "ARTICLE 5 - MULTI-BRANCH, STOCK TRANSFER & BACKUPS\n" +
                                "5.1 In multi-branch or multi-device setups, each device records data for its assigned branch/terminal, and merging records occurs via sync bundles or workflows initiated by the user.\n" +
                                "5.2 Because primary data resides locally on the device, users must perform regular Backups to safe storage or personal cloud (such as Google Drive) to prevent data loss from hardware damage, device loss, app data clearing, or OS reinstallation.\n" +
                                "5.3 The developer is not liable for data loss caused by missing regular backups, external file tampering, or device misconfiguration.\n\n" +
                                "ARTICLE 6 - USER OBLIGATIONS & LEGAL COMPLIANCE\n" +
                                "6.1 Users must not use the application for unlawful activities or in violation of applicable commercial or tax regulations.\n" +
                                "6.2 Tax calculations, service charges, cash rounding, and financial summaries are operational aids; users should perform periodic reconciliation according to their accounting practices.\n\n" +
                                "ARTICLE 7 - POLICY UPDATES & ACCEPTANCE\n" +
                                "7.1 These terms and privacy policies may be updated as ZoKas evolves.\n" +
                                "7.2 By checking the consent box below and tapping save/start, you confirm that you have read, understood, and agreed to ZoKas's User Terms & Privacy Policy."
                            ),
                            jv = (
                                "KEBIJAKAN PANGANGGO & PRIVASI APLIKASI ZOKAS\n\n" +
                                "PASAL 1 - SISTEM OFFLINE-FIRST & PENYIMPENAN DATA LOKAL\n" +
                                "1.1 ZoKas digawe minangka aplikasi kasir (POS) offline-first sing mlaku langsung ing HP/tablet Android sampeyan.\n" +
                                "1.2 Kabeh data usaha - kayata dhaptar barang, varian, rega modal/adol, stok barang, bahan baku/resep (BOM), riwayat transaksi, shift kasir, kas mlebu/metu, data pelanggan, lan setelan toko - disimpen ing memori lokal piranti sampeyan.\n" +
                                "1.3 Transaksi kasir saben dina tetep bisa mlaku sanajan ora ana jaringan internet.\n\n" +
                                "PASAL 2 - PRIVASI DATA & KENDALI PEMILIK TOKO\n" +
                                "2.1 Pangembang (DBSB Studio) ora meksa ngirim database toko menyang server komersial pangembang kanggo nglakokake fitur utama kasir.\n" +
                                "2.2 Pemilik usaha nduweni kendali penuh tumrap file cadangan (backup), ekspor laporan (Excel/PDF/JSON), lan pangiriman liwat piranti/akun pribadi (kayata Google Drive, WhatsApp, utawa Bot Telegram milik pemilik).\n" +
                                "2.3 Ijin kamera, galeri/file, printer Bluetooth, sidik jari, lan notifikasi mung digunakake manut fitur sing diaktifake panganggo.\n\n" +
                                "PASAL 3 - KEAMANAN AKUN, PIN, BIOMETRIK & HAK AKSES\n" +
                                "3.1 Panganggo wajib njaga rahasia PIN akun (Owner, Supervisor, Kasir, lan Staf) supaya ora disalahgunakake pihak liya.\n" +
                                "3.2 PIN disimpen nganggo hashing kriptografis ing piranti lan login biometrik migunakake sistem keamanan bawaan Android.\n" +
                                "3.3 Tindakan wigati kayata void/refund transaksi, mbusak barang akeh, reset laporan, lan mateni Layar Pelanggan (CFD) diwenehi verifikasi otorisasi.\n\n" +
                                "PASAL 4 - CARA PEMBAYARAN (TUNAI, QRIS, TRANSFER MANUAL & EDC)\n" +
                                "4.1 Kanggo QRIS Statis, panganggo kudu mesthekake kode/payload QRIS sing discan utawa dilebokake bener-bener duweke toko sing sah.\n" +
                                "4.2 Kanggo Transfer Bank Manual, kasir kudu mesthekake dhuwit wis mlebu ing rekening sadurunge transaksi dirampungake.\n" +
                                "4.3 Kanggo mesin EDC utawa jaringan lokal, nomer referensi/trace lan status disetujoni kudu dipriksa kasir nalika mbayar.\n\n" +
                                "PASAL 5 - MULTI-CABANG, TRANSFER STOK & BACKUP DATA\n" +
                                "5.1 Ing panggunaan multi-cabang utawa pirang-pirang HP, saben piranti nyathet data ing cabange dhewe-dhewe lan panggabungan data katindakake liwat paket sinkronisasi utawa alur sing dipilih panganggo.\n" +
                                "5.2 Amarga data utama ana ing HP, sampeyan kudu rutin nindakake Backup menyang panyimpenan aman utawa Google Drive supaya data aman yen HP rusak, ilang, utawa direset.\n" +
                                "5.3 Pangembang ora tanggung jawab yen data ilang amarga arang backup, owahing file saka njaba, utawa salah setelan piranti.\n\n" +
                                "PASAL 6 - KEWAJIBAN PANGANGGO & ATURAN HUKUM\n" +
                                "6.1 Aplikasi ora kena digunakake kanggo kegiatan sing nglanggar hukum utawa aturan dagang/pajak sing berlaku.\n" +
                                "6.2 Etungan pajak, service charge, pembulatan tunai, lan rekap laporan minangka piranti bantu; tetap lakokake pencocokan rutin manut kabutuhan usaha sampeyan.\n\n" +
                                "PASAL 7 - OWAHING KEBIJAKAN & PERSETUJUAN\n" +
                                "7.1 Kebijakan iki bisa dianyari manut pangrembakane aplikasi ZoKas.\n" +
                                "7.2 Kanthi nyenthang kothak ing ngisor iki lan mencet tombol simpen/miwiti, sampeyan nyatakake wis maca, ngarteni, lan nyarujuki Kebijakan Panganggo & Privasi ZoKas."
                            ),
                            su = (
                                "KABIJAKAN PAMAKÉ & PRIVASI APLIKASI ZOKAS\n\n" +
                                "PASAL 1 - SISTEM OFFLINE-FIRST & NEUNDEUN DATA LOKAL\n" +
                                "1.1 ZoKas dirarancang salaku aplikasi kasir (POS) offline-first anu jalan langsung dina HP/tablet Android pamake.\n" +
                                "1.2 Sadaya data usaha - sapertos daptar barang, varian, harga modal/jual, stok barang, bahan baku/resep (BOM), riwayat transaksi, shift kasir, kas asup/kaluar, palanggan, sareng setelan toko - disimpen sacara lokal dina alat anjeun.\n" +
                                "1.3 Proses jual-beli poean tetep tiasa lumangsung sanaos teu aya internet.\n\n" +
                                "PASAL 2 - PRIVASI DATA & KAWENANGAN NUPUNYA USAHA\n" +
                                "2.1 Pamekar (DBSB Studio) teu ngawajibkeun ngirim database toko ka server komersial pamekar pikeun ngajalankeun fitur utama kasir.\n" +
                                "2.2 Nu gaduh usaha gaduh kadali pinuh kana file cadangan (backup), ekspor laporan (Excel/PDF/JSON), sareng ngabagikeun ngalangkungan saluran pilihan nu gaduh (sapertos Google Drive, WhatsApp, atanapi Bot Telegram pribadi).\n" +
                                "2.3 Idin kamera, panyimpenan/galeri, printer Bluetooth, sidik ramo, sareng notifikasi ngan dianggo luyu sareng fitur anu diaktipkeun ku pamake.\n\n" +
                                "PASAL 3 - KAAMANAN AKUN, PIN, BIOMETRIK & HAK AKSES\n" +
                                "3.1 Pamake tanggel walerna ngajaga karusiaan PIN akun (Owner, Supervisor, Kasir, sareng Staf) supados henteu dianggo ku pihak anu teu wenang.\n" +
                                "3.2 PIN disimpen ngagunakeun hashing kriptografis dina alat sareng login biometrik ngagunakeun sistem kaamanan bawaan Android.\n" +
                                "3.3 Peta sensitip sapertos void/refund transaksi, mupus sakaligus, reset laporan, sareng ngeureunkeun Layar Palanggan (CFD) dijagi ku verifikasi otorisasi.\n\n" +
                                "PASAL 4 - METODEU PAMAYARAN (TUNAI, QRIS, TRANSFER MANUAL & EDC)\n" +
                                "4.1 Pikeun QRIS Statis, pamake kedah mastikeun payload/kodeu QRIS anu di-scan atanapi diasupkeun mangrupikeun QRIS resmi milik usaha nu sah.\n" +
                                "4.2 Pikeun Transfer Bank Manual, kasir kedah mastikeun artos parantos katampi dina rekening tujuan sateuacan transaksi direngsekeun.\n" +
                                "4.3 Pikeun mesin EDC atanapi jaringan lokal, nomer referensi/trace sareng status disatujuan kedah dipariksa ku kasir waktos mayar.\n\n" +
                                "PASAL 5 - MULTI-CABANG, TRANSFER STOK & CADANGAN (BACKUP)\n" +
                                "5.1 Dina panggunaan multi-cabang atanapi sababaraha alat, unggal alat nyatet data dina cabang/terminalna sewang-sewangan sarta ngahijikeun data dilakukeun ngalangkungan paket sinkronisasi atanapi alur anu dipilih pamake.\n" +
                                "5.2 Kusabab data utama aya dina alat lokal, pamake kedah rutin ngalakukeun Backup ka panyimpenan aman atanapi Google Drive supados data aman upami HP ruksak, leungit, atanapi di-reset.\n" +
                                "5.3 Pamekar teu tanggel walerna kana leungitna data alatan teu rutin backup, parobahan file ti luar, atanapi kasalahan setelan alat.\n\n" +
                                "PASAL 6 - KAWAJIBAN PAMAKE & PATUH HUKUM\n" +
                                "6.1 Pamake dilarang ngagunakeun aplikasi pikeun kagiatan anu ngalanggar hukum atanapi aturan dagang/pajak anu lumaku.\n" +
                                "6.2 Itungan pajak, service charge, pembulatan tunai, sareng rekap laporan mangrupikeun alat bantu operasional; tetep laksanakeun rekonsiliasi rutin luyu sareng kabutuhan usaha.\n\n" +
                                "PASAL 7 - PAROBIHAN KABIJAKAN & PASATUJUAN\n" +
                                "7.1 Kabijakan ieu tiasa diropea saluyu sareng kamekaran fitur aplikasi ZoKas.\n" +
                                "7.2 Ku cara nyentang kotak pasatujuan di handap sareng mencet tombol simpen/mimitian, anjeun netelakeun parantos maca, ngartos, sareng nyatujuan kana Kabijakan Pamake & Privasi ZoKas."
                            ),
                            mad = (
                                "KABIJAKAN ANGKGHUYAN & PRIVASI APLIKASI ZOKAS\n\n" +
                                "PASAL 1 - SISTEM OFFLINE-FIRST & PENYIMPENAN DATA LOKAL\n" +
                                "1.1 ZoKas erancang menangka aplikasi kasir (POS) offline-first se ajhalan langsung e HP/tablèt Android sampeyan.\n" +
                                "1.2 Sakabbhina data usaha - e antarana daftar barang, varian, argha modal/juwal, stok barang, bahan baku/resep (BOM), riwayat transaksi, shift kasir, kas tamaso/kalowar, pelanggan, ban atoran toko - esempèn e panyimpenan lokal HP sampeyan.\n" +
                                "1.3 Transaksi kasir sabbhan are pagghun bisa ajhalan maske tadha internet.\n\n" +
                                "PASAL 2 - PRIVASI DATA & KAWENANGAN PEMELEK TOKO\n" +
                                "2.1 Pangembang (DBSB Studio) ta' menta ngerem database toko ka server komersial pangembang kaangguy ajhalanaghi fitur otama kasir.\n" +
                                "2.2 Pemelek usaha andhi' kawenangan penuh atas file cadangan (backup), ekspor laporan (Excel/PDF/JSON), ban pangereman lewat pele'yan pemelek (kadhie' Google Drive, WhatsApp, otaba Bot Telegram dhibi').\n" +
                                "2.3 Idin kamera, galeri/file, printer Bluetooth, sidik jari, ban notifikasi coma eyangghuy asessuae fitur se eyaktifaghi sareng angghuy.\n\n" +
                                "PASAL 3 - KAAMANAN AKUN, PIN, BIOMETRIK & HAK AKSES\n" +
                                "3.1 Angghuy wajib ajaga rahasiana PIN akun (Owner, Supervisor, Kasir, ban Staf) sopaja ta' eyangghuy oreng laen se ta' andhi' hak.\n" +
                                "3.2 PIN esempèn kalaban hashing kriptografis e HP ban login biometrik ngangghuy sistem kaamanan ghabha'an Android.\n" +
                                "3.3 Lalakon sensitif kadhi' void/refund transaksi, mbuwang sakaligus, reset laporan, ban merenne Layar Pelanggan (CFD) ejaga kalaban verifikasi otorisasi.\n\n" +
                                "PASAL 4 - CARA MAJER (KONTAN, QRIS, TRANSFER MANUAL & EDC)\n" +
                                "4.1 Kaangguy QRIS Statis, angghuy kodhu masteaghi payload/kode QRIS se e-scan otaba emasokaghi bhener-bhener andhi'na usaha se sah.\n" +
                                "4.2 Kaangguy Transfer Bank Manual, kasir kodhu masteaghi pesse la tamaso' e rekening tojjuwan sabellunna transaksi emareaghi.\n" +
                                "4.3 Kaangguy messen EDC otaba jaringan lokal, nomer referensi/trace ban status esetujuwi kodhu epareksa kasir bakto majer.\n\n" +
                                "PASAL 5 - MULTI-CABANG, TRANSFER STOK & CADANGAN (BACKUP)\n" +
                                "5.1 E dhalem angghuyan multi-cabang otaba barang-barang HP, sabbhan HP nyatet data e cabang/terminalna dhibi' ban nyettongaghi data elakoni lewat paket sinkronisasi otaba alur se epele angghuy.\n" +
                                "5.2 Amargha data otama bada e HP lokal, angghuy kodhu rutin alakone Backup ka panyimpenan aman otaba Google Drive sopaja data aman mon HP rosak, elang, otaba di-reset.\n" +
                                "5.3 Pangembang ta' tangghung jawab mon data elang amargha arang backup, oba'na file dari lowar, otaba sala atoran HP.\n\n" +
                                "PASAL 6 - KAWAJIBAN ANGKGHUY & ATURAN HUKUM\n" +
                                "6.1 Angghuy ta' olle ngangghuy aplikasi kaangguy lalakon se alanggar hokom otaba aturan dagang/pajak se bada.\n" +
                                "6.2 Itongan pajak, service charge, pembulatan kontan, ban rekap laporan menangka pakakas bhanto; pagghun lakoni pencocokan rutin asessuae kabutowan usaha.\n\n" +
                                "PASAL 7 - OBA'NA KABIJAKAN & SAROJU\n" +
                                "7.1 Kabijakan areya bisa eanyare asessuae kamajuwan fitur aplikasi ZoKas.\n" +
                                "7.2 Kalaban nyentang kotak saroju e baba ban neken tombol simpen/molae, sampeyan anyataaghi la maca, ngarte, ban asarojuwi Kabijakan Angghuy ban Privasi ZoKas."
                            )
                        )
                    }
                    Text(
                        text = if (showFullPolicyExpanded) fullPolicyText else fullPolicyText.take(280) + "...",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { acceptedPolicy = !acceptedPolicy },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = acceptedPolicy, onCheckedChange = { acceptedPolicy = it })
                    Text(
                        text = wizardText(
                            selectedLang,
                            "Saya menyetujui kebijakan penggunaan & privasi ZoKas",
                            "I agree to ZoKas terms of use & privacy policy",
                            "Kula sarujuk marang kebijakan panganggo lan privasi ZoKas",
                            "Abdi satuju kana kabijakan pamake sareng privasi ZoKas",
                            "Kaula asarojuwi kabijakan angghuyan ban privasi ZoKas"
                        ),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 2. PILIHAN BAHASA
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(LanguageManager.tr("wizard_sec_lang", selectedLang), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                supportedLanguages.forEach { (code, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedLang = code
                            LanguageManager.setLanguage(context, code)
                            ZokasLocaleManager.setLanguage(context, code)
                            settingsManager.language = code
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedLang == code,
                            onClick = {
                                selectedLang = code
                                LanguageManager.setLanguage(context, code)
                                ZokasLocaleManager.setLanguage(context, code)
                                settingsManager.language = code
                            }
                        )
                        Text(label, fontSize = 13.sp)
                    }
                }
            }
        }

        // 3. DETAIL USAHA (PENCARIAN AMAN TANPA AUTO-CLOSE KEYBOARD & DROPDOWN KE ATAS)
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${LanguageManager.tr("wizard_sec_business", selectedLang)} (${BusinessCatalog500.allBusinesses.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = businessSearch,
                    onValueChange = { newText -> businessSearch = newText },
                    label = {
                        Text(
                            wizardText(
                                selectedLang,
                                "Cari Jenis Usaha",
                                "Search Business Type",
                                "Goleki Jinis Usaha",
                                "Milarian Jenis Usaha",
                                "Nyare Macem Usaha"
                            )
                        )
                    },
                    placeholder = {
                        Text(
                            wizardText(
                                selectedLang,
                                "Ketik misal: ayam potong, warung, bengkel, kopi...",
                                "Type e.g. chicken, warung, workshop, coffee...",
                                "Ketik tuladhane: pitik potong, warung, bengkel, kopi...",
                                "Ketik contona: hayam potong, warung, bengkel, kopi...",
                                "Ketik contona: ajam potong, warung, bengkel, kopi..."
                            ),
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (businessSearch.isNotEmpty()) {
                            IconButton(onClick = { businessSearch = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (businessSearch.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                    ) {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()).padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (filteredBusinesses.isEmpty()) {
                                Text(
                                    text = wizardText(
                                        selectedLang,
                                        "Jenis usaha tidak ditemukan.",
                                        "No matching business type found.",
                                        "Jinis usaha ora ketemu.",
                                        "Jenis usaha teu kapendak.",
                                        "Macem usaha ta' etemmo."
                                    ),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp)
                                )
                            } else {
                                filteredBusinesses.forEach { biz ->
                                    val isChosen = biz.id == selectedBusiness.id
                                    Surface(
                                        color = if (isChosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            selectedBusiness = biz
                                            businessSearch = ""
                                        }
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                            Text(translateCatalogBusinessName(biz.name, selectedLang), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                "${translateCatalogSectorGroup(biz.sectorGroup, selectedLang)} - ${biz.mappedBusinessType}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { dropdownExpanded = !dropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = wizardText(
                                selectedLang,
                                "Pilih: ${translateCatalogBusinessName(selectedBusiness.name, selectedLang)}",
                                "Selected: ${translateCatalogBusinessName(selectedBusiness.name, selectedLang)}",
                                "Pilihan: ${selectedBusiness.name}",
                                "Kapilih: ${selectedBusiness.name}",
                                "Peleyan: ${selectedBusiness.name}"
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.88f).heightIn(max = 260.dp)
                    ) {
                        filteredBusinesses.forEach { biz ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(translateCatalogBusinessName(biz.name, selectedLang), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${translateCatalogSectorGroup(biz.sectorGroup, selectedLang)} - ${biz.mappedBusinessType}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedBusiness = biz
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Text(
                    text = wizardText(
                        selectedLang,
                        "Jenis usaha otomatis terdeteksi: ${selectedBusiness.mappedBusinessType} (${translateCatalogSectorGroup(selectedBusiness.sectorGroup, selectedLang)})",
                        "Auto-detected type: ${selectedBusiness.mappedBusinessType} (${translateCatalogSectorGroup(selectedBusiness.sectorGroup, selectedLang)})",
                        "Jinis usaha otomatis: ${selectedBusiness.mappedBusinessType} (${translateCatalogSectorGroup(selectedBusiness.sectorGroup, selectedLang)})",
                        "Jenis usaha otomatis: ${selectedBusiness.mappedBusinessType} (${translateCatalogSectorGroup(selectedBusiness.sectorGroup, selectedLang)})",
                        "Macem usaha otomatis: ${selectedBusiness.mappedBusinessType} (${translateCatalogSectorGroup(selectedBusiness.sectorGroup, selectedLang)})"
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 4. REKOMENDASI MODUL (DEFAULT OFF)
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(LanguageManager.tr("wizard_sec_modules", selectedLang), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = wizardText(
                        selectedLang,
                        "Semua modul rekomendasi dibuat OFF di awal agar tidak berat. Anda boleh menyalakannya sekarang atau mengaktifkannya kapan saja di menu Setting.",
                        "All recommended modules start OFF to stay light. You can enable them now or anytime in Settings.",
                        "Kabeh modul disetel OFF ing wiwitan supaya entheng. Bisa diuripake saiki utawa kapan wae ing Setelan.",
                        "Sadaya modul disetel OFF ti mimiti supados hampang. Tiasa dihurungkeun ayeuna atanapi iraha wae dina Setelan.",
                        "Sakabbhina modul eator OFF e awwal sopaja enteng. Olle eodhi'aghi sateya otaba bila bhai e Atoran."
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                moduleOptions.forEach { mod ->
                    val isOn = enabledOptionalModules[mod.key] ?: false
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(translateOptionalModuleTitle(mod.title, selectedLang), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(translateOptionalModuleDesc(mod.description, selectedLang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isOn,
                            onCheckedChange = { enabledOptionalModules[mod.key] = it }
                        )
                    }
                }
            }
        }

        // 5. PILIH MODE AWAM / MUDAH / STANDAR / LENGKAP
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(LanguageManager.tr("wizard_sec_mode", selectedLang), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                listOf("AWAM", "MUDAH", "STANDAR", "LENGKAP").forEach { modeKey ->
                    val active = selectedMode == modeKey
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { selectedMode = modeKey },
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = active, onClick = { selectedMode = modeKey })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(modeDescriptionForLang(selectedLang, modeKey), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 6. IDENTITAS TOKO (LOGO + NAMA TOKO & TURUNANNYA) + AKUN & METODE LOGIN
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(LanguageManager.tr("wizard_sec_account", selectedLang), fontWeight = FontWeight.Bold, fontSize = 14.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { logoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (storeLogoPathInput.isNotBlank() && File(storeLogoPathInput).exists()) {
                            AsyncImage(
                                model = File(storeLogoPathInput),
                                contentDescription = wizardText(selectedLang, "Logo Toko", "Store Logo", "Logo Toko", "Logo Toko", "Logo Toko"),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = wizardText(
                                selectedLang,
                                "Logo Toko (Struk & Aplikasi)",
                                "Store Logo (Receipt & App)",
                                "Logo Toko (Struk & Aplikasi)",
                                "Logo Toko (Struk & Aplikasi)",
                                "Logo Toko (Struk & Aplikasi)"
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = wizardText(
                                selectedLang,
                                "Ketuk untuk memilih gambar logo dari galeri HP.",
                                "Tap to pick a logo image from device gallery.",
                                "Tutul kanggo milih gambar logo saka galeri HP.",
                                "Toel kanggo milih gambar logo tina galeri HP.",
                                "Teken kaangguy meleh ghambar logo dari galeri HP."
                            ),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { logoPickerLauncher.launch("image/*") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = wizardText(
                                    selectedLang,
                                    "Upload Logo",
                                    "Upload Logo",
                                    "Unggah Logo",
                                    "Unggah Logo",
                                    "Onggha Logo"
                                ),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = storeNameInput,
                    onValueChange = { storeNameInput = it },
                    label = {
                        Text(
                            wizardText(
                                selectedLang,
                                "Nama Toko *",
                                "Store Name *",
                                "Jeneng Toko *",
                                "Nami Toko *",
                                "Nyama Toko *"
                            )
                        )
                    },
                    placeholder = { Text(wizardText(selectedLang, "Contoh: Toko Berkah / Ayam Potong Bu Sari", "Example: Berkah Store / Fresh Chicken", "Tuladha: Toko Berkah / Pitik Potong", "Conto: Toko Berkah / Hayam Potong", "Conto: Toko Berkah / Ajam Potong"), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = storeAddressInput,
                    onValueChange = { storeAddressInput = it },
                    label = {
                        Text(
                            wizardText(
                                selectedLang,
                                "Alamat Toko",
                                "Store Address",
                                "Alamat Toko",
                                "Alamat Toko",
                                "Alamat Toko"
                            )
                        )
                    },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = storePhoneInput,
                    onValueChange = { storePhoneInput = it },
                    label = {
                        Text(
                            wizardText(
                                selectedLang,
                                "No. Telepon / WhatsApp Toko",
                                "Store Phone / WhatsApp",
                                "Nomer Telepon / WA Toko",
                                "Nomer Telepon / WA Toko",
                                "Nomer Telepon / WA Toko"
                            )
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = storeFooterInput,
                    onValueChange = { storeFooterInput = it },
                    label = {
                        Text(
                            wizardText(
                                selectedLang,
                                "Pesan Penutup Struk",
                                "Receipt Footer Message",
                                "Pesen Ngisor Struk",
                                "Pesen Handap Struk",
                                "Pessen Baba Struk"
                            )
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = {
                        Text(
                            wizardText(
                                selectedLang,
                                "Username / Nama Pengguna *",
                                "Username / Account Name *",
                                "Username / Jeneng Panganggo *",
                                "Username / Nami Pamake *",
                                "Username / Nyama Angghuy *"
                            )
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pinCode,
                    onValueChange = { raw -> if (raw.length <= 6 && raw.all { it.isDigit() }) pinCode = raw },
                    label = {
                        Text(
                            wizardText(
                                selectedLang,
                                "PIN / Password (4-6 Angka) *",
                                "PIN / Passcode (4-6 Digits) *",
                                "PIN / Sandi (4-6 Angka) *",
                                "PIN / Sandi (4-6 Angka) *",
                                "PIN / Sandi (4-6 Angka) *"
                            )
                        )
                    },
                    visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { loginMethod = "PIN" }) {
                        RadioButton(selected = loginMethod == "PIN", onClick = { loginMethod = "PIN" })
                        Text(wizardText(selectedLang, "Login PIN", "PIN Login", "Mlebu PIN", "Asup PIN", "Tamaso PIN"), fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        loginMethod = "BIOMETRIC"
                        if (activity != null) {
                            val tempOwner = UserEntity(
                                id = 1L,
                                name = username.trim().ifBlank { "Owner" },
                                pin = hashPin(pinCode.ifBlank { "0000" }),
                                role = "OWNER",
                                branchId = 1L
                            )
                            viewModel.registerBiometric(activity, tempOwner) { ok, msg ->
                                biometricReady = ok
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        RadioButton(selected = loginMethod == "BIOMETRIC", onClick = {
                            loginMethod = "BIOMETRIC"
                            if (activity != null) {
                                val tempOwner = UserEntity(
                                    id = 1L,
                                    name = username.trim().ifBlank { "Owner" },
                                    pin = hashPin(pinCode.ifBlank { "0000" }),
                                    role = "OWNER",
                                    branchId = 1L
                                )
                                viewModel.registerBiometric(activity, tempOwner) { ok, msg ->
                                    biometricReady = ok
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        })
                        Text(wizardText(selectedLang, "Login Biometrik", "Biometric Login", "Mlebu Biometrik", "Asup Biometrik", "Tamaso Biometrik"), fontSize = 13.sp)
                    }
                }
                if (loginMethod == "BIOMETRIC") {
                    Text(
                        text = if (biometricReady)
                            wizardText(selectedLang, "Status Biometrik: Aktif & siap dipakai", "Biometric Status: Active & ready", "Status Biometrik: Aktif", "Status Biometrik: Aktif", "Status Biometrik: Aktif")
                        else
                            wizardText(selectedLang, "Status Biometrik: Ketuk Login Biometrik untuk verifikasi sidik jari sekarang", "Biometric Status: Tap Biometric Login to verify fingerprint now", "Status Biometrik: Tutul kanggo verifikasi driji", "Status Biometrik: Toel kanggo verifikasi sidik ramo", "Status Biometrik: Teken kaangguy verifikasi jari"),
                        fontSize = 11.sp,
                        color = if (biometricReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 7. PEMBAYARAN LENGKAP: TUNAI, QRIS & TRANSFER MANUAL
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(LanguageManager.tr("wizard_sec_payment", selectedLang), fontWeight = FontWeight.Bold, fontSize = 14.sp)

                val cashChecked = paymentSelection["CASH"] ?: true
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { paymentSelection["CASH"] = !cashChecked },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = cashChecked, onCheckedChange = { paymentSelection["CASH"] = it })
                    Text(wizardText(selectedLang, "Tunai (CASH)", "Cash (CASH)", "Tunai (CASH)", "Tunai (CASH)", "Kontan (CASH)"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                val qrisChecked = paymentSelection["QRIS"] ?: false
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { paymentSelection["QRIS"] = !qrisChecked },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = qrisChecked, onCheckedChange = { paymentSelection["QRIS"] = it })
                    Text(wizardText(selectedLang, "QRIS Statis / Dinamis", "QRIS Static / Dynamic", "QRIS Statis / Dinamis", "QRIS Statis / Dinamis", "QRIS Statis / Dinamis"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (qrisChecked) {
                        TextButton(onClick = { showQrisHelpInSetup = !showQrisHelpInSetup }) {
                            Text(wizardText(selectedLang, "Panduan QRIS", "QRIS Guide", "Pandhuan QRIS", "Panduan QRIS", "Panduan QRIS"), fontSize = 11.sp)
                        }
                    }
                }

                if (qrisChecked) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (showQrisHelpInSetup) {
                                Text(
                                    text = wizardText(
                                        selectedLang,
                                        "1. Siapkan gambar/screenshot QRIS Statis resmi toko Anda.\n2. Tekan tombol Scan QRIS di samping untuk membaca payload otomatis (000201...) atau tempel manual.\n3. Aktifkan deteksi notifikasi jika ingin konfirmasi otomatis dari notifikasi e-wallet/bank.",
                                        "1. Prepare your store's official Static QRIS image/screenshot.\n2. Tap Scan QRIS to read payload automatically (000201...) or paste manually.\n3. Enable notification auto-detect to match payment notifications.",
                                        "1. Cepakake gambar/screenshot QRIS Statis toko sampeyan.\n2. Pencet Scan QRIS kanggo maca payload otomatis (000201...) utawa tempel manual.",
                                        "1. Cawiskeun gambar/screenshot QRIS Statis toko anjeun.\n2. Pencet Scan QRIS pikeun maca payload otomatis (000201...) atanapi tempel manual.",
                                        "1. Siyapaghi ghambar/screenshot QRIS Statis toko sampeyan.\n2. Teken Scan QRIS kaangguy maca payload otomatis (000201...) otaba tempel manual."
                                    ),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = qrisInput,
                                    onValueChange = { qrisInput = it },
                                    label = { Text("Payload QRIS (000201...)", fontSize = 11.sp) },
                                    maxLines = 3,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        val opts = ScanOptions().apply {
                                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                            setPrompt(wizardText(selectedLang, "Scan QRIS Toko", "Scan Store QRIS", "Scan QRIS Toko", "Scan QRIS Toko", "Scan QRIS Toko"))
                                            setCameraId(0)
                                            setBeepEnabled(false)
                                            setOrientationLocked(true)
                                        }
                                        qrisScanLauncher.launch(opts)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier.height(50.dp)
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Scan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = wizardText(
                                        selectedLang,
                                        "Deteksi Otomatis Notifikasi QRIS",
                                        "Auto-Detect QRIS Notifications",
                                        "Deteksi Otomatis Notifikasi QRIS",
                                        "Deteksi Otomatis Notifikasi QRIS",
                                        "Deteksi Otomatis Notifikasi QRIS"
                                    ),
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = qrisAutoDetectInput,
                                    onCheckedChange = { qrisAutoDetectInput = it }
                                )
                            }
                        }
                    }
                }

                val transferChecked = paymentSelection["TRANSFER"] ?: false
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { paymentSelection["TRANSFER"] = !transferChecked },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = transferChecked, onCheckedChange = { paymentSelection["TRANSFER"] = it })
                    Text(wizardText(selectedLang, "Transfer Bank Manual", "Manual Bank Transfer", "Transfer Bank Manual", "Transfer Bank Manual", "Transfer Bank Manual"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                if (transferChecked) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = bankNameInput,
                                onValueChange = { bankNameInput = it },
                                label = { Text(wizardText(selectedLang, "Nama Bank *", "Bank Name *", "Jeneng Bank *", "Nami Bank *", "Nyama Bank *"), fontSize = 12.sp) },
                                placeholder = { Text(wizardText(selectedLang, "Contoh: BCA, BRI, Mandiri, BNI", "Example: BCA, BRI, Mandiri, BNI", "Tuladha: BCA, BRI, Mandiri, BNI", "Conto: BCA, BRI, Mandiri, BNI", "Conto: BCA, BRI, Mandiri, BNI"), fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = bankAccInput,
                                onValueChange = { bankAccInput = it.filter { ch -> ch.isDigit() || ch == '-' || ch == ' ' } },
                                label = { Text(wizardText(selectedLang, "Nomor Rekening *", "Account Number *", "Nomer Rekening *", "Nomer Rekening *", "Nomer Rekening *"), fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = bankHolderInput,
                                onValueChange = { bankHolderInput = it },
                                label = { Text(wizardText(selectedLang, "Atas Nama Rekening *", "Account Holder Name *", "Atas Nama Rekening *", "Atas Nami Rekening *", "Atas Nyama Rekening *"), fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                if (!acceptedPolicy) {
                    Toast.makeText(
                        context,
                        wizardText(
                            selectedLang,
                            "Centang persetujuan kebijakan terlebih dahulu.",
                            "Please accept the policy first.",
                            "Centhang sarujuk kebijakan dhisik.",
                            "Centang pasatujuan kabijakan heula.",
                            "Centang saroju kabijakan dhimen."
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@Button
                }
                if (storeNameInput.isBlank()) {
                    Toast.makeText(
                        context,
                        wizardText(
                            selectedLang,
                            "Nama toko wajib diisi.",
                            "Store name is required.",
                            "Jeneng toko wajib diisi.",
                            "Nami toko wajib dieusian.",
                            "Nyama toko wajib eesse'e."
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@Button
                }
                if (username.isBlank()) {
                    Toast.makeText(
                        context,
                        wizardText(
                            selectedLang,
                            "Username wajib diisi.",
                            "Username is required.",
                            "Username wajib diisi.",
                            "Username wajib dieusian.",
                            "Username wajib eesse'e."
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@Button
                }
                if (pinCode.length < 4) {
                    Toast.makeText(
                        context,
                        wizardText(
                            selectedLang,
                            "PIN minimal 4 angka.",
                            "PIN must be at least 4 digits.",
                            "PIN minimal 4 angka.",
                            "PIN minimal 4 angka.",
                            "PIN minimal 4 angka."
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@Button
                }
                if (paymentSelection["QRIS"] == true && qrisInput.isBlank()) {
                    Toast.makeText(
                        context,
                        wizardText(
                            selectedLang,
                            "Isi atau scan payload QRIS terlebih dahulu.",
                            "Enter or scan QRIS payload first.",
                            "Lebokake utawa scan payload QRIS dhisik.",
                            "Eusian atanapi scan payload QRIS heula.",
                            "Esse'e otaba scan payload QRIS dhimen."
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@Button
                }
                if (paymentSelection["TRANSFER"] == true && (bankNameInput.isBlank() || bankAccInput.isBlank())) {
                    Toast.makeText(
                        context,
                        wizardText(
                            selectedLang,
                            "Nama bank dan nomor rekening wajib diisi untuk Transfer Manual.",
                            "Bank name and account number are required for Manual Transfer.",
                            "Jeneng bank lan nomer rekening wajib diisi kanggo Transfer Manual.",
                            "Nami bank sareng nomer rekening wajib dieusian pikeun Transfer Manual.",
                            "Nyama bank ban nomer rekening wajib eesse'e kaangguy Transfer Manual."
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@Button
                }

                settingsManager.language = selectedLang
                LanguageManager.setLanguage(context, selectedLang)
                ZokasLocaleManager.setLanguage(context, selectedLang)

                val cleanStoreName = storeNameInput.trim()
                settingsManager.storeName = cleanStoreName
                settingsManager.storeAddress = storeAddressInput.trim()
                settingsManager.storePhone = storePhoneInput.trim()
                settingsManager.storeFooter = storeFooterInput.trim().ifBlank { "Terima Kasih atas Kunjungan Anda" }
                if (storeLogoPathInput.isNotBlank() && File(storeLogoPathInput).exists()) {
                    settingsManager.storeLogoPath = storeLogoPathInput
                }

                settingsManager.businessType = selectedBusiness.mappedBusinessType
                settingsManager.businessDetailName = selectedBusiness.name
                settingsManager.appMode = selectedMode

                settingsManager.enableFinance = enabledOptionalModules["finance"] ?: false
                settingsManager.enableWholesale = enabledOptionalModules["wholesale"] ?: false
                settingsManager.enableSupplier = enabledOptionalModules["supplier"] ?: false
                settingsManager.isTableModeEnabled = enabledOptionalModules["table_mode"] ?: false
                settingsManager.enableReservation = enabledOptionalModules["reservation"] ?: false
                settingsManager.enableCrmModule = enabledOptionalModules["crm"] ?: false

                val chosenPayments = mutableSetOf<String>()
                if (paymentSelection["CASH"] == true) chosenPayments.add("CASH")
                if (paymentSelection["QRIS"] == true) chosenPayments.add("QRIS")
                if (paymentSelection["TRANSFER"] == true) chosenPayments.add("Bank Transfer")
                if (chosenPayments.isEmpty()) chosenPayments.add("CASH")

                settingsManager.paymentMethods = chosenPayments
                if (paymentSelection["QRIS"] == true) {
                    settingsManager.qrisStaticPayload = qrisInput.trim()
                    settingsManager.enableQrisAutoDetect = qrisAutoDetectInput
                }
                if (paymentSelection["TRANSFER"] == true) {
                    settingsManager.bankName = bankNameInput.trim()
                    settingsManager.bankAccountNumber = bankAccInput.trim()
                    settingsManager.bankAccountName = bankHolderInput.trim()
                    settingsManager.enableTransferAutoDetect = false
                }

                settingsManager.isBiometricEnabled = (loginMethod == "BIOMETRIC" && biometricReady)
                settingsManager.isSetupCompleted = true

                val adminEntity = UserEntity(
                    id = 1L,
                    name = username.trim(),
                    pin = hashPin(pinCode),
                    role = "OWNER",
                    totalCommission = 0L,
                    branchId = 1L
                )
                viewModel.addUser(username.trim(), pinCode, "OWNER", 0L, adminEntity)
                onSetupComplete(adminEntity)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(LanguageManager.tr("wizard_save_start", selectedLang), fontWeight = FontWeight.Bold)
        }
    }
}
