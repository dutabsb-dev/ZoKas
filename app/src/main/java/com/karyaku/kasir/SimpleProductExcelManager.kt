package com.karyaku.kasir

import android.content.Context
import android.net.Uri
import com.karyaku.kasir.data.local.ProductEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class SimpleProductRow(
    val name: String,
    val category: String,
    val costPrice: Long,
    val sellPrice: Long,
    val stock: Int,
    val unit: String,
    val sku: String,
    val barcode: String,
    val variantName: String = "",
    val variantPrice: Long = 0L,
    val variantStock: Int = 0,
    val variantBarcode: String = ""
)

object SimpleProductExcelManager {
    const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    // 12 kolom terpisah (1 kolom = 1 informasi) agar mudah diedit di Excel/WPS/Sheets tanpa typo
    val SIMPLE_HEADERS = listOf(
        "Nama Produk",
        "Kategori",
        "Harga Beli",
        "Harga Jual",
        "Stok",
        "Satuan",
        "SKU",
        "Barcode Kemasan",
        "Nama Varian",
        "Harga Varian",
        "Stok Varian",
        "Barcode Varian"
    )

    fun buildSimpleTemplateXlsxBytes(): ByteArray {
        val rows = listOf(
            SIMPLE_HEADERS,
            listOf(
                "Kopi Hitam Tubruk",
                "Minuman",
                "3500",
                "6000",
                "25",
                "pcs",
                "KOP-001",
                "899100100001",
                "",
                "",
                "",
                ""
            ),
            listOf(
                "Sambal Pedas Botol",
                "Sembako",
                "8000",
                "12000",
                "15",
                "btl",
                "SBL-002",
                "899100200001",
                "Extra Pedas",
                "13500",
                "8",
                "899100200002"
            )
        )
        return writeRowsToXlsxBytes(rows, sheetName = "Produk Simpel")
    }

    fun exportProductsToSimpleXlsxUri(
        context: Context,
        uri: Uri,
        products: List<ProductEntity>
    ): Pair<Int, String> {
        return try {
            val out = context.contentResolver.openOutputStream(uri)
                ?: return 0 to "Tidak dapat membuka file tujuan ekspor."

            val rows = mutableListOf<List<String>>()
            rows.add(SIMPLE_HEADERS)

            products.forEach { p ->
                val sku = extractProductSku(p.barcode)
                val bar = extractActualBarcode(p.barcode)
                val unit = extractProductBaseUnit(p)
                val variantEntries = if (p.variations.isNotBlank()) {
                    p.variations.replace("|", ",").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }

                if (variantEntries.isEmpty()) {
                    rows.add(
                        listOf(
                            p.name,
                            p.category,
                            p.costPrice.toString(),
                            p.price.toString(),
                            p.stock.toString(),
                            unit,
                            sku,
                            bar,
                            "",
                            "",
                            "",
                            ""
                        )
                    )
                } else {
                    variantEntries.forEach { vEntry ->
                        val parts = vEntry.split(":")
                        val vName = parts.getOrNull(0)?.trim().orEmpty()
                        val vPrice = parts.getOrNull(1)?.trim().orEmpty().ifBlank { p.price.toString() }
                        val vStock = parts.getOrNull(3)?.trim().orEmpty().ifBlank { p.stock.toString() }
                        val vBar = parts.getOrNull(5)?.trim().orEmpty()
                        rows.add(
                            listOf(
                                p.name,
                                p.category,
                                parts.getOrNull(2)?.trim().orEmpty().ifBlank { p.costPrice.toString() },
                                p.price.toString(),
                                p.stock.toString(),
                                unit,
                                sku,
                                bar,
                                vName,
                                vPrice,
                                vStock,
                                vBar
                            )
                        )
                    }
                }
            }

            val bytes = writeRowsToXlsxBytes(rows, sheetName = "Daftar Produk")
            out.use { stream ->
                stream.write(bytes)
                stream.flush()
            }
            products.size to "Berhasil mengekspor ${products.size} produk ke file Excel (.xlsx) simpel."
        } catch (e: Exception) {
            0 to "Gagal ekspor Excel (.xlsx): ${e.message ?: "kesalahan penulisan file"}"
        }
    }

