package com.example.smartlens

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sampleDataLoader: SampleDataLoader

    @Inject
    lateinit var userProfileManager: UserProfileManager

    @Inject
    lateinit var motivationalQuotesService: MotivationalQuotesService

    private var previousLanguage: String = "español"

    /**
     * Sobrescribe attachBaseContext para aplicar el idioma guardado
     */
    override fun attachBaseContext(newBase: Context) {
        // Guardar el idioma actual para comparar después
        previousLanguage = LanguageHelper.getCurrentLanguage(newBase)

        // Actualizar el contexto con el idioma guardado
        val language = previousLanguage
        val context = LanguageHelper.updateLanguage(newBase, language)

        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar el LanguageHelper
        LanguageHelper.init(this)

        // Inicializar el ThemeManager
        ThemeManager.init(this)

        // Cargar datos de muestra en segundo plano
        lifecycleScope.launch {
            sampleDataLoader.loadSampleDataIfNeeded()
        }

        setContent {
            // Crear un estado para el SnackbarHost
            val snackbarHostState = remember { SnackbarHostState() }
            val snackbarManager = remember { SnackbarManager(snackbarHostState, lifecycleScope) }

            // Proporcionar el SnackbarManager a través de CompositionLocal
            CompositionLocalProvider(LocalSnackbarManager provides snackbarManager) {
                SmartLensTheme {
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
    }

    /**
     * Este método se llama cuando la actividad vuelve a estar visible
     * Comprobamos si necesitamos reiniciar por un cambio de idioma
     */
    override fun onResume() {
        super.onResume()

        // Obtener el idioma actual
        val currentLanguage = LanguageHelper.getCurrentLanguage(this)

        // Si el idioma ha cambiado, recrear la actividad
        if (currentLanguage != previousLanguage) {
            Log.d("MainActivity", "Idioma cambiado de $previousLanguage a $currentLanguage. Recreando actividad.")
            previousLanguage = currentLanguage
            recreate()
        }
    }

    /**
     * Este método se llama cuando la actividad está a punto de ser destruida
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "Actividad destruida")
    }
}