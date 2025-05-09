package com.example.smartlens.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.smartlens.model.DocumentProcessingState
import com.example.smartlens.model.DocumentType
import com.example.smartlens.model.LogisticsDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.Font
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestor centralizado de procesamiento de documentos.
 * Maneja todo el flujo desde la captura de imagen hasta el procesamiento y exportación.
 */
@Singleton
class DocumentProcessingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ocrService: OcrService,
    private val geminiService: GeminiService,
    private val excelExportService: ExcelExportService
) {
    private val TAG = "DocProcessingManager"

    // Estado del procesamiento
    private val _processingState = MutableStateFlow<DocumentProcessingState>(DocumentProcessingState.Idle)
    val processingState: StateFlow<DocumentProcessingState> = _processingState

    // Texto extraído
    private val _extractedText = MutableStateFlow("")
    val extractedText: StateFlow<String> = _extractedText

    // Resultado OCR sin procesar
    private val _rawOcrResult = MutableStateFlow<Map<String, String>>(emptyMap())
    val rawOcrResult: StateFlow<Map<String, String>> = _rawOcrResult

    // Nombre personalizado para exportación
    private val _customExportName = MutableStateFlow("")
    val customExportName: StateFlow<String> = _customExportName

    // URI de la imagen temporal
    private var tempImageUri: Uri? = null

    /**
     * Procesa una imagen y extrae texto usando OCR
     */
    suspend fun processImage(imageUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando procesamiento de imagen: $imageUri")
            _processingState.value = DocumentProcessingState.Capturing

            // Guardar imagen temporal
            tempImageUri = saveImageToTempStorage(imageUri)
            Log.d(TAG, "Imagen guardada temporalmente: $tempImageUri")

            // Extraer texto con OCR
            _processingState.value = DocumentProcessingState.ExtractingText

            // Intentar preprocesar para mejorar OCR
            var extractedText = ""
            try {
                val preprocessedBitmap = ocrService.preprocessImageForOcr(tempImageUri!!)
                extractedText = ocrService.extractText(preprocessedBitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Error en preprocesamiento, usando método alternativo: ${e.message}")
                extractedText = ocrService.extractTextFromUri(tempImageUri!!)
            }

            Log.d(TAG, "Texto extraído (${extractedText.length} caracteres): ${extractedText.take(100)}...")
            _extractedText.value = extractedText

            // Extraer datos estructurados
            try {
                val structuredData = ocrService.extractStructuredText(tempImageUri!!)
                _rawOcrResult.value = structuredData
                Log.d(TAG, "Datos estructurados extraídos: ${structuredData.keys}")
            } catch (e: Exception) {
                Log.e(TAG, "Error al extraer datos estructurados: ${e.message}")
            }

            return@withContext extractedText
        } catch (e: Exception) {
            Log.e(TAG, "Error en procesamiento de imagen: ${e.message}", e)
            _processingState.value = DocumentProcessingState.Error("Error al procesar imagen: ${e.message}")
            throw e
        }
    }

    /**
     * Detecta el tipo de documento basado en el texto extraído
     */
    fun detectDocumentType(text: String): DocumentType {
        val documentType = ocrService.detectDocumentType(text)
        _processingState.value = DocumentProcessingState.ProcessingDocument(documentType)
        return documentType
    }

    /**
     * Procesa un documento según su tipo
     */
    suspend fun processDocument(documentType: DocumentType): LogisticsDocument = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Procesando documento de tipo: $documentType")
            _processingState.value = DocumentProcessingState.ProcessingDocument(documentType)

            // Verificar que tenemos texto e imagen
            val text = _extractedText.value
            val imageUri = tempImageUri

            if (text.isEmpty() || imageUri == null) {
                throw IllegalStateException("No hay suficiente información para procesar el documento")
            }

            // Procesar según tipo
            val document = when (documentType) {
                DocumentType.INVOICE -> geminiService.processInvoice(text, imageUri)
                DocumentType.DELIVERY_NOTE -> geminiService.processDeliveryNote(text, imageUri)
                DocumentType.WAREHOUSE_LABEL -> geminiService.processWarehouseLabel(text, imageUri)
                DocumentType.UNKNOWN -> {
                    // Intentar procesar como factura por defecto
                    geminiService.processInvoice(text, imageUri)
                }
            }

            // Generar automáticamente archivo Excel
            generateExcelFile(document)

            _processingState.value = DocumentProcessingState.DocumentReady(document)
            return@withContext document
        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar documento: ${e.message}", e)
            _processingState.value = DocumentProcessingState.Error("Error al procesar documento: ${e.message}")
            throw e
        }
    }

    /**
     * Establece un nombre personalizado para la exportación
     */
    fun setCustomExportName(name: String) {
        if (name.isNotBlank()) {
            _customExportName.value = name
        }
    }

    /**
     * Genera un archivo Excel con los datos del documento
     */
    suspend fun generateExcelFile(document: LogisticsDocument): Uri = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generando archivo Excel para documento: ${document.id}")

            // Crear directorio si no existe
            val excelDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "SmartLens/DocumentosOCR"
            )

            if (!excelDir.exists()) {
                excelDir.mkdirs()
            }

            // Determinar nombre de archivo
            val fileName = if (_customExportName.value.isNotBlank()) {
                "${_customExportName.value}.xlsx"
            } else {
                when (document) {
                    is com.example.smartlens.model.Invoice -> "Factura_${document.invoiceNumber}.xlsx"
                    is com.example.smartlens.model.DeliveryNote -> "Albaran_${document.deliveryNoteNumber}.xlsx"
                    is com.example.smartlens.model.WarehouseLabel -> "Etiqueta_${document.labelId}.xlsx"
                    else -> "Documento_${document.id}.xlsx"
                }
            }

            // Crear archivo Excel
            val excelFile = File(excelDir, fileName)
            val uri = excelExportService.exportToExcel(document)

            // Notificar al sistema multimedia
            context.sendBroadcast(android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(excelFile)))

            Log.d(TAG, "Archivo Excel generado en: ${excelFile.absolutePath}")
            return@withContext uri
        } catch (e: Exception) {
            Log.e(TAG, "Error al generar archivo Excel: ${e.message}", e)
            throw e
        }
    }

    /**
     * Guarda una imagen en almacenamiento temporal
     */
    private suspend fun saveImageToTempStorage(uri: Uri): Uri = withContext(Dispatchers.IO) {
        try {
            // Crear directorio si no existe
            val appDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SmartLens/temp")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }

            // Generar nombre único
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val destFile = File(appDir, "SmartLens_${timestamp}.jpg")

            // Copiar archivo
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw FileNotFoundException("No se pudo abrir el stream de entrada")

            return@withContext Uri.fromFile(destFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar imagen temporal: ${e.message}", e)
            throw e
        }
    }

    /**
     * Crear carpeta "SmartLens/DocumentosOCR" si no existe
     */
    fun createAppFolders() {
        try {
            val mainDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "SmartLens"
            )
            if (!mainDir.exists()) {
                mainDir.mkdirs()
            }

            val ocrDir = File(mainDir, "DocumentosOCR")
            if (!ocrDir.exists()) {
                ocrDir.mkdirs()
            }

            Log.d(TAG, "Carpetas de la aplicación creadas correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear carpetas de la aplicación: ${e.message}", e)
        }
    }

    /**
     * Resetea el estado del procesador
     */
    fun reset() {
        _processingState.value = DocumentProcessingState.Idle
        _extractedText.value = ""
        _rawOcrResult.value = emptyMap()
        _customExportName.value = ""
        tempImageUri = null
    }

    /**
     * Obtiene uri de documento para compartir
     */
    fun getShareableDocumentUri(document: LogisticsDocument): Uri {
        val jsonString = com.google.gson.Gson().toJson(document)

        val fileName = when (document) {
            is com.example.smartlens.model.Invoice -> "factura_${document.invoiceNumber}.json"
            is com.example.smartlens.model.DeliveryNote -> "albaran_${document.deliveryNoteNumber}.json"
            is com.example.smartlens.model.WarehouseLabel -> "etiqueta_${document.labelId}.json"
            else -> "documento_${document.id}.json"
        }

        val file = File(context.cacheDir, fileName)
        file.writeText(jsonString)

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}