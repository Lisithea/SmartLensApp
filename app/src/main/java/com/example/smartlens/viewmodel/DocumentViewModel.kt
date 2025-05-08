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
    private fun loadRecentDocuments() {
        viewModelScope.launch {
            repository.getAllDocuments()
                .catch { e ->
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
            val savedUri = repository.saveTempImage(imageUri)
            tempImageUri = savedUri
            return savedUri
        } catch (e: Exception) {
            Log.e("DocumentViewModel", "Error al guardar imagen temporal: ${e.message}", e)
            throw e
        }
    }

    /**
     * Procesa una imagen para extraer texto
     */
    fun processImage(imageUri: Uri) {
        viewModelScope.launch {
            _processingState.value = DocumentProcessingState.Capturing

            try {
                // Guardar imagen temporal si no se ha hecho antes
                tempImageUri = tempImageUri ?: repository.saveTempImage(imageUri)

                // Extraer texto con OCR
                _processingState.value = DocumentProcessingState.ExtractingText
                val text = ocrService.extractTextFromUri(tempImageUri!!)
                _extractedText.value = text

                // Detectar tipo de documento
                val documentType = ocrService.detectDocumentType(text)
                _processingState.value = DocumentProcessingState.ProcessingDocument(documentType)

            } catch (e: Exception) {
                Log.e("DocumentViewModel", "Error en processImage: ${e.message}", e)
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
            _processingState.value = DocumentProcessingState.Error("No hay texto o imagen para procesar")
            _userMessage.value = context.getString(R.string.processing_error_generic)
            return
        }

        // Determinar si debemos usar un Worker (por ejemplo, si el texto es grande)
        val useWorker = text.length > 1000

        if (useWorker) {
            processDocumentWithWorker(documentType)
        } else {
            processDocumentInline(documentType)
        }
    }

    /**
     * Procesa un documento usando un Worker (en segundo plano)
     */
    private fun processDocumentWithWorker(documentType: DocumentType) {
        tempImageUri?.let { uri ->
            _processingState.value = DocumentProcessingState.ProcessingDocument(documentType)

            // Crear una solicitud de trabajo
            val workRequest = DocumentProcessingWorker.createRequest(
                uri.toString(),
                documentType
            )

            // Guardar el ID del trabajo
            currentWorkId = workRequest.id

            // Enviar el trabajo al WorkManager
            workManager.enqueue(workRequest)

            // Observar el estado del trabajo
            workManager.getWorkInfoByIdLiveData(workRequest.id)
                .observeForever { workInfo ->
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            // Obtener el ID del documento procesado
                            val documentId = workInfo.outputData.getString(DocumentProcessingWorker.KEY_RESULT_ID)
                            if (documentId != null) {
                                viewModelScope.launch {
                                    // Cargar el documento procesado
                                    val document = repository.getDocumentById(documentId)
                                    if (document != null) {
                                        _currentDocument.value = document
                                        _processingState.value = DocumentProcessingState.DocumentReady(document)
                                        _userMessage.value = context.getString(R.string.document_saved)

                                        // Recargar documentos recientes
                                        loadRecentDocuments()
                                    }
                                }
                            }
                        }
                        WorkInfo.State.FAILED -> {
                            _processingState.value = DocumentProcessingState.Error("Error al procesar documento")
                            _userMessage.value = context.getString(R.string.processing_error_generic)
                        }
                        WorkInfo.State.CANCELLED -> {
                            _processingState.value = DocumentProcessingState.Idle
                        }
                        else -> {
                            // En progreso, bloqueado, etc.
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
                _processingState.value = DocumentProcessingState.ProcessingDocument(documentType)

                val document = when (documentType) {
                    DocumentType.INVOICE -> geminiService.processInvoice(text, imageUri)
                    DocumentType.DELIVERY_NOTE -> geminiService.processDeliveryNote(text, imageUri)
                    DocumentType.WAREHOUSE_LABEL -> geminiService.processWarehouseLabel(text, imageUri)
                    DocumentType.UNKNOWN -> throw IllegalArgumentException("Tipo de documento desconocido")
                }

                // Guardar documento
                repository.saveDocument(document)

                // Actualizar estado
                _currentDocument.value = document
                _processingState.value = DocumentProcessingState.DocumentReady(document)
                _userMessage.value = context.getString(R.string.document_saved)

                // Recargar documentos recientes
                loadRecentDocuments()

            } catch (e: Exception) {
                Log.e("DocumentViewModel", "Error al procesar documento: ${e.message}", e)
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
                val document = repository.getDocumentById(id)
                if (document != null) {
                    _currentDocument.value = document
                    _processingState.value = DocumentProcessingState.DocumentReady(document)
                } else {
                    _processingState.value = DocumentProcessingState.Error("Documento no encontrado")
                    _userMessage.value = context.getString(R.string.document_not_found_error)
                }
            } catch (e: Exception) {
                Log.e("DocumentViewModel", "Error al cargar documento: ${e.message}", e)
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
                repository.deleteDocument(id)
                loadRecentDocuments()
                _userMessage.value = context.getString(R.string.document_deleted)

                if (_currentDocument.value?.id == id) {
                    _currentDocument.value = null
                    _processingState.value = DocumentProcessingState.Idle
                }
            } catch (e: Exception) {
                Log.e("DocumentViewModel", "Error al eliminar documento: ${e.message}", e)
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
            workManager.cancelWorkById(workId)
            _processingState.value = DocumentProcessingState.Idle
            currentWorkId = null
        }
    }

    /**
     * Exporta documento a Excel
     */
    fun exportToExcel(document: LogisticsDocument): Uri {
        return excelExportService.exportToExcel(document)
    }

    /**
     * Comparte documento
     */
    fun shareDocument(document: LogisticsDocument) {
        shareService.shareDocument(document)
    }

    /**
     * Comparte como Excel
     */
    fun shareAsExcel(document: LogisticsDocument) {
        val excelUri = exportToExcel(document)
        shareService.shareAsExcel(excelUri)
    }

    /**
     * Genera código QR
     */
    fun generateQrCode(document: LogisticsDocument): Bitmap {
        return shareService.generateQrCode(document)
    }

    /**
     * Reinicia el estado
     */
    fun resetState() {
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

    override fun onCleared() {
        super.onCleared()

        // Cancelar cualquier trabajo en progreso cuando se elimine el ViewModel
        currentWorkId?.let { workId ->
            workManager.cancelWorkById(workId)
        }
    }
}