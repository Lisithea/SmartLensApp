package com.example.smartlens.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.smartlens.model.DocumentType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class OcrService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extrae texto de una imagen usando OCR
     */
    suspend fun extractText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                continuation.resume(visionText.text)
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
    }

    /**
     * Extrae texto de una imagen a partir de una URI
     */
    suspend fun extractTextFromUri(uri: Uri): String = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    /**
     * Detecta el tipo de documento basado en el texto extraído
     */
    fun detectDocumentType(text: String): DocumentType {
        val lowerText = text.lowercase()

        return when {
            // Detección de facturas
            lowerText.contains("factura") ||
                    lowerText.contains("invoice") ||
                    (lowerText.contains("total") && (lowerText.contains("iva") || lowerText.contains("impuesto"))) -> {
                DocumentType.INVOICE
            }

            // Detección de albaranes
            lowerText.contains("albarán") ||
                    lowerText.contains("delivery note") ||
                    lowerText.contains("nota de entrega") ||
                    (lowerText.contains("entrega") && lowerText.contains("mercancía")) -> {
                DocumentType.DELIVERY_NOTE
            }

            // Detección de etiquetas
            lowerText.contains("ref:") ||
                    lowerText.contains("lote:") ||
                    lowerText.contains("peso:") ||
                    (lowerText.contains("producto") && lowerText.contains("código")) -> {
                DocumentType.WAREHOUSE_LABEL
            }

            // Si no se puede determinar
            else -> DocumentType.UNKNOWN
        }
    }

    /**
     * Extrae bloques estructurados de texto
     */
    suspend fun extractStructuredText(uri: Uri): Map<String, String> = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val structuredData = extractDataFromBlocks(visionText)
                    continuation.resume(structuredData)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    /**
     * Procesa los bloques de texto para extraer información estructurada
     */
    private fun extractDataFromBlocks(visionText: Text): Map<String, String> {
        val result = mutableMapOf<String, String>()

        // Patrones comunes en documentos
        val patterns = mapOf(
            "fecha" to "(?i)fecha:?\\s*([0-9]{1,2}[/.-][0-9]{1,2}[/.-][0-9]{2,4})",
            "numero" to "(?i)(factura|albaran|nota)\\s*(?:n[°º]|num):?\\s*([A-Za-z0-9-/]+)",
            "total" to "(?i)total:?\\s*([0-9.,]+)",
            "cliente" to "(?i)cliente:?\\s*([^\\n]+)"
        )

        // Extraer datos según patrones
        patterns.forEach { (key, pattern) ->
            val regex = Regex(pattern)
            val matchResult = regex.find(visionText.text)
            matchResult?.let {
                val value = if (key == "numero") it.groupValues[2] else it.groupValues[1]
                result[key] = value.trim()
            }
        }

        return result
    }
}