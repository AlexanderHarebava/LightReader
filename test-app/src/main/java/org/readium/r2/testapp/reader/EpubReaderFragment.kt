/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.RectF
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.annotation.ColorInt

import androidx.lifecycle.lifecycleScope   // если используете lifecycleScope напрямую (но мы используем requireActivity().lifecycleScope)
import androidx.appcompat.widget.SearchView
import androidx.core.os.BundleCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.FragmentResultListener
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.*
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.html.HtmlDecorationTemplate
import org.readium.r2.navigator.html.toCss
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.epub.pageList
import org.readium.r2.testapp.LITERATA
import org.readium.r2.testapp.AINEW.AIRequest
import org.readium.r2.testapp.Application
import org.readium.r2.testapp.reader.preferences.UserPreferencesViewModel
import org.readium.r2.testapp.search.SearchFragment
import android.view.ActionMode
import android.view.Menu
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.testapp.AINEW.AIManager
import org.readium.r2.testapp.R
import org.readium.r2.testapp.data.model.Highlight

@OptIn(ExperimentalReadiumApi::class)
class EpubReaderFragment : VisualReaderFragment() {

    override lateinit var navigator: EpubNavigatorFragment
    // Добавьте в начало класса (после объявления navigator)
    private val aiManager: AIManager by lazy {
        (requireContext().applicationContext as Application).aiManager
    }
    private lateinit var menuSearch: MenuItem
    lateinit var menuSearchView: SearchView

