package org.readium.r2.testapp.utils.txttoepub

import java.io.File
import java.nio.charset.Charset
import java.util.UUID
import org.mozilla.universalchardet.UniversalDetector
import org.readium.r2.testapp.utils.fb2toepub.Author
import org.readium.r2.testapp.utils.fb2toepub.Book
import org.readium.r2.testapp.utils.fb2toepub.Chapter

/**
 * Простой парсер TXT-файлов.
 * Разбивает текст на главы по пустым строкам или заголовкам (опционально).
 */
class TxtParser {

    fun parseFile(file: File): Book {
        val bytes = file.readBytes()

        // 1. Пытаемся автоматически определить кодировку
        val detector = UniversalDetector(null)
        detector.handleData(bytes, 0, bytes.size)
        detector.dataEnd()

        // Получаем название кодировки (или null, если определить не удалось)
        val detectedCharset = detector.detectedCharset
        detector.reset()

        // 2. Декодируем текст
        // Если кодировка распознана - используем ее. Если нет - пробуем UTF-8 по умолчанию.
        val charset = if (detectedCharset != null) {
            Charset.forName(detectedCharset)
        } else {
            Charsets.UTF_8
        }

        var text = String(bytes, charset)

        if (text.contains("\uFFFD")) {
            text = String(bytes, Charset.forName("windows-1251"))
        }

        val lines = text.lines()

        // Определяем название как имя файла без расширения
        val title = file.nameWithoutExtension.ifBlank { "Unknown" }

        // Автор пока неизвестен
        val authors = listOf(Author(nickname = "Unknown"))

        // Простейшая логика: одна глава на весь файл [cite: 248]
        val chapter = Chapter(
            id = "chapter1",
            title = "Сhapter 1",
            content = buildChapterContent(lines)
        )

        return Book(
            id = UUID.randomUUID().toString(),
            title = title,
            authors = authors,
            language = "ru", // можно улучшить автоопределением [cite: 249]
            date = "",
            coverImage = null,
            chapters = listOf(chapter),
            annotation = ""
        )
    }

    private fun buildChapterContent(lines: List<String>): String {
        return lines
            .map { line ->
                if (line.isBlank()) {
                    "<p></p>"
                } else {
                    "<p>${escapeHtml(line.trim())}</p>"
                }
            }
            .joinToString("\n")
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}