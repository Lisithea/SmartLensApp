package com.example.smartlens.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.smartlens.model.LogisticsDocument
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentShareService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    /**
     * Convierte un documento a formato JSON
     */
    fun documentToJson(document: LogisticsDocument): String {
        return gson.toJson(document)
    }

    /**
     * Guarda el documento en un archivo temporal para compartir
     */
    fun saveDocumentToFile(document: LogisticsDocument): Uri {
        val jsonString = gson.toJson(document)

        val fileName = when (document) {
            is com.example.smartlens.model.Invoice -> "factura_${document.invoiceNumber}.json"
            is com.example.smartlens.model.DeliveryNote -> "albaran_${document.deliveryNoteNumber}.json"
            is com.example.smartlens.model.WarehouseLabel -> "etiqueta_${document.labelId}.json"
            else -> "documento_${document.id}.json"
        }

        val file = File(context.cacheDir, fileName)
        file.writeText(jsonString)

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Comparte el documento mediante intent
     */
    fun shareDocument(document: LogisticsDocument) {
        val fileUri = saveDocumentToFile(document)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Documento ${document.getTypeDisplay()} - ${document.getIdentifier()}")
            putExtra(Intent.EXTRA_TEXT, "Documento procesado con SmartLens")
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(intent, "Compartir documento")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    }

    /**
     * Genera un código QR para acceso rápido al documento
     */
    fun generateQrCode(document: LogisticsDocument, baseUrl: String = "https://smartlens.example.com/view/"): Bitmap {
        val documentId = document.id
        val accessUrl = "$baseUrl$documentId"

        val qrCodeWriter = QRCodeWriter()
        val bitMatrix = qrCodeWriter.encode(accessUrl, BarcodeFormat.QR_CODE, 512, 512)

        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        return bitmap
    }

    /**
     * Comparte el documento como Excel
     */
    fun shareAsExcel(excelUri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_SUBJECT, "Documento exportado desde SmartLens")
            putExtra(Intent.EXTRA_TEXT, "Documento exportado desde SmartLens")
            putExtra(Intent.EXTRA_STREAM, excelUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(intent, "Compartir Excel")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    }

    /**
     * Comparte enlace para abrir en PC
     */
    fun shareWebLink(document: LogisticsDocument) {
        val baseUrl = "https://smartlens.example.com/view/"
        val link = "$baseUrl${document.id}"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Enlace para documento en SmartLens")
            putExtra(Intent.EXTRA_TEXT, "Abra este enlace en su PC para ver el documento: $link")
        }

        val chooserIntent = Intent.createChooser(intent, "Compartir enlace")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    }
}