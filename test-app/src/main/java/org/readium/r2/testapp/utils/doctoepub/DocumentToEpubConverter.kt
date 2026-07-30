/*
package com.example.transmute.converter

import android.content.Context
import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.epub.EpubWriter
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Color


object DocumentToEpubConverter {

    suspend fun convertPdf(
        context: Context,
        inputUri: Uri,
        outputStream: java.io.OutputStream,
        titleFallback: String,
        onProgress: (progress: Float, status: String) -> Unit
    ): Result<Unit> {
        return PdfToEpubConverter.convert(context, inputUri, outputStream, titleFallback, onProgress)
    }

    suspend fun convertTxt(
        context: Context,
        inputUri: Uri,
        outputStream: java.io.OutputStream,
        titleFallback: String,
        onProgress: (progress: Float, status: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(0.0f, "Reading plain text file...")
            val contentResolver = context.contentResolver
            val inputStream: InputStream = contentResolver.openInputStream(inputUri)
                ?: throw IllegalArgumentException("Could not open input stream for URI: $inputUri")

            val paragraphs = mutableListOf<String>()
            inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val currentParagraph = StringBuilder()
                reader.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) {
                        if (currentParagraph.isNotEmpty()) {
                            paragraphs.add(currentParagraph.toString())
                            currentParagraph.clear()
                        }
                    } else {
                        if (currentParagraph.isNotEmpty()) {
                            currentParagraph.append(" ")
                        }
                        currentParagraph.append(trimmed)
                    }
                }
                if (currentParagraph.isNotEmpty()) {
                    paragraphs.add(currentParagraph.toString())
                }
            }

            onProgress(0.3f, "Structuring text into chapters...")
            createEpubFromParagraphs(outputStream, titleFallback, paragraphs, onProgress)
            onProgress(1.0f, "Conversion complete!")
        }
    }

    suspend fun convertDocx(
        context: Context,
        inputUri: Uri,
        outputStream: java.io.OutputStream,
        titleFallback: String,
        onProgress: (progress: Float, status: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(0.0f, "Reading DOCX document...")
            val contentResolver = context.contentResolver
            val inputStream: InputStream = contentResolver.openInputStream(inputUri)
                ?: throw IllegalArgumentException("Could not open input stream for URI: $inputUri")

            val paragraphs = mutableListOf<String>()
            val zip = ZipInputStream(inputStream)
            try {
                var entry = zip.nextEntry
                var found = false
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        found = true
                        onProgress(0.2f, "Parsing Word document XML content...")
                        paragraphs.addAll(parseDocumentXml(zip))
                        break
                    }
                    entry = zip.nextEntry
                }
                if (!found) {
                    throw IllegalStateException("Invalid Word document: word/document.xml entry not found.")
                }
            } finally {
                zip.close()
            }

            onProgress(0.5f, "Structuring text into chapters...")
            createEpubFromParagraphs(outputStream, titleFallback, paragraphs, onProgress)
            onProgress(1.0f, "Conversion complete!")
        }
    }

    private fun parseDocumentXml(inputStream: InputStream): List<String> {
        val paragraphs = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        val currentParagraph = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.substringAfter(':')
                    if (name == "p") {
                        currentParagraph.clear()
                    } else if (name == "t") {
                        currentParagraph.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name.substringAfter(':')
                    if (name == "p") {
                        val text = currentParagraph.toString().trim()
                        if (text.isNotEmpty()) {
                            paragraphs.add(text)
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return paragraphs
    }

    private fun createEpubFromParagraphs(
        outputStream: java.io.OutputStream,
        title: String,
        paragraphs: List<String>,
        onProgress: (progress: Float, status: String) -> Unit
    ) {
        val book = Book()
        book.metadata.addTitle(title)
        book.metadata.addAuthor(nl.siegmann.epublib.domain.Author("Unknown Author"))

        // Split paragraphs into sections of roughly 2500 words each
        val sections = mutableListOf<List<String>>()
        var currentSection = mutableListOf<String>()
        var wordCount = 0

        for (para in paragraphs) {
            currentSection.add(para)
            wordCount += para.split(Regex("\\s+")).size
            if (wordCount >= 2500) {
                sections.add(currentSection)
                currentSection = mutableListOf()
                wordCount = 0
            }
        }
        if (currentSection.isNotEmpty()) {
            sections.add(currentSection)
        }

        val totalSections = sections.size.coerceAtLeast(1)

        sections.forEachIndexed { index, sectionParas ->
            val pageNumber = index + 1
            onProgress(
                0.5f + (pageNumber.toFloat() / totalSections) * 0.4f,
                "Writing Chapter $pageNumber of $totalSections..."
            )

            val htmlContent = buildString {
                append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n")
                append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n")
                append("<head>\n")
                append("    <title>Chapter $pageNumber</title>\n")
                append("    <style type=\"text/css\">\n")
                append("        body { font-family: sans-serif; padding: 1em; line-height: 1.5; }\n")
                append("        p { margin-bottom: 1em; text-indent: 1.5em; }\n")
                append("    </style>\n")
                append("</head>\n")
                append("<body>\n")
                append("    <h3>Chapter $pageNumber</h3>\n")
                sectionParas.forEach { para ->
                    append("    <p>${escapeHtml(para)}</p>\n")
                }
                append("</body>\n")
                append("</html>")
            }

            val resource = Resource(htmlContent.toByteArray(Charsets.UTF_8), "chapter_$pageNumber.xhtml")
            book.addSection("Chapter $pageNumber", resource)
        }

        outputStream.use { os ->
            EpubWriter().write(book, os)
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    suspend fun convertDocxToPdf(
        context: Context,
        inputUri: Uri,
        outputStream: java.io.OutputStream,
        onProgress: (progress: Float, status: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(0.0f, "Reading DOCX document...")
            val contentResolver = context.contentResolver
            val inputStream: InputStream = contentResolver.openInputStream(inputUri)
                ?: throw IllegalArgumentException("Could not open input stream for URI: $inputUri")

            val paragraphs = mutableListOf<String>()
            val zip = ZipInputStream(inputStream)
            try {
                var entry = zip.nextEntry
                var found = false
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        found = true
                        onProgress(0.2f, "Parsing Word document XML content...")
                        paragraphs.addAll(parseDocumentXml(zip))
                        break
                    }
                    entry = zip.nextEntry
                }
                if (!found) {
                    throw IllegalStateException("Invalid Word document: word/document.xml entry not found.")
                }
            } finally {
                zip.close()
            }

            onProgress(0.5f, "Generating PDF document...")
            val pdfDoc = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
            var page = pdfDoc.startPage(pageInfo)
            var canvas = page.canvas
            
            val paint = Paint().apply {
                textSize = 12f
                color = Color.BLACK
                isAntiAlias = true
            }

            val margin = 50f
            val maxWidth = 595f - 2 * margin
            var y = margin + 20f
            val lineHeight = paint.fontSpacing

            paragraphs.forEach { para ->
                val lines = wrapText(para, paint, maxWidth)
                lines.forEach { line ->
                    if (y + lineHeight > 842f - margin) {
                        pdfDoc.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                        page = pdfDoc.startPage(pageInfo)
                        canvas = page.canvas
                        y = margin + 20f
                    }
                    canvas.drawText(line, margin, y, paint)
                    y += lineHeight
                }
                y += lineHeight * 0.5f // Paragraph spacing
            }

            pdfDoc.finishPage(page)
            
            onProgress(0.9f, "Writing PDF file...")
            outputStream.use { os ->
                pdfDoc.writeTo(os)
            }
            pdfDoc.close()
            onProgress(1.0f, "Conversion complete!")
        }
    }

    suspend fun convertPdfToDocx(
        context: Context,
        inputUri: Uri,
        outputStream: java.io.OutputStream,
        onProgress: (progress: Float, status: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(0.0f, "Opening PDF document...")
            val contentResolver = context.contentResolver
            val inputStream: InputStream = contentResolver.openInputStream(inputUri)
                ?: throw IllegalArgumentException("Could not open input stream for URI: $inputUri")

            var document: PDDocument? = null
            try {
                document = PDDocument.load(inputStream)
                onProgress(0.3f, "Extracting text from PDF...")
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)
                
                onProgress(0.6f, "Structuring paragraphs...")
                val paragraphs = text.split(Regex("\n\n+")).map { it.trim() }.filter { it.isNotEmpty() }
                
                onProgress(0.8f, "Writing DOCX package...")
                writeDocx(outputStream, paragraphs)
                onProgress(1.0f, "Conversion complete!")
            } finally {
                document?.close()
                try {
                    inputStream.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        val currentLine = StringBuilder()
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) "" else " ").append(word)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine.clear()
                }
                currentLine.append(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }

    private fun writeDocx(outputStream: java.io.OutputStream, paragraphs: List<String>) {
        val zip = java.util.zip.ZipOutputStream(outputStream)
        
        // Write [Content_Types].xml
        zip.putNextEntry(java.util.zip.ZipEntry("[Content_Types].xml"))
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
        zip.write(contentTypes.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        
        // Write _rels/.rels
        zip.putNextEntry(java.util.zip.ZipEntry("_rels/.rels"))
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        zip.write(rels.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        
        // Write word/document.xml
        zip.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
        val docXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
            paragraphs.forEach { para ->
                append("<w:p><w:r><w:t>${escapeXml(para)}</w:t></w:r></w:p>")
            }
            append("""</w:body></w:document>""")
        }
        zip.write(docXml.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        
        zip.close()
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
*/