package org.readium.r2.testapp.utils.doctoepub

import android.util.Xml
import org.readium.r2.testapp.utils.fb2toepub.Author
import org.readium.r2.testapp.utils.fb2toepub.Book
import org.readium.r2.testapp.utils.fb2toepub.Chapter
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Улучшенный парсер DOCX.
 * - Поддерживает жирный (<w:b>), курсив (<w:i>) и подчеркивание (<w:u>).
 * - Корректно обрабатывает пустые строки как <div class="empty-line"/>.
 * - Разбивает на главы по заголовкам или по количеству слов (fallback).
 */
class DocxParser {

    companion object {
        private const val WORDS_PER_CHAPTER = 2500
        private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    }

    // Внутренняя структура для хранения богатого текста параграфа
    private data class RichParagraph(
        val styleName: String?,
        val htmlContent: String, // Уже готовый HTML с <strong>, <em> и т.д.
        val isEmpty: Boolean
    )

    fun parseFile(file: File): Book {
        val paragraphs = mutableListOf<RichParagraph>()

        FileInputStream(file).use { fis ->
            ZipInputStream(fis).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name.equals("word/document.xml", ignoreCase = true)) {
                        parseDocumentXml(zis, paragraphs)
                        break
                    }
                    entry = zis.nextEntry
                }
            }
        }

        val chapters = splitIntoChapters(paragraphs)
        val title = guessTitle(paragraphs, file.nameWithoutExtension)

        return Book(
            id = UUID.randomUUID().toString(),
            title = title,
            authors = listOf(Author(nickname = "")),
            language = "",
            date = "",
            coverImage = null,
            chapters = chapters,
            annotation = ""
        )
    }

    private fun parseDocumentXml(input: java.io.InputStream, result: MutableList<RichParagraph>) {
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")

        var eventType = parser.eventType

        // Состояние параграфа
        var inParagraph = false
        var currentStyle: String? = null
        var paraStyleRead = false

        // Состояние пробега (run)
        var inRun = false
        var isBold = false
        var isItalic = false
        var isUnderline = false

        val paragraphBuilder = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name?.substringAfter(':') ?: ""

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "p" -> {
                            inParagraph = true
                            paragraphBuilder.clear()
                            currentStyle = null
                            paraStyleRead = false
                        }
                        "pPr" -> { /* Блок свойств параграфа */ }
                        "pStyle" -> {
                            if (inParagraph && !paraStyleRead) {
                                currentStyle = parser.getAttributeValue(null, "val")
                                    ?: parser.getAttributeValue(W_NS, "val")
                                paraStyleRead = true
                            }
                        }
                        "r" -> {
                            inRun = true
                            // Сбрасываем форматирование для нового run
                            isBold = false; isItalic = false; isUnderline = false
                        }
                        "rPr" -> { /* Блок свойств run */ }
                        "b" -> if (inRun) isBold = true
                        "i" -> if (inRun) isItalic = true
                        "u" -> if (inRun) isUnderline = true

                        "t" -> {
                            if (inParagraph) {
                                try {
                                    val text = parser.nextText()
                                    if (text.isNotEmpty()) {
                                        val escaped = escapeHtml(text)
                                        var formatted = escaped

                                        // Применяем форматирование в порядке вложенности
                                        if (isUnderline) formatted = "<u>$formatted</u>"
                                        if (isItalic) formatted = "<em>$formatted</em>"
                                        if (isBold) formatted = "<strong>$formatted</strong>"

                                        paragraphBuilder.append(formatted)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (tagName) {
                        "r" -> inRun = false
                        "p" -> {
                            if (inParagraph) {
                                val content = paragraphBuilder.toString().trim()
                                val isEmpty = content.isEmpty() || content.all { it == ' ' || it == '\u00A0' }

                                // Если параграф пустой, но имеет стиль, сохраняем его как empty-line
                                // Иначе сохраняем как обычный контент
                                result.add(RichParagraph(currentStyle, content, isEmpty))
                                inParagraph = false
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private fun splitIntoChapters(paragraphs: List<RichParagraph>): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        var currentTitle = "I"
        val contentBuilder = StringBuilder()
        var chapterIndex = 1
        var hasHeadings = false
        var wordCount = 0

        for (para in paragraphs) {
            val style = para.styleName?.lowercase() ?: ""
            val isHeading = style.startsWith("heading") || style.matches(Regex("heading\\d+"))

            if (isHeading) {
                hasHeadings = true
                // Сохраняем предыдущую главу
                if (contentBuilder.isNotBlank() || chapterIndex > 1) {
                    chapters.add(Chapter("chapter$chapterIndex", currentTitle, contentBuilder.toString()))
                    chapterIndex++
                    contentBuilder.clear()
                    wordCount = 0
                }
                currentTitle = para.htmlContent.ifBlank { " $chapterIndex" }
                // Заголовок тоже может содержать форматирование, но в TOC лучше чистый текст
                // Здесь мы оставляем HTML для тела главы, если он идет первым элементом
                contentBuilder.append("<h2>${para.htmlContent}</h2>\n")
            } else {
                if (para.isEmpty) {
                    contentBuilder.append("<div class=\"empty-line\"></div>\n")
                } else {
                    contentBuilder.append("<p>${para.htmlContent}</p>\n")
                    wordCount += para.htmlContent.split(Regex("\\s+")).size
                }
            }
        }

        // Финализация последней главы
        if (contentBuilder.isNotBlank()) {
            chapters.add(Chapter("chapter$chapterIndex", currentTitle, contentBuilder.toString()))
        }

        // Fallback: если нет заголовков и получилась одна огромная глава
        if (!hasHeadings && chapters.size == 1 && wordCount > WORDS_PER_CHAPTER) {
            return reflowByWordCount(paragraphs)
        }

        return chapters.ifEmpty { listOf(Chapter("chapter1", "Unknown", "<p></p>")) }
    }

    /**
     * Пересоздает главы, разбивая их строго по количеству слов.
     * Используется для документов без стилей заголовков.
     */
    private fun reflowByWordCount(paragraphs: List<RichParagraph>): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        val builder = StringBuilder()
        var words = 0
        var idx = 1

        for (para in paragraphs) {
            if (para.isEmpty) {
                builder.append("<div class=\"empty-line\"></div>\n")
                continue
            }

            builder.append("<p>${para.htmlContent}</p>\n")
            words += para.htmlContent.split(Regex("\\s+")).size

            if (words >= WORDS_PER_CHAPTER) {
                chapters.add(Chapter("chapter$idx", " $idx", builder.toString()))
                builder.clear()
                words = 0
                idx++
            }
        }

        if (builder.isNotBlank()) {
            chapters.add(Chapter("chapter$idx", if (chapters.isEmpty()) "" else " $idx", builder.toString()))
        }
        return chapters
    }

    private fun guessTitle(paragraphs: List<RichParagraph>, fallback: String): String {
        // Ищем стиль Title или первый непустой параграф
        val titlePara = paragraphs.firstOrNull { it.styleName?.lowercase() == "title" }
            ?: paragraphs.firstOrNull { !it.isEmpty }

        // Убираем HTML-теги для метаданных книги
        return titlePara?.htmlContent?.replace(Regex("<[^>]*>"), "")?.takeIf { it.isNotBlank() }
            ?: fallback
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}