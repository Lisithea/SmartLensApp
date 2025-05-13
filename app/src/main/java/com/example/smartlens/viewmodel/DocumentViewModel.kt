package com.example.smartlens.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlens.R
import com.example.smartlens.model.*
import com.example.smartlens.repository.DocumentRepository
import com.example.smartlens.service.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel avanzado para la gestión de documentos con clasificación inteligente
 * Integra el nuevo sistema de clasificación y análisis de documentos
 */
@HiltViewModel
class EnhancedDocumentViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val documentAnalysisService: DocumentAnalysisService,
    private val enhancedGeminiService: EnhancedGeminiService,
    private val shareService: DocumentShareService,
    private val excelExportService: ExcelExportService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val TAG = "EnhancedDocumentVM"

    // Estado del análisis
    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState

    // Documento analizado
    private val _analysisResult = MutableStateFlow<DocumentAnalysisService.DocumentAnalysisResult?>(null)
    val analysisResult: StateFlow<DocumentAnalysisService.DocumentAnalysisResult?> = _analysisResult

    // Documento procesado
    private val _processedDocument = MutableStateFlow<LogisticsDocument?>(null)
    val processedDocument: StateFlow<LogisticsDocument?> = _processedDocument

    // Lista de documentos
    private val _documentList = MutableStateFlow<List<LogisticsDocument>>(emptyList())
    val documentList: StateFlow<List<LogisticsDocument>> = _documentList

    // Estado del mensaje para el usuario
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage

    // Estado de búsqueda
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Documentos filtrados por búsqueda
    val filteredDocuments: StateFlow<List<LogisticsDocument>> = combine(
        _documentList,
        _searchQuery
    ) { documents, query ->
        if (query.isBlank()) {
            documents
        } else {
            filterDocumentsByQuery(documents, query)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Tipo de documento específico seleccionado para filtrar
    private val _selectedDocumentType = MutableStateFlow<String?>(null)
    val selectedDocumentType: StateFlow<String?> = _selectedDocumentType

    // Documentos filtrados por tipo específico
    val documentsByType: StateFlow<List<LogisticsDocument>> = combine(
        filteredDocuments,
        _selectedDocumentType
    ) { documents, typeFilter ->
        if (typeFilter.isNullOrBlank()) {
            documents
        } else {
            documents.filter { document ->
                when (document) {
                    is Invoice -> {
                        // Para facturas, comparar con tipos específicos
                        typeFilter == "Factura Electrónica" ||
                                typeFilter == "Boleta" ||
                                typeFilter == "Nota de Crédito" ||
                                typeFilter == "Cotización" ||
                                typeFilter == "Orden de Compra" ||
                                typeFilter == "Recibo / Comprobante" ||
                                typeFilter == "Liquidación de Sueldo" ||
                                typeFilter == "Cartola Bancaria" ||
                                typeFilter == "Cheque"
                    }
                    is DeliveryNote -> typeFilter == "Guía de Despacho"
                    is WarehouseLabel -> typeFilter == "Etiqueta de Bodega"
                    else -> false
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Estado de diagnóstico
    private val _diagnosticResult = MutableStateFlow<DocumentAnalysisService.DiagnosticResult?>(null)
    val diagnosticResult: StateFlow<DocumentAnalysisService.DiagnosticResult?> = _diagnosticResult

    init {
        loadDocuments()
    }

    /**
     * Carga todos los documentos del repositorio
     */
    fun loadDocuments() {
        viewModelScope.launch {
            try {
                repository.getAllDocuments()
                    .catch { e ->
                        Log.e(TAG, "Error cargando documentos: ${e.message}", e)
                        _userMessage.value = context.getString(R.string.loading_error)
                    }
                    .collect { documents ->
                        _documentList.value = documents.sortedByDescending { it.timestamp }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error general cargando documentos: ${e.message}", e)
                _userMessage.value = "Error cargando documentos: ${e.message}"
            }
        }
    }

    /**
     * Establece el término de búsqueda para filtrar documentos
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Establece el tipo de documento para filtrar
     */
    fun setSelectedDocumentType(type: String?) {
        _selectedDocumentType.value = type
    }

    /**
     * Procesa la imagen utilizando el servicio de análisis avanzado
     */
    fun analyzeImage(imageUri: Uri) {
        viewModelScope.launch {
            try {
                _analysisState.value = AnalysisState.Analyzing
                _userMessage.value = "Analizando documento..."

                // Analizar la imagen para detectar el tipo y extraer texto
                val analysisResult = documentAnalysisService.analyzeDocument(imageUri)
                _analysisResult.value = analysisResult

                // Actualizar el estado según el resultado
                if (analysisResult.documentType == DocumentType.UNKNOWN) {
                    _analysisState.value = AnalysisState.UnknownType
                    _userMessage.value = "No se pudo determinar el tipo de documento"
                } else {
                    _analysisState.value = AnalysisState.TypeDetected(
                        analysisResult.documentType,
                        analysisResult.specificType
                    )
                    _userMessage.value = "Documento detectado: ${analysisResult.specificType}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error analizando imagen: ${e.message}", e)
                _analysisState.value = AnalysisState.Error(e.message ?: "Error desconocido")
                _userMessage.value = "Error analizando imagen: ${e.message}"
            }
        }
    }

    /**
     * Procesa completamente un documento utilizando el tipo detectado o específico
     */
    fun processDocument(imageUri: Uri, specificType: String? = null) {
        viewModelScope.launch {
            try {
                val currentAnalysis = _analysisResult.value

                _analysisState.value = AnalysisState.Processing
                _userMessage.value = "Procesando documento..."

                // Si ya tenemos un análisis previo, lo usamos
                val (document, analysisResult) = if (currentAnalysis != null) {
                    // Si se especificó un tipo distinto, lo usamos en lugar del detectado
                    val typeToUse = specificType ?: currentAnalysis.specificType

                    // Generar prompt optimizado para el tipo específico
                    val optimizedPrompt = documentAnalysisService.generateGeminiPrompt(
                        currentAnalysis.copy(specificType = typeToUse)
                    )

                    // Procesar documento con el tipo específico
                    val processedDoc = enhancedGeminiService.processGenericDocument(
                        currentAnalysis.extractedText,
                        imageUri,
                        typeToUse
                    )

                    Pair(processedDoc, currentAnalysis)
                } else {
                    // Si no tenemos análisis previo, hacemos el análisis y procesamiento completo
                    documentAnalysisService.analyzeAndProcessDocument(imageUri)
                }

                // Guardar documento si se procesó correctamente
                if (document != null) {
                    repository.saveDocument(document)
                    _processedDocument.value = document
                    _analysisState.value = AnalysisState.Completed(document, analysisResult.specificType)
                    _userMessage.value = "Documento procesado: ${analysisResult.specificType}"

                    // Recargar la lista de documentos
                    loadDocuments()
                } else {
                    _analysisState.value = AnalysisState.Error("No se pudo procesar el documento")
                    _userMessage.value = "Error: No se pudo procesar el documento"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando documento: ${e.message}", e)
                _analysisState.value = AnalysisState.Error(e.message ?: "Error desconocido")
                _userMessage.value = "Error procesando documento: ${e.message}"
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
                    _processedDocument.value = document
                    _userMessage.value = "Documento cargado: ${document.getIdentifier()}"
                } else {
                    _userMessage.value = context.getString(R.string.document_not_found_error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando documento: ${e.message}", e)
                _userMessage.value = "Error cargando documento: ${e.message}"
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
                loadDocuments()
            } catch (e: Exception) {
                Log.e(TAG, "Error eliminando documento: ${e.message}", e)
                _userMessage.value = "Error eliminando documento: ${e.message}"
            }
        }
    }

    /**
     * Exporta un documento a Excel y comparte
     */
    fun shareAsExcel(document: LogisticsDocument) {
        viewModelScope.launch {
            try {
                val excelUri = excelExportService.exportToExcel(document)
                shareService.shareAsExcel(excelUri)
            } catch (e: Exception) {
                Log.e(TAG, "Error exportando a Excel: ${e.message}", e)
                _userMessage.value = "Error exportando a Excel: ${e.message}"
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
                Log.e(TAG, "Error compartiendo documento: ${e.message}", e)
                _userMessage.value = "Error compartiendo documento: ${e.message}"
            }
        }
    }

    /**
     * Realiza un diagnóstico del sistema de OCR usando una imagen
     */
    fun performDiagnostic(imageUri: Uri) {
        viewModelScope.launch {
            try {
                _analysisState.value = AnalysisState.Analyzing
                _userMessage.value = "Realizando diagnóstico..."

                val result = documentAnalysisService.diagnosticAnalysis(imageUri)
                _diagnosticResult.value = result

                _analysisState.value = AnalysisState.DiagnosticCompleted(result)
                _userMessage.value = "Diagnóstico completado"
            } catch (e: Exception) {
                Log.e(TAG, "Error en diagnóstico: ${e.message}", e)
                _analysisState.value = AnalysisState.Error(e.message ?: "Error desconocido")
                _userMessage.value = "Error en diagnóstico: ${e.message}"
            }
        }
    }

    /**
     * Filtra documentos según el texto de búsqueda
     */
    private fun filterDocumentsByQuery(documents: List<LogisticsDocument>, query: String): List<LogisticsDocument> {
        val searchText = query.lowercase()

        return documents.filter { document ->
            // Buscar en el texto completo extraído
            if (document.rawTextContent.lowercase().contains(searchText)) {
                return@filter true
            }

            // Buscar en los campos específicos según el tipo
            when (document) {
                is Invoice -> {
                    document.invoiceNumber.lowercase().contains(searchText) ||
                            document.supplier.name.lowercase().contains(searchText) ||
                            document.client.name.lowercase().contains(searchText) ||
                            document.items.any { it.description.lowercase().contains(searchText) }
                }
                is DeliveryNote -> {
                    document.deliveryNoteNumber.lowercase().contains(searchText) ||
                            document.origin.name.lowercase().contains(searchText) ||
                            document.destination.name.lowercase().contains(searchText) ||
                            document.items.any { it.description.lowercase().contains(searchText) }
                }
                is WarehouseLabel -> {
                    document.labelId.lowercase().contains(searchText) ||
                            document.productName.lowercase().contains(searchText) ||
                            document.productCode.lowercase().contains(searchText)
                }
                else -> false
            }
        }
    }

    /**
     * Marca que el mensaje al usuario ha sido mostrado
     */
    fun messageShown() {
        _userMessage.value = null
    }

    /**
     * Estados posibles del análisis de documentos
     */
    sealed class AnalysisState {
        object Idle : AnalysisState()
        object Analyzing : AnalysisState()
        object Processing : AnalysisState()
        object UnknownType : AnalysisState()
        data class TypeDetected(val documentType: DocumentType, val specificType: String) : AnalysisState()
        data class Completed(val document: LogisticsDocument, val specificType: String) : AnalysisState()
        data class DiagnosticCompleted(val result: DocumentAnalysisService.DiagnosticResult) : AnalysisState()
        data class Error(val message: String) : AnalysisState()
    }
}