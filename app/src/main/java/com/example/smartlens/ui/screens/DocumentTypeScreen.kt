package com.example.smartlens.ui.screens

import android.net.Uri
import android.util.Log
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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.smartlens.R
import com.example.smartlens.model.DocumentProcessingState
import com.example.smartlens.model.DocumentType
import com.example.smartlens.ui.components.LocalSnackbarManager
import com.example.smartlens.ui.navigation.Screen
import com.example.smartlens.viewmodel.DocumentViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentTypeScreen(
    navController: NavController,
    imageUriString: String,
    viewModel: DocumentViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarManager = LocalSnackbarManager.current
    val coroutineScope = rememberCoroutineScope()
    val imageUri = Uri.parse(imageUriString)

    val processingState by viewModel.processingState.collectAsState()
    val extractedText by viewModel.extractedText.collectAsState()

    // Estados locales
    var selectedType by remember { mutableStateOf<DocumentType?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var textExtractionComplete by remember { mutableStateOf(false) }
    var showTextPreview by remember { mutableStateOf(false) }
    var customFileName by remember { mutableStateOf("") }
    var showCustomFileNameDialog by remember { mutableStateOf(false) }

    // Iniciar procesamiento de OCR
    LaunchedEffect(key1 = imageUri) {
        try {
            Log.d("DocumentTypeScreen", "Iniciando procesamiento de imagen: $imageUri")
            snackbarManager?.showInfo("Procesando imagen...")
            viewModel.processImage(imageUri)
        } catch (e: Exception) {
            Log.e("DocumentTypeScreen", "Error al procesar imagen: ${e.message}", e)
            snackbarManager?.showError("Error: ${e.message}")
            isLoading = false
        }
    }

    // Observar cambios en estado de procesamiento
    LaunchedEffect(key1 = processingState) {
        when (processingState) {
            is DocumentProcessingState.ExtractingText -> {
                isLoading = true
                Log.d("DocumentTypeScreen", "Extrayendo texto...")
            }
            is DocumentProcessingState.ProcessingDocument -> {
                isLoading = false
                textExtractionComplete = true
                selectedType = (processingState as DocumentProcessingState.ProcessingDocument).documentType
                Log.d("DocumentTypeScreen", "Tipo detectado: $selectedType")

                // Mostrar mensaje de tipo detectado
                snackbarManager?.showInfo("Tipo de documento detectado: ${selectedType?.getDisplayName() ?: "Desconocido"}")
            }
            is DocumentProcessingState.Error -> {
                isLoading = false
                Log.e("DocumentTypeScreen", "Error: ${(processingState as DocumentProcessingState.Error).message}")
                snackbarManager?.showError("Error: ${(processingState as DocumentProcessingState.Error).message}")
            }
            is DocumentProcessingState.DocumentReady -> {
                // Si el documento está listo, redirigir a detalles
                val document = (processingState as DocumentProcessingState.DocumentReady).document
                navController.navigate("${Screen.DocumentDetails.route}/${document.id}") {
                    popUpTo(Screen.Camera.route)
                }
            }
            else -> {}
        }
    }

    // Animación para la carga
    val progressAlpha by animateFloatAsState(
        targetValue = if (isLoading) 1f else 0f,
        label = "loadingAnimation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.document_type)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancelProcessing()
                        navController.navigateUp()
                    }) {
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
            // Vista previa de la imagen
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            ImageRequest.Builder(LocalContext.current)
                                .data(data = imageUri)
                                .build()
                        ),
                        contentDescription = "Vista previa de documento",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Overlay de carga
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .alpha(progressAlpha),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.extracting_text),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Extracto de texto (expansible)
            AnimatedVisibility(
                visible = extractedText.isNotEmpty() && textExtractionComplete,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.extracted_text),
                            style = MaterialTheme.typography.titleMedium
                        )

                        IconButton(onClick = { showTextPreview = !showTextPreview }) {
                            Icon(
                                imageVector = if (showTextPreview) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Mostrar/ocultar texto"
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showTextPreview,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = if (extractedText.length > 500)
                                    extractedText.substring(0, 500) + "..."
                                else extractedText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }

            // Estado de procesamiento o mensaje
            AnimatedVisibility(
                visible = !isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (processingState is DocumentProcessingState.Error) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = (processingState as DocumentProcessingState.Error).message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Seleccione el tipo de documento manualmente:",
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Text(
                            text = stringResource(
                                R.string.detected_document_type,
                                selectedType?.getDisplayName() ?: stringResource(R.string.unknown_document)
                            ),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.change_document_type),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Opciones de tipos de documento
            DocumentTypeOption(
                type = DocumentType.INVOICE,
                isSelected = selectedType == DocumentType.INVOICE,
                onSelect = { selectedType = DocumentType.INVOICE }
            )

            Spacer(modifier = Modifier.height(8.dp))

            DocumentTypeOption(
                type = DocumentType.DELIVERY_NOTE,
                isSelected = selectedType == DocumentType.DELIVERY_NOTE,
                onSelect = { selectedType = DocumentType.DELIVERY_NOTE }
            )

            Spacer(modifier = Modifier.height(8.dp))

            DocumentTypeOption(
                type = DocumentType.WAREHOUSE_LABEL,
                isSelected = selectedType == DocumentType.WAREHOUSE_LABEL,
                onSelect = { selectedType = DocumentType.WAREHOUSE_LABEL }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Opción para nombre de archivo personalizado
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showCustomFileNameDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar nombre",
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nombre personalizado para exportación",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (customFileName.isNotBlank()) {
                            Text(
                                text = customFileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (customFileName.isNotBlank()) {
                        IconButton(onClick = { customFileName = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Borrar nombre"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Botón de continuar
            Button(
                onClick = {
                    selectedType?.let { type ->
                        if (customFileName.isNotBlank()) {
                            viewModel.setCustomFileName(customFileName)
                        }

                        coroutineScope.launch {
                            try {
                                // Mostrar diálogo de carga
                                snackbarManager?.showInfo("Procesando documento...")

                                // Procesar documento
                                viewModel.processDocument(type)

                                // Navegar a la pantalla de procesamiento
                                navController.navigate("${Screen.Processing.route}/${type.name}/${imageUriString}") {
                                    popUpTo(Screen.Camera.route)
                                }
                            } catch (e: Exception) {
                                snackbarManager?.showError("Error: ${e.message}")
                            }
                        }
                    }
                },
                enabled = selectedType != null && !isLoading,
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
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.continue_text))
                }
            }
        }
    }

    // Diálogo para nombre personalizado
    if (showCustomFileNameDialog) {
        AlertDialog(
            onDismissRequest = { showCustomFileNameDialog = false },
            title = { Text("Nombre personalizado") },
            text = {
                Column {
                    Text("Introduce un nombre personalizado para el archivo Excel:")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = customFileName,
                        onValueChange = { customFileName = it },
                        label = { Text("Nombre del archivo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCustomFileNameDialog = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    customFileName = ""
                    showCustomFileNameDialog = false
                }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun DocumentTypeOption(
    type: DocumentType,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onSelect
            ),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                RadioButton(
                    selected = isSelected,
                    onClick = onSelect
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = type.getDisplayName(),
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = when (type) {
                        DocumentType.INVOICE -> stringResource(R.string.invoice_description)
                        DocumentType.DELIVERY_NOTE -> stringResource(R.string.delivery_note_description)
                        DocumentType.WAREHOUSE_LABEL -> stringResource(R.string.warehouse_label_description)
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}