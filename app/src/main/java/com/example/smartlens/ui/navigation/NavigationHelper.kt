package com.example.smartlens.ui.navigation

import android.net.Uri
import android.util.Log
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

/**
 * Clase que contiene funciones estáticas para manejar la navegación entre pantallas
 * Resuelve problemas de navegación creando correctamente las rutas con parámetros
 */
object NavigationHelper {

    private const val TAG = "NavigationHelper"

    /**
     * Navega a la pantalla de inicio
     */
    fun navigateToHome(navController: NavController) {
        navController.navigate(Screen.Home.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    /**
     * Navega a la pantalla de ajustes
     */
    fun navigateToSettings(navController: NavController) {
        navController.navigate(Screen.Settings.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    /**
     * Navega a la pantalla de cámara
     */
    fun navigateToCamera(navController: NavController) {
        navController.navigate(Screen.Camera.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    /**
     * Navega desde la cámara a la pantalla de tipo de documento
     * Este método maneja correctamente la codificación de la URI
     */
    fun navigateToDocumentType(navController: NavController, imageUriString: String) {
        try {
            // Asegurar que la URI esté correctamente codificada para evitar caracteres especiales
            val encodedUri = Uri.encode(imageUriString)
            Log.d(TAG, "Navegando a DocumentType con URI codificada: $encodedUri")
            // Componer la ruta con el parámetro
            val route = "${Screen.DocumentType.route}/$encodedUri"
            navController.navigate(route)
        } catch (e: Exception) {
            Log.e(TAG, "Error al navegar a DocumentType: ${e.message}", e)
        }
    }

    /**
     * Navega desde la pantalla de tipo de documento a la pantalla de procesamiento
     * Maneja correctamente la codificación de parámetros
     */
    fun navigateToProcessing(navController: NavController, documentType: String, imageUriString: String) {
        try {
            // Asegurar que la URI esté correctamente codificada
            val encodedUri = Uri.encode(imageUriString)
            Log.d(TAG, "Navegando a Processing con Tipo: $documentType, URI codificada: $encodedUri")
            // Componer la ruta con los parámetros
            val route = "${Screen.Processing.route}/$documentType/$encodedUri"
            Log.d(TAG, "Ruta completa: $route")

            navController.navigate(route)
        } catch (e: Exception) {
            Log.e(TAG, "Error al navegar a Processing: ${e.message}", e)
        }
    }

    /**
     * Navega a la pantalla de detalles del documento
     */
    fun navigateToDocumentDetails(navController: NavController, documentId: String) {
        try {
            val route = "${Screen.DocumentDetails.route}/$documentId"
            Log.d(TAG, "Navegando a DocumentDetails con ID: $documentId, Ruta: $route")
            navController.navigate(route)
        } catch (e: Exception) {
            Log.e(TAG, "Error al navegar a DocumentDetails: ${e.message}", e)
        }
    }

    /**
     * Navega a la pantalla de exportación
     */
    fun navigateToExport(navController: NavController, documentId: String) {
        try {
            val route = "${Screen.Export.route}/$documentId"
            Log.d(TAG, "Navegando a Export con ID: $documentId, Ruta: $route")
            navController.navigate(route)
        } catch (e: Exception) {
            Log.e(TAG, "Error al navegar a Export: ${e.message}", e)
        }
    }

    /**
     * Navega a la pantalla de diagnóstico
     */
    fun navigateToDiagnostic(navController: NavController) {
        navController.navigate(Screen.Diagnostic.route)
    }
}

/**
 * Extensión para la clase Screen que permite crear rutas con parámetros
 * de manera segura, evitando errores comunes
 */
fun Screen.createRoutePath(vararg params: String): String {
    return buildString {
        append(this@createRoutePath.route)
        params.forEach { param ->
            // Asegurar que el parámetro esté codificado si es necesario
            val encodedParam = if (param.contains(":") || param.contains("/") || param.contains("?")) {
                Uri.encode(param)
            } else {
                param
            }
            append("/$encodedParam")
        }
    }
}