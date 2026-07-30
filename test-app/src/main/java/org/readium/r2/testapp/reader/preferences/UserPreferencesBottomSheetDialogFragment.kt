/*
 * Copyright 2022 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader.preferences

import android.app.Dialog
import android.os.Bundle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.readium.r2.testapp.reader.ReaderViewModel
import org.readium.r2.testapp.utils.compose.ComposeBottomSheetDialogFragment

abstract class UserPreferencesBottomSheetDialogFragment(
    private val title: String,
) : ComposeBottomSheetDialogFragment(
    isScrollable = true
) {
    abstract val preferencesModel: UserPreferencesViewModel<*, *>

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels

            // Адаптивная высота в зависимости от размера экрана
            val peekHeight = (screenHeight * 0.4).toInt()
            val maxHeight = (screenHeight * 0.9).toInt()

            behavior.apply {
                this.peekHeight = peekHeight
                this.maxHeight = maxHeight
                isFitToContents = true
                skipCollapsed = true
            }

            window?.setDimAmount(0.15f)
        }

    @Composable
    override fun Content() {
        UserPreferences(preferencesModel, title)
    }
}

// В UserPreferencesBottomSheetDialogFragment добавить поиск


class MainPreferencesBottomSheetDialogFragment : UserPreferencesBottomSheetDialogFragment(
    "User Settings"
) {

    private val viewModel: ReaderViewModel by activityViewModels()

    override val preferencesModel: UserPreferencesViewModel<*, *> by lazy {
        checkNotNull(viewModel.settings)
    }
}
