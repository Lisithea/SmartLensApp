package com.example.smartlens.worker

import android.content.Context
import android.net.Uri
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
        const val KEY_RESULT_ID = "KEY_RESULT_ID"
        private const val MIN_BACKOFF_MILLIS = 10000L // 10 segundos como valor de retroceso mínimo

        /**
         * Crea una solicitud para procesar un documento
         */
        fun createRequest(imageUri: String, documentType: DocumentType): OneTimeWorkRequest {
            val inputData = Data.Builder()
                .putString(KEY_IMAGE_URI, imageUri)
                .putString(KEY_DOCUMENT_TYPE, documentType.name)
                .build()

            return OneTimeWorkRequestBuilder<DocumentProcessingWorker>()
                .setInputData(inputData)
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

            val imageUri = Uri.parse(imageUriString)
            val documentType = DocumentType.valueOf(documentTypeString)

            // Extraer texto con OCR
            val extractedText = ocrService.extractTextFromUri(imageUri)
            if (extractedText.isBlank()) {
                return@withContext Result.failure()
            }

            // Procesar documento según su tipo
            val document = when (documentType) {
                DocumentType.INVOICE -> geminiService.processInvoice(extractedText, imageUri)
                DocumentType.DELIVERY_NOTE -> geminiService.processDeliveryNote(extractedText, imageUri)
                DocumentType.WAREHOUSE_LABEL -> geminiService.processWarehouseLabel(extractedText, imageUri)
                else -> throw IllegalArgumentException("Tipo de documento desconocido: $documentType")
            }

            // Guardar el documento
            repository.saveDocument(document)

            // Devolver el ID del documento procesado
            val outputData = Data.Builder()
                .putString(KEY_RESULT_ID, document.id)
                .build()

            Result.success(outputData)

        } catch (e: Exception) {
            // Registrar error y devolver fallo
            e.printStackTrace()
            Result.failure()
        }
    }
}