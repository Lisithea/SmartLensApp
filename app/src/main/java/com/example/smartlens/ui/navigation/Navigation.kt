package com.example.smartlens.ui.navigation

import android.net.Uri
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

/**
 * Main navigation component for the app
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

    // Authentication state
    val isAuthenticated by loginViewModel.isAuthenticated.collectAsState()

    // API Key state
    val apiKey by settingsViewModel.apiKey.collectAsState()

    // Greeting and motivational quote state
    val greeting = userProfileManager.getGreeting()
    val quote = motivationalQuotesService.getRandomQuote()
    val quotesEnabled by motivationalQuotesService.quotesEnabled.collectAsState()

    // Determine start destination based on auth and API Key
    val startDestination = when {
        !isAuthenticated -> Screen.Login.route
        apiKey.isEmpty() -> Screen.ApiKeySetup.route
        else -> Screen.Home.route
    }

    // Redirect to login if not authenticated
    LaunchedEffect(isAuthenticated) {
        if (!isAuthenticated) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Get current route
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ""

    // Define navigation items
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

    // Determine whether to show the bottom bar
    val showBottomBar = isAuthenticated && !apiKey.isEmpty() &&
            (currentRoute.startsWith(Screen.Home.route) ||
                    currentRoute.startsWith(Screen.Camera.route) ||
                    currentRoute.startsWith(Screen.Settings.route))

    Scaffold(
        topBar = {
            // Only show greeting on main screens
            if (showBottomBar) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Personalized greeting
                    Text(
                        text = greeting,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )

                    // Motivational quote if enabled
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
                            selected = currentRoute.startsWith(item.route),
                            onClick = {
                                if (!currentRoute.startsWith(item.route)) {
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
            // Login and setup screens
            composable(Screen.Login.route) {
                LoginScreen(navController = navController)
            }

            composable(Screen.ApiKeySetup.route) {
                ApiKeySetupScreen(navController = navController)
            }

            // Main screens
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

            // Document processing flow
            composable(
                route = "${Screen.DocumentType.route}/{imageUri}",
                arguments = listOf(
                    navArgument("imageUri") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val imageUriString = backStackEntry.arguments?.getString("imageUri") ?: return@composable
                DocumentTypeScreen(
                    navController = navController,
                    imageUriString = imageUriString
                )
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
                val documentTypeString = backStackEntry.arguments?.getString("documentType") ?: return@composable
                val imageUriString = backStackEntry.arguments?.getString("imageUri") ?: return@composable
                ProcessingScreen(
                    navController = navController,
                    documentTypeString = documentTypeString,
                    imageUriString = imageUriString
                )
            }

            // Document details and export
            composable(
                route = "${Screen.DocumentDetails.route}/{documentId}",
                arguments = listOf(
                    navArgument("documentId") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val documentId = backStackEntry.arguments?.getString("documentId") ?: return@composable
                DocumentDetailsScreen(
                    navController = navController,
                    documentId = documentId
                )
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
                ExportScreen(
                    navController = navController,
                    documentId = documentId
                )
            }

            // Diagnostic screen
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
 * Data class for bottom navigation items
 */
data class BottomNavItem(
    val route: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)