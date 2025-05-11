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
import com.example.smartlens.ui.navigation.NavigationActions
import com.example.smartlens.viewmodel.DocumentViewModel
import kotlinx.coroutines.launch

private const val TAG = "DocumentTypeScreen"

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

    // Decode and parse URI
    val decodedUri = Uri.decode(imageUriString)
    val imageUri = Uri.parse(decodedUri)

    // State from ViewModel
    val processingState by viewModel.processingState.collectAsState()
    val extractedText by viewModel.extractedText.collectAsState()

    // Local states
    var selectedType by remember { mutableStateOf<DocumentType?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var textExtractionComplete by remember { mutableStateOf(false) }
    var showTextPreview by remember { mutableStateOf(false) }
    var customFileName by remember { mutableStateOf("") }
    var showCustomFileNameDialog by remember { mutableStateOf(false) }

    // Start OCR processing
    LaunchedEffect(key1 = imageUri) {
        try {
            Log.d(TAG, "Starting image processing: $imageUri")
            snackbarManager?.showInfo("Processing image...")
            viewModel.processImage(imageUri)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ${e.message}", e)
            snackbarManager?.showError("Error: ${e.message}")
            isLoading = false
        }
    }

    // Watch processing state changes
    LaunchedEffect(key1 = processingState) {
        when (processingState) {
            is DocumentProcessingState.ExtractingText -> {
                isLoading = true
                Log.d(TAG, "State: Extracting text...")
            }
            is DocumentProcessingState.ProcessingDocument -> {
                isLoading = false
                textExtractionComplete = true
                selectedType = (processingState as DocumentProcessingState.ProcessingDocument).documentType
                Log.d(TAG, "Detected type: $selectedType")

                // Show detected document type message
                snackbarManager?.showInfo("Detected document type: ${selectedType?.getDisplayName() ?: "Unknown"}")
            }
            is DocumentProcessingState.Error -> {
                isLoading = false
                Log.e(TAG, "Error: ${(processingState as DocumentProcessingState.Error).message}")
                snackbarManager?.showError("Error: ${(processingState as DocumentProcessingState.Error).message}")
            }
            is DocumentProcessingState.DocumentReady -> {
                // If document is ready, redirect to details
                val document = (processingState as DocumentProcessingState.DocumentReady).document
                NavigationActions.navigateToDocumentDetails(navController, document.id)
            }
            else -> {}
        }
    }

    // Loading animation
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            // Image preview
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
                        contentDescription = "Document preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Loading overlay
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                .alpha(progressAlpha),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.extracting_text),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Text extract (expandable)
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
                                contentDescription = "Show/hide text"
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

            // Processing state or message
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
                            text = "Select document type manually:",
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

            // Document type options
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

            // Custom filename option
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
                        contentDescription = "Edit name",
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Custom export name",
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
                                contentDescription = "Clear name"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Continue button
            Button(
                onClick = {
                    selectedType?.let { type ->
                        if (customFileName.isNotBlank()) {
                            viewModel.setCustomFileName(customFileName)
                        }

                        // Show loading dialog
                        snackbarManager?.showInfo("Processing document...")

                        // Navigate to processing screen
                        NavigationActions.navigateToProcessing(navController, type.name, imageUriString)
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

    // Custom filename dialog
    if (showCustomFileNameDialog) {
        AlertDialog(
            onDismissRequest = { showCustomFileNameDialog = false },
            title = { Text("Custom filename") },
            text = {
                Column {
                    Text("Enter a custom name for the Excel file:")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = customFileName,
                        onValueChange = { customFileName = it },
                        label = { Text("Filename") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCustomFileNameDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    customFileName = ""
                    showCustomFileNameDialog = false
                }) {
                    Text("Cancel")
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