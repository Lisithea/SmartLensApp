package com.example.smartlens.service

import android.util.Log
import com.example.smartlens.model.DocumentType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Clase encargada de clasificar documentos según su contenido
 * Proporciona funcionalidades avanzadas para detectar tipos de documentos
 * y extraer campos estructurados basados en reglas predefinidas
 */
class DocumentClassifier {
    private val TAG = "DocumentClassifier"

    // Lista de tipos de documentos con sus keywords y campos
    private val documentTypes = listOf(
        DocumentTypeDefinition(
            type = "Factura Electrónica",
            keywords = listOf("factura", "rut", "razón social", "neto", "iva", "total"),
            fields = listOf("RUT Emisor", "Razón Social", "Fecha", "Folio", "Total Neto", "IVA", "Total"),
            documentType = DocumentType.INVOICE
        ),
        DocumentTypeDefinition(
            type = "Boleta",
            keywords = listOf("boleta", "total", "neto", "rut", "cliente"),
            fields = listOf("RUT", "Fecha", "Total Neto", "IVA", "Total"),
            documentType = DocumentType.INVOICE
        ),
        DocumentTypeDefinition(
            type = "Guía de Despacho",
            keywords = listOf("guía de despacho", "remitente", "destinatario", "vehículo"),
            fields = listOf("Remitente", "Destinatario", "Patente", "Folio", "Fecha", "Productos"),
            documentType = DocumentType.DELIVERY_NOTE
        ),
        DocumentTypeDefinition(
            type = "Nota de Crédito",
            keywords = listOf("nota de crédito", "devolución", "referencia", "factura"),
            fields = listOf("Fecha", "Monto", "Referencia Factura", "RUT", "Motivo"),
            documentType = DocumentType.INVOICE
        ),
        DocumentTypeDefinition(
            type = "Cotización",
            keywords = listOf("cotización", "precio", "validez", "producto", "total"),
            fields = listOf("RUT", "Cliente", "Fecha", "Detalle Productos", "Subtotal", "IVA", "Total"),
            documentType = DocumentType.INVOICE
        ),
        DocumentTypeDefinition(
            type = "Orden de Compra",
            keywords = listOf("orden de compra", "proveedor", "cantidad", "número orden"),
            fields = listOf("Fecha", "Número de Orden", "Proveedor", "RUT", "Monto", "Productos"),
            documentType = DocumentType.INVOICE
        ),
        DocumentTypeDefinition(
            type = "Recibo / Comprobante",
            keywords = listOf("recibo", "comprobante", "cancelado", "efectivo"),
            fields = listOf("Fecha", "Monto", "Pagador", "Motivo"),
            documentType = DocumentType.INVOICE
        ),
        DocumentTypeDefinition(
            type = "Liquidación de Sueldo",
            keywords = listOf("liquidación", "renta", "imposiciones", "afp", "salud"),
            fields = listOf("Nombre", "RUT", "Fecha", "Sueldo Base", "Descuentos", "Líquido a Pagar"),
            documentType = DocumentType.INVOICE
        ),
        DocumentTypeDefinition(
            type = "Cartola Bancaria",
            keywords = listOf("cartola", "saldo", "abono", "cargo", "movimientos"),
            fields = listOf("Nombre", "RUT", "Banco", "Número de Cuenta", "Fecha", "Saldo", "Movimientos"),
            documentType = DocumentType.INVOICE
        ),
        DocumentTypeDefinition(
            type = "Cheque",
            keywords = listOf("cheque", "banco", "orden de", "monto", "fecha"),
            fields = listOf("Banco", "Fecha", "Monto", "A nombre de"),
            documentType = DocumentType.INVOICE
        ),
        DocumentTypeDefinition(
            type = "Etiqueta de Bodega",
            keywords = listOf("producto", "lote", "peso", "ref", "código"),
            fields = listOf("Producto", "Código", "Lote", "Fecha", "Peso"),
            documentType = DocumentType.WAREHOUSE_LABEL
        )
    )

