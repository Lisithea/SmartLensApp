package com.example.smartlens.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.example.smartlens.util.OcrServiceFactory
import com.example.smartlens.util.OcrTester
import com.example.smartlens.viewmodel.DocumentViewModel
import com.example.smartlens.viewmodel.LoginViewModel
import com.example.smartlens.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

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
    val context = LocalContext.current

    // Estado de autenticación
    val isAuthenticated by loginViewModel.isAuthenticated.collectAsState()

    // Estado para la API Key
    val apiKey = settingsViewModel.apiKey

    // Estado para el saludo y frases motivacionales
    val greeting = userProfileManager.getGreeting()
    val quote = motivationalQuotesService.getRandomQuote()
    val quotesEnabled by motivationalQuotesService.quotesEnabled.collectAsState()

    // Determinar la pantalla inicial basada en autenticación y API Key
    val startDestination = when {
        !isAuthenticated -> Screen.Login.route
        apiKey.isBlank() -> Screen.ApiKeySetup.route
        else -> Screen.Home.route
    }

    // Efecto para redirigir al inicio de sesión si no está autenticado
    LaunchedEffect(isAuthenticated) {
        if (!isAuthenticated) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Obtener la ruta actual
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ""

    // Definir los elementos de la barra de navegación
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

    // Determinar si se debe mostrar la barra de navegación
    val showBottomBar = isAuthenticated && apiKey.isNotBlank() &&
            (currentRoute == Screen.Home.route ||
                    currentRoute == Screen.Camera.route ||
                    currentRoute == Screen.Settings.route)

    Scaffold(
        topBar = {
            // Solo mostrar el saludo en las pantallas principales
            if (showBottomBar) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Saludo personalizado
                    Text(
                        text = greeting,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )

                    // Frase motivacional si está activada
                    if (quotesEnabled && quote.isNotEmpty()) {
                        Text(
                            text = "\"$quote\"",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
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
                LoginScreen(navController = navController)
            }

            // Pantalla de configuración inicial
            composable(Screen.ApiKeySetup.route) {
                ApiKeySetupScreen(navController = navController)
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
                CameraScreen(navController = navController)
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
                DocumentTypeScreen(navController = navController, imageUriString = imageUri)
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
                ProcessingScreen(navController = navController, documentTypeString = documentType, imageUriString = imageUri)
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
                DocumentDetailsScreen(navController = navController, documentId = documentId)
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
                ExportScreen(navController = navController, documentId = documentId)
            }

            // Pantalla de diagnóstico
            composable(Screen.Diagnostic.route) {
                val documentViewModel = hiltViewModel<DocumentViewModel>()
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