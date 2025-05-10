package com.example.smartlens.util

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Clase para facilitar la inicialización y comprobación de OpenCV
 * Usa reflexión para evitar errores de compilación si OpenCV no está correctamente configurado
 */
object OpenCVInitializer {
    private const val TAG = "OpenCVInitializer"
    private val initialized = AtomicBoolean(false)
    private val initializing = AtomicBoolean(false)

    /**
     * Verifica si OpenCV está inicializado
     */
    fun isInitialized(): Boolean {
        return initialized.get()
    }

    /**
     * Intenta inicializar OpenCV sincronamente usando reflexión
     * @return true si la inicialización fue exitosa
     */
    fun initSync(): Boolean {
        if (initialized.get()) return true
        if (initializing.getAndSet(true)) return false

        try {
            // Obtener la clase OpenCVLoader mediante reflexión
            val openCVLoaderClass = Class.forName("org.opencv.android.OpenCVLoader")

            // Llamar al método initDebug mediante reflexión
            val initDebugMethod = openCVLoaderClass.getMethod("initDebug")
            val success = initDebugMethod.invoke(null) as Boolean

            if (success) {
                Log.d(TAG, "OpenCV inicializado correctamente (estático)")
                initialized.set(true)
                initializing.set(false)
                return true
            } else {
                Log.e(TAG, "Error en la inicialización estática de OpenCV")
                initializing.set(false)
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al inicializar OpenCV: ${e.message}", e)
            initializing.set(false)
            return false
        }
    }

    /**
     * Inicializa OpenCV asincrónicamente mediante reflexión
     */
    fun initAsync(context: Context, callback: ((Boolean) -> Unit)? = null) {
        if (initialized.get()) {
            callback?.invoke(true)
            return
        }

        if (initializing.getAndSet(true)) {
            callback?.invoke(false)
            return
        }

        try {
            // Verificar que los archivos de biblioteca existen
            val libraryCheck = checkOpenCVLibraries(context)
            if (!libraryCheck) {
                Log.e(TAG, "Bibliotecas nativas de OpenCV no encontradas")
                initializing.set(false)
                callback?.invoke(false)
                return
            }

            // Usar reflexión para acceder a OpenCV
            val openCVLoaderClass = Class.forName("org.opencv.android.OpenCVLoader")

            // Crear un objeto LoaderCallbackInterface mediante una clase proxy
            val loaderCallback = object : Any() {
                // Este método será llamado por OpenCV mediante reflexión
                fun onManagerConnected(status: Int) {
                    when (status) {
                        // LoaderCallbackInterface.SUCCESS = 0
                        0 -> {
                            Log.d(TAG, "OpenCV inicializado con éxito (asíncrono)")
                            initialized.set(true)
                            initializing.set(false)
                            callback?.invoke(true)
                        }
                        else -> {
                            Log.e(TAG, "Error en la inicialización asíncrona de OpenCV: $status")
                            initializing.set(false)
                            callback?.invoke(false)
                        }
                    }
                }
            }

            // Intentar la inicialización asíncrona mediante reflexión
            try {
                // Obtener la versión de OpenCV (típicamente "4.8.0")
                val versionField = openCVLoaderClass.getField("OPENCV_VERSION")
                val version = versionField.get(null) as String

                // Llamar al método initAsync
                val initAsyncMethod = openCVLoaderClass.getMethod(
                    "initAsync",
                    String::class.java,
                    Context::class.java,
                    Class.forName("org.opencv.android.LoaderCallbackInterface")
                )

                // Esta parte podría fallar si la interfaz de callback no es accesible
                // Alternativa: usar una implementación directa en tu módulo OpenCV
                val result = initAsyncMethod.invoke(null, version, context, loaderCallback) as Boolean

                if (!result) {
                    Log.e(TAG, "Error al iniciar la carga asíncrona de OpenCV")
                    initializing.set(false)
                    callback?.invoke(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo invocar la inicialización asíncrona: ${e.message}")

                // Intento alternativo con inicialización síncrona
                val success = initSync()
                initializing.set(false)
                callback?.invoke(success)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Excepción en la inicialización asíncrona: ${e.message}", e)
            initializing.set(false)
            callback?.invoke(false)
        }
    }

    /**
     * Verifica que las bibliotecas nativas de OpenCV existen en el dispositivo
     */
    private fun checkOpenCVLibraries(context: Context): Boolean {
        val libraryDir = File(context.applicationInfo.nativeLibraryDir)
        if (!libraryDir.exists() || !libraryDir.isDirectory) {
            Log.e(TAG, "Directorio de bibliotecas nativas no encontrado: ${libraryDir.absolutePath}")
            return false
        }

        // Buscar la biblioteca principal de OpenCV (libopencv_java4.so o similar)
        val openCVLibrary = libraryDir.listFiles()?.find {
            it.name.contains("opencv") || it.name.contains("libopencv_java")
        }

        if (openCVLibrary == null) {
            Log.e(TAG, "Biblioteca de OpenCV no encontrada en ${libraryDir.absolutePath}")
            return false
        }

        Log.d(TAG, "Biblioteca OpenCV encontrada: ${openCVLibrary.absolutePath}")
        return true
    }

    /**
     * Registra información sobre el entorno para diagnóstico
     */
    fun logEnvironmentInfo(context: Context) {
        try {
            Log.d(TAG, "--- Información del entorno OpenCV ---")
            Log.d(TAG, "Directorio de bibliotecas nativas: ${context.applicationInfo.nativeLibraryDir}")

            val libraryDir = File(context.applicationInfo.nativeLibraryDir)
            if (libraryDir.exists() && libraryDir.isDirectory) {
                val libraries = libraryDir.listFiles()
                Log.d(TAG, "Bibliotecas disponibles (${libraries?.size ?: 0}):")
                libraries?.forEach { lib ->
                    Log.d(TAG, " - ${lib.name} (${lib.length()} bytes)")
                }
            } else {
                Log.d(TAG, "Directorio de bibliotecas no encontrado o no es un directorio")
            }

            // Intentar obtener la versión de OpenCV mediante reflexión
            try {
                val openCVLoaderClass = Class.forName("org.opencv.android.OpenCVLoader")
                val versionField = openCVLoaderClass.getField("OPENCV_VERSION")
                val version = versionField.get(null) as String
                Log.d(TAG, "Versión de OpenCV solicitada: $version")
            } catch (e: Exception) {
                Log.d(TAG, "No se pudo obtener la versión de OpenCV: ${e.message}")
            }

            Log.d(TAG, "Estado de inicialización: ${initialized.get()}")
            Log.d(TAG, "----------------------------------")
        } catch (e: Exception) {
            Log.e(TAG, "Error al registrar información del entorno: ${e.message}")
        }
    }
}