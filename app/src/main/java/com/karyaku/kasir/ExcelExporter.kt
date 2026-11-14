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
            val headerCellStyle = workbook.createCellStyle().apply { setFont(headerFont) }
            val wrapTextStyle = workbook.createCellStyle().apply { wrapText = true }

            val headerRow = sheet.createRow(0)
            val headers = listOf(
                "Nama", "HargaJual", "HargaModal", "Stok", "Kategori", "Barcode", 
                "Varian", "Komisi", "Expiry", "IsService", "HargaGrosir", "MinGrosir"
            )
            headers.forEachIndexed { index, title ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(title)
                cell.cellStyle = headerCellStyle
            }

            val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

            products.forEachIndexed { rowIndex, product ->
                val row = sheet.createRow(rowIndex + 1)
                row.createCell(0).setCellValue(product.name)
                row.createCell(1).setCellValue(product.price.toDouble())
                row.createCell(2).setCellValue(product.costPrice.toDouble())
                row.createCell(3).setCellValue(product.stock.toDouble())
                row.createCell(4).setCellValue(product.category)
                row.createCell(5).setCellValue(product.barcode)
                
                val varianCell = row.createCell(6)
                varianCell.setCellValue(product.variations)
                varianCell.cellStyle = wrapTextStyle
                
                row.createCell(7).setCellValue(product.commissionPerItem.toDouble())
                
                val expiryStr = if (product.expiryDate > 0) dateFormat.format(Date(product.expiryDate)) else "-"
                row.createCell(8).setCellValue(expiryStr)
                
                row.createCell(9).setCellValue(if (product.isService) "TRUE" else "FALSE")
                row.createCell(10).setCellValue(product.wholesalePrice.toDouble())
                row.createCell(11).setCellValue(product.wholesaleMinQty.toDouble())
            }

            sheet.setColumnWidth(0, 8000)
            sheet.setColumnWidth(4, 5000)
            sheet.setColumnWidth(5, 5000)
            sheet.setColumnWidth(6, 10000)

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

            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                
                fun getCellValue(index: Int): String {
                    val cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) ?: return ""
                    return dataFormatter.formatCellValue(cell)
                }

                val name = getCellValue(0).trim()
                if (name.isBlank()) continue

                val price = getCellValue(1).toDoubleOrNull()?.toLong() ?: 0L
                val costPrice = getCellValue(2).toDoubleOrNull()?.toLong() ?: 0L
                val stock = getCellValue(3).toDoubleOrNull()?.toInt() ?: 0
                val category = getCellValue(4).ifBlank { "Umum" }
                val barcode = getCellValue(5)
                val variations = getCellValue(6)
                val commission = getCellValue(7).toDoubleOrNull()?.toLong() ?: 0L
                
                val expiryStr = getCellValue(8)
                val expiryDate = try {
                    if (expiryStr.isNotBlank() && expiryStr != "-") {
                        val format = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                        format.parse(expiryStr)?.time ?: 0L
                    } else 0L
                } catch (e: Exception) { 0L }

                val isService = getCellValue(9).equals("TRUE", ignoreCase = true) || getCellValue(9) == "1"
                val wholesalePrice = getCellValue(10).toDoubleOrNull()?.toLong() ?: 0L
                val wholesaleMinQty = getCellValue(11).toDoubleOrNull()?.toInt() ?: 0

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

            val headerFont = workbook.createFont().apply { bold = true }
            val headerCellStyle = workbook.createCellStyle().apply { setFont(headerFont) }

            val headerRow = sheet.createRow(0)
            val headers = listOf("ID", "Date", "Total Price", "Cash Received", "Change", "Customer", "Status", "Payment Method", "Cashier")
            headers.forEachIndexed { index, title ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(title)
                cell.cellStyle = headerCellStyle
            }

            val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())

            transactions.forEachIndexed { rowIndex, trx ->
                val row = sheet.createRow(rowIndex + 1)
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

    // ================================================================
    // FUNGSI EKSPOR LAPORAN SHIFT (TUTUP KASIR) - FIX CATATAN
    // ================================================================
    suspend fun exportShiftsToExcel(context: Context, uri: Uri, shifts: List<ShiftEntity>): Boolean = withContext(Dispatchers.IO) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Laporan Tutup Kasir")

            val headerFont = workbook.createFont().apply { bold = true }
            val headerCellStyle = workbook.createCellStyle().apply { setFont(headerFont) }

            val currencyFormat = workbook.createDataFormat().getFormat("#,##0")
            val currencyStyle = workbook.createCellStyle().apply { dataFormat = currencyFormat }
            
            val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())

            val headerRow = sheet.createRow(0)
            // FIX: Tambahkan "Catatan"
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
                val row = sheet.createRow(rowIndex + 1)
                
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
                // FIX: Tulis catatan ke Excel
                row.createCell(9).setCellValue(shift.note)
            }

            sheet.setColumnWidth(0, 4000)
            sheet.setColumnWidth(1, 5000)
            sheet.setColumnWidth(2, 6000)
            sheet.setColumnWidth(3, 6000)
            sheet.setColumnWidth(4, 5000)
            sheet.setColumnWidth(5, 5000)
            sheet.setColumnWidth(6, 5000)
            sheet.setColumnWidth(7, 4000)
            sheet.setColumnWidth(8, 4000)
            sheet.setColumnWidth(9, 10000) // Lebar kolom catatan

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