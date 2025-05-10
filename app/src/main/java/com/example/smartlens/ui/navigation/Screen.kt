package com.example.smartlens.ui.navigation

/**
 * Enum class que define las rutas principales de navegación
 */
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object ApiKeySetup : Screen("api_key_setup")
    object Home : Screen("home")
    object Camera : Screen("camera")
    object Settings : Screen("settings")
    object DocumentType : Screen("document_type/{imageUri}") {
        fun createRoute(imageUri: String) = "document_type/$imageUri"
    }
    object Processing : Screen("processing/{documentType}/{imageUri}") {
        fun createRoute(documentType: String, imageUri: String) = "processing/$documentType/$imageUri"
    }
    object DocumentDetails : Screen("document_details/{documentId}") {
        fun createRoute(documentId: String) = "document_details/$documentId"
    }
    object Export : Screen("export/{documentId}") {
        fun createRoute(documentId: String) = "export/$documentId"
    }
    object Diagnostic : Screen("diagnostic")
}