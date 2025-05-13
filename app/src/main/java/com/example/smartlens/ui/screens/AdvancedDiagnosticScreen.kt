package com.example.smartlens.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.smartlens.R
import com.example.smartlens.model.DocumentType
import com.example.smartlens.service.DocumentAnalysisService
import com.example.smartlens.ui.components.LocalSnackbarManager
import com.example.smartlens.viewmodel.DocumentViewModel
import com.example.smartlens.viewmodel.EnhancedDocumentViewModel
import kotlinx.coroutines.launch

/**
 * Pantalla de diagnóstico avanzado para pruebas de OCR y clasificación de documentos
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedDiagnosticScreen(
    navController: NavController,
    viewModel: EnhancedDocumentViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarManager = LocalSnackbarManager.current
    val coroutineScope = rememberCoroutineScope()

    // Estados
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var showRawText by remember { mutableStateOf(false) }
    var showDetectedFields by remember { mutableStateOf(false) }

    // Estados del ViewModel
    val analysisState by viewModel.analysisState.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val diagnosticResult by viewModel.diagnosticResult.collectAsState()

    // Selector de imágenes
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            snackbarManager?.showInfo("Imagen seleccionada")
        }
    }

    // Calcular el progreso de análisis
    val progress by animateFloatAsState(
        targetValue = when (analysisState) {
            is EnhancedDocumentViewModel.AnalysisState.Idle -> 0f
            is EnhancedDocumentViewModel.AnalysisState.Analyzing -> 0.3f
            is EnhancedDocumentViewModel.AnalysisState.Processing -> 0.7f
            is EnhancedDocumentViewModel.AnalysisState.Completed,
            is EnhancedDocumentViewModel.AnalysisState.DiagnosticCompleted -> 1f
            else -> 0f
        },
        label = "progressAnimation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico Avanzado") },
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
            // Panel de información
            InfoPanel(
                title = "Herramienta de Diagnóstico OCR",
                description = "Analiza imágenes para probar el sistema de OCR y clasificación de documentos"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Selector de imagen
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { galleryLauncher.launch("image/*") }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Seleccionar imagen para diagnóstico",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Selecciona una imagen de documento para analizar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Previsualización de imagen seleccionada
            selectedImageUri?.let { uri ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                ImageRequest.Builder(LocalContext.current)
                                    .data(data = uri)
                                    .build()
                            ),
                            contentDescription = "Imagen seleccionada",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isAnalyzing = true
                            coroutineScope.launch {
                                try {
                                    viewModel.analyzeImage(uri)
                                } finally {
                                    isAnalyzing = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isAnalyzing && analysisState !is EnhancedDocumentViewModel.AnalysisState.Analyzing
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analizar")
                    }

                    Button(
                        onClick = {
                            isAnalyzing = true
                            coroutineScope.launch {
                                try {
                                    viewModel.performDiagnostic(uri)
                                } finally {
                                    isAnalyzing = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isAnalyzing && analysisState !is EnhancedDocumentViewModel.AnalysisState.Analyzing
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Diagnóstico")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Indicador de progreso
                if (isAnalyzing ||
                    analysisState is EnhancedDocumentViewModel.AnalysisState.Analyzing ||
                    analysisState is EnhancedDocumentViewModel.AnalysisState.Processing
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when(analysisState) {
                                is EnhancedDocumentViewModel.AnalysisState.Analyzing -> "Analizando imagen..."
                                is EnhancedDocumentViewModel.AnalysisState.Processing -> "Procesando documento..."
                                else -> "Procesando..."
                            },
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Resultados del análisis
            when (analysisState) {
                is EnhancedDocumentViewModel.AnalysisState.TypeDetected -> {
                    val typeDetected = analysisState as EnhancedDocumentViewModel.AnalysisState.TypeDetected

                    analysisResult?.let { result ->
                        ResultCard(
                            title = "Análisis Completado",
                            backgroundColor = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            // Tipo de documento detectado
                            DetailItem(label = "Tipo de documento", value = typeDetected.specificType)

                            // Opciones para mostrar/ocultar información
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(onClick = { showRawText = !showRawText }) {
                                    Text(if (showRawText) "Ocultar texto OCR" else "Mostrar texto OCR")
                                }

                                TextButton(onClick = { showDetectedFields = !showDetectedFields }) {
                                    Text(if (showDetectedFields) "Ocultar campos" else "Mostrar campos")
                                }
                            }

                            // Texto extraído
                            AnimatedVisibility(
                                visible = showRawText,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    Text(
                                        text = "Texto Extraído:",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        val previewText = if (result.extractedText.length > 300) {
                                            result.extractedText.substring(0, 300) + "..."
                                        } else result.extractedText

                                        Text(
                                            text = previewText,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }

                            // Campos detectados
                            AnimatedVisibility(
                                visible = showDetectedFields,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    Text(
                                        text = "Campos Detectados:",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    if (result.structuredData.isNotEmpty()) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                result.structuredData.forEach { (key, value) ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = key,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.weight(2f)
                                                        )

                                                        Text(
                                                            text = value,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            modifier = Modifier.weight(3f)
                                                        )
                                                    }

                                                    if (key != result.structuredData.keys.last()) {
                                                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "No se detectaron campos estructurados",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            // Botón para procesar documento
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    selectedImageUri?.let { uri ->
                                        coroutineScope.launch {
                                            try {
                                                isAnalyzing = true
                                                viewModel.processDocument(uri, typeDetected.specificType)
                                            } finally {
                                                isAnalyzing = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isAnalyzing
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Procesar como ${typeDetected.specificType}")
                            }
                        }
                    }
                }

                is EnhancedDocumentViewModel.AnalysisState.DiagnosticCompleted -> {
                    val diagCompleted = analysisState as EnhancedDocumentViewModel.AnalysisState.DiagnosticCompleted

                    diagnosticResult?.let { result ->
                        ResultCard(
                            title = "Diagnóstico Completo",
                            backgroundColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            // Resultados OCR
                            Text(
                                text = "Estado OCR:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (result.ocrSuccess) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (result.ocrSuccess) Color.Green else Color.Red
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = if (result.ocrSuccess) "Texto extraído correctamente" else "Error en extracción de texto",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            // Resultados de clasificación
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Clasificación de documento:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (result.classificationSuccess) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (result.classificationSuccess) Color.Green else Color.Red
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = if (result.classificationSuccess)
                                        "Documento clasificado como: ${result.detectedType}"
                                    else
                                        "No se pudo clasificar el documento",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            // Información de tiempo
                            Spacer(modifier = Modifier.height(16.dp))

                            DetailItem(
                                label = "Tiempo de procesamiento OCR",
                                value = "${result.ocrTimeMs} ms"
                            )

                            DetailItem(
                                label = "Tiempo de clasificación",
                                value = "${result.classificationTimeMs} ms"
                            )

                            // Características de imagen
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Características de imagen:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            DetailItem(
                                label = "Dimensiones",
                                value = "${result.imageWidth} x ${result.imageHeight} px"
                            )

                            DetailItem(
                                label = "Tamaño de archivo",
                                value = "${result.imageSize / 1024} KB"
                            )

                            // Opciones para mostrar/ocultar texto extraído
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                TextButton(onClick = { showRawText = !showRawText }) {
                                    Text(if (showRawText) "Ocultar texto OCR" else "Mostrar texto OCR")
                                }
                            }

                            // Texto extraído
                            AnimatedVisibility(
                                visible = showRawText,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        val previewText = if (result.extractedText.length > 300) {
                                            result.extractedText.substring(0, 300) + "..."
                                        } else result.extractedText

                                        Text(
                                            text = previewText,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                is EnhancedDocumentViewModel.AnalysisState.Completed -> {
                    val completed = analysisState as EnhancedDocumentViewModel.AnalysisState.Completed

                    ResultCard(
                        title = "Procesamiento completado",
                        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = "El documento ha sido procesado y guardado correctamente como:",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = completed.specificType,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "ID del documento: ${completed.document.id}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                navController.popBackStack()
                                navController.navigate("document_details_route/${completed.document.id}")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ver detalles del documento")
                        }
                    }
                }

                is EnhancedDocumentViewModel.AnalysisState.Error -> {
                    val error = analysisState as EnhancedDocumentViewModel.AnalysisState.Error

                    ResultCard(
                        title = "Error",
                        backgroundColor = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = error.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Seleccionar otra imagen")
                        }
                    }
                }

                else -> {
                    // Estado inicial o no manejado, no mostrar nada adicional
                }
            }

            // Espacio inferior para evitar que el contenido quede detrás del botón flotante
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
fun InfoPanel(title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ResultCard(
    title: String,
    backgroundColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            content()
        }
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }

    Divider(modifier = Modifier.padding(vertical = 4.dp))
}