package org.readium.r2.testapp.utils.fb2toepub

import android.util.Base64
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import timber.log.Timber
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class FB2Parser {

    fun parseFile(file: File): Book {
        val document = parseXmlDocument(file)
        val root = document.documentElement

        // 1. Извлекаем все бинарные данные (картинки)
        val images = extractImages(root)

        // 2. Метаданные
        val title = extractSimpleMetadata(root, "book-title") ?: "Untitled"
        val authors = extractAuthors(root)
        val language = extractSimpleMetadata(root, "lang") ?: "en"
        val date = extractSimpleMetadata(root, "date") ?: ""
        val annotation = extractAnnotation(root)
        val coverImageId = extractCoverImageId(root)

        // 3. Главы
        val chapters = extractChapters(root, images)

        return Book(
            title = title,
            authors = authors,
            language = language,
            date = date,
            coverImageId = coverImageId,
            images = images,
            chapters = chapters,
            annotation = annotation
        )
    }

    private fun parseXmlDocument(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(file)
    }

    private fun extractImages(root: Element): Map<String, ImageData> {
        val images = mutableMapOf<String, ImageData>()
        val binaries = root.getElementsByTagName("binary")
        for (i in 0 until binaries.length) {
            val bin = binaries.item(i) as Element
            val id = bin.getAttribute("id")
            val contentType = bin.getAttribute("content-type")
            val base64Data = bin.textContent.trim()

            if (id.isNotBlank() && contentType.isNotBlank() && base64Data.isNotBlank()) {
                try {
                    val data = Base64.decode(base64Data, Base64.DEFAULT)
                    images[id] = ImageData(id, contentType, data)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to decode image: $id")
                }
            }
        }
        return images
    }

    private fun extractCoverImageId(root: Element): String? {
        val titleInfo = getDescriptionTitleInfo(root)
        val coverpage = titleInfo?.getElementsByTagName("coverpage")?.item(0) as? Element
        if (coverpage != null) {
            val images = coverpage.getElementsByTagName("image")
            if (images.length > 0) {
                val img = images.item(0) as Element
                val href = getHref(img)
                if (href.startsWith("#")) return href.removePrefix("#")
            }
        }
        // Фоллбэк: первая попавшаяся картинка
        val binaries = root.getElementsByTagName("binary")
        for (i in 0 until binaries.length) {
            val bin = binaries.item(i) as Element
            if (bin.getAttribute("content-type").contains("image/", ignoreCase = true)) {
                return bin.getAttribute("id")
            }
        }
        return null
    }

    private fun extractAuthors(root: Element): List<Author> {
        val titleInfo = getDescriptionTitleInfo(root)
        val authorNodes = titleInfo?.getElementsByTagName("author")
        val authors = mutableListOf<Author>()
        if (authorNodes != null) {
            for (i in 0 until authorNodes.length) {
                val authorNode = authorNodes.item(i) as Element
                authors.add(Author(
                    firstName = getTextContent(authorNode, "first-name") ?: "",
                    middleName = getTextContent(authorNode, "middle-name") ?: "",
                    lastName = getTextContent(authorNode, "last-name") ?: "",
                    nickname = getTextContent(authorNode, "nickname") ?: ""
                ))
            }
        }
        return authors
    }

    private fun extractAnnotation(root: Element): String {
        val titleInfo = getDescriptionTitleInfo(root)
        val annotationNode = titleInfo?.getElementsByTagName("annotation")?.item(0) as? Element
        return extractTextFromElement(annotationNode) ?: ""
    }


    private fun extractChapters(root: Element, images: Map<String, ImageData>): List<Chapter> {
        val body = root.getElementsByTagName("body").item(0) as? Element ?: return emptyList()
        val chapters = mutableListOf<Chapter>()

        // Рекурсивный обход всех section в body
        var chapterIndex = 0
        val topLevelSections = getDirectChildrenByTagName(body, "section")

        for (section in topLevelSections) {
            chapterIndex++
            val title = extractSectionTitle(section) ?: "Chapter $chapterIndex"
            val content = nodeToXhtml(section, images, 1)

            chapters.add(
                Chapter(
                    id = "chapter$chapterIndex",
                    title = title.trim(),
                    content = content
                )
            )
        }

        // Фоллбэк: если секций нет, но есть текст прямо в body
        if (chapters.isEmpty() && body.textContent.isNotBlank()) {
            chapters.add(
                Chapter(
                    id = "chapter1",
                    title = "Content",
                    content = nodeToXhtml(body, images, 1)
                )
            )
        }

        return chapters
    }

    /**
     * Улучшенное извлечение заголовка раздела.
     * Учитывает <title>, <subtitle> и текстовые узлы.
     */
    private fun extractSectionTitle(section: Element): String? {
        val titleElement = getDirectChildByTagName(section, "title") ?: return null

        val parts = mutableListOf<String>()
        val children = titleElement.childNodes

        for (i in 0 until children.length) {
            val node = children.item(i)
            when {
                node.nodeType == Node.TEXT_NODE -> {
                    val text = node.textContent.trim()
                    if (text.isNotEmpty()) parts.add(text)
                }
                node is Element -> {
                    when (node.tagName.lowercase()) {
                        "p" -> parts.add(node.textContent.trim())
                        "subtitle" -> parts.add(": ${node.textContent.trim()}")
                        "emphasis", "strong" -> parts.add(node.textContent.trim())
                    }
                }
            }
        }

        return parts.joinToString(" ").takeIf { it.isNotBlank() }
    }

    /**
     * Возвращает только прямых потомков с указанным именем тега.
     * Важно для корректной работы с вложенными section.
     */
    private fun getDirectChildrenByTagName(parent: Element, tagName: String): List<Element> {
        val result = mutableListOf<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName.equals(tagName, ignoreCase = true)) {
                result.add(node)
            }
        }
        return result
    }

    private fun getDirectChildByTagName(parent: Element, tagName: String): Element? {
        return getDirectChildrenByTagName(parent, tagName).firstOrNull()
    }


    // --- ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ МЕТАДАННЫХ (ИСПРАВЛЕНО) ---

    /**
     * Extract simple text metadata from title-info section
     */
    private fun extractSimpleMetadata(root: Element, tagName: String): String? {
        val titleInfo = getDescriptionTitleInfo(root)
        return getTextContent(titleInfo, tagName)
    }

    private fun getDescriptionTitleInfo(root: Element): Element? {
        val description = root.getElementsByTagName("description").item(0) as? Element
        return description?.getElementsByTagName("title-info")?.item(0) as? Element
    }

    private fun getTextContent(parent: Element?, tagName: String): String? {
        val node = parent?.getElementsByTagName(tagName)?.item(0)
        return node?.textContent?.trim()
    }

    // ---------------------------------------------------------

    private fun extractTextFromElement(element: Element?): String? {
        if (element == null) return null
        return element.textContent.trim().takeIf { it.isNotBlank() }
    }

    // --- ГЕНЕРАЦИЯ XHTML ИЗ DOM ДЕРЕВА ---

    private fun nodeToXhtml(node: Node, images: Map<String, ImageData>, sectionDepth: Int = 1): String {
        if (node.nodeType == Node.TEXT_NODE) return escapeHtml(node.textContent)
        if (node.nodeType != Node.ELEMENT_NODE) return ""

        val el = node as Element
        val tagName = el.tagName.lowercase()

        return when (tagName) {
            "p" -> "<p>${childrenToXhtml(el, images, sectionDepth)}</p>\n"
            "strong", "b" -> "<strong>${childrenToXhtml(el, images, sectionDepth)}</strong>"
            "emphasis", "i" -> "<em>${childrenToXhtml(el, images, sectionDepth)}</em>"
            "style" -> "<span class=\"custom-style\">${childrenToXhtml(el, images, sectionDepth)}</span>"
            "a" -> {
                val href = getHref(el)
                if (href.startsWith("#")) "<a href=\"${href}\">${childrenToXhtml(el, images, sectionDepth)}</a>"
                else "<a href=\"${escapeHtml(href)}\" target=\"_blank\">${childrenToXhtml(el, images, sectionDepth)}</a>"
            }
            "image" -> {
                val href = getHref(el)
                if (href.startsWith("#")) {
                    val imgId = href.removePrefix("#")
                    if (images.containsKey(imgId)) "<div class=\"image\"><img src=\"images/$imgId\" alt=\"\"/></div>\n" else ""
                } else ""
            }
            "empty-line" -> "<p class=\"empty-line\">&nbsp;</p>\n"
            "cite" -> "<blockquote>${childrenToXhtml(el, images, sectionDepth)}</blockquote>\n"
            "poem" -> "<div class=\"poem\">${childrenToXhtml(el, images, sectionDepth)}</div>\n"
            "stanza" -> "<div class=\"stanza\">${childrenToXhtml(el, images, sectionDepth)}</div>\n"
            "v" -> "<p class=\"verse\">${childrenToXhtml(el, images, sectionDepth)}</p>\n"
            "text-author" -> "<p class=\"text-author\">${childrenToXhtml(el, images, sectionDepth)}</p>\n"
            "date" -> "<p class=\"date\">${childrenToXhtml(el, images, sectionDepth)}</p>\n"
            "subtitle" -> "<h${sectionDepth + 1}>${childrenToXhtml(el, images, sectionDepth)}</h${sectionDepth + 1}>\n"
            "table" -> "<table>${childrenToXhtml(el, images, sectionDepth)}</table>\n"
            "tr" -> "<tr>${childrenToXhtml(el, images, sectionDepth)}</tr>\n"
            "th" -> "<th>${childrenToXhtml(el, images, sectionDepth)}</th>\n"
            "td" -> "<td>${childrenToXhtml(el, images, sectionDepth)}</td>\n"
            "code" -> "<pre><code>${childrenToXhtml(el, images, sectionDepth)}</code></pre>\n"
            "sup" -> "<sup>${childrenToXhtml(el, images, sectionDepth)}</sup>"
            "sub" -> "<sub>${childrenToXhtml(el, images, sectionDepth)}</sub>"
            "title" -> {
                var inner = childrenToXhtml(el, images, sectionDepth)
                inner = inner.replace(Regex("</?p>"), "") // Убираем <p> внутри заголовков
                "<h$sectionDepth>$inner</h$sectionDepth>\n"
            }
            "section" -> "<div class=\"section\">\n${childrenToXhtml(el, images, sectionDepth + 1)}</div>\n"
            "body" -> childrenToXhtml(el, images, sectionDepth)
            else -> childrenToXhtml(el, images, sectionDepth)
        }
    }

    private fun childrenToXhtml(el: Element, images: Map<String, ImageData>, sectionDepth: Int = 1): String {
        val sb = StringBuilder()
        val children = el.childNodes
        for (i in 0 until children.length) {
            sb.append(nodeToXhtml(children.item(i), images, sectionDepth))
        }
        return sb.toString()
    }

    private fun getHref(el: Element): String {
        var href = el.getAttribute("l:href")
        if (href.isBlank()) href = el.getAttribute("xlink:href")
        if (href.isBlank()) href = el.getAttributeNS("http://www.w3.org/1999/xlink", "href")
        return href
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")
    }
}