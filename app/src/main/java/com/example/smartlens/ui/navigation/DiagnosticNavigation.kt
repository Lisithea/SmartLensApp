package com.example.smartlens.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.smartlens.ui.screens.AdvancedDiagnosticScreen
import com.example.smartlens.ui.screens.DiagnosticScreen

/**
 * Extensiones de navegación para las pantallas de diagnóstico
 */

// Ruta para la pantalla de diagnóstico avanzado
const val ADVANCED_DIAGNOSTIC_ROUTE = "advanced_diagnostic_route"

// Función para añadir las rutas de diagnóstico al grafo de navegación
fun NavGraphBuilder.diagnosticScreens(navController: NavHostController) {
    // Pantalla de diagnóstico básico existente
    composable(Screen.Diagnostic.route) {
        DiagnosticScreen(navController = navController)
    }

    // Nueva pantalla de diagnóstico avanzado
    composable(ADVANCED_DIAGNOSTIC_ROUTE) {
        AdvancedDiagnosticScreen(navController = navController)
    }
}

// Función de extensión para navegar a la pantalla de diagnóstico avanzado
fun NavHostController.navigateToAdvancedDiagnostic() {
    this.navigate(ADVANCED_DIAGNOSTIC_ROUTE)
}