
/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import org.readium.r2.testapp.bookshelf.FilteredBookshelfActivity
import org.readium.r2.testapp.bookshelf.NavHeaderBinder
import org.readium.r2.testapp.databinding.DialogAboutBinding

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var navController: NavController
    private val viewModel: MainViewModel by viewModels()
    private var switchDarkMode: MaterialSwitch? = null

    /** Привязка реальных данных к nav_header_main.xml. */
    private var navHeaderBinder: NavHeaderBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val container = findViewById<View>(R.id.container)
        val navView = findViewById<BottomNavigationView>(R.id.nav_view)


        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            navView.setPadding(
                navView.paddingLeft,
                navView.paddingTop,
                navView.paddingRight,
                bars.bottom
            )
            // Потребляем insets, чтобы они НЕ пошли дальше по иерархии
            WindowInsetsCompat.CONSUMED
        }

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view_drawer)

        navigationView.setBackgroundColor(
            ContextCompat.getColor(this, R.color.Main_Theme)
        )
        // 3. Переключатель темы в шапке NavigationView
        val headerView = navigationView.getHeaderView(0)
        // В методе onCreate, после инициализации navigationView:

        navHeaderBinder = NavHeaderBinder(
            context = this,
            bookRepository = (application as Application).bookRepository,
            lifecycleOwner = this,
            lifecycleScope = lifecycleScope,
            isDarkThemeProvider = { isDarkThemeEnabled(this) },
            onThemeToggle = { isDark ->
                setDarkThemeEnabled(this, isDark)
                AppCompatDelegate.setDefaultNightMode(
                    if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
                delegate.localNightMode = if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            }
        ).also { it.bind() }

// Настройка Compose для шапки
        val headerComposeView = navigationView.getHeaderView(0) as androidx.compose.ui.platform.ComposeView
        headerComposeView.setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        headerComposeView.setContent {
            androidx.compose.material3.MaterialTheme {
                val uiState by navHeaderBinder!!.uiState.collectAsState()
                org.readium.r2.testapp.bookshelf.NavHeaderMain(
                    uiState = uiState,
                    onFormatToggle = { tag -> navHeaderBinder!!.toggleFormat(tag) },
                    onThemeToggle = { isDark -> navHeaderBinder!!.toggleTheme(isDark) },
                    onFormatClick = { tag ->
                        startActivity(FilteredBookshelfActivity.newIntent(this, tag))
                        drawerLayout.closeDrawers()
                    },
                    onAboutClick = { showAboutDialog() }
                )
            }
        }



        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // 6. Остальная настройка бокового меню и кликов
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.menu_24px)
        toolbar.setNavigationOnClickListener { drawerLayout.open() }



        navView.setupWithNavController(navController)
        viewModel.channel.receive(this) { handleEvent(it) }
    }


    override fun onResume() {
        super.onResume()

        WindowCompat.getInsetsController(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }

    }

    private fun showAboutDialog() {
        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)
    }

    private fun applySavedTheme() {
        val dark = isDarkThemeEnabled(this)
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }






    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
    companion object {
        private const val PREFS_THEME = "theme_prefs"
        private const val KEY_DARK_MODE = "dark_mode"

        fun isDarkThemeEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
                .getBoolean(KEY_DARK_MODE, false) // по умолчанию светлая
        }

        fun setDarkThemeEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DARK_MODE, enabled)
                .apply()
        }
    }
    private fun handleEvent(event: MainViewModel.Event) {
        when (event) {
            is MainViewModel.Event.ImportPublicationSuccess ->
                Snackbar.make(
                    findViewById(android.R.id.content),
                    getString(R.string.import_publication_success),
                    Snackbar.LENGTH_LONG
                ).show()

            is MainViewModel.Event.ImportPublicationError -> {
                event.error.toUserError().show(this)
            }
        }
    }
}


