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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartlens.R
import com.example.smartlens.service.MotivationalQuotesService
import com.example.smartlens.service.UserProfileManager
import com.example.smartlens.util.ThemeManager
import com.example.smartlens.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    userProfileManager: UserProfileManager,
    motivationalQuotesService: MotivationalQuotesService
) {
    val context = LocalContext.current

    // Usamos un estado local que se actualiza con LaunchedEffect para evitar problemas con collectAsState
    var isDarkTheme by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf(userProfileManager.getUserName()) }
    var userEmail by remember { mutableStateOf(userProfileManager.getEmail()) }
    var quotesEnabled by remember { mutableStateOf(motivationalQuotesService.getQuotesEnabled()) }

    // Actualizar el estado al iniciar el componente
    LaunchedEffect(Unit) {
        isDarkTheme = viewModel.isDarkTheme
    }

    // Observador para cambios en el tema
    DisposableEffect(Unit) {
        val observer = { newValue: Boolean ->
            isDarkTheme = newValue
        }

        // Registramos un observador para el tema (simulado)
        ThemeManager.addObserver(observer)

        onDispose {
            // Limpiamos el observador
            ThemeManager.removeObserver(observer)
        }
    }

    // Estados para la UI
    var apiKeyInput by remember { mutableStateOf(viewModel.apiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(viewModel.selectedLanguage) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
                    Toast.makeText(context, "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show()
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
                        quotesEnabled = it
                        motivationalQuotesService.toggleQuotes(it)
                        Toast.makeText(context, "Frases motivadoras " + (if (it) "activadas" else "desactivadas"), Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, context.getString(R.string.theme_changed), Toast.LENGTH_SHORT).show()
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
                                    selectedLanguage = language
                                    viewModel.saveLanguage(language)
                                    showLanguageDialog = false
                                    Toast.makeText(context, context.getString(R.string.language_change_restart), Toast.LENGTH_LONG).show()
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
}