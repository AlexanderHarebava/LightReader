/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader
import kotlinx.coroutines.Job
import android.content.Context
import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.adapter.pdfium.navigator.PdfiumNavigatorFragment
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.adapter.pdfium.navigator.PdfiumSettings
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.testapp.AINEW.AIManager
import org.readium.r2.testapp.Application
import org.readium.r2.testapp.R
import android.graphics.Color
import android.view.inputmethod.InputMethodManager

import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorInt

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.commitNow

import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

import org.readium.adapter.pdfium.navigator.TextSelectionOverlay

import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

import org.readium.r2.testapp.AINEW.AIRequest

import org.readium.r2.testapp.data.model.Highlight
import org.readium.r2.testapp.reader.preferences.UserPreferencesViewModel


@OptIn(ExperimentalReadiumApi::class)
class PdfReaderFragment : VisualReaderFragment() {

    override lateinit var navigator: PdfiumNavigatorFragment

    private val aiManager: AIManager by lazy {
        (requireContext().applicationContext as Application).aiManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val readerData = model.readerInitData as? PdfReaderInitData ?: run {
            // We provide a dummy fragment factory  if the ReaderActivity is restored after the
            // app process was killed because the ReaderRepository is empty. In that case, finish
            // the activity as soon as possible and go back to the previous one.
            childFragmentManager.fragmentFactory = PdfNavigatorFragment.createDummyFactory(
                pdfEngineProvider = PdfiumEngineProvider()
            )
            super.onCreate(savedInstanceState)
            requireActivity().finish()
            return
        }

        childFragmentManager.fragmentFactory =
            readerData.navigatorFactory.createFragmentFactory(
                initialLocator = readerData.initialLocation,
                initialPreferences = readerData.preferencesManager.preferences.value,
                listener = model
            )

        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.commitNow {
                replace(
                    R.id.fragment_reader_container,
                    PdfNavigatorFragment::class.java,
                    Bundle(),
                    NAVIGATOR_FRAGMENT_TAG
                )
            }
        }

