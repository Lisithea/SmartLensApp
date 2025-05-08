package com.example.smartlens

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.smartlens.ui.components.LocalSnackbarManager
import com.example.smartlens.ui.components.SnackbarManager
import com.example.smartlens.ui.navigation.MainNavigation
import com.example.smartlens.ui.theme.SmartLensTheme
import com.example.smartlens.util.LanguageHelper
import com.example.smartlens.util.SampleDataLoader
import com.example.smartlens.util.ThemeManager
import com.example.smartlens.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.smartlens.service.UserProfileManager
import com.example.smartlens.service.MotivationalQuotesService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sampleDataLoader: SampleDataLoader

    @Inject
    lateinit var userProfileManager: UserProfileManager

    @Inject
    lateinit var motivationalQuotesService: MotivationalQuotesService

    // Variable para controlar si estamos recreando la actividad
    private var isRecreating = false

    /**
     * Sobrescribe attachBaseContext para aplicar el idioma guardado
     */
    override fun attachBaseContext(newBase: Context) {
        val currentLanguage = LanguageHelper.getCurrentLanguage(newBase)
        Log.d("MainActivity", "attachBaseContext: Aplicando idioma: $currentLanguage")
        val updatedContext = LanguageHelper.updateLanguage(newBase, currentLanguage)

        // Mostrar el locale actual para debug
        val locale = LanguageHelper.getCurrentLocale(updatedContext)
        Log.d("MainActivity", "Locale configurado: $locale")

        super.attachBaseContext(updatedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar el ThemeManager
        ThemeManager.init(this)

        // Cargar datos de muestra en segundo plano
        lifecycleScope.launch {
            sampleDataLoader.loadSampleDataIfNeeded()
        }

        // Verificar el idioma actual para debug
        val currentLanguage = LanguageHelper.getCurrentLanguage(this)
        val currentLocale = LanguageHelper.getCurrentLocale(this)
        Log.d("MainActivity", "onCreate: Idioma actual: $currentLanguage, Locale: $currentLocale")

        setContent {
            // Usando estado local en lugar de collectAsState
            var isDarkTheme by remember { mutableStateOf(ThemeManager.isDarkMode.value) }

            // Registramos un observador para el tema
            ThemeManager.addObserver { newValue ->
                isDarkTheme = newValue
                Log.d("MainActivity", "Theme changed to dark: $newValue")
            }

            // Crear SnackbarHostState y SnackbarManager
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()
            val snackbarManager = remember { SnackbarManager(snackbarHostState, coroutineScope) }

            SmartLensTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()

                CompositionLocalProvider(LocalSnackbarManager provides snackbarManager) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainNavigation(
                            navController = navController,
                            userProfileManager = userProfileManager,
                            motivationalQuotesService = motivationalQuotesService,
                            snackbarHostState = snackbarHostState
                        )
                    }
                }
            }
        }
    }

    /**
     * Este método se llama cuando la actividad vuelve a estar visible
     * Comprobamos si necesitamos reiniciar por un cambio de idioma
     */
    override fun onResume() {
        super.onResume()

        // Si estamos en proceso de recreación, no hacer nada
        if (isRecreating) {
            isRecreating = false
            return
        }

        // Verificar si necesitamos recrear la actividad por cambio de idioma
        val settingsViewModel: SettingsViewModel by viewModels()
        if (settingsViewModel.needsRestart()) {
            Log.d("MainActivity", "onResume: Recreando actividad por cambio de idioma")

            // Marcar que vamos a recrear para evitar bucles
            isRecreating = true

            // Retraso pequeño para asegurar que todo se complete antes de recrear
            lifecycleScope.launch {
                delay(300) // 300ms de retraso
                recreate()
            }
        }
    }
}