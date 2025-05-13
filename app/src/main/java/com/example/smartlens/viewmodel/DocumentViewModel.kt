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
import com.example.smartlens.service.*
import com.example.smartlens.worker.DocumentProcessingWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.UUID


/**
 * ViewModel para la gestión de documentos
 * Actualización: Agregado estado para controlar el procesamiento
 */
@HiltViewModel
class DocumentViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val ocrService: OcrService,
    private val geminiService: GeminiService,
    private val excelExportService: ExcelExportService,
    private val shareService: DocumentShareService,
    private val workManager: WorkManager,
    private val processingManager: DocumentProcessingManager,
    private val imageProcessingService: ImageProcessingService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val TAG = "DocumentViewModel"

    // Estado del procesamiento
    private val _processingState = MutableStateFlow<DocumentProcessingState>(DocumentProcessingState.Idle)
    val processingState: StateFlow<DocumentProcessingState> = _processingState

    // Estado de procesamiento activo
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    // Texto extraído por OCR
    private val _extractedText = MutableStateFlow("")
    val extractedText: StateFlow<String> = _extractedText

    // Datos estructurados extraídos
    private val _structuredData = MutableStateFlow<Map<String, String>>(emptyMap())
    val structuredData: StateFlow<Map<String, String>> = _structuredData

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

    // URI de la imagen procesada
    private val _processedImageUri = MutableStateFlow<Uri?>(null)
    val processedImageUri: StateFlow<Uri?> = _processedImageUri

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
     * Establece explícitamente la URI de la imagen procesada
     * Este método es crucial para mantener la referencia a la imagen
     */
    fun setProcessedImageUri(uri: Uri) {
        _processedImageUri.value = uri
        tempImageUri = uri
        Log.d(TAG, "URI de imagen procesada establecida: $uri")
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
            try {
                repository.getAllDocuments()
                    .catch { e ->
                        Log.e(TAG, "Error al cargar documentos: ${e.message}", e)
                        _userMessage.value = context.getString(R.string.loading_error)
                    }
                    .collect { documents ->
                        _recentDocuments.value = documents.sortedByDescending { doc -> doc.timestamp }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error en loadRecentDocuments: ${e.message}", e)
            }
        }
    }

    /**
     * Carga un documento por su ID
     */
    fun loadDocumentById(id: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Cargando documento con ID: $id")

                // Informar que estamos cargando
                _userMessage.value = context.getString(R.string.loading)

                val document = repository.getDocumentById(id)
                if (document != null) {
                    Log.d(TAG, "Documento encontrado: ${document.getTypeDisplay()} - ${document.getIdentifier()}")
                    _currentDocument.value = document

                    // Actualizar el estado para indicar que el documento está listo
                    _processingState.value = DocumentProcessingState.DocumentReady(document)
                } else {
                    Log.e(TAG, "Documento no encontrado con ID: $id")
                    _processingState.value = DocumentProcessingState.Error(
                        context.getString(R.string.document_not_found_error)
                    )
                    _userMessage.value = context.getString(R.string.document_not_found_error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar documento por ID: ${e.message}", e)
                _processingState.value = DocumentProcessingState.Error(e.message ?: "Error desconocido")
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
                _userMessage.value = context.getString(R.string.document_deleted)
                loadRecentDocuments()
            } catch (e: Exception) {
                Log.e(TAG, "Error al eliminar documento: ${e.message}", e)
                _userMessage.value = "Error al eliminar documento: ${e.message}"
            }
        }
    }

    /**
     * Guarda una imagen temporal en el directorio de la aplicación
     */
    suspend fun saveTemporaryImage(imageUri: Uri): Uri {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Guardando imagen temporal: $imageUri")
                val savedUri = repository.saveTempImage(imageUri)
                tempImageUri = savedUri
                Log.d(TAG, "Imagen temporal guardada en: $savedUri")
                savedUri
            } catch (e: Exception) {
                Log.e(TAG, "Error al guardar imagen temporal: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Obtiene el tipo de documento detectado
     */
    fun getDetectedDocumentType(): DocumentType {
        return when (val state = _processingState.value) {
            is DocumentProcessingState.ProcessingDocument -> state.documentType
            else -> DocumentType.UNKNOWN
        }
    }

    /**
     * Procesa una imagen para extraer texto
     */
    suspend fun processImage(imageUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Iniciando procesamiento de imagen con CV: $imageUri")
                _processingState.value = DocumentProcessingState.Capturing
                _isProcessing.value = true

                // Fase 1: Procesar la imagen con Computer Vision
                val enhancedImageUri = imageProcessingService.enhanceImageWithComputerVision(imageUri)
                _processedImageUri.value = enhancedImageUri
                tempImageUri = enhancedImageUri
                Log.d(TAG, "Imagen mejorada con CV: $enhancedImageUri")

                // Fase 2: Extraer texto con OCR de la imagen mejorada
                _processingState.value = DocumentProcessingState.ExtractingText
                val extractedText = ocrService.extractTextFromUri(enhancedImageUri)
                _extractedText.value = extractedText

                if (extractedText.isBlank()) {
                    throw IllegalStateException("No se pudo extraer texto de la imagen")
                }

                Log.d(TAG, "Texto extraído (${extractedText.length} caracteres)")

                // Detectar tipo de documento
                val detectedType = ocrService.detectDocumentType(extractedText)
                _processingState.value = DocumentProcessingState.ProcessingDocument(detectedType)
                Log.d(TAG, "Tipo de documento detectado: $detectedType")

                // Extraer datos estructurados si es posible
                try {
                    val structuredData = ocrService.extractStructuredText(enhancedImageUri)
                    _structuredData.value = structuredData
                    Log.d(TAG, "Datos estructurados extraídos: ${structuredData.keys}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error al extraer datos estructurados: ${e.message}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error en processImage: ${e.message}", e)
                _processingState.value = DocumentProcessingState.Error(e.message ?: "Error desconocido")
                _userMessage.value = context.getString(R.string.ocr_error)
                _isProcessing.value = false
            }
        }
    }

    /**
     * Procesa una imagen y devuelve el texto extraído (para diagnóstico)
     */
    suspend fun processImageAndGetText(imageUri: Uri): String {
        return withContext(Dispatchers.IO) {
            try {
                // Mejora la imagen con CV primero
                val enhancedUri = imageProcessingService.enhanceImageWithComputerVision(imageUri)
                // Luego extrae el texto con OCR
                val extractedText = ocrService.extractTextFromUri(enhancedUri)
                _extractedText.value = extractedText
                extractedText
            } catch (e: Exception) {
                Log.e(TAG, "Error en processImageAndGetText: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Procesa el documento según el tipo seleccionado
     * Implementa el flujo completo y maneja las transiciones de estado correctamente
     */
    fun processDocument(documentType: DocumentType) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Iniciando procesamiento de documento tipo: $documentType")
                _processingState.value = DocumentProcessingState.ProcessingDocument(documentType)
                _isProcessing.value = true

                // Obtener URI de la imagen procesada, o usar la temporal si no está disponible
                val imageUri = _processedImageUri.value ?: tempImageUri
                if (imageUri == null) {
                    Log.e(TAG, "Error: No hay imagen disponible para procesar")
                    _processingState.value = DocumentProcessingState.Error("No hay imagen disponible para procesar")
                    _userMessage.value = "Error: No hay imagen disponible"
                    _isProcessing.value = false
                    return@launch
                }

                // Primero actualizamos el estado para mostrar que estamos extrayendo texto
                // Esto es importante para la UI
                _processingState.value = DocumentProcessingState.ExtractingText

                // Extraer texto si aún no lo hemos hecho
                if (_extractedText.value.isBlank()) {
                    Log.d(TAG, "Extrayendo texto de la imagen")
                    try {
                        val extractedText = ocrService.extractTextFromUri(imageUri)
                        _extractedText.value = extractedText

                        if (extractedText.isBlank()) {
                            Log.e(TAG, "Error: No se pudo extraer texto de la imagen")
                            _processingState.value = DocumentProcessingState.Error("No se pudo extraer texto de la imagen")
                            _userMessage.value = context.getString(R.string.ocr_error)
                            _isProcessing.value = false
                            return@launch
                        }

                        Log.d(TAG, "Texto extraído correctamente (${extractedText.length} caracteres)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al extraer texto: ${e.message}", e)
                        _processingState.value = DocumentProcessingState.Error("Error al extraer texto: ${e.message}")
                        _userMessage.value = context.getString(R.string.ocr_error)
                        _isProcessing.value = false
                        return@launch
                    }
                }

                // Actualizar estado para mostrar que estamos procesando el documento
                _processingState.value = DocumentProcessingState.ProcessingDocument(documentType)

                // Procesar el documento según el tipo
                Log.d(TAG, "Procesando documento con el tipo seleccionado: $documentType")
                val document = try {
                    when (documentType) {
                        DocumentType.INVOICE -> {
                            geminiService.processInvoice(_extractedText.value, imageUri)
                        }
                        DocumentType.DELIVERY_NOTE -> {
                            geminiService.processDeliveryNote(_extractedText.value, imageUri)
                        }
                        DocumentType.WAREHOUSE_LABEL -> {
                            geminiService.processWarehouseLabel(_extractedText.value, imageUri)
                        }
                        else -> {
                            // Si es desconocido, intentar procesar como factura por defecto
                            Log.w(TAG, "Tipo de documento desconocido, procesando como factura")
                            geminiService.processInvoice(_extractedText.value, imageUri)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al procesar documento con Gemini: ${e.message}", e)

                    // Determinar tipo de error para mensaje adecuado
                    val errorMessage = when {
                        e.message?.contains("API Key") == true -> context.getString(R.string.api_key_missing_error)
                        e.message?.contains("conexión") == true -> context.getString(R.string.network_error)
                        else -> context.getString(R.string.processing_error_generic)
                    }

                    _processingState.value = DocumentProcessingState.Error(e.message ?: errorMessage)
                    _userMessage.value = errorMessage
                    _isProcessing.value = false
                    return@launch
                }

                // Guardar documento en la base de datos
                try {
                    Log.d(TAG, "Guardando documento en la base de datos: ${document.id}")
                    repository.saveDocument(document)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al guardar documento: ${e.message}", e)
                    _processingState.value = DocumentProcessingState.Error("Error al guardar documento: ${e.message}")
                    _userMessage.value = "Error al guardar documento"
                    _isProcessing.value = false
                    return@launch
                }

                // Actualizar estado para indicar que el documento está listo
                Log.d(TAG, "Documento procesado correctamente: ${document.id}")
                _currentDocument.value = document
                _processingState.value = DocumentProcessingState.DocumentReady(document)
                _userMessage.value = context.getString(R.string.document_saved)
                _isProcessing.value = false

                // Recargar documentos recientes
                loadRecentDocuments()

            } catch (e: Exception) {
                Log.e(TAG, "Error general en processDocument: ${e.message}", e)
                _processingState.value = DocumentProcessingState.Error(e.message ?: "Error desconocido")
                _userMessage.value = context.getString(R.string.processing_error_generic)
                _isProcessing.value = false
            }
        }
    }

    /**
     * Cancela el procesamiento actual
     */
    fun cancelProcessing() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Cancelando procesamiento...")

                currentWorkId?.let {
                    workManager.cancelWorkById(it)
                }

                _processingState.value = DocumentProcessingState.Idle
                _extractedText.value = ""
                _structuredData.value = emptyMap()
                tempImageUri = null
                _processedImageUri.value = null
                _isProcessing.value = false

                Log.d(TAG, "Procesamiento cancelado")
            } catch (e: Exception) {
                Log.e(TAG, "Error al cancelar procesamiento: ${e.message}", e)
            }
        }
    }

    /**
     * Comparte un documento como JSON
     */
    fun shareDocument(document: LogisticsDocument) {
        viewModelScope.launch {
            try {
                shareService.shareDocument(document)
            } catch (e: Exception) {
                Log.e(TAG, "Error al compartir documento: ${e.message}", e)
                _userMessage.value = "Error al compartir documento: ${e.message}"
            }
        }
    }

    /**
     * Comparte un documento como Excel
     */
    fun shareAsExcel(document: LogisticsDocument) {
        viewModelScope.launch {
            try {
                val excelUri = excelExportService.exportToExcel(document)
                shareService.shareAsExcel(excelUri)
            } catch (e: Exception) {
                Log.e(TAG, "Error al exportar a Excel: ${e.message}", e)
                _userMessage.value = "Error al exportar a Excel: ${e.message}"
            }
        }
    }

    /**
     * Genera un código QR para el documento
     */
    fun generateQrCode(document: LogisticsDocument): Bitmap {
        return shareService.generateQrCode(document)
    }

    /**
     * Marca que el mensaje al usuario ha sido mostrado
     */
    fun messageShown() {
        _userMessage.value = null
    }

    /**
     * Limpia recursos y reinicia estados
     */
    fun reset() {
        _processingState.value = DocumentProcessingState.Idle
        _extractedText.value = ""
        _structuredData.value = emptyMap()
        _customFileName.value = ""
        _processedImageUri.value = null
        tempImageUri = null
        _isProcessing.value = false
    }

    override fun onCleared() {
        super.onCleared()
        reset()
        // Realizar limpieza adicional si es necesario
    }
}