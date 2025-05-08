package com.example.smartlens.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.smartlens.model.DocumentProcessingState
import com.example.smartlens.viewmodel.DocumentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    navController: NavController,
    documentId: String,
    viewModel: DocumentViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val currentDocument by viewModel.currentDocument.collectAsState()
    val processingState by viewModel.processingState.collectAsState()

    // Estado para QR code
    var qrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Cargar documento
    LaunchedEffect(documentId) {
        viewModel.loadDocumentById(documentId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exportar documento") },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (currentDocument == null) {
                if (processingState is DocumentProcessingState.Error) {
                    // Mostrar error
                    val errorMessage = (processingState as DocumentProcessingState.Error).message

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(64.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Error al cargar documento",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    // Mostrar carga
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                // Contenido de exportación
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Opciones de exportación",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Opción: Exportar a Excel
                    ExportOption(
                        icon = Icons.Default.Description,
                        title = "Exportar a Excel",
                        description = "Genera un archivo Excel con los datos estructurados del documento",
                        onClick = {
                            currentDocument?.let {
                                viewModel.shareAsExcel(it)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Opción: Compartir documento
                    ExportOption(
                        icon = Icons.Default.Share,
                        title = "Compartir documento",
                        description = "Comparte el documento en formato JSON para abrir en PC",
                        onClick = {
                            currentDocument?.let {
                                viewModel.shareDocument(it)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Opción: Generar código QR
                    ExportOption(
                        icon = Icons.Default.QrCode,
                        title = "Generar código QR",
                        description = "Genera un código QR para acceder rápidamente al documento",
                        onClick = {
                            currentDocument?.let {
                                qrCodeBitmap = viewModel.generateQrCode(it)
                            }
                        }
                    )

                    // Mostrar código QR si se ha generado
                    qrCodeBitmap?.let { bitmap ->
                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "Código QR generado",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier
                                .size(250.dp)
                                .padding(8.dp)
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Código QR",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Escanea este código para abrir el documento en otro dispositivo",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}