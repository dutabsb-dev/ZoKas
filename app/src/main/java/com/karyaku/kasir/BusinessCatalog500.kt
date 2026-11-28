package com.karyaku.kasir

data class IndonesiaBusinessItem(
    val id: Int,
    val name: String,
    val sectorGroup: String,
    val mappedBusinessType: String, // "KULINER", "RETAIL", "JASA", "MUSIMAN"
    val isServiceOnly: Boolean = false,
    val defaultUnit: String = "pcs"
)

data class OptionalModuleOption(
    val key: String,
    val title: String,
    val description: String,
    val recommendedForSector: Boolean = false
)

object BusinessCatalog500 {
    private val baseTemplates: List<Triple<String, String, Pair<String, String>>> = listOf(
        // Kuliner & Minuman (22)
        Triple("Warung Nasi Padang", "Kuliner & Minuman", "KULINER" to "porsi"),
        Triple("Warung Makan Prasmanan", "Kuliner & Minuman", "KULINER" to "porsi"),
        Triple("Warung Kopi / Warkop", "Kuliner & Minuman", "KULINER" to "gelas"),
        Triple("Kedai Kopi Kekinian", "Kuliner & Minuman", "KULINER" to "cup"),
        Triple("Angkringan", "Kuliner & Minuman", "KULINER" to "tusuk"),
        Triple("Warung Bakso & Mie Ayam", "Kuliner & Minuman", "KULINER" to "mangkok"),
        Triple("Warung Soto & Rawon", "Kuliner & Minuman", "KULINER" to "mangkok"),
        Triple("Warung Pecel Lele / Ayam Penyet", "Kuliner & Minuman", "KULINER" to "porsi"),
        Triple("Warung Sate & Gulai", "Kuliner & Minuman", "KULINER" to "porsi"),
        Triple("Warung Seblak & Baso Aci", "Kuliner & Minuman", "KULINER" to "porsi"),
        Triple("Toko Roti & Bakery", "Kuliner & Minuman", "KULINER" to "pcs"),
        Triple("Toko Kue & Camilan", "Kuliner & Minuman", "MUSIMAN" to "pcs"),
        Triple("Usaha Catering Harian", "Kuliner & Minuman", "MUSIMAN" to "box"),
        Triple("Kedai Es Teh / Minuman Segar", "Kuliner & Minuman", "KULINER" to "cup"),
        Triple("Warung Martabak & Terang Bulan", "Kuliner & Minuman", "KULINER" to "loyang"),
        Triple("Warung Bubur Ayam & Lontong", "Kuliner & Minuman", "KULINER" to "mangkok"),
        Triple("Warung Nasi Goreng & Mie Tek-Tek", "Kuliner & Minuman", "KULINER" to "porsi"),
        Triple("Kedai Boba & Thai Tea", "Kuliner & Minuman", "KULINER" to "cup"),
        Triple("Kedai Jus Buah & Smoothies", "Kuliner & Minuman", "KULINER" to "cup"),
        Triple("Warung Gorengan & Jajanan Sore", "Kuliner & Minuman", "KULINER" to "pcs"),
        Triple("Restoran Seafood & Ikan Bakar", "Kuliner & Minuman", "KULINER" to "porsi"),
        Triple("Kedai Dimsum & Sushi", "Kuliner & Minuman", "KULINER" to "porsi"),

        // Pasar Tradisional & Bahan Segar / UMKM (16)
        Triple("Pedagang Ayam Potong Pasar", "Pasar & Bahan Segar", "RETAIL" to "kg"),
        Triple("Pedagang Daging Sapi Pasar", "Pasar & Bahan Segar", "RETAIL" to "kg"),
        Triple("Pedagang Ikan Segar / Laut Pasar", "Pasar & Bahan Segar", "RETAIL" to "kg"),
        Triple("Pedagang Sayur Mayur Pasar", "Pasar & Bahan Segar", "RETAIL" to "ikat"),
        Triple("Pedagang Buah Segar / Parcel", "Pasar & Bahan Segar", "RETAIL" to "kg"),
        Triple("Pedagang Telur Ayam / Bebek", "Pasar & Bahan Segar", "RETAIL" to "kg"),
        Triple("Pedagang Bumbu & Rempah Pasar", "Pasar & Bahan Segar", "RETAIL" to "ons"),
        Triple("Pedagang Tahu & Tempe", "Pasar & Bahan Segar", "RETAIL" to "pcs"),
        Triple("Pedagang Kelapa Parut & Santan", "Pasar & Bahan Segar", "RETAIL" to "butir"),
        Triple("Pedagang Beras & Biji-Bijian", "Pasar & Bahan Segar", "RETAIL" to "kg"),
        Triple("Pedagang Frozen Food & Nugget", "Pasar & Bahan Segar", "RETAIL" to "pack"),
        Triple("Pedagang Kerupuk & Oleh-Oleh", "Pasar & Bahan Segar", "RETAIL" to "ball"),
        Triple("Pedagang Ikan Asin & Terasi", "Pasar & Bahan Segar", "RETAIL" to "ons"),
        Triple("Pedagang Cabai & Bawang", "Pasar & Bahan Segar", "RETAIL" to "kg"),
        Triple("Usaha Penggilingan Daging & Bakso", "Pasar & Bahan Segar", "JASA" to "kg"),
        Triple("Agen Susu Segar & Kedelai", "Pasar & Bahan Segar", "RETAIL" to "liter"),

        // Retail, Sembako & Grosir (24)
        Triple("Warung Sembako / Kelontong", "Retail & Grosir", "RETAIL" to "pcs"),
        Triple("Toko Kelontong Rumahan", "Retail & Grosir", "RETAIL" to "pcs"),
        Triple("Agen Gas LPG & Air Galon", "Retail & Grosir", "RETAIL" to "tabung"),
        Triple("Toko Pakaian / Fashion", "Retail & Grosir", "RETAIL" to "pcs"),
        Triple("Toko Hijab & Busana Muslim", "Retail & Grosir", "RETAIL" to "pcs"),
        Triple("Toko Sepatu & Sandal", "Retail & Grosir", "RETAIL" to "pasang"),
        Triple("Toko Alat Tulis & Fotokopi", "Retail & Grosir", "RETAIL" to "pcs"),
        Triple("Toko Bahan Bangunan & Cat", "Retail & Grosir", "RETAIL" to "zak"),
        Triple("Toko Pakan Ternak & Unggas", "Retail & Grosir", "RETAIL" to "zak"),
        Triple("Toko Pupuk & Obat Pertanian", "Retail & Grosir", "RETAIL" to "kg"),
        Triple("Konter Pulsa, Paket Data & Aksesoris HP", "Retail & Grosir", "RETAIL" to "pcs"),
        Triple("Toko Sparepart Motor & Oli", "Retail & Grosir", "RETAIL" to "pcs"),
        Triple("Toko Sparepart Mobil & Aki", "Retail & Grosir", "RETAIL" to "pcs"),
        Triple("Toko Elektronik & Alat Listrik", "Retail & Grosir", "RETAIL" to "pcs"),
        Triple("Toko Perabot & Plastik Rumah Tangga", "Retail & Grosir", "RETAIL" to "pcs"),
        Triple("Toko Mainan & Perlengkapan Anak", "Retail & Grosir", "RETAIL" to "pcs"),
        Triple("Toko Kosmetik & Skincare", "Retail & Grosir", "RETAIL" to "pcs"),
        Triple("Apotek & Toko Obat Herbal", "Retail & Grosir", "RETAIL" to "strip"),
        Triple("Toko Kacamata & Optik", "Retail & Grosir", "RETAIL" to "pcs"),
        Triple("Toko Tas & Koper", "Retail & Grosir", "RETAIL" to "pcs"),
        Triple("Toko Kain & Perlengkapan Jahit", "Retail & Grosir", "RETAIL" to "meter"),
        Triple("Toko Vape & Liquid", "Retail & Grosir", "RETAIL" to "botol"),
        Triple("Toko Ikan Hias & Petshop", "Retail & Grosir", "RETAIL" to "pcs"),
        Triple("Toko Buku & Perlengkapan Sekolah", "Retail & Grosir", "RETAIL" to "pcs"),

        // Jasa, Bengkel & Perbaikan (20)
        Triple("Bengkel Motor", "Jasa & Perbaikan", "JASA" to "jasa"),
        Triple("Bengkel Mobil", "Jasa & Perbaikan", "JASA" to "jasa"),
        Triple("Cuci Motor & Mobil (Carwash)", "Jasa & Perbaikan", "JASA" to "unit"),
        Triple("Laundry Kiloan & Satuan", "Jasa & Perbaikan", "JASA" to "kg"),
        Triple("Barbershop / Pangkas Rambut", "Jasa & Perbaikan", "JASA" to "layanan"),
        Triple("Salon Kecantikan & Spa", "Jasa & Perbaikan", "JASA" to "layanan"),
        Triple("Servis HP & Elektronik", "Jasa & Perbaikan", "JASA" to "unit"),
        Triple("Jahit & Permak Pakaian", "Jasa & Perbaikan", "JASA" to "pcs"),
        Triple("Jasa Cetak / Sablon / Percetakan", "Jasa & Perbaikan", "JASA" to "pcs"),
        Triple("Jasa Kebersihan / Cleaning", "Jasa & Perbaikan", "JASA" to "paket"),
        Triple("Jasa Nota, Faktur & Administrasi", "Jasa & Perbaikan", "JASA" to "nota"),
        Triple("Tambal Ban & Servis Velg", "Jasa & Perbaikan", "JASA" to "unit"),
        Triple("Servis AC & Kulkas", "Jasa & Perbaikan", "JASA" to "unit"),
        Triple("Studio Foto & Cetak Pas Foto", "Jasa & Perbaikan", "JASA" to "paket"),
        Triple("Rental Mobil, Motor & Sepeda", "Jasa & Perbaikan", "JASA" to "hari"),
        Triple("Jasa Ekspedisi & Agen Pengiriman", "Jasa & Perbaikan", "JASA" to "paket"),
        Triple("Klinik Pratama, Bidan & Terapi", "Jasa & Perbaikan", "JASA" to "sesi"),
        Triple("Bimbingan Belajar & Kursus", "Jasa & Perbaikan", "JASA" to "paket"),
        Triple("Jasa Las & Bengkel Konstruksi", "Jasa & Perbaikan", "JASA" to "unit"),
        Triple("Jasa Dekorasi & Sewa Tenda Event", "Jasa & Perbaikan", "MUSIMAN" to "paket")
    )

