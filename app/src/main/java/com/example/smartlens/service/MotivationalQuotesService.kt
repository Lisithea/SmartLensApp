package com.example.smartlens.service

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Servicio para mostrar frases motivadoras en la aplicación
 */
@Singleton
class MotivationalQuotesService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val preferences: SharedPreferences = context.getSharedPreferences("motivational_quotes", Context.MODE_PRIVATE)

    // Estado de si las frases motivadoras están activadas
    private val _quotesEnabled = MutableStateFlow(getQuotesEnabled())
    val quotesEnabled: StateFlow<Boolean> = _quotesEnabled

    // Lista de frases motivadoras
    private val quotes = listOf(
        "La mejor forma de predecir el futuro es creándolo.",
        "El único lugar donde el éxito llega antes que el trabajo es en el diccionario.",
        "Siempre parece imposible hasta que se hace.",
        "No dejes para mañana lo que puedes escanear hoy.",
        "La organización es la clave del éxito.",
        "Un documento ordenado refleja una mente ordenada.",
        "El conocimiento es poder, y la información es libertad.",
        "Nunca es tarde para empezar a organizar.",
        "La simplicidad es la máxima sofisticación.",
        "Pequeños pasos, grandes resultados."
    )

    /**
     * Retorna si las frases motivadoras están activadas
     */
    fun getQuotesEnabled(): Boolean {
        return preferences.getBoolean("quotes_enabled", true)
    }

    /**
     * Cambia el estado de las frases motivadoras
     */
    fun toggleQuotes(enabled: Boolean) {
        preferences.edit().putBoolean("quotes_enabled", enabled).apply()
        _quotesEnabled.value = enabled
    }

    /**
     * Obtiene una frase motivadora aleatoria
     */
    fun getRandomQuote(): String {
        // Si las frases no están habilitadas, retorna vacío
        if (!getQuotesEnabled()) return ""

        return quotes[Random.nextInt(quotes.size)]
    }

    /**
     * Retorna una frase específica por su índice
     */
    fun getQuoteByIndex(index: Int): String {
        // Si las frases no están habilitadas, retorna vacío
        if (!getQuotesEnabled()) return ""

        return if (index >= 0 && index < quotes.size) {
            quotes[index]
        } else {
            getRandomQuote()
        }
    }
}