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
    private val imageProcessingService: ImageProcessingService, // Nuevo servicio añadido
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
     * Procesa una imagen para extraer texto
     * Ahora utiliza el nuevo servicio de procesamiento de imágenes
     */
    suspend fun processImage(imageUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Iniciando procesamiento de imagen con CV: $imageUri")
                _processingState.update { DocumentProcessingState.Capturing }

                // Fase 1: Procesar la imagen con Computer Vision
                val enhancedImageUri = imageProcessingService.enhanceImageWithComputerVision(imageUri)
                _processedImageUri.value = enhancedImageUri
                Log.d(TAG, "Imagen mejorada con CV: $enhancedImageUri")

                // Fase 2: Extraer texto con OCR de la imagen mejorada
                _processingState.update { DocumentProcessingState.ExtractingText }
                val extractedText = ocrService.extractTextFromUri(enhancedImageUri)
                processingManager.extractedText.value = extractedText

                if (extractedText.isBlank()) {
                    throw IllegalStateException("No se pudo extraer texto de la imagen")
                }

                Log.d(TAG, "Texto extraído (${extractedText.length} caracteres)")

                // Detectar tipo de documento
                val detectedType = ocrService.detectDocumentType(extractedText)
                _processingState.update { DocumentProcessingState.ProcessingDocument(detectedType) }
                Log.d(TAG, "Tipo de documento detectado: $detectedType")

                // Extraer datos estructurados si es posible
                try {
                    val structuredData = ocrService.extractStructuredText(enhancedImageUri)
                    processingManager.rawOcrResult.value = structuredData
                    Log.d(TAG, "Datos estructurados extraídos: ${structuredData.keys}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error al extraer datos estructurados: ${e.message}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error en processImage: ${e.message}", e)
                _processingState.update { DocumentProcessingState.Error(e.message ?: "Error desconocido") }
                _userMessage.value = context.getString(R.string.ocr_error)
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
                extractedText
            } catch (e: Exception) {
                Log.e(TAG, "Error en processImageAndGetText: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Procesa el documento según el tipo seleccionado
     * Utiliza el flujo completo con el nuevo servicio
     */
    fun processDocument(documentType: DocumentType) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Procesando documento de tipo: $documentType")
                _processingState.update { DocumentProcessingState.ProcessingDocument(documentType) }

                // Obtener URI de la imagen procesada, o usar la temporal si no está disponible
                val imageUri = _processedImageUri.value ?: tempImageUri
                if (imageUri == null) {
                    throw IllegalStateException("No hay imagen disponible para procesar")
                }

                // Proceso completo con el nuevo servicio de procesamiento
                val document = imageProcessingService.processDocumentImage(imageUri, documentType)

                // Guardar documento
                repository.saveDocument(document)

                // Actualizar estado
                _currentDocument.value = document
                _processingState.update { DocumentProcessingState.DocumentReady(document) }

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

                _processingState.update { DocumentProcessingState.Error(e.message ?: errorMessage) }
                _userMessage.value = errorMessage
            }
        }
    }}