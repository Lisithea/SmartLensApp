package com.example.smartlens.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.smartlens.R
import com.example.smartlens.model.DocumentProcessingState
import com.example.smartlens.ui.components.LocalSnackbarManager
import com.example.smartlens.ui.navigation.Screen
import com.example.smartlens.viewmodel.DocumentViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    navController: NavController,
    viewModel: DocumentViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarManager = LocalSnackbarManager.current
    val coroutineScope = rememberCoroutineScope()
    val TAG = "CameraScreen"

    var hasCameraPermission by remember { mutableStateOf(false) }
    var hasStoragePermission by remember { mutableStateOf(false) }
    var lastImageUri by remember { mutableStateOf<Uri?>(null) }
    val processingState by viewModel.processingState.collectAsState()

    // Estados para animación de escáner
    val infiniteTransition = rememberInfiniteTransition(label = "scannerTransition")
    val scanLineY = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
        ),
        label = "scannerAnimation"
    )

    // Lanzadores para solicitar permisos
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (!granted) {
                snackbarManager?.showError(context.getString(R.string.camera_permission_required))
            }
        }
    )

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasStoragePermission = granted
            if (!granted) {
                snackbarManager?.showError(context.getString(R.string.storage_permission_required))
            }
        }
    )

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                Log.d(TAG, "Imagen seleccionada de galería: $uri")
                snackbarManager?.showInfo(context.getString(R.string.processing))

                lastImageUri = uri
                coroutineScope.launch {
                    // Guardar la imagen temporal para procesarla
                    val tempUri = viewModel.saveTemporaryImage(uri)
                    // Mostrar diálogo de progreso
                    navigateToDocumentType(navController, tempUri.toString())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al procesar imagen de galería: ${e.message}", e)
                snackbarManager?.showError("Error: ${e.message}")
            }
        }
    }

    // Ejecutor para tareas de cámara
    val executor = remember { ContextCompat.getMainExecutor(context) }

    // Controlador de cámara
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // Configuración de captura de imagen
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    // Estado de carga
    var isTakingPicture by remember { mutableStateOf(false) }

    // Efectos para solicitar permisos al inicio
    LaunchedEffect(key1 = Unit) {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)

        // Verificar qué permiso de almacenamiento solicitar según la versión de Android
        val storagePermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        storagePermissionLauncher.launch(storagePermission)
    }

    // Observar cambios en el estado de procesamiento
    LaunchedEffect(key1 = processingState) {
        when (processingState) {
            is DocumentProcessingState.DocumentReady -> {
                // Si el documento está listo y tenemos su URI, mostramos mensaje de éxito
                snackbarManager?.showSuccess(context.getString(R.string.document_saved))
            }
            is DocumentProcessingState.Error -> {
                // Si hay un error, mostramos mensaje de error
                val errorMsg = (processingState as DocumentProcessingState.Error).message
                snackbarManager?.showError("Error: $errorMsg")
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capture)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // Botón para seleccionar de galería
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = stringResource(R.string.select_from_gallery)
                        )
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
            if (hasCameraPermission) {
                // Vista de cámara
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            // Configurar para mejor visualización
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }

                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()

                                val preview = Preview.Builder()
                                    .build()
                                    .also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageCapture
                                    )

                                } catch (e: Exception) {
                                    Log.e(TAG, "Error al vincular casos de uso de cámara: ${e.message}", e)
                                    snackbarManager?.showError("Error al inicializar la cámara: ${e.localizedMessage}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error al obtener proveedor de cámara: ${e.message}", e)
                                snackbarManager?.showError("Error al obtener el proveedor de cámara: ${e.localizedMessage}")
                            }
                        }, executor)

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Recuadro guía para la cámara con animación de escáner
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Cuadro de límite para el documento
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4/3f)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .drawWithContent {
                                drawContent()
                                // Línea de escaneo animada
                                val y = scanLineY.value * size.height
                                drawLine(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.0f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.0f),
                                        )
                                    ),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 5.dp.toPx()
                                )
                            }
                    )
                }

                // Instrucciones
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Coloca el documento dentro del recuadro",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // Controles de cámara
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón para abrir galería
                        FilledTonalIconButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = stringResource(R.string.select_from_gallery),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Botón de captura
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = {
                                    if (!isTakingPicture) {
                                        isTakingPicture = true
                                        takePicture(
                                            context = context,
                                            viewModel = viewModel,
                                            imageCapture = imageCapture,
                                            executor = executor,
                                            onImageCaptured = { uri ->
                                                isTakingPicture = false
                                                lastImageUri = uri
                                                // Mostrar un mensaje de confirmación
                                                snackbarManager?.showSuccess("Imagen capturada correctamente")
                                                // Navegar a la pantalla de tipo de documento
                                                navigateToDocumentType(navController, uri.toString())
                                            },
                                            onError = { exception ->
                                                isTakingPicture = false
                                                Log.e(TAG, "Error al capturar imagen: ${exception.message}", exception)
                                                snackbarManager?.showError("Error al capturar imagen: ${exception.localizedMessage}")
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .size(64.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = stringResource(R.string.capture),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        // Invertir cámara
                        FilledTonalIconButton(
                            onClick = { /* Implementar cambio de cámara */ },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlipCameraAndroid,
                                contentDescription = "Invertir cámara",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Indicador de carga
                if (isTakingPicture) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .width(200.dp)
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Capturando imagen...")
                            }
                        }
                    }
                }
            } else {
                // Sin permiso de cámara
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.camera_permission_required),
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Para capturar documentos, necesitamos acceso a la cámara",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                    ) {
                        Text(stringResource(R.string.grant_permission))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") }
                    ) {
                        Text(stringResource(R.string.select_from_gallery))
                    }
                }
            }

            // Si hay un último documento escaneado, mostrar una miniatura
            lastImageUri?.let { uri ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .size(60.dp)
                            .clickable {
                                // Navegar a la vista de detalles si ya hay un documento procesado
                                if (processingState is DocumentProcessingState.DocumentReady) {
                                    val document = (processingState as DocumentProcessingState.DocumentReady).document
                                    navController.navigate("${Screen.DocumentDetails.route}/${document.id}")
                                } else {
                                    // Si no, volver a procesar la última imagen
                                    uri.toString().let { uriStr ->
                                        navigateToDocumentType(navController, uriStr)
                                    }
                                }
                            }
                    ) {
                        Image(
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = "Último documento",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_launcher_foreground)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Función mejorada para navegar a la pantalla de selección de tipo de documento
 */
private fun navigateToDocumentType(navController: NavController, uriString: String) {
    try {
        Log.d("CameraScreen", "Navegando a DocumentType con URI: $uriString")
        navController.navigate("${Screen.DocumentType.route}/$uriString")
    } catch (e: Exception) {
        Log.e("CameraScreen", "Error al navegar: ${e.message}", e)
    }
}

/**
 * Función mejorada para tomar una foto con feedback visual de progreso
 */
private fun takePicture(
    context: Context,
    viewModel: DocumentViewModel,
    imageCapture: ImageCapture,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    try {
        // Crear un nombre único para la imagen
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

        // Crear el directorio si no existe
        val appDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SmartLens")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }

        // Crear el archivo para la imagen
        val photoFile = File(appDir, "SmartLens_$timestamp.jpg")

        // Configurar opciones de salida para almacenamiento interno
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        Log.d("CameraScreen", "Iniciando captura de imagen: ${photoFile.absolutePath}")

        // Capturar la imagen con alta calidad
        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    Log.d("CameraScreen", "Imagen guardada en: $savedUri")

                    // Guardar una copia en el directorio de la aplicación
                    try {
                        val tempUri = viewModel.saveTemporaryImage(savedUri)
                        Log.d("CameraScreen", "Copia guardada en: $tempUri")
                        onImageCaptured(tempUri)
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Error al guardar copia temporal: ${e.message}", e)
                        onImageCaptured(savedUri)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraScreen", "Error en callback de imagen: ${exception.message}", exception)
                    onError(exception)
                }
            }
        )
    } catch (e: Exception) {
        Log.e("CameraScreen", "Error al tomar imagen: ${e.message}", e)
        onError(ImageCaptureException(0, "Error al tomar imagen: ${e.localizedMessage}", e))
    }
}