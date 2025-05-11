package com.example.smartlens.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object ApiKeySetup : Screen("api_key_setup")
    object Home : Screen("home")
    object Camera : Screen("camera")
    object Settings : Screen("settings")
    object DocumentType : Screen("document_type/{imageUri}")
    object Processing : Screen("processing/{documentType}/{imageUri}")
    object DocumentDetails : Screen("document_details/{documentId}")
    object Export : Screen("export/{documentId}")
    object Diagnostic : Screen("diagnostic")

    fun createRoute(vararg params: String): String {
        return buildString {
            append(route)
            var paramIndex = 0
            val regex = "\\{([^/}]+)\\}".toRegex()
            regex.findAll(route).forEach { matchResult ->
                if (paramIndex < params.size) {
                    val replacement = params[paramIndex++]
                    val placeholder = matchResult.value
                    val routeWithReplacement = route.replace(placeholder, Uri.encode(replacement))
                    clear()
                    append(routeWithReplacement)
                }
            }
        }
    }
}