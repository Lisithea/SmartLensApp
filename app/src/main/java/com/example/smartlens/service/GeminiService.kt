package com.example.smartlens.service

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.example.smartlens.model.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

@Singleton
class GeminiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val TAG = "GeminiService"
    private val preferences: SharedPreferences = context.getSharedPreferences("smartlens_settings", Context.MODE_PRIVATE)

    // Obtener API Key de las preferencias
    private fun getApiKey(): String {
        return preferences.getString("api_key", "") ?: ""
    }

    // Guardar API Key en las preferencias
    fun saveApiKey(key: String) {
        preferences.edit().putString("api_key", key).apply()
    }

    /**
     * Procesa una factura con IA
     */
    suspend fun processInvoice(text: String, imageUri: Uri?): Invoice = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            throw IllegalStateException("API Key de Gemini no configurada. Por favor, configure la API Key en Ajustes.")
        }

        Log.d(TAG, "Procesando factura con Gemini. Texto: ${text.take(100)}...")

        val prompt = """
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
            
            Asegúrate de que el JSON sea válido y respete esta estructura exacta.
        """.trimIndent()

        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-pro",
            apiKey = apiKey
        )

        val inputContent = content { text(prompt) }

        try {
            Log.d(TAG, "Enviando prompt a Gemini API")
            val response = generativeModel.generateContent(inputContent)

            Log.d(TAG, "Respuesta recibida de Gemini. Procesando JSON...")

            // Extraer el JSON de la respuesta
            val jsonText = extractJsonFromResponse(response.text ?: "")
            val invoiceData = gson.fromJson(jsonText, InvoiceData::class.java)

            Log.d(TAG, "JSON procesado correctamente: ${jsonText.take(100)}...")

            // Convertir a modelo Invoice
            return@withContext Invoice(
                imageUri = imageUri ?: Uri.EMPTY,
                rawTextContent = text,
                invoiceNumber = invoiceData.invoiceNumber,
                date = invoiceData.date,
                dueDate = invoiceData.dueDate,
                supplier = invoiceData.supplier,
                client = invoiceData.client,
                items = invoiceData.items,
                subtotal = invoiceData.subtotal,
                taxAmount = invoiceData.taxAmount,
                totalAmount = invoiceData.totalAmount,
                paymentTerms = invoiceData.paymentTerms,
                notes = invoiceData.notes,
                barcode = invoiceData.barcode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error en procesamiento de factura con Gemini: ${e.message}", e)
            throw IllegalStateException("Error al procesar factura: ${e.message}")
        }
    }

    /**
     * Procesa un albarán con IA
     */
    suspend fun processDeliveryNote(text: String, imageUri: Uri?): DeliveryNote = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            throw IllegalStateException("API Key de Gemini no configurada. Por favor, configure la API Key en Ajustes.")
        }

        Log.d(TAG, "Procesando albarán con Gemini. Texto: ${text.take(100)}...")

        val prompt = """
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
            
            Asegúrate de que el JSON sea válido y respete esta estructura exacta.
        """.trimIndent()

        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-pro",
            apiKey = apiKey
        )

        val inputContent = content { text(prompt) }

        try {
            Log.d(TAG, "Enviando prompt a Gemini API")
            val response = generativeModel.generateContent(inputContent)

            Log.d(TAG, "Respuesta recibida de Gemini. Procesando JSON...")

            // Extraer el JSON de la respuesta
            val jsonText = extractJsonFromResponse(response.text ?: "")
            val deliveryData = gson.fromJson(jsonText, DeliveryNoteData::class.java)

            Log.d(TAG, "JSON procesado correctamente: ${jsonText.take(100)}...")

            // Convertir a modelo DeliveryNote
            return@withContext DeliveryNote(
                imageUri = imageUri ?: Uri.EMPTY,
                rawTextContent = text,
                deliveryNoteNumber = deliveryData.deliveryNoteNumber,
                date = deliveryData.date,
                origin = deliveryData.origin,
                destination = deliveryData.destination,
                carrier = deliveryData.carrier,
                items = deliveryData.items,
                totalPackages = deliveryData.totalPackages,
                totalWeight = deliveryData.totalWeight,
                observations = deliveryData.observations
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error en procesamiento de albarán con Gemini: ${e.message}", e)
            throw IllegalStateException("Error al procesar albarán: ${e.message}")
        }
    }

    /**
     * Procesa una etiqueta de almacén con IA
     */
    suspend fun processWarehouseLabel(text: String, imageUri: Uri?): WarehouseLabel = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            throw IllegalStateException("API Key de Gemini no configurada. Por favor, configure la API Key en Ajustes.")
        }

        Log.d(TAG, "Procesando etiqueta con Gemini. Texto: ${text.take(100)}...")

        val prompt = """
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
            
            Asegúrate de que el JSON sea válido y respete esta estructura exacta.
        """.trimIndent()

        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-pro",
            apiKey = apiKey
        )

        val inputContent = content { text(prompt) }

        try {
            Log.d(TAG, "Enviando prompt a Gemini API")
            val response = generativeModel.generateContent(inputContent)

            Log.d(TAG, "Respuesta recibida de Gemini. Procesando JSON...")

            // Extraer el JSON de la respuesta
            val jsonText = extractJsonFromResponse(response.text ?: "")
            val labelData = gson.fromJson(jsonText, WarehouseLabelData::class.java)

            Log.d(TAG, "JSON procesado correctamente: ${jsonText.take(100)}...")

            // Convertir a modelo WarehouseLabel
            return@withContext WarehouseLabel(
                imageUri = imageUri ?: Uri.EMPTY,
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
            Log.e(TAG, "Error en procesamiento de etiqueta con Gemini: ${e.message}", e)
            throw IllegalStateException("Error al procesar etiqueta: ${e.message}")
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
        val invoiceNumber: String,
        val date: String,
        val dueDate: String? = null,
        val supplier: Company,
        val client: Company,
        val items: List<InvoiceItem>,
        val subtotal: Double,
        val taxAmount: Double,
        val totalAmount: Double,
        val paymentTerms: String? = null,
        val notes: String? = null,
        val barcode: String? = null
    )

    private data class DeliveryNoteData(
        val deliveryNoteNumber: String,
        val date: String,
        val origin: Location,
        val destination: Location,
        val carrier: String? = null,
        val items: List<DeliveryItem>,
        val totalPackages: Int? = null,
        val totalWeight: Double? = null,
        val observations: String? = null
    )

    private data class WarehouseLabelData(
        val labelId: String,
        val productCode: String,
        val productName: String,
        val quantity: Double,
        val batchNumber: String? = null,
        val expirationDate: String? = null,
        val location: String? = null,
        val barcode: String? = null
    )
}