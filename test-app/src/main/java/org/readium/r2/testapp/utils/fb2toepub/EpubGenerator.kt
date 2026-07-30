package org.readium.r2.testapp.utils.fb2toepub

import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubGenerator {

    fun generateEpub(book: Book, outputFile: File) {
        FileOutputStream(outputFile).use { fos ->
            ZipOutputStream(fos).use { zip ->
                addMimetypeFile(zip)
                addContainerXml(zip)
                addCss(zip)
                addImages(zip, book)
                addContentOpf(zip, book)
                addTocNcx(zip, book)
                addChapters(zip, book)
            }
        }
    }

    private fun addMimetypeFile(zip: ZipOutputStream) {
        val content = "application/epub+zip".toByteArray(Charsets.UTF_8)
        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = content.size.toLong()
            crc = calculateCrc32(content)
        }
        zip.putNextEntry(entry)
        zip.write(content)
        zip.closeEntry()
    }

    private fun addContainerXml(zip: ZipOutputStream) {
        val containerXml = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
<rootfiles>
<rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
</rootfiles>
</container>"""
        addZipEntry(zip, "META-INF/container.xml", containerXml)
    }

    private fun addCss(zip: ZipOutputStream) {
        val css = """
            body { font-family: serif; margin: 1em; line-height: 1.6; text-align: justify; }
            h1, h2, h3, h4, h5, h6 { font-family: sans-serif; text-align: center; margin-bottom: 1em; margin-top: 2em; page-break-after: avoid; }
            p { margin: 0.5em 0; text-indent: 1.5em; }
            p:first-of-type, .empty-line + p { text-indent: 0; }
            .empty-line { height: 1em; margin: 0; padding: 0; }
            .image { text-align: center; margin: 1em 0; text-indent: 0; }
            .image img { max-width: 100%; height: auto; }
            blockquote { margin: 1em 2em; font-style: italic; }
            .poem { margin: 1em 0; text-indent: 0; }
            .stanza { margin-bottom: 1em; }
            .verse { text-indent: 0; margin: 0.2em 0; text-align: left; }
            .text-author { text-align: right; font-style: italic; margin-top: 0.5em; text-indent: 0; }
            .date { text-align: center; font-style: italic; text-indent: 0; }
            table { border-collapse: collapse; margin: 1em 0; }
            th, td { border: 1px solid #ccc; padding: 0.5em; }
            a { color: #0066cc; text-decoration: underline; }
        """.trimIndent()
        addZipEntry(zip, "OEBPS/styles.css", css)
    }

    private fun addImages(zip: ZipOutputStream, book: Book) {
        book.images.forEach { (id, img) ->
            val entry = ZipEntry("OEBPS/images/$id")
            entry.method = ZipEntry.DEFLATED
            zip.putNextEntry(entry)
            zip.write(img.data)
            zip.closeEntry()
        }
    }

    private fun addContentOpf(zip: ZipOutputStream, book: Book) {
        val currentDate = getCurrentDateISO()
        val manifestBuilder = StringBuilder()
        manifestBuilder.append("    <item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n")
        manifestBuilder.append("    <item id=\"css\" href=\"styles.css\" media-type=\"text/css\"/>\n")

        if (book.coverImageId != null && book.images.containsKey(book.coverImageId)) {
            val img = book.images[book.coverImageId]!!
            manifestBuilder.append("    <item id=\"cover-image\" href=\"images/${book.coverImageId}\" media-type=\"${img.contentType}\" properties=\"cover-image\"/>\n")
        }

        book.images.forEach { (id, img) ->
            if (id != book.coverImageId) {
                val safeId = "img-${id.replace(Regex("[^a-zA-Z0-9]"), "_")}"
                manifestBuilder.append("    <item id=\"$safeId\" href=\"images/$id\" media-type=\"${img.contentType}\"/>\n")
            }
        }

        book.chapters.forEach { chapter ->
            manifestBuilder.append("    <item id=\"${chapter.id}\" href=\"${chapter.id}.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
        }

        val spineBuilder = StringBuilder()
        book.chapters.forEach { chapter ->
            spineBuilder.append("    <itemref idref=\"${chapter.id}\"/>\n")
        }

        val metadataExtras = if (book.coverImageId != null) "<meta name=\"cover\" content=\"cover-image\"/>" else ""

        val contentOpf = """<?xml version="1.0" encoding="UTF-8"?>
<package version="3.0" xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookid">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="bookid">${book.id}</dc:identifier>
<dc:title>${escapeXml(book.title)}</dc:title>
<dc:creator>${escapeXml(book.authorsString())}</dc:creator>
<dc:language>${book.language}</dc:language>
<dc:date>${if (book.date.isNotBlank()) book.date else currentDate}</dc:date>
<meta property="dcterms:modified">${currentDate}</meta>
$metadataExtras
</metadata>
<manifest>
$manifestBuilder</manifest>
<spine toc="ncx">
$spineBuilder</spine>
</package>"""
        addZipEntry(zip, "OEBPS/content.opf", contentOpf)
    }

    private fun addTocNcx(zip: ZipOutputStream, book: Book) {
        val navPoints = book.chapters.mapIndexed { index, chapter ->
            """
            <navPoint id="${chapter.id}" playOrder="${index + 1}">
            <navLabel><text>${escapeXml(chapter.title)}</text></navLabel>
            <content src="${chapter.id}.xhtml"/>
            </navPoint>"""
        }.joinToString("")

        val tocNcx = """<?xml version="1.0" encoding="UTF-8"?>
<ncx version="2005-1" xmlns="http://www.daisy.org/z3986/2005/ncx/">
<head>
<meta name="dtb:uid" content="${book.id}"/>
<meta name="dtb:depth" content="1"/>
<meta name="dtb:totalPageCount" content="0"/>
<meta name="dtb:maxPageNumber" content="0"/>
</head>
<docTitle><text>${escapeXml(book.title)}</text></docTitle>
<docAuthor><text>${escapeXml(book.authorsString())}</text></docAuthor>
<navMap>${navPoints}</navMap>
</ncx>"""
        addZipEntry(zip, "OEBPS/toc.ncx", tocNcx)
    }

    private fun addChapters(zip: ZipOutputStream, book: Book) {
        book.chapters.forEach { chapter ->
            val chapterXhtml = createChapterXhtml(chapter)
            addZipEntry(zip, "OEBPS/${chapter.id}.xhtml", chapterXhtml)
        }
    }

    private fun createChapterXhtml(chapter: Chapter): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<title>${escapeXml(chapter.title)}</title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<link rel="stylesheet" type="text/css" href="styles.css"/>
</head>
<body>
${chapter.content}
</body>
</html>"""
    }

    private fun addZipEntry(zip: ZipOutputStream, path: String, content: String) {
        val entry = ZipEntry(path)
        if (path != "mimetype") entry.method = ZipEntry.DEFLATED
        zip.putNextEntry(entry)
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun getCurrentDateISO(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        return dateFormat.format(Date())
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")
    }

    private fun calculateCrc32(data: ByteArray): Long {
        val crc = CRC32()
        crc.update(data)
        return crc.value
    }
}