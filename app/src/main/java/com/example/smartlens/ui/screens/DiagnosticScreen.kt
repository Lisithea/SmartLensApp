package com.example.smartlens.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.smartlens.R
import com.example.smartlens.ui.components.LocalSnackbarManager
import com.example.smartlens.ui.navigation.navigateToAdvancedDiagnostic
import com.example.smartlens.util.OcrTester
import com.example.smartlens.viewmodel.DocumentViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen(
    navController: NavController,
    viewModel: DocumentViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarManager = LocalSnackbarManager.current
    val coroutineScope = rememberCoroutineScope()

    // Para obtener una instancia de OcrTester, se recomienda mover esta lógica al ViewModel
    // o usar un DI adecuado. Esto es solo para mantener la compatibilidad con el código existente.
    val ocrTester = remember { OcrTester(context, viewModel.getOcrService()) }

    var isLoading by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf("") }
    var selectedTestType by remember { mutableStateOf("simple") }

    // Selector de imágenes
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    isLoading = true
                    snackbarManager?.showInfo("Procesando imagen...")

                    // Guardar la imagen temporal
                    val tempUri = viewModel.saveTemporaryImage(it)

                    // Probar OCR directamente
                    val extractedText = viewModel.processImageAndGetText(tempUri)

                    testResults = "Resultado de OCR:\n\n" +
                            "Texto extraído (${extractedText.length} caracteres):\n\n" +
                            extractedText.take(500) + "...\n\n" +
                            "Tipo detectado: ${viewModel.getDetectedDocumentType()}"

                    isLoading = false
                    snackbarManager?.showSuccess("Prueba completada")
                } catch (e: Exception) {
                    isLoading = false
                    testResults = "Error: ${e.message}"
                    snackbarManager?.showError("Error: ${e.message}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico de OCR") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Nuevo banner para diagnóstico avanzado
            ElevatedCard(
                onClick = { navController.navigateToAdvancedDiagnostic() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Diagnóstico Avanzado",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text(
                            text = "Análisis detallado con clasificación automática de documentos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Ir a diagnóstico avanzado"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Divider()

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Diagnóstico Básico",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Herramientas de diagnóstico",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Esta pantalla permite diagnosticar problemas con el OCR",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Opciones de prueba
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedTestType == "simple",
                    onClick = { selectedTestType = "simple" }
                )
                Text(
                    text = "Prueba simple (imagen pre-generada)",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedTestType == "complete",
                    onClick = { selectedTestType = "complete" }
                )
                Text(
                    text = "Prueba completa (diagnóstico detallado)",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedTestType == "custom",
                    onClick = { selectedTestType = "custom" }
                )
                Text(
                    text = "Prueba con imagen personalizada",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    when (selectedTestType) {
                        "simple" -> {
                            coroutineScope.launch {
                                isLoading = true
                                testResults = "Ejecutando prueba simple..."
                                try {
                                    val result = ocrTester.testOcrWithSampleImage()
                                    testResults = result
                                } catch (e: Exception) {
                                    testResults = "Error: ${e.message}"
                                }
                                isLoading = false
                            }
                        }
                        "complete" -> {
                            coroutineScope.launch {
                                isLoading = true
                                testResults = "Ejecutando prueba completa..."
                                try {
                                    val result = ocrTester.runFullTestWithImages()
                                    testResults = result
                                } catch (e: Exception) {
                                    testResults = "Error: ${e.message}"
                                }
                                isLoading = false
                            }
                        }
                        "custom" -> {
                            galleryLauncher.launch("image/*")
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = when (selectedTestType) {
                                "custom" -> Icons.Default.CameraAlt
                                else -> Icons.Default.Check
                            },
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text("Ejecutar prueba de OCR")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Resultados
            if (testResults.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Resultados de la prueba",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = testResults,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}