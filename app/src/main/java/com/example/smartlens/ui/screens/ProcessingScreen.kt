package com.example.smartlens.ui.screens

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.smartlens.R
import com.example.smartlens.model.DocumentProcessingState
import com.example.smartlens.model.DocumentType
import com.example.smartlens.ui.navigation.Screen
import com.example.smartlens.viewmodel.DocumentViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    navController: NavController,
    documentTypeString: String,
    imageUriString: String,
    viewModel: DocumentViewModel = hiltViewModel()
) {
    val documentType = try {
        DocumentType.valueOf(documentTypeString)
    } catch (e: Exception) {
        DocumentType.UNKNOWN
    }

    val imageUri = Uri.parse(imageUriString)
    val processingState by viewModel.processingState.collectAsState()
    val currentDocument by viewModel.currentDocument.collectAsState()
    val extractedText by viewModel.extractedText.collectAsState()

    // Estado de animación
    var currentStep by remember { mutableStateOf(1) }
    var progress by remember { mutableStateOf(0.05f) }

    // Estado para mostrar y determinar la duración de procesamiento
    var elapsedTime by remember { mutableStateOf(0) }
    var processing by remember { mutableStateOf(true) }

    // Iniciar procesamiento si no está ya en curso
    LaunchedEffect(key1 = imageUri) {
        Log.d("ProcessingScreen", "Estado inicial: $processingState")
        if (processingState !is DocumentProcessingState.DocumentReady) {
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
            }
            is DocumentProcessingState.ProcessingDocument -> {
                // Animar progreso
                currentStep = 2
                for (i in 30..80) {
                    progress = i / 100f
                    delay(50)
                }
            }
            is DocumentProcessingState.DocumentReady -> {
                currentStep = 3
                progress = 1.0f
                processing = false // Detener el contador de tiempo

                delay(1500) // Esperar un momento antes de navegar para mostrar el progreso completo
                currentDocument?.let { document ->
                    navController.navigate("${Screen.DocumentDetails.route}/${document.id}") {
                        popUpTo(Screen.Home.route)
                    }
                }
            }
            is DocumentProcessingState.Error -> {
                processing = false // Detener el contador de tiempo
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
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.processing) + " " +
                                    documentType.getDisplayName(),
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Mostrar tiempo de procesamiento
                        Text(
                            text = String.format("Tiempo: %d segundos", elapsedTime),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        ProcessStep(
                            number = 1,
                            title = stringResource(R.string.extracting_text),
                            isCompleted = currentStep > 1,
                            isActive = currentStep == 1
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        ProcessStep(
                            number = 2,
                            title = stringResource(R.string.analyzing_document),
                            isCompleted = currentStep > 2,
                            isActive = currentStep == 2
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        ProcessStep(
                            number = 3,
                            title = stringResource(R.string.finalizing),
                            isCompleted = currentStep > 3,
                            isActive = currentStep == 3
                        )

                        // Mostrar una parte del texto extraído
                        if (extractedText.isNotEmpty() && currentStep >= 2) {
                            Spacer(modifier = Modifier.height(24.dp))

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

@Composable
fun ProcessStep(
    number: Int,
    title: String,
    isCompleted: Boolean,
    isActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = when {
                isCompleted -> MaterialTheme.colorScheme.primary
                isActive -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.size(32.dp),
            contentColor = when {
                isCompleted -> MaterialTheme.colorScheme.onPrimary
                isActive -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(number.toString())
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                isCompleted -> MaterialTheme.colorScheme.primary
                isActive -> MaterialTheme.colorScheme.onBackground
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}