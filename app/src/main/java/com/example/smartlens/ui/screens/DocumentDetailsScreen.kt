package com.example.smartlens.ui.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.smartlens.model.*
import com.example.smartlens.ui.navigation.Screen
import com.example.smartlens.viewmodel.DocumentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailsScreen(
    navController: NavController,
    documentId: String,
    viewModel: DocumentViewModel = hiltViewModel()
) {
    val currentDocument by viewModel.currentDocument.collectAsState()
    val processingState by viewModel.processingState.collectAsState()

    // Cargar documento por ID
    LaunchedEffect(documentId) {
        Log.d("DocumentDetailsScreen", "Cargando documento con ID: $documentId")
        viewModel.loadDocumentById(documentId)
    }

    // Observar cambios en el estado de procesamiento
    LaunchedEffect(processingState) {
        if (processingState is DocumentProcessingState.Error) {
            val error = (processingState as DocumentProcessingState.Error).message
            snackbarManager?.showError("Error: $error")
        }
    }

    // Observar cambios en el documento actual
    LaunchedEffect(currentDocument) {
        if (currentDocument != null) {
            Log.d("DocumentDetailsScreen", "Documento cargado correctamente: ${currentDocument!!.id}")
            snackbarManager?.showSuccess("Documento cargado")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    currentDocument?.let {
                        Text("${it.getTypeDisplay()}: ${it.getIdentifier()}")
                    } ?: Text("Detalles del documento")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            currentDocument?.let {
                                navController.navigate("${Screen.Export.route}/${documentId}")
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Exportar")
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
            when {
                currentDocument == null -> {
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
                }

                else -> {
                    // Mostrar detalles del documento
                    when (val document = currentDocument) {
                        is Invoice -> InvoiceDetails(document, navController)
                        is DeliveryNote -> DeliveryNoteDetails(document, navController)
                        is WarehouseLabel -> WarehouseLabelDetails(document, navController)
                        else -> {
                            // No debería ocurrir debido a los tipos sellados
                            Text("Tipo de documento no soportado")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InvoiceDetails(invoice: Invoice, navController: NavController) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Imagen del documento
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(LocalContext.current)
                            .data(data = invoice.imageUri)
                            .build()
                    ),
                    contentDescription = "Imagen del documento",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Información básica de factura
        item {
            Text(
                text = "Información de factura",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailItem(label = "Número de factura", value = invoice.invoiceNumber)
            DetailItem(label = "Fecha", value = invoice.date)
            invoice.dueDate?.let { DetailItem(label = "Vencimiento", value = it) }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Información de proveedor
        item {
            Text(
                text = "Proveedor",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailItem(label = "Nombre", value = invoice.supplier.name)
            invoice.supplier.taxId?.let { DetailItem(label = "NIF", value = it) }
            invoice.supplier.address?.let { DetailItem(label = "Dirección", value = it) }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Información de cliente
        item {
            Text(
                text = "Cliente",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailItem(label = "Nombre", value = invoice.client.name)
            invoice.client.taxId?.let { DetailItem(label = "NIF", value = it) }
            invoice.client.address?.let { DetailItem(label = "Dirección", value = it) }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Encabezado de artículos
        item {
            Text(
                text = "Artículos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Encabezados
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = "Descripción",
                    modifier = Modifier.weight(3f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Cant.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Precio",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Total",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Lista de artículos
        items(invoice.items) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(3f)) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    item.code?.let {
                        Text(
                            text = "Cód: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = item.quantity.toString(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "%.2f".format(item.unitPrice),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "%.2f".format(item.totalPrice),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Divider(modifier = Modifier.padding(horizontal = 8.dp))
        }

        // Totales
        item {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(8.dp)
                ) {
                    DetailRow(label = "Subtotal", value = "%.2f".format(invoice.subtotal))
                    DetailRow(label = "IVA", value = "%.2f".format(invoice.taxAmount))
                    Divider(modifier = Modifier.width(200.dp).padding(vertical = 8.dp))
                    DetailRow(
                        label = "TOTAL",
                        value = "%.2f".format(invoice.totalAmount),
                        isHighlighted = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            invoice.paymentTerms?.let {
                DetailItem(label = "Condiciones de pago", value = it)
            }

            invoice.notes?.let {
                DetailItem(label = "Notas", value = it)
            }
        }

        // Texto original
        item {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Texto extraído original",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = invoice.rawTextContent,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DeliveryNoteDetails(deliveryNote: DeliveryNote, navController: NavController) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Imagen del documento
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(LocalContext.current)
                            .data(data = deliveryNote.imageUri)
                            .build()
                    ),
                    contentDescription = "Imagen del documento",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Información básica de albarán
        item {
            Text(
                text = "Información de albarán",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailItem(label = "Número de albarán", value = deliveryNote.deliveryNoteNumber)
            DetailItem(label = "Fecha", value = deliveryNote.date)

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Información de origen
        item {
            Text(
                text = "Origen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailItem(label = "Nombre", value = deliveryNote.origin.name)
            DetailItem(label = "Dirección", value = deliveryNote.origin.address)
            deliveryNote.origin.contactPerson?.let { DetailItem(label = "Contacto", value = it) }
            deliveryNote.origin.contactPhone?.let { DetailItem(label = "Teléfono", value = it) }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Información de destino
        item {
            Text(
                text = "Destino",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailItem(label = "Nombre", value = deliveryNote.destination.name)
            DetailItem(label = "Dirección", value = deliveryNote.destination.address)
            deliveryNote.destination.contactPerson?.let { DetailItem(label = "Contacto", value = it) }
            deliveryNote.destination.contactPhone?.let { DetailItem(label = "Teléfono", value = it) }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Transportista (si está disponible)
        item {
            deliveryNote.carrier?.let {
                DetailItem(label = "Transportista", value = it)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Encabezado de artículos
        item {
            Text(
                text = "Artículos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Encabezados
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = "Descripción",
                    modifier = Modifier.weight(3f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Cant.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Embalaje",
                    modifier = Modifier.weight(1.5f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Peso",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Lista de artículos
        items(deliveryNote.items) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(3f)) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    item.code?.let {
                        Text(
                            text = "Cód: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = item.quantity.toString(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = item.packageType ?: "-",
                    modifier = Modifier.weight(1.5f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = item.weight?.toString() ?: "-",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Divider(modifier = Modifier.padding(horizontal = 8.dp))
        }

        // Totales
        item {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(8.dp)
                ) {
                    deliveryNote.totalPackages?.let {
                        DetailRow(label = "Total bultos", value = it.toString())
                    }
                    deliveryNote.totalWeight?.let {
                        DetailRow(label = "Peso total", value = "%.2f".format(it))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            deliveryNote.observations?.let {
                DetailItem(label = "Observaciones", value = it)
            }
        }

        // Texto original
        item {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Texto extraído original",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = deliveryNote.rawTextContent,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun WarehouseLabelDetails(label: WarehouseLabel, navController: NavController) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Imagen del documento
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(LocalContext.current)
                            .data(data = label.imageUri)
                            .build()
                    ),
                    contentDescription = "Imagen del documento",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Información de etiqueta
        item {
            Text(
                text = "Información de etiqueta",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailItem(label = "ID Etiqueta", value = label.labelId)
            DetailItem(label = "Código de producto", value = label.productCode)
            DetailItem(label = "Nombre de producto", value = label.productName)
            DetailItem(label = "Cantidad", value = label.quantity.toString())

            label.batchNumber?.let { DetailItem(label = "Número de lote", value = it) }
            label.expirationDate?.let { DetailItem(label = "Fecha de caducidad", value = it) }
            label.location?.let { DetailItem(label = "Ubicación", value = it) }
            label.barcode?.let { DetailItem(label = "Código de barras", value = it) }
        }

        // Texto original
        item {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Texto extraído original",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = label.rawTextContent,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }

    Divider(modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
fun DetailRow(label: String, value: String, isHighlighted: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(end = 16.dp)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
        )
    }
}