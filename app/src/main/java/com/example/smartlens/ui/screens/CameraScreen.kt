package com.example.smartlens.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Environment
import android.util.Log
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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

    // Estados básicos
    var hasCameraPermission by remember { mutableStateOf(false) }
    var hasStoragePermission by remember { mutableStateOf(false) }
    var lastImageUri by remember { mutableStateOf<Uri?>(null) }
    var isTakingPicture by remember { mutableStateOf(false) }
    val processingState by viewModel.processingState.collectAsState()

    // Configuración de captura de imagen
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

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
                snackbarManager?.showInfo(context.getString(R.string.processing))
                lastImageUri = uri
                coroutineScope.launch {
                    val tempUri = viewModel.saveTemporaryImage(uri)
                    navigateToDocumentType(navController, tempUri.toString())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al procesar imagen de galería: ${e.message}", e)
                snackbarManager?.showError("Error: ${e.message}")
            }
        }
    }

    // Solicitar permisos al inicio
    LaunchedEffect(Unit) {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        val storagePermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        storagePermissionLauncher.launch(storagePermission)
    }

    // UI principal
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
                // Vista previa de cámara
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        val executor = ContextCompat.getMainExecutor(ctx)

                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build()
                                preview.setSurfaceProvider(previewView.surfaceProvider)

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageCapture
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error al inicializar cámara: ${e.message}", e)
                                snackbarManager?.showError("Error al inicializar la cámara")
                            }
                        }, executor)

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Botón de captura
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    // Botón circular de captura
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .clickable {
                                if (!isTakingPicture) {
                                    isTakingPicture = true
                                    takePhoto(
                                        context = context,
                                        imageCapture = imageCapture,
                                        onSuccess = { uri ->
                                            isTakingPicture = false
                                            lastImageUri = uri
                                            snackbarManager?.showSuccess("Imagen capturada")
                                            navigateToDocumentType(navController, uri.toString())
                                        },
                                        onError = { error ->
                                            isTakingPicture = false
                                            snackbarManager?.showError("Error: $error")
                                        }
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Capturar",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
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
                        Card {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Procesando imagen...")
                            }
                        }
                    }
                }
            } else {
                // Sin permiso de cámara - mostrar mensaje
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

                    Spacer(modifier = Modifier.height(16.dp))

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

            // Miniatura del último documento
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
                                if (processingState is DocumentProcessingState.DocumentReady) {
                                    val document = (processingState as DocumentProcessingState.DocumentReady).document
                                    navController.navigate("${Screen.DocumentDetails.route}/${document.id}")
                                } else {
                                    navigateToDocumentType(navController, uri.toString())
                                }
                            }
                    ) {
                        Image(
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = "Último documento",
                            contentScale = ContentScale.Crop,
                            painter = painterResource(id = R.drawable.ic_launcher_foreground)
                        )
                    }
                }
            }
        }
    }
}

// Función para tomar una foto - ahora completamente separada de los Composables
private fun takePhoto(
    context: android.content.Context,
    imageCapture: ImageCapture,
    onSuccess: (Uri) -> Unit,
    onError: (String) -> Unit
) {
    Log.d("CameraScreen", "Iniciando captura de foto")

    // Crear archivo para guardar la foto
    val photoFile = File(
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
        "SmartLens_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
    )

    // Configurar opciones de salida
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    // Capturar la imagen
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                Log.d("CameraScreen", "Imagen guardada exitosamente en: $savedUri")
                onSuccess(savedUri)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraScreen", "Error al capturar: ${exception.message}", exception)
                onError(exception.message ?: "Error desconocido")
            }
        }
    )
}

// Función para navegar a la pantalla de selección de tipo de documento
private fun navigateToDocumentType(navController: NavController, uriString: String) {
    try {
        Log.d("CameraScreen", "Navegando a DocumentTypeScreen con URI: $uriString")
        navController.navigate("${Screen.DocumentType.route}/$uriString")
    } catch (e: Exception) {
        Log.e("CameraScreen", "Error al navegar: ${e.message}", e)
    }
}