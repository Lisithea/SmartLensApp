package com.example.smartlens.ui.navigation

/**
 * Enum class que define las rutas principales de navegación
 */
sealed class Screen(val route: String) {
    object ApiKeySetup : Screen("api_key_setup")
    object Home : Screen("home")
    object Camera : Screen("camera")
    object Settings : Screen("settings")
    object DocumentType : Screen("document_type")
    object Processing : Screen("processing")
    object DocumentDetails : Screen("document_details")
    object Export : Screen("export")
}