    /**
     * Detecta el tipo de documento basado en el texto extraído
     */
    fun detectDocumentType(text: String): Pair<DocumentType, String> {
        Log.d(TAG, "Detectando tipo de documento a partir del texto")
        val lowerText = text.lowercase()

        // Buscar coincidencias con tipos de documentos específicos
        documentTypes.forEach { docType ->
            // Contamos cuántas palabras clave coinciden
            val matchCount = docType.keywords.count { keyword ->
                lowerText.contains(keyword.lowercase())
            }

            // Si coinciden al menos la mitad de las palabras clave, consideramos una coincidencia
            if (matchCount >= (docType.keywords.size / 2)) {
                Log.d(TAG, "Documento detectado como: ${docType.type}")
                return Pair(docType.documentType, docType.type)
            }
        }

        // Si no hay una coincidencia clara con los tipos específicos, usamos la detección genérica
        return when {
            // Detección de facturas
            lowerText.contains("factura") ||
                    lowerText.contains("invoice") ||
                    lowerText.contains("recibo") ||
                    lowerText.contains("receipt") ||
                    (lowerText.contains("total") && (lowerText.contains("iva") || lowerText.contains("impuesto"))) -> {
                Log.d(TAG, "Documento detectado como: FACTURA")
                Pair(DocumentType.INVOICE, "Factura")
            }

            // Detección de albaranes
            lowerText.contains("albarán") ||
                    lowerText.contains("delivery note") ||
                    lowerText.contains("nota de entrega") ||
                    lowerText.contains("guía de despacho") ||
                    (lowerText.contains("entrega") && lowerText.contains("mercancía")) -> {
                Log.d(TAG, "Documento detectado como: ALBARÁN")
                Pair(DocumentType.DELIVERY_NOTE, "Albarán")
            }

            // Detección de etiquetas
            lowerText.contains("ref:") ||
                    lowerText.contains("lote:") ||
                    lowerText.contains("peso:") ||
                    lowerText.contains("etiqueta") ||
                    (lowerText.contains("producto") && lowerText.contains("código")) -> {
                Log.d(TAG, "Documento detectado como: ETIQUETA")
                Pair(DocumentType.WAREHOUSE_LABEL, "Etiqueta")
            }

            // Si no se puede determinar
            else -> {
                Log.d(TAG, "No se pudo determinar el tipo de documento, marcando como DESCONOCIDO")
                Pair(DocumentType.UNKNOWN, "Documento Desconocido")
            }
        }
    }

    /**
     * Extrae campos específicos del texto según el tipo de documento detectado
     */
    fun extractFields(text: String, detectedType: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lowerText = text.lowercase()

        // Buscar el tipo de documento
        val docType = documentTypes.find { it.type.equals(detectedType, ignoreCase = true) }

        // Si no encontramos el tipo específico, devolvemos un mapa vacío
        if (docType == null) {
            Log.d(TAG, "No se encontró una definición para el tipo: $detectedType")
            return result
        }

        // Para cada campo que queremos extraer
        docType.fields.forEach { field ->
            // Buscamos patrones en el texto según el tipo de campo
            val value = when (field) {
                "RUT", "RUT Emisor" -> extractRut(text)
                "Fecha" -> extractDate(text)
                "Total", "Monto", "Total Neto" -> extractAmount(text, field)
                "IVA" -> extractIva(text)
                "Folio", "Número de Orden" -> extractDocumentNumber(text)
                "Razón Social", "Cliente", "Proveedor" -> extractEntityName(text, field)
                else -> extractGenericField(text, field)
            }

            // Añadimos el campo al resultado si se encontró un valor
            if (value.isNotEmpty()) {
                result[field] = value
            }
        }

        return result
    }

    /**
     * Extrae un RUT del texto
     */
    private fun extractRut(text: String): String {
        // Patrón para RUT chileno: XX.XXX.XXX-X o sin puntos
        val rutPattern = Regex("\\b\\d{1,2}(\\.\\d{3}){2}-[\\dkK]\\b|\\b\\d{7,8}-[\\dkK]\\b")
        val match = rutPattern.find(text) ?: return ""
        return match.value
    }

    /**
     * Extrae una fecha del texto
     */
    private fun extractDate(text: String): String {
        // Patrones de fecha comunes en Chile
        val datePatterns = listOf(
            Regex("\\b(\\d{1,2})/(\\d{1,2})/(\\d{2,4})\\b"),  // dd/mm/yyyy
            Regex("\\b(\\d{1,2})-(\\d{1,2})-(\\d{2,4})\\b"),  // dd-mm-yyyy
            Regex("\\b(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2,4})\\b"),  // dd.mm.yyyy
            Regex("\\b(\\d{1,2}) de ([a-zA-ZáéíóúÁÉÍÓÚ]+),? (\\d{4})\\b")  // dd de Mes, yyyy
        )

        for (pattern in datePatterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.value
            }
        }

