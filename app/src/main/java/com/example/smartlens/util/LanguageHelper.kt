package com.example.smartlens.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Clase de utilidad para gestionar el idioma de la aplicación
 */
object LanguageHelper {

    /**
     * Actualiza la configuración de idioma para toda la aplicación
     */
    fun updateLanguage(context: Context, languageName: String): Context {
        val locale = getLocaleFromLanguageName(languageName)
        Locale.setDefault(locale)
        return updateResources(context, locale)
    }

    /**
     * Obtiene la configuración de idioma actual guardada en las preferencias
     */
    fun getCurrentLanguage(context: Context): String {
        val prefs = context.getSharedPreferences("smartlens_settings", Context.MODE_PRIVATE)
        return prefs.getString("selected_language", "español") ?: "español"
    }

    /**
     * Convierte un nombre de idioma en un objeto Locale
     */
    private fun getLocaleFromLanguageName(languageName: String): Locale {
        return when (languageName.lowercase()) {
            "español" -> Locale("es")
            "inglés" -> Locale("en")
            "francés" -> Locale("fr")
            "alemán" -> Locale("de")
            "italiano" -> Locale("it")
            "portugués" -> Locale("pt")
            "chino" -> Locale("zh")
            "japonés" -> Locale("ja")
            "coreano" -> Locale("ko")
            "ruso" -> Locale("ru")
            "árabe" -> Locale("ar")
            else -> Locale("es") // Español por defecto
        }
    }

    /**
     * Actualiza los recursos del contexto con el nuevo idioma
     */
    private fun updateResources(context: Context, locale: Locale): Context {
        var updatedContext = context

        val resources = context.resources
        val configuration = Configuration(resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Para Android 7.0 (API 24) y superior
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            configuration.setLocales(localeList)
            updatedContext = context.createConfigurationContext(configuration)
        } else {
            // Para versiones anteriores
            configuration.locale = locale
            resources.updateConfiguration(configuration, resources.displayMetrics)
        }

        return updatedContext
    }
}