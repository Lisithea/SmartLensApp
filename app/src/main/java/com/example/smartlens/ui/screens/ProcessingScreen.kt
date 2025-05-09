package com.example.smartlens.ui.screens

import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.smartlens.R
import com.example.smartlens.model.DocumentProcessingState
import com.example.smartlens.model.DocumentType
import com.example.smartlens.ui.components.LocalSnackbarManager
import com.example.smartlens.ui.navigation.Screen
import com.example.smartlens.viewmodel.DocumentViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    navController: NavController,
    documentTypeString: String,
    imageUriString: String,
    viewModel: DocumentViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarManager = LocalSnackbarManager.current
    val coroutineScope = rememberCoroutineScope()

    val documentType = try {
        DocumentType.valueOf(documentTypeString)
    } catch (e: Exception) {
        DocumentType.UNKNOWN
    }

    val imageUri = Uri.parse(imageUriString)
    val processingState by viewModel.processingState.collectAsState()
    val currentDocument by viewModel.currentDocument.collectAsState()
    val extractedText by viewModel.extractedText.collectAsState()
    val structuredData by viewModel.structuredData.collectAsState()

    // Estado de animación
    var currentStep by remember { mutableStateOf(1) }
    var progress by remember { mutableStateOf(0.05f) }

    // Estado para mostrar y determinar la duración de procesamiento
    var elapsedTime by remember { mutableStateOf(0) }
    var processing by remember { mutableStateOf(true) }

    // Estado para mostrar información detallada
    var showRawData by remember { mutableStateOf(false) }

    // Animaciones para efectos visuales
    val infiniteTransition = rememberInfiniteTransition(label = "processingAnimation")
    val pulseAlpha = infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAnimation"
    )

    // Iniciar procesamiento si no está ya en curso
    LaunchedEffect(key1 = imageUri) {
        Log.d("ProcessingScreen", "Estado inicial: $processingState")
        if (processingState !is DocumentProcessingState.DocumentReady) {
            snackbarManager?.showInfo("Procesando documento...")
            viewModel.processDocument(documentType)
        }
    }

    // Contador de tiempo de procesamiento
    LaunchedEffect(key1 = processing) {
        while (processing) {
            delay(1000) // Incrementar cada segundo
            elapsedTime++
        }
    }

    // Manejar la animación de procesamiento
    LaunchedEffect(key1 = processingState) {
        when (processingState) {
            is DocumentProcessingState.ExtractingText -> {
                currentStep = 1
                progress = 0.3f
                snackbarManager?.showInfo("Extrayendo texto del documento...")
            }
            is DocumentProcessingState.ProcessingDocument -> {
                // Animar progreso
                currentStep = 2
                for (i in 30..80) {
                    progress = i / 100f
                    delay(50)
                }
                snackbarManager?.showInfo("Analizando documento...")
            }
            is DocumentProcessingState.DocumentReady -> {
                currentStep = 3
                progress = 1.0f
                processing = false // Detener el contador de tiempo
                snackbarManager?.showSuccess("¡Documento procesado correctamente!")

                delay(1500) // Esperar un momento antes de navegar para mostrar el progreso completo
                currentDocument?.let { document ->
                    navController.navigate("${Screen.DocumentDetails.route}/${document.id}") {
                        popUpTo(Screen.Camera.route)
                    }
                }
            }
            is DocumentProcessingState.Error -> {
                processing = false // Detener el contador de tiempo
                snackbarManager?.showError("Error: ${(processingState as DocumentProcessingState.Error).message}")
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.processing)) },
                navigationIcon = {
                    IconButton(onClick = {
                        // Cancelar el procesamiento y volver atrás
                        viewModel.cancelProcessing()
                        navController.navigateUp()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                processingState is DocumentProcessingState.Error -> {
                    // Mostrar error
                    val error = (processingState as DocumentProcessingState.Error).message
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(64.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.processing_error),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { navController.navigateUp() }
                        ) {
                            Text(stringResource(R.string.back))
                        }
                    }
                }

                else -> {
                    // Mostrar progreso
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Título animado con pulso
                        Text(
                            text = stringResource(R.string.processing) + " " +
                                    documentType.getDisplayName(),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.alpha(pulseAlpha.value)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Mostrar tiempo de procesamiento
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = String.format("Tiempo: %d segundos", elapsedTime),
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                // Barra de progreso circular con porcentaje
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.size(120.dp),
                                        strokeWidth = 8.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Text(
                                        text = "${(progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Barra de progreso
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Pasos del proceso con animación
                        ProcessStepCard(
                            number = 1,
                            title = stringResource(R.string.extracting_text),
                            isCompleted = currentStep > 1,
                            isActive = currentStep == 1,
                            icon = Icons.Default.Image
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        ProcessStepCard(
                            number = 2,
                            title = stringResource(R.string.analyzing_document),
                            isCompleted = currentStep > 2,
                            isActive = currentStep == 2,
                            icon = Icons.Default.Analytics
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        ProcessStepCard(
                            number = 3,
                            title = stringResource(R.string.finalizing),
                            isCompleted = currentStep > 3,
                            isActive = currentStep == 3,
                            icon = Icons.Default.Done
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Mostrar una parte del texto extraído
                        if (extractedText.isNotEmpty() && currentStep >= 2) {
                            OutlinedButton(
                                onClick = { showRawData = !showRawData },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = if (showRawData) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (showRawData) "Ocultar datos extraídos" else "Mostrar datos extraídos")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (showRawData) {
                                // Mostrar datos estructurados si hay
                                if (structuredData.isNotEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Datos estructurados detectados",
                                                style = MaterialTheme.typography.titleSmall
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Mostrar cada par clave-valor
                                            structuredData.entries.take(5).forEach { (key, value) ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = key,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                        modifier = Modifier.weight(1f)
                                                    )

                                                    Text(
                                                        text = value,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.weight(2f)
                                                    )
                                                }

                                                Divider()
                                            }

                                            // Si hay más de 5 elementos, mostrar un mensaje
                                            if (structuredData.size > 5) {
                                                Text(
                                                    text = "Y ${structuredData.size - 5} campos más...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                // Texto extraído
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = stringResource(R.string.extracted_text),
                                            style = MaterialTheme.typography.titleSmall
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Mostrar solo una parte del texto
                                        val previewText = if (extractedText.length > 200) {
                                            extractedText.substring(0, 200) + "..."
                                        } else extractedText

                                        Text(
                                            text = previewText,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessStepCard(
    number: Int,
    title: String,
    isCompleted: Boolean,
    isActive: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    // Animación para pulsar si está activo
    val infiniteTransition = rememberInfiniteTransition(label = "stepAnimation")
    val scale = infiniteTransition.animateFloat(
        initialValue = if (isActive) 1f else 1.05f,
        targetValue = if (isActive) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleAnimation"
    )

    val cardColor = when {
        isCompleted -> MaterialTheme.colorScheme.primary
        isActive -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    val contentColor = when {
        isCompleted -> MaterialTheme.colorScheme.onPrimary
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val cardElevation = when {
        isActive -> 8.dp
        isCompleted -> 4.dp
        else -> 1.dp
    }

    val modifier = if (isActive) {
        Modifier
            .fillMaxWidth()
            .scale(scale.value)
    } else {
        Modifier.fillMaxWidth()
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = cardColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Círculo de número o icono de check
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCompleted) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        else contentColor.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = contentColor
                    )
                } else {
                    Text(
                        text = number.toString(),
                        color = contentColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Título e icono
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
            }

            // Icono de estado
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor
            )
        }
    }
}