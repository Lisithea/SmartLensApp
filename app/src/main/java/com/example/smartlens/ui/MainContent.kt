package com.example.smartlens.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.smartlens.R
import com.example.smartlens.service.MotivationalQuotesService
import com.example.smartlens.service.UserProfileManager
import com.example.smartlens.ui.navigation.Destinations
import com.example.smartlens.ui.navigation.Screen
import com.example.smartlens.ui.screens.*

// Suprimir la advertencia de API experimental
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    navController: NavHostController,
    userProfileManager: UserProfileManager,
    motivationalQuotesService: MotivationalQuotesService
) {
    val greeting = userProfileManager.getGreeting()
    val quote = motivationalQuotesService.getRandomQuote()
    val quotesEnabled by motivationalQuotesService.quotesEnabled.collectAsState()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Saludo personalizado
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )

                // Frase motivacional si está activada
                if (quotesEnabled && quote.isNotEmpty()) {
                    Text(
                        text = "\"$quote\"",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                val items = listOf(
                    NavigationItem(
                        route = Destinations.CAMERA_ROUTE,
                        labelResId = R.string.navigation_camera,
                        icon = Icons.Default.Camera
                    ),
                    NavigationItem(
                        route = Destinations.GALLERY_ROUTE,
                        labelResId = R.string.navigation_home,
                        icon = Icons.Default.Collections
                    ),
                    NavigationItem(
                        route = Destinations.SETTINGS_ROUTE,
                        labelResId = R.string.navigation_settings,
                        icon = Icons.Default.Settings
                    )
                )

                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelResId)) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                popUpTo(navController.graph.findStartDestination().id)
                                // Avoid multiple copies of the same destination when
                                // reselecting the same item
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // Reemplazando SmartLensNavHost con un NavHost directamente
        NavHost(
            navController = navController,
            startDestination = Destinations.CAMERA_ROUTE,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Pantalla de captura de cámara
            composable(Destinations.CAMERA_ROUTE) {
                CameraScreen(navController = navController)
            }

            // Pantalla de galería (lista de documentos)
            composable(Destinations.GALLERY_ROUTE) {
                // Pasamos el UserProfileManager
                HomeScreen(
                    navController = navController,
                    userProfileManager = userProfileManager,
                    motivationalQuotesService = motivationalQuotesService
                )
            }

            // Pantalla de configuración
            composable(Destinations.SETTINGS_ROUTE) {
                SettingsScreen(
                    navController = navController,
                    userProfileManager = userProfileManager,
                    motivationalQuotesService = motivationalQuotesService
                )
            }

            // Pantalla de configuración inicial de API Key
            composable(Destinations.API_KEY_SETUP_ROUTE) {
                ApiKeySetupScreen(navController = navController)
            }

            // Pantalla para seleccionar tipo de documento
            composable(
                route = "${Destinations.DOCUMENT_TYPE_ROUTE}/{imageUri}",
                arguments = listOf(
                    navArgument("imageUri") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val imageUriString = backStackEntry.arguments?.getString("imageUri") ?: return@composable
                DocumentTypeScreen(navController = navController, imageUriString = imageUriString)
            }

            // Pantalla de procesamiento de documento
            composable(
                route = "${Destinations.PROCESSING_ROUTE}/{documentType}/{imageUri}",
                arguments = listOf(
                    navArgument("documentType") {
                        type = NavType.StringType
                    },
                    navArgument("imageUri") {
                        type = NavType.StringType
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

            // Pantalla de detalles de documento
            composable(
                route = "${Destinations.DOCUMENT_DETAILS_ROUTE}/{documentId}",
                arguments = listOf(
                    navArgument("documentId") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val documentId = backStackEntry.arguments?.getString("documentId") ?: return@composable
                DocumentDetailsScreen(navController = navController, documentId = documentId)
            }

            // Pantalla de exportación de documento
            composable(
                route = "${Destinations.EXPORT_ROUTE}/{documentId}",
                arguments = listOf(
                    navArgument("documentId") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val documentId = backStackEntry.arguments?.getString("documentId") ?: return@composable
                ExportScreen(navController = navController, documentId = documentId)
            }
        }
    }
}

data class NavigationItem(
    val route: String,
    val labelResId: Int,
    val icon: ImageVector
)