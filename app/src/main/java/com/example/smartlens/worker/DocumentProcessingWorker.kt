package com.example.smartlens.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.smartlens.model.DocumentType
import com.example.smartlens.repository.DocumentRepository
import com.example.smartlens.service.GeminiService
import com.example.smartlens.service.OcrService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker para procesar documentos en segundo plano
 * Esto evita bloquear la UI durante el procesamiento de documentos grandes
 */
@HiltWorker
class DocumentProcessingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: DocumentRepository,
    private val ocrService: OcrService,
    private val geminiService: GeminiService
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DocumentProcessingWorker"
        const val KEY_IMAGE_URI = "KEY_IMAGE_URI"
        const val KEY_DOCUMENT_TYPE = "KEY_DOCUMENT_TYPE"
        const val KEY_EXTRACTED_TEXT = "KEY_EXTRACTED_TEXT"
        const val KEY_RESULT_ID = "KEY_RESULT_ID"
        private const val MIN_BACKOFF_MILLIS = 10000L // 10 segundos como valor de retroceso mínimo

        /**
         * Crea una solicitud para procesar un documento
         */
        fun createRequest(
            imageUri: String,
            documentType: DocumentType,
            extractedText: String = ""
        ): OneTimeWorkRequest {
            val inputData = Data.Builder()
                .putString(KEY_IMAGE_URI, imageUri)
                .putString(KEY_DOCUMENT_TYPE, documentType.name)
                .putString(KEY_EXTRACTED_TEXT, extractedText)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return OneTimeWorkRequestBuilder<DocumentProcessingWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Obtener parámetros de entrada
            val imageUriString = inputData.getString(KEY_IMAGE_URI)
                ?: return@withContext Result.failure()

            val documentTypeString = inputData.getString(KEY_DOCUMENT_TYPE)
                ?: return@withContext Result.failure()

            // Obtener texto extraído si está disponible
            var preExtractedText = inputData.getString(KEY_EXTRACTED_TEXT) ?: ""

            val imageUri = Uri.parse(imageUriString)
            val documentType = DocumentType.valueOf(documentTypeString)

            Log.d(TAG, "Iniciando procesamiento de documento en Worker")
            Log.d(TAG, "Tipo: $documentType, URI: $imageUri")

            // Verificar si ya tenemos texto extraído
            var extractedText = preExtractedText

            // Si no tenemos texto, extraerlo con OCR
            if (extractedText.isBlank()) {
                Log.d(TAG, "No hay texto pre-extraído, realizando OCR...")
                try {
                    // Intentar preprocesar la imagen para mejorar OCR
                    val processedBitmap = ocrService.preprocessImageForOcr(imageUri)
                    extractedText = ocrService.extractText(processedBitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "Error en procesamiento alternativo de OCR: ${e.message}", e)
                    // Si falla, intentar directamente sin preprocesamiento
                    extractedText = ocrService.extractTextFromUri(imageUri)
                }

                Log.d(TAG, "Texto extraído: ${extractedText.take(50)}...")
            } else {
                Log.d(TAG, "Usando texto pre-extraído (${preExtractedText.length} caracteres)")
            }

            if (extractedText.isBlank()) {
                Log.e(TAG, "No se pudo extraer texto de la imagen")
                return@withContext Result.failure(
                    Data.Builder()
                        .putString("error", "No se pudo extraer texto de la imagen")
                        .build()
                )
            }

            // Procesar documento según su tipo
            Log.d(TAG, "Procesando documento con Gemini...")
            val document = when (documentType) {
                DocumentType.INVOICE -> geminiService.processInvoice(extractedText, imageUri)
                DocumentType.DELIVERY_NOTE -> geminiService.processDeliveryNote(extractedText, imageUri)
                DocumentType.WAREHOUSE_LABEL -> geminiService.processWarehouseLabel(extractedText, imageUri)
                else -> {
                    // Si es desconocido, intentar procesar como factura
                    Log.w(TAG, "Tipo de documento desconocido, procesando como factura")
                    geminiService.processInvoice(extractedText, imageUri)
                }
            }

            // Guardar el documento
            Log.d(TAG, "Guardando documento en repositorio. ID: ${document.id}")
            repository.saveDocument(document)

            // Devolver el ID del documento procesado
            val outputData = Data.Builder()
                .putString(KEY_RESULT_ID, document.id)
                .build()

            Log.d(TAG, "Worker completado con éxito")
            Result.success(outputData)

        } catch (e: Exception) {
            // Registrar error y devolver fallo
            Log.e(TAG, "Error en el Worker: ${e.message}", e)
            Result.failure(
                Data.Builder()
                    .putString("error", e.message ?: "Error desconocido")
                    .build()
            )
        }
    }
}