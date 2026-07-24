package com.deafregistry.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ExportUtils {

    /** Writes rows of a report to a CSV file in the device's Downloads folder (opens fine in Excel). */
    fun exportCsv(context: Context, fileName: String, header: List<String>, rows: List<List<String>>, title: String? = null): String {
        val csv = buildString {
            if (!title.isNullOrBlank()) {
                appendLine(escape(title))
                appendLine()
            }
            appendLine(header.joinToString(",") { escape(it) })
            rows.forEach { row -> appendLine(row.joinToString(",") { escape(it) }) }
        }
        return writeToDownloads(context, fileName, "text/csv") { it.write(csv.toByteArray()) }
    }

    /** Renders a simple tabular report as a PDF and saves it to Downloads. */
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

        val path = writeToDownloads(context, fileName, "application/pdf") { out ->
            document.writeTo(out)
        }
        document.close()
        return path
    }

    private fun writeToDownloads(context: Context, fileName: String, mimeType: String, writer: (OutputStream) -> Unit): String {
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
            return "Downloads/$fileName"
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { writer(it) }
            return file.absolutePath
        }
    }

    private fun escape(value: String): String {
        val needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n")
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuoting) "\"$escaped\"" else escaped
    }
}
