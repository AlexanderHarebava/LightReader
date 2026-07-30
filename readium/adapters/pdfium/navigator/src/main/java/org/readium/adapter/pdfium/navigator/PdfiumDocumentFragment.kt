/*
* Copyright 2022 Readium Foundation. All rights reserved.
* Use of this source code is governed by the BSD-style license
* available in the top-level LICENSE file of the project.
*/
@file:OptIn(InternalReadiumApi::class)

package org.readium.adapter.pdfium.navigator

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.view.ActionMode
import org.readium.adapter.pdfium.navigator.R

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.barteksc.pdfviewer.PDFView
import kotlin.collections.mapNotNull
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.navigator.pdf.PdfDocumentFragment
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.Fit
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.InternalReadiumApi
import org.readium.r2.shared.publication.LocalizedString
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.SingleJob
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.toDebugDescription
import timber.log.Timber

@ExperimentalReadiumApi
public class PdfiumDocumentFragment internal constructor(
    private val publication: Publication,
    private val href: Url,
    private val initialPageIndex: Int,
    initialSettings: PdfiumSettings,
    private val listener: Listener?,
) : PdfDocumentFragment<PdfiumSettings>() {

    // Добавим поле для временного EditText, чтобы держать ссылку
    private var cachedPdfBoxDocument: com.tom_roush.pdfbox.pdmodel.PDDocument? = null
    private var selectedText: String? = null
    private var selectionRect: RectF? = null
    private lateinit var textSelectionOverlay: TextSelectionOverlay
    private var currentResource: Resource? = null

    // Dummy constructor to address https://github.com/readium/kotlin-toolkit/issues/395
    public constructor() : this(
        publication = Publication(
            manifest = Manifest(
                metadata = Metadata(
                    identifier = "readium:dummy",
                    localizedTitle = LocalizedString("")
                )
            )
        ),
        href = Url("publication.pdf")!!,
        initialPageIndex = 0,
        initialSettings = PdfiumSettings(
            fit = Fit.WIDTH,
            pageSpacing = 0.0,
            readingProgression = ReadingProgression.LTR,
            scrollAxis = Axis.VERTICAL
        ),
        listener = null
    )

    internal interface Listener {
        fun onResourceLoadFailed(href: Url, error: ReadError)
        fun onConfigurePdfView(configurator: PDFView.Configurator)
        fun onTap(point: PointF): Boolean
    }

    private var currentPdfiumDocument: org.readium.adapter.pdfium.document.PdfiumDocument? = null
    private lateinit var pdfView: PDFView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {

        // 1. Создаем PDFView
        pdfView = PDFView(inflater.context, null).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 2. Создаем наш Overlay (теперь он контейнер)
        textSelectionOverlay = TextSelectionOverlay(inflater.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Устанавливаем слушатели
            setOnSelectionFinishedListener(object : TextSelectionOverlay.OnSelectionFinishedListener {
                override fun onSelectionFinished(selectionRectInView: RectF, pageIndex: Int) {
                    handleSelectionFinished(selectionRectInView, pageIndex)
                }
            })

            // ИСПРАВЛЕНИЕ ЗДЕСЬ: мы обращаемся напрямую к свойству onAnnotationClicked
            // без указания "textSelectionOverlay.", так как уже находимся внутри него.
            onAnnotationClicked = { annotation, rect ->
                val bundle = Bundle().apply {
                    putString("annotationId", annotation.id)
                    putParcelable("rect", rect)
                }
                requireActivity().supportFragmentManager.setFragmentResult("pdf_annotation_clicked", bundle)
            }

            setOnTextSelectedListener { text, rects, page ->
                Timber.d("Text selected: $text")
            }

            // 3. Добавляем PDFView ВНУТРЬ оверлея
            addView(pdfView)
        }

        // 4. Возвращаем оверлей как корневой View
        return textSelectionOverlay
    }


    private var pendingAnnotations: List<TextSelectionOverlay.PermanentAnnotation>? = null

    public fun setPermanentAnnotations(annotations: List<TextSelectionOverlay.PermanentAnnotation>) {
        if (this::textSelectionOverlay.isInitialized) {
            textSelectionOverlay.setPermanentAnnotations(annotations)
        } else {

            pendingAnnotations = annotations
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initReflectionFields()


        pendingAnnotations?.let {
            textSelectionOverlay.setPermanentAnnotations(it)
            pendingAnnotations = null
        }

        resetJob = SingleJob(viewLifecycleOwner.lifecycleScope)
        reset(pageIndex = initialPageIndex)
    }

    private lateinit var resetJob: SingleJob


    private fun reset(pageIndex: Int = _pageIndex.value) {
        if (view == null) return
        val context = context?.applicationContext ?: return

        resetJob.launch {
            val resource = requireNotNull(publication.get(href))
            currentResource = resource // Сохраняем ресурс для PDFBox
            withContext(Dispatchers.IO) {
                val file = resource.sourceUrl?.toFile()
                if (file != null && file.exists()) {
                    cachedPdfBoxDocument?.close()
                    cachedPdfBoxDocument = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)
                }
            }
            val document = PdfiumDocumentFactory(context)
                .open(resource, null)
                .getOrElse { error ->
                    Timber.e(error.toDebugDescription())
                    listener?.onResourceLoadFailed(href, error)
                    return@launch
                }

            currentPdfiumDocument = document
            pageCount = document.pageCount

            // Вычисляем индекс страницы для view с учетом RTL
            val page = convertPageIndexToView(pageIndex)

            pdfView.recycle()
            pdfView
                .fromSource { _, _, _ -> document.document }
                .apply {
                    if (isPagesOrderReversed) {
                        pages(*((pageCount - 1) downTo 0).toList().toIntArray())
                    }
                }
                .swipeHorizontal(settings.scrollAxis == Axis.HORIZONTAL)
                .spacing(settings.pageSpacing.roundToInt())
                .apply { listener?.onConfigurePdfView(this) }
                .defaultPage(page)
                .onRender {
                    if (settings.fit == Fit.WIDTH) {
                        pdfView.fitToWidth(page)
                        pdfView.jumpTo(page, false)
                    }
                    updateOverlayTransform()
                }
                .onPageChange { index, _ ->
                    _pageIndex.value = convertPageIndexFromView(index)
                    actionMode?.finish() // <- Закрываем меню!
                    textSelectionOverlay.setCurrentPageIndex(index)
                    textSelectionOverlay.clearSelection()
                    updateOverlayTransform()
                }

                .onTap { event ->
                    // СНАЧАЛА проверяем, не кликнул ли пользователь по готовому хайлайту/заметке
                    if (textSelectionOverlay.handleAnnotationClick(event.x, event.y)) {
                        return@onTap true // Отменяем дальнейшую обработку тапа, если попали в аннотацию
                    }

                    listener?.onTap(PointF(event.x, event.y)) ?: false
                }
                        .onDraw { canvas, pageWidth, pageHeight, displayedPage ->
                            updateOverlayTransformFast() // Синхронизируем каждый кадр скролла/зума
                        }

                .load()
        }
    }

    private fun handleSelectionFinished(selectionRectInView: RectF, pageIndex: Int) {
        val resource = currentResource ?: return
        val context = requireContext()

        lifecycleScope.launch {
            var currentZoom = 1f
            var currentXOffset = 0f
            var currentYOffset = 0f

            var pageXOffsetAtZoom1 = 0f
            var pageYOffsetAtZoom1 = 0f
            var pageWidthAtZoom1 = 0f
            var pageHeightAtZoom1 = 0f

            try {
                val zoomField = pdfView.javaClass.getDeclaredField("zoom")
                zoomField.isAccessible = true
                currentZoom = zoomField.getFloat(pdfView)

                val offsetXField = pdfView.javaClass.getDeclaredField("currentXOffset")
                offsetXField.isAccessible = true
                currentXOffset = offsetXField.getFloat(pdfView)

                val offsetYField = pdfView.javaClass.getDeclaredField("currentYOffset")
                offsetYField.isAccessible = true
                currentYOffset = offsetYField.getFloat(pdfView)

                //  if (Math.abs(currentYOffset) > 10000f) currentYOffset = 0f
                //  if (Math.abs(currentXOffset) > 10000f) currentXOffset = 0f

                val pdfFileField = pdfView.javaClass.getDeclaredField("pdfFile")
                pdfFileField.isAccessible = true
                val pdfFile = pdfFileField.get(pdfView)

                if (pdfFile != null) {
                    val getPageOffsetMethod = pdfFile.javaClass.getDeclaredMethod("getPageOffset", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                    getPageOffsetMethod.isAccessible = true
                    val getSecondaryPageOffsetMethod = pdfFile.javaClass.getDeclaredMethod("getSecondaryPageOffset", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                    getSecondaryPageOffsetMethod.isAccessible = true
                    val getScaledPageSizeMethod = pdfFile.javaClass.getDeclaredMethod("getScaledPageSize", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                    getScaledPageSizeMethod.isAccessible = true

                    val isSwipeVertical = pdfView.isSwipeVertical

                    val pageOffsetAtZoom1 = getPageOffsetMethod.invoke(pdfFile, pageIndex, 1.0f) as Float
                    val secondaryPageOffsetAtZoom1 = getSecondaryPageOffsetMethod.invoke(pdfFile, pageIndex, 1.0f) as Float

                    pageXOffsetAtZoom1 = if (isSwipeVertical) secondaryPageOffsetAtZoom1 else pageOffsetAtZoom1
                    pageYOffsetAtZoom1 = if (isSwipeVertical) pageOffsetAtZoom1 else secondaryPageOffsetAtZoom1

                    val scaledSizeAtZoom1 = getScaledPageSizeMethod.invoke(pdfFile, pageIndex, 1.0f)
                    val getWidthMethod = scaledSizeAtZoom1.javaClass.getDeclaredMethod("getWidth")
                    val getHeightMethod = scaledSizeAtZoom1.javaClass.getDeclaredMethod("getHeight")
                    pageWidthAtZoom1 = getWidthMethod.invoke(scaledSizeAtZoom1) as Float
                    pageHeightAtZoom1 = getHeightMethod.invoke(scaledSizeAtZoom1) as Float
                } else {
                    Timber.w("pdfFile is null")
                    return@launch
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to get PDFView transform")
                return@launch
            }

            // --- ЗАЩИТА ОТ "НУЛЕВОГО" ВЫДЕЛЕНИЯ (ТАПА) ---
            // Если пользователь просто тапнул (не тянул палец), делаем рамку 40x40 пикселей вокруг точки
            var safeLeft = selectionRectInView.left
            var safeRight = selectionRectInView.right
            var safeTop = selectionRectInView.top
            var safeBottom = selectionRectInView.bottom

            if (Math.abs(safeRight - safeLeft) < 15f) {
                safeLeft -= 20f
                safeRight += 20f
            }
            if (Math.abs(safeBottom - safeTop) < 15f) {
                safeTop -= 20f
                safeBottom += 20f
            }

            val safeSelectionRect = RectF(safeLeft, safeTop, safeRight, safeBottom)

            // Переводим исправленную рамку в Zoom 1.0
            val selectionLeftAtZoom1 = (safeSelectionRect.left - currentXOffset) / currentZoom
            val selectionTopAtZoom1 = (safeSelectionRect.top - currentYOffset) / currentZoom
            val selectionRightAtZoom1 = (safeSelectionRect.right - currentXOffset) / currentZoom
            val selectionBottomAtZoom1 = (safeSelectionRect.bottom - currentYOffset) / currentZoom

            val selectionRectAtZoom1 = RectF(selectionLeftAtZoom1, selectionTopAtZoom1, selectionRightAtZoom1, selectionBottomAtZoom1)

            Timber.d("DEBUG_PDF: Пользователь выделил экран (Safe): $safeSelectionRect")
            Timber.d("DEBUG_PDF: Ищем на странице $pageIndex с координатами (Zoom1): $selectionRectAtZoom1")

            val result = withContext(Dispatchers.IO) {
                extractTextWithPdfBox(
                    context, resource, pageIndex, selectionRectAtZoom1,
                    pageXOffsetAtZoom1, pageYOffsetAtZoom1,
                    pageWidthAtZoom1, pageHeightAtZoom1
                )
            }

            if (result != null && result.first.isNotBlank()) {
                textSelectionOverlay.finalizeSelection(
                    selectedText = result.first,
                    selectedRectsInPdfCoords = result.second,
                    scale = currentZoom,
                    offsetX = currentXOffset,
                    offsetY = currentYOffset,
                    pageIndex = pageIndex
                )
                showCopyMenu(result.first, safeSelectionRect) // Передаем координаты
            } else {
                Timber.w("Текст не найден или пустой. Посмотри логи с тегом DEBUG_PDF, чтобы понять почему не пересеклись рамки.")
            }
        }
    }


    private var actionMode: ActionMode? = null // [cite: 115]

    private fun showCopyMenu(text: String, selectionRect: RectF) {
        // 1. Очистка предыдущего состояния
        actionMode?.finish()

        // 2. Запускаем нативное плавающее меню выделения
        actionMode = textSelectionOverlay.startActionMode(
            TextSelectionCallback(text, selectionRect), // Передаем рамку в Callback
            ActionMode.TYPE_FLOATING
        )

        // 3. Fallback
        if (actionMode == null) {
            Timber.e("ActionMode не создался! Используем ClipboardManager как fallback")
            copyToClipboard(text)
        } else {
            Timber.d("ActionMode успешно создан")
        }
    }


    private fun copyToClipboard(text: String) {
        val context = context ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("selected text", text)
        clipboard.setPrimaryClip(clip)
        // Заменили хардкод на getString
        Toast.makeText(context, context.getString(R.string.toast_text_copied), Toast.LENGTH_SHORT).show()
    }

    private inner class TextSelectionCallback(
        private val selectedText: String,
        private val selectionRect: RectF
    ) : android.view.ActionMode.Callback2() {

        // Определяем локальные уникальные ID для пунктов меню
        private val MENU_COPY = android.R.id.copy
        private val MENU_TRANSLATE = 101
        private val MENU_HIGHLIGHT = 102
        private val MENU_UNDERLINE = 103
        private val MENU_NOTE = 104
        private val MENU_AI = 105

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val ctx = requireContext() // Получаем контекст для доступа к ресурсам

            menu.add(Menu.NONE, MENU_COPY, 0, android.R.string.copy)
            menu.add(Menu.NONE, MENU_TRANSLATE, 1, ctx.getString(R.string.action_translate_text))
            menu.add(Menu.NONE, MENU_HIGHLIGHT, 2, ctx.getString(R.string.action_mode_menu_highlight))
            menu.add(Menu.NONE, MENU_UNDERLINE, 3, ctx.getString(R.string.action_mode_menu_underline))
            menu.add(Menu.NONE, MENU_NOTE, 4, ctx.getString(R.string.action_mode_menu_note))
            menu.add(Menu.NONE, MENU_AI, 5, ctx.getString(R.string.action_explain_ai))
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            // Используем наши локальные ID
            val action = when (item.itemId) {
                MENU_COPY -> "copy"
                MENU_TRANSLATE -> "translate"
                MENU_HIGHLIGHT -> "highlight"
                MENU_UNDERLINE -> "underline"
                MENU_NOTE -> "note"
                MENU_AI -> "ai"
                else -> return false
            }

            // Обрабатываем системные команды на месте
            if (action == "copy") {
                copyToClipboard(selectedText)
                mode.finish()
                return true
            }
            if (action == "translate") {
                translateText(selectedText)
                mode.finish()
                return true
            }

            // Для хайлайтов, заметок и ИИ отправляем данные во внешний PdfReaderFragment
            val bundle = Bundle().apply {
                putString("action", action)
                putString("text", selectedText)
                putParcelable("rect", selectionRect)
                putInt("pageIndex", _pageIndex.value)
                putParcelableArrayList("pdfRects", ArrayList(textSelectionOverlay.getCurrentSelectedRects()))
            }
            requireActivity().supportFragmentManager.setFragmentResult("pdf_selection_action", bundle)
            mode.finish()
            return true
        }



        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            textSelectionOverlay.clearSelection()
        }

        override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: android.graphics.Rect?) {
            outRect?.set(
                selectionRect.left.toInt(),
                selectionRect.top.toInt(),
                selectionRect.right.toInt(),
                selectionRect.bottom.toInt()
            )
        }
    }

    // Добавьте поля для кэширования reflection на уровне класса
    private var zoomField: java.lang.reflect.Field? = null
    private var offsetXField: java.lang.reflect.Field? = null
    private var offsetYField: java.lang.reflect.Field? = null

    private fun initReflectionFields() {
        try {
            zoomField = pdfView.javaClass.getDeclaredField("zoom").apply { isAccessible = true }
            offsetXField = pdfView.javaClass.getDeclaredField("currentXOffset").apply { isAccessible = true }
            offsetYField = pdfView.javaClass.getDeclaredField("currentYOffset").apply { isAccessible = true }
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache PDFView fields")
        }
    }

    // Измените метод updateOverlayTransform для использования закэшированных полей
    private fun updateOverlayTransformFast() {
        try {
            val scale = zoomField?.getFloat(pdfView) ?: 1f
            val offsetX = offsetXField?.getFloat(pdfView) ?: 0f
            val offsetY = offsetYField?.getFloat(pdfView) ?: 0f
            textSelectionOverlay.setPdfViewTransform(scale, offsetX, offsetY)
            textSelectionOverlay.invalidate() // Заставляем оверлей перерисоваться
        } catch (e: Exception) {
            // Игнорируем ошибки при быстром обновлении
        }
    }


    private fun shareText(text: String) {
        val context = context ?: return
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        // Заменили хардкод на добавленный строковый ресурс
        startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.action_send_text)))
    }


    private fun translateText(text: String) {
        val context = context ?: return
        // Отправляем текст в Google Переводчик или любое другое встроенное приложение
        val intent = android.content.Intent(android.content.Intent.ACTION_PROCESS_TEXT).apply {
            putExtra(android.content.Intent.EXTRA_PROCESS_TEXT, text)
            type = "text/plain"
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Timber.e("Не найдено приложение для перевода")

            Toast.makeText(context, context.getString(R.string.error_no_translation_app), Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun extractTextWithPdfBox(
        context: Context,
        resource: Resource,
        pageIndex: Int,
        selectionRectAtZoom1: RectF,
        pageXOffsetAtZoom1: Float,
        pageYOffsetAtZoom1: Float,
        pageWidthAtZoom1: Float,
        pageHeightAtZoom1: Float
    ): Pair<String, List<RectF>>? {

        val file = resource.sourceUrl?.toFile()
        if (file == null || !file.exists()) return null

        val document = cachedPdfBoxDocument ?: return null
        try {
            if (pageIndex >= document.numberOfPages) return null


            val page = document.getPage(pageIndex)
            val cropBox = page.cropBox
            val pdfBoxWidth = cropBox.width
            val pdfBoxHeight = cropBox.height

            val scaleX = pageWidthAtZoom1 / pdfBoxWidth
            val scaleY = pageHeightAtZoom1 / pdfBoxHeight

            Timber.d("DEBUG_PDF: PDFBox PageSize=$pdfBoxWidth x $pdfBoxHeight, ScaledPageSize=$pageWidthAtZoom1 x $pageHeightAtZoom1, scaleX=$scaleX, scaleY=$scaleY")

            val positions = mutableListOf<com.tom_roush.pdfbox.text.TextPosition>()
            val customStripper = object : com.tom_roush.pdfbox.text.PDFTextStripper() {
                override fun processTextPosition(text: com.tom_roush.pdfbox.text.TextPosition) {
                    positions.add(text)
                }
            }
            customStripper.startPage = pageIndex + 1
            customStripper.endPage = pageIndex + 1
            customStripper.writeText(document, java.io.StringWriter())

            val selectedBlocks = mutableListOf<Pair<String, RectF>>()
            var logCount = 0

            for (pos in positions) {
                val text = pos.unicode ?: continue
                if (text.isBlank()) continue


                val adjustedX = pos.x

                val adjustedY = pos.y - pos.height * 0.85f

                val localLeft = adjustedX * scaleX
                val localTop = adjustedY * scaleY
                val localWidth = pos.width * scaleX
                val localHeight = pos.height * scaleY

                val globalLeft = pageXOffsetAtZoom1 + localLeft
                val globalTop = pageYOffsetAtZoom1 + localTop
                val globalRight = globalLeft + localWidth
                val globalBottom = globalTop + localHeight

                val blockRectAtZoom1 = RectF(globalLeft, globalTop, globalRight, globalBottom)

                // Логируем первые 5 слов, чтобы просто видеть, ГДЕ pdfbox видит текст
                if (logCount < 5) {
                    Timber.d("DEBUG_PDF: Блок [ $text ] -> RectF: $blockRectAtZoom1")
                    logCount++
                }

                if (RectF.intersects(selectionRectAtZoom1, blockRectAtZoom1)) {
                    selectedBlocks.add(Pair(text, blockRectAtZoom1))
                    Timber.d("DEBUG_PDF: >>> СОВПАДЕНИЕ! Выделено слово [ $text ]")
                }
            }

            val builder = java.lang.StringBuilder()
            val mergedRects = mutableListOf<RectF>()
            var currentWordRect: RectF? = null

            for (i in selectedBlocks.indices) {
                val currentBlock = selectedBlocks[i]
                val currText = currentBlock.first
                val currRect = currentBlock.second

                if (i > 0) {
                    val prevRect = selectedBlocks[i - 1].second
                    val gapX = currRect.left - prevRect.right
                    val gapY = kotlin.math.abs(currRect.top - prevRect.top)

                    // Если прыжок по Y (новая строка) или заметный отступ по X (пробел) - это новое слово
                    if (gapY > currRect.height() * 0.5f || gapX > currRect.height() * 0.25f) {
                        builder.append(if (gapY > currRect.height() * 0.5f) "\n" else " ")

                        // Сохраняем готовую рамку предыдущего слова/строки
                        if (currentWordRect != null) {
                            // Немного расширяем рамку для красоты (создаем визуальный padding)
                            currentWordRect.inset(-4f, -6f)
                            mergedRects.add(currentWordRect)
                        }
                        // Начинаем новую рамку
                        currentWordRect = RectF(currRect)
                    } else {
                        // Буквы стоят плотно друг к другу — склеиваем их рамки (Union)
                        currentWordRect?.union(currRect)
                    }
                } else {
                    currentWordRect = RectF(currRect)
                }
                builder.append(currText)
            }

            if (currentWordRect != null) {
                currentWordRect.inset(-4f, -6f) // Делаем ее чуть толще, как текстовый маркер
                mergedRects.add(currentWordRect)
            }

            val fullText = builder.toString()
            return Pair(fullText, mergedRects)

        } catch (e: Exception) {
            Timber.e(e, "Ошибка извлечения текста через PDFBox")
            return null
        }
    }

    override fun onDestroyView() {
        cachedPdfBoxDocument?.close()
        cachedPdfBoxDocument = null
        super.onDestroyView()
    }

    private fun updateOverlayTransform() {
        try {
            val zoomField = pdfView.javaClass.getDeclaredField("zoom")
            zoomField.isAccessible = true
            val scale = zoomField.getFloat(pdfView)
            val offsetXField = pdfView.javaClass.getDeclaredField("currentXOffset")
            offsetXField.isAccessible = true
            val offsetX = offsetXField.getFloat(pdfView)
            val offsetYField = pdfView.javaClass.getDeclaredField("currentYOffset")
            offsetYField.isAccessible = true
            val offsetY = offsetYField.getFloat(pdfView)
            textSelectionOverlay.setPdfViewTransform(scale, offsetX, offsetY)
        } catch (e: Exception) {
            Timber.e(e, "Не удалось получить параметры трансформации PDFView для оверлея")
        }
    }

    private var pageCount = 0
    private val _pageIndex = MutableStateFlow(initialPageIndex)
    override val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

    override fun goToPageIndex(index: Int, animated: Boolean): Boolean {
        if (!isValidPageIndex(index)) {
            return false
        }
        pdfView.jumpTo(convertPageIndexToView(index), animated)
        return true
    }

    private fun isValidPageIndex(pageIndex: Int): Boolean {
        val validRange = 0 until pageCount
        return validRange.contains(pageIndex)
    }

    private fun convertPageIndexToView(page: Int): Int {
        var index = (page - 1).coerceAtLeast(0)
        if (isPagesOrderReversed) {
            index = (pageCount - 1) - index
        }
        return index
    }

    private fun convertPageIndexFromView(index: Int): Int {
        var page = index + 1
        if (isPagesOrderReversed) {
            page = (pageCount + 1) - page
        }
        return page
    }

    private val isPagesOrderReversed: Boolean get() =
        settings.scrollAxis == Axis.HORIZONTAL && settings.readingProgression == ReadingProgression.RTL

    private var settings: PdfiumSettings = initialSettings

    override fun applySettings(settings: PdfiumSettings) {
        if (this.settings == settings) {
            return
        }
        this.settings = settings
        reset()
    }
    public fun setPermanentHighlights(highlights: List<TextSelectionOverlay.PermanentHighlight>) {
        textSelectionOverlay.setPermanentHighlights(highlights)
    }
}