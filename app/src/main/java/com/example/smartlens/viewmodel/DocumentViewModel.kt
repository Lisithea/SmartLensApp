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
import com.example.smartlens.service.DocumentProcessingManager
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
import kotlinx.coroutines.flow.update
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
    private val processingManager: DocumentProcessingManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val TAG = "DocumentViewModel"

    // Estado del procesamiento - delegado al ProcessingManager
    val processingState: StateFlow<DocumentProcessingState> = processingManager.processingState

    // Texto extraído por OCR - delegado al ProcessingManager
    val extractedText: StateFlow<String> = processingManager.extractedText

    // Datos estructurados extraídos
    val structuredData: StateFlow<Map<String, String>> = processingManager.rawOcrResult

    // Documentos recientes
    private val _recentDocuments = MutableStateFlow<List<LogisticsDocument>>(emptyList())
    val recentDocuments: StateFlow<List<LogisticsDocument>> = _recentDocuments

    // Documento actual
    private val _currentDocument = MutableStateFlow<LogisticsDocument?>(null)
    val currentDocument: StateFlow<LogisticsDocument?> = _currentDocument

    // Estado de mensajes para el usuario
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage

    // Nombre personalizado para exportación
    private val _customFileName = MutableStateFlow("")
    val customFileName: StateFlow<String> = _customFileName

    // ID de trabajo en progreso
    private var currentWorkId: UUID? = null

    // Imagen temporal
    private var tempImageUri: Uri? = null

    init {
        loadRecentDocuments()

        // Crear carpetas de la aplicación
        viewModelScope.launch {
            processingManager.createAppFolders()
        }
    }

    /**
     * Establece un nombre personalizado para exportación
     */
    fun setCustomFileName(name: String) {
        _customFileName.value = name
        processingManager.setCustomExportName(name)
    }

    /**
     * Carga documentos recientes
     */
    fun loadRecentDocuments() {
        viewModelScope.launch {
            repository.getAllDocuments()
                .catch { e ->
                    Log.e(TAG, "Error al cargar documentos: ${e.message}", e)
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
    suspend fun saveTemporaryImage(imageUri: Uri): Uri {
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
    suspend fun processImage(imageUri: Uri) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Iniciando procesamiento de imagen: $imageUri")

                // Delegar al ProcessingManager
                val extractedText = processingManager.processImage(imageUri)

                if (extractedText.isBlank()) {
                    throw IllegalStateException("No se pudo extraer texto de la imagen")
                }

                // Detectar tipo de documento
                val documentType = processingManager.detectDocumentType(extractedText)
                Log.d(TAG, "Tipo de documento detectado: $documentType")

            } catch (e: Exception) {
                Log.e(TAG, "Error en processImage: ${e.message}", e)
                _userMessage.value = context.getString(R.string.ocr_error)
            }
        }
    }

    /**
     * Procesa el documento según el tipo seleccionado
     */
    fun processDocument(documentType: DocumentType) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Procesando documento de tipo: $documentType")

                // Procesar documento con el manager
                val document = processingManager.processDocument(documentType)

                // Guardar documento
                repository.saveDocument(document)

                // Actualizar estado
                _currentDocument.value = document

                // Mensaje de éxito
                _userMessage.value = context.getString(R.string.document_saved)

                // Recargar documentos recientes
                loadRecentDocuments()

            } catch (e: Exception) {
                Log.e(TAG, "Error al procesar documento: ${e.message}", e)

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
                    // No actualizamos processingState aquí para mantener consistencia
                    Log.d(TAG, "Documento cargado con éxito: ${document.getTypeDisplay()}")
                } else {
                    Log.e(TAG, "Documento no encontrado con ID: $id")
                    _userMessage.value = context.getString(R.string.document_not_found_error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar documento: ${e.message}", e)
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
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al eliminar documento: ${e.message}", e)
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
            currentWorkId = null
        }

        // Reiniciar el estado del processingManager
        processingManager.reset()
    }

    /**
     * Exporta documento a Excel
     */
    suspend fun exportToExcel(document: LogisticsDocument): Uri {
        Log.d(TAG, "Exportando documento a Excel: ${document.id}")
        return processingManager.generateExcelFile(document)
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
        viewModelScope.launch {
            Log.d(TAG, "Compartiendo documento como Excel: ${document.id}")
            try {
                val excelUri = exportToExcel(document)
                shareService.shareAsExcel(excelUri)
            } catch (e: Exception) {
                Log.e(TAG, "Error al compartir Excel: ${e.message}", e)
                _userMessage.value = "Error al compartir Excel: ${e.message}"
            }
        }
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
        processingManager.reset()
        _customFileName.value = ""
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
     * Procesa una imagen y devuelve el texto extraído (para diagnóstico)
     */
    suspend fun processImageAndGetText(imageUri: Uri): String {
        return try {
            processingManager.processImage(imageUri)
        } catch (e: Exception) {
            Log.e(TAG, "Error en processImageAndGetText: ${e.message}", e)
            throw e
        }
    }

    /**
     * Obtiene el tipo de documento detectado basado en el texto extraído actual
     */
    fun getDetectedDocumentType(): DocumentType {
        val text = extractedText.value
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