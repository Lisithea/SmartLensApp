package com.example.smartlens.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.example.smartlens.model.DocumentType
import com.example.smartlens.service.OcrService
import java.io.File
import java.io.FileOutputStream

/**
 * Clase utilitaria para probar el OCR con imágenes de ejemplo
 * Version modificada sin inyección de dependencias
 */
class OcrTester(
    private val context: Context,
    private val ocrService: OcrService
) {
    private val TAG = "OcrTester"

    /**
     * Crea una imagen de ejemplo con texto para probar OCR
     */
    suspend fun testOcrWithSampleImage(): String {
        try {
            // Crear una imagen de ejemplo para factura
            val sampleImageUri = createSampleInvoiceImage()

            // Probar OCR en la imagen
            Log.d(TAG, "Iniciando prueba de OCR con imagen de muestra: $sampleImageUri")

            // Intentar primero con preprocesamiento
            val processingResult = runCatching {
                val processedBitmap = ocrService.preprocessImageForOcr(sampleImageUri)
                ocrService.extractText(processedBitmap)
            }

            // Si falla, probar directamente
            val extractedText = processingResult.getOrElse {
                Log.e(TAG, "Error en extracción con preprocesamiento: ${it.message}", it)
                ocrService.extractTextFromUri(sampleImageUri)
            }

            // Detectar tipo de documento
            val detectedType = ocrService.detectDocumentType(extractedText)

            Log.d(TAG, "Texto extraído (${extractedText.length} caracteres): ${extractedText.take(100)}...")
            Log.d(TAG, "Tipo de documento detectado: $detectedType")

            return "Texto extraído: ${extractedText.length} caracteres\n" +
                    "Fragmento: ${extractedText.take(100)}...\n" +
                    "Tipo detectado: $detectedType"

        } catch (e: Exception) {
            Log.e(TAG, "Error en prueba de OCR: ${e.message}", e)
            return "Error: ${e.message}"
        }
    }

    /**
     * Crea una imagen de factura de ejemplo con texto
     */
    private fun createSampleInvoiceImage(): Uri {
        val file = File(context.filesDir, "sample_invoice_test.jpg")

        // Si ya existe, devolver la URI
        if (file.exists()) {
            return file.toUri()
        }

        // Crear una imagen con texto de factura
        val bitmap = Bitmap.createBitmap(800, 1200, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Fondo blanco
        canvas.drawColor(android.graphics.Color.WHITE)

        // Configurar texto
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 30f
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }

        // Dibujar texto de factura
        val invoiceText = """
            FACTURA
            
            Número: F-2023-001
            Fecha: 01/05/2023
            
            PROVEEDOR:
            Suministros TechPro S.L.
            NIF: B12345678
            Calle Principal 123
            28001 Madrid
            
            CLIENTE:
            SmartLens Innovations
            NIF: A87654321
            Avenida Tecnología 45
            08001 Barcelona
            
            CONCEPTO                CANTIDAD   PRECIO    TOTAL
            ----------------------------------------------
            Servicio de OCR           1        500,00€   500,00€
            Licencia de Software      2        150,00€   300,00€
            Soporte Técnico           5        100,00€   500,00€
            
            Subtotal:                                  1.300,00€
            IVA (21%):                                   273,00€
            TOTAL:                                     1.573,00€
        """.trimIndent()

        // Dividir el texto en líneas y dibujarlo
        val lines = invoiceText.split("\n")
        var y = 100f
        lines.forEach { line ->
            canvas.drawText(line, 50f, y, paint)
            y += 40f
        }

        // Guardar la imagen
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        return file.toUri()
    }

    /**
     * Ejecuta una prueba completa del procesamiento de documentos
     */
    suspend fun runFullTestWithImages(): String {
        try {
            // Crear imágenes de muestra para cada tipo
            val invoiceUri = createSampleInvoiceImage()

            val results = StringBuilder()

            // Probar OCR en la factura
            results.append("=== PRUEBA DE FACTURA ===\n")
            val invoiceText = ocrService.extractTextFromUri(invoiceUri)
            val invoiceType = ocrService.detectDocumentType(invoiceText)
            results.append("Texto extraído: ${invoiceText.length} caracteres\n")
            results.append("Tipo detectado: $invoiceType\n\n")

            // Verificar detección
            val detectionSuccess = invoiceType == DocumentType.INVOICE
            results.append("Detección correcta: $detectionSuccess\n\n")

            // Análisis de resultados
            results.append("Análisis:\n")
            if (invoiceText.length < 10) {
                results.append("❌ El OCR no extrajo suficiente texto\n")
            } else {
                results.append("✅ El OCR extrajo texto correctamente\n")

                if (detectionSuccess) {
                    results.append("✅ El tipo de documento se detectó correctamente\n")
                } else {
                    results.append("❌ El tipo de documento no se detectó correctamente\n")
                }
            }

            return results.toString()

        } catch (e: Exception) {
            Log.e(TAG, "Error en prueba completa: ${e.message}", e)
            return "Error: ${e.message}"
        }
    }
}