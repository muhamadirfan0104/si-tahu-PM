package muhamad.irfan.si_tahu.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PembantuCetak {
    private const val STRUK_FOLDER = "SI Tahu/Struk"

    fun printPlainText(context: Context, jobName: String, text: String) {
        printNota(context, jobName, text)
    }

    fun printNota(context: Context, jobName: String, text: String) {
        val html = buildReceiptHtml(text)
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val mediaSize = PrintAttributes.MediaSize("SI_TAHU_NOTA_80MM", "Nota 80mm", 3150, 11000)
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(mediaSize)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                printManager.print(jobName, view.createPrintDocumentAdapter(jobName), attributes)
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    fun downloadStrukPdf(context: Context, title: String, text: String): String {
        val fileName = safeFileName("struk_${title}_${timestamp()}.pdf")
        val bytes = buildReceiptPdf(title, text)
        saveBytesToDownloads(context, STRUK_FOLDER, fileName, "application/pdf", bytes)
        Toast.makeText(context, "Struk tersimpan di Download/$STRUK_FOLDER: $fileName", Toast.LENGTH_LONG).show()
        return fileName
    }

    fun shareStrukPdf(context: Context, title: String, text: String) {
        val fileName = safeFileName("struk_${title}_${timestamp()}.pdf")
        val file = File(context.cacheDir, fileName)
        file.writeBytes(buildReceiptPdf(title, text))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan $title"))
    }

    private fun buildReceiptHtml(text: String): String {
        val safeText = escapeHtml(text)
            .replace("\n", "<br>")
        return buildString {
            append("<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>")
            append("<style>")
            append("@page{size:80mm auto;margin:2mm;} body{margin:0;background:#fff;font-family:monospace;color:#111;}")
            append(".nota{width:72mm;margin:0 auto;padding:2mm;font-size:11px;line-height:1.35;white-space:normal;}")
            append(".title{text-align:center;font-weight:bold;font-size:12px;margin-bottom:4px;} .isi{white-space:pre-wrap;word-break:break-word;}")
            append("</style></head><body><div class=\"nota\"><div class=\"title\">NOTA SI TAHU</div><div class=\"isi\">")
            append(safeText)
            append("</div></div></body></html>")
        }
    }

    private fun buildReceiptPdf(title: String, text: String): ByteArray {
        val pdf = PdfDocument()
        val pageWidth = 302
        val pageHeight = 760
        val margin = 16f
        val bottom = pageHeight - 20f
        val contentWidth = pageWidth - margin * 2
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = margin

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 12f
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textSize = 10f
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textSize = 8f
        }

        fun startPage() {
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page!!.canvas
            y = margin + 2f
            val header = "NOTA SI TAHU"
            canvas!!.drawText(header, (pageWidth - titlePaint.measureText(header)) / 2f, y + 10f, titlePaint)
            y += 28f
        }

        fun finishPage() {
            val p = page ?: return
            val c = canvas ?: return
            val footer = "Hal. $pageNumber"
            c.drawText(footer, pageWidth - margin - smallPaint.measureText(footer), pageHeight - 8f, smallPaint)
            pdf.finishPage(p)
        }

        fun wrap(value: String): List<String> {
            if (value.isBlank()) return listOf("")
            val out = mutableListOf<String>()
            value.split(' ').forEach { raw ->
                var word = raw
                if (word.isBlank()) return@forEach
                if (out.isEmpty()) out += ""
                while (bodyPaint.measureText(word) > contentWidth) {
                    val count = bodyPaint.breakText(word, true, contentWidth, null).coerceAtLeast(1)
                    if (out.last().isNotBlank()) out += ""
                    out[out.lastIndex] = word.take(count)
                    out += ""
                    word = word.drop(count)
                }
                val current = out.last()
                val candidate = if (current.isBlank()) word else "$current $word"
                if (bodyPaint.measureText(candidate) <= contentWidth) out[out.lastIndex] = candidate else out += word
            }
            return out.ifEmpty { listOf("") }
        }

        startPage()
        text.lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            val wrapped = if (line.isBlank()) listOf("") else wrap(line)
            wrapped.forEach { wrappedLine ->
                if (y + 14f > bottom) {
                    finishPage()
                    startPage()
                }
                canvas!!.drawText(wrappedLine, margin, y, bodyPaint)
                y += 13f
            }
        }
        finishPage()
        val bytes = ByteArrayOutputStream().use { out ->
            pdf.writeTo(out)
            pdf.close()
            out.toByteArray()
        }
        return bytes
    }

    private fun saveBytesToDownloads(context: Context, folderName: String, fileName: String, mimeType: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$folderName")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Gagal membuat file di folder Download")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Gagal menulis file")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folderName)
            if (!directory.exists()) directory.mkdirs()
            val file = File(directory, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
        }
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun safeFileName(value: String): String {
        return value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "struk_sitahu_${timestamp()}.pdf" }
    }
}
