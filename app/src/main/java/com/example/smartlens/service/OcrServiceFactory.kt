package com.example.smartlens.util

import android.content.Context
import com.example.smartlens.service.OcrService

/**
 * Factory para crear instancias de OcrService sin necesidad de inyección de dependencias
 * Útil para obtener OcrService en contextos donde no podemos usar Hilt directamente
 */
object OcrServiceFactory {
    /**
     * Crea una instancia de OcrService usando el contexto proporcionado
     */
    fun create(context: Context): OcrService {
        return OcrService(context)
    }
}