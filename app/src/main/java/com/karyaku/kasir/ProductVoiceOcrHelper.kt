package com.karyaku.kasir

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Locale

object ProductVoiceOcrHelper {

    fun createSpeechIntent(
        promptText: String = "Ucapkan teks dalam Bahasa Indonesia...",
        languageTag: String = "id-ID"
    ): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PROMPT, promptText)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
    }

    fun createTempOcrPhotoUri(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "ocr_captures").apply { mkdirs() }
        val file = File(dir, "ocr_label_${System.currentTimeMillis()}.jpg")
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        return file to uri
    }

    suspend fun recognizeProductNameCandidates(context: Context, imageUri: Uri): List<String> {
        return try {
            val inputImage = InputImage.fromFilePath(context, imageUri)
            extractCandidatesFromInputImage(inputImage)
        } catch (_: Exception) {
            try {
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream) ?: return emptyList()
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    extractCandidatesFromInputImage(inputImage)
                } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun extractCandidatesFromInputImage(inputImage: InputImage): List<String> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val visionText = recognizer.process(inputImage).await()
            val rawCandidates = mutableListOf<String>()

            for (block in visionText.textBlocks) {
                val blockClean = cleanProductLabelText(block.text)
                if (blockClean.length >= 2) {
                    rawCandidates.add(blockClean)
                }
                for (line in block.lines) {
                    val lineClean = cleanProductLabelText(line.text)
                    if (lineClean.length >= 2) {
                        rawCandidates.add(lineClean)
                    }
                }
            }

            rawCandidates
                .distinctBy { it.lowercase(Locale.ROOT) }
                .sortedWith(
                    compareByDescending<String> { scoreProductNameCandidate(it) }
                        .thenByDescending { it.length }
                )
                .take(6)
        } finally {
            recognizer.close()
        }
    }

    fun cleanProductLabelText(raw: String): String {
        return raw
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s\\-./()&+]"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    private fun scoreProductNameCandidate(text: String): Int {
        var score = 0
        val lettersAndDigits = text.count { it.isLetterOrDigit() }
        val letters = text.count { it.isLetter() }
        if (lettersAndDigits in 3..45) score += 10
        if (letters >= 2) score += 8
        if (letters == 0 && text.any { it.isDigit() }) score -= 12
        val wordCount = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        if (wordCount in 1..6) score += 6
        return score
    }
}

@Composable
fun VoiceMicButton(
    promptLabel: String,
    currentValue: String,
    appendMode: Boolean = false,
    onResultText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                val finalVal = if (appendMode && currentValue.isNotBlank()) {
                    "$currentValue $spoken".trim()
                } else {
                    spoken
                }
                onResultText(finalVal)
            }
        }
    }

    IconButton(
        onClick = {
            try {
                val intent = ProductVoiceOcrHelper.createSpeechIntent(
                    promptText = "Ucapkan $promptLabel (Bahasa Indonesia)"
                )
                speechLauncher.launch(intent)
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    "Fitur Voice-to-Text belum tersedia atau layanan suara Google belum aktif di HP ini.",
                    Toast.LENGTH_LONG
                ).show()
            }
        },
        modifier = modifier.size(36.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Voice to Text $promptLabel",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}
