/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlinx.coroutines.flow.combine

import android.app.AlertDialog
import android.content.Context

import android.graphics.RectF
import android.os.Bundle
import android.view.ActionMode
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.BaseActionModeCallback
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.Language
import org.readium.r2.testapp.R
import org.readium.r2.testapp.data.model.Bookmark
import org.readium.r2.testapp.data.model.Highlight
import org.readium.r2.testapp.databinding.FragmentReaderBinding
import org.readium.r2.testapp.reader.tts.TtsControls
import org.readium.r2.testapp.reader.tts.TtsPreferencesBottomSheetDialogFragment
import org.readium.r2.testapp.reader.tts.TtsViewModel
import org.readium.r2.testapp.utils.clearPadding
import org.readium.r2.testapp.utils.extensions.confirmDialog
import org.readium.r2.testapp.utils.extensions.throttleLatest
import org.readium.r2.testapp.utils.hideSystemUi
import org.readium.r2.testapp.utils.isSystemUiVisible
import org.readium.r2.testapp.utils.observeWhenStarted
import org.readium.r2.testapp.utils.padSystemUi
import org.readium.r2.testapp.utils.showSystemUi
import org.readium.r2.testapp.utils.toggleSystemUi
import org.readium.r2.testapp.utils.viewLifecycle
import timber.log.Timber

/*
 * Base reader fragment class
 *
 * Provides common menu items and saves last location on stop.
 */
@OptIn(ExperimentalReadiumApi::class)
abstract class VisualReaderFragment : BaseReaderFragment() {

    protected var binding: FragmentReaderBinding by viewLifecycle()

    private lateinit var navigatorFragment: Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentReaderBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * When true, the user won't be able to interact with the navigator.
     */
    private var disableTouches by mutableStateOf(false)

    private var currentPageBookmark by mutableStateOf<Bookmark?>(null)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        navigatorFragment = navigator as Fragment
        setupPageSlider()
        (navigator as OverflowableNavigator).apply {
            // This will automatically turn pages when tapping the screen edges or arrow keys.
            addInputListener(DirectionalNavigationAdapter(this))
        }

