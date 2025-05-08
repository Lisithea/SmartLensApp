package com.example.smartlens.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.util.Log
import java.util.Locale

/**
 * Clase de utilidad para gestionar el idioma de la aplicación
 */
object LanguageHelper {

    private const val TAG = "LanguageHelper"

    /**
     * Actualiza la configuración de idioma para toda la aplicación
     */
    fun updateLanguage(context: Context, languageName: String): Context {
        val locale = getLocaleFromLanguageName(languageName)
        Log.d(TAG, "Actualizando idioma a: $languageName (Locale: $locale)")

        // Guardar el idioma seleccionado en preferencias
        val prefs = context.getSharedPreferences("smartlens_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_language", languageName).apply()

        // Establecer el locale por defecto
        Locale.setDefault(locale)

        return updateResources(context, locale)
    }

    /**
     * Obtiene la configuración de idioma actual guardada en las preferencias
     */
    fun getCurrentLanguage(context: Context): String {
        val prefs = context.getSharedPreferences("smartlens_settings", Context.MODE_PRIVATE)
        val language = prefs.getString("selected_language", "español") ?: "español"
        Log.d(TAG, "Idioma actual: $language")
        return language
    }

    /**
     * Convierte un nombre de idioma en un objeto Locale
     */
    private fun getLocaleFromLanguageName(languageName: String): Locale {
        return when (languageName.lowercase()) {
            "español" -> Locale("es", "ES")
            "inglés" -> Locale("en", "US")
            "francés" -> Locale("fr", "FR")
            "alemán" -> Locale("de", "DE")
            "italiano" -> Locale("it", "IT")
            "portugués" -> Locale("pt", "PT")
            "chino" -> Locale("zh", "CN")
            "japonés" -> Locale("ja", "JP")
            "coreano" -> Locale("ko", "KR")
            "ruso" -> Locale("ru", "RU")
            "árabe" -> Locale("ar", "SA")
            else -> {
                Log.w(TAG, "Idioma desconocido: $languageName, usando español por defecto")
                Locale("es", "ES") // Español por defecto
            }
        }
    }

    /**
     * Actualiza los recursos del contexto con el nuevo idioma
     * Implementación actualizada para usar métodos no deprecados cuando sea posible
     */
    private fun updateResources(context: Context, locale: Locale): Context {
        var updatedContext = context

        try {
            val resources = context.resources
            val configuration = Configuration(resources.configuration)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Para Android 7.0 (API 24) y superior
                val localeList = LocaleList(locale)
                LocaleList.setDefault(localeList)
                configuration.setLocales(localeList)
                Log.d(TAG, "Configurando idioma para Android 7.0+: $locale")

                try {
                    updatedContext = context.createConfigurationContext(configuration)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al crear ConfigurationContext: ${e.message}", e)
                    // Si falla la creación del ConfigurationContext, caemos al método antiguo
                    // pero con @Suppress para evitar warnings de deprecation
                    @Suppress("DEPRECATION")
                    configuration.locale = locale
                    @Suppress("DEPRECATION")
                    resources.updateConfiguration(configuration, resources.displayMetrics)
                }
            } else {
                // Para versiones anteriores a Android 7.0
                // Usamos @Suppress para evitar warnings de deprecation
                @Suppress("DEPRECATION")
                configuration.locale = locale
                Log.d(TAG, "Configurando idioma para Android < 7.0: $locale")
                @Suppress("DEPRECATION")
                resources.updateConfiguration(configuration, resources.displayMetrics)
            }

            // Imprimir el idioma configurado para verificar
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Log.d(TAG, "Locale configurado: ${updatedContext.resources.configuration.locales.get(0)}")
            } else {
                @Suppress("DEPRECATION")
                Log.d(TAG, "Locale configurado: ${updatedContext.resources.configuration.locale}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar recursos: ${e.message}", e)
        }

        return updatedContext
    }

    /**
     * Obtiene el Locale actual de la configuración
     */
    fun getCurrentLocale(context: Context): Locale {
        val configuration = context.resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale
        }
    }
}