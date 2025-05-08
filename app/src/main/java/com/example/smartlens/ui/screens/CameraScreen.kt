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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.smartlens.ui.navigation.Screen
import com.example.smartlens.viewmodel.DocumentViewModel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    navController: NavController,
    viewModel: DocumentViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember { mutableStateOf(false) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                // Guardar la imagen temporal y navegar
                val tempUri = viewModel.saveTemporaryImage(it)
                navigateToDocumentType(navController, tempUri.toString())
            } catch (e: Exception) {
                Log.e("CameraScreen", "Error al seleccionar imagen: ${e.message}", e)
                Toast.makeText(context, "Error al seleccionar imagen: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Ejecutor para tareas de cámara
    val executor = remember { ContextCompat.getMainExecutor(context) }

    // Controlador de cámara
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // Configuración de captura de imagen
    val imageCapture = remember { ImageCapture.Builder().build() }

    // Estado de carga
    var isTakingPicture by remember { mutableStateOf(false) }

    // Efecto para solicitar permisos
    LaunchedEffect(key1 = Unit) {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                        val previewView = PreviewView(ctx)

                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()

                                val preview = Preview.Builder().build().also {
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
                                    e.printStackTrace()
                                    Toast.makeText(context, "Error al inicializar la cámara: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "Error al obtener el proveedor de cámara: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }, executor)

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

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
                        IconButton(
                            onClick = { galleryLauncher.launch("image/*") }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = stringResource(R.string.select_from_gallery),
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Botón de captura
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .border(2.dp, Color.White, CircleShape),
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
                                                navigateToDocumentType(navController, uri.toString())
                                            },
                                            onError = { exception ->
                                                isTakingPicture = false
                                                Log.e("CameraScreen", "Error al capturar imagen: ${exception.message}", exception)
                                                Toast.makeText(
                                                    context,
                                                    "Error al capturar imagen: ${exception.localizedMessage}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .size(64.dp)
                                    .border(2.dp, Color.White, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = stringResource(R.string.capture),
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        // Espacio para balance visual
                        Box(modifier = Modifier.size(36.dp))
                    }
                }

                // Indicador de carga
                if (isTakingPicture) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
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
                        onClick = { requestPermissionLauncher.launch(Manifest.permission.CAMERA) }
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
            // Corregido: Eliminada la propiedad inclusive y la referencia a Camera.route
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
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "SmartLens_$timestamp")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        Log.d("CameraScreen", "Iniciando captura de imagen")
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
                    Log.e("CameraScreen", "Error en callback de imagen: ${exception.message}")
                    onError(exception)
                }
            }
        )
    } catch (e: Exception) {
        Log.e("CameraScreen", "Error al tomar imagen: ${e.message}", e)
        onError(ImageCaptureException(0, "Error al tomar imagen: ${e.localizedMessage}", e))
    }
}