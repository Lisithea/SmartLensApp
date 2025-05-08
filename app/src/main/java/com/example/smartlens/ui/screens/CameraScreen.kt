package com.example.smartlens.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.smartlens.R
import com.example.smartlens.ui.components.LocalSnackbarManager
import com.example.smartlens.ui.navigation.Screen
import com.example.smartlens.viewmodel.DocumentViewModel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
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
    val TAG = "CameraScreen"

    var hasCameraPermission by remember { mutableStateOf(false) }
    var hasStoragePermission by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (!granted) {
                snackbarManager?.showError("Se requiere permiso de cámara para escanear documentos")
            }
        }
    )

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasStoragePermission = granted
            if (!granted) {
                snackbarManager?.showError("Se requiere permiso de almacenamiento para guardar documentos")
            }
        }
    )

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                Log.d(TAG, "Imagen seleccionada de galería: $uri")
                // Mostrar un mensaje de carga
                snackbarManager?.showInfo("Procesando imagen...")
                // Guardar la imagen temporal y navegar
                val tempUri = viewModel.saveTemporaryImage(it)
                navigateToDocumentType(navController, tempUri.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error al seleccionar imagen: ${e.message}", e)
                snackbarManager?.showError("Error al seleccionar imagen: ${e.message}")
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

    // Efectos para solicitar permisos
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

    // Cuando el OCR falla, necesitamos un modo de depuración
    var debugMode by remember { mutableStateOf(false) }
    var debugInfo by remember { mutableStateOf("") }

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
                    // Botón para activar/desactivar el modo de depuración
                    IconButton(onClick = { debugMode = !debugMode }) {
                        Icon(
                            imageVector = if (debugMode) Icons.Default.BugReport else Icons.Default.Settings,
                            contentDescription = "Modo de depuración"
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

                                    if (debugMode) {
                                        debugInfo = "Cámara inicializada correctamente"
                                    }

                                } catch (e: Exception) {
                                    Log.e(TAG, "Error al vincular casos de uso de cámara: ${e.message}", e)
                                    if (debugMode) {
                                        debugInfo = "Error al inicializar cámara: ${e.message}"
                                    }
                                    snackbarManager?.showError("Error al inicializar la cámara: ${e.localizedMessage}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error al obtener proveedor de cámara: ${e.message}", e)
                                if (debugMode) {
                                    debugInfo = "Error al obtener proveedor de cámara: ${e.message}"
                                }
                                snackbarManager?.showError("Error al obtener el proveedor de cámara: ${e.localizedMessage}")
                            }
                        }, executor)

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Recuadro guía para la cámara
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4/3f)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    )
                }

                // Controles de cámara
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Instrucciones
                    Text(
                        text = "Coloca el documento dentro del recuadro",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

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
                                                if (debugMode) {
                                                    debugInfo = "Imagen capturada: $uri"
                                                }
                                                navigateToDocumentType(navController, uri.toString())
                                            },
                                            onError = { exception ->
                                                isTakingPicture = false
                                                if (debugMode) {
                                                    debugInfo = "Error al capturar: ${exception.message}"
                                                }
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

                // Modo de depuración
                if (debugMode) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Modo de depuración activado",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (debugInfo.isNotEmpty()) {
                            Text(
                                text = debugInfo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
        }
    }
}

private fun navigateToDocumentType(navController: NavController, uriString: String) {
    try {
        Log.d("CameraScreen", "Navegando a DocumentType con URI: $uriString")
        navController.navigate("${Screen.DocumentType.route}/$uriString") {
            popUpTo(navController.graph.findStartDestination().id)
        }
    } catch (e: Exception) {
        Log.e("CameraScreen", "Error al navegar: ${e.message}", e)
    }
}

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
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "SmartLens_$timestamp")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }

        // Configurar opciones de salida utilizando ContentResolver
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        Log.d("CameraScreen", "Iniciando captura de imagen")

        // Capturar la imagen con alta calidad
        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    outputFileResults.savedUri?.let { uri ->
                        Log.d("CameraScreen", "Imagen guardada en: $uri")
                        // Guardar una copia en el directorio de la aplicación
                        try {
                            val tempUri = viewModel.saveTemporaryImage(uri)
                            Log.d("CameraScreen", "Copia guardada en: $tempUri")
                            onImageCaptured(tempUri)
                        } catch (e: Exception) {
                            Log.e("CameraScreen", "Error al guardar copia temporal: ${e.message}", e)
                            onImageCaptured(uri)
                        }
                    } ?: run {
                        onError(ImageCaptureException(0, "URI nula al guardar imagen", null))
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