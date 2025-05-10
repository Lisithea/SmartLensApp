package com.example.smartlens

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.multidex.MultiDex
import androidx.work.Configuration
import com.example.smartlens.util.LanguageHelper
import dagger.hilt.android.HiltAndroidApp
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import javax.inject.Inject

@HiltAndroidApp
class SmartLensApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val TAG = "SmartLensApplication"

    // Callback para la inicialización de OpenCV
    private val mLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.i(TAG, "OpenCV cargado correctamente")
                }
                else -> {
                    Log.e(TAG, "No se pudo inicializar OpenCV: $status")
                    super.onManagerConnected(status)
                }
            }
        }
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }

    override fun attachBaseContext(base: Context) {
        // Aplicar el idioma guardado al iniciar la aplicación
        val language = LanguageHelper.getCurrentLanguage(base)
        val context = LanguageHelper.updateLanguage(base, language)
        super.attachBaseContext(context)

        // Inicializar MultiDex para manejar muchos métodos
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Inicializar OpenCV
        initOpenCV()

        // Aplicar también el idioma al contexto de la aplicación
        val language = LanguageHelper.getCurrentLanguage(this)
        LanguageHelper.updateLanguage(this, language)
    }

    /**
     * Inicializa OpenCV utilizando diferentes métodos para maximizar la compatibilidad
     */
    private fun initOpenCV() {
        try {
            // Primero intentamos cargarlo estáticamente
            if (!OpenCVLoader.initDebug()) {
                Log.d(TAG, "Inicialización estática de OpenCV falló, intentando carga asíncrona")

                // Si la inicialización estática falla, intentamos la carga asíncrona
                OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback)
            } else {
                Log.d(TAG, "Inicialización estática de OpenCV exitosa")
                mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar OpenCV: ${e.message}", e)
            // Intentamos un tercer método
            try {
                // Intentar cargar explícitamente la biblioteca nativa de OpenCV
                System.loadLibrary("opencv_java4")
                Log.d(TAG, "Biblioteca opencv_java4 cargada exitosamente mediante loadLibrary")
                mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
            } catch (e2: Exception) {
                Log.e(TAG, "Error al cargar opencv_java4 manualmente: ${e2.message}", e2)
            }
        }
    }}