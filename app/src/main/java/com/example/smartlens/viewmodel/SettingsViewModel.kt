package com.example.smartlens.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlens.service.GeminiService
import com.example.smartlens.util.LanguageHelper
import com.example.smartlens.util.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geminiService: GeminiService
) : ViewModel() {

    // Estado para la API Key
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey

    // Estado para el idioma seleccionado
    private val _selectedLanguage = MutableStateFlow(LanguageHelper.currentLanguage.value)
    val selectedLanguage: StateFlow<String> = _selectedLanguage

    // Estado para el tema oscuro
    private val _isDarkTheme = MutableStateFlow(ThemeManager.isDarkMode.value)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme

    // Bandera para saber si se cambió el idioma
    private var languageChanged = false

    private val preferences = context.getSharedPreferences("smartlens_settings", Context.MODE_PRIVATE)

    init {
        // Inicializar ThemeManager
        ThemeManager.init(context)

        // Cargar valores iniciales
        _apiKey.value = geminiService.getApiKey()
        _selectedLanguage.value = LanguageHelper.getCurrentLanguage(context)

        // Registrar observador para el tema
        ThemeManager.addObserver { isDark ->
            _isDarkTheme.value = isDark
        }

        Log.d("SettingsViewModel", "API Key: ${_apiKey.value}, Language: ${_selectedLanguage.value}, Dark Theme: ${_isDarkTheme.value}")
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            geminiService.saveApiKey(key)
            _apiKey.value = key
            Log.d("SettingsViewModel", "Saved API Key: $key")
        }
    }

    fun saveLanguage(language: String) {
        viewModelScope.launch {
            if (_selectedLanguage.value != language) {
                _selectedLanguage.value = language

                // Marcar que se cambió el idioma
                languageChanged = true

                // Aplicar el cambio de idioma inmediatamente
                val updatedContext = LanguageHelper.updateLanguage(context, language)

                // Mostrar un Toast para indicar que se cambió el idioma
                Toast.makeText(updatedContext, "Idioma cambiado a $language", Toast.LENGTH_SHORT).show()

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
        _isDarkTheme.value = newTheme
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

    override fun onCleared() {
        super.onCleared()
        // Eliminar el observador cuando se destruya el ViewModel
        ThemeManager.removeObserver { _isDarkTheme.value = it }
    }
}