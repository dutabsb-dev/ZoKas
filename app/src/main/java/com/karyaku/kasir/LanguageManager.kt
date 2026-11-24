package com.karyaku.kasir

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object LanguageManager {
    private const val PREFS_NAME = "zokas_auto_lang_prefs"
    private const val KEY_LANG = "selected_lang"

    private val _activeLanguage = mutableStateOf("in")
    val activeLanguage: String get() = _activeLanguage.value

    private val cacheByLang = mutableMapOf<String, MutableMap<String, String>>()

    // Kamus inti bahasa Indonesia & Inggris yang sudah ada di proyek
    private val enBase = mapOf(
        "selamat_datang" to "Welcome to ZoKas",
        "masukkan_pin" to "Enter your PIN to continue",
        "pin_6" to "6-Digit PIN",
        "login" to "LOGIN",
        "login_fingerprint" to "LOGIN WITH FINGERPRINT",
        "pin_salah" to "Incorrect PIN! Please try again.",
        "pin_harus_6" to "PIN must be 6 digits.",
        "cari_produk" to "Search Product",
        "ketik_produk" to "Type product name...",
        "hapus" to "Delete",
        "kasir" to "Cashier",
        "produk" to "Products",
        "laporan" to "Reports",
        "pengaturan" to "Settings",
        "dapur" to "Kitchen",
        "gabung_meja" to "Merge Tables",
        "buka_laci" to "Open Drawer",
        "pesanan_parkir" to "Hold Orders",
        "kas_masuk_keluar" to "Cash In/Out",
        "logout" to "LOGOUT",
        "mode_kontras" to "High Contrast Mode",
        "mode_kontras_desc" to "For low light environments",
        "mode_gelap" to "Dark Mode",
        "ukuran_font" to "Font Size (Accessibility)",
        "skala" to "Scale",
        "bahasa" to "Language",
        "manajemen_akun" to "Account Management",
        "login_sebagai" to "Logged in as",
        "ubah_pin" to "Change PIN",
        "kelola_karyawan" to "Manage Staff",
        "riwayat_shift" to "VIEW CASHIER SHIFT HISTORY",
        "printer_kasir" to "Cashier Printer",
        "terhubung" to "Connected",
        "belum_printer" to "No printer yet",
        "pilih_printer" to "SELECT PRINTER",
        "printer_dapur" to "Kitchen Printer (KDS)",
        "belum_printer_dapur" to "No kitchen printer",
        "pilih_printer_dapur" to "SELECT KITCHEN PRINTER",
        "profil_toko" to "Store Profile & Receipt",
        "nama_toko" to "Store Name",
        "alamat_toko" to "Store Address",
        "no_telp" to "Phone Number",
        "pesan_struk" to "Receipt Footer Message",
        "pajak" to "Tax (%) e.g: 11",
        "happy_hour" to "Happy Hour Promo",
        "happy_hour_desc" to "Automatic discount at specific times.",
        "mulai" to "Start (HH:mm)",
        "selesai" to "End (HH:mm)",
        "diskon_persen" to "Discount (%) e.g: 10",
        "keamanan_diskon" to "Discount Security & Currency",
        "max_diskon" to "Max Discount (Rp) - 0 for unlimited",
        "multi_currency" to "Enable Multi-Currency (USD)",
        "kurs_usd" to "Exchange Rate 1 USD to IDR",
        "data_backup" to "Data & Backup",
        "data_backup_desc" to "Backup all data to a file.",
        "backup" to "BACKUP",
        "restore" to "RESTORE",
        "manajemen_produk" to "Bulk Product Management (Excel)",
        "manajemen_produk_desc" to "Export or import products using a Excel file.",
        "ekspor_csv" to "EXPORT EXCEL",
        "impor_csv" to "IMPORT EXCEL",
        "log_aktivitas" to "Activity Log (Audit)",
        "belum_aktivitas" to "No activity yet.",
        "tambah_produk" to "Add New Product",
        "edit_produk" to "Edit Product",
        "produk_jasa" to "Service Product (No Stock)",
        "produk_combo" to "Combo/Package Product",
        "nama_produk" to "Product Name",
        "varian" to "Variations (Comma / | for Matrix)",
        "barcode" to "Barcode (Optional)",
        "kategori" to "Category",
        "harga_modal" to "Purchase Price / Cost (Rp)",
        "harga_jual" to "Selling Price (Rp)",
        "stok" to "Stock",
        "komisi" to "Cashier Commission / Item (Rp)",
        "expiry" to "Expiry Date",
        "simpan_produk" to "SAVE PRODUCT",
        "update_produk" to "UPDATE PRODUCT",
        "pilih_varian" to "Select Variant",
        "batal" to "Cancel",
        "pilih" to "Select",
        "tutup" to "Close",
        "today" to "Today",
        "this_month" to "This Month",
        "days_7" to "7 Days",
        "days_30" to "30 Days",
        "all" to "All",
        "total_revenue" to "Total Revenue",
        "success_trx" to "Successful Transactions",
        "cash_payment" to "Cash Payment",
        "non_cash_payment" to "BON/QRIS Payment",
        "gross_profit" to "Gross Profit",
        "net_profit" to "Net Profit",
        "total_cogs" to "Total COGS",
        "view_detail_report" to "VIEW DETAILED FINANCIAL REPORT",
        "inventory_value" to "Inventory Asset Value (COGS)",
        "inventory_desc" to "Total current stock value",
        "last_7_days_chart" to "Last 7 Days Chart",
        "financial_report" to "Financial Report",
        "period" to "Period",
        "revenue" to "REVENUE",
        "expenses" to "EXPENSES",
        "cash_sales" to "Cash Sales",
        "non_cash_sales" to "Non-Cash Sales",
        "cash_in_add" to "Cash In (Additional)",
        "total_cogs_cost" to "Total COGS (Cost)",
        "cash_out_op" to "Cash Out (Operational)",
        "expiring_items" to "Expiring Items",
        "low_stock_warning" to "Low Stock Warning!",
        "low_stock_desc" to "There are products with stock <= 5.",
        "staff_performance" to "Staff Performance & Commission (All-Time)",
        "no_cashier_data" to "No cashier data yet.",
        "trx_period" to "Transactions (Current Period)",
        "total_commission" to "Total Commission",
        "transaction_history" to "Transaction History",
        "no_trx_found" to "No transactions found for this period.",
        "receipt" to "Receipt",
        "customer" to "Customer",
        "transaction_details" to "Transaction Details",
        "date" to "Date",
        "loading" to "Loading...",
        "note" to "Note",
        "total" to "Total",
        "refund" to "REFUND",
        "pay_bon" to "PAY BON",
        "print" to "PRINT",
        "close" to "Close",
        "supervisor_auth" to "Supervisor Authorization",
        "enter_pin_cancel" to "Enter Supervisor/Owner PIN to cancel this transaction:",
        "supervisor_pin" to "Supervisor PIN",
        "cancel_trx" to "CANCEL TRANSACTION",
        "refund_auth" to "Refund Authorization",
        "enter_pin" to "Enter Supervisor/Owner PIN:",
        "pin" to "PIN",
        "bon_payment" to "BON Payment",
        "total_bill" to "Total Bill",
        "cash_received" to "Cash Received",
        "pay" to "PAY",
        "voided_msg" to "THIS TRANSACTION HAS BEEN VOIDED",
        "unpaid_msg" to "THIS TRANSACTION IS UNPAID (BON)",
        "void" to "VOID",
        "bon" to "BON",
        "tipe_usaha" to "Business Type",
        "tipe_usaha_desc" to "Select the most suitable business type. App menus will adapt automatically.",
        "kepegawaian_hr" to "Human Resources (HR)",
        "kepegawaian_hr_desc" to "Manage daily attendance and employee payroll.",
        "absensi" to "Attendance",
        "gaji_payroll" to "Payroll",
        "multi_cabang" to "Multi-Branch",
        "multi_cabang_desc" to "Manage your store branches",
        "modul_toko" to "Store Modules",
        "modul_toko_desc" to "Adjust features to your business needs.",
        "pembayaran_qris" to "QRIS Payment",
        "transfer_bank_manual" to "Manual Bank Transfer",
        "belum_diatur" to "Not configured",
        "data_dan_backup" to "Data & Backup",
        "cadangkan_data" to "Backup all data to a file.",
        "manajemen_produk_massal" to "Bulk Product Management (Excel)",
        "manajemen_produk_massal_desc" to "Export or import product lists at once using an Excel file (XLSX).",
        "ekspor_excel" to "EXPORT EXCEL",
        "impor_excel" to "IMPORT EXCEL",
        "log_aktivitas_audit" to "Activity Log (Audit)",
        "aktivitas_tercatat" to "activities recorded",
        "menampilkan_30_aktivitas" to "Showing latest 30 activities.",
        "toko_pret" to "Store Profile"
    )

    private val idBase = mapOf(
        "selamat_datang" to "Selamat Datang di ZoKas",
        "masukkan_pin" to "Masukkan PIN Anda untuk melanjutkan",
        "pin_6" to "PIN 6-Digit",
        "login" to "MASUK",
        "login_fingerprint" to "MASUK DENGAN SIDIK JARI",
        "pin_salah" to "PIN salah! Silakan coba lagi.",
        "pin_harus_6" to "PIN harus 6 digit angka.",
        "cari_produk" to "Cari Produk",
        "ketik_produk" to "Ketik nama produk...",
        "hapus" to "Hapus",
        "kasir" to "Kasir",
        "produk" to "Produk",
        "laporan" to "Laporan",
        "pengaturan" to "Setting",
        "dapur" to "Dapur",
        "gabung_meja" to "Gabung Meja",
        "buka_laci" to "Buka Laci",
        "pesanan_parkir" to "Pesanan Parkir",
        "kas_masuk_keluar" to "Kas Masuk/Keluar",
        "logout" to "LOGOUT (KELUAR)",
        "mode_kontras" to "Mode Kontras Tinggi",
        "mode_kontras_desc" to "Untuk lingkungan minim cahaya",
        "mode_gelap" to "Mode Gelap",
        "ukuran_font" to "Ukuran Font (Aksesibilitas)",
        "skala" to "Skala",
        "bahasa" to "Bahasa",
        "manajemen_akun" to "Manajemen Akun",
        "login_sebagai" to "Login sebagai",
        "ubah_pin" to "Ubah PIN",
        "kelola_karyawan" to "Kelola Karyawan",
        "riwayat_shift" to "LIHAT RIWAYAT SHIFT KASIR",
        "printer_kasir" to "Printer Kasir",
        "terhubung" to "Terhubung",
        "belum_printer" to "Belum ada printer",
        "pilih_printer" to "PILIH PRINTER",
        "printer_dapur" to "Printer Dapur (KDS)",
        "belum_printer_dapur" to "Belum ada printer dapur",
        "pilih_printer_dapur" to "PILIH PRINTER DAPUR",
        "profil_toko" to "Profil Toko & Struk",
        "nama_toko" to "Nama Toko",
        "alamat_toko" to "Alamat Toko",
        "no_telp" to "No. Telepon",
        "pesan_struk" to "Pesan Bawah Struk",
        "pajak" to "Pajak (%) cth: 11",
        "happy_hour" to "Happy Hour Promo",
        "happy_hour_desc" to "Diskon otomatis pada jam tertentu.",
        "mulai" to "Mulai (HH:mm)",
        "selesai" to "Selesai (HH:mm)",
        "diskon_persen" to "Diskon (%) cth: 10",
        "keamanan_diskon" to "Keamanan Diskon & Mata Uang",
        "max_diskon" to "Max Diskon (Rp) - isi 0 jika tanpa batas",
        "multi_currency" to "Aktifkan Multi-Currency (USD)",
        "kurs_usd" to "Kurs 1 USD ke IDR",
        "data_backup" to "Data & Backup",
        "data_backup_desc" to "Cadangkan seluruh data ke file.",
        "backup" to "BACKUP",
        "restore" to "RESTORE",
        "manajemen_produk" to "Manajemen Produk Massal (Excel)",
        "manajemen_produk_desc" to "Ekspor atau impor daftar produk sekaligus.",
        "ekspor_csv" to "EKSPOR EXCEL",
        "impor_csv" to "IMPOR EXCEL",
        "log_aktivitas" to "Log Aktivitas (Audit)",
        "belum_aktivitas" to "Belum ada aktivitas.",
        "tambah_produk" to "Tambah Produk Baru",
        "edit_produk" to "Edit Produk",
        "produk_jasa" to "Produk Jasa (Tanpa Stok)",
        "produk_combo" to "Produk Combo/Paket",
        "nama_produk" to "Nama Produk",
        "varian" to "Varian (Pisah koma / | untuk Matrix)",
        "barcode" to "Barcode (Opsional)",
        "kategori" to "Kategori",
        "harga_modal" to "Harga Beli / Modal (Rp)",
        "harga_jual" to "Harga Jual (Rp)",
        "stok" to "Stok",
        "komisi" to "Komisi Kasir / Item (Rp)",
        "expiry" to "Expiry",
        "simpan_produk" to "SIMPAN PRODUK",
        "update_produk" to "UPDATE PRODUK",
        "pilih_varian" to "Pilih Varian",
        "batal" to "Batal",
        "pilih" to "Pilih",
        "tutup" to "Tutup",
        "today" to "Hari Ini",
        "this_month" to "Bulan Ini",
        "days_7" to "7 Hari",
        "days_30" to "30 Hari",
        "all" to "Semua",
        "total_revenue" to "Total Omzet",
        "success_trx" to "Transaksi Sukses",
        "cash_payment" to "Pembayaran Tunai",
        "non_cash_payment" to "Pembayaran BON/QRIS",
        "gross_profit" to "Laba Kotor",
        "net_profit" to "Laba Bersih",
        "total_cogs" to "Total Modal (HPP)",
        "view_detail_report" to "LIHAT LAPORAN KEUANGAN DETAIL",
        "inventory_value" to "Nilai Aset Gudang (HPP)",
        "inventory_desc" to "Total nilai stok barang saat ini",
        "last_7_days_chart" to "Grafik 7 Hari Terakhir",
        "financial_report" to "Laporan Keuangan",
        "period" to "Periode",
        "revenue" to "PENDAPATAN",
        "expenses" to "PENGELUARAN",
        "cash_sales" to "Penjualan Tunai",
        "non_cash_sales" to "Penjualan Non-Tunai",
        "cash_in_add" to "Kas Masuk (Tambahan)",
        "total_cogs_cost" to "Total HPP (Modal)",
        "cash_out_op" to "Kas Keluar (Operasional)",
        "expiring_items" to "Barang Mendekati Expired",
        "low_stock_warning" to "Peringatan Stok Menipis!",
        "low_stock_desc" to "Ada produk dengan stok <= 5.",
        "staff_performance" to "Kinerja & Komisi Kasir (All-Time)",
        "no_cashier_data" to "Belum ada data kasir.",
        "trx_period" to "Transaksi (Periode Ini)",
        "total_commission" to "Total Komisi",
        "transaction_history" to "Riwayat Transaksi",
        "no_trx_found" to "Tidak ada transaksi pada periode ini.",
        "receipt" to "Struk",
        "customer" to "Pelanggan",
        "transaction_details" to "Rincian Transaksi",
        "date" to "Tanggal",
        "loading" to "Memuat...",
        "note" to "Catatan",
        "total" to "Total",
        "refund" to "REFUND",
        "pay_bon" to "BAYAR BON",
        "print" to "CETAK",
        "close" to "Tutup",
        "supervisor_auth" to "Otorisasi Supervisor",
        "enter_pin_cancel" to "Masukkan PIN Supervisor/Owner untuk membatalkan transaksi ini:",
        "supervisor_pin" to "PIN Supervisor",
        "cancel_trx" to "BATALKAN TRANSAKSI",
        "refund_auth" to "Otorisasi Refund",
        "enter_pin" to "Masukkan PIN Supervisor/Owner:",
        "pin" to "PIN",
        "bon_payment" to "Pelunasan Bon",
        "total_bill" to "Total Tagihan",
        "cash_received" to "Uang Diterima",
        "pay" to "BAYAR",
        "voided_msg" to "TRANSAKSI INI TELAH DIBATALKAN (VOID)",
        "unpaid_msg" to "TRANSAKSI INI BELUM DIBAYAR (BON)",
        "void" to "VOID",
        "bon" to "BON",
        "tipe_usaha" to "Tipe Usaha",
        "tipe_usaha_desc" to "Pilih tipe usaha yang paling sesuai. Menu aplikasi akan menyesuaikan otomatis.",
        "kepegawaian_hr" to "Kepegawaian (HR)",
        "kepegawaian_hr_desc" to "Kelola absensi harian dan slip gaji karyawan.",
        "absensi" to "Absensi",
        "gaji_payroll" to "Gaji (Payroll)",
        "multi_cabang" to "Multi-Cabang",
        "multi_cabang_desc" to "Kelola cabang toko Anda",
        "modul_toko" to "Modul Toko",
        "modul_toko_desc" to "Sesuaikan fitur dengan kebutuhan bisnis.",
        "pembayaran_qris" to "Pembayaran QRIS",
        "transfer_bank_manual" to "Transfer Bank Manual",
        "belum_diatur" to "Belum diatur",
        "data_dan_backup" to "Data & Backup",
        "cadangkan_data" to "Cadangkan seluruh data ke file.",
        "manajemen_produk_massal" to "Manajemen Produk Massal (Excel)",
        "manajemen_produk_massal_desc" to "Ekspor atau impor daftar produk sekaligus menggunakan file Excel (XLSX).",
        "ekspor_excel" to "EKSPOR EXCEL",
        "impor_excel" to "IMPOR EXCEL",
        "log_aktivitas_audit" to "Log Aktivitas (Audit)",
        "aktivitas_tercatat" to "aktivitas tercatat",
        "menampilkan_30_aktivitas" to "Menampilkan 30 dari %d aktivitas terbaru.",
        "toko_pret" to "Profil Toko"
    )

    // Aturan translasi otomatis berbasis frasa & kata kunci untuk Bahasa Jawa (jv), Sunda (su), dan Madura (mad)
    private val phraseRules = listOf(
        // Frasa umum
        Triple("Selamat Datang", "Mangga Rawuh", "Wilujeng Sumping") to "Dhateng kalayan slamet",
        Triple("Masukkan PIN", "Lebokna PIN", "Lebetkeun PIN") to "Mamasok PIN",
        Triple("Cari Produk", "Goleki Produk", "Milarian Produk") to "Nyare Produk",
        Triple("Tambah Produk", "Tambah Produk", "Tambah Produk") to "Namba Produk",
        Triple("Simpan Produk", "Simpen Produk", "Simpen Produk") to "Nyimpen Produk",
        Triple("Total Omzet", "Total Pendapatan", "Total Panghasilan") to "Total Pangaselan",
        Triple("Pembayaran Tunai", "Bayar Kontan", "Bayar Tunai") to "Majer Kontan",
        Triple("Riwayat Transaksi", "Riwayat Transaksi", "Riwayat Transaksi") to "Riwayat Transaksi",
        Triple("Tutup Kasir", "Tutup Kasir", "Tutup Kasir") to "Notop Kasir",
        Triple("Buka Laci", "Bukak Laci", "Buka Laci") to "Mbukak Laci",
        Triple("Log Aktivitas", "Cathetan Kegiatan", "Catetan Kagiatan") to "Catetan Lalakon",
        Triple("Pengaturan", "Setelan", "Setelan") to "Atoran"
    )

    private val wordRules = listOf(
        "kasir" to Triple("Kasir", "Kasir", "Kasir"),
        "produk" to Triple("Barang", "Barang", "Barang"),
        "laporan" to Triple("Laporan", "Laporan", "Laporan"),
        "pengaturan" to Triple("Setelan", "Setelan", "Atoran"),
        "setting" to Triple("Setelan", "Setelan", "Atoran"),
        "simpan" to Triple("Simpen", "Simpen", "Simpen"),
        "batal" to Triple("Batal", "Bolay", "Batal"),
        "hapus" to Triple("Busak", "Pupus", "Busek"),
        "bayar" to Triple("Bayar", "Bayar", "Majer"),
        "stok" to Triple("Stok", "Stok", "Stok"),
        "harga" to Triple("Rega", "Harga", "Arga"),
        "jual" to Triple("Didol", "Dijual", "Ejuwal"),
        "beli" to Triple("Tuku", "Mésér", "Melli"),
        "modal" to Triple("Modal", "Modal", "Modal"),
        "total" to Triple("Gunggung", "Jumlah", "Jumla"),
        "diskon" to Triple("Diskon", "Diskon", "Diskon"),
        "struk" to Triple("Struk", "Struk", "Struk"),
        "pelanggan" to Triple("Pelanggan", "Palanggan", "Pelanggan"),
        "toko" to Triple("Toko", "Toko", "Toko"),
        "usaha" to Triple("Usaha", "Usaha", "Usaha"),
        "bahasa" to Triple("Basa", "Basa", "Bhasa"),
        "keluar" to Triple("Metu", "Kaluar", "Kalowar"),
        "masuk" to Triple("Mlebu", "Asup", "Tamaso")
    )

    fun autoTranslateText(sourceText: String, targetLang: String): String {
        val lang = normalizeLang(targetLang)
        if (sourceText.isBlank() || lang == "id") return sourceText

        val langCache = cacheByLang.getOrPut(lang) { mutableMapOf() }
        langCache[sourceText]?.let { return it }

        val translated = when (lang) {
            "en" -> enBase.entries.find { it.value.equals(sourceText, ignoreCase = true) }?.value
                ?: enBase.entries.find { sourceText.contains(it.key.replace("_", " "), ignoreCase = true) }?.value
                ?: sourceText
            "jv", "su", "mad" -> {
                var out = sourceText
                for ((rule, madText) in phraseRules) {
                    val replacement = when (lang) {
                        "jv" -> rule.first
                        "su" -> rule.second
                        else -> madText
                    }
                    if (out.contains(rule.first, ignoreCase = true)) {
                        out = out.replace(rule.first, replacement, ignoreCase = true)
                    }
                }
                for ((keyWord, tri) in wordRules) {
                    val replacement = when (lang) {
                        "jv" -> tri.first
                        "su" -> tri.second
                        else -> tri.third
                    }
                    out = out.replace(Regex("\b${Regex.escape(keyWord)}\b", RegexOption.IGNORE_CASE), replacement)
                }
                out
            }
            else -> sourceText
        }

        langCache[sourceText] = translated
        return translated
    }

    fun normalizeLang(raw: String?): String {
        return when (raw?.trim()?.lowercase()) {
            "en", "english", "inggris" -> "en"
            "jv", "jw", "java", "jawa" -> "jv"
            "su", "sundanese", "sunda" -> "su"
            "mad", "madura", "madurese" -> "mad"
            else -> "id"
        }
    }

    fun loadSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = normalizeLang(prefs.getString(KEY_LANG, "id"))
        _activeLanguage.value = saved
        return saved
    }

    fun setLanguage(context: Context, langCode: String) {
        val norm = normalizeLang(langCode)
        _activeLanguage.value = norm
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, norm)
            .apply()
    }

    fun tr(key: String, lang: String = _activeLanguage.value): String {
        val norm = normalizeLang(lang)
        val baseId = idBase[key] ?: key.replace("_", " ")
        return when (norm) {
            "en" -> enBase[key] ?: autoTranslateText(baseId, "en")
            "jv", "su", "mad" -> autoTranslateText(baseId, norm)
            else -> baseId
        }
    }
}
