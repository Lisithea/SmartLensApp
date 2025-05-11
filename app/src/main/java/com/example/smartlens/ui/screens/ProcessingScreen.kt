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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.smartlens.R
import com.example.smartlens.model.DocumentProcessingState
import com.example.smartlens.model.DocumentType
import com.example.smartlens.ui.components.LocalSnackbarManager
import com.example.smartlens.ui.navigation.NavigationActions
import com.example.smartlens.viewmodel.DocumentViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "ProcessingScreen"

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

    // Parse parameters
    val documentType = try {
        DocumentType.valueOf(documentTypeString)
    } catch (e: Exception) {
        Log.e(TAG, "Invalid document type: $documentTypeString", e)
        DocumentType.UNKNOWN
    }

    // Decode URI and parse
    val decodedUri = Uri.decode(imageUriString)
    val imageUri = Uri.parse(decodedUri)

    // ViewModel states
    val processingState by viewModel.processingState.collectAsState()
    val currentDocument by viewModel.currentDocument.collectAsState()
    val extractedText by viewModel.extractedText.collectAsState()
    val structuredData by viewModel.structuredData.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    // Animation states
    var currentStep by remember { mutableStateOf(1) }
    var progress by remember { mutableStateOf(0.05f) }

    // Process duration tracking
    var elapsedTime by remember { mutableStateOf(0) }
    var processing by remember { mutableStateOf(true) }

    // Detailed info display state
    var showRawData by remember { mutableStateOf(false) }

    // Error handling and retry states
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isProcessingCompleted by remember { mutableStateOf(false) }
    var hasTriedProcessing by remember { mutableStateOf(false) }

    // Current processing state text
    val processingStateText = remember(processingState) {
        when (processingState) {
            is DocumentProcessingState.Idle -> "Starting processing..."
            is DocumentProcessingState.Capturing -> "Capturing image..."
            is DocumentProcessingState.ExtractingText -> "Extracting text..."
            is DocumentProcessingState.ProcessingDocument -> "Analyzing document..."
            is DocumentProcessingState.DocumentReady -> "Document ready!"
            is DocumentProcessingState.Error -> "Error: ${(processingState as DocumentProcessingState.Error).message}"
            else -> "Processing..."
        }
    }

    // Visual effect animations
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

    // Start processing if not already in progress or completed
    LaunchedEffect(key1 = imageUri, key2 = documentType) {
        if (!isProcessingCompleted && !hasTriedProcessing) {
            Log.d(TAG, "⚠️ Starting processing. State: $processingState, Type: $documentType, Image: $imageUri")
            hasError = false
            processing = true
            hasTriedProcessing = true

            snackbarManager?.showInfo("Processing document...")

            try {
                // Update UI to show we're processing
                currentStep = 1
                progress = 0.2f

                // Save temporary image first to ensure it's available
                val tempImageUri = viewModel.saveTemporaryImage(imageUri)

                // Start document processing with the temporary URI
                viewModel.setProcessedImageUri(tempImageUri)
                viewModel.processDocument(documentType)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting processing: ${e.message}", e)
                hasError = true
                errorMessage = e.message ?: "Unknown error processing document"
                processing = false
            }
        }
    }

    // Processing time counter
    LaunchedEffect(key1 = processing) {
        while (processing) {
            delay(1000) // Increment every second
            elapsedTime++

            // Consider something went wrong if it takes too long
            if (elapsedTime > 60 && !isProcessingCompleted && isProcessing) {
                hasError = true
                errorMessage = "Processing is taking too long. There might be an issue with the API Key or Internet connection."
                processing = false
                viewModel.cancelProcessing()
            }
        }
    }

    // Handle processing state changes and animations
    LaunchedEffect(key1 = processingState) {
        Log.d(TAG, "⚠️ Processing state updated: $processingState")

        when (processingState) {
            is DocumentProcessingState.ExtractingText -> {
                Log.d(TAG, "State: Extracting text")
                currentStep = 1
                progress = 0.3f
                snackbarManager?.showInfo("Extracting text from document...")
            }
            is DocumentProcessingState.ProcessingDocument -> {
                Log.d(TAG, "State: Processing document")
                // Animate progress
                currentStep = 2
                for (i in 30..80) {
                    progress = i / 100f
                    delay(50)
                }
                snackbarManager?.showInfo("Analyzing document...")
            }
            is DocumentProcessingState.DocumentReady -> {
                Log.d(TAG, "State: Document ready")
                currentStep = 3
                progress = 1.0f
                processing = false // Stop the time counter
                isProcessingCompleted = true
                snackbarManager?.showSuccess("Document processed successfully!")

                delay(1000) // Wait a moment before navigating to show complete progress
                currentDocument?.let { document ->
                    Log.d(TAG, "Navigating to document details: ${document.id}")
                    NavigationActions.navigateToDocumentDetails(navController, document.id)
                }
            }
            is DocumentProcessingState.Error -> {
                val error = (processingState as DocumentProcessingState.Error).message
                Log.e(TAG, "Error in processing: $error")
                hasError = true
                errorMessage = error
                processing = false // Stop the time counter
                snackbarManager?.showError("Error: $error")
            }
            else -> {
                // Other states
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.processing)) },
                navigationIcon = {
                    IconButton(onClick = {
                        // Cancel processing and go back
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
                hasError -> {
                    // Show error with retry option
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
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = {
                                    // Reset state
                                    hasError = false
                                    processing = true
                                    elapsedTime = 0
                                    currentStep = 1
                                    progress = 0.05f
                                    hasTriedProcessing = false

                                    // Restart processing
                                    coroutineScope.launch {
                                        try {
                                            // Try to save the URI again first
                                            val tempImageUri = viewModel.saveTemporaryImage(imageUri)
                                            viewModel.setProcessedImageUri(tempImageUri)
                                            viewModel.processDocument(documentType)
                                        } catch (e: Exception) {
                                            hasError = true
                                            errorMessage = e.message ?: "Error retrying"
                                            processing = false
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry")
                            }

                            OutlinedButton(
                                onClick = { navController.navigateUp() }
                            ) {
                                Text(stringResource(R.string.back))
                            }
                        }

                        // Additional debug info
                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedButton(
                            onClick = { showRawData = !showRawData }
                        ) {
                            Text(if (showRawData) "Hide debug info" else "Show debug info")
                        }

                        if (showRawData) {
                            Spacer(modifier = Modifier.height(16.dp))

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Processing state: $processingState",
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Document type: $documentType",
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Image URI: $imageUri",
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Current document: ${currentDocument?.id ?: "None"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    if (extractedText.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = "Extracted text (${extractedText.length} characters):\n${extractedText.take(300)}...",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    // Show progress
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Animated title with pulse
                        Text(
                            text = processingStateText,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.alpha(pulseAlpha.value)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Show processing time
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
                                    text = String.format("Time: %d seconds", elapsedTime),
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                // Circular progress with percentage
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = progress,
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

                        // Progress bar
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Process steps with animation
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

                        // Show part of extracted text
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
                                Text(if (showRawData) "Hide extracted data" else "Show extracted data")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (showRawData) {
                                // Show structured data if available
                                if (structuredData.isNotEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Detected structured data",
                                                style = MaterialTheme.typography.titleSmall
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Show each key-value pair
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
                                                        fontWeight = FontWeight.Bold,
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

                                            // If there are more than 5 items, show a message
                                            if (structuredData.size > 5) {
                                                Text(
                                                    text = "And ${structuredData.size - 5} more fields...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier
                                                        .align(Alignment.End)
                                                        .padding(top = 8.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                // Extracted text
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = stringResource(R.string.extracted_text),
                                            style = MaterialTheme.typography.titleSmall
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Show only part of the text
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
    // Animation to pulse if active
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
            // Number circle or check icon
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

            // Title and icon
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
            }

            // Status icon
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor
            )
        }
    }
}