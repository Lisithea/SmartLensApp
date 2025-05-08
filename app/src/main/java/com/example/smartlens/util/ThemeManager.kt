package com.example.smartlens.util

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Clase singleton para manejar el tema de la aplicación con múltiples opciones
 */
object ThemeManager {

    // Tipos de tema disponibles
    enum class ThemeType {
        SYSTEM, // Sigue la configuración del sistema
        LIGHT, // Tema claro
        DARK, // Tema oscuro (predeterminado)
        OLED_BLACK, // Tema negro puro para pantallas OLED
        RETRO, // Tema retro con estética vintage
        NATURE, // Tema con paleta de colores naturales
        ELEGANT // Tema elegante con acabado premium
    }

    // Estado que indica si el modo oscuro está activado
    private val _isDarkMode = MutableStateFlow(true) // Predeterminado oscuro
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    // Estado que almacena el tipo de tema actual
    private val _currentTheme = MutableStateFlow(ThemeType.DARK)
    val currentTheme: StateFlow<ThemeType> = _currentTheme

    // Lista de observadores para cambios de tema
    private val observers = mutableListOf<(Boolean) -> Unit>()
    private val themeTypeObservers = mutableListOf<(ThemeType) -> Unit>()

    // Obtiene el modo actual según las preferencias
    fun init(context: Context) {
        val preferences = context.getSharedPreferences("smartlens_settings", Context.MODE_PRIVATE)
        val themeValue = preferences.getInt("theme_type", ThemeType.DARK.ordinal)
        val themeType = ThemeType.values().getOrNull(themeValue) ?: ThemeType.DARK

        setThemeType(themeType, context)
    }

    /**
     * Cambia el modo oscuro y guarda la preferencia
     */
    fun toggleDarkMode(context: Context): Boolean {
        val newMode = !_isDarkMode.value

        // Si cambiamos a modo claro, usar el tema LIGHT
        // Si cambiamos a modo oscuro, usar el tema DARK
        val newThemeType = if (newMode) ThemeType.DARK else ThemeType.LIGHT

        setThemeType(newThemeType, context)
        return newMode
    }

    /**
     * Establece el tipo de tema y lo aplica
     */
    fun setThemeType(themeType: ThemeType, context: Context) {
        // Guardar en preferencias
        val preferences = context.getSharedPreferences("smartlens_settings", Context.MODE_PRIVATE)
        preferences.edit().putInt("theme_type", themeType.ordinal).apply()

        // Determinar si este tema es oscuro
        val isDark = when (themeType) {
            ThemeType.SYSTEM -> isSystemDarkMode(context)
            ThemeType.LIGHT -> false
            ThemeType.DARK, ThemeType.OLED_BLACK, ThemeType.RETRO -> true
            ThemeType.NATURE -> false
            ThemeType.ELEGANT -> true
        }

        // Establecer el modo en la aplicación
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        // Actualizar estados
        _isDarkMode.value = isDark
        _currentTheme.value = themeType

        // Notificar a los observadores
        notifyObservers(isDark)
        notifyThemeObservers(themeType)

        Log.d("ThemeManager", "Theme changed to: $themeType, isDark: $isDark")
    }

    private fun isSystemDarkMode(context: Context): Boolean {
        return when (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }

    /**
     * Añade un observador para cambios de tema
     */
    fun addObserver(observer: (Boolean) -> Unit) {
        observers.add(observer)
        // Notificar el estado actual inmediatamente
        observer(_isDarkMode.value)
    }

    /**
     * Añade un observador para cambios del tipo de tema
     */
    fun addThemeObserver(observer: (ThemeType) -> Unit) {
        themeTypeObservers.add(observer)
        // Notificar el estado actual inmediatamente
        observer(_currentTheme.value)
    }

    /**
     * Elimina un observador
     */
    fun removeObserver(observer: (Boolean) -> Unit) {
        observers.remove(observer)
    }

    /**
     * Elimina un observador de tipo de tema
     */
    fun removeThemeObserver(observer: (ThemeType) -> Unit) {
        themeTypeObservers.remove(observer)
    }

    /**
     * Notifica a todos los observadores del cambio de tema
     */
    private fun notifyObservers(isDark: Boolean) {
        observers.forEach { observer ->
            observer(isDark)
        }
    }

    /**
     * Notifica a todos los observadores del cambio de tipo de tema
     */
    private fun notifyThemeObservers(themeType: ThemeType) {
        themeTypeObservers.forEach { observer ->
            observer(themeType)
        }
    }

    /**
     * Devuelve si el modo oscuro está activado según el sistema o las preferencias
     */
    @Composable
    fun isDarkTheme(useDarkTheme: Boolean? = null): Boolean {
        val isDark = useDarkTheme ?: _isDarkMode.value
        return if (useDarkTheme == null && _currentTheme.value == ThemeType.SYSTEM) {
            // Si el tema es SYSTEM, usar el tema del sistema
            isSystemInDarkTheme()
        } else {
            // Si hay preferencia explícita, usar ese valor
            isDark
        }
    }
}