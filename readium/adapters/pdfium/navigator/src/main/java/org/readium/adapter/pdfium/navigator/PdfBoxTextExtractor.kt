package org.readium.adapter.pdfium.navigator

import android.content.Context
import android.graphics.RectF
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.util.resource.Resource
import timber.log.Timber
import java.io.File

/**
 * Данные о текстовом блоке.
 */
public data class TextBlock(
    val text: String,
    val rect: RectF // Координаты относительно страницы PDF (не экрана)
)


public object PdfBoxTextExtractor {



    public suspend fun getTextBlocks(
        context: Context,
        resource: Resource,
        pageIndex: Int
    ): List<TextBlock> = withContext(Dispatchers.IO) {
        val blocks = mutableListOf<TextBlock>()

        // Попытка открыть как файл для производительности
        val file = resource.sourceUrl?.toFile()
        if (file == null || !file.exists()) {
            Timber.e("PDFBox extraction failed: Resource is not a file or doesn't exist.")
            return@withContext emptyList()
        }

        var document: PDDocument? = null
        try {
            document = PDDocument.load(file)

            // Проверка на наличие страницы
            if (pageIndex >= document.numberOfPages) {
                return@withContext emptyList()
            }

            val page = document.getPage(pageIndex)
            val mediaBox = page.mediaBox
            val cropBox = page.cropBox



            val stripper = PDFTextStripperByArea()
            stripper.sortByPosition = true

            val positions = mutableListOf<TextPosition>()
            val customStripper = object : com.tom_roush.pdfbox.text.PDFTextStripper() {
                override fun processTextPosition(text: TextPosition) {
                    positions.add(text)
                }
            }
            customStripper.startPage = pageIndex + 1
            customStripper.endPage = pageIndex + 1
            customStripper.writeText(document, java.io.StringWriter()) // Запускаем парсинг


            blocks.addAll(groupPositionsIntoBlocks(positions, cropBox))

        } catch (e: Exception) {
            Timber.e(e, "Error extracting text with PDFBox")
        } finally {
            document?.close()
        }

        blocks
    }


    private fun groupPositionsIntoBlocks(
        positions: List<TextPosition>,
        cropBox: com.tom_roush.pdfbox.pdmodel.common.PDRectangle
    ): List<TextBlock> {
        val blocks = mutableListOf<TextBlock>()
        val pdfHeight = cropBox.height

        for (pos in positions) {
            val text = pos.unicode ?: continue
            if (text.isBlank()) continue

            val x = pos.x
            val y = pos.y  // PDFBox: Y=0 внизу страницы
            val width = pos.width
            val textHeight = pos.height

            // === ВОЗМОЖНОЕ ИСПРАВЛЕНИЕ: Инверсия Y если нужно ===
            // val yInverted = pdfHeight - y - textHeight

            val rect = RectF(x, y, x + width, y + textHeight)
            blocks.add(TextBlock(text, rect))
        }
        return blocks
    }
}