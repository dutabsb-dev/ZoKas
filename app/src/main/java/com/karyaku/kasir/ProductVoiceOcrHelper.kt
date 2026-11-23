package com.karyaku.kasir

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

data class SmartLabelHint(
    val bestProductName: String = "",
    val candidates: List<String> = emptyList(),
    val suggestedPrice: Long? = null,
    val suggestedBarcodeOrSku: String = "",
    val suggestedCategoryHint: String = ""
)

object ProductVoiceOcrHelper {
    private val NOISE_KEYWORDS = listOf(
        "EXP", "EXPIRED", "MFG", "PROD", "PRODUKSI", "BAIK DIGUNAKAN",
        "BPOM", "P-IRT", "MD ", "ML ", "HALAL", "NETTO", "BERAT BERSIH",
        "KOMPOSISI", "INGREDIENTS", "SIMPAN", "SEJUK", "KERING", "MATAHARI",
        "PT.", "PT ", "CV.", "CV ", "TBK", "JL.", "JALAN", "KOTA", "KAB.",
        "PROVINSI", "INDONESIA", "TELP", "FAX", "WWW.", "HTTP", "GRATIS",
        "PROMO", "HEMAT", "DISKON", "BONUS", "ISI:", "KODE PROD"
    )

    private val CATEGORY_HINTS = mapOf(
        "Minuman" to listOf("KOPI", "COFFEE", "TEH", "TEA", "SUSU", "MILK", "JUS", "JUICE", "AIR", "MINERAL", "SIRUP", "SODA", "ES "),
        "Makanan Ringan" to listOf("SNACK", "KERIPIK", "CHIPS", "BISC", "BISKUIT", "WAFER", "COKLAT", "CHOCOLATE", "PERMEN", "KACANG", "ROTI"),
        "Sembako" to listOf("BERAS", "GULA", "MINYAK", "TEPUNG", "GARAM", "TELUR", "MIE", "NOODLE", "SAUS", "KECAP", "SAMBAL", "BUMBU"),
        "Perawatan & Kebersihan" to listOf("SABUN", "SHAMPOO", "SAMPO", "PASTA GIGI", "DETERJEN", "TISU", "TISSUE", "LOTION", "PARFUM")
    )