    private var isSearchViewIconified = true

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            isSearchViewIconified = savedInstanceState.getBoolean(IS_SEARCH_VIEW_ICONIFIED)
        }

        val readerData = model.readerInitData as? EpubReaderInitData ?: run {
            childFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
            super.onCreate(savedInstanceState)
            requireActivity().finish()
            return
        }


        childFragmentManager.fragmentFactory =
            readerData.navigatorFactory.createFragmentFactory(
                initialLocator = readerData.initialLocation,
                initialPreferences = readerData.preferencesManager.preferences.value,
                listener = model,
                configuration = EpubNavigatorFragment.Configuration {


                    selectionActionModeCallback = object : ActionMode.Callback {
                        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                            mode.menuInflater.inflate(R.menu.menu_action_mode, menu)

                            menu.add(Menu.NONE, Menu.NONE, 60, getString(R.string.action_explain_ai)).setOnMenuItemClickListener {
                                viewLifecycleOwner.lifecycleScope.launch {
                                    try {
                                        val selection = navigator.currentSelection()
                                        val selectedText = selection?.locator?.text?.highlight?.trim() ?: ""

                                        if (selectedText.isNotEmpty()) {
                                            val apiKey = (requireActivity().application as Application).getAiApiKey()
                                            if (apiKey.isNullOrBlank()) {
                                                Toast.makeText(requireContext(), getString(R.string.error_configure_api_key_first), Toast.LENGTH_LONG).show()
                                            } else {
                                                // Вместо мгновенной отправки вызываем диалог
                                                showAiPromptDialog(selectedText)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(requireContext(), getString(R.string.error_getting_text), Toast.LENGTH_SHORT).show()
                                    }
                                }
                                mode.finish() // Закрываем всплывающее меню выделения текста
                                true
                            }
                            return true
                        }

                        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

                        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                            return when (item.itemId) {
                                R.id.action_copy -> {
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        try {
                                            val selection = navigator.currentSelection()
                                            val text = selection?.locator?.text?.highlight?.trim() ?: ""
                                            if (text.isNotEmpty()) {
                                                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("copied_text", text)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(requireContext(), getString(R.string.toast_text_copied), Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(requireContext(), getString(R.string.error_copying_text), Toast.LENGTH_SHORT).show()
                                        }
                                        mode.finish()
                                    }
                                    true
                                }
                                R.id.action_translate -> {
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        try {
                                            val selection = navigator.currentSelection()
                                            val text = selection?.locator?.text?.highlight?.trim() ?: ""
                                            if (text.isNotEmpty()) {
                                                val intent = android.content.Intent().apply {
                                                    action = android.content.Intent.ACTION_PROCESS_TEXT
                                                    type = "text/plain"
                                                    putExtra(android.content.Intent.EXTRA_PROCESS_TEXT, text)
                                                    putExtra(android.content.Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                                                }
                                                startActivity(intent)
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(requireContext(), getString(R.string.error_no_translation_app), Toast.LENGTH_SHORT).show()
                                        }
                                        mode.finish()
                                    }
                                    true
                                }
                                R.id.highlight -> {
                                    showHighlightPopupWithStyle(Highlight.Style.HIGHLIGHT)
                                    mode.finish()
                                    true
                                }
                                R.id.underline -> {
                                    showHighlightPopupWithStyle(Highlight.Style.UNDERLINE)
                                    mode.finish()
                                    true
                                }
                                R.id.note -> {
                                    showAnnotationPopup()
                                    mode.finish()
                                    true
                                }
                                else -> false
                            }
                        }

                        override fun onDestroyActionMode(mode: ActionMode) { }

                    }

                    servedAssets = listOf(
                        "fonts/.*",
                        "annotation-icon.svg"
                    )
                    decorationTemplates[DecorationStyleAnnotationMark::class] = annotationMarkTemplate()
                    decorationTemplates[DecorationStylePageNumber::class] = pageNumberTemplate()
                    addFontFamilyDeclaration(FontFamily.LITERATA) {
                        addFontFace {
                            addSource("fonts/Literata-VariableFont_opsz,wght.ttf")
                            setFontStyle(FontStyle.NORMAL)
                            setFontWeight(200..900)
                        }
                        addFontFace {
                            addSource("fonts/Literata-Italic-VariableFont_opsz,wght.ttf")
                            setFontStyle(FontStyle.ITALIC)
                            setFontWeight(200..900)
                        }
                    }
                }
            )

        // Теперь передаём этот callback в configuration


        childFragmentManager.setFragmentResultListener(
            SearchFragment::class.java.name,
            this,
            FragmentResultListener { _, result ->
                menuSearch.collapseActionView()
                BundleCompat.getParcelable(
                    result,
                    SearchFragment::class.java.name,
                    Locator::class.java
                )?.let {
                    navigator.go(it)
                }
            }
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
                add(
                    R.id.fragment_reader_container,
                    EpubNavigatorFragment::class.java,
                    Bundle(),
                    NAVIGATOR_FRAGMENT_TAG
                )
            }
        }
        navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_FRAGMENT_TAG) as EpubNavigatorFragment

        return view
    }

    private val aiJavascriptInterface = object {
        @JavascriptInterface
        fun sendTextToAI(text: String) {
            if (text.trim().isEmpty()) return

            requireActivity().lifecycleScope.launch {
                Toast.makeText(requireContext(), getString(R.string.toast_ai_analyzing), Toast.LENGTH_SHORT).show()

                val messages = listOf(
                    AIRequest.Message("system", getString(R.string.ai_system_prompt_explain)),
                    AIRequest.Message("user", text)
                )

                val result = aiManager.processChat(messages)

                result.fold(
                    onSuccess = { reply ->
                        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.dialog_title_ai_response)).setPositiveButton(android.R.string.ok, null)
                            .show()
                    },
                    onFailure = { error ->
                        Toast.makeText(requireContext(), getString(R.string.error_with_message, error.message), Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        @Suppress("Unchecked_cast")
        (model.settings as UserPreferencesViewModel<EpubSettings, EpubPreferences>)
            .bind(navigator, viewLifecycleOwner)

        navigator.addInputListener(object : InputListener {
             fun onSelectionAction(action: String, text: String, rect: RectF?): Boolean {
                when (action) {
                    "highlight" -> {
                        showHighlightPopupWithStyle(Highlight.Style.HIGHLIGHT)
                        return true
                    }
                    "underline" -> {
                        showHighlightPopupWithStyle(Highlight.Style.UNDERLINE)
                        return true
                    }
                    "note" -> {
                        showAnnotationPopup()
                        return true
                    }
                    "translate" -> {
                        // Логика перевода
                        val intent = Intent().apply {
                            this.action = Intent.ACTION_PROCESS_TEXT
                            type = "text/plain"
                            putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                        }
                        try { startActivity(intent) } catch (e: Exception) { /* ... */ }

                        // Важно: очистить выделение после действия
                        (navigator as? SelectableNavigator)?.clearSelection()
                        return true
                    }
                    "copy" -> {
                        // Логика копирования...
                        (navigator as? SelectableNavigator)?.clearSelection()
                        return true
                    }
                }
                return false
            }
        })


        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Display page number labels if the book contains a `page-list` navigation document.
                (navigator as? DecorableNavigator)?.applyPageNumberDecorations()
            }
        }

        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuSearch = menu.findItem(R.id.search).apply {
                        isVisible = true
                        menuSearchView = actionView as SearchView
                    }

                    connectSearch()
                    if (!isSearchViewIconified) menuSearch.expandActionView()
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    when (menuItem.itemId) {
                        R.id.search -> {
                            return true
                        }
                        android.R.id.home -> {
                            menuSearch.collapseActionView()
                            return true
                        }
                    }
                    return false
                }
            },
            viewLifecycleOwner
        )
    }


    private fun showAiPromptDialog(selectedText: String) {
        // Создаем поле ввода программно
        val input = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.hint_ai_query)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4
        }

        // Оборачиваем во FrameLayout, чтобы задать отступы по краям
        val container = android.widget.FrameLayout(requireContext())
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(50, 20, 50, 20)
        }
        input.layoutParams = params
        container.addView(input)

        // Показываем превью текста, чтобы пользователь понимал, к чему задает вопрос
        val previewText = if (selectedText.length > 60) selectedText.take(60) + "..." else selectedText

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
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

            // Формируем итоговый запрос
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
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
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




    /**
     * Will display margin labels next to page numbers in an EPUB publication with a `page-list`
     * navigation document.
     *
     * See http://kb.daisy.org/publishing/docs/navigation/pagelist.html
     */
    private suspend fun DecorableNavigator.applyPageNumberDecorations() {
        val decorations = publication.pageList
            .mapIndexedNotNull { index, link ->
                val label = link.title ?: return@mapIndexedNotNull null
                val locator = publication.locatorFromLink(link) ?: return@mapIndexedNotNull null

                Decoration(
                    id = "page-$index",
                    locator = locator,
                    style = DecorationStylePageNumber(label = label)
                )
            }

        applyDecorations(decorations, "pageNumbers")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(IS_SEARCH_VIEW_ICONIFIED, isSearchViewIconified)
    }

    private fun connectSearch() {
        menuSearch.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {

            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                if (isSearchViewIconified) { // It is not a state restoration.
                    showSearchFragment()
                }

                isSearchViewIconified = false
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                isSearchViewIconified = true
                childFragmentManager.popBackStack()
                menuSearchView.clearFocus()

                return true
            }
        })

        menuSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            override fun onQueryTextSubmit(query: String): Boolean {
                model.search(query)
                menuSearchView.clearFocus()

                return false
            }

            override fun onQueryTextChange(s: String): Boolean {
                return false
            }
        })

        menuSearchView.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn).setOnClickListener {
            menuSearchView.requestFocus()
            model.cancelSearch()
            menuSearchView.setQuery("", false)

            (activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(
                this.view,
                0
            )
        }
    }

    private fun showSearchFragment() {
        childFragmentManager.commit {
            childFragmentManager.findFragmentByTag(SEARCH_FRAGMENT_TAG)?.let { remove(it) }
            add(
                R.id.fragment_reader_container,
                SearchFragment::class.java,
                Bundle(),
                SEARCH_FRAGMENT_TAG
            )
            hide(navigator)
            addToBackStack(SEARCH_FRAGMENT_TAG)
        }
    }

    companion object {
        private const val SEARCH_FRAGMENT_TAG = "search"
        private const val NAVIGATOR_FRAGMENT_TAG = "navigator"
        private const val IS_SEARCH_VIEW_ICONIFIED = "isSearchViewIconified"
    }
}

