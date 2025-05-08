package com.example.smartlens.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Objeto para gestionar los Snackbars en la aplicación
 */
class SnackbarManager(
    private val snackbarHostState: SnackbarHostState,
    private val coroutineScope: CoroutineScope
) {
    /**
     * Muestra un mensaje de éxito
     */
    fun showSuccess(message: String, duration: SnackbarDuration = SnackbarDuration.Short) {
        showMessage(message, duration)
    }

    /**
     * Muestra un mensaje de error
     */
    fun showError(message: String, duration: SnackbarDuration = SnackbarDuration.Long) {
        showMessage(message, duration)
    }

    /**
     * Muestra un mensaje de información
     */
    fun showInfo(message: String, duration: SnackbarDuration = SnackbarDuration.Short) {
        showMessage(message, duration)
    }

    /**
     * Muestra un mensaje con acción
     */
    fun showAction(
        message: String,
        actionLabel: String,
        duration: SnackbarDuration = SnackbarDuration.Indefinite,
        onAction: () -> Unit
    ) {
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = duration
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                onAction()
            }
        }
    }

    /**
     * Muestra un mensaje básico sin acción
     */
    private fun showMessage(message: String, duration: SnackbarDuration) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                duration = duration
            )
        }
    }
}

/**
 * CompositionLocal para acceder al SnackbarManager desde cualquier parte de la aplicación
 */
val LocalSnackbarManager = compositionLocalOf<SnackbarManager?> { null }