    fun createTempOcrPhotoUri(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "zokas_ocr_tmp")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "label_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    suspend fun recognizeProductNameCandidates(context: Context, uri: Uri): List<String> {
        return analyzeLabelSmartly(context, uri).candidates
    }

    suspend fun analyzeLabelSmartly(context: Context, uri: Uri): SmartLabelHint = withContext(Dispatchers.IO) {
        val rawLines = extractTextLinesWithMlKitIfAvailable(context, uri)
        if (rawLines.isEmpty()) {
            return@withContext SmartLabelHint(
                bestProductName = "",
                candidates = emptyList()
            )
        }

        val cleanedLines = rawLines
            .flatMap { it.split('\n') }
            .map { cleanRawLine(it) }
            .filter { it.isNotBlank() }
            .distinct()

        val scored = cleanedLines
            .filterNot { isNonNameNoise(it) }
            .map { line -> line to scoreProductNameLine(line) }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .map { (line, _) -> toSmartTitleCase(line) }
            .distinct()

        val bestName = scored.firstOrNull().orEmpty()
        val fullTextUpper = cleanedLines.joinToString(" ").uppercase(Locale.US)

        val suggestedPrice = extractPriceFromLines(cleanedLines)
        val suggestedCode = extractBarcodeOrSkuFromLines(cleanedLines)
        val suggestedCategory = inferCategory(fullTextUpper)

        SmartLabelHint(
            bestProductName = bestName,
            candidates = scored.take(6),
            suggestedPrice = suggestedPrice,
            suggestedBarcodeOrSku = suggestedCode,
            suggestedCategoryHint = suggestedCategory
        )
    }

    private suspend fun extractTextLinesWithMlKitIfAvailable(context: Context, uri: Uri): List<String> {
        return try {
            val inputImageClass = Class.forName("com.google.mlkit.vision.common.InputImage")
            val textRecClass = Class.forName("com.google.mlkit.vision.text.TextRecognition")
            val clientOptsClass = Class.forName("com.google.mlkit.vision.text.latin.TextRecognizerOptions")

            val fromFileMethod = inputImageClass.getMethod("fromFilePath", Context::class.java, Uri::class.java)
            val inputImage = fromFileMethod.invoke(null, context, uri) ?: return emptyList()

            val defaultOptsField = clientOptsClass.getField("DEFAULT_OPTIONS")
            val opts = defaultOptsField.get(null)
            val getClientMethod = textRecClass.getMethod("getClient", Class.forName("com.google.mlkit.vision.text.TextRecognizerOptionsInterface"))
            val recognizer = getClientMethod.invoke(null, opts) ?: return emptyList()

            val processMethod = recognizer.javaClass.getMethod("process", inputImageClass)
            val task = processMethod.invoke(recognizer, inputImage) ?: return emptyList()

            suspendCancellableCoroutine { cont ->
                val addOnSuccess = task.javaClass.getMethod(
                    "addOnSuccessListener",
                    Class.forName("com.google.android.gms.tasks.OnSuccessListener")
                )
                val addOnFailure = task.javaClass.getMethod(
                    "addOnFailureListener",
                    Class.forName("com.google.android.gms.tasks.OnFailureListener")
                )

                val successProxy = java.lang.reflect.Proxy.newProxyInstance(
                    ProductVoiceOcrHelper::class.java.classLoader,
                    arrayOf(Class.forName("com.google.android.gms.tasks.OnSuccessListener"))
                ) { _, _, args ->
                    val visionText = args?.firstOrNull()
                    val textBlockList = visionText?.javaClass?.getMethod("getTextBlocks")?.invoke(visionText) as? List<*>
                    val lines = mutableListOf<String>()
                    textBlockList?.forEach { block ->
                        val bLines = block?.javaClass?.getMethod("getLines")?.invoke(block) as? List<*>
                        bLines?.forEach { l ->
                            val txt = l?.javaClass?.getMethod("getText")?.invoke(l) as? String
                            if (!txt.isNullOrBlank()) lines.add(txt)
                        }
                    }
                    if (cont.isActive) cont.resume(lines)
                    null
                }

                val failureProxy = java.lang.reflect.Proxy.newProxyInstance(
                    ProductVoiceOcrHelper::class.java.classLoader,
                    arrayOf(Class.forName("com.google.android.gms.tasks.OnFailureListener"))
                ) { _, _, _ ->
                    if (cont.isActive) cont.resume(emptyList())
                    null
                }

                addOnSuccess.invoke(task, successProxy)
                addOnFailure.invoke(task, failureProxy)
            }
        } catch (_: Throwable) {
            // Fallback bila ML Kit belum terpasang: pastikan gambar dapat dibaca
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            }
            emptyList()
        }
    }

    private fun cleanRawLine(raw: String): String {
        return raw
            .replace(Regex("[\\u0000-\\u001F]"), " ")
            .replace(Regex("[•|*~_=<>]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isNonNameNoise(line: String): Boolean {
        val upper = line.uppercase(Locale.US)
        val lettersCount = line.count { it.isLetter() }
        val digitsCount = line.count { it.isDigit() }

        // Terlalu pendek atau tidak ada huruf sama sekali
        if (lettersCount < 2 || line.length < 2) return true

        // Dominan angka (barcode, tanggal kadaluarsa, harga)
        if (digitsCount > lettersCount * 2) return true

        // Barcode EAN/UPC/SKU murni
        if (Regex("^\\d{6,14}$").matches(upper.replace(" ", ""))) return true

        // Baris harga (Rp 15.000 / 15000)
        if (Regex("^(RP\\.?\\s*)?[0-9.,]+\\s*(RB|K)?$").matches(upper)) return true

        // Kata kunci administratif / komposisi / alamat
        if (NOISE_KEYWORDS.any { upper.contains(it) }) return true

        return false
    }

    private fun scoreProductNameLine(line: String): Int {
        val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        val wordCount = words.size
        val lettersCount = line.count { it.isLetter() }
        val digitsCount = line.count { it.isDigit() }

        var score = 0
        // Idealnya nama produk terdiri dari 1 sampai 5 kata
        score += when (wordCount) {
            in 2..4 -> 35
            1, 5 -> 25
            6 -> 12
            else -> 4
        }

        // Panjang karakter wajar untuk nama produk
        if (line.length in 3..42) score += 20

        // Rasio huruf tinggi dibanding angka
        if (lettersCount >= 3 && digitsCount == 0) score += 20
        else if (lettersCount > digitsCount * 2) score += 10

        // Hindari kalimat panjang seperti paragraf petunjuk
        if (wordCount >= 8) score -= 25

        return score
    }

    private fun extractPriceFromLines(lines: List<String>): Long? {
        for (line in lines) {
            val match = Regex("(?i)(?:rp\\.?|harga)\\s*([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]{4,8})").find(line)
            if (match != null) {
                val digits = match.groupValues[1].filter { it.isDigit() }
                val value = digits.toLongOrNull()
                if (value != null && value in 500L..100_000_000L) {
                    return value
                }
            }
        }
        return null
    }

    private fun extractBarcodeOrSkuFromLines(lines: List<String>): String {
        for (line in lines) {
            val digitsOnly = line.filter { it.isDigit() }
            if (digitsOnly.length in 8..14 && digitsOnly.length.toFloat() / line.length.coerceAtLeast(1) >= 0.75f) {
                return digitsOnly
            }
        }
        return ""
    }

    private fun inferCategory(upperAllText: String): String {
        for ((cat, keywords) in CATEGORY_HINTS) {
            if (keywords.any { upperAllText.contains(it) }) {
                return cat
            }
        }
        return ""
    }

    private fun toSmartTitleCase(text: String): String {
        return text.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                if (word.length <= 3 && word.all { it.isUpperCase() || !it.isLetter() }) {
                    word
                } else {
                    word.lowercase(Locale.getDefault()).replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                    }
                }
            }
    }
}


@androidx.compose.runtime.Composable
fun VoiceMicButton(
    promptLabel: String = "",
    currentValue: String = "",
    onResultText: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val speechLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            val matches = res.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                onResultText(spoken)
            }
        }
    }

    androidx.compose.material3.IconButton(
        onClick = {
            try {
                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
                    if (promptLabel.isNotBlank()) {
                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Ucapkan $promptLabel")
                    }
                }
                speechLauncher.launch(intent)
            } catch (_: Exception) {
                android.widget.Toast.makeText(
                    context,
                    "Fitur suara tidak tersedia di perangkat ini.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        },
        modifier = androidx.compose.ui.Modifier.size(34.dp)
    ) {
        androidx.compose.material3.Icon(
            imageVector = androidx.compose.material.icons.Icons.Default.Mic,
            contentDescription = if (currentValue.isBlank()) "Input Suara" else "Ulangi Input Suara",
            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            modifier = androidx.compose.ui.Modifier.size(18.dp)
        )
    }
}