// Examples of HTML templates for custom Decoration Styles.

/**
 * This Decorator Style will display a tinted "pen" icon in the page margin to show that a highlight
 * has an associated note.
 *
 * Note that the icon is served from the app assets folder.
 */
private fun annotationMarkTemplate(@ColorInt defaultTint: Int = Color.YELLOW): HtmlDecorationTemplate {
    val className = "testapp-annotation-mark"
    val iconUrl = checkNotNull(EpubNavigatorFragment.assetUrl("annotation-icon.svg"))
    return HtmlDecorationTemplate(
        layout = HtmlDecorationTemplate.Layout.BOUNDS,
        width = HtmlDecorationTemplate.Width.PAGE,
        element = { decoration ->
            val style = decoration.style as? DecorationStyleAnnotationMark
            val tint = style?.tint ?: defaultTint
            // Using `data-activable=1` prevents the whole decoration container from being
            // clickable. Only the icon will respond to activation events.
            """
            <div><div data-activable="1" class="$className" style="background-color: ${tint.toCss()} !important"/></div>"
            """
        },
        stylesheet = """
            .$className {
                float: left;
                margin-left: 8px;
                width: 30px;
                height: 30px;
                border-radius: 50%;
                background: url('$iconUrl') no-repeat center;
                background-size: auto 50%;
                opacity: 0.8;
            }
            """
    )
}

/**
 * This Decoration Style is used to display the page number labels in the margins, when a book
 * provides a `page-list`. The label is stored in the [DecorationStylePageNumber] itself.
 *
 * See http://kb.daisy.org/publishing/docs/navigation/pagelist.html
 */
private fun pageNumberTemplate(): HtmlDecorationTemplate {
    val className = "testapp-page-number"
    return HtmlDecorationTemplate(
        layout = HtmlDecorationTemplate.Layout.BOUNDS,
        width = HtmlDecorationTemplate.Width.PAGE,
        element = { decoration ->
            val style = decoration.style as? DecorationStylePageNumber

            // Using `var(--RS__backgroundColor)` is a trick to use the same background color as
            // the Readium theme. If we don't set it directly inline in the HTML, it might be
            // forced transparent by Readium CSS.
            """
            <div><span class="$className" style="background-color: var(--RS__backgroundColor) !important">${style?.label}</span></div>"
            """
        },
        stylesheet = """
            .$className {
                float: left;
                margin-left: 8px;
                padding: 0px 4px 0px 4px;
                border: 1px solid;
                border-radius: 20%;
                box-shadow: rgba(50, 50, 93, 0.25) 0px 2px 5px -1px, rgba(0, 0, 0, 0.3) 0px 1px 3px -1px;
                opacity: 0.8;
            }
            """
    )
}