        @Suppress("Unchecked_cast")
        navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_FRAGMENT_TAG)!!
            as PdfiumNavigatorFragment
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        @Suppress("Unchecked_cast")
        (model.settings as UserPreferencesViewModel<PdfiumSettings, PdfiumPreferences>)
            .bind(navigator, viewLifecycleOwner)

        // Слушаем события из меню выделения PDF
        // Внутри родительского фрагмента (PdfReaderFragment) в onViewCreated:
        requireActivity().supportFragmentManager.setFragmentResultListener("pdf_selection_action", viewLifecycleOwner) { _, bundle ->
            val action = bundle.getString("action")
            val text = bundle.getString("text")
            val pageIndex = bundle.getInt("pageIndex")
            val pdfRects = bundle.getParcelableArrayList<RectF>("pdfRects")
            val screenRect = bundle.getParcelable<RectF>("rect")

            if (pdfRects != null && text != null && screenRect != null) {
                pendingPdfSelection = PendingPdfSelection(text, pageIndex, pdfRects)
                when (action) {
                    "highlight" -> showHighlightPopup(screenRect, Highlight.Style.HIGHLIGHT)
                    "underline" -> showHighlightPopup(screenRect, Highlight.Style.UNDERLINE)
                    "note" -> showAnnotationPopup()
                    "ai" -> showAiPromptDialog(text) // <- Добавлено!
                }
            }
        }

        requireActivity().supportFragmentManager.setFragmentResultListener("pdf_annotation_clicked", viewLifecycleOwner) { _, bundle ->
            val idStr = bundle.getString("annotationId") ?: return@setFragmentResultListener
            val rect = bundle.getParcelable<RectF>("rect") ?: return@setFragmentResultListener
            val highlightId = idStr.toLongOrNull() ?: return@setFragmentResultListener

            viewLifecycleOwner.lifecycleScope.launch {
                val highlight = model.highlightById(highlightId) ?: return@launch
                showHighlightPopup(rect, highlight.style, highlightId)
            }
        }

        // Подписываемся на смену страницы и обновление базы данных, чтобы отрисовывать маркеры
        // Объединяем поток текущей позиции и поток хайлайтов из базы
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    navigator.currentLocator,
                    model.highlights
                ) { locator, highlights ->
                    // Вычисляем индекс страницы из локатора
                    val pageIndex = (locator.locations.position ?: 1) - 1
                    pageIndex to highlights
                }.collect { (pageIndex, highlights) ->
                    // Вызываем отрисовку
                    updatePdfHighlights(pageIndex, highlights)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.highlights.collect { highlights ->
                    val locator = navigator.currentLocator.value
                    val pageIndex = (locator.locations.position ?: 1) - 1
                    updatePdfHighlights(pageIndex, highlights)
                }
            }
        }
    }

    data class PendingPdfSelection(val text: String, val pageIndex: Int, val pdfRects: List<RectF>)
    private var pendingPdfSelection: PendingPdfSelection? = null

    private fun getPdfiumDocumentFragment(): org.readium.adapter.pdfium.navigator.PdfiumDocumentFragment? {
        return childFragmentManager.fragments.firstOrNull { it is PdfNavigatorFragment<*, *> }
            ?.childFragmentManager?.fragments?.firstOrNull { it is org.readium.adapter.pdfium.navigator.PdfiumDocumentFragment }
            as? org.readium.adapter.pdfium.navigator.PdfiumDocumentFragment
    }

    private fun updatePdfHighlights(pageIndex: Int, highlights: List<Highlight>) {
        val docFragment = getPdfiumDocumentFragment() ?: return

        val pageAnnotations = mutableListOf<TextSelectionOverlay.PermanentAnnotation>()
        highlights.filter { it.locator.locations.position == pageIndex + 1 }.forEach { highlight ->
            val rectsFragment = highlight.locator.locations.fragments.firstOrNull { it.startsWith("rects=") }
            if (rectsFragment != null) {
                val rectsStr = rectsFragment.removePrefix("rects=")
                val rects = rectsStr.split(";").mapNotNull {
                    val parts = it.split(",")
                    if (parts.size == 4) RectF(parts[0].toFloat(), parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat()) else null
                }

                val type = if (highlight.style == Highlight.Style.UNDERLINE) TextSelectionOverlay.AnnotationType.UNDERLINE else TextSelectionOverlay.AnnotationType.HIGHLIGHT
                pageAnnotations.add(TextSelectionOverlay.PermanentAnnotation(highlight.id.toString(), rects, highlight.tint, type))

                // Если есть заметка, рисуем квадрат-индикатор как в EPUB
                if (highlight.annotation.isNotEmpty()) {
                    pageAnnotations.add(TextSelectionOverlay.PermanentAnnotation(highlight.id.toString(), rects, highlight.tint, TextSelectionOverlay.AnnotationType.NOTE))
                }
            }
        }
        docFragment.setPermanentAnnotations(pageAnnotations)
    }

    // Измените старый savePdfHighlight на этот:
    private suspend fun savePdfHighlightInternal(
        style: Highlight.Style,
        tint: Int,
        text: String,
        pageIndex: Int,
        pdfRects: List<RectF>,
        annotation: String
    ) {
        val href = model.publication.readingOrder.first().url().toString()
        val position = pageIndex + 1
        val rectsStr = pdfRects.joinToString(";") { "${it.left},${it.top},${it.right},${it.bottom}" }

        val locator = Locator(
            href = Url(href)!!,
            mediaType = MediaType.PDF,
            locations = Locator.Locations(fragments = listOf("page=$position", "rects=$rectsStr"), position = position),
            text = Locator.Text(highlight = text)
        )
        model.addHighlight(locator, style, tint, annotation)
    }

    override fun selectHighlightTint(
        highlightId: Long?,
        style: Highlight.Style,
        @ColorInt tint: Int
    ): Job = viewLifecycleOwner.lifecycleScope.launch { // Добавляем : Job и =
        if (highlightId != null) {
            model.updateHighlightStyle(highlightId, style, tint)
        } else {
            pendingPdfSelection?.let { selection ->
                // Вызываем сохранение
                savePdfHighlightInternal(style, tint, selection.text, selection.pageIndex, selection.pdfRects, "")
            }
            pendingPdfSelection = null
        }
        popupWindow?.dismiss()
        mode?.finish()
    }

    override fun showAnnotationPopup(highlightId: Long?) {
        if (highlightId != null) {
            super.showAnnotationPopup(highlightId) // Вызов стандартного механизма для уже существующих
        } else {
            val selection = pendingPdfSelection ?: return
            viewLifecycleOwner.lifecycleScope.launch {
                val activity = activity ?: return@launch
                val view = layoutInflater.inflate(R.layout.popup_note, null, false)
                val note = view.findViewById<EditText>(R.id.note)
                val alert = AlertDialog.Builder(activity).setView(view).create()

                fun dismiss() {
                    alert.dismiss()
                    mode?.finish()
                    (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(
                            note.applicationWindowToken,
                            InputMethodManager.HIDE_NOT_ALWAYS
                        )
                }

                val tint = Color.parseColor("#F9EF7D")
                view.findViewById<View>(R.id.sidemark).setBackgroundColor(tint)
                view.findViewById<TextView>(R.id.select_text).text = selection.text

                view.findViewById<TextView>(R.id.positive).setOnClickListener {
                    // Оборачиваем в launch, так как метод suspend
                    viewLifecycleOwner.lifecycleScope.launch {
                        savePdfHighlightInternal( // <-- Используем новое имя
                            Highlight.Style.HIGHLIGHT,
                            tint,
                            selection.text,
                            selection.pageIndex,
                            selection.pdfRects,
                            note.text.toString()
                        )
                        dismiss()
                        pendingPdfSelection = null
                    }
                }
                view.findViewById<TextView>(R.id.negative).setOnClickListener {
                    dismiss()
                    pendingPdfSelection = null
                }
                alert.show()
            }
        }
    }

    private fun showAiPromptDialog(selectedText: String) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.hint_ai_query)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4
        }

        val container = FrameLayout(requireContext())
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(50, 20, 50, 20) }
        input.layoutParams = params
        container.addView(input)

        val previewText = if (selectedText.length > 60) selectedText.take(60) + "..." else selectedText

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_title_ai_query))
            .setMessage(getString(R.string.dialog_message_fragment, previewText))
            .setView(container)
            .setPositiveButton(getString(R.string.action_send)) { _, _ ->
                val userQuery = input.text.toString().trim()
                sendToAiWithCustomPrompt(userQuery, selectedText)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun sendToAiWithCustomPrompt(userQuery: String, selectedText: String) {
        Toast.makeText(requireContext(), getString(R.string.toast_ai_analyzing), Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            val bookTitle = model.publication.metadata.title

            val finalPrompt = if (userQuery.isEmpty()) {
                getString(R.string.ai_prompt_explain_fragment, bookTitle, selectedText)
            } else {
                getString(R.string.ai_prompt_custom_query, bookTitle, selectedText, userQuery)
            }

            val messages = listOf(
                AIRequest.Message("system", getString(R.string.ai_system_prompt_literary)),
                AIRequest.Message("user", finalPrompt)
            )

            val result = aiManager.processChat(messages)

            result.fold(
                onSuccess = { reply ->
                    AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.dialog_title_ai_response))
                        .setMessage(reply)
                        .setPositiveButton(getString(R.string.action_got_it), null)
                        .show()
                },
                onFailure = { error ->
                    Toast.makeText(requireContext(), getString(R.string.error_with_message, error.message), Toast.LENGTH_LONG).show()
                }
            )
        }
    }



    companion object {

        const val NAVIGATOR_FRAGMENT_TAG = "navigator"
    }
}
