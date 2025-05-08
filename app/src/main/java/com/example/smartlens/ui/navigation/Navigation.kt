package com.example.smartlens.ui.navigation

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.example.smartlens.viewmodel.LoginViewModel
import com.example.smartlens.viewmodel.SettingsViewModel
import com.example.smartlens.util.OcrTester
import com.example.smartlens.util.OcrServiceFactory

/**
 * Componente principal de navegación que incluye la BottomBar
 */
@Composable
fun MainNavigation(
    navController: NavHostController,
    userProfileManager: UserProfileManager,
    motivationalQuotesService: MotivationalQuotesService,
    snackbarHostState: SnackbarHostState
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val loginViewModel: LoginViewModel = hiltViewModel()
    // Necesitamos el context para el OcrTester
    val context = LocalContext.current

    // Estado de autenticación
    val isAuthenticated by loginViewModel.isAuthenticated.collectAsState()

    // Usamos Estado simple en lugar de LiveData o StateFlow para la API Key
    val apiKey by settingsViewModel.apiKey.collectAsState()

    // Determinar la pantalla inicial
    val startDestination = Screen.Login.route

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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            // Mostrar barra de navegación solo en las pantallas principales y si el usuario está autenticado
            val showBottomBar = isAuthenticated && apiKey.isNotEmpty() && bottomNavItems.any { item ->
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
            // Pantalla de inicio de sesión
            composable(Screen.Login.route) {
                LoginScreen(navController)
            }

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
                    navController = navController,
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

            // Pantalla de diagnóstico
            composable(Screen.Diagnostic.route) {
                // Usar ViewModels específicos para evitar problemas de compatibilidad
                val documentViewModel = hiltViewModel<com.example.smartlens.viewmodel.DocumentViewModel>()

                // Crear OcrService y OcrTester usando el factory
                val ocrService = OcrServiceFactory.create(context)
                val ocrTester = OcrTester(context, ocrService)

                DiagnosticScreen(
                    navController = navController,
                    ocrTester = ocrTester,
                    viewModel = documentViewModel
                )
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