package com.example.smartlens.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlens.model.DocumentType
import com.example.smartlens.model.LogisticsDocument
import com.example.smartlens.repository.DocumentRepository
import com.example.smartlens.service.DocumentAnalysisService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel mejorado que utiliza el servicio de análisis de documentos
 * para proporcionar funcionalidades avanzadas de clasificación y diagnóstico
 */
@HiltViewModel
class EnhancedDocumentViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val documentAnalysisService: DocumentAnalysisService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val TAG = "EnhancedDocumentVM"

    // Estado del análisis
    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState

    // Resultado del análisis
    private val _analysisResult = MutableStateFlow<DocumentAnalysisService.DocumentAnalysisResult?>(null)
    val analysisResult: StateFlow<DocumentAnalysisService.DocumentAnalysisResult?> = _analysisResult

    // Resultado del diagnóstico
    private val _diagnosticResult = MutableStateFlow<DocumentAnalysisService.DiagnosticResult?>(null)
    val diagnosticResult: StateFlow<DocumentAnalysisService.DiagnosticResult?> = _diagnosticResult

    // Documento procesado
    private val _processedDocument = MutableStateFlow<LogisticsDocument?>(null)
    val processedDocument: StateFlow<LogisticsDocument?> = _processedDocument

    // Mensajes para el usuario
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage

    /**
     * Analiza una imagen para detectar el tipo de documento
     */
    fun analyzeImage(imageUri: Uri) {
        viewModelScope.launch {
            try {
                _analysisState.value = AnalysisState.Analyzing
                _userMessage.value = "Analizando documento..."

                // Realizar análisis del documento
                val result = documentAnalysisService.analyzeDocument(imageUri)
                _analysisResult.value = result

                // Actualizar estado según el resultado
                if (result.documentType == DocumentType.UNKNOWN) {
                    _analysisState.value = AnalysisState.UnknownType
                    _userMessage.value = "No se pudo determinar el tipo de documento"
                } else {
                    _analysisState.value = AnalysisState.TypeDetected(
                        result.documentType,
                        result.specificType
                    )
                    _userMessage.value = "Se ha detectado un documento tipo: ${result.specificType}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al analizar imagen: ${e.message}", e)
                _analysisState.value = AnalysisState.Error(e.message ?: "Error desconocido")
                _userMessage.value = "Error al analizar imagen: ${e.message}"
            }
        }
    }

    /**
     * Realiza un diagnóstico completo de OCR
     */
    fun performDiagnostic(imageUri: Uri) {
        viewModelScope.launch {
            try {
                _analysisState.value = AnalysisState.Analyzing
                _userMessage.value = "Realizando diagnóstico..."

                // Realizar diagnóstico
                val result = documentAnalysisService.diagnosticAnalysis(imageUri)
                _diagnosticResult.value = result

                _analysisState.value = AnalysisState.DiagnosticCompleted(result)

                if (result.ocrSuccess) {
                    _userMessage.value = "Diagnóstico completado con éxito"
                } else {
                    _userMessage.value = "OCR no pudo extraer texto de la imagen"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en diagnóstico: ${e.message}", e)
                _analysisState.value = AnalysisState.Error(e.message ?: "Error desconocido")
                _userMessage.value = "Error en diagnóstico: ${e.message}"
            }
        }
    }

    /**
     * Procesa un documento con el tipo específico indicado
     */
    fun processDocument(imageUri: Uri, specificType: String? = null) {
        viewModelScope.launch {
            try {
                _analysisState.value = AnalysisState.Processing
                _userMessage.value = "Procesando documento..."

                // Comprobar si ya tenemos un análisis previo
                val currentAnalysis = _analysisResult.value

                if (currentAnalysis != null) {
                    // Usar el tipo específico proporcionado o el detectado previamente
                    val typeToUse = specificType ?: currentAnalysis.specificType

                    // Procesar el documento según su tipo genérico
                    val document = when (currentAnalysis.documentType) {
                        DocumentType.INVOICE ->
                            processInvoice(currentAnalysis.extractedText, imageUri)
                        DocumentType.DELIVERY_NOTE ->
                            processDeliveryNote(currentAnalysis.extractedText, imageUri)
                        DocumentType.WAREHOUSE_LABEL ->
                            processWarehouseLabel(currentAnalysis.extractedText, imageUri)
                        else ->
                            // Por defecto procesar como factura
                            processInvoice(currentAnalysis.extractedText, imageUri)
                    }

                    // Guardar el documento procesado
                    repository.saveDocument(document)

                    // Actualizar estado
                    _processedDocument.value = document
                    _analysisState.value = AnalysisState.Completed(document, typeToUse)
                    _userMessage.value = "Documento procesado y guardado"
                } else {
                    // Si no tenemos análisis previo, realizar análisis y procesamiento completo
                    val (document, result) = documentAnalysisService.analyzeAndProcessDocument(imageUri)

                    if (document != null) {
                        // Guardar documento
                        repository.saveDocument(document)

                        // Actualizar estado
                        _processedDocument.value = document
                        _analysisState.value = AnalysisState.Completed(document, result.specificType)
                        _userMessage.value = "Documento procesado y guardado"
                    } else {
                        _analysisState.value = AnalysisState.Error("No se pudo procesar el documento")
                        _userMessage.value = "Error: No se pudo procesar el documento"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al procesar documento: ${e.message}", e)
                _analysisState.value = AnalysisState.Error(e.message ?: "Error desconocido")
                _userMessage.value = "Error al procesar documento: ${e.message}"
            }
        }
    }

    // Métodos auxiliares para procesar documentos según su tipo
    private suspend fun processInvoice(text: String, imageUri: Uri): LogisticsDocument {
        return documentAnalysisService.generateGeminiPrompt(_analysisResult.value!!).let { prompt ->
            repository.processInvoice(text, imageUri, prompt)
        }
    }

    private suspend fun processDeliveryNote(text: String, imageUri: Uri): LogisticsDocument {
        return documentAnalysisService.generateGeminiPrompt(_analysisResult.value!!).let { prompt ->
            repository.processDeliveryNote(text, imageUri, prompt)
        }
    }

    private suspend fun processWarehouseLabel(text: String, imageUri: Uri): LogisticsDocument {
        return documentAnalysisService.generateGeminiPrompt(_analysisResult.value!!).let { prompt ->
            repository.processWarehouseLabel(text, imageUri, prompt)
        }
    }

    /**
     * Marca que el mensaje ha sido mostrado
     */
    fun messageShown() {
        _userMessage.value = null
    }

    /**
     * Estados posibles durante el análisis y procesamiento
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