    // 82 baseTemplates x 24 modifiers = 1,968 jenis usaha terstruktur (mendekati 2.000)
    val allBusinesses: List<IndonesiaBusinessItem> by lazy {
        val list = mutableListOf<IndonesiaBusinessItem>()
        var idx = 1
        val modifiers = listOf(
            "", "Rumahan", "Skala Mikro", "Skala Menengah", "Grosir",
            "Eceran", "Pasar Tradisional", "Pinggir Jalan", "Harian", "Paket Khusus",
            "Kiloan", "Satuan", "Kios / Ruko", "Outlet / Booth", "Pesan Antar / Delivery",
            "Pre-Order (PO)", "24 Jam", "Cabang", "Keliling / Lapak", "Online & Offline",
            "Langganan / Member", "Spesialis", "Express / Kilat", "Nota & Order"
        )
        for (mod in modifiers) {
            for (tpl in baseTemplates) {
                if (idx > 2000) break
                val fullName = if (mod.isBlank()) tpl.first else "${tpl.first} ($mod)"
                val isSvc = tpl.third.first == "JASA"
                val unit = when {
                    mod == "Kiloan" -> "kg"
                    mod == "Satuan" && isSvc -> "pcs"
                    mod == "Nota & Order" -> "nota"
                    else -> tpl.third.second
                }
                list.add(
                    IndonesiaBusinessItem(
                        id = idx++,
                        name = fullName,
                        sectorGroup = tpl.second,
                        mappedBusinessType = tpl.third.first,
                        isServiceOnly = isSvc,
                        defaultUnit = unit
                    )
                )
            }
            if (idx > 2000) break
        }
        list
    }

