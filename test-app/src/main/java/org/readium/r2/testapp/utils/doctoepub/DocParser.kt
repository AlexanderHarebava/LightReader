package org.readium.r2.testapp.utils.doctoepub

import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.usermodel.*
import org.readium.r2.testapp.utils.fb2toepub.Author
import org.readium.r2.testapp.utils.fb2toepub.Book
import org.readium.r2.testapp.utils.fb2toepub.Chapter
import java.io.File
import java.io.FileInputStream
import java.util.UUID

class DocParser {
    fun parseFile(file: File): Book {
        val doc = HWPFDocument(FileInputStream(file))
        val range = doc.range
        var text = range.text()
        // Очень примитивно: одна глава – весь текст
        val chapter = Chapter("chapter1", "Unknown", "<p>${escapeHtml(text)}</p>")
        return Book(
            id = UUID.randomUUID().toString(),
            title = file.nameWithoutExtension,
            authors = listOf(Author(nickname = "")),
            language = "",
            date = "",
            chapters = listOf(chapter),
            annotation = ""
        )
    }
    private fun escapeHtml(s: String) = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
}