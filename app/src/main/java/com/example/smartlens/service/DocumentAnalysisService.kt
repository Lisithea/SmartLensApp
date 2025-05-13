package com.example.smartlens.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.smartlens.model.DocumentType
import com.example.smartlens.model.LogisticsDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Servicio avanzado para el análisis y clasificación de documentos
 * Integra OCR y clasificación inteligente para extraer información estructurada
 */
@Singleton
class DocumentAnalysisService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ocrService: OcrService,
    private val geminiService: GeminiService
) {
    private val TAG = "DocumentAnalysisService"
    private val documentClassifier = DocumentClassifier()

    /**
     * Analiza un documento para detectar su tipo y extraer campos clave
     */
    suspend fun analyzeDocument(imageUri: Uri): DocumentAnalysisResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Iniciando análisis de documento: $imageUri")
        val startTime = System.currentTimeMillis()

        try {
            // Extraer texto con OCR
            val ocrStartTime = System.currentTimeMillis()
            val extractedText = ocrService.extractTextFromUri(imageUri)
            val ocrEndTime = System.currentTimeMillis()
            val ocrTimeMs = ocrEndTime - ocrStartTime

            if (extractedText.isBlank()) {
                Log.e(TAG, "No se pudo extraer texto de la imagen")
                throw IllegalStateException("No se pudo extraer texto de la imagen")
            }

            Log.d(TAG, "Texto extraído (${extractedText.length} caracteres): ${extractedText.take(100)}...")

            // Detectar tipo de documento
            val classificationStartTime = System.currentTimeMillis()
            val (documentType, specificType) = documentClassifier.detectDocumentType(extractedText)
            val classificationEndTime = System.currentTimeMillis()
            val classificationTimeMs = classificationEndTime - classificationStartTime

            Log.d(TAG, "Tipo de documento detectado: $documentType (específico: $specificType)")

            // Extraer campos estructurados
            val structuredData = documentClassifier.extractFields(extractedText, specificType)
            Log.d(TAG, "Campos extraídos: ${structuredData.keys}")

            // Obtener información de la imagen
            val imageInfo = getImageInfo(imageUri)

            val endTime = System.currentTimeMillis()
            val totalProcessingTime = endTime - startTime

            return@withContext DocumentAnalysisResult(
                documentType = documentType,
                specificType = specificType,
                extractedText = extractedText,
                structuredData = structuredData,
                imageWidth = imageInfo.width,
                imageHeight = imageInfo.height,
                imageSize = imageInfo.size,
                analysisTimeMs = totalProcessingTime,
                ocrTimeMs = ocrTimeMs,
                classificationTimeMs = classificationTimeMs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error en análisis de documento: ${e.message}", e)
            throw e
        }
    }

    /**
     * Realiza un análisis y procesamiento completo del documento
     */
    suspend fun analyzeAndProcessDocument(imageUri: Uri): Pair<LogisticsDocument?, DocumentAnalysisResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Iniciando análisis y procesamiento completo: $imageUri")

        try {
            // Primero analizamos el documento
            val analysisResult = analyzeDocument(imageUri)

            // Si el tipo de documento es desconocido, no podemos proceder con el procesamiento
            if (analysisResult.documentType == DocumentType.UNKNOWN) {
                Log.w(TAG, "Tipo de documento desconocido, no se puede procesar")
                return@withContext Pair(null, analysisResult)
            }

            // Procesamos el documento según el tipo específico detectado
            val document = when (analysisResult.documentType) {
                DocumentType.INVOICE ->
                    geminiService.processInvoice(analysisResult.extractedText, imageUri)
                DocumentType.DELIVERY_NOTE ->
                    geminiService.processDeliveryNote(analysisResult.extractedText, imageUri)
                DocumentType.WAREHOUSE_LABEL ->
                    geminiService.processWarehouseLabel(analysisResult.extractedText, imageUri)
                else ->
                    // Por defecto intentamos como factura
                    geminiService.processInvoice(analysisResult.extractedText, imageUri)
            }

            Log.d(TAG, "Documento procesado correctamente como: ${analysisResult.specificType}")
            return@withContext Pair(document, analysisResult)

        } catch (e: Exception) {
            Log.e(TAG, "Error en análisis y procesamiento: ${e.message}", e)
            throw e
        }
    }

    /**
     * Realiza un diagnóstico detallado de una imagen
     */
    suspend fun diagnosticAnalysis(imageUri: Uri): DiagnosticResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Iniciando diagnóstico para: $imageUri")

        try {
            // Extraer texto con OCR
            val ocrStartTime = System.currentTimeMillis()
            val extractedText = ocrService.extractTextFromUri(imageUri)
            val ocrEndTime = System.currentTimeMillis()
            val ocrTimeMs = ocrEndTime - ocrStartTime

            val ocrSuccess = extractedText.isNotBlank()

            // Intentar clasificar el documento
            val classificationStartTime = System.currentTimeMillis()
            val (documentType, specificType) = if (ocrSuccess) {
                documentClassifier.detectDocumentType(extractedText)
            } else {
                Pair(DocumentType.UNKNOWN, "Desconocido")
            }
            val classificationEndTime = System.currentTimeMillis()
            val classificationTimeMs = classificationEndTime - classificationStartTime

            val classificationSuccess = documentType != DocumentType.UNKNOWN

            // Obtener información de la imagen
            val imageInfo = getImageInfo(imageUri)

            return@withContext DiagnosticResult(
                ocrSuccess = ocrSuccess,
                extractedText = extractedText,
                classificationSuccess = classificationSuccess,
                detectedType = specificType,
                imageWidth = imageInfo.width,
                imageHeight = imageInfo.height,
                imageSize = imageInfo.size,
                ocrTimeMs = ocrTimeMs,
                classificationTimeMs = classificationTimeMs,
                textLength = extractedText.length
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error en diagnóstico: ${e.message}", e)
            throw e
        }
    }

    /**
     * Genera un prompt personalizado para Gemini basado en el resultado del análisis
     */
    fun generateGeminiPrompt(analysisResult: DocumentAnalysisResult): String {
        return documentClassifier.generatePromptForAI(analysisResult.extractedText, analysisResult.specificType)
    }

    /**
     * Obtiene información básica sobre una imagen
     */
    private suspend fun getImageInfo(imageUri: Uri): ImageInfo = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val width = options.outWidth
            val height = options.outHeight

            // Calcular tamaño aproximado del archivo
            val size = context.contentResolver.openInputStream(imageUri)?.use { stream ->
                stream.available().toLong()
            } ?: 0L

            return@withContext ImageInfo(width, height, size)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo información de imagen: ${e.message}", e)
            return@withContext ImageInfo(0, 0, 0L)
        }
    }

    /**
     * Clase con información básica de una imagen
     */
    data class ImageInfo(
        val width: Int,
        val height: Int,
        val size: Long
    )

    /**
     * Clase que contiene el resultado del análisis de documento
     */
    data class DocumentAnalysisResult(
        val documentType: DocumentType,
        val specificType: String,
        val extractedText: String,
        val structuredData: Map<String, String>,
        val imageWidth: Int,
        val imageHeight: Int,
        val imageSize: Long,
        val analysisTimeMs: Long,
        val ocrTimeMs: Long,
        val classificationTimeMs: Long
    )

    /**
     * Clase que contiene el resultado de un diagnóstico
     */
    data class DiagnosticResult(
        val ocrSuccess: Boolean,
        val extractedText: String,
        val classificationSuccess: Boolean,
        val detectedType: String,
        val imageWidth: Int,
        val imageHeight: Int,
        val imageSize: Long,
        val ocrTimeMs: Long,
        val classificationTimeMs: Long,
        val textLength: Int
    )
}