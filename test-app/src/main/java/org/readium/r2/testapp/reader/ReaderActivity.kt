
/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toolbar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentResultListener
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.ViewModelProvider
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.toUri
import org.readium.r2.testapp.Application
import org.readium.r2.testapp.R
import org.readium.r2.testapp.databinding.ActivityReaderBinding
import org.readium.r2.testapp.drm.DrmManagementContract
import org.readium.r2.testapp.drm.DrmManagementFragment
import org.readium.r2.testapp.outline.OutlineContract
import org.readium.r2.testapp.outline.OutlineFragment
import org.readium.r2.testapp.utils.launchWebBrowser

/*
 * An activity to read a publication
 *
 * This class can be used as it is or be inherited from.
 */
open class ReaderActivity : AppCompatActivity() {


    private val model: ReaderViewModel by viewModels()

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = ReaderViewModel.createFactory(
            application as Application,
            ReaderActivityContract.parseIntent(this)
        )

    override fun onDestroy() {

        super.onDestroy()
    }



    override fun onStop() {
        super.onStop()

    }



    private lateinit var binding: ActivityReaderBinding
    private lateinit var readerFragment: BaseReaderFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Сначала создаём и устанавливаем layout
        val binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        this.binding = binding

        // 2. Теперь можно найти Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        toolbar.setOnApplyWindowInsetsListener { view, insets ->
            val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return@setOnApplyWindowInsetsListener insets
            lp.topMargin = 0
            lp.bottomMargin = 0
            view.layoutParams = lp
            insets
        }

        androidx.core.view.ViewCompat.requestApplyInsets(toolbar)

        // УДАЛИТЕ ЭТУ СТРОКУ: var isUIVisible = false
        // Вместо этого используем свойство класса ниже

        binding.root.post {
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            val navPanel = findViewById<View>(R.id.navigation_panel)

            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

            binding.toolbar.visibility = View.GONE
            binding.toolbar.alpha = 0f

            navPanel?.visibility = View.GONE
            navPanel?.alpha = 0f

            // Устанавливаем начальное состояние
            isUIVisible = false
        }

        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.contentInsetStartWithNavigation = 0
        val readerFragment = supportFragmentManager.findFragmentByTag(READER_FRAGMENT_TAG)
            ?.let { it as BaseReaderFragment }
            ?: run { createReaderFragment(model.readerInitData) }

        if (readerFragment is VisualReaderFragment) {
            val fullscreenDelegate = FullscreenReaderActivityDelegate(this, readerFragment, binding)
            lifecycle.addObserver(fullscreenDelegate)
        }

        readerFragment?.let { this.readerFragment = it }

        model.activityChannel.receive(this) { handleReaderFragmentEvent(it) }

        reconfigureActionBar()

        supportFragmentManager.setFragmentResultListener(
            OutlineContract.REQUEST_KEY,
            this,
            FragmentResultListener { _, result ->
                val locator = OutlineContract.parseResult(result).destination
                closeOutlineFragment(locator)
            }
        )

        supportFragmentManager.setFragmentResultListener(
            DrmManagementContract.REQUEST_KEY,
            this,
            FragmentResultListener { _, result ->
                if (DrmManagementContract.parseResult(result).hasReturned) {
                    finish()
                }
            }
        )

        supportFragmentManager.addOnBackStackChangedListener {
            reconfigureActionBar()
        }

        // Add support for display cutout.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    // ЭТО СВОЙСТВО КЛАССА (оставьте как есть)
    var isUIVisible = true

    fun toggleSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        val toolbar = binding.toolbar
        val navigationPanel = findViewById<View>(R.id.navigation_panel)

        isUIVisible = !isUIVisible

        if (isUIVisible) {
            // --- ПОКАЗЫВАЕМ ---
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())

            toolbar.visibility = View.VISIBLE
            toolbar.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()

            navigationPanel?.visibility = View.VISIBLE
            navigationPanel?.animate()
                ?.alpha(1f)
                ?.translationY(0f)
                ?.setDuration(250)
                ?.setInterpolator(android.view.animation.DecelerateInterpolator())
                ?.start()
        } else {
            // --- СКРЫВАЕМ ---
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

            // Сдвигаем Toolbar вверх за пределы экрана (учитывая его текущую позицию top)
            val hideY = -(toolbar.height.toFloat() + toolbar.top.toFloat())
            toolbar.animate()
                .alpha(0f)
                .translationY(hideY)
                .setDuration(200)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction { toolbar.visibility = View.GONE }
                .start()

            navigationPanel?.animate()
                ?.alpha(0f)
                ?.translationY(navigationPanel?.height?.toFloat() ?: 0f)
                ?.setDuration(200)
                ?.setInterpolator(android.view.animation.AccelerateInterpolator())
                ?.withEndAction { navigationPanel?.visibility = View.GONE }
                ?.start()
        }
    }



    private fun createReaderFragment(readerData: ReaderInitData): BaseReaderFragment? {
        val readerClass: Class<out Fragment>? = when (readerData) {
            is EpubReaderInitData -> EpubReaderFragment::class.java
            is ImageReaderInitData -> ImageReaderFragment::class.java
            is MediaReaderInitData -> AudioReaderFragment::class.java
            is PdfReaderInitData -> PdfReaderFragment::class.java
            is DummyReaderInitData -> null
        }

        readerClass?.let { it ->
            supportFragmentManager.commitNow {
                replace(R.id.activity_container, it, Bundle(), READER_FRAGMENT_TAG)
            }
        }

        return supportFragmentManager.findFragmentByTag(READER_FRAGMENT_TAG) as BaseReaderFragment?
    }

    override fun onStart() {
        super.onStart()
        reconfigureActionBar()
    }

    private fun reconfigureActionBar() {
        val currentFragment = supportFragmentManager.fragments.lastOrNull()

        title = when (currentFragment) {
            is OutlineFragment -> model.publication.metadata.title
            is DrmManagementFragment -> getString(R.string.title_fragment_drm_management)
            else -> null
        }

        // Логика отображения кнопки назад:
        // Мы хотим показывать её всегда, когда мы внутри ReaderActivity,
        // чтобы можно было выйти в MainActivity.
        // Если вы хотите, чтобы она исчезала только в особых случаях, измените условие ниже.
        val shouldShowBackButton = true

        supportActionBar?.setDisplayHomeAsUpEnabled(shouldShowBackButton)
    }

    private fun handleReaderFragmentEvent(command: ReaderViewModel.ActivityCommand) {
        when (command) {
            is ReaderViewModel.ActivityCommand.OpenOutlineRequested ->
                showOutlineFragment()
            is ReaderViewModel.ActivityCommand.OpenDrmManagementRequested ->
                showDrmManagementFragment()
            is ReaderViewModel.ActivityCommand.OpenExternalLink ->
                launchWebBrowser(this, command.url.toUri())
            is ReaderViewModel.ActivityCommand.ToastError ->
                command.error.show(this)
        }
    }

    private fun showOutlineFragment() {
        val outlineFragment = supportFragmentManager.findFragmentByTag(OUTLINE_FRAGMENT_TAG)
        if (outlineFragment == null) {
            supportFragmentManager.commit {
                add(
                    R.id.activity_container,
                    OutlineFragment::class.java,
                    Bundle(),
                    OUTLINE_FRAGMENT_TAG
                )
                hide(readerFragment)
                addToBackStack(null)
            }
        }
    }

    private fun closeOutlineFragment(locator: Locator) {
        readerFragment.go(locator, true)
        supportFragmentManager.popBackStack()
    }

    private fun showDrmManagementFragment() {
        supportFragmentManager.commit {
            add(
                R.id.activity_container,
                DrmManagementFragment::class.java,
                Bundle(),
                DRM_FRAGMENT_TAG
            )
            hide(readerFragment)
            addToBackStack(null)
        }
    }


    override fun finish() {

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())

        super.finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {

                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val READER_FRAGMENT_TAG = "reader"
        const val OUTLINE_FRAGMENT_TAG = "outline"
        const val DRM_FRAGMENT_TAG = "drm"
    }
}


