package com.example.smartlens.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.example.smartlens.R
import com.example.smartlens.model.*
import com.example.smartlens.repository.DocumentRepository
import com.example.smartlens.service.DocumentShareService
import com.example.smartlens.service.ExcelExportService
import com.example.smartlens.service.GeminiService
import com.example.smartlens.service.OcrService
import com.example.smartlens.worker.DocumentProcessingWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@HiltViewModel
class DocumentViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val ocrService: OcrService,
    private val geminiService: GeminiService,
    private val excelExportService: ExcelExportService,
    private val shareService: DocumentShareService,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val TAG = "DocumentViewModel"

    // Estado del procesamiento
    private val _processingState = MutableStateFlow<DocumentProcessingState>(DocumentProcessingState.Idle)
    val processingState: StateFlow<DocumentProcessingState> = _processingState

    // Texto extraído por OCR
    private val _extractedText = MutableStateFlow("")
    val extractedText: StateFlow<String> = _extractedText

    // Documentos recientes
    private val _recentDocuments = MutableStateFlow<List<LogisticsDocument>>(emptyList())
    val recentDocuments: StateFlow<List<LogisticsDocument>> = _recentDocuments

    // Documento actual
    private val _currentDocument = MutableStateFlow<LogisticsDocument?>(null)
    val currentDocument: StateFlow<LogisticsDocument?> = _currentDocument

    // Estado de mensajes para el usuario
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage

    // ID de trabajo en progreso
    private var currentWorkId: UUID? = null

    // Imagen temporal
    private var tempImageUri: Uri? = null

    init {
        loadRecentDocuments()
    }

    /**
     * Carga documentos recientes
     */
    fun loadRecentDocuments() {
        viewModelScope.launch {
            repository.getAllDocuments()
                .catch { e ->
                    Log.e(TAG, "Error al cargar documentos: ${e.message}", e)
                    _processingState.value = DocumentProcessingState.Error("Error al cargar documentos: ${e.message}")
                    _userMessage.value = context.getString(R.string.loading_error)
                }
                .collect { documents ->
                    _recentDocuments.value = documents.sortedByDescending { it.timestamp }
                }
        }
    }

    /**
     * Guarda una imagen temporal en el directorio de la aplicación
     */
    fun saveTemporaryImage(imageUri: Uri): Uri {
        try {
            Log.d(TAG, "Guardando imagen temporal: $imageUri")
            val savedUri = repository.saveTempImage(imageUri)
            tempImageUri = savedUri
            Log.d(TAG, "Imagen temporal guardada en: $savedUri")
            return savedUri
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar imagen temporal: ${e.message}", e)
            throw e
        }
    }

    /**
     * Procesa una imagen para extraer texto
     */
    fun processImage(imageUri: Uri) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Iniciando procesamiento de imagen: $imageUri")
                _processingState.value = DocumentProcessingState.Capturing

                // Guardar imagen temporal si no se ha hecho antes
                tempImageUri = tempImageUri ?: repository.saveTempImage(imageUri)
                Log.d(TAG, "URI temporal: $tempImageUri")

                // Extraer texto con OCR
                _processingState.value = DocumentProcessingState.ExtractingText

                // Intentar preprocesar la imagen para mejorar el OCR
                var extractedText = ""
                try {
                    Log.d(TAG, "Preprocesando imagen para OCR...")
                    val processedBitmap = ocrService.preprocessImageForOcr(tempImageUri!!)
                    Log.d(TAG, "Extrayendo texto de bitmap preprocesado...")
                    extractedText = ocrService.extractText(processedBitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "Error en preprocesamiento, intentando extracción directa: ${e.message}")
                    extractedText = ocrService.extractTextFromUri(tempImageUri!!)
                }

                Log.d(TAG, "Texto extraído (${extractedText.length} caracteres): ${extractedText.take(50)}...")
                _extractedText.value = extractedText

                if (extractedText.isBlank()) {
                    throw IllegalStateException("No se pudo extraer texto de la imagen")
                }

                // Detectar tipo de documento
                val documentType = ocrService.detectDocumentType(extractedText)
                Log.d(TAG, "Tipo de documento detectado: $documentType")
                _processingState.value = DocumentProcessingState.ProcessingDocument(documentType)

            } catch (e: Exception) {
                Log.e(TAG, "Error en processImage: ${e.message}", e)
                _processingState.value = DocumentProcessingState.Error("Error al procesar imagen: ${e.message}")
                _userMessage.value = context.getString(R.string.ocr_error)
            }
        }
    }

    /**
     * Procesa el documento según el tipo seleccionado
     * En documentos grandes, usa un Worker para procesarlo en segundo plano
     */
    fun processDocument(documentType: DocumentType) {
        val text = _extractedText.value
        if (text.isEmpty() || tempImageUri == null) {
            Log.e(TAG, "No hay texto o imagen para procesar")
            _processingState.value = DocumentProcessingState.Error("No hay texto o imagen para procesar")
            _userMessage.value = context.getString(R.string.processing_error_generic)
            return
        }

        Log.d(TAG, "Procesando documento de tipo: $documentType con texto (${text.length} caracteres)")

        // Determinar si debemos usar un Worker (por ejemplo, si el texto es grande)
        val useWorker = text.length > 500

        if (useWorker) {
            Log.d(TAG, "Usando Worker para procesar documento (texto grande)")
            processDocumentWithWorker(documentType)
        } else {
            Log.d(TAG, "Procesando documento en el ViewModel")
            processDocumentInline(documentType)
        }
    }

    /**
     * Procesa un documento usando un Worker (en segundo plano)
     */
    private fun processDocumentWithWorker(documentType: DocumentType) {
        tempImageUri?.let { uri ->
            Log.d(TAG, "Configurando Worker para procesar documento: $documentType, URI: $uri")
            _processingState.value = DocumentProcessingState.ProcessingDocument(documentType)

            // Crear una solicitud de trabajo con restricciones
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Crear una solicitud de trabajo
            val workRequest = DocumentProcessingWorker.createRequest(
                uri.toString(),
                documentType,
                _extractedText.value
            )

            // Guardar el ID del trabajo
            currentWorkId = workRequest.id
            Log.d(TAG, "Work ID: $currentWorkId")

            // Enviar el trabajo al WorkManager
            workManager.enqueue(workRequest)

            // Observar el estado del trabajo
            workManager.getWorkInfoByIdLiveData(workRequest.id)
                .observeForever { workInfo ->
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            Log.d(TAG, "Worker completado con éxito")
                            // Obtener el ID del documento procesado
                            val documentId = workInfo.outputData.getString(DocumentProcessingWorker.KEY_RESULT_ID)
                            if (documentId != null) {
                                viewModelScope.launch {
                                    // Cargar el documento procesado
                                    Log.d(TAG, "Cargando documento procesado con ID: $documentId")
                                    val document = repository.getDocumentById(documentId)
                                    if (document != null) {
                                        _currentDocument.value = document
                                        _processingState.value = DocumentProcessingState.DocumentReady(document)
                                        _userMessage.value = context.getString(R.string.document_saved)

                                        // Recargar documentos recientes
                                        loadRecentDocuments()
                                    } else {
                                        Log.e(TAG, "Documento no encontrado con ID: $documentId")
                                        _processingState.value = DocumentProcessingState.Error("Documento procesado no encontrado")
                                        _userMessage.value = context.getString(R.string.document_not_found_error)
                                    }
                                }
                            } else {
                                Log.e(TAG, "DocumentID nulo en el resultado del Worker")
                                _processingState.value = DocumentProcessingState.Error("Error al procesar documento: ID nulo")
                                _userMessage.value = context.getString(R.string.processing_error_generic)
                            }
                        }
                        WorkInfo.State.FAILED -> {
                            Log.e(TAG, "Worker fallido")
                            _processingState.value = DocumentProcessingState.Error("Error al procesar documento en segundo plano")
                            _userMessage.value = context.getString(R.string.processing_error_generic)
                        }
                        WorkInfo.State.CANCELLED -> {
                            Log.d(TAG, "Worker cancelado")
                            _processingState.value = DocumentProcessingState.Idle
                        }
                        else -> {
                            // En progreso, bloqueado, etc.
                            Log.d(TAG, "Estado del Worker: ${workInfo.state}")
                        }
                    }
                }
        }
    }

    /**
     * Procesa un documento directamente en el ViewModel
     */
    private fun processDocumentInline(documentType: DocumentType) {
        val text = _extractedText.value
        val imageUri = tempImageUri

        viewModelScope.launch {
            try {
                Log.d(TAG, "Iniciando procesamiento de documento inline")
                _processingState.value = DocumentProcessingState.ProcessingDocument(documentType)

                // Crear un documento según el tipo seleccionado
                val document = withContext(Dispatchers.IO) {
                    when (documentType) {
                        DocumentType.INVOICE -> {
                            Log.d(TAG, "Procesando factura con Gemini...")
                            geminiService.processInvoice(text, imageUri)
                        }
                        DocumentType.DELIVERY_NOTE -> {
                            Log.d(TAG, "Procesando albarán con Gemini...")
                            geminiService.processDeliveryNote(text, imageUri)
                        }
                        DocumentType.WAREHOUSE_LABEL -> {
                            Log.d(TAG, "Procesando etiqueta con Gemini...")
                            geminiService.processWarehouseLabel(text, imageUri)
                        }
                        DocumentType.UNKNOWN -> {
                            Log.e(TAG, "Tipo de documento desconocido, reintentando como factura")
                            // Intentar procesar como factura si es desconocido
                            geminiService.processInvoice(text, imageUri)
                        }
                    }
                }

                Log.d(TAG, "Documento procesado, guardando en repositorio. ID: ${document.id}")
                // Guardar documento
                repository.saveDocument(document)

                // Actualizar estado
                _currentDocument.value = document
                _processingState.value = DocumentProcessingState.DocumentReady(document)
                _userMessage.value = context.getString(R.string.document_saved)

                // Recargar documentos recientes
                loadRecentDocuments()

            } catch (e: Exception) {
                Log.e(TAG, "Error al procesar documento inline: ${e.message}", e)
                _processingState.value = DocumentProcessingState.Error("Error al procesar documento: ${e.message}")

                // Determinar tipo de error para mensaje adecuado
                val errorMessage = when {
                    e.message?.contains("API Key") == true -> context.getString(R.string.api_key_missing_error)
                    e.message?.contains("conexión") == true -> context.getString(R.string.network_error)
                    else -> context.getString(R.string.processing_error_generic)
                }

                _userMessage.value = errorMessage
            }
        }
    }

    /**
     * Carga un documento por ID
     */
    fun loadDocumentById(id: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Cargando documento con ID: $id")
                val document = repository.getDocumentById(id)
                if (document != null) {
                    _currentDocument.value = document
                    _processingState.value = DocumentProcessingState.DocumentReady(document)
                    Log.d(TAG, "Documento cargado con éxito: ${document.getTypeDisplay()}")
                } else {
                    Log.e(TAG, "Documento no encontrado con ID: $id")
                    _processingState.value = DocumentProcessingState.Error("Documento no encontrado")
                    _userMessage.value = context.getString(R.string.document_not_found_error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar documento: ${e.message}", e)
                _processingState.value = DocumentProcessingState.Error("Error al cargar documento: ${e.message}")
                _userMessage.value = context.getString(R.string.loading_error)
            }
        }
    }

    /**
     * Elimina un documento
     */
    fun deleteDocument(id: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Eliminando documento con ID: $id")
                repository.deleteDocument(id)
                loadRecentDocuments()
                _userMessage.value = context.getString(R.string.document_deleted)

                if (_currentDocument.value?.id == id) {
                    _currentDocument.value = null
                    _processingState.value = DocumentProcessingState.Idle
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al eliminar documento: ${e.message}", e)
                _processingState.value = DocumentProcessingState.Error("Error al eliminar documento: ${e.message}")
                _userMessage.value = context.getString(R.string.error)
            }
        }
    }

    /**
     * Cancela el procesamiento en curso
     */
    fun cancelProcessing() {
        currentWorkId?.let { workId ->
            Log.d(TAG, "Cancelando procesamiento en curso, Work ID: $workId")
            workManager.cancelWorkById(workId)
            _processingState.value = DocumentProcessingState.Idle
            currentWorkId = null
        }
    }

    /**
     * Exporta documento a Excel
     */
    fun exportToExcel(document: LogisticsDocument): Uri {
        Log.d(TAG, "Exportando documento a Excel: ${document.id}")
        return excelExportService.exportToExcel(document)
    }

    /**
     * Comparte documento
     */
    fun shareDocument(document: LogisticsDocument) {
        Log.d(TAG, "Compartiendo documento: ${document.id}")
        shareService.shareDocument(document)
    }

    /**
     * Comparte como Excel
     */
    fun shareAsExcel(document: LogisticsDocument) {
        Log.d(TAG, "Compartiendo documento como Excel: ${document.id}")
        val excelUri = exportToExcel(document)
        shareService.shareAsExcel(excelUri)
    }

    /**
     * Genera código QR
     */
    fun generateQrCode(document: LogisticsDocument): Bitmap {
        Log.d(TAG, "Generando código QR para documento: ${document.id}")
        return shareService.generateQrCode(document)
    }

    /**
     * Reinicia el estado
     */
    fun resetState() {
        Log.d(TAG, "Reiniciando estado del ViewModel")
        _processingState.value = DocumentProcessingState.Idle
        _extractedText.value = ""
        tempImageUri = null
        currentWorkId = null
    }

    /**
     * Marca un mensaje de usuario como leído
     */
    fun messageShown() {
        _userMessage.value = null
    }

    /**
     * Fuerza la extracción de texto (para depuración)
     */
    fun forceTextExtraction() {
        tempImageUri?.let { uri ->
            viewModelScope.launch {
                try {
                    Log.d(TAG, "Forzando extracción de texto de: $uri")
                    val extractedText = ocrService.extractTextFromUri(uri)
                    _extractedText.value = extractedText
                    _userMessage.value = "Texto extraído con éxito (${extractedText.length} caracteres)"

                    // Actualizar estado
                    if (extractedText.isNotBlank()) {
                        val documentType = ocrService.detectDocumentType(extractedText)
                        _processingState.value = DocumentProcessingState.ProcessingDocument(documentType)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al forzar extracción de texto: ${e.message}", e)
                    _userMessage.value = "Error al extraer texto: ${e.message}"
                }
            }
        } ?: run {
            _userMessage.value = "No hay imagen para procesar"
        }
    }

    /**
     * Procesa una imagen y devuelve el texto extraído (para diagnóstico)
     */
    suspend fun processImageAndGetText(imageUri: Uri): String {
        return try {
            Log.d(TAG, "Procesando imagen para diagnóstico: $imageUri")

            // Guardar imagen temporal si no se ha hecho antes
            tempImageUri = tempImageUri ?: repository.saveTempImage(imageUri)

            // Intentar preprocesar la imagen para mejorar el OCR
            var extractedText = ""
            try {
                Log.d(TAG, "Preprocesando imagen para OCR diagnóstico...")
                val processedBitmap = ocrService.preprocessImageForOcr(tempImageUri!!)
                Log.d(TAG, "Extrayendo texto de bitmap preprocesado...")
                extractedText = ocrService.extractText(processedBitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Error en preprocesamiento, intentando extracción directa: ${e.message}")
                extractedText = ocrService.extractTextFromUri(tempImageUri!!)
            }

            Log.d(TAG, "Texto extraído para diagnóstico (${extractedText.length} caracteres)")

            // Actualizar estado
            _extractedText.value = extractedText

            if (extractedText.isNotBlank()) {
                val documentType = ocrService.detectDocumentType(extractedText)
                _processingState.value = DocumentProcessingState.ProcessingDocument(documentType)
            }

            extractedText
        } catch (e: Exception) {
            Log.e(TAG, "Error en processImageAndGetText: ${e.message}", e)
            throw e
        }
    }

    /**
     * Obtiene el tipo de documento detectado basado en el texto extraído actual
     */
    fun getDetectedDocumentType(): DocumentType {
        val text = _extractedText.value
        return if (text.isNotBlank()) {
            ocrService.detectDocumentType(text)
        } else {
            DocumentType.UNKNOWN
        }
    }

    override fun onCleared() {
        super.onCleared()

        // Cancelar cualquier trabajo en progreso cuando se elimine el ViewModel
        currentWorkId?.let { workId ->
            workManager.cancelWorkById(workId)
        }
    }
}