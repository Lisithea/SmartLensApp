package com.example.smartlens.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Clase de utilidad para gestionar el idioma de la aplicación
 */
object LanguageHelper {
    private const val TAG = "LanguageHelper"
    private const val PREF_NAME = "smartlens_language_settings"
    private const val KEY_SELECTED_LANGUAGE = "selected_language"

    // Estado del idioma actual
    private val _currentLanguage = MutableStateFlow("español")
    val currentLanguage: StateFlow<String> = _currentLanguage

    // Lista de idiomas soportados
    val supportedLanguages = listOf(
        "español",
        "inglés",
        "francés",
        "alemán",
        "italiano",
        "portugués",
        "catalán"
    )

    // Mapeo de nombres a códigos ISO
    private val languageCodes = mapOf(
        "español" to "es",
        "inglés" to "en",
        "francés" to "fr",
        "alemán" to "de",
        "italiano" to "it",
        "portugués" to "pt",
        "catalán" to "ca"
    )

    // Mapeo de códigos a emojis de banderas
    private val languageFlags = mapOf(
        "es" to "🇪🇸",
        "en" to "🇬🇧",
        "fr" to "🇫🇷",
        "de" to "🇩🇪",
        "it" to "🇮🇹",
        "pt" to "🇵🇹",
        "ca" to "🏳️"
    )

    /**
     * Inicializa el helper con el contexto de la aplicación
     */
    fun init(context: Context) {
        val savedLanguage = getSavedLanguage(context)
        _currentLanguage.value = savedLanguage

        // Aplicar idioma guardado
        val locale = getLocaleFromLanguageName(savedLanguage)
        updateLocale(context, locale)

        Log.d(TAG, "LanguageHelper inicializado con idioma: $savedLanguage")
    }

    /**
     * Actualiza el idioma para toda la aplicación
     * Retorna true si se cambió el idioma y es necesario recrear las actividades
     */
    fun setLanguage(context: Context, languageName: String): Boolean {
        if (_currentLanguage.value == languageName) {
            return false // No hay cambio
        }

        Log.d(TAG, "Cambiando idioma de ${_currentLanguage.value} a $languageName")

        // Guardar en preferencias
        saveLanguagePreference(context, languageName)

        // Actualizar estado
        _currentLanguage.value = languageName

        // Actualizar locale
        val locale = getLocaleFromLanguageName(languageName)
        updateLocale(context, locale)

        return true // Idioma cambiado, se debe recrear la actividad
    }

    /**
     * Obtiene el idioma guardado en preferencias
     */
    private fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SELECTED_LANGUAGE, "español") ?: "español"
    }

    /**
     * Guarda el idioma en preferencias
     */
    private fun saveLanguagePreference(context: Context, languageName: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_LANGUAGE, languageName).apply()
    }

    /**
     * Obtiene el emoji de bandera para un idioma
     */
    fun getFlagForLanguage(languageName: String): String {
        val code = languageCodes[languageName.lowercase()] ?: "es"
        return languageFlags[code] ?: "🏳️"
    }

    /**
     * Actualiza el idioma para toda la aplicación
     */
    fun updateLanguage(context: Context, languageName: String): Context {
        val locale = getLocaleFromLanguageName(languageName)
        return updateResources(context, locale)
    }

    /**
     * Obtiene la configuración de idioma actual guardada en las preferencias
     */
    fun getCurrentLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SELECTED_LANGUAGE, "español") ?: "español"
    }

    /**
     * Convierte un nombre de idioma en un objeto Locale
     */
    private fun getLocaleFromLanguageName(languageName: String): Locale {
        val code = languageCodes[languageName.lowercase()] ?: "es"
        return when (code) {
            "es" -> Locale("es", "ES")
            "en" -> Locale("en", "GB")
            "fr" -> Locale("fr", "FR")
            "de" -> Locale("de", "DE")
            "it" -> Locale("it", "IT")
            "pt" -> Locale("pt", "PT")
            "ca" -> Locale("ca", "ES")
            else -> Locale("es", "ES") // Español por defecto
        }
    }

    /**
     * Actualiza el locale del sistema
     */
    private fun updateLocale(context: Context, locale: Locale) {
        Locale.setDefault(locale)

        val res = context.resources
        val config = Configuration(res.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            config.locale = locale
        }

        res.updateConfiguration(config, res.displayMetrics)
    }

    /**
     * Actualiza los recursos del contexto con el nuevo idioma
     */
    private fun updateResources(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)

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