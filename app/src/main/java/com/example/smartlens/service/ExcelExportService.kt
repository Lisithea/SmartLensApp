package com.example.smartlens.service

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.smartlens.model.DeliveryNote
import com.example.smartlens.model.Invoice
import com.example.smartlens.model.LogisticsDocument
import com.example.smartlens.model.WarehouseLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExcelExportService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Exporta un documento a Excel y devuelve la URI del archivo
     */
    fun exportToExcel(document: LogisticsDocument): Uri {
        val workbook = XSSFWorkbook()

        when (document) {
            is Invoice -> exportInvoice(workbook, document)
            is DeliveryNote -> exportDeliveryNote(workbook, document)
            is WarehouseLabel -> exportWarehouseLabel(workbook, document)
        }

        // Guardar el archivo en el directorio de caché
        val fileName = when (document) {
            is Invoice -> "factura_${document.invoiceNumber}.xlsx"
            is DeliveryNote -> "albaran_${document.deliveryNoteNumber}.xlsx"
            is WarehouseLabel -> "etiqueta_${document.labelId}.xlsx"
            else -> "documento_${document.id}.xlsx"
        }

        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { outputStream ->
            workbook.write(outputStream)
        }

        // Devolver la URI del archivo para compartir
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Exporta una factura a Excel
     */
    private fun exportInvoice(workbook: XSSFWorkbook, invoice: Invoice) {
        val sheet = workbook.createSheet("Factura")

        // Estilos para encabezados
        val headerStyle = workbook.createCellStyle().apply {
            setFont(workbook.createFont().apply {
                bold = true
            })
            fillForegroundColor = IndexedColors.LIGHT_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }

        // Sección de información básica
        var rowNum = 0
        createHeaderRow(sheet, rowNum++, headerStyle, "INFORMACIÓN DE FACTURA")
        createRow(sheet, rowNum++, "Número de Factura:", invoice.invoiceNumber)
        createRow(sheet, rowNum++, "Fecha:", invoice.date)
        createRow(sheet, rowNum++, "Fecha Vencimiento:", invoice.dueDate ?: "")

        // Sección de proveedor
        rowNum++
        createHeaderRow(sheet, rowNum++, headerStyle, "PROVEEDOR")
        createRow(sheet, rowNum++, "Nombre:", invoice.supplier.name)
        createRow(sheet, rowNum++, "NIF:", invoice.supplier.taxId ?: "")
        createRow(sheet, rowNum++, "Dirección:", invoice.supplier.address ?: "")

        // Sección de cliente
        rowNum++
        createHeaderRow(sheet, rowNum++, headerStyle, "CLIENTE")
        createRow(sheet, rowNum++, "Nombre:", invoice.client.name)
        createRow(sheet, rowNum++, "NIF:", invoice.client.taxId ?: "")
        createRow(sheet, rowNum++, "Dirección:", invoice.client.address ?: "")

        // Tabla de artículos
        rowNum += 2
        createHeaderRow(sheet, rowNum++, headerStyle, "DETALLE DE ARTÍCULOS")
        val itemHeaderRow = sheet.createRow(rowNum++)
        val headers = listOf("Código", "Descripción", "Cantidad", "Precio Unitario", "IVA%", "Total")

        headers.forEachIndexed { index, header ->
            val cell = itemHeaderRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }

        // Filas de artículos
        invoice.items.forEach { item ->
            val itemRow = sheet.createRow(rowNum++)
            itemRow.createCell(0).setCellValue(item.code ?: "")
            itemRow.createCell(1).setCellValue(item.description)
            itemRow.createCell(2).setCellValue(item.quantity)
            itemRow.createCell(3).setCellValue(item.unitPrice)
            itemRow.createCell(4).setCellValue(item.taxRate ?: 0.0)
            itemRow.createCell(5).setCellValue(item.totalPrice)
        }

        // Totales
        rowNum += 2
        createRow(sheet, rowNum++, "Subtotal:", invoice.subtotal.toString())
        createRow(sheet, rowNum++, "IVA:", invoice.taxAmount.toString())
        createRow(sheet, rowNum++, "TOTAL:", invoice.totalAmount.toString())

        // Autoajustar columnas
        for (i in 0..5) {
            sheet.autoSizeColumn(i)
        }
    }

    /**
     * Exporta un albarán a Excel
     */
    private fun exportDeliveryNote(workbook: XSSFWorkbook, deliveryNote: DeliveryNote) {
        val sheet = workbook.createSheet("Albarán")

        // Estilos para encabezados
        val headerStyle = workbook.createCellStyle().apply {
            setFont(workbook.createFont().apply {
                bold = true
            })
            fillForegroundColor = IndexedColors.LIGHT_GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }

        // Sección de información básica
        var rowNum = 0
        createHeaderRow(sheet, rowNum++, headerStyle, "INFORMACIÓN DE ALBARÁN")
        createRow(sheet, rowNum++, "Número de Albarán:", deliveryNote.deliveryNoteNumber)
        createRow(sheet, rowNum++, "Fecha:", deliveryNote.date)

        // Sección de origen
        rowNum++
        createHeaderRow(sheet, rowNum++, headerStyle, "ORIGEN")
        createRow(sheet, rowNum++, "Nombre:", deliveryNote.origin.name)
        createRow(sheet, rowNum++, "Dirección:", deliveryNote.origin.address)
        createRow(sheet, rowNum++, "Contacto:", deliveryNote.origin.contactPerson ?: "")
        createRow(sheet, rowNum++, "Teléfono:", deliveryNote.origin.contactPhone ?: "")

        // Sección de destino
        rowNum++
        createHeaderRow(sheet, rowNum++, headerStyle, "DESTINO")
        createRow(sheet, rowNum++, "Nombre:", deliveryNote.destination.name)
        createRow(sheet, rowNum++, "Dirección:", deliveryNote.destination.address)
        createRow(sheet, rowNum++, "Contacto:", deliveryNote.destination.contactPerson ?: "")
        createRow(sheet, rowNum++, "Teléfono:", deliveryNote.destination.contactPhone ?: "")

        // Transportista
        rowNum++
        createRow(sheet, rowNum++, "Transportista:", deliveryNote.carrier ?: "")

        // Tabla de artículos
        rowNum += 2
        createHeaderRow(sheet, rowNum++, headerStyle, "DETALLE DE ARTÍCULOS")
        val itemHeaderRow = sheet.createRow(rowNum++)
        val headers = listOf("Código", "Descripción", "Cantidad", "Tipo Embalaje", "Peso")

        headers.forEachIndexed { index, header ->
            val cell = itemHeaderRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }

        // Filas de artículos
        deliveryNote.items.forEach { item ->
            val itemRow = sheet.createRow(rowNum++)
            itemRow.createCell(0).setCellValue(item.code ?: "")
            itemRow.createCell(1).setCellValue(item.description)
            itemRow.createCell(2).setCellValue(item.quantity)
            itemRow.createCell(3).setCellValue(item.packageType ?: "")
            itemRow.createCell(4).setCellValue(item.weight ?: 0.0)
        }

        // Totales
        rowNum += 2
        createRow(sheet, rowNum++, "Total Bultos:", deliveryNote.totalPackages?.toString() ?: "")
        createRow(sheet, rowNum++, "Peso Total:", deliveryNote.totalWeight?.toString() ?: "")

        // Observaciones
        rowNum++
        createHeaderRow(sheet, rowNum++, headerStyle, "OBSERVACIONES")
        createRow(sheet, rowNum++, "", deliveryNote.observations ?: "")

        // Autoajustar columnas
        for (i in 0..4) {
            sheet.autoSizeColumn(i)
        }
    }

    /**
     * Exporta una etiqueta de almacén a Excel
     */
    private fun exportWarehouseLabel(workbook: XSSFWorkbook, label: WarehouseLabel) {
        val sheet = workbook.createSheet("Etiqueta")

        // Estilos para encabezados
        val headerStyle = workbook.createCellStyle().apply {
            setFont(workbook.createFont().apply {
                bold = true
            })
            fillForegroundColor = IndexedColors.LIGHT_YELLOW.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }

        // Sección de información básica
        var rowNum = 0
        createHeaderRow(sheet, rowNum++, headerStyle, "INFORMACIÓN DE ETIQUETA")
        createRow(sheet, rowNum++, "ID Etiqueta:", label.labelId)
        createRow(sheet, rowNum++, "Código de Producto:", label.productCode)
        createRow(sheet, rowNum++, "Nombre de Producto:", label.productName)
        createRow(sheet, rowNum++, "Cantidad:", label.quantity.toString())
        createRow(sheet, rowNum++, "Número de Lote:", label.batchNumber ?: "")
        createRow(sheet, rowNum++, "Fecha de Caducidad:", label.expirationDate ?: "")
        createRow(sheet, rowNum++, "Ubicación:", label.location ?: "")
        createRow(sheet, rowNum++, "Código de Barras:", label.barcode ?: "")

        // Autoajustar columnas
        sheet.autoSizeColumn(0)
        sheet.autoSizeColumn(1)
    }

    /**
     * Crea una fila de encabezado
     */
    private fun createHeaderRow(sheet: org.apache.poi.ss.usermodel.Sheet, rowNum: Int, style: CellStyle, title: String) {
        val row = sheet.createRow(rowNum)
        val cell = row.createCell(0)
        cell.setCellValue(title)
        cell.cellStyle = style
    }

    /**
     * Crea una fila con etiqueta y valor
     */
    private fun createRow(sheet: org.apache.poi.ss.usermodel.Sheet, rowNum: Int, label: String, value: String) {
        val row = sheet.createRow(rowNum)
        row.createCell(0).setCellValue(label)
        row.createCell(1).setCellValue(value)
    }

    /**
     * Convierte un Excel a bytes
     */
    fun workbookToBytes(workbook: XSSFWorkbook): ByteArray {
        val bos = ByteArrayOutputStream()
        workbook.write(bos)
        return bos.toByteArray()
    }
}