package com.karyaku.kasir

import android.content.Context
import android.net.Uri
import com.karyaku.kasir.data.local.ProductEntity
import com.karyaku.kasir.data.local.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream

object ExcelExporter {

    suspend fun exportProductsToExcel(context: Context, uri: Uri, products: List<ProductEntity>): Boolean = withContext(Dispatchers.IO) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Products")

            // Header Style
            val headerFont = workbook.createFont().apply { bold = true }
            val headerCellStyle = workbook.createCellStyle().apply { setFont(headerFont) }

            val headerRow = sheet.createRow(0)
            val headers = listOf("ID", "Name", "Price", "Stock", "Category", "Cost Price", "Barcode", "Variations", "Is Service")
            headers.forEachIndexed { index, title ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(title)
                cell.cellStyle = headerCellStyle
            }

            // Data
            products.forEachIndexed { rowIndex, product ->
                val row = sheet.createRow(rowIndex + 1)
                row.createCell(0).setCellValue(product.id.toDouble())
                row.createCell(1).setCellValue(product.name)
                row.createCell(2).setCellValue(product.price.toDouble())
                row.createCell(3).setCellValue(product.stock.toDouble())
                row.createCell(4).setCellValue(product.category)
                row.createCell(5).setCellValue(product.costPrice.toDouble())
                row.createCell(6).setCellValue(product.barcode)
                row.createCell(7).setCellValue(product.variations)
                row.createCell(8).setCellValue(if (product.isService) "Yes" else "No")
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