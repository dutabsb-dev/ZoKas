package com.karyaku.kasir

import android.content.Context
import android.net.Uri
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.ShiftEntity
import com.karyaku.kasir.data.local.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelExporter {

    suspend fun exportProductsToExcel(context: Context, uri: Uri, products: List<ProductEntity>): Boolean = withContext(Dispatchers.IO) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Daftar Produk")

            val headerFont = workbook.createFont().apply { bold = true }
            val headerCellStyle = workbook.createCellStyle().apply {
                setFont(headerFont)
                verticalAlignment = VerticalAlignment.CENTER
            }
            val wrapTextStyle = workbook.createCellStyle().apply {
                wrapText = true
                verticalAlignment = VerticalAlignment.TOP
            }
            val currencyFormat = workbook.createDataFormat().getFormat("#,##0")
            val currencyStyle = workbook.createCellStyle().apply {
                dataFormat = currencyFormat
                verticalAlignment = VerticalAlignment.TOP
            }
            val topAlignStyle = workbook.createCellStyle().apply {
                verticalAlignment = VerticalAlignment.TOP
            }

            val headerRow = sheet.createRow(0)
            headerRow.heightInPoints = 22f
            val headers = listOf(
                "Nama", "HargaJual", "HargaModal", "Stok", "Kategori", "Barcode",
                "Varian", "DetailVarian", "Komisi", "Expiry", "IsService", "HargaGrosir", "MinGrosir"
            )
            headers.forEachIndexed { index, title ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(title)
                cell.cellStyle = headerCellStyle
            }

            val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

            products.forEachIndexed { rowIndex, product ->
                val row = sheet.createRow(rowIndex + 1)

                row.createCell(0).apply {
                    setCellValue(product.name)
                    cellStyle = topAlignStyle
                }
                row.createCell(1).apply {
                    setCellValue(product.price.toDouble())
                    cellStyle = currencyStyle
                }
                row.createCell(2).apply {
                    setCellValue(product.costPrice.toDouble())
                    cellStyle = currencyStyle
                }
                row.createCell(3).apply {
                    setCellValue(product.stock.toDouble())
                    cellStyle = topAlignStyle
                }
                row.createCell(4).apply {
                    setCellValue(product.category)
                    cellStyle = topAlignStyle
                }
                row.createCell(5).apply {
                    setCellValue(product.barcode)
                    cellStyle = topAlignStyle
                }

                // Kolom 6: Format mentah varian (aman untuk impor balik ke ZoKas)
                row.createCell(6).apply {
                    setCellValue(product.variations)
                    cellStyle = wrapTextStyle
                }

                // Kolom 7: Detail varian berlabel & multi-baris agar mudah dibaca
                val detailVarianText = ProductVariantExcelFormatter.formatVariantsMultiline(product)
                val variantCount = ProductVariantExcelFormatter.parseDetailedVariants(product).size.coerceAtLeast(1)
                row.createCell(7).apply {
                    setCellValue(detailVarianText)
                    cellStyle = wrapTextStyle
                }

                if (product.variations.isNotBlank()) {
                    row.heightInPoints = (variantCount * 16f + 8f).coerceAtLeast(24f)
                }

                row.createCell(8).apply {
                    setCellValue(product.commissionPerItem.toDouble())
                    cellStyle = currencyStyle
                }

                val expiryStr = if (product.expiryDate > 0) dateFormat.format(Date(product.expiryDate)) else "-"
                row.createCell(9).apply {
                    setCellValue(expiryStr)
                    cellStyle = topAlignStyle
                }

                row.createCell(10).apply {
                    setCellValue(if (product.isService) "TRUE" else "FALSE")
                    cellStyle = topAlignStyle
                }
                row.createCell(11).apply {
                    setCellValue(product.wholesalePrice.toDouble())
                    cellStyle = currencyStyle
                }
                row.createCell(12).apply {
                    setCellValue(product.wholesaleMinQty.toDouble())
                    cellStyle = topAlignStyle
                }
            }

            sheet.setColumnWidth(0, 8000)
            sheet.setColumnWidth(1, 4200)
            sheet.setColumnWidth(2, 4200)
            sheet.setColumnWidth(3, 3000)
            sheet.setColumnWidth(4, 5000)
            sheet.setColumnWidth(5, 5500)
            sheet.setColumnWidth(6, 9000)
            sheet.setColumnWidth(7, 15500)
            sheet.setColumnWidth(8, 3800)
            sheet.setColumnWidth(9, 3800)
            sheet.setColumnWidth(10, 3200)
            sheet.setColumnWidth(11, 4200)
            sheet.setColumnWidth(12, 3400)

            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                workbook.write(outputStream)
                outputStream.close()
            }
            workbook.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importProductsFromExcel(context: Context, uri: Uri): List<ProductEntity>? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return@withContext null

            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            val products = mutableListOf<ProductEntity>()
            val dataFormatter = DataFormatter()

            val headerRow = sheet.getRow(0)
            val hasDetailColumn = (0 until (headerRow?.lastCellNum ?: 0)).any { colIdx ->
                val hVal = headerRow?.getCell(colIdx)?.let { dataFormatter.formatCellValue(it).trim() }.orEmpty()
                hVal.equals("DetailVarian", ignoreCase = true)
            }

            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue

                fun getCellValue(index: Int): String {
                    val cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) ?: return ""
                    return dataFormatter.formatCellValue(cell)
                }

                val name = getCellValue(0).trim()
                if (name.isBlank()) continue

                val price = getCellValue(1).replace(Regex("[^0-9.-]"), "").toDoubleOrNull()?.toLong() ?: 0L
                val costPrice = getCellValue(2).replace(Regex("[^0-9.-]"), "").toDoubleOrNull()?.toLong() ?: 0L
                val stock = getCellValue(3).replace(Regex("[^0-9.-]"), "").toDoubleOrNull()?.toInt() ?: 0
                val category = getCellValue(4).ifBlank { "Umum" }
                val barcode = getCellValue(5)

                val variations: String
                val commission: Long
                val expiryStr: String
                val isServiceRaw: String
                val wholesalePrice: Long
                val wholesaleMinQty: Int

                if (hasDetailColumn) {
                    variations = getCellValue(6)
                    commission = getCellValue(8).replace(Regex("[^0-9.-]"), "").toDoubleOrNull()?.toLong() ?: 0L
                    expiryStr = getCellValue(9)
                    isServiceRaw = getCellValue(10)
                    wholesalePrice = getCellValue(11).replace(Regex("[^0-9.-]"), "").toDoubleOrNull()?.toLong() ?: 0L
                    wholesaleMinQty = getCellValue(12).replace(Regex("[^0-9.-]"), "").toDoubleOrNull()?.toInt() ?: 0
                } else {
                    variations = getCellValue(6)
                    commission = getCellValue(7).replace(Regex("[^0-9.-]"), "").toDoubleOrNull()?.toLong() ?: 0L
                    expiryStr = getCellValue(8)
                    isServiceRaw = getCellValue(9)
                    wholesalePrice = getCellValue(10).replace(Regex("[^0-9.-]"), "").toDoubleOrNull()?.toLong() ?: 0L
                    wholesaleMinQty = getCellValue(11).replace(Regex("[^0-9.-]"), "").toDoubleOrNull()?.toInt() ?: 0
                }

                val expiryDate = try {
                    if (expiryStr.isNotBlank() && expiryStr != "-") {
                        val format = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                        format.parse(expiryStr)?.time ?: 0L
                    } else 0L
                } catch (e: Exception) { 0L }

                val isService = isServiceRaw.equals("TRUE", ignoreCase = true) || isServiceRaw == "1"

                products.add(ProductEntity(
                    name = name, price = price, stock = stock, category = category, costPrice = costPrice,
                    barcode = barcode, variations = variations, commissionPerItem = commission, expiryDate = expiryDate,
                    isService = isService, wholesalePrice = wholesalePrice, wholesaleMinQty = wholesaleMinQty,
                    isSynced = false, updatedAt = System.currentTimeMillis()
                ))
            }

            inputStream.close()
            workbook.close()
            products
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun exportTransactionsToExcel(context: Context, uri: Uri, transactions: List<TransactionEntity>): Boolean = withContext(Dispatchers.IO) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Transactions")
            val settingsManager = SettingsManager(context)
            val startRow = UniversalBookkeepingExporter.attachExcelStoreHeader(
                context = context,
                workbook = workbook,
                sheet = sheet,
                settingsManager = settingsManager,
                reportTitle = "LAPORAN TRANSAKSI PENJUALAN",
                periodLabel = "${transactions.size} Transaksi",
                totalCols = 9
            )

            val headerFont = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.index
            }
            val headerCellStyle = workbook.createCellStyle().apply {
                setFont(headerFont)
                fillForegroundColor = IndexedColors.DARK_TEAL.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
            }

            val headerRow = sheet.createRow(startRow)
            val headers = listOf("ID", "Date", "Total Price", "Cash Received", "Change", "Customer", "Status", "Payment Method", "Cashier")
            headers.forEachIndexed { index, title ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(title)
                cell.cellStyle = headerCellStyle
            }

            val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())

            transactions.forEachIndexed { rowIndex, trx ->
                val row = sheet.createRow(startRow + rowIndex + 1)
                row.createCell(0).setCellValue(trx.id.toDouble())
                row.createCell(1).setCellValue(dateFormat.format(Date(trx.timestamp)))
                row.createCell(2).setCellValue(trx.totalPrice.toDouble())
                row.createCell(3).setCellValue(trx.cashReceived.toDouble())
                row.createCell(4).setCellValue(trx.changeAmount.toDouble())
                row.createCell(5).setCellValue(trx.customerName)
                row.createCell(6).setCellValue(trx.status)
                row.createCell(7).setCellValue(trx.paymentMethod)
                row.createCell(8).setCellValue(trx.cashierName)
            }

            sheet.setColumnWidth(1, 6000)
            sheet.setColumnWidth(5, 6000)

            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                workbook.write(outputStream)
                outputStream.close()
            }
            workbook.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportShiftsToExcel(context: Context, uri: Uri, shifts: List<ShiftEntity>): Boolean = withContext(Dispatchers.IO) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Laporan Tutup Kasir")
            val settingsManager = SettingsManager(context)
            val startRow = UniversalBookkeepingExporter.attachExcelStoreHeader(
                context = context,
                workbook = workbook,
                sheet = sheet,
                settingsManager = settingsManager,
                reportTitle = "LAPORAN SHIFT & TUTUP KASIR",
                periodLabel = "${shifts.size} Sesi Shift",
                totalCols = 10
            )

            val headerFont = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.index
            }
            val headerCellStyle = workbook.createCellStyle().apply {
                setFont(headerFont)
                fillForegroundColor = IndexedColors.DARK_TEAL.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
            }

            val currencyFormat = workbook.createDataFormat().getFormat("#,##0")
            val currencyStyle = workbook.createCellStyle().apply { dataFormat = currencyFormat }

            val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())

            val headerRow = sheet.createRow(startRow)
            val headers = listOf(
                "Tanggal Buka", "Kasir", "Waktu Buka", "Waktu Tutup",
                "Modal Awal", "Kas Sistem", "Kas Fisik", "Selisih", "Status", "Catatan"
            )
            headers.forEachIndexed { index, title ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(title)
                cell.cellStyle = headerCellStyle
            }

            shifts.forEachIndexed { rowIndex, shift ->
                val row = sheet.createRow(startRow + rowIndex + 1)

                val dateOpen = dateFormat.format(Date(shift.startTime)).split(" ").firstOrNull() ?: "-"
                row.createCell(0).setCellValue(dateOpen)

                row.createCell(1).setCellValue(shift.userName)
                row.createCell(2).setCellValue(dateFormat.format(Date(shift.startTime)))

                val closeTimeStr = if (shift.endTime != null) dateFormat.format(Date(shift.endTime)) else "MASIH BUKA"
                row.createCell(3).setCellValue(closeTimeStr)

                val modalCell = row.createCell(4)
                modalCell.setCellValue(shift.openingCash.toDouble())
                modalCell.cellStyle = currencyStyle

                val systemCell = row.createCell(5)
                systemCell.setCellValue(shift.expectedCash.toDouble())
                systemCell.cellStyle = currencyStyle

                val fisikCell = row.createCell(6)
                fisikCell.setCellValue((shift.closingCash ?: 0L).toDouble())
                fisikCell.cellStyle = currencyStyle

                val selisihCell = row.createCell(7)
                selisihCell.setCellValue((shift.discrepancy ?: 0L).toDouble())
                selisihCell.cellStyle = currencyStyle

                row.createCell(8).setCellValue(shift.status)
                row.createCell(9).setCellValue(shift.note)
            }

            sheet.setColumnWidth(1, 5000)
            sheet.setColumnWidth(2, 6000)
            sheet.setColumnWidth(3, 6000)
            sheet.setColumnWidth(4, 5000)
            sheet.setColumnWidth(5, 5000)
            sheet.setColumnWidth(6, 5000)
            sheet.setColumnWidth(7, 4000)
            sheet.setColumnWidth(8, 4000)
            sheet.setColumnWidth(9, 10000)

            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                workbook.write(outputStream)
                outputStream.close()
            }
            workbook.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
