package com.example.smartlens.ui.screens

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.smartlens.R
import com.example.smartlens.model.DocumentProcessingState
import com.example.smartlens.model.DocumentType
import com.example.smartlens.ui.navigation.Screen
import com.example.smartlens.viewmodel.DocumentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentTypeScreen(
    navController: NavController,
    imageUriString: String,
    viewModel: DocumentViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val imageUri = Uri.parse(imageUriString)

    val processingState by viewModel.processingState.collectAsState()
    val extractedText by viewModel.extractedText.collectAsState()

    // Detectar tipo automáticamente
    var selectedType by remember { mutableStateOf<DocumentType?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var textExtractionComplete by remember { mutableStateOf(false) }

    // Iniciar procesamiento de OCR
    LaunchedEffect(key1 = imageUri) {
        try {
            Log.d("DocumentTypeScreen", "Iniciando procesamiento de imagen: $imageUri")
            viewModel.processImage(imageUri)
        } catch (e: Exception) {
            Log.e("DocumentTypeScreen", "Error al procesar imagen: ${e.message}", e)
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
            }
            is DocumentProcessingState.Error -> {
                isLoading = false
                Log.e("DocumentTypeScreen", "Error: ${(processingState as DocumentProcessingState.Error).message}")
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.document_type)) },
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

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.extracting_text),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mostrar texto extraído si está disponible
            if (extractedText.isNotEmpty() && textExtractionComplete) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.extracted_text),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (extractedText.length > 500) extractedText.substring(0, 500) + "..." else extractedText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Estado de procesamiento
            if (isLoading) {
                Text(
                    text = stringResource(R.string.analyzing_document),
                    style = MaterialTheme.typography.titleMedium
                )
            } else if (processingState is DocumentProcessingState.Error) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                }
            } else {
                Text(
                    text = "Tipo de documento detectado: ${selectedType?.getDisplayName() ?: "Desconocido"}",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Puede cambiar el tipo si la detección no es correcta:",
                    style = MaterialTheme.typography.bodyMedium
                )
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

            Spacer(modifier = Modifier.height(32.dp))

            // Botón de continuar
            Button(
                onClick = {
                    selectedType?.let { type ->
                        // Iniciar el procesamiento inmediatamente
                        viewModel.processDocument(type)
                        navController.navigate("${Screen.Processing.route}/${type.name}/${imageUriString}") {
                            // Corregido: Eliminar uso de 'inclusive' en popUpTo
                            popUpTo(Screen.Camera.route)
                        }
                    }
                },
                enabled = selectedType != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.continue_text))
            }
        }
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