package com.karyaku.kasir

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UniversalBookkeepingExporter {

    private fun loadStoreLogoBitmap(context: Context, logoPath: String?): Bitmap? {
        try {
            if (!logoPath.isNullOrBlank()) {
                val file = File(logoPath)
                if (file.exists()) {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) return bmp
                }
            }
            val drawable = ContextCompat.getDrawable(context, R.drawable.logo_zokas) ?: return null
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 160
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 160
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        } catch (_: Exception) {
            return null
        }
    }

    internal fun attachExcelStoreHeader(
        context: Context,
        workbook: XSSFWorkbook,
        sheet: Sheet,
        settingsManager: SettingsManager,
        reportTitle: String,
        periodLabel: String,
        totalCols: Int
    ): Int {
        val lastCol = (totalCols - 1).coerceAtLeast(3)
        val storeName = settingsManager.storeName?.takeIf { it.isNotBlank() } ?: "ZoKas Store"
        val storeAddress = settingsManager.storeAddress?.takeIf { it.isNotBlank() } ?: "-"
        val storePhone = settingsManager.storePhone?.takeIf { it.isNotBlank() } ?: "-"
        val printDate = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date())

        val titleFont = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 14
            color = IndexedColors.DARK_GREEN.index
        }
        val subtitleFont = workbook.createFont().apply {
            fontHeightInPoints = 9
            color = IndexedColors.GREY_50_PERCENT.index
        }
        val bannerFont = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 11
            color = IndexedColors.WHITE.index
        }

        val titleStyle = workbook.createCellStyle().apply {
            setFont(titleFont)
            verticalAlignment = VerticalAlignment.CENTER
        }
        val subtitleStyle = workbook.createCellStyle().apply {
            setFont(subtitleFont)
            verticalAlignment = VerticalAlignment.CENTER
        }
        val bannerStyle = workbook.createCellStyle().apply {
            setFont(bannerFont)
            fillForegroundColor = IndexedColors.SEA_GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            verticalAlignment = VerticalAlignment.CENTER
            alignment = HorizontalAlignment.LEFT
        }

        val row0 = sheet.createRow(0).apply { heightInPoints = 24f }
        val row1 = sheet.createRow(1).apply { heightInPoints = 16f }
        val row2 = sheet.createRow(2).apply { heightInPoints = 16f }
        val row3 = sheet.createRow(3).apply { heightInPoints = 22f }

        // Kolom A disediakan untuk logo toko
        sheet.setColumnWidth(0, 3200)
        val textStartCol = 1
        if (lastCol > textStartCol) {
            sheet.addMergedRegion(CellRangeAddress(0, 0, textStartCol, lastCol))
            sheet.addMergedRegion(CellRangeAddress(1, 1, textStartCol, lastCol))
            sheet.addMergedRegion(CellRangeAddress(2, 2, textStartCol, lastCol))
        }
        sheet.addMergedRegion(CellRangeAddress(3, 3, 0, lastCol))

        row0.createCell(textStartCol).apply {
            setCellValue(storeName.uppercase())
            cellStyle = titleStyle
        }
        row1.createCell(textStartCol).apply {
            setCellValue("Alamat: $storeAddress | Telp: $storePhone")
            cellStyle = subtitleStyle
        }
        row2.createCell(textStartCol).apply {
            setCellValue("Periode: $periodLabel  •  Dicetak: $printDate")
            cellStyle = subtitleStyle
        }
        for (c in 0..lastCol) {
            row3.createCell(c).apply {
                if (c == 0) setCellValue("  $reportTitle")
                cellStyle = bannerStyle
            }
        }

        // Sematkan Logo Toko di pojok kiri atas (A1:A3)
        val logoBmp = loadStoreLogoBitmap(context, settingsManager.storeLogoPath)
        if (logoBmp != null) {
            try {
                val scaled = Bitmap.createScaledBitmap(logoBmp, 110, 110, true)
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.PNG, 95, baos)
                val picIdx = workbook.addPicture(baos.toByteArray(), Workbook.PICTURE_TYPE_PNG)
                val helper = workbook.creationHelper
                val drawing = sheet.createDrawingPatriarch()
                val anchor = helper.createClientAnchor().apply {
                    setCol1(0)
                    row1.rowNum
                    setRow1(0)
                    setCol2(1)
                    setRow2(3)
                    anchorType = ClientAnchor.AnchorType.MOVE_AND_RESIZE
                }
                drawing.createPicture(anchor, picIdx)
            } catch (_: Exception) {
            }
        }

        return 5 // Baris awal tabel data
    }

    private fun createTableHeaderStyle(workbook: XSSFWorkbook): CellStyle {
        val font = workbook.createFont().apply {
            bold = true
            color = IndexedColors.WHITE.index
            fontHeightInPoints = 10
        }
        return workbook.createCellStyle().apply {
            setFont(font)
            fillForegroundColor = IndexedColors.DARK_TEAL.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            borderTop = BorderStyle.THIN
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }
    }

    private fun createDataStyle(workbook: XSSFWorkbook, isAltRow: Boolean, isCurrency: Boolean = false, isBold: Boolean = false): CellStyle {
        val font = workbook.createFont().apply {
            bold = isBold
            fontHeightInPoints = 10
        }
        return workbook.createCellStyle().apply {
            setFont(font)
            if (isAltRow) {
                fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
            }
            if (isCurrency) {
                dataFormat = workbook.createDataFormat().getFormat("#,##0")
                alignment = HorizontalAlignment.RIGHT
            }
            verticalAlignment = VerticalAlignment.CENTER
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }
    }

    suspend fun exportUniversalBookkeepingExcel(
        context: Context,
        uri: Uri,
        viewModel: PosViewModel,
        settingsManager: SettingsManager,
        transactions: List<TransactionEntity>,
        shifts: List<ShiftEntity>,
        cashFlows: List<CashFlowEntity>,
        expenses: List<ExpenseEntity>,
        products: List<ProductEntity>,
        rawMaterials: List<RawMaterialEntity>,
        purchases: List<PurchaseEntity>,
        auditLogs: List<AuditLogEntity>,
        periodLabel: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val workbook = XSSFWorkbook()
            val tblHeaderStyle = createTableHeaderStyle(workbook)
            val dateFmt = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())

            val validTx = transactions.filter { it.status == "SUCCESS" }
            val unpaidTx = transactions.filter { it.status == "UNPAID" }
            val voidTx = transactions.filter { it.status == "VOIDED" }

            val totalOmzet = validTx.sumOf { it.totalPrice }
            val totalHpp = validTx.sumOf { it.totalCost }
            val taxRate = settingsManager.taxPercent
            val totalTax = if (taxRate <= 0.0) 0L else validTx.sumOf {
                (it.totalPrice - (it.totalPrice / (1.0 + (taxRate / 100.0)))).toLong()
            }
            val netOmzet = (totalOmzet - totalTax).coerceAtLeast(0L)
            val grossProfit = netOmzet - totalHpp
            val cashSales = validTx.filter { it.paymentMethod == "CASH" }.sumOf { it.totalPrice }
            val qrisSales = validTx.filter { it.paymentMethod.equals("QRIS", true) }.sumOf { it.totalPrice }
            val transferSales = validTx.filter { it.paymentMethod.equals("Bank Transfer", true) }.sumOf { it.totalPrice }
            val otherNonCashSales = (validTx.filter { it.paymentMethod != "CASH" }.sumOf { it.totalPrice } - qrisSales - transferSales).coerceAtLeast(0L)

            val totalCashIn = cashFlows.filter { it.type == "IN" }.sumOf { it.amount }
            val totalCashOut = cashFlows.filter { it.type == "OUT" }.sumOf { it.amount }
            val totalOpExpenses = expenses.sumOf { it.amount }
            val netProfit = grossProfit - totalCashOut - totalOpExpenses
            val drawerCashEstimate = (cashSales + totalCashIn - totalCashOut).coerceAtLeast(0L)
            val inventoryVal = products.sumOf { it.stock * it.costPrice }
            val rawMatVal = rawMaterials.sumOf { (it.stock * it.costPerUnit).toLong() }

            // =========================================================================
            // SHEET 1: RINGKASAN LABA RUGI & NERACA OPERASIONAL
            // =========================================================================
            val sSummary = workbook.createSheet("1_Ringkasan_Laba_Rugi")
            var rIdx = attachExcelStoreHeader(context, workbook, sSummary, settingsManager, "BUKU PEMBUKUAN LENGKAP — RINGKASAN LABA RUGI & KAS", periodLabel, 4)

            val sumHeaders = listOf("Kode", "Pos Pembukuan / Keterangan", "Nominal (Rp)", "Catatan / Status")
            val sumHdrRow = sSummary.createRow(rIdx++).apply { heightInPoints = 20f }
            sumHeaders.forEachIndexed { idx, h ->
                sumHdrRow.createCell(idx).apply { setCellValue(h); cellStyle = tblHeaderStyle }
            }

            val summaryRows = listOf(
                listOf("REV-01", "Total Omzet Kotor (${validTx.size} Transaksi Sukses)", totalOmzet, "Termasuk pajak & layanan"),
                listOf("REV-02", "Titipan Pajak (PB1 / PPN ${taxRate}%)", totalTax, "Kewajiban pajak"),
                listOf("REV-03", "Omzet Bersih (Setelah Pajak)", netOmzet, "Pendapatan usaha bersih"),
                listOf("COGS-01", "Total Harga Pokok Penjualan (HPP / Modal)", totalHpp, "Modal produk terjual"),
                listOf("GP-01", "LABA KOTOR (GROSS PROFIT)", grossProfit, "Omzet Bersih - HPP"),
                listOf("EXP-01", "Pengeluaran Operasional (${expenses.size} Catatan)", totalOpExpenses, "Listrik, gaji, sewa, dll."),
                listOf("CF-OUT", "Kas Keluar Laci Kasir", totalCashOut, "Pengambilan kas laci"),
                listOf("NP-01", "LABA BERSIH (NET PROFIT)", netProfit, "Laba Kotor - Biaya & Kas Keluar"),
                listOf("PAY-01", "Penjualan Tunai (CASH)", cashSales, "Masuk ke laci kasir"),
                listOf("PAY-02", "Penjualan QRIS", qrisSales, "Masuk ke rekening merchant QRIS"),
                listOf("PAY-03", "Penjualan Transfer Bank", transferSales, "Masuk ke rekening bank toko"),
                listOf("PAY-04", "Penjualan Non-Tunai Lainnya", otherNonCashSales, "Metode non-tunai lain"),
                listOf("CF-IN", "Kas Masuk Tambahan Laci", totalCashIn, "Penambahan kas laci"),
                listOf("CASH-BAL", "Estimasi Saldo Laci Tunai", drawerCashEstimate, "Tunai + Kas Masuk - Kas Keluar"),
                listOf("AR-01", "Piutang / Bon Belum Lunas (${unpaidTx.size} Struk)", unpaidTx.sumOf { it.totalPrice }, "Menunggu pelunasan"),
                listOf("VOID-01", "Transaksi Dibatalkan / Void (${voidTx.size} Struk)", voidTx.sumOf { it.totalPrice }, "Tidak dihitung ke omzet"),
                listOf("INV-01", "Nilai Aset Stok Produk Jadi (${products.size} Item)", inventoryVal, "Stok × Harga Beli"),
                listOf("INV-02", "Nilai Aset Bahan Baku (${rawMaterials.size} Bahan)", rawMatVal, "Stok Bahan × Harga Satuan")
            )

            summaryRows.forEachIndexed { i, item ->
                val row = sSummary.createRow(rIdx++).apply { heightInPoints = 19f }
                val highlight = item[0] in listOf("GP-01", "NP-01", "CASH-BAL")
                val alt = i % 2 == 1
                row.createCell(0).apply { setCellValue(item[0].toString()); cellStyle = createDataStyle(workbook, alt, isBold = highlight) }
                row.createCell(1).apply { setCellValue(item[1].toString()); cellStyle = createDataStyle(workbook, alt, isBold = highlight) }
                row.createCell(2).apply { setCellValue((item[2] as Long).toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true, isBold = highlight) }
                row.createCell(3).apply { setCellValue(item[3].toString()); cellStyle = createDataStyle(workbook, alt, isBold = highlight) }
            }
            sSummary.setColumnWidth(0, 3500)
            sSummary.setColumnWidth(1, 11500)
            sSummary.setColumnWidth(2, 5500)
            sSummary.setColumnWidth(3, 8500)

            // =========================================================================
            // SHEET 2: TRANSAKSI PENJUALAN
            // =========================================================================
            val sTrx = workbook.createSheet("2_Transaksi_Penjualan")
            var rTrx = attachExcelStoreHeader(context, workbook, sTrx, settingsManager, "RINCIAN TRANSAKSI PENJUALAN", periodLabel, 10)
            val trxHeaders = listOf("No. Struk", "Waktu", "Pelanggan", "Kasir", "Meja/Tipe", "Metode", "Total Tagihan", "HPP / Modal", "Laba Kotor", "Status")
            val trxHdrRow = sTrx.createRow(rTrx++).apply { heightInPoints = 20f }
            trxHeaders.forEachIndexed { idx, h -> trxHdrRow.createCell(idx).apply { setCellValue(h); cellStyle = tblHeaderStyle } }

            transactions.forEachIndexed { i, tx ->
                val row = sTrx.createRow(rTrx++).apply { heightInPoints = 18f }
                val alt = i % 2 == 1
                val profit = if (tx.status == "SUCCESS") (tx.totalPrice - tx.totalCost) else 0L
                row.createCell(0).apply { setCellValue("#${tx.id}"); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(1).apply { setCellValue(dateFmt.format(Date(tx.timestamp))); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(2).apply { setCellValue(tx.customerName); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(3).apply { setCellValue(tx.cashierName); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(4).apply { setCellValue("${tx.tableName} (${tx.orderType})"); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(5).apply { setCellValue(tx.paymentMethod); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(6).apply { setCellValue(tx.totalPrice.toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                row.createCell(7).apply { setCellValue(tx.totalCost.toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                row.createCell(8).apply { setCellValue(profit.toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                row.createCell(9).apply { setCellValue(tx.status); cellStyle = createDataStyle(workbook, alt) }
            }
            for (c in 1..9) sTrx.setColumnWidth(c, 4800)

            // =========================================================================
            // SHEET 3: RINCIAN ITEM TERJUAL
            // =========================================================================
            val sItems = workbook.createSheet("3_Rincian_Item_Terjual")
            var rItem = attachExcelStoreHeader(context, workbook, sItems, settingsManager, "RINCIAN ITEM TERJUAL PER TRANSAKSI", periodLabel, 7)
            val itemHeaders = listOf("No. Struk", "Waktu", "Nama Produk", "Qty", "Harga Satuan", "Subtotal", "Catatan")
            val itemHdrRow = sItems.createRow(rItem++).apply { heightInPoints = 20f }
            itemHeaders.forEachIndexed { idx, h -> itemHdrRow.createCell(idx).apply { setCellValue(h); cellStyle = tblHeaderStyle } }

            var itemCounter = 0
            for (tx in validTx) {
                val items = viewModel.getTransactionItems(tx.id).first()
                for (it in items) {
                    val row = sItems.createRow(rItem++).apply { heightInPoints = 18f }
                    val alt = itemCounter++ % 2 == 1
                    val uPrice = if (it.quantity > 0) it.subtotal / it.quantity else it.price
                    row.createCell(0).apply { setCellValue("#${tx.id}"); cellStyle = createDataStyle(workbook, alt) }
                    row.createCell(1).apply { setCellValue(dateFmt.format(Date(tx.timestamp))); cellStyle = createDataStyle(workbook, alt) }
                    row.createCell(2).apply { setCellValue(it.productName); cellStyle = createDataStyle(workbook, alt) }
                    row.createCell(3).apply { setCellValue(it.quantity.toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                    row.createCell(4).apply { setCellValue(uPrice.toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                    row.createCell(5).apply { setCellValue(it.subtotal.toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                    row.createCell(6).apply { setCellValue(it.notes); cellStyle = createDataStyle(workbook, alt) }
                }
            }
            sItems.setColumnWidth(1, 5200)
            sItems.setColumnWidth(2, 8500)
            sItems.setColumnWidth(4, 4800)
            sItems.setColumnWidth(5, 4800)
            sItems.setColumnWidth(6, 7000)

            // =========================================================================
            // SHEET 4: REKAP SHIFT & TUTUP KASIR
            // =========================================================================
            val sShift = workbook.createSheet("4_Shift_Tutup_Kasir")
            var rShift = attachExcelStoreHeader(context, workbook, sShift, settingsManager, "REKAP SHIFT & TUTUP KASIR", periodLabel, 9)
            val shiftHeaders = listOf("Kasir", "Waktu Buka", "Waktu Tutup", "Modal Awal", "Kas Sistem", "Kas Fisik", "Selisih", "Status", "Catatan")
            val shiftHdrRow = sShift.createRow(rShift++).apply { heightInPoints = 20f }
            shiftHeaders.forEachIndexed { idx, h -> shiftHdrRow.createCell(idx).apply { setCellValue(h); cellStyle = tblHeaderStyle } }

            shifts.forEachIndexed { i, sh ->
                val row = sShift.createRow(rShift++).apply { heightInPoints = 18f }
                val alt = i % 2 == 1
                row.createCell(0).apply { setCellValue(sh.userName); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(1).apply { setCellValue(dateFmt.format(Date(sh.startTime))); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(2).apply { setCellValue(if (sh.endTime != null) dateFmt.format(Date(sh.endTime)) else "MASIH BUKA"); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(3).apply { setCellValue(sh.openingCash.toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                row.createCell(4).apply { setCellValue(sh.expectedCash.toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                row.createCell(5).apply { setCellValue((sh.closingCash ?: 0L).toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                row.createCell(6).apply { setCellValue((sh.discrepancy ?: 0L).toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                row.createCell(7).apply { setCellValue(sh.status); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(8).apply { setCellValue(sh.note); cellStyle = createDataStyle(workbook, alt) }
            }
            for (c in 1..8) sShift.setColumnWidth(c, 5000)

            // =========================================================================
            // SHEET 5: ARUS KAS & PENGELUARAN OPERASIONAL
            // =========================================================================
            val sCf = workbook.createSheet("5_Arus_Kas_&_Biaya")
            var rCf = attachExcelStoreHeader(context, workbook, sCf, settingsManager, "BUKU ARUS KAS & PENGELUARAN OPERASIONAL", periodLabel, 5)
            val cfHeaders = listOf("Waktu", "Jenis", "Kategori / Sumber", "Nominal (Rp)", "Keterangan")
            val cfHdrRow = sCf.createRow(rCf++).apply { heightInPoints = 20f }
            cfHeaders.forEachIndexed { idx, h -> cfHdrRow.createCell(idx).apply { setCellValue(h); cellStyle = tblHeaderStyle } }

            var cfIdx = 0
            cashFlows.forEach { cf ->
                val row = sCf.createRow(rCf++).apply { heightInPoints = 18f }
                val alt = cfIdx++ % 2 == 1
                row.createCell(0).apply { setCellValue(dateFmt.format(Date(cf.timestamp))); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(1).apply { setCellValue(if (cf.type == "IN") "KAS MASUK" else "KAS KELUAR"); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(2).apply { setCellValue("Laci Kasir"); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(3).apply { setCellValue(cf.amount.toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                row.createCell(4).apply { setCellValue(cf.note); cellStyle = createDataStyle(workbook, alt) }
            }
            expenses.forEach { exp ->
                val row = sCf.createRow(rCf++).apply { heightInPoints = 18f }
                val alt = cfIdx++ % 2 == 1
                row.createCell(0).apply { setCellValue(dateFmt.format(Date(exp.timestamp))); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(1).apply { setCellValue("BIAYA OPERASIONAL"); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(2).apply { setCellValue(exp.category); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(3).apply { setCellValue(exp.amount.toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                row.createCell(4).apply { setCellValue(exp.note); cellStyle = createDataStyle(workbook, alt) }
            }
            sCf.setColumnWidth(1, 5200)
            sCf.setColumnWidth(2, 5500)
            sCf.setColumnWidth(3, 5000)
            sCf.setColumnWidth(4, 9000)

            // =========================================================================
            // SHEET 6: STOK PRODUK & BAHAN BAKU
            // =========================================================================
            val sInv = workbook.createSheet("6_Stok_&_Bahan_Baku")
            var rInv = attachExcelStoreHeader(context, workbook, sInv, settingsManager, "VALUASI STOK PRODUK & BAHAN BAKU", periodLabel, 7)
            val invHeaders = listOf("Tipe", "Nama Item", "Kategori / Satuan", "Sisa Stok", "Harga Modal", "Harga Jual", "Nilai Aset Modal")
            val invHdrRow = sInv.createRow(rInv++).apply { heightInPoints = 20f }
            invHeaders.forEachIndexed { idx, h -> invHdrRow.createCell(idx).apply { setCellValue(h); cellStyle = tblHeaderStyle } }

            var invIdx = 0
            products.forEach { p ->
                val row = sInv.createRow(rInv++).apply { heightInPoints = 18f }
                val alt = invIdx++ % 2 == 1
                val asset = if (p.isService) 0L else (p.stock * p.costPrice)
                row.createCell(0).apply { setCellValue(if (p.isService) "JASA" else "PRODUK"); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(1).apply { setCellValue(p.name); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(2).apply { setCellValue(p.category); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(3).apply { setCellValue(p.stock.toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                row.createCell(4).apply { setCellValue(p.costPrice.toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                row.createCell(5).apply { setCellValue(p.price.toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                row.createCell(6).apply { setCellValue(asset.toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
            }
            rawMaterials.forEach { rm ->
                val row = sInv.createRow(rInv++).apply { heightInPoints = 18f }
                val alt = invIdx++ % 2 == 1
                val asset = (rm.stock * rm.costPerUnit).toLong()
                row.createCell(0).apply { setCellValue("BAHAN BAKU"); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(1).apply { setCellValue(rm.name); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(2).apply { setCellValue(rm.unit); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(3).apply { setCellValue(rm.stock); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                row.createCell(4).apply { setCellValue(rm.costPerUnit); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                row.createCell(5).apply { setCellValue(0.0); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                row.createCell(6).apply { setCellValue(asset.toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
            }
            sInv.setColumnWidth(1, 8000)
            sInv.setColumnWidth(2, 4800)
            sInv.setColumnWidth(4, 4800)
            sInv.setColumnWidth(5, 4800)
            sInv.setColumnWidth(6, 5200)

            // =========================================================================
            // SHEET 7: PEMBELIAN (PO) & LOG AUDIT
            // =========================================================================
            val sPo = workbook.createSheet("7_Pembelian_PO_&_Audit")
            var rPo = attachExcelStoreHeader(context, workbook, sPo, settingsManager, "RIWAYAT PEMBELIAN SUPPLIER (PO) & LOG AUDIT", periodLabel, 6)
            val poHeaders = listOf("Waktu", "Jenis Catatan", "Referensi / User", "Supplier / Aksi", "Total Nominal", "Status")
            val poHdrRow = sPo.createRow(rPo++).apply { heightInPoints = 20f }
            poHeaders.forEachIndexed { idx, h -> poHdrRow.createCell(idx).apply { setCellValue(h); cellStyle = tblHeaderStyle } }

            var poIdx = 0
            purchases.forEach { po ->
                val row = sPo.createRow(rPo++).apply { heightInPoints = 18f }
                val alt = poIdx++ % 2 == 1
                row.createCell(0).apply { setCellValue(dateFmt.format(Date(po.timestamp))); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(1).apply { setCellValue("PURCHASE ORDER"); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(2).apply { setCellValue(po.invoiceNumber); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(3).apply { setCellValue(po.supplierName); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(4).apply { setCellValue(po.totalAmount.toDouble()); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                row.createCell(5).apply { setCellValue(po.status); cellStyle = createDataStyle(workbook, alt) }
            }
            auditLogs.take(200).forEach { log ->
                val row = sPo.createRow(rPo++).apply { heightInPoints = 18f }
                val alt = poIdx++ % 2 == 1
                row.createCell(0).apply { setCellValue(dateFmt.format(Date(log.timestamp))); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(1).apply { setCellValue("AUDIT LOG"); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(2).apply { setCellValue(log.userName); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(3).apply { setCellValue(log.action); cellStyle = createDataStyle(workbook, alt) }
                row.createCell(4).apply { setCellValue(0.0); cellStyle = createDataStyle(workbook, alt, isCurrency = true) }
                row.createCell(5).apply { setCellValue(log.actionType); cellStyle = createDataStyle(workbook, alt) }
            }
            sPo.setColumnWidth(1, 4800)
            sPo.setColumnWidth(2, 5200)
            sPo.setColumnWidth(3, 9500)
            sPo.setColumnWidth(4, 4800)

            context.contentResolver.openOutputStream(uri)?.use { out ->
                workbook.write(out)
            }
            workbook.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportUniversalBookkeepingPdf(
        context: Context,
        uri: Uri,
        viewModel: PosViewModel,
        settingsManager: SettingsManager,
        transactions: List<TransactionEntity>,
        shifts: List<ShiftEntity>,
        cashFlows: List<CashFlowEntity>,
        expenses: List<ExpenseEntity>,
        products: List<ProductEntity>,
        rawMaterials: List<RawMaterialEntity>,
        purchases: List<PurchaseEntity>,
        auditLogs: List<AuditLogEntity>,
        periodLabel: String
    ) = withContext(Dispatchers.IO) {
        try {
            val pdf = PdfDocument()
            val storeName = settingsManager.storeName?.takeIf { it.isNotBlank() } ?: "ZoKas Store"
            val storeAddress = settingsManager.storeAddress?.takeIf { it.isNotBlank() } ?: "-"
            val storePhone = settingsManager.storePhone?.takeIf { it.isNotBlank() } ?: "-"
            val logoBmp = loadStoreLogoBitmap(context, settingsManager.storeLogoPath)
            val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID"))
            val nf = NumberFormat.getInstance(Locale("id", "ID"))
            fun rp(v: Long): String = "Rp " + nf.format(v)

            val brandPaint = Paint().apply { color = android.graphics.Color.rgb(16, 124, 84); style = Paint.Style.FILL }
            val lightBoxPaint = Paint().apply { color = android.graphics.Color.rgb(241, 245, 249); style = Paint.Style.FILL }
            val zebraPaint = Paint().apply { color = android.graphics.Color.rgb(248, 250, 252); style = Paint.Style.FILL }
            val linePaint = Paint().apply { color = android.graphics.Color.rgb(203, 213, 225); strokeWidth = 1f }
            val titleWhitePaint = Paint().apply { color = android.graphics.Color.WHITE; textSize = 16f; isFakeBoldText = true }
            val subWhitePaint = Paint().apply { color = android.graphics.Color.rgb(226, 232, 240); textSize = 9.5f }
            val chapterPaint = Paint().apply { color = android.graphics.Color.rgb(16, 124, 84); textSize = 12.5f; isFakeBoldText = true }
            val tblHeadPaint = Paint().apply { color = android.graphics.Color.WHITE; textSize = 9.5f; isFakeBoldText = true }
            val cellPaint = Paint().apply { color = android.graphics.Color.rgb(30, 41, 59); textSize = 9.5f }
            val cellBoldPaint = Paint().apply { color = android.graphics.Color.rgb(15, 23, 42); textSize = 9.5f; isFakeBoldText = true }
            val footerPaint = Paint().apply { color = android.graphics.Color.GRAY; textSize = 8.5f }

            var pageNum = 1
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
            var canvas = page.canvas

            fun drawPageHeader(isCover: Boolean): Float {
                val headerHeight = if (isCover) 92f else 62f
                canvas.drawRect(0f, 0f, 595f, headerHeight, brandPaint)
                var textX = 36f
                if (logoBmp != null) {
                    val logoSize = if (isCover) 54f else 38f
                    val logoTop = (headerHeight - logoSize) / 2f
                    val dst = RectF(36f, logoTop, 36f + logoSize, logoTop + logoSize)
                    canvas.drawBitmap(logoBmp, null, dst, null)
                    textX = 36f + logoSize + 14f
                }
                if (isCover) {
                    canvas.drawText(storeName.uppercase(), textX, 34f, titleWhitePaint)
                    canvas.drawText("Alamat: $storeAddress  |  Telp: $storePhone", textX, 50f, subWhitePaint)
                    canvas.drawText("E-BOOK LAPORAN PEMBUKUAN LENGKAP  •  Periode: $periodLabel", textX, 66f, subWhitePaint)
                    canvas.drawText("Dicetak pada: ${dateFmt.format(Date())}", textX, 80f, subWhitePaint)
                } else {
                    canvas.drawText("${storeName.uppercase()} — BUKU PEMBUKUAN", textX, 27f, titleWhitePaint.apply { textSize = 12f })
                    canvas.drawText("Periode: $periodLabel  •  Hal. $pageNum", textX, 44f, subWhitePaint)
                    titleWhitePaint.textSize = 16f
                }
                return headerHeight + 22f
            }

            fun finishCurrentPage() {
                canvas.drawLine(36f, 812f, 559f, 812f, linePaint)
                canvas.drawText("$storeName • Dokumen Pembukuan Resmi ZoKas", 36f, 826f, footerPaint)
                val pLabel = "Halaman $pageNum"
                canvas.drawText(pLabel, 559f - footerPaint.measureText(pLabel), 826f, footerPaint)
                pdf.finishPage(page)
            }

            fun nextPage(): Float {
                finishCurrentPage()
                pageNum++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
                canvas = page.canvas
                return drawPageHeader(isCover = false)
            }

            var y = drawPageHeader(isCover = true)

            // Kalkulasi Keuangan
            val validTx = transactions.filter { it.status == "SUCCESS" }
            val unpaidTx = transactions.filter { it.status == "UNPAID" }
            val totalOmzet = validTx.sumOf { it.totalPrice }
            val totalHpp = validTx.sumOf { it.totalCost }
            val taxRate = settingsManager.taxPercent
            val totalTax = if (taxRate <= 0.0) 0L else validTx.sumOf {
                (it.totalPrice - (it.totalPrice / (1.0 + (taxRate / 100.0)))).toLong()
            }
            val netOmzet = (totalOmzet - totalTax).coerceAtLeast(0L)
            val grossProfit = netOmzet - totalHpp
            val cashSales = validTx.filter { it.paymentMethod == "CASH" }.sumOf { it.totalPrice }
            val nonCashSales = validTx.filter { it.paymentMethod != "CASH" }.sumOf { it.totalPrice }
            val totalCashIn = cashFlows.filter { it.type == "IN" }.sumOf { it.amount }
            val totalCashOut = cashFlows.filter { it.type == "OUT" }.sumOf { it.amount }
            val totalOpExpenses = expenses.sumOf { it.amount }
            val netProfit = grossProfit - totalCashOut - totalOpExpenses
            val drawerCash = (cashSales + totalCashIn - totalCashOut).coerceAtLeast(0L)
            val invAsset = products.sumOf { it.stock * it.costPrice }

            // BAB 1: RINGKASAN EKSEKUTIF & LABA RUGI
            canvas.drawText("BAB 1. RINGKASAN EKSEKUTIF & LAPORAN LABA RUGI", 36f, y, chapterPaint)
            y += 12f
            canvas.drawRect(36f, y, 559f, y + 20f, brandPaint)
            canvas.drawText("Pos Keuangan & Operasional", 44f, y + 14f, tblHeadPaint)
            canvas.drawText("Nilai (Rupiah)", 460f, y + 14f, tblHeadPaint)
            y += 20f

            val pnlItems = listOf(
                Triple("Total Omzet Kotor (${validTx.size} Transaksi Sukses)", rp(totalOmzet), false),
                Triple("Titipan Pajak (PB1 / PPN ${taxRate}%)", rp(totalTax), false),
                Triple("Omzet Bersih Setelah Pajak", rp(netOmzet), true),
                Triple("Total Harga Pokok Penjualan (HPP / Modal)", rp(totalHpp), false),
                Triple("LABA KOTOR (GROSS PROFIT)", rp(grossProfit), true),
                Triple("Pengeluaran Operasional (${expenses.size} Catatan)", "- " + rp(totalOpExpenses), false),
                Triple("Kas Keluar Laci Kasir", "- " + rp(totalCashOut), false),
                Triple("LABA BERSIH USAHA (NET PROFIT)", rp(netProfit), true),
                Triple("Penjualan Tunai (CASH)", rp(cashSales), false),
                Triple("Penjualan Non-Tunai (QRIS / Transfer)", rp(nonCashSales), false),
                Triple("Kas Masuk Tambahan Laci", rp(totalCashIn), false),
                Triple("Estimasi Saldo Laci Tunai", rp(drawerCash), true),
                Triple("Piutang / Bon Belum Lunas (${unpaidTx.size} Struk)", rp(unpaidTx.sumOf { it.totalPrice }), false),
                Triple("Nilai Aset Stok Produk Jadi", rp(invAsset), false)
            )
            pnlItems.forEachIndexed { idx, (label, valStr, bold) ->
                if (idx % 2 == 1) canvas.drawRect(36f, y, 559f, y + 18f, zebraPaint)
                val p = if (bold) cellBoldPaint else cellPaint
                canvas.drawText(label, 44f, y + 13f, p)
                canvas.drawText(valStr, 551f - p.measureText(valStr), y + 13f, p)
                canvas.drawLine(36f, y + 18f, 559f, y + 18f, linePaint)
                y += 18f
            }

            // BAB 2: REKAP SHIFT & TUTUP KASIR
            y += 22f
            if (y > 720f) y = nextPage()
            canvas.drawText("BAB 2. REKAP SHIFT & TUTUP KASIR (${shifts.size} SESI)", 36f, y, chapterPaint)
            y += 12f
            canvas.drawRect(36f, y, 559f, y + 20f, brandPaint)
            canvas.drawText("Kasir", 42f, y + 14f, tblHeadPaint)
            canvas.drawText("Waktu Buka", 130f, y + 14f, tblHeadPaint)
            canvas.drawText("Modal Awal", 235f, y + 14f, tblHeadPaint)
            canvas.drawText("Kas Sistem", 320f, y + 14f, tblHeadPaint)
            canvas.drawText("Kas Fisik", 405f, y + 14f, tblHeadPaint)
            canvas.drawText("Selisih", 490f, y + 14f, tblHeadPaint)
            y += 20f

            if (shifts.isEmpty()) {
                canvas.drawText("Belum ada data shift pada periode ini.", 42f, y + 14f, cellPaint)
                y += 22f
            } else {
                shifts.forEachIndexed { idx, sh ->
                    if (y > 785f) y = nextPage()
                    if (idx % 2 == 1) canvas.drawRect(36f, y, 559f, y + 18f, zebraPaint)
                    canvas.drawText(sh.userName.take(14), 42f, y + 13f, cellPaint)
                    canvas.drawText(dateFmt.format(Date(sh.startTime)), 130f, y + 13f, cellPaint)
                    canvas.drawText(rp(sh.openingCash), 235f, y + 13f, cellPaint)
                    canvas.drawText(rp(sh.expectedCash), 320f, y + 13f, cellPaint)
                    canvas.drawText(rp(sh.closingCash ?: 0L), 405f, y + 13f, cellPaint)
                    canvas.drawText(rp(sh.discrepancy ?: 0L), 490f, y + 13f, cellBoldPaint)
                    canvas.drawLine(36f, y + 18f, 559f, y + 18f, linePaint)
                    y += 18f
                }
            }

            // BAB 3: ARUS KAS & BIAYA OPERASIONAL
            y += 22f
            if (y > 720f) y = nextPage()
            canvas.drawText("BAB 3. ARUS KAS & PENGELUARAN OPERASIONAL", 36f, y, chapterPaint)
            y += 12f
            canvas.drawRect(36f, y, 559f, y + 20f, brandPaint)
            canvas.drawText("Waktu", 42f, y + 14f, tblHeadPaint)
            canvas.drawText("Jenis", 145f, y + 14f, tblHeadPaint)
            canvas.drawText("Keterangan", 240f, y + 14f, tblHeadPaint)
            canvas.drawText("Nominal", 480f, y + 14f, tblHeadPaint)
            y += 20f

            if (cashFlows.isEmpty() && expenses.isEmpty()) {
                canvas.drawText("Tidak ada catatan arus kas atau pengeluaran pada periode ini.", 42f, y + 14f, cellPaint)
                y += 22f
            } else {
                cashFlows.forEach { cf ->
                    if (y > 785f) y = nextPage()
                    canvas.drawText(dateFmt.format(Date(cf.timestamp)), 42f, y + 13f, cellPaint)
                    canvas.drawText(if (cf.type == "IN") "KAS MASUK" else "KAS KELUAR", 145f, y + 13f, cellBoldPaint)
                    canvas.drawText(cf.note.take(36), 240f, y + 13f, cellPaint)
                    val amtStr = rp(cf.amount)
                    canvas.drawText(amtStr, 553f - cellPaint.measureText(amtStr), y + 13f, cellPaint)
                    canvas.drawLine(36f, y + 18f, 559f, y + 18f, linePaint)
                    y += 18f
                }
                expenses.forEach { exp ->
                    if (y > 785f) y = nextPage()
                    canvas.drawText(dateFmt.format(Date(exp.timestamp)), 42f, y + 13f, cellPaint)
                    canvas.drawText("BIAYA (${exp.category.take(10)})", 145f, y + 13f, cellBoldPaint)
                    canvas.drawText(exp.note.take(36), 240f, y + 13f, cellPaint)
                    val amtStr = rp(exp.amount)
                    canvas.drawText(amtStr, 553f - cellPaint.measureText(amtStr), y + 13f, cellPaint)
                    canvas.drawLine(36f, y + 18f, 559f, y + 18f, linePaint)
                    y += 18f
                }
            }

            // BAB 4: STOK PRODUK & BAHAN BAKU
            y += 22f
            if (y > 720f) y = nextPage()
            canvas.drawText("BAB 4. LAPORAN INVENTARIS STOK & BAHAN BAKU", 36f, y, chapterPaint)
            y += 12f
            canvas.drawRect(36f, y, 559f, y + 20f, brandPaint)
            canvas.drawText("Nama Produk / Bahan", 42f, y + 14f, tblHeadPaint)
            canvas.drawText("Kategori", 240f, y + 14f, tblHeadPaint)
            canvas.drawText("Stok", 335f, y + 14f, tblHeadPaint)
            canvas.drawText("Modal/Unit", 395f, y + 14f, tblHeadPaint)
            canvas.drawText("Nilai Aset", 485f, y + 14f, tblHeadPaint)
            y += 20f

            products.forEachIndexed { idx, p ->
                if (y > 785f) y = nextPage()
                if (idx % 2 == 1) canvas.drawRect(36f, y, 559f, y + 18f, zebraPaint)
                val asset = if (p.isService) 0L else (p.stock * p.costPrice)
                canvas.drawText(p.name.take(32), 42f, y + 13f, cellPaint)
                canvas.drawText(p.category.take(14), 240f, y + 13f, cellPaint)
                canvas.drawText(if (p.isService) "Jasa" else "${p.stock}", 335f, y + 13f, cellPaint)
                canvas.drawText(rp(p.costPrice), 395f, y + 13f, cellPaint)
                val aStr = rp(asset)
                canvas.drawText(aStr, 553f - cellBoldPaint.measureText(aStr), y + 13f, cellBoldPaint)
                canvas.drawLine(36f, y + 18f, 559f, y + 18f, linePaint)
                y += 18f
            }
            rawMaterials.forEach { rm ->
                if (y > 785f) y = nextPage()
                val asset = (rm.stock * rm.costPerUnit).toLong()
                canvas.drawText("[Bahan] ${rm.name}".take(32), 42f, y + 13f, cellPaint)
                canvas.drawText(rm.unit, 240f, y + 13f, cellPaint)
                canvas.drawText("${rm.stock}", 335f, y + 13f, cellPaint)
                canvas.drawText(rp(rm.costPerUnit.toLong()), 395f, y + 13f, cellPaint)
                val aStr = rp(asset)
                canvas.drawText(aStr, 553f - cellBoldPaint.measureText(aStr), y + 13f, cellBoldPaint)
                canvas.drawLine(36f, y + 18f, 559f, y + 18f, linePaint)
                y += 18f
            }

            // BAB 5: PEMBELIAN SUPPLIER (PO)
            if (purchases.isNotEmpty()) {
                y += 22f
                if (y > 720f) y = nextPage()
                canvas.drawText("BAB 5. RIWAYAT PEMBELIAN SUPPLIER (PO)", 36f, y, chapterPaint)
                y += 12f
                canvas.drawRect(36f, y, 559f, y + 20f, brandPaint)
                canvas.drawText("Waktu", 42f, y + 14f, tblHeadPaint)
                canvas.drawText("No. Invoice", 145f, y + 14f, tblHeadPaint)
                canvas.drawText("Supplier", 255f, y + 14f, tblHeadPaint)
                canvas.drawText("Status", 395f, y + 14f, tblHeadPaint)
                canvas.drawText("Total", 495f, y + 14f, tblHeadPaint)
                y += 20f

                purchases.forEach { po ->
                    if (y > 785f) y = nextPage()
                    canvas.drawText(dateFmt.format(Date(po.timestamp)), 42f, y + 13f, cellPaint)
                    canvas.drawText(po.invoiceNumber.take(16), 145f, y + 13f, cellPaint)
                    canvas.drawText(po.supplierName.take(22), 255f, y + 13f, cellPaint)
                    canvas.drawText(po.status, 395f, y + 13f, cellBoldPaint)
                    val tStr = rp(po.totalAmount)
                    canvas.drawText(tStr, 553f - cellBoldPaint.measureText(tStr), y + 13f, cellBoldPaint)
                    canvas.drawLine(36f, y + 18f, 559f, y + 18f, linePaint)
                    y += 18f
                }
            }

            // BAB 6: RINCIAN TRANSAKSI PENJUALAN & ITEM
            y += 22f
            if (y > 720f) y = nextPage()
            canvas.drawText("BAB 6. RINCIAN TRANSAKSI PENJUALAN (${transactions.size} STRUK)", 36f, y, chapterPaint)
            y += 14f

            for (tx in transactions) {
                if (y > 755f) y = nextPage()
                canvas.drawRect(36f, y, 559f, y + 19f, lightBoxPaint)
                canvas.drawText(
                    "Struk #${tx.id}  |  ${dateFmt.format(Date(tx.timestamp))}  |  ${tx.customerName}  |  Kasir: ${tx.cashierName} (${tx.paymentMethod}) [${tx.status}]",
                    42f,
                    y + 13f,
                    cellBoldPaint
                )
                y += 21f

                val items = viewModel.getTransactionItems(tx.id).first()
                for (it in items) {
                    if (y > 785f) y = nextPage()
                    val uPrice = if (it.quantity > 0) it.subtotal / it.quantity else it.price
                    canvas.drawText("   • ${it.quantity}x ${it.productName} @ ${rp(uPrice)}", 44f, y + 12f, cellPaint)
                    val sStr = rp(it.subtotal)
                    canvas.drawText(sStr, 553f - cellPaint.measureText(sStr), y + 12f, cellPaint)
                    y += 15f
                }
                val totStr = "Total Struk #${tx.id}: ${rp(tx.totalPrice)}"
                canvas.drawText(totStr, 553f - cellBoldPaint.measureText(totStr), y + 12f, cellBoldPaint)
                y += 18f
                canvas.drawLine(36f, y, 559f, y, linePaint)
                y += 6f
            }

            // BAB 7: LAMPIRAN JEJAK AKTIVITAS (AUDIT LOG)
            if (auditLogs.isNotEmpty()) {
                y += 16f
                if (y > 720f) y = nextPage()
                canvas.drawText("BAB 7. LAMPIRAN JEJAK AKTIVITAS (AUDIT LOG)", 36f, y, chapterPaint)
                y += 14f
                for (log in auditLogs.take(100)) {
                    if (y > 785f) y = nextPage()
                    canvas.drawText("[${dateFmt.format(Date(log.timestamp))}] ${log.userName}: ${log.action}".take(92), 42f, y + 12f, cellPaint)
                    y += 15f
                }
            }

            finishCurrentPage()
            withContext(Dispatchers.Main) {
                context.contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
                pdf.close()
                Toast.makeText(context, "E-Book Pembukuan Lengkap (PDF) berhasil disimpan!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Gagal membuat E-Book Pembukuan PDF: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