    fun parseSimpleSpreadsheetUri(context: Context, uri: Uri): List<SimpleProductRow> {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return emptyList()
        if (rawBytes.isEmpty()) return emptyList()

        // Deteksi magic bytes ZIP ('P','K') untuk file .xlsx asli
        val isZipXlsx = rawBytes.size >= 4 && rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte()
        val rawRows: List<List<String>> = if (isZipXlsx) {
            readRowsFromXlsxBytes(rawBytes)
        } else {
            readRowsFromFallbackText(rawBytes)
        }

        if (rawRows.size <= 1) return emptyList()
        val results = mutableListOf<SimpleProductRow>()
        for (idx in 1 until rawRows.size) {
            val cols = rawRows[idx]
            val pName = cols.getOrNull(0)?.trim().orEmpty()
            val sellPrice = cols.getOrNull(3)?.trim()?.filter { it.isDigit() }?.toLongOrNull() ?: 0L
            if (pName.isNotBlank() && sellPrice > 0L) {
                results.add(
                    SimpleProductRow(
                        name = pName,
                        category = cols.getOrNull(1)?.trim().orEmpty().ifBlank { "Umum" },
                        costPrice = cols.getOrNull(2)?.trim()?.filter { it.isDigit() }?.toLongOrNull() ?: 0L,
                        sellPrice = sellPrice,
                        stock = cols.getOrNull(4)?.trim()?.filter { it.isDigit() }?.toIntOrNull() ?: 0,
                        unit = cols.getOrNull(5)?.trim().orEmpty().ifBlank { "pcs" },
                        sku = cols.getOrNull(6)?.trim().orEmpty().uppercase(),
                        barcode = cols.getOrNull(7)?.trim().orEmpty(),
                        variantName = cols.getOrNull(8)?.trim().orEmpty(),
                        variantPrice = cols.getOrNull(9)?.trim()?.filter { it.isDigit() }?.toLongOrNull() ?: sellPrice,
                        variantStock = cols.getOrNull(10)?.trim()?.filter { it.isDigit() }?.toIntOrNull() ?: 0,
                        variantBarcode = cols.getOrNull(11)?.trim().orEmpty()
                    )
                )
            }
        }
        return results
    }

