package com.example.smartlens.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.smartlens.R
import com.example.smartlens.model.LogisticsDocument
import com.example.smartlens.ui.components.DeleteConfirmationDialog
import com.example.smartlens.ui.components.LocalSnackbarManager
import com.example.smartlens.ui.navigation.Screen
import com.example.smartlens.service.MotivationalQuotesService
import com.example.smartlens.service.UserProfileManager
import com.example.smartlens.viewmodel.DocumentViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: DocumentViewModel = hiltViewModel(),
    userProfileManager: UserProfileManager,
    motivationalQuotesService: MotivationalQuotesService
) {
    val recentDocuments by viewModel.recentDocuments.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val context = LocalContext.current
    val snackbarManager = LocalSnackbarManager.current

    // Estado para la búsqueda
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Estado para diálogo de confirmación de eliminación
    var showDeleteDialog by remember { mutableStateOf(false) }
    var documentToDelete by remember { mutableStateOf<LogisticsDocument?>(null) }

    // Mostrar mensaje al usuario si hay alguno
    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarManager?.showInfo(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                // Mostrar barra de búsqueda cuando está activa
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { /* Realizar búsqueda */ },
                    active = true,
                    onActiveChange = { isSearchActive = it },
                    placeholder = { Text(stringResource(R.string.search)) },
                    leadingIcon = {
                        IconButton(onClick = { isSearchActive = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                        }
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
                            }
                        }
                    }
                ) {
                    // Resultados de la búsqueda se muestran aquí
                    // Si se implementa una búsqueda avanzada en el futuro
                }
            } else {
                // Mostrar barra de título normal
                TopAppBar(
                    title = { Text(stringResource(R.string.navigation_home)) },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.Camera.route) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.capture))
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (recentDocuments.isEmpty()) {
                EmptyDocumentsList(modifier = Modifier.align(Alignment.Center))
            } else {
                DocumentsList(
                    documents = if (searchQuery.isEmpty()) recentDocuments
                    else recentDocuments.filter { filterDocument(it, searchQuery) },
                    onDocumentClick = { document ->
                        navController.navigate("${Screen.DocumentDetails.route}/${document.id}")
                    },
                    onDeleteClick = { document ->
                        documentToDelete = document
                        showDeleteDialog = true
                    }
                )
            }
        }
    }

    // Diálogo de confirmación de eliminación
    if (showDeleteDialog && documentToDelete != null) {
        DeleteConfirmationDialog(
            onConfirm = {
                documentToDelete?.let { viewModel.deleteDocument(it.id) }
            },
            onDismiss = {
                showDeleteDialog = false
                documentToDelete = null
            }
        )
    }
}

/**
 * Filtra un documento en base a una consulta de búsqueda
 */
private fun filterDocument(document: LogisticsDocument, query: String): Boolean {
    val searchText = query.lowercase()

    return when (document) {
        is com.example.smartlens.model.Invoice -> {
            document.invoiceNumber.lowercase().contains(searchText) ||
                    document.supplier.name.lowercase().contains(searchText) ||
                    document.client.name.lowercase().contains(searchText) ||
                    document.items.any { it.description.lowercase().contains(searchText) }
        }
        is com.example.smartlens.model.DeliveryNote -> {
            document.deliveryNoteNumber.lowercase().contains(searchText) ||
                    document.origin.name.lowercase().contains(searchText) ||
                    document.destination.name.lowercase().contains(searchText) ||
                    document.items.any { it.description.lowercase().contains(searchText) }
        }
        is com.example.smartlens.model.WarehouseLabel -> {
            document.labelId.lowercase().contains(searchText) ||
                    document.productName.lowercase().contains(searchText) ||
                    document.productCode.lowercase().contains(searchText)
        }
        else -> false
    }
}

@Composable
fun EmptyDocumentsList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.no_documents),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.scan_instructions),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun DocumentsList(
    documents: List<LogisticsDocument>,
    onDocumentClick: (LogisticsDocument) -> Unit,
    onDeleteClick: (LogisticsDocument) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(documents) { document ->
            DocumentItem(
                document = document,
                onClick = { onDocumentClick(document) },
                onDeleteClick = { onDeleteClick(document) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentItem(
    document: LogisticsDocument,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(document.timestamp))

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = document.getTypeDisplay(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = document.getIdentifier(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                when (document) {
                    is com.example.smartlens.model.Invoice -> {
                        Text(
                            text = "${stringResource(R.string.client)}: ${document.client.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${stringResource(R.string.total)}: ${document.totalAmount}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is com.example.smartlens.model.DeliveryNote -> {
                        Text(
                            text = "${stringResource(R.string.origin)}: ${document.origin.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${stringResource(R.string.destination)}: ${document.destination.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    is com.example.smartlens.model.WarehouseLabel -> {
                        Text(
                            text = "${stringResource(R.string.product_name)}: ${document.productName}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${stringResource(R.string.quantity)}: ${document.quantity}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_confirm),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}