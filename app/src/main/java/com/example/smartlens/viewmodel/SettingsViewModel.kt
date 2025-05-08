package com.example.smartlens.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlens.service.GeminiService
import com.example.smartlens.util.LanguageHelper
import com.example.smartlens.util.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geminiService: GeminiService
) : ViewModel() {

    // Acceso directo a los valores en lugar de StateFlow
    val apiKey: String
        get() = preferences.getString("api_key", "") ?: ""

    val selectedLanguage: String
        get() = preferences.getString("selected_language", "español") ?: "español"

    // Usa ThemeManager para acceder al modo oscuro
    val isDarkTheme: Boolean
        get() = ThemeManager.isDarkMode.value

    private val preferences = context.getSharedPreferences("smartlens_settings", Context.MODE_PRIVATE)

    // Bandera para saber si se cambió el idioma
    private var languageChanged = false

    init {
        // Inicializar ThemeManager
        ThemeManager.init(context)
        Log.d("SettingsViewModel", "API Key: $apiKey, Language: $selectedLanguage, Dark Theme: $isDarkTheme")
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            preferences.edit().putString("api_key", key).apply()
            geminiService.saveApiKey(key)
            Log.d("SettingsViewModel", "Saved API Key: $key")
        }
    }

    fun saveLanguage(language: String) {
        viewModelScope.launch {
            if (selectedLanguage != language) {
                preferences.edit().putString("selected_language", language).apply()

                // Marcar que se cambió el idioma
                languageChanged = true

                // Aplicar el cambio de idioma inmediatamente
                LanguageHelper.updateLanguage(context, language)

                // Si el contexto es una actividad, recrearla para aplicar el cambio completamente
                if (context is Activity) {
                    val intent = context.intent
                    context.finish()
                    context.startActivity(intent)
                }

                Log.d("SettingsViewModel", "Language changed to: $language")
            }
        }
    }

    fun toggleTheme() {
        // Usar ThemeManager para cambiar el tema
        val newTheme = ThemeManager.toggleDarkMode(context)
        Log.d("SettingsViewModel", "Theme toggled to dark: $newTheme")
    }

    /**
     * Comprueba si es necesario reiniciar la actividad por un cambio de idioma
     */
    fun needsRestart(): Boolean {
        val result = languageChanged
        languageChanged = false  // Resetear la bandera
        return result
    }

    // Lista de idiomas disponibles para traducción
    val availableLanguages = listOf(
        "español", "inglés", "francés", "alemán", "italiano", "portugués",
        "chino", "japonés", "coreano", "ruso", "árabe"
    )
}