    fun optionalModulesFor(businessType: String): List<OptionalModuleOption> {
        val isCulinary = businessType.equals("KULINER", ignoreCase = true)
        val isService = businessType.equals("JASA", ignoreCase = true)
        return listOf(
            OptionalModuleOption(
                key = "finance",
                title = "Catatan Kas & Bon / Hutang",
                description = "Mencatat kas masuk/keluar serta bon/piutang pelanggan.",
                recommendedForSector = true
            ),
            OptionalModuleOption(
                key = "wholesale",
                title = "Harga Grosir / Bertingkat",
                description = "Harga khusus untuk pembelian jumlah tertentu atau timbangan.",
                recommendedForSector = !isService
            ),
            OptionalModuleOption(
                key = "supplier",
                title = "Pemasok & Kulakan (PO)",
                description = "Mencatat pemasok barang dan riwayat kulakan stok.",
                recommendedForSector = !isService
            ),
            OptionalModuleOption(
                key = "table_mode",
                title = "Mode Meja & Pesanan",
                description = "Mengatur nomor meja atau jenis pesanan makan di tempat/bungkus.",
                recommendedForSector = isCulinary
            ),
            OptionalModuleOption(
                key = "reservation",
                title = "Jadwal / Reservasi",
                description = "Mencatat jadwal booking atau janji temu pelanggan.",
                recommendedForSector = isService
            ),
            OptionalModuleOption(
                key = "crm",
                title = "Data Pelanggan & Poin",
                description = "Menyimpan riwayat pelanggan dan promo ulang tahun.",
                recommendedForSector = false
            )
        )
    }
}