        // Si hay la palabra "fecha" seguida de algo que parece una fecha
        val fechaPattern = Regex("fecha:?\\s*([0-9]{1,2}[/.-][0-9]{1,2}[/.-][0-9]{2,4})", RegexOption.IGNORE_CASE)
        val fechaMatch = fechaPattern.find(text)
        if (fechaMatch != null && fechaMatch.groupValues.size > 1) {
            return fechaMatch.groupValues[1]
        }

        return ""
    }

    /**
     * Extrae un monto del texto
     */
    private fun extractAmount(text: String, fieldName: String): String {
        // Buscar patrones como "Total: $1.234.567" o "Monto: 1.234.567"
        val amountPatterns = listOf(
            Regex("$fieldName:?\\s*\\$?\\s*([0-9.,]+)", RegexOption.IGNORE_CASE),
            Regex("$fieldName:?\\s*\\$?\\s*(\\d{1,3}(\\.\\d{3})*,\\d{2})", RegexOption.IGNORE_CASE),
            Regex("$fieldName:?\\s*\\$?\\s*(\\d{1,3}(,\\d{3})*\\.\\d{2})", RegexOption.IGNORE_CASE),
            Regex("$fieldName:?\\s*\\$?\\s*(\\d+)", RegexOption.IGNORE_CASE)
        )

        for (pattern in amountPatterns) {
            val match = pattern.find(text)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }

        // Si no encontramos un patrón específico para el campo, buscar cualquier valor monetario
        val moneyPattern = Regex("\\$\\s*([0-9.,]+)")
        val moneyMatch = moneyPattern.find(text)
        if (moneyMatch != null && moneyMatch.groupValues.size > 1) {
            return moneyMatch.groupValues[1]
        }

        return ""
    }

    /**
     * Extrae el IVA del texto
     */
    private fun extractIva(text: String): String {
        // Buscar patrones como "IVA: $1.234" o "IVA 19%: 1.234"
        val ivaPatterns = listOf(
            Regex("IVA(?:\\s*19%)?:?\\s*\\$?\\s*([0-9.,]+)", RegexOption.IGNORE_CASE),
            Regex("I\\.V\\.A(?:\\s*19%)?:?\\s*\\$?\\s*([0-9.,]+)", RegexOption.IGNORE_CASE)
        )

        for (pattern in ivaPatterns) {
            val match = pattern.find(text)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }

        return ""
    }

    /**
     * Extrae un número de documento
     */
    private fun extractDocumentNumber(text: String): String {
        // Buscar patrones como "Folio: 12345" o "N° 12345" o "Número: 12345"
        val numberPatterns = listOf(
            Regex("Folio:?\\s*([0-9]+)", RegexOption.IGNORE_CASE),
            Regex("N[°º]\\s*:?\\s*([0-9]+)", RegexOption.IGNORE_CASE),
            Regex("Numero:?\\s*([0-9]+)", RegexOption.IGNORE_CASE),
            Regex("Número:?\\s*([0-9]+)", RegexOption.IGNORE_CASE),
            Regex("Orden:?\\s*([0-9]+)", RegexOption.IGNORE_CASE),
            Regex("N°\\s*Orden:?\\s*([0-9]+)", RegexOption.IGNORE_CASE)
        )

        for (pattern in numberPatterns) {
            val match = pattern.find(text)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }

        return ""
    }

    /**
     * Extrae un nombre de entidad (cliente, proveedor, etc.)
     */
    private fun extractEntityName(text: String, fieldName: String): String {
        // Buscar patrones como "Cliente: Nombre Empresa" o "Razón Social: Nombre Empresa"
        val patterns = listOf(
            Regex("$fieldName:?\\s*([A-Za-zÁÉÍÓÚáéíóúÑñ0-9.\\s]+)(?:\\r|\\n|\\s{2,})", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null && match.groupValues.size > 1) {
                // Limpiamos el resultado eliminando espacios extra y caracteres raros
                return match.groupValues[1].trim().replace(Regex("\\s+"), " ")
            }
        }

        return ""
    }

    /**
     * Extrae un campo genérico buscando el nombre del campo seguido de algún valor
     */
    private fun extractGenericField(text: String, fieldName: String): String {
        // Buscar patron como "Campo: Valor"
        val pattern = Regex("$fieldName:?\\s*([\\w\\s.,]+)(?:\\r|\\n|\\s{2,})", RegexOption.IGNORE_CASE)
        val match = pattern.find(text)

        if (match != null && match.groupValues.size > 1) {
            return match.groupValues[1].trim()
        }

        return ""
    }

    /**
     * Genera un prompt para la API de Gemini o GPT basado en el tipo de documento detectado
     */
    fun generatePromptForAI(text: String, detectedType: String): String {
        // Buscar campos relevantes para el tipo de documento
        val docType = documentTypes.find { it.type.equals(detectedType, ignoreCase = true) }

        if (docType != null) {
            val fieldsToExtract = docType.fields.joinToString(", ")

            return """
                Analiza el siguiente texto OCR de un documento tipo "${docType.type}" y extrae los siguientes campos:
                ${fieldsToExtract}
                
                Texto OCR:
                $text
                
                Extrae la información relevante y estructúrala en formato JSON con la siguiente estructura:
                {
                  "documentType": "${docType.type}",
                  "fields": {
                    ${docType.fields.joinToString(",\n    ") { "\"$it\": \"\"" }}
                  }
                }
                
                Si no puedes identificar algún campo, déjalo vacío.
                Responde únicamente con el JSON, sin texto adicional.
            """.trimIndent()
        } else {
            // Prompt genérico si no tenemos definición para este tipo
            return """
                Analiza el siguiente texto OCR y extrae la información más relevante como campos clave-valor en formato JSON.
                
                Texto OCR:
                $text
                
                Extrae la información relevante y estructúrala en formato JSON.
                Responde únicamente con el JSON, sin texto adicional.
            """.trimIndent()
        }
    }

    /**
     * Carga definiciones de tipos de documentos desde un JSON
     */
    fun loadDefinitionsFromJson(jsonString: String) {
        try {
            val jsonArray = JSONArray(jsonString)
            val tempTypes = mutableListOf<DocumentTypeDefinition>()

            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)

                val type = json.getString("type")

                val keywordsArray = json.getJSONArray("keywords")
                val keywords = mutableListOf<String>()
                for (j in 0 until keywordsArray.length()) {
                    keywords.add(keywordsArray.getString(j))
                }

                val fieldsArray = json.getJSONArray("fields")
                val fields = mutableListOf<String>()
                for (j in 0 until fieldsArray.length()) {
                    fields.add(fieldsArray.getString(j))
                }

                // Determinar el tipo de documento para SmartLens
                val documentType = when {
                    type.contains("Guía") || type.contains("Despacho") -> DocumentType.DELIVERY_NOTE
                    type.contains("Etiqueta") -> DocumentType.WAREHOUSE_LABEL
                    else -> DocumentType.INVOICE // Por defecto, la mayoría son del tipo factura
                }

                tempTypes.add(
                    DocumentTypeDefinition(
                        type = type,
                        keywords = keywords,
                        fields = fields,
                        documentType = documentType
                    )
                )
            }

            // Reemplazar las definiciones existentes si la carga fue exitosa
            if (tempTypes.isNotEmpty()) {
                Log.d(TAG, "Cargadas ${tempTypes.size} definiciones de tipos de documentos")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al cargar definiciones desde JSON: ${e.message}", e)
        }
    }

    /**
     * Obtiene las definiciones actuales como JSON
     */
    fun getDefinitionsAsJson(): String {
        val jsonArray = JSONArray()

        documentTypes.forEach { docType ->
            val json = JSONObject()
            json.put("type", docType.type)

            val keywordsArray = JSONArray()
            docType.keywords.forEach { keywordsArray.put(it) }
            json.put("keywords", keywordsArray)

            val fieldsArray = JSONArray()
            docType.fields.forEach { fieldsArray.put(it) }
            json.put("fields", fieldsArray)

            jsonArray.put(json)
        }

        return jsonArray.toString(2)
    }

    /**
     * Clase para definir un tipo de documento con sus palabras clave y campos
     */
    data class DocumentTypeDefinition(
        val type: String,
        val keywords: List<String>,
        val fields: List<String>,
        val documentType: DocumentType
    )
}