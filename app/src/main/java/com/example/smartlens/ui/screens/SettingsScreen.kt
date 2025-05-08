package com.example.smartlens.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.smartlens.R
import com.example.smartlens.service.MotivationalQuotesService
import com.example.smartlens.service.UserProfileManager
import com.example.smartlens.ui.components.LocalSnackbarManager
import com.example.smartlens.ui.navigation.Screen
import com.example.smartlens.util.ThemeManager
import com.example.smartlens.viewmodel.LoginViewModel
import com.example.smartlens.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
    loginViewModel: LoginViewModel = hiltViewModel(),
    userProfileManager: UserProfileManager,
    motivationalQuotesService: MotivationalQuotesService
) {
    val context = LocalContext.current
    val snackbarManager = LocalSnackbarManager.current
    val scope = rememberCoroutineScope()

    // Estados de UI
    val apiKey by viewModel.apiKey.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val quotesEnabled by motivationalQuotesService.quotesEnabled.collectAsState()

    // Estado local para visualización
    var apiKeyInput by remember { mutableStateOf(apiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf(userProfileManager.getUserName()) }
    var userEmail by remember { mutableStateOf(userProfileManager.getEmail()) }

    // Estado para confirmar cierre de sesión
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Sincronizar apiKeyInput cuando cambia apiKey
    LaunchedEffect(apiKey) {
        apiKeyInput = apiKey
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                actions = {
                    // Botón de cerrar sesión
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Cerrar sesión",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Sección de Perfil de Usuario
            Text(
                text = "Perfil de Usuario",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = userName,
                onValueChange = { userName = it },
                label = { Text("Nombre de Usuario") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = userEmail,
                onValueChange = { userEmail = it },
                label = { Text("Correo Electrónico") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    userProfileManager.saveUserName(userName)
                    userProfileManager.saveEmail(userEmail)
                    snackbarManager?.showSuccess("Perfil actualizado correctamente")
                    Toast.makeText(context, "Perfil actualizado", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Guardar Perfil")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sección de Frases Motivadoras
            Text(
                text = "Frases Motivadoras",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Mostrar frases motivadoras",
                    style = MaterialTheme.typography.bodyLarge
                )

                Switch(
                    checked = quotesEnabled,
                    onCheckedChange = {
                        motivationalQuotesService.toggleQuotes(it)
                        snackbarManager?.showInfo("Frases motivadoras " + (if (it) "activadas" else "desactivadas"))
                        Toast.makeText(
                            context,
                            "Frases motivadoras " + (if (it) "activadas" else "desactivadas"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sección de API
            Text(
                text = stringResource(R.string.api_settings),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text(stringResource(R.string.api_key)) },
                placeholder = { Text(stringResource(R.string.api_key_hint)) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (showApiKey) stringResource(R.string.hide_api_key) else stringResource(R.string.show_api_key)
                        )
                    }
                },
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.saveApiKey(apiKeyInput)
                    snackbarManager?.showSuccess("API Key guardada correctamente")
                    Toast.makeText(context, "API Key guardada correctamente", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.save_api_key))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sección de apariencia
            Text(
                text = stringResource(R.string.appearance),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.dark_theme),
                    style = MaterialTheme.typography.bodyLarge
                )

                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = {
                        viewModel.toggleTheme()
                        scope.launch {
                            snackbarManager?.showInfo(context.getString(R.string.theme_changed))
                        }
                        Toast.makeText(context, "Tema cambiado", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sección de idioma
            Text(
                text = stringResource(R.string.language),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedCard(
                onClick = { showLanguageDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedLanguage.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.select_language)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botón específico para cerrar sesión
            FilledTonalButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cerrar Sesión")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sección de herramientas de diagnóstico
            Text(
                text = "Herramientas de Diagnóstico",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            FilledTonalButton(
                onClick = { navController.navigate(Screen.Diagnostic.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BugReport, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Diagnosticar OCR")
                }
            }

            // Información de la aplicación
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.about),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.version),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.app_description),
                style = MaterialTheme.typography.bodyMedium
            )

            // Botón de contacto
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    // Código para contactar con el soporte (WhatsApp o correo)
                    snackbarManager?.showInfo("Contactando con soporte...")
                    Toast.makeText(context, "Contactando con soporte...", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Contactar con Soporte")
            }
        }
    }

    // Diálogo de selección de idioma
    if (showLanguageDialog) {
        val availableLanguages = viewModel.availableLanguages

        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.select_language)) },
            text = {
                Column {
                    availableLanguages.forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = language == selectedLanguage,
                                onClick = {
                                    viewModel.saveLanguage(language)
                                    showLanguageDialog = false
                                    Toast.makeText(
                                        context,
                                        "Cambiando idioma a $language. Reinicie la app si es necesario.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    snackbarManager?.showInfo(context.getString(R.string.language_change_restart))
                                }
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = language.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Diálogo de confirmación para cerrar sesión
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Cerrar Sesión") },
            text = {
                Text(
                    "¿Estás seguro de que deseas cerrar sesión? Esta acción te llevará a la pantalla de inicio de sesión.",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        // Realizar cierre de sesión
                        loginViewModel.logout()
                        // Mostrar un Toast
                        Toast.makeText(context, "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()
                        // Navegar a la pantalla de login
                        navController.navigate(Screen.Login.route) {
                            // Limpiar el back stack para que no se pueda volver atrás
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Cerrar Sesión")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}