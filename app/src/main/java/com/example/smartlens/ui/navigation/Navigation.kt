package com.example.smartlens.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.smartlens.R
import com.example.smartlens.service.MotivationalQuotesService
import com.example.smartlens.service.UserProfileManager
import com.example.smartlens.ui.screens.*
import com.example.smartlens.viewmodel.SettingsViewModel

/**
 * Componente principal de navegación que incluye la BottomBar
 */
@Composable
fun MainNavigation(
    navController: NavHostController,
    userProfileManager: UserProfileManager,
    motivationalQuotesService: MotivationalQuotesService
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    // Usamos Estado simple en lugar de LiveData o StateFlow
    var apiKey by remember { mutableStateOf(settingsViewModel.apiKey) }

    // Actualizar el estado cuando se carga el componente
    LaunchedEffect(Unit) {
        apiKey = settingsViewModel.apiKey
    }

    // Determinar la pantalla inicial según si hay API Key configurada
    val startDestination = if (apiKey.isBlank()) {
        Screen.ApiKeySetup.route
    } else {
        Screen.Home.route
    }

    val bottomNavItems = listOf(
        BottomNavItem(
            route = Screen.Home.route,
            title = stringResource(R.string.navigation_home),
            icon = Icons.Default.Collections
        ),
        BottomNavItem(
            route = Screen.Camera.route,
            title = stringResource(R.string.navigation_camera),
            icon = Icons.Default.Camera
        ),
        BottomNavItem(
            route = Screen.Settings.route,
            title = stringResource(R.string.navigation_settings),
            icon = Icons.Default.Settings
        )
    )

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            // Mostrar barra de navegación solo en las pantallas principales y si API Key está configurada
            val showBottomBar = apiKey.isNotBlank() && bottomNavItems.any { item ->
                currentDestination?.hierarchy?.any { it.route == item.route } == true
            }

            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Pantalla de configuración inicial
            composable(Screen.ApiKeySetup.route) {
                ApiKeySetupScreen(navController)
            }

            // Pantallas principales
            composable(Screen.Home.route) {
                HomeScreen(
                    navController = navController,
                    userProfileManager = userProfileManager,
                    motivationalQuotesService = motivationalQuotesService
                )
            }

            composable(Screen.Camera.route) {
                CameraScreen(navController)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    userProfileManager = userProfileManager,
                    motivationalQuotesService = motivationalQuotesService
                )
            }

            // Flujo de procesamiento de documentos
            composable(
                route = "${Screen.DocumentType.route}/{imageUri}",
                arguments = listOf(
                    navArgument("imageUri") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val imageUri = backStackEntry.arguments?.getString("imageUri") ?: return@composable
                DocumentTypeScreen(navController, imageUri)
            }

            composable(
                route = "${Screen.Processing.route}/{documentType}/{imageUri}",
                arguments = listOf(
                    navArgument("documentType") {
                        type = NavType.StringType
                    },
                    navArgument("imageUri") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val documentType = backStackEntry.arguments?.getString("documentType") ?: return@composable
                val imageUri = backStackEntry.arguments?.getString("imageUri") ?: return@composable
                ProcessingScreen(navController, documentType, imageUri)
            }

            // Pantallas de detalle y exportación
            composable(
                route = "${Screen.DocumentDetails.route}/{documentId}",
                arguments = listOf(
                    navArgument("documentId") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val documentId = backStackEntry.arguments?.getString("documentId") ?: return@composable
                DocumentDetailsScreen(navController, documentId)
            }

            composable(
                route = "${Screen.Export.route}/{documentId}",
                arguments = listOf(
                    navArgument("documentId") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val documentId = backStackEntry.arguments?.getString("documentId") ?: return@composable
                ExportScreen(navController, documentId)
            }
        }
    }
}

/**
 * Clase de datos para los elementos de la barra de navegación inferior
 */
data class BottomNavItem(
    val route: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)