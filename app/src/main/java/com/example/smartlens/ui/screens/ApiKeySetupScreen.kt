package com.example.smartlens.ui.screens

import android.content.Intent
import android.net.Uri
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
import com.example.smartlens.ui.components.LocalSnackbarManager
import com.example.smartlens.ui.navigation.Screen
import com.example.smartlens.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeySetupScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarManager = LocalSnackbarManager.current
    val coroutineScope = rememberCoroutineScope()

    // Estado local para el input de la API Key
    // Obtenemos el valor de apiKey como StateFlow
    val apiKey by viewModel.apiKey.collectAsState()

    // Estado inicial en el composable
    var apiKeyInput by remember { mutableStateOf(apiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    // Verificamos si ya hay una API Key configurada
    LaunchedEffect(Unit) {
        if (apiKey.isNotEmpty()) {
            // Si ya hay una API Key, navegar directamente a la pantalla principal
            navController.navigate(Screen.Home.route) {
                popUpTo(0)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.api_settings)) },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            // Título y descripción
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.api_key_setup_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.api_key_setup_desc),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Campo para ingresar la API Key
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = {
                    apiKeyInput = it
                    showError = false
                },
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
                singleLine = true,
                isError = showError
            )

            if (showError) {
                Text(
                    text = stringResource(R.string.api_key_required),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.Start)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Link para obtener una API Key
            TextButton(
                onClick = {
                    // Abrir navegador para obtener API Key
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.google.dev/"))
                    context.startActivity(intent)
                }
            ) {
                Text(stringResource(R.string.get_api_key))
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Información adicional sobre la API
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "¿Cómo obtener una API Key de Gemini?",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "1. Visita ai.google.dev\n" +
                                "2. Crea una cuenta o inicia sesión\n" +
                                "3. Ve a la sección 'API Keys'\n" +
                                "4. Crea una nueva API Key\n" +
                                "5. Copia y pega la clave aquí",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Botón de continuar
            Button(
                onClick = {
                    if (apiKeyInput.isNotEmpty()) {
                        isLoading = true
                        coroutineScope.launch {
                            viewModel.saveApiKey(apiKeyInput)
                            // Mostrar un Toast
                            Toast.makeText(context, "API Key guardada correctamente", Toast.LENGTH_SHORT).show()
                            navController.navigate(Screen.Home.route) {
                                popUpTo(0)
                            }
                        }
                    } else {
                        showError = true
                    }
                },
                enabled = !isLoading && apiKeyInput.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.continue_text))
                }
            }

            // Opción para continuar en modo prueba sin API Key
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    // Usar una clave de API ficticia para pruebas
                    coroutineScope.launch {
                        viewModel.saveApiKey("TEST_MODE_API_KEY")
                        snackbarManager?.showInfo("Modo prueba activado - Funcionalidad limitada")
                        navController.navigate(Screen.Home.route) {
                            popUpTo(0)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Continuar en modo prueba")
            }
        }
    }
}