package com.example.smartlens.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.example.smartlens.model.DocumentType
import com.example.smartlens.model.LogisticsDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Servicio para el procesamiento inteligente de imágenes con OpenCV y OCR
 * Implementa el flujo completo desde la captura hasta el procesamiento avanzado
 */
@Singleton
class ImageProcessingService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ocrService: OcrService,
    private val geminiService: GeminiService,
    private val excelExportService: ExcelExportService
) {
    private val TAG = "ImageProcessingService"

    init {
        // Inicializar OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "No se pudo inicializar OpenCV")
        } else {
            Log.d(TAG, "OpenCV inicializado correctamente")
        }
    }

    /**
     * Procesa una imagen desde su URI siguiendo el flujo completo:
     * 1. Procesamiento de imagen con CV (corrección, recorte, mejora)
     * 2. OCR sobre la imagen procesada
     * 3. Análisis con IA del texto extraído
     * 4. Generación de documento estructurado
     *
     * @param imageUri URI de la imagen a procesar
     * @param documentType Tipo de documento si ya se conoce, o null para detectarlo automáticamente
     * @return El documento procesado con toda la información extraída
     */
    suspend fun processDocumentImage(
        imageUri: Uri,
        documentType: DocumentType? = null
    ): LogisticsDocument = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando procesamiento completo de imagen: $imageUri")

            // FASE 1: Procesamiento de imagen
            val processedImageUri = enhanceImageWithComputerVision(imageUri)
            Log.d(TAG, "Imagen procesada con CV: $processedImageUri")

            // FASE 2.1: OCR sobre la imagen procesada
            val extractedText = ocrService.extractTextFromUri(processedImageUri)
            if (extractedText.isBlank()) {
                throw IllegalStateException("No se pudo extraer texto de la imagen")
            }
            Log.d(TAG, "Texto extraído (${extractedText.length} caracteres)")

            // FASE 2.2: Detectar tipo de documento si no se especificó
            val detectedType = documentType ?: ocrService.detectDocumentType(extractedText)
            Log.d(TAG, "Tipo de documento detectado: $detectedType")

            // FASE 2.3: Procesamiento con IA para extraer datos estructurados
            val document = when (detectedType) {
                DocumentType.INVOICE -> geminiService.processInvoice(extractedText, processedImageUri)
                DocumentType.DELIVERY_NOTE -> geminiService.processDeliveryNote(extractedText, processedImageUri)
                DocumentType.WAREHOUSE_LABEL -> geminiService.processWarehouseLabel(extractedText, processedImageUri)
                else -> geminiService.processInvoice(extractedText, processedImageUri) // Por defecto intentamos como factura
            }

            // FASE 2.4: Generar Excel (se hace bajo demanda en ExportScreen)

            return@withContext document
        } catch (e: Exception) {
            Log.e(TAG, "Error en el procesamiento completo: ${e.message}", e)
            throw e
        }
    }

    /**
     * Mejora la imagen usando técnicas de Computer Vision con OpenCV
     * 1. Detecta bordes y contornos
     * 2. Corrige perspectiva
     * 3. Recorta al área del documento
     * 4. Mejora contraste y legibilidad
     * 5. Guarda la imagen procesada
     *
     * @param imageUri URI de la imagen original
     * @return URI de la imagen procesada
     */
    suspend fun enhanceImageWithComputerVision(imageUri: Uri): Uri = withContext(Dispatchers.IO) {
        try {
            // Abrir la imagen desde la URI
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: throw IllegalArgumentException("No se pudo abrir la imagen")

            // Decodificar el bitmap
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Convertir a formato OpenCV (Mat)
            val srcMat = Mat()
            Utils.bitmapToMat(originalBitmap, srcMat)

            // 1. Convertir a escala de grises
            val grayMat = Mat()
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGR2GRAY)

            // 2. Aplicar desenfoque gaussiano para reducir ruido
            Imgproc.GaussianBlur(grayMat, grayMat, Size(5.0, 5.0), 0.0)

            // 3. Detección de bordes con Canny
            val edgesMat = Mat()
            Imgproc.Canny(grayMat, edgesMat, 75.0, 200.0)

            // 4. Dilatar bordes para mejorar contornos
            val dilatedEdges = Mat()
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(edgesMat, dilatedEdges, kernel)

            // 5. Encontrar contornos
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                dilatedEdges,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            // 6. Encontrar el contorno más grande (presumiblemente el documento)
            var maxArea = 0.0
            var maxContourIndex = -1

            for (i in contours.indices) {
                val area = Imgproc.contourArea(contours[i])
                if (area > maxArea) {
                    maxArea = area
                    maxContourIndex = i
                }
            }

            // Si no se encontraron contornos adecuados, usar la imagen original
            if (maxContourIndex == -1 || contours.isEmpty()) {
                Log.w(TAG, "No se detectaron contornos. Usando imagen original.")
                return@withContext saveProcessedImage(originalBitmap, "original")
            }

            // 7. Aproximar el contorno a un polígono (para obtener las esquinas)
            val maxContour = contours[maxContourIndex]
            val approxCurve = MatOfPoint2f()
            val contour2f = MatOfPoint2f()
            maxContour.convertTo(contour2f, CvType.CV_32FC2)

            // Aproximar el contorno con precisión proporcional al perímetro
            val epsilon = 0.02 * Imgproc.arcLength(contour2f, true)
            Imgproc.approxPolyDP(contour2f, approxCurve, epsilon, true)

            // 8. Extraer las cuatro esquinas para la corrección de perspectiva
            val points = approxCurve.toArray()

            // Si no se encontraron exactamente 4 puntos, aplicar solo otras mejoras
            if (points.size != 4) {
                Log.w(TAG, "No se detectaron las 4 esquinas del documento. Encontradas: ${points.size}")

                // Aplicar mejoras básicas
                val enhancedMat = Mat()
                Imgproc.cvtColor(grayMat, enhancedMat, Imgproc.COLOR_GRAY2BGR)

                // Mejorar contraste usando CLAHE (Contrast Limited Adaptive Histogram Equalization)
                val grayEnhanced = Mat()
                Imgproc.cvtColor(enhancedMat, grayEnhanced, Imgproc.COLOR_BGR2GRAY)
                val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
                clahe.apply(grayEnhanced, grayEnhanced)

                // Convertir de vuelta a BGR
                Imgproc.cvtColor(grayEnhanced, enhancedMat, Imgproc.COLOR_GRAY2BGR)

                // Convertir a bitmap y guardar
                val enhancedBitmap = Bitmap.createBitmap(enhancedMat.cols(), enhancedMat.rows(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(enhancedMat, enhancedBitmap)

                return@withContext saveProcessedImage(enhancedBitmap, "enhanced")
            }

            // Ordenar los puntos correctamente (superior-izquierda, superior-derecha, inferior-derecha, inferior-izquierda)
            val sortedPoints = sortPoints(points)

            // 9. Calcular dimensiones para la imagen transformada
            val width = Math.max(
                distance(sortedPoints[0], sortedPoints[1]),
                distance(sortedPoints[2], sortedPoints[3])
            ).toInt()

            val height = Math.max(
                distance(sortedPoints[0], sortedPoints[3]),
                distance(sortedPoints[1], sortedPoints[2])
            ).toInt()

            // 10. Definir puntos destino para la transformación
            val dst = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(width.toDouble(), 0.0),
                Point(width.toDouble(), height.toDouble()),
                Point(0.0, height.toDouble())
            )

            // 11. Aplicar la transformación de perspectiva
            val srcPoints = MatOfPoint2f(*sortedPoints)
            val perspectiveTransform = Imgproc.getPerspectiveTransform(srcPoints, dst)
            val correctedMat = Mat()
            Imgproc.warpPerspective(srcMat, correctedMat, perspectiveTransform, Size(width.toDouble(), height.toDouble()))

            // 12. Mejorar la imagen para OCR
            // Convertir a escala de grises
            val correctedGray = Mat()
            Imgproc.cvtColor(correctedMat, correctedGray, Imgproc.COLOR_BGR2GRAY)

            // Aplicar umbral adaptativo
            val thresholdMat = Mat()
            Imgproc.adaptiveThreshold(
                correctedGray,
                thresholdMat,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                11,
                2.0
            )

            // 13. Convertir a bitmap y guardar
            val resultBitmap = Bitmap.createBitmap(thresholdMat.cols(), thresholdMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(thresholdMat, resultBitmap)

            return@withContext saveProcessedImage(resultBitmap, "processed")

        } catch (e: Exception) {
            Log.e(TAG, "Error en el procesamiento CV: ${e.message}", e)
            // En caso de error, devolver la imagen original
            return@withContext imageUri
        }
    }

    /**
     * Ordena los puntos en el orden: superior-izquierda, superior-derecha, inferior-derecha, inferior-izquierda
     */
    private fun sortPoints(points: Array<Point>): Array<Point> {
        val sortedPoints = arrayOfNulls<Point>(4)

        // Calcular la suma y diferencia de coordenadas x e y
        val sumCoords = DoubleArray(points.size)
        val diffCoords = DoubleArray(points.size)

        for (i in points.indices) {
            sumCoords[i] = points[i].x + points[i].y
            diffCoords[i] = points[i].x - points[i].y
        }

        // Superior izquierda: punto con menor suma
        var minIndex = 0
        for (i in 1 until sumCoords.size) {
            if (sumCoords[i] < sumCoords[minIndex]) minIndex = i
        }
        sortedPoints[0] = points[minIndex]

        // Inferior derecha: punto con mayor suma
        var maxIndex = 0
        for (i in 1 until sumCoords.size) {
            if (sumCoords[i] > sumCoords[maxIndex]) maxIndex = i
        }
        sortedPoints[2] = points[maxIndex]

        // Superior derecha: punto con mayor diferencia
        maxIndex = 0
        for (i in 1 until diffCoords.size) {
            if (diffCoords[i] > diffCoords[maxIndex]) maxIndex = i
        }
        sortedPoints[1] = points[maxIndex]

        // Inferior izquierda: punto con menor diferencia
        minIndex = 0
        for (i in 1 until diffCoords.size) {
            if (diffCoords[i] < diffCoords[minIndex]) minIndex = i
        }
        sortedPoints[3] = points[minIndex]

        @Suppress("UNCHECKED_CAST")
        return sortedPoints as Array<Point>
    }

    /**
     * Calcula la distancia euclidiana entre dos puntos
     */
    private fun distance(p1: Point, p2: Point): Double {
        return Math.sqrt(Math.pow(p2.x - p1.x, 2.0) + Math.pow(p2.y - p1.y, 2.0))
    }

    /**
     * Guarda la imagen procesada en el almacenamiento interno y devuelve su URI
     */
    private fun saveProcessedImage(bitmap: Bitmap, prefix: String): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "${prefix}_${timestamp}.jpg"
        val outputDir = File(context.filesDir, "processed_images")

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val outputFile = File(outputDir, filename)
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }

        return outputFile.toUri()
    }
}