    private fun writeRowsToXlsxBytes(rows: List<List<String>>, sheetName: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // 1. [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""".toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 2. _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 3. xl/workbook.xml
            val safeSheetName = escapeXml(sheetName.take(30).ifBlank { "Produk" })
            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="$safeSheetName" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 4. xl/_rels/workbook.xml.rels
            zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 5. xl/styles.xml (Header bold dengan warna hijau lembut agar rapi di Excel)
            zos.putNextEntry(ZipEntry("xl/styles.xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF059669"/><bgColor indexed="64"/></patternFill></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>""".toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 6. xl/worksheets/sheet1.xml (Inline strings + numeric cells)
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val sheetSb = StringBuilder()
            sheetSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            sheetSb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            sheetSb.append("""<cols>""")
            val colWidths = listOf(26, 16, 14, 14, 10, 10, 14, 18, 18, 14, 12, 18)
            colWidths.forEachIndexed { i, w ->
                val colNum = i + 1
                sheetSb.append("""<col min="$colNum" max="$colNum" width="$w" customWidth="1"/>""")
            }
            sheetSb.append("""</cols><sheetData>""")

            rows.forEachIndexed { rIdx, rowCells ->
                val rowNum = rIdx + 1
                val styleAttr = if (rIdx == 0) """ s="1"""" else ""
                sheetSb.append("""<row r="$rowNum">""")
                rowCells.forEachIndexed { cIdx, rawVal ->
                    val cellRef = "${colIndexToLetters(cIdx)}$rowNum"
                    val isNumericCol = rIdx > 0 && cIdx in listOf(2, 3, 4, 9, 10) && rawVal.isNotBlank() && rawVal.all { it.isDigit() }
                    if (isNumericCol) {
                        sheetSb.append("""<c r="$cellRef"><v>$rawVal</v></c>""")
                    } else {
                        val safeText = escapeXml(rawVal)
                        sheetSb.append("""<c r="$cellRef"$styleAttr t="inlineStr"><is><t>$safeText</t></is></c>""")
                    }
                }
                sheetSb.append("""</row>""")
            }
            sheetSb.append("""</sheetData></worksheet>""")
            zos.write(sheetSb.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun readRowsFromXlsxBytes(xlsxBytes: ByteArray): List<List<String>> {
        var sharedStringsXml: ByteArray? = null
        var sheet1Xml: ByteArray? = null

        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (!entry.isDirectory) {
                    if (name == "xl/sharedstrings.xml") {
                        sharedStringsXml = zis.readBytes()
                    } else if (name == "xl/worksheets/sheet1.xml" || (sheet1Xml == null && name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml"))) {
                        sheet1Xml = zis.readBytes()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sheetBytes = sheet1Xml ?: return emptyList()
        val sharedStrings = sharedStringsXml?.let { parseSharedStrings(it) }.orEmpty()

        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(sheetBytes), "UTF-8")

        val allRows = mutableListOf<List<String>>()
        var currentRow = mutableMapOf<Int, String>()
        var currentColIdx = 0
        var currentCellType = ""
        var inValueOrText = false
        val textAcc = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRow = mutableMapOf()
                        "c" -> {
                            val ref = parser.getAttributeValue(null, "r").orEmpty()
                            currentColIdx = parseColIndexFromRef(ref, currentRow.size)
                            currentCellType = parser.getAttributeValue(null, "t").orEmpty()
                            textAcc.setLength(0)
                        }
                        "v", "t" -> {
                            inValueOrText = true
                            textAcc.setLength(0)
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inValueOrText) {
                        textAcc.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v", "t" -> inValueOrText = false
                        "c" -> {
                            val raw = textAcc.toString()
                            val resolved = if (currentCellType == "s") {
                                val sIdx = raw.trim().toIntOrNull() ?: -1
                                sharedStrings.getOrElse(sIdx) { "" }
                            } else {
                                raw
                            }
                            currentRow[currentColIdx] = resolved
                        }
                        "row" -> {
                            if (currentRow.isNotEmpty()) {
                                val maxCol = (currentRow.keys.maxOrNull() ?: 0).coerceAtLeast(11)
                                val listRow = (0..maxCol).map { idx -> currentRow[idx].orEmpty() }
                                allRows.add(listRow)
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return allRows
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
        var inSi = false
        var inT = false
        val sb = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        inSi = true
                        sb.setLength(0)
                    } else if (inSi && parser.name == "t") {
                        inT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inSi && inT) sb.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "t") inT = false
                    else if (parser.name == "si") {
                        inSi = false
                        list.add(sb.toString())
                    }
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun readRowsFromFallbackText(bytes: ByteArray): List<List<String>> {
        val out = mutableListOf<List<String>>()
        BufferedReader(InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8)).use { reader ->
            var delimiter = ','
            var first = true
            var line = reader.readLine()
            while (line != null) {
                val cleaned = line.removePrefix("\uFEFF").trim()
                if (cleaned.isNotEmpty()) {
                    if (first) {
                        delimiter = if (cleaned.count { it == ';' } > cleaned.count { it == ',' }) ';' else ','
                        first = false
                    }
                    out.add(cleaned.split(delimiter).map { it.trim().trim('"') })
                }
                line = reader.readLine()
            }
        }
        return out
    }

    private fun colIndexToLetters(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.append(('A'.code + (n % 26)).toChar())
            n = (n / 26) - 1
        }
        return sb.reverse().toString()
    }

    private fun parseColIndexFromRef(cellRef: String, fallback: Int): Int {
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return fallback
        var col = 0
        for (ch in letters) {
            col = col * 26 + (ch.code - 'A'.code + 1)
        }
        return (col - 1).coerceAtLeast(0)
    }

    private fun escapeXml(s: String): String {
        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
