package com.karyaku.kasir

import android.content.Context
import android.net.Uri
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

object ExcelExporter {

    suspend fun exportProductsToExcel(context: Context, uri: Uri, products: List<ProductEntity>): Boolean = withContext(Dispatchers.IO) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Daftar Produk")

            // Header Style
            val headerFont = workbook.createFont().apply { bold = true }
            val headerCellStyle = workbook.createCellStyle().apply { setFont(headerFont) }

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

            // Data
            products.forEachIndexed { rowIndex, product ->
                val row = sheet.createRow(rowIndex + 1)
                row.createCell(0).setCellValue(product.name)
                row.createCell(1).setCellValue(product.price.toDouble())
                row.createCell(2).setCellValue(product.costPrice.toDouble())
                row.createCell(3).setCellValue(product.stock.toDouble())
                row.createCell(4).setCellValue(product.category)
                row.createCell(5).setCellValue(product.barcode)
                row.createCell(6).setCellValue(product.variations)
                row.createCell(7).setCellValue(product.commissionPerItem.toDouble())
                row.createCell(8).setCellValue(product.expiryDate.toDouble())
                row.createCell(9).setCellValue(if (product.isService) "TRUE" else "FALSE")
                row.createCell(10).setCellValue(product.wholesalePrice.toDouble())
                row.createCell(11).setCellValue(product.wholesaleMinQty.toDouble())
            }

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

            // Loop mulai dari baris 1 (skip header)
            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                
                // Helper untuk mengambil nilai string sel dengan aman
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
                val expiry = getCellValue(8).toDoubleOrNull()?.toLong() ?: 0L
                val isService = getCellValue(9).equals("TRUE", ignoreCase = true) || getCellValue(9) == "1"
                val wholesalePrice = getCellValue(10).toDoubleOrNull()?.toLong() ?: 0L
                val wholesaleMinQty = getCellValue(11).toDoubleOrNull()?.toInt() ?: 0

                products.add(ProductEntity(
                    name = name, price = price, stock = stock, category = category, costPrice = costPrice,
                    barcode = barcode, variations = variations, commissionPerItem = commission, expiryDate = expiry,
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

            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

            transactions.forEachIndexed { rowIndex, trx ->
                val row = sheet.createRow(rowIndex + 1)
                row.createCell(0).setCellValue(trx.id.toDouble())
                row.createCell(1).setCellValue(dateFormat.format(java.util.Date(trx.timestamp)))
                row.createCell(2).setCellValue(trx.totalPrice.toDouble())
                row.createCell(3).setCellValue(trx.cashReceived.toDouble())
                row.createCell(4).setCellValue(trx.changeAmount.toDouble())
                row.createCell(5).setCellValue(trx.customerName)
                row.createCell(6).setCellValue(trx.status)
                row.createCell(7).setCellValue(trx.paymentMethod)
                row.createCell(8).setCellValue(trx.cashierName)
            }

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