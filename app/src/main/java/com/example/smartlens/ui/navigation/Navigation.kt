package com.example.smartlens.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.smartlens.service.MotivationalQuotesService
import com.example.smartlens.service.UserProfileManager
import com.example.smartlens.ui.screens.*

/**
 * Navegación principal de la aplicación
 */
@Composable
fun MainNavigation(
    navController: NavHostController,
    userProfileManager: UserProfileManager,
    motivationalQuotesService: MotivationalQuotesService,
    snackbarHostState: SnackbarHostState
) {
    // Definición de rutas de navegación
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // Pantallas principales
        composable(Screen.Home.route) {
            HomeScreen(
                navController = navController,
                userProfileManager = userProfileManager,
                motivationalQuotesService = motivationalQuotesService
            )
        }

        composable(Screen.Camera.route) {
            CameraScreen(navController = navController)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                navController = navController,
                userProfileManager = userProfileManager,
                motivationalQuotesService = motivationalQuotesService
            )
        }

        // Pantallas de flujo de procesamiento de documentos
        composable(
            route = "${Screen.DocumentType.route}/{imageUri}",
            arguments = listOf(
                androidx.navigation.navArgument("imageUri") {
                    type = androidx.navigation.NavType.StringType
                }
            )
        ) { backStackEntry ->
            val imageUriString = backStackEntry.arguments?.getString("imageUri") ?: return@composable
            DocumentTypeScreen(navController = navController, imageUriString = imageUriString)
        }

        composable(
            route = "${Screen.Processing.route}/{documentType}/{imageUri}",
            arguments = listOf(
                androidx.navigation.navArgument("documentType") {
                    type = androidx.navigation.NavType.StringType
                },
                androidx.navigation.navArgument("imageUri") {
                    type = androidx.navigation.NavType.StringType
                }
            )
        ) { backStackEntry ->
            val documentTypeString = backStackEntry.arguments?.getString("documentType") ?: return@composable
            val imageUriString = backStackEntry.arguments?.getString("imageUri") ?: return@composable
            ProcessingScreen(
                navController = navController,
                documentTypeString = documentTypeString,
                imageUriString = imageUriString
            )
        }

        // Pantallas de documento
        composable(
            route = "${Screen.DocumentDetails.route}/{documentId}",
            arguments = listOf(
                androidx.navigation.navArgument("documentId") {
                    type = androidx.navigation.NavType.StringType
                }
            )
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId") ?: return@composable
            DocumentDetailsScreen(navController = navController, documentId = documentId)
        }

        composable(
            route = "${Screen.Export.route}/{documentId}",
            arguments = listOf(
                androidx.navigation.navArgument("documentId") {
                    type = androidx.navigation.NavType.StringType
                }
            )
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId") ?: return@composable
            ExportScreen(navController = navController, documentId = documentId)
        }

        // Pantalla de configuración de API Key
        composable(Screen.ApiKeySetup.route) {
            ApiKeySetupScreen(navController = navController)
        }

        // Pantallas de diagnóstico
        diagnosticScreens(navController)
    }
}