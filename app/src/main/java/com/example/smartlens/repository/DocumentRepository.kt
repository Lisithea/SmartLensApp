package com.example.smartlens.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.smartlens.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.documentsDataStore: DataStore<Preferences> by preferencesDataStore(name = "documents")

@Singleton
class DocumentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val documentIds = stringPreferencesKey("document_ids")
    private val documentCache = mutableMapOf<String, LogisticsDocument>()

    /**
     * Guarda un documento y sus metadatos
     */
    suspend fun saveDocument(document: LogisticsDocument) = withContext(Dispatchers.IO) {
        // Guardar el documento en un archivo
        val documentFile = File(context.filesDir, "document_${document.id}.json")
        documentFile.writeText(gson.toJson(document))

        // Actualizar la caché
        documentCache[document.id] = document

        // Actualizar la lista de IDs de forma eficiente
        context.documentsDataStore.edit { preferences ->
            val currentIds = preferences[documentIds]?.split(",")?.toMutableSet() ?: mutableSetOf()
            if (!currentIds.contains(document.id)) {
                currentIds.add(document.id)
                preferences[documentIds] = currentIds.joinToString(",")
            }
        }
    }

    /**
     * Obtiene todos los documentos, usando caché cuando sea posible
     */
    fun getAllDocuments(): Flow<List<LogisticsDocument>> {
        return context.documentsDataStore.data.map { preferences ->
            val ids = preferences[documentIds]?.split(",") ?: emptyList()

            ids.mapNotNull { id ->
                // Primero intentamos obtener de la caché
                documentCache[id] ?: loadDocumentFromDisk(id)?.also { document ->
                    // Almacenar en caché para acceso futuro
                    documentCache[id] = document
                }
            }
        }
    }

    /**
     * Carga un documento desde el almacenamiento
     */
    private fun loadDocumentFromDisk(id: String): LogisticsDocument? {
        return try {
            val file = File(context.filesDir, "document_${id}.json")
            if (file.exists()) {
                val json = file.readText()
                when {
                    json.contains("\"invoiceNumber\"") -> gson.fromJson(json, Invoice::class.java)
                    json.contains("\"deliveryNoteNumber\"") -> gson.fromJson(json, DeliveryNote::class.java)
                    json.contains("\"labelId\"") -> gson.fromJson(json, WarehouseLabel::class.java)
                    else -> null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Obtiene un documento por su ID
     */
    suspend fun getDocumentById(id: String): LogisticsDocument? = withContext(Dispatchers.IO) {
        // Primero buscar en la caché
        documentCache[id] ?: loadDocumentFromDisk(id)?.also { document ->
            // Almacenar en caché
            documentCache[id] = document
        }
    }

    /**
     * Elimina un documento
     */
    suspend fun deleteDocument(id: String) = withContext(Dispatchers.IO) {
        // Eliminar de la caché
        documentCache.remove(id)

        // Eliminar el archivo
        val file = File(context.filesDir, "document_${id}.json")
        if (file.exists()) {
            file.delete()
        }

        // Actualizar la lista de IDs
        context.documentsDataStore.edit { preferences ->
            val currentIds = preferences[documentIds]?.split(",")?.toMutableList() ?: mutableListOf()
            if (currentIds.contains(id)) {
                currentIds.remove(id)
                preferences[documentIds] = currentIds.joinToString(",")
            }
        }
    }

    /**
     * Busca documentos por texto, usando getAllDocuments para aprovechar la caché
     */
    fun searchDocuments(query: String): Flow<List<LogisticsDocument>> {
        return getAllDocuments().map { documents ->
            val searchText = query.lowercase()
            documents.filter { document ->
                when (document) {
                    is Invoice -> {
                        document.invoiceNumber.lowercase().contains(searchText) ||
                                document.supplier.name.lowercase().contains(searchText) ||
                                document.client.name.lowercase().contains(searchText) ||
                                document.items.any { it.description.lowercase().contains(searchText) }
                    }
                    is DeliveryNote -> {
                        document.deliveryNoteNumber.lowercase().contains(searchText) ||
                                document.origin.name.lowercase().contains(searchText) ||
                                document.destination.name.lowercase().contains(searchText) ||
                                document.items.any { it.description.lowercase().contains(searchText) }
                    }
                    is WarehouseLabel -> {
                        document.labelId.lowercase().contains(searchText) ||
                                document.productName.lowercase().contains(searchText) ||
                                document.productCode.lowercase().contains(searchText)
                    }
                    else -> false
                }
            }
        }
    }

    /**
     * Obtiene documentos por tipo, aprovechando la caché con getAllDocuments
     */
    fun getDocumentsByType(type: DocumentType): Flow<List<LogisticsDocument>> {
        return getAllDocuments().map { documents ->
            documents.filter { document ->
                when (type) {
                    DocumentType.INVOICE -> document is Invoice
                    DocumentType.DELIVERY_NOTE -> document is DeliveryNote
                    DocumentType.WAREHOUSE_LABEL -> document is WarehouseLabel
                    DocumentType.UNKNOWN -> false
                }
            }
        }
    }

    /**
     * Guarda imagen temporal de un documento en proceso
     */
    fun saveTempImage(uri: Uri): Uri {
        val tempFile = File(context.cacheDir, "temp_document_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return Uri.fromFile(tempFile)
    }

    /**
     * Limpia la caché de documentos
     */
    fun clearCache() {
        documentCache.clear()
    }
}