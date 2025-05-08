package com.example.smartlens

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.smartlens.util.LanguageHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SmartLensApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

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
    }

    override fun onCreate() {
        super.onCreate()
        // Aplicar también al contexto de la aplicación
        val language = LanguageHelper.getCurrentLanguage(this)
        LanguageHelper.updateLanguage(this, language)
    }
}