        (navigator as VisualNavigator).apply {
            addInputListener(object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    val activity = requireActivity() as ReaderActivity
                    activity.toggleSystemUI()
                    return true
                }

                // ➕ Обработка кнопок меню
                fun onSelectionAction(action: String, text: String, rect: RectF?): Boolean {
                    when (action) {
                        "copy" -> {
                            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("copied_text", text)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(requireContext(), getString(R.string.toast_text_copied), android.widget.Toast.LENGTH_SHORT).show()
                            (navigator as? SelectableNavigator)?.clearSelection() // ➕
                        }
                        "translate" -> {
                            val intent = android.content.Intent().apply {
                                this.action = android.content.Intent.ACTION_PROCESS_TEXT
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_PROCESS_TEXT, text)
                                putExtra(android.content.Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                            }
                            try { startActivity(intent) } catch (e: Exception) {
                                android.widget.Toast.makeText(requireContext(), getString(R.string.error_no_translation_app), android.widget.Toast.LENGTH_SHORT).show()
                            }
                            (navigator as? SelectableNavigator)?.clearSelection() // ➕
                        }
                        "highlight" -> {
                            rect?.let { showHighlightPopup(it, Highlight.Style.HIGHLIGHT) }
                        }
                        "underline" -> {
                            rect?.let { showHighlightPopup(it, Highlight.Style.UNDERLINE) }
                        }
                        "note" -> {
                            showAnnotationPopup()
                        }
                        else -> return false
                    }
                    return true
                }
            })
        }

        setupObservers()

        childFragmentManager.addOnBackStackChangedListener {
            updateSystemUiVisibility()
        }

        binding.navigationPanel.setOnApplyWindowInsetsListener { view, insets ->
            val windowInsetsCompat = androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(insets, view)
            val systemBars = windowInsetsCompat.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val basePadding = (0 * resources.displayMetrics.density).toInt()
            view.setPadding(
                basePadding,
                basePadding,
                basePadding,
                basePadding + systemBars.bottom
            )
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(binding.navigationPanel)

        binding.overlay.setContent {
            if (disableTouches) {
                // Add an invisible box on top of the navigator to intercept touch gestures.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                (requireActivity() as ReaderActivity).toggleSystemUI()  }
                        }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                content = { Overlay() }
            )
        }

        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menu.findItem(R.id.tts).isVisible = (model.tts != null)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    when (menuItem.itemId) {
                        R.id.tts -> {
                            checkNotNull(model.tts).start(navigator)
                            return true
                        }
                    }
                    return false
                }
            },
            viewLifecycleOwner
        )

        model.visualFragmentChannel.receive(viewLifecycleOwner) { event ->
            when (event) {
                is ReaderViewModel.VisualFragmentCommand.ShowPopup ->
                    showFootnotePopup(event.text)
            }
        }
    }


    private fun setupPageSlider() {
        viewLifecycleOwner.lifecycleScope.launch {
            val positions = publication.positions()
            val totalPositions = positions.size
            if (totalPositions == 0) {
                binding.navigationPanel.visibility = View.GONE
                return@launch
            }
            val isPdf = publication.conformsTo(Publication.Profile.PDF)

            // Мелкая шкала, чтобы палка ходила плавно и совпадала с процентом
            val seekMax = 1000
            binding.pageSeekBar.max = seekMax

            // Одна функция формата — и для текста при перетаскивании, и для локатора
            fun ratioToPercent(ratio: Double): String =
                String.format("%.2f", (ratio * 100).coerceIn(0.0, 100.0)) + "%"

            fun ratioToPage(ratio: Double): Int =
                (ratio * totalPositions).roundToInt().coerceIn(1, totalPositions)

            fun progressToRatio(progress: Int): Double =
                progress.toDouble() / seekMax

            // --- Расчёт маркеров глав (без изменений) ---
            val markers = mutableListOf<Float>()
            val toc = publication.tableOfContents

            fun flattenLinks(links: List<org.readium.r2.shared.publication.Link>): List<org.readium.r2.shared.publication.Link> {
                val flat = mutableListOf<org.readium.r2.shared.publication.Link>()
                for (link in links) {
                    flat.add(link)
                    flat.addAll(flattenLinks(link.children))
                }
                return flat
            }

            val flatToc = flattenLinks(toc)
            val hrefToPosition = mutableMapOf<String, Int>()
            positions.forEachIndexed { index, locator ->
                val href = locator.href.toString()
                if (!hrefToPosition.containsKey(href)) {
                    hrefToPosition[href] = index
                }
            }

            val chapterLinks = if (flatToc.isNotEmpty()) flatToc else publication.readingOrder
            for (link in chapterLinks) {
                val linkHref = link.href.toString().substringBefore("#")
                val posIndex = hrefToPosition.entries.firstOrNull { entry ->
                    val locHref = entry.key.substringBefore("#")
                    locHref.endsWith(linkHref) || linkHref.endsWith(locHref)
                }?.value
                if (posIndex != null && totalPositions > 1) {
                    markers.add(posIndex.toFloat() / (totalPositions - 1).toFloat())
                }
            }
            binding.chapterMarkersView.markers = markers.distinct().sorted()
            // ---------------------------------------------

            binding.pageSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val ratio = progressToRatio(progress)
                        binding.pageNumberText.text = if (isPdf) {
                            "${ratioToPage(ratio)} / $totalPositions"
                        } else {
                            ratioToPercent(ratio)
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    // Переводим долю обратно в ближайшую позицию книги
                    val ratio = progressToRatio(seekBar.progress)
                    val index = (ratio * (totalPositions - 1)).roundToInt()
                        .coerceIn(0, totalPositions - 1)
                    viewLifecycleOwner.lifecycleScope.launch {
                        publication.positions().getOrNull(index)?.let { navigator.go(it) }
                    }
                }
            })

            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.currentLocator.collect { locator ->
                    val currentPosition = locator.locations.position ?: 1

                    // Палка и текст считаются от ОДНОГО значения — totalProgression,
                    // поэтому слайдер всегда точно соответствует проценту.
                    val ratio = locator.locations.totalProgression
                        ?: (if (totalPositions > 1) {
                            (currentPosition - 1).toDouble() / (totalPositions - 1)
                        } else {
                            0.0
                        })

                    binding.pageSeekBar.progress =
                        (ratio.coerceIn(0.0, 1.0) * seekMax).roundToInt()

                    binding.pageNumberText.text = if (isPdf) {
                        "$currentPosition / $totalPositions"
                    } else {
                        ratioToPercent(ratio)
                    }
                    binding.chapterTitleText.text = locator.title ?: ""
                }
            }
        }
    }


    @Composable
    private fun BoxScope.Overlay() {
        model.tts?.let { tts ->
            TtsControls(
                model = tts,
                onPreferences = {
                    TtsPreferencesBottomSheetDialogFragment()
                        .show(childFragmentManager, "TtsSettings")
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            )
        }

        BookmarkRibbon(
            visible = currentPageBookmark != null,
            onRemove = {
                currentPageBookmark?.id?.let { id ->
                    model.deleteBookmark(id)
                    Toast.makeText(requireContext(), R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp)
        )
    }


    @Composable
    private fun BookmarkRibbon(
        visible: Boolean,
        onRemove: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                initialOffsetY = { fullHeight -> -fullHeight }
            ) + fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 160),
                targetOffsetY = { fullHeight -> -fullHeight }
            ) + fadeOut(animationSpec = tween(durationMillis = 160)),
        ) {
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(32.dp)
                    .shadow(elevation = 8.dp, shape = RibbonShape)
                    .clip(RibbonShape)
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xFF3D8BFD),
                            0.55f to Color(0xFF3D8BFD),
                            1f to Color(0xFF3D8BFD),
                        )
                    )
                    .clickable(onClick = onRemove)
            ) {

                // Стежок у верхнего края
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp, start = 6.dp, end = 6.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.35f))
                )
            }
        }
    }


    private object RibbonShape : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline {
            val w = size.width
            val h = size.height
            val notch = w * 0.55f
            return Outline.Generic(
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, h)
                    lineTo(w / 2f, h - notch)
                    lineTo(0f, h)
                    close()
                }
            )
        }
    }


    private fun isOnCurrentPage(bookmark: Bookmark, locator: Locator): Boolean {
        if (bookmark.locator.href != locator.href) return false
        val bookmarkPosition = bookmark.locator.locations.position
        val currentPosition = locator.locations.position
        if (bookmarkPosition != null && currentPosition != null) {
            return bookmarkPosition == currentPosition
        }
        val bookmarkProgression = bookmark.locator.locations.progression
        val currentProgression = locator.locations.progression
        return when {
            bookmarkProgression != null && currentProgression != null ->
                abs(bookmarkProgression - currentProgression) < 0.01
            else -> false
        }
    }

    private fun setupObservers() {

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.currentLocator
                    .throttleLatest(3.seconds)
                    .collect { locator ->
                        model.saveProgression(locator)
                    }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    navigator.currentLocator,
                    model.getBookmarks()
                ) { locator, bookmarks ->
                    bookmarks.firstOrNull { isOnCurrentPage(it, locator) }
                }.collect { currentPageBookmark = it }
            }
        }

        (navigator as? DecorableNavigator)
            ?.addDecorationListener("highlights", decorationListener)

        viewLifecycleOwner.lifecycleScope.launch {
            setupHighlights(viewLifecycleOwner.lifecycleScope)
            setupSearch(viewLifecycleOwner.lifecycleScope)
            setupTts()
        }
    }

    private suspend fun setupHighlights(scope: CoroutineScope) {
        (navigator as? DecorableNavigator)?.let { navigator ->
            model.highlightDecorations
                .onEach { decorations ->
                    Timber.d("Applying ${decorations.size} decorations to group 'highlights'")
                    // Отладка: распечатать ID стилей
                    decorations.forEach { d ->
                        Timber.d("Decoration ID: ${d.id}, Style: ${d.style::class.simpleName}")
                    }
                    navigator.applyDecorations(decorations, "highlights")
                }
                .launchIn(scope)
        }
    }

    private suspend fun setupSearch(scope: CoroutineScope) {
        (navigator as? DecorableNavigator)?.let { navigator ->
            model.searchDecorations
                .onEach { navigator.applyDecorations(it, "search") }
                .launchIn(scope)
        }
    }

    /**
     * Setup text-to-speech observers, if available.
     */
    private suspend fun setupTts() {
        model.tts?.apply {
            events
                .observeWhenStarted(viewLifecycleOwner) { event ->
                    when (event) {
                        is TtsViewModel.Event.OnError -> {
                            showError(event.error.toUserError())
                        }
                        is TtsViewModel.Event.OnMissingVoiceData ->
                            confirmAndInstallTtsVoice(event.language)
                    }
                }

            // Navigate to the currently spoken word.
            // This will automatically turn pages when needed.
            position
                .filterNotNull()
                // Improve performances by throttling the moves to maximum one per second.
                .throttleLatest(1.seconds)
                .observeWhenStarted(viewLifecycleOwner) { locator ->
                    navigator.go(locator, animated = false)
                }

            // Prevent interacting with the publication (including page turns) while the TTS is
            // playing.
            isPlaying
                .observeWhenStarted(viewLifecycleOwner) { isPlaying ->
                    disableTouches = isPlaying
                }

            // Highlight the currently spoken utterance.
            (navigator as? DecorableNavigator)?.let { navigator ->
                highlight
                    .observeWhenStarted(viewLifecycleOwner) { locator ->
                        val decoration = locator?.let {
                            Decoration(
                                id = "tts",
                                locator = it,
                                style = Decoration.Style.Highlight(tint = android.graphics.Color.RED)
                            )
                        }
                        navigator.applyDecorations(listOfNotNull(decoration), "tts")
                    }
            }
        }
    }

    /**
     * Confirms with the user if they want to download the TTS voice data for the given language.
     */
    private suspend fun confirmAndInstallTtsVoice(language: Language) {
        val activity = activity ?: return
        model.tts ?: return

        if (
            activity.confirmDialog(
                getString(
                    R.string.tts_error_language_support_incomplete,
                    language.locale.displayLanguage
                )
            )
        ) {
            AndroidTtsEngine.requestInstallVoice(activity)
        }
    }

    override fun go(locator: Locator, animated: Boolean) {
        model.tts?.stop()
        super.go(locator, animated)
    }

    override fun onDestroyView() {
        (navigator as? DecorableNavigator)?.removeDecorationListener(decorationListener)
        super.onDestroyView()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        setMenuVisibility(!hidden)
        requireActivity().invalidateOptionsMenu()
    }

    // DecorableNavigator.Listener

    private val decorationListener by lazy { DecorationListener() }

    inner class DecorationListener : DecorableNavigator.Listener {
        override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
            val decoration = event.decoration
            // We stored the highlight's database ID in the `Decoration.extras` map, for
            // easy retrieval. You can store arbitrary information in the map.
            val id = (decoration.extras["id"] as Long)
                .takeIf { it > 0 } ?: return false

            // This listener will be called when tapping on any of the decorations in the
            // "highlights" group. To differentiate between the page margin icon and the
            // actual highlight, we check for the type of `decoration.style`. But you could
            // use any other information, including the decoration ID or the extras bundle.
            if (decoration.style is DecorationStyleAnnotationMark) {
                showAnnotationPopup(id)
            } else {
                event.rect?.let { rect ->
                    val isUnderline = (decoration.style is Decoration.Style.Underline)
                    showHighlightPopup(
                        rect,
                        style = if (isUnderline) {
                            Highlight.Style.UNDERLINE
                        } else {
                            Highlight.Style.HIGHLIGHT
                        },
                        highlightId = id
                    )
                }
            }

            return true
        }
    }

    // Highlights

    protected var popupWindow: PopupWindow? = null
    protected var mode: ActionMode? = null

    // Available tint colors for highlight and underline annotations.
    private val highlightTints = mapOf</*@IdRes*/ Int, /*@ColorInt*/ Int>(
        R.id.red to android.graphics.Color.rgb(247, 124, 124),
        R.id.green to android.graphics.Color.rgb(173, 247, 123),
        R.id.blue to android.graphics.Color.rgb(124, 198, 247),
        R.id.yellow to android.graphics.Color.rgb(249, 239, 125),
        R.id.purple to android.graphics.Color.rgb(182, 153, 255)
    )

    val customSelectionActionModeCallback: ActionMode.Callback by lazy { SelectionActionModeCallback() }

    private inner class SelectionActionModeCallback : BaseActionModeCallback() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_action_mode, menu)
            if (navigator is DecorableNavigator) {
                menu.findItem(R.id.highlight).isVisible = true
                menu.findItem(R.id.underline).isVisible = true
                menu.findItem(R.id.note).isVisible = true
            }
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.action_copy -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        (navigator as? SelectableNavigator)?.currentSelection()?.let { selection ->
                            val text = selection.locator.text.highlight?.trim() ?: ""
                            if (text.isNotEmpty()) {
                                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("copied_text", text)
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(requireContext(), getString(R.string.toast_text_copied), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        mode.finish()
                    }
                    return true
                }
                R.id.action_translate -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        (navigator as? SelectableNavigator)?.currentSelection()?.let { selection ->
                            val text = selection.locator.text.highlight?.trim() ?: ""
                            if (text.isNotEmpty()) {
                                val intent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_PROCESS_TEXT
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_PROCESS_TEXT, text)
                                    putExtra(android.content.Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                                }
                                try {
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(requireContext(), getString(R.string.error_no_translation_app), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        mode.finish()
                    }
                    return true
                }
                R.id.highlight -> showHighlightPopupWithStyle(Highlight.Style.HIGHLIGHT)
                R.id.underline -> showHighlightPopupWithStyle(Highlight.Style.UNDERLINE)
                R.id.note -> showAnnotationPopup()
                else -> return false
            }

            mode.finish()
            return true
        }
    }

    protected fun showHighlightPopupWithStyle(style: Highlight.Style) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Get the rect of the current selection to know where to position the highlight
            // popup.
            (navigator as? SelectableNavigator)?.currentSelection()?.rect?.let { selectionRect ->
                showHighlightPopup(selectionRect, style)
            }
        }
    }


    protected fun showHighlightPopup(rect: RectF, style: Highlight.Style, highlightId: Long? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (popupWindow?.isShowing == true) return@launch

            model.activeHighlightId.value = highlightId

            val isReverse = (rect.top > 60)
            val popupView = layoutInflater.inflate(
                if (isReverse) R.layout.view_action_mode_reverse else R.layout.view_action_mode,
                null,
                false
            )
            popupView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            popupWindow = PopupWindow(
                popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                isFocusable = true
                setOnDismissListener {
                    model.activeHighlightId.value = null
                    (navigator as? SelectableNavigator)?.clearSelection() // ➕ Очищаем, если пользователь просто закрыл попап
                }
            }

            val x = rect.left
            val y = if (isReverse) rect.top else rect.bottom + rect.height()

            popupWindow?.showAtLocation(popupView, Gravity.NO_GRAVITY, x.toInt(), y.toInt())

            val highlight = highlightId?.let { model.highlightById(it) }
            popupView.run {
                findViewById<View>(R.id.notch).run {
                    setX(rect.left * 2)
                }

                fun selectTint(view: View) {
                    val tint = highlightTints[view.id] ?: return
                    selectHighlightTint(highlightId, style, tint)
                }

                findViewById<View>(R.id.red).setOnClickListener(::selectTint)
                findViewById<View>(R.id.green).setOnClickListener(::selectTint)
                findViewById<View>(R.id.blue).setOnClickListener(::selectTint)
                findViewById<View>(R.id.yellow).setOnClickListener(::selectTint)
                findViewById<View>(R.id.purple).setOnClickListener(::selectTint)

                findViewById<View>(R.id.annotation).setOnClickListener {
                    popupWindow?.dismiss()
                    showAnnotationPopup(highlightId)
                }
                findViewById<View>(R.id.del).run {
                    visibility = if (highlight != null) View.VISIBLE else View.GONE
                    setOnClickListener {
                        highlightId?.let {
                            model.deleteHighlight(highlightId)
                        }
                        popupWindow?.dismiss()
                        mode?.finish()
                    }
                }
            }
        }
    }



    protected open fun selectHighlightTint(
        highlightId: Long? = null,
        style: Highlight.Style,
        @ColorInt tint: Int,
    ) = viewLifecycleOwner.lifecycleScope.launch {
        if (highlightId != null) {
            model.updateHighlightStyle(highlightId, style, tint)
        } else {
            (navigator as? SelectableNavigator)?.let { navigator ->
                navigator.currentSelection()?.let { selection ->
                    model.addHighlight(
                        locator = selection.locator,
                        style = style,
                        tint = tint
                    )
                }
                navigator.clearSelection()
            }
        }

        popupWindow?.dismiss()
        mode?.finish()
    }

    protected open fun showAnnotationPopup(highlightId: Long? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            val activity = activity ?: return@launch
            val view = layoutInflater.inflate(R.layout.popup_note, null, false)
            val note = view.findViewById<EditText>(R.id.note)
            val alert = AlertDialog.Builder(activity)
                .setView(view)
                .create()

            fun dismiss() {
                alert.dismiss()
                mode?.finish()
                (activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.hideSoftInputFromWindow(note.applicationWindowToken, InputMethodManager.HIDE_NOT_ALWAYS)
                (navigator as? SelectableNavigator)?.clearSelection() // ➕
            }

            with(view) {
                val highlight = highlightId?.let { model.highlightById(it) }
                if (highlight != null) {
                    note.setText(highlight.annotation)
                    findViewById<View>(R.id.sidemark).setBackgroundColor(highlight.tint)
                    findViewById<TextView>(R.id.select_text).text =
                        highlight.locator.text.highlight

                    findViewById<TextView>(R.id.positive).setOnClickListener {
                        val text = note.text.toString()
                        model.updateHighlightAnnotation(highlight.id, annotation = text)
                        dismiss()
                    }
                } else {
                    val tint = highlightTints.values.random()
                    findViewById<View>(R.id.sidemark).setBackgroundColor(tint)
                    val navigator =
                        navigator as? SelectableNavigator ?: return@launch
                    val selection = navigator.currentSelection() ?: return@launch
                    navigator.clearSelection()
                    findViewById<TextView>(R.id.select_text).text =
                        selection.locator.text.highlight

                    findViewById<TextView>(R.id.positive).setOnClickListener {
                        model.addHighlight(
                            locator = selection.locator,
                            style = Highlight.Style.HIGHLIGHT,
                            tint = tint,
                            annotation = note.text.toString()
                        )
                        dismiss()
                    }
                }

                findViewById<TextView>(R.id.negative).setOnClickListener {
                    dismiss()
                }
            }

            alert.show()
        }
    }

    private fun showFootnotePopup(
        text: CharSequence,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Initialize a new instance of LayoutInflater service
            val inflater =
                requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

            // Inflate the custom layout/view
            val customView = inflater.inflate(R.layout.popup_footnote, null)

            // Initialize a new instance of popup window
            val mPopupWindow = PopupWindow(
                customView,
                ListPopupWindow.WRAP_CONTENT,
                ListPopupWindow.WRAP_CONTENT
            )
            mPopupWindow.isOutsideTouchable = true
            mPopupWindow.isFocusable = true

            // Set an elevation value for popup window
            // Call requires API level 21
            mPopupWindow.elevation = 5.0f

            val textView = customView.findViewById(R.id.footnote) as TextView
            textView.text = text

            // Get a reference for the custom view close button
            val closeButton = customView.findViewById(R.id.ib_close) as ImageButton

            // Set a click listener for the popup window close button
            closeButton.setOnClickListener {
                // Dismiss the popup window
                mPopupWindow.dismiss()
            }

            // Finally, show the popup window at the center location of root relative layout
            // FIXME: should anchor on noteref and be scrollable if the note is too long.
            mPopupWindow.showAtLocation(
                requireView(),
                Gravity.CENTER,
                0,
                0
            )
        }
    }


    fun updateSystemUiVisibility() {
        val activity = requireActivity() as AppCompatActivity
        if (navigatorFragment.isHidden) {
            activity.showSystemUi()
        } else {
            activity.hideSystemUi()
        }
        requireView().requestApplyInsets()
    }

    private fun updateSystemUiPadding(container: View, insets: WindowInsets) {
        if (navigatorFragment.isHidden) {
            container.padSystemUi(insets, requireActivity() as AppCompatActivity)
        } else {
            container.clearPadding()
        }
    }
}

/**
 * Decoration Style for a page margin icon.
 *
 * This is an example of a custom Decoration Style declaration.
 */
@Parcelize
data class DecorationStyleAnnotationMark(@ColorInt val tint: Int) : Decoration.Style

/**
 * Decoration Style for a page number label.
 *
 * This is an example of a custom Decoration Style declaration.
 *
 * @param label Page number label as declared in the `page-list` link object.
 */
@Parcelize
data class DecorationStylePageNumber(val label: String) : Decoration.Style
