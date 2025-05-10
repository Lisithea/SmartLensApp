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
 * Servicio para el procesamiento inteligente de imágenes
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
    private var openCVInitialized = false

    init {
        // Intentar inicializar OpenCV de forma segura
        try {
            // Verificar si OpenCV ya está inicializado (por SmartLensApplication)
            if (isOpenCVInitialized()) {
                openCVInitialized = true
                Log.d(TAG, "OpenCV ya estaba inicializado")
            } else {
                // Intentar inicializar OpenCV
                openCVInitialized = OpenCVLoader.initDebug()
                if (openCVInitialized) {
                    Log.d(TAG, "OpenCV inicializado correctamente")
                } else {
                    Log.e(TAG, "No se pudo inicializar OpenCV")
                    // No lanzar excepción, permitir el uso degradado de la app
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar OpenCV: ${e.message}", e)
            openCVInitialized = false
        }
    }

    /**
     * Verifica si OpenCV ya está inicializado
     */
    private fun isOpenCVInitialized(): Boolean {
        return try {
            // Intentar hacer una operación básica con OpenCV
            val mat = Mat(1, 1, CvType.CV_8UC1)
            mat.release()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Procesa una imagen desde su URI siguiendo el flujo completo:
     * 1. Procesamiento de imagen (corrección, recorte, mejora) si OpenCV está disponible
     * 2. OCR sobre la imagen procesada
     * 3. Análisis con IA del texto extraído
     * 4. Generación de documento estructurado
     */
    suspend fun processDocumentImage(
        imageUri: Uri,
        documentType: DocumentType? = null
    ): LogisticsDocument = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando procesamiento completo de imagen: $imageUri")

            // FASE 1: Procesamiento de imagen - Solo si OpenCV está inicializado
            val processedImageUri = if (openCVInitialized) {
                try {
                    enhanceImageWithComputerVision(imageUri)
                } catch (e: Exception) {
                    Log.e(TAG, "Error en procesamiento con OpenCV, usando imagen original: ${e.message}", e)
                    imageUri
                }
            } else {
                Log.d(TAG, "OpenCV no disponible, usando imagen original")
                imageUri
            }

            Log.d(TAG, "Imagen a procesar: $processedImageUri")

            // FASE 2.1: OCR sobre la imagen procesada
            val extractedText = ocrService.extractTextFromUri(processedImageUri)
            if (extractedText.isBlank()) {
                throw IllegalStateException("No se pudo extraer texto de la imagen")
            }
            Log.d(TAG, "Texto extraído (${extractedText.length} caracteres)")

            // FASE 2.2: Detectar tipo de documento si no se especificó
            val detectedType = documentType ?: ocrService.detectDocumentType(extractedText)
            Log.d(TAG, "Tipo de documento detectado: $detectedType")

            // FASE 3: Procesamiento con IA para extraer datos estructurados
            val document = when (detectedType) {
                DocumentType.INVOICE -> geminiService.processInvoice(extractedText, processedImageUri)
                DocumentType.DELIVERY_NOTE -> geminiService.processDeliveryNote(extractedText, processedImageUri)
                DocumentType.WAREHOUSE_LABEL -> geminiService.processWarehouseLabel(extractedText, processedImageUri)
                else -> geminiService.processInvoice(extractedText, processedImageUri) // Intentar como factura por defecto
            }

            return@withContext document
        } catch (e: Exception) {
            Log.e(TAG, "Error en el procesamiento completo: ${e.message}", e)
            throw e
        }
    }

    /**
     * Mejora la imagen usando técnicas de Computer Vision con OpenCV
     * 1. Detecta bordes y contornos
     * 2. Intenta corregir perspectiva si se detectan cuatro bordes claros
     * 3. Mejora contraste y legibilidad
     */
    suspend fun enhanceImageWithComputerVision(imageUri: Uri): Uri = withContext(Dispatchers.IO) {
        if (!openCVInitialized) {
            Log.w(TAG, "OpenCV no inicializado, devolviendo imagen original")
            return@withContext imageUri
        }

        try {
            // Abrir la imagen desde la URI
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: throw IllegalArgumentException("No se pudo abrir la imagen")

            // Decodificar el bitmap
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1 // No reducir resolución
            }
            val originalBitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            if (originalBitmap == null) {
                Log.e(TAG, "No se pudo decodificar la imagen")
                return@withContext imageUri
            }

            // Convertir a formato OpenCV (Mat)
            val srcMat = Mat()
            Utils.bitmapToMat(originalBitmap, srcMat)

            // 1. Convertir a escala de grises
            val grayMat = Mat()
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGR2GRAY)

            // 2. Aplicar desenfoque para reducir ruido
            Imgproc.GaussianBlur(grayMat, grayMat, Size(5.0, 5.0), 0.0)

            // 3. Detección de bordes
            val edgesMat = Mat()
            Imgproc.Canny(grayMat, edgesMat, 75.0, 200.0)

            // 4. Dilatar bordes para mejorar contornos
            val dilatedEdges = Mat()
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(edgesMat, dilatedEdges, kernel)

            // 5. Encontrar contornos - Usar clone() para evitar modificar la original
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            val tempMat = dilatedEdges.clone()
            Imgproc.findContours(
                tempMat,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )
            tempMat.release() // Liberar la matriz temporal

            // Procesar el resultado según si se encontraron contornos adecuados
            val resultBitmap = processContours(srcMat, contours, originalBitmap.width, originalBitmap.height)

            // Liberar recursos de OpenCV
            srcMat.release()
            grayMat.release()
            edgesMat.release()
            dilatedEdges.release()
            hierarchy.release()
            contours.forEach { it.release() }

            // Guardar la imagen procesada
            return@withContext saveProcessedImage(resultBitmap, "processed")

        } catch (e: Exception) {
            Log.e(TAG, "Error en enhanceImageWithComputerVision: ${e.message}", e)
            return@withContext imageUri
        }
    }

    /**
     * Procesa los contornos encontrados para mejorar la imagen
     */
    private fun processContours(srcMat: Mat, contours: List<MatOfPoint>, originalWidth: Int, originalHeight: Int): Bitmap {
        // Si no hay contornos, aplicar solo mejoras básicas
        if (contours.isEmpty()) {
            Log.d(TAG, "No se detectaron contornos, aplicando solo mejoras básicas")
            return applyBasicEnhancements(srcMat)
        }

        // Encontrar el contorno más grande (presumiblemente el documento)
        var maxArea = 0.0
        var maxContourIndex = -1

        for (i in contours.indices) {
            val area = Imgproc.contourArea(contours[i])
            if (area > maxArea) {
                maxArea = area
                maxContourIndex = i
            }
        }

        // Si el contorno es muy pequeño o no existe, usar solo mejoras básicas
        if (maxContourIndex == -1 || maxArea < (originalWidth * originalHeight * 0.1)) {
            Log.d(TAG, "Contorno demasiado pequeño (área: $maxArea), aplicando solo mejoras básicas")
            return applyBasicEnhancements(srcMat)
        }

        try {
            // Aproximar el contorno a un polígono
            val maxContour = contours[maxContourIndex]
            val contour2f = MatOfPoint2f()
            maxContour.convertTo(contour2f, CvType.CV_32FC2)
            val approxCurve = MatOfPoint2f()
            val epsilon = 0.02 * Imgproc.arcLength(contour2f, true)
            Imgproc.approxPolyDP(contour2f, approxCurve, epsilon, true)

            // Verificar si tenemos 4 puntos (un rectángulo)
            val points = approxCurve.toArray()

            if (points.size == 4) {
                Log.d(TAG, "Detectadas 4 esquinas, aplicando corrección de perspectiva")
                val result = applyPerspectiveCorrection(srcMat, points)
                contour2f.release()
                approxCurve.release()
                return result
            } else {
                Log.d(TAG, "No se detectaron exactamente 4 esquinas (${points.size}), aplicando solo mejoras básicas")
                contour2f.release()
                approxCurve.release()
                return applyBasicEnhancements(srcMat)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar contornos: ${e.message}", e)
            return applyBasicEnhancements(srcMat)
        }
    }

    /**
     * Aplica corrección de perspectiva si se detectan las cuatro esquinas
     */
    private fun applyPerspectiveCorrection(srcMat: Mat, points: Array<Point>): Bitmap {
        // Ordenar puntos (superior-izquierda, superior-derecha, inferior-derecha, inferior-izquierda)
        val sortedPoints = sortPoints(points)

        // Calcular dimensiones para la imagen transformada
        val width = maxOf(
            distance(sortedPoints[0], sortedPoints[1]),
            distance(sortedPoints[2], sortedPoints[3])
        ).toInt()

        val height = maxOf(
            distance(sortedPoints[0], sortedPoints[3]),
            distance(sortedPoints[1], sortedPoints[2])
        ).toInt()

        // Evitar dimensiones extremas
        if (width <= 0 || height <= 0 || width > 5000 || height > 5000) {
            Log.w(TAG, "Dimensiones inválidas calculadas: $width x $height, usando mejoras básicas")
            return applyBasicEnhancements(srcMat)
        }

        // Definir puntos destino para la transformación
        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(width.toDouble(), 0.0),
            Point(width.toDouble(), height.toDouble()),
            Point(0.0, height.toDouble())
        )

        // Aplicar la transformación de perspectiva
        val srcPoints = MatOfPoint2f(*sortedPoints)
        val perspectiveTransform = Imgproc.getPerspectiveTransform(srcPoints, dst)
        val correctedMat = Mat()
        Imgproc.warpPerspective(srcMat, correctedMat, perspectiveTransform, Size(width.toDouble(), height.toDouble()))

        // Aplicar mejoras adicionales al resultado
        val enhancedMat = applyEnhancementsToMat(correctedMat)

        // Convertir a bitmap
        val resultBitmap = Bitmap.createBitmap(enhancedMat.cols(), enhancedMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(enhancedMat, resultBitmap)

        // Liberar recursos
        correctedMat.release()
        enhancedMat.release()
        perspectiveTransform.release()
        srcPoints.release()
        dst.release()

        return resultBitmap
    }

    /**
     * Aplica mejoras básicas a la imagen cuando no se puede hacer corrección de perspectiva
     */
    private fun applyBasicEnhancements(srcMat: Mat): Bitmap {
        val enhancedMat = applyEnhancementsToMat(srcMat)

        // Convertir a bitmap
        val resultBitmap = Bitmap.createBitmap(enhancedMat.cols(), enhancedMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(enhancedMat, resultBitmap)

        enhancedMat.release()

        return resultBitmap
    }

    /**
     * Aplica mejoras de imagen (contraste, nitidez) a una Mat
     */
    private fun applyEnhancementsToMat(inputMat: Mat): Mat {
        val enhancedMat = Mat()

        // Convertir a escala de grises si no lo está
        if (inputMat.channels() > 1) {
            val grayMat = Mat()
            Imgproc.cvtColor(inputMat, grayMat, Imgproc.COLOR_BGR2GRAY)

            // Aplicar ecualización adaptativa de histograma (CLAHE)
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(grayMat, grayMat)

            // Aplicar umbral adaptativo para mejorar el texto
            Imgproc.adaptiveThreshold(
                grayMat,
                enhancedMat,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                11,
                2.0
            )

            grayMat.release()
        } else {
            // Ya está en escala de grises
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(inputMat, enhancedMat)

            // Aplicar umbral adaptativo
            Imgproc.adaptiveThreshold(
                enhancedMat,
                enhancedMat,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                11,
                2.0
            )
        }

        return enhancedMat
    }

    /**
     * Ordena los puntos en el orden: superior-izquierda, superior-derecha, inferior-derecha, inferior-izquierda
     */
    private fun sortPoints(points: Array<Point>): Array<Point> {
        val result = arrayOfNulls<Point>(4)

        // Calcular la suma y diferencia de coordenadas
        val sumPoints = points.map { it.x + it.y }
        val diffPoints = points.map { it.x - it.y }

        // Superior izquierda: punto con menor suma
        result[0] = points[sumPoints.indexOf(sumPoints.minOrNull())]

        // Inferior derecha: punto con mayor suma
        result[2] = points[sumPoints.indexOf(sumPoints.maxOrNull())]

        // Superior derecha: punto con mayor diferencia
        result[1] = points[diffPoints.indexOf(diffPoints.maxOrNull())]

        // Inferior izquierda: punto con menor diferencia
        result[3] = points[diffPoints.indexOf(diffPoints.minOrNull())]

        @Suppress("UNCHECKED_CAST")
        return result as Array<Point>
    }

    /**
     * Calcula la distancia euclidiana entre dos puntos
     */
    private fun distance(p1: Point, p2: Point): Double {
        return Math.sqrt(Math.pow(p2.x - p1.x, 2.0) + Math.pow(p2.y - p1.y, 2.0))
    }

    /**
     * Guarda la imagen procesada en el almacenamiento interno
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
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        return outputFile.toUri()
    }
}