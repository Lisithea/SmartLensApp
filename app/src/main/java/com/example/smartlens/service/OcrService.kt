package com.example.smartlens.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.smartlens.model.DocumentType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class OcrService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val TAG = "OcrService"

    /**
     * Extrae texto de una imagen usando OCR
     */
    suspend fun extractText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        try {
            Log.d(TAG, "Iniciando extracción de texto de bitmap")
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    Log.d(TAG, "Texto extraído con éxito: ${visionText.text.take(50)}...")
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error al extraer texto: ${e.message}", e)
                    continuation.resumeWithException(e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al procesar imagen para OCR: ${e.message}", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * Extrae texto de una imagen a partir de una URI
     */
    suspend fun extractTextFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando extracción de texto de URI: $uri")

            // Primero verificamos si podemos acceder a la imagen
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("No se pudo abrir la imagen")

            // Cerramos el stream después de verificar
            inputStream.close()

            // Ahora creamos la imagen de entrada para ML Kit
            val image = InputImage.fromFilePath(context, uri)

            return@withContext suspendCancellableCoroutine<String> { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val extractedText = visionText.text
                        Log.d(TAG, "Texto extraído con éxito (${extractedText.length} caracteres): ${extractedText.take(50)}...")
                        continuation.resume(extractedText)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error durante el procesamiento de OCR: ${e.message}", e)
                        continuation.resumeWithException(e)
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al extraer texto de URI: ${e.message}", e)

            // Intentamos un enfoque alternativo si falló el método principal
            try {
                Log.d(TAG, "Intentando enfoque alternativo con bitmap")
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()

                    if (bitmap != null) {
                        return@withContext extractText(bitmap)
                    } else {
                        throw IllegalArgumentException("No se pudo decodificar la imagen como bitmap")
                    }
                } else {
                    throw IllegalArgumentException("No se pudo abrir la imagen")
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Falló el enfoque alternativo: ${e2.message}", e2)
                throw e2
            }
        }
    }

    /**
     * Detecta el tipo de documento basado en el texto extraído
     */
    fun detectDocumentType(text: String): DocumentType {
        Log.d(TAG, "Detectando tipo de documento a partir del texto")
        val lowerText = text.lowercase()

        return when {
            // Detección de facturas
            lowerText.contains("factura") ||
                    lowerText.contains("invoice") ||
                    lowerText.contains("recibo") ||
                    lowerText.contains("receipt") ||
                    (lowerText.contains("total") && (lowerText.contains("iva") || lowerText.contains("impuesto"))) -> {
                Log.d(TAG, "Documento detectado como: FACTURA")
                DocumentType.INVOICE
            }

            // Detección de albaranes
            lowerText.contains("albarán") ||
                    lowerText.contains("delivery note") ||
                    lowerText.contains("nota de entrega") ||
                    (lowerText.contains("entrega") && lowerText.contains("mercancía")) -> {
                Log.d(TAG, "Documento detectado como: ALBARÁN")
                DocumentType.DELIVERY_NOTE
            }

            // Detección de etiquetas
            lowerText.contains("ref:") ||
                    lowerText.contains("lote:") ||
                    lowerText.contains("peso:") ||
                    (lowerText.contains("producto") && lowerText.contains("código")) -> {
                Log.d(TAG, "Documento detectado como: ETIQUETA")
                DocumentType.WAREHOUSE_LABEL
            }

            // Si no se puede determinar
            else -> {
                Log.d(TAG, "No se pudo determinar el tipo de documento, marcando como DESCONOCIDO")
                DocumentType.UNKNOWN
            }
        }
    }

    /**
     * Extrae bloques estructurados de texto
     */
    suspend fun extractStructuredText(uri: Uri): Map<String, String> = suspendCancellableCoroutine { continuation ->
        try {
            Log.d(TAG, "Iniciando extracción estructurada de texto de URI: $uri")
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val structuredData = extractDataFromBlocks(visionText)
                    Log.d(TAG, "Datos estructurados extraídos: ${structuredData.keys}")
                    continuation.resume(structuredData)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error al extraer datos estructurados: ${e.message}", e)
                    continuation.resumeWithException(e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al extraer datos estructurados: ${e.message}", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * Procesa los bloques de texto para extraer información estructurada
     */
    private fun extractDataFromBlocks(visionText: Text): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val fullText = visionText.text

        // Patrones comunes en documentos
        val patterns = mapOf(
            "fecha" to "(?i)fecha:?\\s*([0-9]{1,2}[/.-][0-9]{1,2}[/.-][0-9]{2,4})",
            "numero" to "(?i)(factura|albaran|nota|invoice|receipt)\\s*(?:n[°º]|num|number):?\\s*([A-Za-z0-9-/]+)",
            "total" to "(?i)total:?\\s*([0-9.,€$]+)",
            "cliente" to "(?i)cliente:?\\s*([^\\n]+)",
            "proveedor" to "(?i)proveedor:?\\s*([^\\n]+)",
            "nif" to "(?i)(nif|cif|tax\\s*id):?\\s*([A-Z0-9-]+)",
            "direccion" to "(?i)direccion:?\\s*([^\\n]+)",
            "producto" to "(?i)producto:?\\s*([^\\n]+)",
            "cantidad" to "(?i)cantidad:?\\s*([0-9.,]+)",
            "precio" to "(?i)precio:?\\s*([0-9.,€$]+)"
        )

        // Extraer datos según patrones
        patterns.forEach { (key, pattern) ->
            val regex = Regex(pattern)
            val matchResult = regex.find(fullText)
            matchResult?.let {
                val value = if (key == "numero") it.groupValues[2] else it.groupValues[1]
                result[key] = value.trim()
            }
        }

        // Analizar estructura de líneas para mejorar la extracción
        val lines = fullText.split("\n")

        // Procesamiento avanzado por líneas
        lines.forEach { line ->
            // Buscar pares clave-valor en formato "Clave: Valor"
            val keyValuePattern = Regex("([A-Za-zÀ-ú\\s]+):(.+)")
            val match = keyValuePattern.find(line)

            if (match != null) {
                val key = match.groupValues[1].trim().lowercase()
                val value = match.groupValues[2].trim()

                // Solo añadir si es un campo reconocido y no está ya en el resultado
                when {
                    key.contains("fecha") && !result.containsKey("fecha") -> result["fecha"] = value
                    key.contains("cliente") && !result.containsKey("cliente") -> result["cliente"] = value
                    key.contains("proveedor") && !result.containsKey("proveedor") -> result["proveedor"] = value
                    key.contains("numero") && !result.containsKey("numero") -> result["numero"] = value
                    key.contains("total") && !result.containsKey("total") -> result["total"] = value
                    key.contains("nif") || key.contains("cif") -> result["nif"] = value
                    key.contains("producto") -> result["producto"] = value
                }
            }
        }

        return result
    }

    /**
     * Procesa una imagen para preprocesamiento antes de OCR
     * (mejora resultados en imágenes difíciles)
     */
    suspend fun preprocessImageForOcr(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("No se pudo abrir la imagen")

            // Decodificar la imagen
            val options = BitmapFactory.Options().apply {
                // Calcular dimensiones sin cargar
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Calcular factor de escala para reducir memoria
            val maxDimension = 2048 // Tamaño máximo que mantendrá buena calidad para OCR
            var sampleSize = 1

            while (options.outWidth / sampleSize > maxDimension ||
                options.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            // Ahora cargar la imagen con el factor de escala
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }

            val newInputStream = context.contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("No se pudo abrir la imagen para procesamiento")

            val bitmap = BitmapFactory.decodeStream(newInputStream, null, loadOptions)
            newInputStream.close()

            // Aquí se podrían aplicar filtros adicionales para mejorar la calidad de OCR
            // como binarización, aumento de contraste, etc.

            return@withContext bitmap ?: throw IllegalStateException("No se pudo procesar la imagen")
        } catch (e: Exception) {
            Log.e(TAG, "Error en preprocesamiento de imagen: ${e.message}", e)
            throw e
        }
    }
}