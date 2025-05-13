package com.example.smartlens.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.smartlens.model.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extensión del servicio de Gemini con métodos mejorados para procesamiento de documentos
 * basado en el servicio existente pero con soporte para prompts personalizados y tipos adicionales
 */
@Singleton
class EnhancedGeminiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val geminiService: GeminiService
) {
    private val TAG = "EnhancedGeminiService"

    /**
     * Procesa una factura con IA usando un prompt personalizado
     */
    suspend fun processInvoiceWithCustomPrompt(
        text: String,
        imageUri: Uri,
        customPrompt: String? = null
    ): Invoice = withContext(Dispatchers.IO) {
        val apiKey = geminiService.getApiKey()
        if (apiKey.isBlank()) {
            Log.e(TAG, "Error: API Key de Gemini no configurada")
            throw IllegalStateException("API Key de Gemini no configurada. Por favor, configure la API Key en Ajustes.")
        }

        Log.d(TAG, "Procesando factura con prompt personalizado. Texto: ${text.take(100)}... (${text.length} caracteres)")

        // Usar el prompt personalizado si se proporciona, o el estándar en caso contrario
        val prompt = customPrompt ?: """
            Analiza el siguiente texto OCR de una factura y extrae los datos estructurados.
            
            Texto OCR:
            $text
            
            Extrae estos campos:
            1. Número de factura
            2. Fecha
            3. Fecha de vencimiento (si está disponible)
            4. Datos del proveedor (nombre, NIF, dirección)
            5. Datos del cliente (nombre, NIF, dirección)
            6. Líneas de detalle con: código, descripción, cantidad, precio unitario, precio total
            7. Subtotal
            8. Importe de IVA (con porcentaje si está disponible)
            9. Total
            10. Condiciones de pago
            11. Observaciones o notas
            12. Código de barras o referencia (si está disponible)
            
            Formatea la respuesta como JSON estrictamente con la siguiente estructura:
            {
              "invoiceNumber": "",
              "date": "",
              "dueDate": "",
              "supplier": {"name": "", "taxId": "", "address": "", "contactInfo": ""},
              "client": {"name": "", "taxId": "", "address": "", "contactInfo": ""},
              "items": [{"code": "", "description": "", "quantity": 0, "unitPrice": 0, "totalPrice": 0, "taxRate": 0}],
              "subtotal": 0,
              "taxAmount": 0,
              "totalAmount": 0,
              "paymentTerms": "",
              "notes": "",
              "barcode": ""
            }
            
            Si no puedes identificar algún campo, déjalo vacío o en 0 según corresponda.
            Es muy importante que el JSON sea válido y respete esta estructura exacta. 
            No agregues ningún texto adicional, solo el JSON.
        """.trimIndent()

        val generativeModel = try {
            GenerativeModel(
                modelName = "gemini-1.5-pro",
                apiKey = apiKey
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear modelo generativo: ${e.message}", e)
            throw IllegalStateException("Error al inicializar Gemini API: ${e.message}")
        }

        val inputContent = content { text(prompt) }

        try {
            Log.d(TAG, "Enviando prompt a Gemini API")
            val response = generativeModel.generateContent(inputContent)

            if (response.text == null) {
                Log.e(TAG, "Respuesta de Gemini vacía")
                throw IllegalStateException("El modelo no generó ninguna respuesta")
            }

            Log.d(TAG, "Respuesta recibida de Gemini. Procesando JSON...")

            // Extraer el JSON de la respuesta
            val jsonText = extractJsonFromResponse(response.text!!)
            Log.d(TAG, "JSON extraído: ${jsonText.take(100)}...")

            // Deserializar el JSON
            val invoiceData = try {
                gson.fromJson(jsonText, InvoiceData::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Error al deserializar JSON: ${e.message}", e)
                Log.e(TAG, "JSON recibido: $jsonText")
                throw IllegalStateException("Error al procesar la respuesta de Gemini: ${e.message}")
            }

            // Validar datos mínimos
            if (invoiceData.invoiceNumber.isBlank() || invoiceData.date.isBlank()) {
                Log.e(TAG, "Datos de factura incompletos: Número=${invoiceData.invoiceNumber}, Fecha=${invoiceData.date}")
                throw IllegalStateException("No se pudieron extraer los datos mínimos necesarios de la factura")
            }

            Log.d(TAG, "JSON procesado correctamente. Factura: ${invoiceData.invoiceNumber}")

            // Convertir a modelo Invoice
            return@withContext Invoice(
                imageUri = imageUri,
                rawTextContent = text,
                invoiceNumber = invoiceData.invoiceNumber,
                date = invoiceData.date,
                dueDate = invoiceData.dueDate,
                supplier = Company(
                    name = invoiceData.supplier.name,
                    taxId = invoiceData.supplier.taxId,
                    address = invoiceData.supplier.address,
                    contactInfo = invoiceData.supplier.contactInfo
                ),
                client = Company(
                    name = invoiceData.client.name,
                    taxId = invoiceData.client.taxId,
                    address = invoiceData.client.address,
                    contactInfo = invoiceData.client.contactInfo
                ),
                items = invoiceData.items.map { item ->
                    InvoiceItem(
                        code = item.code,
                        description = item.description,
                        quantity = item.quantity,
                        unitPrice = item.unitPrice,
                        totalPrice = item.totalPrice,
                        taxRate = item.taxRate
                    )
                },
                subtotal = invoiceData.subtotal,
                taxAmount = invoiceData.taxAmount,
                totalAmount = invoiceData.totalAmount,
                paymentTerms = invoiceData.paymentTerms,
                notes = invoiceData.notes,
                barcode = invoiceData.barcode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar factura con Gemini: ${e.message}", e)
            throw IllegalStateException("Error al procesar factura: ${e.message}")
        }
    }

    /**
     * Procesa un albarán/nota de entrega con IA usando un prompt personalizado
     */
    suspend fun processDeliveryNoteWithCustomPrompt(
        text: String,
        imageUri: Uri,
        customPrompt: String? = null
    ): DeliveryNote = withContext(Dispatchers.IO) {
        val apiKey = geminiService.getApiKey()
        if (apiKey.isBlank()) {
            Log.e(TAG, "Error: API Key de Gemini no configurada")
            throw IllegalStateException("API Key de Gemini no configurada. Por favor, configure la API Key en Ajustes.")
        }

        Log.d(TAG, "Procesando albarán con prompt personalizado. Texto: ${text.take(100)}...")

        // Usar el prompt personalizado si se proporciona, o el estándar en caso contrario
        val prompt = customPrompt ?: """
            Analiza el siguiente texto OCR de un albarán o nota de entrega y extrae los datos estructurados.
            
            Texto OCR:
            $text
            
            Extrae estos campos:
            1. Número de albarán
            2. Fecha
            3. Origen (nombre, dirección, persona de contacto, teléfono)
            4. Destino (nombre, dirección, persona de contacto, teléfono)
            5. Transportista
            6. Líneas de detalle con: código, descripción, cantidad, tipo de embalaje, peso
            7. Total de bultos
            8. Peso total
            9. Observaciones
            
            Formatea la respuesta como JSON estrictamente con la siguiente estructura:
            {
              "deliveryNoteNumber": "",
              "date": "",
              "origin": {"name": "", "address": "", "contactPerson": "", "contactPhone": ""},
              "destination": {"name": "", "address": "", "contactPerson": "", "contactPhone": ""},
              "carrier": "",
              "items": [{"code": "", "description": "", "quantity": 0, "packageType": "", "weight": 0}],
              "totalPackages": 0,
              "totalWeight": 0,
              "observations": ""
            }
            
            Si no puedes identificar algún campo, déjalo vacío o en 0 según corresponda.
            Es muy importante que el JSON sea válido y respete esta estructura exacta.
            No agregues ningún texto adicional, solo el JSON.
        """.trimIndent()

        val generativeModel = try {
            GenerativeModel(
                modelName = "gemini-1.5-pro",
                apiKey = apiKey
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear modelo generativo: ${e.message}", e)
            throw IllegalStateException("Error al inicializar Gemini API: ${e.message}")
        }

        val inputContent = content { text(prompt) }

        try {
            Log.d(TAG, "Enviando prompt a Gemini API")
            val response = generativeModel.generateContent(inputContent)

            if (response.text == null) {
                Log.e(TAG, "Respuesta de Gemini vacía")
                throw IllegalStateException("El modelo no generó ninguna respuesta")
            }

            Log.d(TAG, "Respuesta recibida de Gemini. Procesando JSON...")

            // Extraer el JSON de la respuesta
            val jsonText = extractJsonFromResponse(response.text!!)
            Log.d(TAG, "JSON extraído: ${jsonText.take(100)}...")

            // Deserializar el JSON
            val deliveryData = try {
                gson.fromJson(jsonText, DeliveryNoteData::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Error al deserializar JSON: ${e.message}", e)
                Log.e(TAG, "JSON recibido: $jsonText")
                throw IllegalStateException("Error al procesar la respuesta de Gemini: ${e.message}")
            }

            // Validar datos mínimos
            if (deliveryData.deliveryNoteNumber.isBlank() || deliveryData.date.isBlank()) {
                Log.e(TAG, "Datos de albarán incompletos: Número=${deliveryData.deliveryNoteNumber}, Fecha=${deliveryData.date}")
                throw IllegalStateException("No se pudieron extraer los datos mínimos necesarios del albarán")
            }

            Log.d(TAG, "JSON procesado correctamente. Albarán: ${deliveryData.deliveryNoteNumber}")

            // Convertir a modelo DeliveryNote
            return@withContext DeliveryNote(
                imageUri = imageUri,
                rawTextContent = text,
                deliveryNoteNumber = deliveryData.deliveryNoteNumber,
                date = deliveryData.date,
                origin = Location(
                    name = deliveryData.origin.name,
                    address = deliveryData.origin.address,
                    contactPerson = deliveryData.origin.contactPerson,
                    contactPhone = deliveryData.origin.contactPhone
                ),
                destination = Location(
                    name = deliveryData.destination.name,
                    address = deliveryData.destination.address,
                    contactPerson = deliveryData.destination.contactPerson,
                    contactPhone = deliveryData.destination.contactPhone
                ),
                carrier = deliveryData.carrier,
                items = deliveryData.items.map { item ->
                    DeliveryItem(
                        code = item.code,
                        description = item.description,
                        quantity = item.quantity,
                        packageType = item.packageType,
                        weight = item.weight
                    )
                },
                totalPackages = deliveryData.totalPackages,
                totalWeight = deliveryData.totalWeight,
                observations = deliveryData.observations
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar albarán con Gemini: ${e.message}", e)
            throw IllegalStateException("Error al procesar albarán: ${e.message}")
        }
    }

    /**
     * Procesa una etiqueta de almacén con IA usando un prompt personalizado
     */
    suspend fun processWarehouseLabelWithCustomPrompt(
        text: String,
        imageUri: Uri,
        customPrompt: String? = null
    ): WarehouseLabel = withContext(Dispatchers.IO) {
        val apiKey = geminiService.getApiKey()
        if (apiKey.isBlank()) {
            Log.e(TAG, "Error: API Key de Gemini no configurada")
            throw IllegalStateException("API Key de Gemini no configurada. Por favor, configure la API Key en Ajustes.")
        }

        Log.d(TAG, "Procesando etiqueta con prompt personalizado. Texto: ${text.take(100)}...")

        // Usar el prompt personalizado si se proporciona, o el estándar en caso contrario
        val prompt = customPrompt ?: """
            Analiza el siguiente texto OCR de una etiqueta de almacén o producto y extrae los datos estructurados.
            
            Texto OCR:
            $text
            
            Extrae estos campos:
            1. ID de etiqueta
            2. Código de producto
            3. Nombre de producto
            4. Cantidad
            5. Número de lote (si disponible)
            6. Fecha de caducidad (si disponible)
            7. Ubicación (si disponible)
            8. Código de barras (si disponible)
            
            Formatea la respuesta como JSON estrictamente con la siguiente estructura:
            {
              "labelId": "",
              "productCode": "",
              "productName": "",
              "quantity": 0,
              "batchNumber": "",
              "expirationDate": "",
              "location": "",
              "barcode": ""
            }
            
            Si no puedes identificar algún campo, déjalo vacío o en 0 según corresponda.
            Es muy importante que el JSON sea válido y respete esta estructura exacta.
            No agregues ningún texto adicional, solo el JSON.
        """.trimIndent()

        val generativeModel = try {
            GenerativeModel(
                modelName = "gemini-1.5-pro",
                apiKey = apiKey
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear modelo generativo: ${e.message}", e)
            throw IllegalStateException("Error al inicializar Gemini API: ${e.message}")
        }

        val inputContent = content { text(prompt) }

        try {
            Log.d(TAG, "Enviando prompt a Gemini API")
            val response = generativeModel.generateContent(inputContent)

            if (response.text == null) {
                Log.e(TAG, "Respuesta de Gemini vacía")
                throw IllegalStateException("El modelo no generó ninguna respuesta")
            }

            Log.d(TAG, "Respuesta recibida de Gemini. Procesando JSON...")

            // Extraer el JSON de la respuesta
            val jsonText = extractJsonFromResponse(response.text!!)
            Log.d(TAG, "JSON extraído: ${jsonText.take(100)}...")

            // Deserializar el JSON
            val labelData = try {
                gson.fromJson(jsonText, WarehouseLabelData::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Error al deserializar JSON: ${e.message}", e)
                Log.e(TAG, "JSON recibido: $jsonText")
                throw IllegalStateException("Error al procesar la respuesta de Gemini: ${e.message}")
            }

            // Validar datos mínimos
            if (labelData.labelId.isBlank() || labelData.productCode.isBlank() || labelData.productName.isBlank()) {
                Log.e(TAG, "Datos de etiqueta incompletos: ID=${labelData.labelId}, Código=${labelData.productCode}")
                throw IllegalStateException("No se pudieron extraer los datos mínimos necesarios de la etiqueta")
            }

            Log.d(TAG, "JSON procesado correctamente. Etiqueta: ${labelData.labelId}")

            // Convertir a modelo WarehouseLabel
            return@withContext WarehouseLabel(
                imageUri = imageUri,
                rawTextContent = text,
                labelId = labelData.labelId,
                productCode = labelData.productCode,
                productName = labelData.productName,
                quantity = labelData.quantity,
                batchNumber = labelData.batchNumber,
                expirationDate = labelData.expirationDate,
                location = labelData.location,
                barcode = labelData.barcode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar etiqueta con Gemini: ${e.message}", e)
            throw IllegalStateException("Error al procesar etiqueta: ${e.message}")
        }
    }

    /**
     * Procesa un documento de tipo genérico basado en el texto y el tipo detectado
     */
    suspend fun processGenericDocument(
        text: String,
        imageUri: Uri,
        specificType: String
    ): LogisticsDocument = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Procesando documento genérico de tipo: $specificType")

            // Determinar el tipo de documento y delegate al método correspondiente
            when {
                specificType.contains("Factura") ||
                        specificType.contains("Boleta") ||
                        specificType.contains("Recibo") ||
                        specificType.contains("Nota de Crédito") ||
                        specificType.contains("Cotización") ||
                        specificType.contains("Orden de Compra") ||
                        specificType.contains("Liquidación") ||
                        specificType.contains("Cheque") ||
                        specificType.contains("Cartola") -> {
                    return@withContext processInvoiceWithCustomPrompt(text, imageUri)
                }

                specificType.contains("Guía") ||
                        specificType.contains("Despacho") ||
                        specificType.contains("Transporte") -> {
                    return@withContext processDeliveryNoteWithCustomPrompt(text, imageUri)
                }

                specificType.contains("Etiqueta") ||
                        specificType.contains("Bodega") -> {
                    return@withContext processWarehouseLabelWithCustomPrompt(text, imageUri)
                }

                // Por defecto, procesar como factura
                else -> {
                    Log.d(TAG, "Tipo específico no reconocido, procesando como factura genérica")
                    return@withContext processInvoiceWithCustomPrompt(text, imageUri)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar documento genérico: ${e.message}", e)
            throw IllegalStateException("Error al procesar documento genérico: ${e.message}")
        }
    }

    /**
     * Extrae JSON de la respuesta de Gemini
     */
    private fun extractJsonFromResponse(response: String): String {
        // Buscar contenido entre llaves {} que sería nuestro JSON
        val jsonPattern = Regex("\\{[\\s\\S]*\\}")
        val matchResult = jsonPattern.find(response)

        return matchResult?.value ?: throw IllegalStateException("No se pudo extraer un JSON válido de la respuesta")
    }

    // Clases de datos para el parsing del JSON
    private data class InvoiceData(
        val invoiceNumber: String = "",
        val date: String = "",
        val dueDate: String? = null,
        val supplier: CompanyData = CompanyData(),
        val client: CompanyData = CompanyData(),
        val items: List<InvoiceItemData> = emptyList(),
        val subtotal: Double = 0.0,
        val taxAmount: Double = 0.0,
        val totalAmount: Double = 0.0,
        val paymentTerms: String? = null,
        val notes: String? = null,
        val barcode: String? = null
    )

    private data class CompanyData(
        val name: String = "",
        val taxId: String? = null,
        val address: String? = null,
        val contactInfo: String? = null
    )

    private data class InvoiceItemData(
        val code: String? = null,
        val description: String = "",
        val quantity: Double = 0.0,
        val unitPrice: Double = 0.0,
        val totalPrice: Double = 0.0,
        val taxRate: Double? = null
    )

    private data class DeliveryNoteData(
        val deliveryNoteNumber: String = "",
        val date: String = "",
        val origin: LocationData = LocationData(),
        val destination: LocationData = LocationData(),
        val carrier: String? = null,
        val items: List<DeliveryItemData> = emptyList(),
        val totalPackages: Int? = null,
        val totalWeight: Double? = null,
        val observations: String? = null
    )

    private data class LocationData(
        val name: String = "",
        val address: String = "",
        val contactPerson: String? = null,
        val contactPhone: String? = null
    )

    private data class DeliveryItemData(
        val code: String? = null,
        val description: String = "",
        val quantity: Double = 0.0,
        val packageType: String? = null,
        val weight: Double? = null
    )

    private data class WarehouseLabelData(
        val labelId: String = "",
        val productCode: String = "",
        val productName: String = "",
        val quantity: Double = 0.0,
        val batchNumber: String? = null,
        val expirationDate: String? = null,
        val location: String? = null,
        val barcode: String? = null
    )
}