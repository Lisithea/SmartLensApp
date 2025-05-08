package com.example.smartlens.model

import android.net.Uri
import java.util.UUID
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.InsertDriveFile

/**
 * Clase base para todos los documentos logísticos
 */
sealed class LogisticsDocument {
    abstract val id: String
    abstract val timestamp: Long
    abstract val imageUri: Uri
    abstract val rawTextContent: String
    abstract val tags: List<String>
    abstract val isStarred: Boolean

    fun getTypeDisplay(): String {
        return when (this) {
            is Invoice -> "Factura"
            is DeliveryNote -> "Albarán"
            is WarehouseLabel -> "Etiqueta"
            else -> "Documento"
        }
    }

    fun getIdentifier(): String {
        return when (this) {
            is Invoice -> this.invoiceNumber
            is DeliveryNote -> this.deliveryNoteNumber
            is WarehouseLabel -> this.labelId
            else -> this.id
        }
    }
}

/**
 * Modelo para facturas
 */
data class Invoice(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val imageUri: Uri,
    override val rawTextContent: String,
    override val tags: List<String> = emptyList(),
    override val isStarred: Boolean = false,
    val invoiceNumber: String,
    val date: String,
    val dueDate: String? = null,
    val supplier: Company,
    val client: Company,
    val items: List<InvoiceItem>,
    val subtotal: Double,
    val taxAmount: Double,
    val totalAmount: Double,
    val paymentTerms: String? = null,
    val notes: String? = null,
    val barcode: String? = null,
    val customFields: Map<String, String> = emptyMap()
) : LogisticsDocument()

/**
 * Modelo para albaranes/notas de entrega
 */
data class DeliveryNote(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val imageUri: Uri,
    override val rawTextContent: String,
    override val tags: List<String> = emptyList(),
    override val isStarred: Boolean = false,
    val deliveryNoteNumber: String,
    val date: String,
    val origin: Location,
    val destination: Location,
    val carrier: String? = null,
    val items: List<DeliveryItem>,
    val totalPackages: Int? = null,
    val totalWeight: Double? = null,
    val observations: String? = null,
    val customFields: Map<String, String> = emptyMap()
) : LogisticsDocument()

/**
 * Modelo para etiquetas de almacén
 */
data class WarehouseLabel(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val imageUri: Uri,
    override val rawTextContent: String,
    override val tags: List<String> = emptyList(),
    override val isStarred: Boolean = false,
    val labelId: String,
    val productCode: String,
    val productName: String,
    val quantity: Double,
    val batchNumber: String? = null,
    val expirationDate: String? = null,
    val location: String? = null,
    val barcode: String? = null,
    val customFields: Map<String, String> = emptyMap()
) : LogisticsDocument()

/**
 * Clases complementarias
 */
data class Company(
    val name: String,
    val taxId: String? = null,
    val address: String? = null,
    val contactInfo: String? = null
)

data class Location(
    val name: String,
    val address: String,
    val contactPerson: String? = null,
    val contactPhone: String? = null
)

data class InvoiceItem(
    val code: String? = null,
    val description: String,
    val quantity: Double,
    val unitPrice: Double,
    val totalPrice: Double,
    val taxRate: Double? = null
)

data class DeliveryItem(
    val code: String? = null,
    val description: String,
    val quantity: Double,
    val packageType: String? = null,
    val weight: Double? = null
)

/**
 * Etiquetas predefinidas para documentos
 */
object DocumentTags {
    const val IMPORTANT = "Importante"
    const val URGENT = "Urgente"
    const val PENDING = "Pendiente"
    const val COMPLETED = "Completado"
    const val VERIFIED = "Verificado"
    const val PERSONAL = "Personal"
    const val WORK = "Trabajo"
    const val HEALTHCARE = "Salud"
    const val FINANCIAL = "Financiero"
    const val EDUCATION = "Educación"

    // Lista de etiquetas disponibles
    val ALL_TAGS = listOf(
        IMPORTANT, URGENT, PENDING, COMPLETED, VERIFIED,
        PERSONAL, WORK, HEALTHCARE, FINANCIAL, EDUCATION
    )

    // Colores para etiquetas
    val TAG_COLORS = mapOf(
        IMPORTANT to android.graphics.Color.parseColor("#FF5252"),
        URGENT to android.graphics.Color.parseColor("#D50000"),
        PENDING to android.graphics.Color.parseColor("#FFC107"),
        COMPLETED to android.graphics.Color.parseColor("#4CAF50"),
        VERIFIED to android.graphics.Color.parseColor("#2196F3"),
        PERSONAL to android.graphics.Color.parseColor("#9C27B0"),
        WORK to android.graphics.Color.parseColor("#3F51B5"),
        HEALTHCARE to android.graphics.Color.parseColor("#00BCD4"),
        FINANCIAL to android.graphics.Color.parseColor("#009688"),
        EDUCATION to android.graphics.Color.parseColor("#FF9800")
    )
}

/**
 * Enumeración para tipos de documentos
 */
enum class DocumentType {
    INVOICE,
    DELIVERY_NOTE,
    WAREHOUSE_LABEL,
    UNKNOWN;

    fun getDisplayName(): String {
        return when (this) {
            INVOICE -> "Factura"
            DELIVERY_NOTE -> "Albarán"
            WAREHOUSE_LABEL -> "Etiqueta"
            UNKNOWN -> "Documento desconocido"
        }
    }

    companion object {
        fun getIconForType(type: DocumentType): androidx.compose.ui.graphics.vector.ImageVector {
            return when (type) {
                INVOICE -> Icons.Filled.Description
                DELIVERY_NOTE -> Icons.Filled.LocalShipping
                WAREHOUSE_LABEL -> Icons.Filled.QrCode
                UNKNOWN -> Icons.Filled.InsertDriveFile
            }
        }
    }
}

/**
 * Estado del procesamiento del documento
 */
sealed class DocumentProcessingState {
    object Idle : DocumentProcessingState()
    object Capturing : DocumentProcessingState()
    object ExtractingText : DocumentProcessingState()
    data class ProcessingDocument(val documentType: DocumentType) : DocumentProcessingState()
    data class DocumentReady(val document: LogisticsDocument) : DocumentProcessingState()
    data class Error(val message: String) : DocumentProcessingState()
}

/**
 * Extensiones para la conversión de modelos antiguos a mejorados
 */
fun Invoice.toImproved(tags: List<String> = emptyList(), isStarred: Boolean = false): Invoice {
    return this.copy(
        tags = tags,
        isStarred = isStarred
    )
}

fun DeliveryNote.toImproved(tags: List<String> = emptyList(), isStarred: Boolean = false): DeliveryNote {
    return this.copy(
        tags = tags,
        isStarred = isStarred
    )
}

fun WarehouseLabel.toImproved(tags: List<String> = emptyList(), isStarred: Boolean = false): WarehouseLabel {
    return this.copy(
        tags = tags,
        isStarred = isStarred
    )
}