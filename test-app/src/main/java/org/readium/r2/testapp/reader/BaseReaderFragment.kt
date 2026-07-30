/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.readium.r2.lcp.lcpLicense
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.preferences.Configurable
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.testapp.R
import org.readium.r2.testapp.reader.preferences.MainPreferencesBottomSheetDialogFragment
import org.readium.r2.testapp.reader.preferences.QuickSettingsDialogFragment
import org.readium.r2.testapp.utils.UserError

/*
 * Base reader fragment class
 *
 * Provides common menu items and saves last location on stop.
 */
@OptIn(ExperimentalReadiumApi::class)
abstract class BaseReaderFragment : Fragment() {

    val model: ReaderViewModel by activityViewModels()
    protected val publication: Publication get() = model.publication
    private var bookmarkMenuItem: MenuItem? = null
    private var currentBookmarks: List<org.readium.r2.testapp.data.model.Bookmark> = emptyList()
    private var currentLocator: org.readium.r2.shared.publication.Locator? = null
    protected abstract val navigator: Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model.fragmentChannel.receive(this) { event ->
            fun toast(id: Int) {
                Toast.makeText(requireContext(), getString(id), Toast.LENGTH_SHORT).show()
            }

            when (event) {
                is ReaderViewModel.FragmentFeedback.BookmarkFailed -> toast(
                    R.string.bookmark_exists
                )
                is ReaderViewModel.FragmentFeedback.BookmarkSuccessfullyAdded -> toast(
                    R.string.bookmark_added
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.startReading()
    }

    override fun onPause() {
        super.onPause()
        // Сохраняем текущую позицию сразу при уходе с экрана
        model.saveProgression(navigator.currentLocator.value)
        model.stopReading()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_reader, menu)
                    menu.findItem(R.id.settings).isVisible =
                        navigator is Configurable<*, *>
                    menu.findItem(R.id.drm).isVisible =
                        model.publication.lcpLicense != null

                    // Сохраняем ссылку на пункт меню закладки
                    bookmarkMenuItem = menu.findItem(R.id.bookmark)
                    updateBookmarkIcon() // Обновляем цвет при первом создании меню
                }

                override fun onPrepareMenu(menu: Menu) {
                    super.onPrepareMenu(menu)
                    updateBookmarkIcon()
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    when (menuItem.itemId) {
                        R.id.toc -> {
                            model.activityChannel.send(
                                ReaderViewModel.ActivityCommand.OpenOutlineRequested
                            )
                        }
                        R.id.info -> {
                            PublicationMetadataDialogFragment()
                                .show(childFragmentManager, "Info")
                        }
                        R.id.bookmark -> {
                            val locator = navigator.currentLocator.value
                            // Ищем существующую закладку на этой же позиции
                            val existing = currentBookmarks.firstOrNull { isSameLocation(it, locator) }
                            if (existing != null) {
                                // Если закладка уже есть - удаляем её (Toggle)
                                model.deleteBookmark(existing.id!!)
                            } else {
                                // Если нет - добавляем
                                model.insertBookmark(locator)
                            }
                        }
                        R.id.settings -> {
                            QuickSettingsDialogFragment()
                                .show(childFragmentManager, "QuickSettings")
                        }
                        R.id.drm -> {
                            model.activityChannel.send(
                                ReaderViewModel.ActivityCommand.OpenDrmManagementRequested
                            )
                        }
                        else -> return false
                    }
                    return true
                }
            },
            viewLifecycleOwner
        )

        // Запускаем наблюдение за текущей страницей и списком закладок в базе данных
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                kotlinx.coroutines.flow.combine(
                    navigator.currentLocator,
                    model.getBookmarks()
                ) { locator, bookmarks ->
                    currentLocator = locator
                    currentBookmarks = bookmarks
                    // Проверяем, есть ли среди закладок совпадающая с текущей позицией
                    bookmarks.any { isSameLocation(it, locator) }
                }.collect { isBookmarked ->
                    updateBookmarkIcon(isBookmarked)
                }
            }
        }
    }

    /**
     * Сравнивает закладку и текущий локатор.
     * Используется допуск (tolerance) для прогресса, так как в reflowable EPUB
     * точное значение может незначительно "плавать" при перерисовке текста.
     */
    private fun isSameLocation(bm: org.readium.r2.testapp.data.model.Bookmark, loc: org.readium.r2.shared.publication.Locator): Boolean {
        if (bm.locator.href != loc.href) return false
        val bmProg = bm.locator.locations.progression
        val locProg = loc.locations.progression
        return when {
            bmProg == null && locProg == null -> true
            bmProg != null && locProg != null -> kotlin.math.abs(bmProg - locProg) < 0.005 // Допуск 0.5%
            else -> false
        }
    }

    /**
     * Динамически перекрашивает иконку закладки в меню.
     */
    /**
     * Динамически перекрашивает иконку закладки в меню.
     */
    private fun updateBookmarkIcon(isBookmarked: Boolean? = null) {
        val menuItem = bookmarkMenuItem ?: return
        val icon = menuItem.icon?.mutate() ?: return

        val isMarked = isBookmarked ?: currentBookmarks.any { bm ->
            val loc = currentLocator ?: return@any false
            isSameLocation(bm, loc)
        }

        if (isMarked) {
            // Перекрашиваем в синий (Material Blue #2196F3)
            androidx.core.graphics.drawable.DrawableCompat.setTint(icon, android.graphics.Color.parseColor("#2196F3"))
        } else {
            // Получаем colorControlNormal из темы приложения
            val typedValue = android.util.TypedValue()
            requireContext().theme.resolveAttribute(
                androidx.appcompat.R.attr.colorControlNormal,
                typedValue,
                true
            )
            val color = androidx.core.content.ContextCompat.getColor(requireContext(), typedValue.resourceId)
            androidx.core.graphics.drawable.DrawableCompat.setTint(icon, color)
        }
        menuItem.icon = icon
    }
    
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        setMenuVisibility(!hidden)
        requireActivity().invalidateOptionsMenu()
    }

    open fun go(locator: Locator, animated: Boolean) {
        navigator.go(locator, animated)
    }

    protected fun showError(error: UserError) {
        val activity = activity ?: return
        error.show(activity)
    }
}
