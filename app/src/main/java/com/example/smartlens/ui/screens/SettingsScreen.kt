package com.example.smartlens.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.example.smartlens.util.LanguageHelper
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
    val coroutineScope = rememberCoroutineScope()

    // Usamos un estado local que se actualiza con LaunchedEffect para evitar problemas
    var isDarkTheme by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf(userProfileManager.getUserName()) }
    var userEmail by remember { mutableStateOf(userProfileManager.getEmail()) }
    var quotesEnabled by remember { mutableStateOf(motivationalQuotesService.getQuotesEnabled()) }
    var selectedTheme by remember { mutableStateOf(ThemeManager.currentTheme.value) }

    // Actualizar el estado al iniciar el componente
    LaunchedEffect(Unit) {
        isDarkTheme = viewModel.isDarkTheme
    }

    // Observador para cambios en el tema
    DisposableEffect(Unit) {
        val observer = { newValue: Boolean ->
            isDarkTheme = newValue
        }

        // Registramos un observador para el tema
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
    var showThemeDialog by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(LanguageHelper.currentLanguage.value) }

    // Estado para confirmar cierre de sesión
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Estado para el banner de cambio de idioma
    var showLanguageChangedBanner by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                actions = {
                    // Botón de cerrar sesión
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.Default.Logout, contentDescription = "Cerrar sesión")
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
            // Banner informativo después de cambio de idioma
            AnimatedVisibility(
                visible = showLanguageChangedBanner,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.language_change_restart),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = { showLanguageChangedBanner = false }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Sección de Perfil de Usuario
            SectionHeader(
                icon = Icons.Default.Person,
                title = "Perfil de Usuario"
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = userName,
                onValueChange = { userName = it },
                label = { Text("Nombre de Usuario") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = userEmail,
                onValueChange = { userEmail = it },
                label = { Text("Correo Electrónico") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    userProfileManager.saveUserName(userName)
                    userProfileManager.saveEmail(userEmail)
                    snackbarManager?.showSuccess("Perfil actualizado correctamente")
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar Perfil")
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Sección de Frases Motivadoras
            SectionHeader(
                icon = Icons.Default.Lightbulb,
                title = "Frases Motivadoras"
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
                        snackbarManager?.showInfo("Frases motivadoras " + (if (it) "activadas" else "desactivadas"))
                    }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Sección de API
            SectionHeader(
                icon = Icons.Default.Api,
                title = stringResource(R.string.api_settings)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text(stringResource(R.string.api_key)) },
                placeholder = { Text(stringResource(R.string.api_key_hint)) },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
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
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.save_api_key))
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Sección de apariencia
            SectionHeader(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.appearance)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Selector de tema
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showThemeDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when(selectedTheme) {
                            ThemeManager.ThemeType.LIGHT -> Icons.Default.LightMode
                            ThemeManager.ThemeType.DARK -> Icons.Default.DarkMode
                            ThemeManager.ThemeType.SYSTEM -> Icons.Default.SettingsBrightness
                            ThemeManager.ThemeType.OLED_BLACK -> Icons.Default.Brightness2
                            ThemeManager.ThemeType.RETRO -> Icons.Default.Vintage
                            ThemeManager.ThemeType.NATURE -> Icons.Default.Forest
                            ThemeManager.ThemeType.ELEGANT -> Icons.Default.AutoAwesome
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tema",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = when(selectedTheme) {
                                ThemeManager.ThemeType.LIGHT -> "Claro"
                                ThemeManager.ThemeType.DARK -> "Oscuro"
                                ThemeManager.ThemeType.SYSTEM -> "Sistema"
                                ThemeManager.ThemeType.OLED_BLACK -> "OLED (Negro puro)"
                                ThemeManager.ThemeType.RETRO -> "Retro"
                                ThemeManager.ThemeType.NATURE -> "Naturaleza"
                                ThemeManager.ThemeType.ELEGANT -> "Elegante"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Sección de idioma
            SectionHeader(
                icon = Icons.Default.Language,
                title = stringResource(R.string.language)
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Emoji de bandera
                    Text(
                        text = LanguageHelper.getFlagForLanguage(selectedLanguage),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                            .padding(4.dp),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.language),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = selectedLanguage.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.select_language)
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Sección de herramientas de diagnóstico
            SectionHeader(
                icon = Icons.Default.BugReport,
                title = "Herramientas de Diagnóstico"
            )

            Spacer(modifier = Modifier.height(16.dp))

            FilledTonalButton(
                onClick = { navController.navigate(Screen.Diagnostic.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Diagnosticar OCR")
            }

            // Información de la aplicación
            Divider(modifier = Modifier.padding(vertical = 16.dp))

            SectionHeader(
                icon = Icons.Default.Info,
                title = stringResource(R.string.about)
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

            // Espacio al final
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Diálogo de selección de idioma
    if (showLanguageDialog) {
        val availableLanguages = LanguageHelper.supportedLanguages

        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.select_language))
                }
            },
            text = {
                Column {
                    availableLanguages.forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Intentar cambiar el idioma
                                    coroutineScope.launch {
                                        val languageChanged = LanguageHelper.setLanguage(context, language)
                                        if (languageChanged) {
                                            selectedLanguage = language
                                            showLanguageChangedBanner = true

                                            // Recrear la actividad para aplicar el cambio
                                            if (context is android.app.Activity) {
                                                context.recreate()
                                            }
                                        }
                                        showLanguageDialog = false
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Emoji de bandera
                            Text(
                                text = LanguageHelper.getFlagForLanguage(language),
                                style = MaterialTheme.typography.titleLarge
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = language.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (language == selectedLanguage) FontWeight.Bold else FontWeight.Normal,
                                color = if (language == selectedLanguage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            if (language == selectedLanguage) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (language != availableLanguages.last()) {
                            Divider(modifier = Modifier.padding(horizontal = 16.dp))
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

    // Diálogo de selección de tema
    if (showThemeDialog) {
        val availableThemes = listOf(
            ThemeManager.ThemeType.SYSTEM to "Sistema",
            ThemeManager.ThemeType.LIGHT to "Claro",
            ThemeManager.ThemeType.DARK to "Oscuro",
            ThemeManager.ThemeType.OLED_BLACK to "OLED (Negro puro)",
            ThemeManager.ThemeType.RETRO to "Retro",
            ThemeManager.ThemeType.NATURE to "Naturaleza",
            ThemeManager.ThemeType.ELEGANT to "Elegante"
        )

        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Seleccionar tema")
                }
            },
            text = {
                Column {
                    availableThemes.forEach { (theme, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ThemeManager.setThemeType(theme, context)
                                    selectedTheme = theme
                                    snackbarManager?.showSuccess(context.getString(R.string.theme_changed))
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icono de tema
                            Icon(
                                imageVector = when(theme) {
                                    ThemeManager.ThemeType.LIGHT -> Icons.Default.LightMode
                                    ThemeManager.ThemeType.DARK -> Icons.Default.DarkMode
                                    ThemeManager.ThemeType.SYSTEM -> Icons.Default.SettingsBrightness
                                    ThemeManager.ThemeType.OLED_BLACK -> Icons.Default.Brightness2
                                    ThemeManager.ThemeType.RETRO -> Icons.Default.Vintage
                                    ThemeManager.ThemeType.NATURE -> Icons.Default.Forest
                                    ThemeManager.ThemeType.ELEGANT -> Icons.Default.AutoAwesome
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (theme == selectedTheme) FontWeight.Bold else FontWeight.Normal,
                                color = if (theme == selectedTheme) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            if (theme == selectedTheme) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (theme != availableThemes.last().first) {
                            Divider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Diálogo de confirmación para cerrar sesión
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cerrar Sesión", color = MaterialTheme.colorScheme.error)
                }
            },
            text = { Text("¿Estás seguro de que deseas cerrar sesión?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        loginViewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Confirmar")
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

@Composable
fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}