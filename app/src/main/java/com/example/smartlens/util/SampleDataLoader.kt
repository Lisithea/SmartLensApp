package com.example.smartlens.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.net.toUri
import com.example.smartlens.model.*
import com.example.smartlens.repository.DocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cargador de datos de muestra para la aplicación
 */
@Singleton
class SampleDataLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DocumentRepository
) {
    private val preferences: SharedPreferences = context.getSharedPreferences("sample_data_prefs", Context.MODE_PRIVATE)
    private val SAMPLE_DATA_KEY = "sample_data_loaded"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Verifica si ya se han cargado los datos de muestra
     */
    private fun isDataLoaded(): Boolean {
        return preferences.getBoolean(SAMPLE_DATA_KEY, false)
    }

    /**
     * Marca los datos como cargados
     */
    private fun markDataAsLoaded() {
        preferences.edit().putBoolean(SAMPLE_DATA_KEY, true).apply()
    }

    /**
     * Carga datos de muestra si es necesario
     * Versión no suspendida para mayor flexibilidad
     */
    fun loadSampleDataIfNeeded() {
        // Si ya están cargados, no hacer nada
        if (isDataLoaded()) {
            return
        }

        // Lanzar en una corrutina pero sin hacer suspender al llamador
        coroutineScope.launch {
            loadSampleData()
            markDataAsLoaded()
        }
    }

    /**
     * Carga datos de muestra en la base de datos
     */
    private suspend fun loadSampleData() = withContext(Dispatchers.IO) {
        // Crear algunas imágenes de muestra
        val invoiceImageUri = createSampleImage("sample_invoice.jpg")
        val deliveryNoteImageUri = createSampleImage("sample_delivery_note.jpg")
        val labelImageUri = createSampleImage("sample_warehouse_label.jpg")

        // Crear documentos de muestra

        // Factura de muestra
        val invoice = Invoice(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis() - 86400000, // Un día atrás
            imageUri = invoiceImageUri,
            rawTextContent = "FACTURA\nNúmero: F-2023-001\nFecha: 01/01/2023\nProveedor: Suministros ABC\nNIF: B12345678\nCliente: Logística Express\nNIF: A87654321\nProductos:\n10 x Cajas grandes - 50€\n5 x Cintas adhesivas - 25€\nSubtotal: 75€\nIVA (21%): 15.75€\nTotal: 90.75€",
            invoiceNumber = "F-2023-001",
            date = "01/01/2023",
            dueDate = "31/01/2023",
            supplier = Company(
                name = "Suministros ABC",
                taxId = "B12345678",
                address = "Calle Industria 123, Madrid"
            ),
            client = Company(
                name = "Logística Express",
                taxId = "A87654321",
                address = "Avenida del Transporte 45, Barcelona"
            ),
            items = listOf(
                InvoiceItem(
                    code = "CB-001",
                    description = "Cajas grandes",
                    quantity = 10.0,
                    unitPrice = 5.0,
                    totalPrice = 50.0,
                    taxRate = 21.0
                ),
                InvoiceItem(
                    code = "CT-002",
                    description = "Cintas adhesivas",
                    quantity = 5.0,
                    unitPrice = 5.0,
                    totalPrice = 25.0,
                    taxRate = 21.0
                )
            ),
            subtotal = 75.0,
            taxAmount = 15.75,
            totalAmount = 90.75,
            paymentTerms = "Pago a 30 días"
        )

        // Albarán de muestra
        val deliveryNote = DeliveryNote(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis() - 172800000, // Dos días atrás
            imageUri = deliveryNoteImageUri,
            rawTextContent = "ALBARÁN\nNúmero: A-2023-001\nFecha: 02/01/2023\nOrigen: Almacén Central\nDirección: Polígono Industrial Norte, Nave 7\nDestino: Tienda Centro\nDirección: Calle Mayor 10, Madrid\nTransportista: Transportes Rápidos\nProductos:\n20 x Paquetes pequeños\n10 x Cajas medianas\nTotal bultos: 30\nPeso total: 150kg",
            deliveryNoteNumber = "A-2023-001",
            date = "02/01/2023",
            origin = Location(
                name = "Almacén Central",
                address = "Polígono Industrial Norte, Nave 7",
                contactPerson = "Juan Martínez",
                contactPhone = "600123456"
            ),
            destination = Location(
                name = "Tienda Centro",
                address = "Calle Mayor 10, Madrid",
                contactPerson = "María López",
                contactPhone = "600654321"
            ),
            carrier = "Transportes Rápidos",
            items = listOf(
                DeliveryItem(
                    code = "PP-001",
                    description = "Paquetes pequeños",
                    quantity = 20.0,
                    packageType = "Caja",
                    weight = 5.0
                ),
                DeliveryItem(
                    code = "CM-002",
                    description = "Cajas medianas",
                    quantity = 10.0,
                    packageType = "Caja",
                    weight = 10.0
                )
            ),
            totalPackages = 30,
            totalWeight = 150.0,
            observations = "Entregar en horario comercial"
        )

        // Etiqueta de almacén de muestra
        val warehouseLabel = WarehouseLabel(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis() - 259200000, // Tres días atrás
            imageUri = labelImageUri,
            rawTextContent = "ETIQUETA\nProducto: Smartphone Model X\nCódigo: SM-X-001\nCantidad: 50\nLote: L2023-005\nCaducidad: 31/12/2025\nUbicación: Estantería E5, Pasillo 3",
            labelId = "ET-2023-001",
            productCode = "SM-X-001",
            productName = "Smartphone Model X",
            quantity = 50.0,
            batchNumber = "L2023-005",
            expirationDate = "31/12/2025",
            location = "Estantería E5, Pasillo 3",
            barcode = "7891234567890"
        )

        // Guardar documentos en la base de datos
        repository.saveDocument(invoice)
        repository.saveDocument(deliveryNote)
        repository.saveDocument(warehouseLabel)
    }

    /**
     * Crea una imagen de muestra y devuelve su URI
     */
    private fun createSampleImage(fileName: String): Uri {
        // Crear un archivo de imagen vacío (1x1 pixel)
        val file = File(context.filesDir, fileName)

        // Si el archivo ya existe, simplemente devolver su URI
        if (file.exists()) {
            return Uri.fromFile(file)
        }

        // Crear un archivo de imagen placeholder muy simple (1x1 pixel)
        val placeholderBytes = byteArrayOf(
            0x42.toByte(), 0x4D.toByte(), 0x3A.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x36.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x28.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x18.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x04.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xFF.toByte(), 0x00.toByte()
        )

        FileOutputStream(file).use { output ->
            output.write(placeholderBytes)
        }

        return Uri.fromFile(file)
    }
}