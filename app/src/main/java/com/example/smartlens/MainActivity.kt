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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sampleDataLoader: SampleDataLoader

    @Inject
    lateinit var userProfileManager: UserProfileManager

    @Inject
    lateinit var motivationalQuotesService: MotivationalQuotesService

    /**
     * Sobrescribe attachBaseContext para aplicar el idioma guardado
     */
    override fun attachBaseContext(newBase: Context) {
        val language = LanguageHelper.getCurrentLanguage(newBase)
        val context = LanguageHelper.updateLanguage(newBase, language)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar el ThemeManager
        ThemeManager.init(this)

        // Cargar datos de muestra en segundo plano
        lifecycleScope.launch {
            sampleDataLoader.loadSampleDataIfNeeded()
        }

        setContent {
            // Usando estado local en lugar de collectAsState
            var isDarkTheme by remember { mutableStateOf(false) }

            // Registramos un observador para el tema
            ThemeManager.addObserver { newValue ->
                isDarkTheme = newValue
                Log.d("MainActivity", "Theme changed to dark: $newValue")
            }

            SmartLensTheme(darkTheme = isDarkTheme) {
                val snackbarHostState = remember { SnackbarHostState() }
                val navController = rememberNavController()

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

    /**
     * Este método se llama cuando la actividad vuelve a estar visible
     * Comprobamos si necesitamos reiniciar por un cambio de idioma
     */
    override fun onResume() {
        super.onResume()
        // Si usamos un viewModel para gestionar los ajustes, podemos comprobar si ha habido cambios
        val settingsViewModel: SettingsViewModel by viewModels()
        if (settingsViewModel.needsRestart()) {
            // Reiniciar la actividad para aplicar el nuevo idioma
            recreate()
        }
    }
}