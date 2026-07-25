package com.deafregistry.app.util

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

object ExportUtils {

    /**
     * Writes a photo to a destination the user picked themselves via the system's Storage Access
     * Framework "Save As" picker (`ActivityResultContracts.CreateDocument`) - callers launch that
     * picker for a `Uri`, then hand it here rather than this util choosing a folder on its own.
     * Works for both a real network URL (fetched over HTTP) and a local, not-yet-synced file path
     * (copied directly) - the same photo picker/camera flow used elsewhere in the app hands either
     * kind of string to `AsyncImage`, so this needs to handle both the same way.
     */
    suspend fun writeImageToUri(context: Context, destination: Uri, source: String): Unit = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(destination)
            ?: throw IllegalStateException("Could not open the chosen location for writing")
        output.use { out ->
            if (source.startsWith("http://") || source.startsWith("https://")) {
                val connection = URL(source).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                try {
                    connection.connect()
                    connection.inputStream.use { input -> input.copyTo(out) }
                } finally {
                    connection.disconnect()
                }
            } else {
                val sourceFile = File(source)
                if (!sourceFile.exists()) throw IllegalStateException("Photo file not found")
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            }
        }
    }

    /** Writes rows of a report to a CSV file in the device's Downloads folder and opens it. */
    fun exportCsv(context: Context, fileName: String, header: List<String>, rows: List<List<String>>, title: String? = null): String {
        val csv = buildString {
            if (!title.isNullOrBlank()) {
                appendLine(escape(title))
                appendLine()
            }
            appendLine(header.joinToString(",") { escape(it) })
            rows.forEach { row -> appendLine(row.joinToString(",") { escape(it) }) }
        }
        val result = writeToDownloads(context, fileName, "text/csv") { it.write(csv.toByteArray()) }
        openFile(context, result.uri, "text/csv")
        return result.displayPath
    }

    /** Renders a simple tabular report as a PDF, saves it to Downloads, and opens it. */
    fun exportPdf(context: Context, fileName: String, title: String, header: List<String>, rows: List<List<String>>): String {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
        val headerPaint = Paint().apply { textSize = 12f; isFakeBoldText = true }
        val cellPaint = Paint().apply { textSize = 11f }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas: Canvas = page.canvas
        var y = 40f
        canvas.drawText(title, 24f, y, titlePaint)
        y += 30f

        val columnWidth = (pageWidth - 48) / header.size.coerceAtLeast(1)
        header.forEachIndexed { i, h -> canvas.drawText(h, 24f + i * columnWidth, y, headerPaint) }
        y += 20f

        for (row in rows) {
            if (y > pageHeight - 40) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 40f
            }
            row.forEachIndexed { i, cell -> canvas.drawText(cell, 24f + i * columnWidth, y, cellPaint) }
            y += 18f
        }
        document.finishPage(page)

        val result = writeToDownloads(context, fileName, "application/pdf") { out ->
            document.writeTo(out)
        }
        document.close()
        openFile(context, result.uri, "application/pdf")
        return result.displayPath
    }

    private data class WriteResult(val displayPath: String, val uri: Uri)

    private fun writeToDownloads(context: Context, fileName: String, mimeType: String, writer: (OutputStream) -> Unit): WriteResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create file in Downloads")
            resolver.openOutputStream(uri)?.use { writer(it) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return WriteResult("Downloads/$fileName", uri)
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { writer(it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            return WriteResult(file.absolutePath, uri)
        }
    }

    /** Launches whatever app the device has registered for this file type (Excel for CSV, a PDF viewer, etc.). */
    private fun openFile(context: Context, uri: Uri, mimeType: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No app installed that can open this file type - it's still saved in Downloads either way.
        }
    }

    private fun escape(value: String): String {
        val needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n")
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuoting) "\"$escaped\"" else escaped
    }
}
