package com.example.smartlens.ui.navigation

import android.net.Uri

/**
 * Define routes for navigation
 */
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object ApiKeySetup : Screen("api_key_setup")
    object Home : Screen("home")
    object Camera : Screen("camera")
    object Settings : Screen("settings")
    object DocumentType : Screen("document_type")
    object Processing : Screen("processing")
    object DocumentDetails : Screen("document_details")
    object Export : Screen("export")
    object Diagnostic : Screen("diagnostic")

    fun createRoute(vararg params: String): String {
        return buildString {
            append(route)
            params.forEach { param ->
                append("/$param")
            }
        }
    }
}

/**
 * Navigation helper functions
 */
object NavigationActions {
    /**
     * Navigate from camera to document type selection
     */
    fun navigateToDocumentType(navController: androidx.navigation.NavController, imageUriString: String) {
        val encodedUri = Uri.encode(imageUriString)
        navController.navigate("${Screen.DocumentType.route}/$encodedUri")
    }

    /**
     * Navigate to processing screen
     */
    fun navigateToProcessing(navController: androidx.navigation.NavController, documentType: String, imageUriString: String) {
        val encodedUri = Uri.encode(imageUriString)
        navController.navigate("${Screen.Processing.route}/$documentType/$encodedUri")
    }

    /**
     * Navigate to document details
     */
    fun navigateToDocumentDetails(navController: androidx.navigation.NavController, documentId: String) {
        navController.navigate("${Screen.DocumentDetails.route}/$documentId")
    }

    /**
     * Navigate to export screen
     */
    fun navigateToExport(navController: androidx.navigation.NavController, documentId: String) {
        navController.navigate("${Screen.Export.route}/$documentId")
    }
}