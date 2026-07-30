/*
 * Copyright 2022 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

@file:OptIn(ExperimentalReadiumApi::class)

package org.readium.r2.testapp.reader.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.readium.adapter.exoplayer.audio.ExoPlayerPreferencesEditor
import org.readium.adapter.pdfium.navigator.PdfiumPreferencesEditor
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.r2.navigator.epub.EpubPreferencesEditor
import org.readium.r2.navigator.preferences.*
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.epub.EpubLayout
import org.readium.r2.shared.util.Language
import org.readium.r2.testapp.LITERATA
import org.readium.r2.testapp.R
import org.readium.r2.testapp.reader.ReaderViewModel
import org.readium.r2.testapp.reader.tts.TtsPreferencesEditor
import org.readium.r2.testapp.shared.views.*
import org.readium.r2.testapp.utils.compose.DropdownMenuButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.stringResource

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.util.Locale
import org.readium.r2.navigator.preferences.clear
import org.readium.r2.navigator.preferences.withSupportedValues
import org.readium.r2.testapp.utils.compose.ColorPicker
/**
 * Stateful user settings component paired with a [ReaderViewModel].
 */
@Composable
fun UserPreferences(
    model: UserPreferencesViewModel<*, *>,
    title: String,
) {
    val editor by model.editor.collectAsState()

    UserPreferences(
        editor = editor,
        commit = model::commit,
        title = title
    )
}
@Composable
private fun <P : Configurable.Preferences<P>, E : PreferencesEditor<P>> UserPreferences(
    editor: E,
    commit: () -> Unit,
    title: String,
) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        // Старый заголовок - пока оставляем
        Text(
            text = title,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        )

        // ЗАМЕНА 1: Вместо старых PresetsMenuButton используем PresetsCarousel
        PresetsCarousel(
            presets = editor.presets,
            clear = editor::clear,
            commit = commit,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // ЗАМЕНА 2: Вместо старой кнопки Reset используем EnhancedResetButton
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            EnhancedResetButton(
                onReset = {
                    editor.clear()
                    commit()
                }
            )
        }

        Divider()

        // Остальной код пока оставляем без изменений
        when (editor) {
            is PdfiumPreferencesEditor ->
                FixedLayoutUserPreferences(
                    commit = commit,
                    readingProgression = editor.readingProgression,
                    scrollAxis = editor.scrollAxis,
                    fit = editor.fit,
                    pageSpacing = editor.pageSpacing
                )

            is EpubPreferencesEditor ->
                when (editor.layout) {
                    EpubLayout.REFLOWABLE ->
                        EnhancedReflowableUserPreferences(
                            commit = commit,
                            backgroundColor = editor.backgroundColor,
                            columnCount = editor.columnCount,
                            fontFamily = editor.fontFamily,
                            fontSize = editor.fontSize,
                            fontWeight = editor.fontWeight,
                            hyphens = editor.hyphens,
                            imageFilter = editor.imageFilter,
                            language = editor.language,
                            letterSpacing = editor.letterSpacing,
                            ligatures = editor.ligatures,
                            lineHeight = editor.lineHeight,
                            pageMargins = editor.pageMargins,
                            paragraphIndent = editor.paragraphIndent,
                            paragraphSpacing = editor.paragraphSpacing,
                            publisherStyles = editor.publisherStyles,
                            readingProgression = editor.readingProgression,
                            scroll = editor.scroll,
                            textAlign = editor.textAlign,
                            textColor = editor.textColor,
                            textNormalization = editor.textNormalization,
                            theme = editor.theme,
                            typeScale = editor.typeScale,
                            verticalText = editor.verticalText,
                            wordSpacing = editor.wordSpacing
                        )
                    EpubLayout.FIXED ->
                        FixedLayoutUserPreferences(
                            commit = commit,
                            backgroundColor = editor.backgroundColor,
                            language = editor.language,
                            readingProgression = editor.readingProgression,
                            spread = editor.spread
                        )
                }
            is TtsPreferencesEditor ->
                MediaUserPreferences(
                    commit = commit,
                    language = editor.language,
                    voice = editor.voice,
                    speed = editor.speed,
                    pitch = editor.pitch
                )
            is ExoPlayerPreferencesEditor ->
                MediaUserPreferences(
                    commit = commit,
                    speed = editor.speed,
                    pitch = editor.pitch
                )
        }
    }
}


@Composable
fun MediaUserPreferences(
    commit: () -> Unit,
    language: Preference<Language?>? = null,
    voice: EnumPreference<AndroidTtsEngine.Voice.Id?>? = null,
    speed: RangePreference<Double>? = null,
    pitch: RangePreference<Double>? = null,
) {
    Column {
        if (speed != null) {
            StepperItem(
                title = stringResource(R.string.speed_rate),
                preference = speed,
                commit = commit
            )
        }

        if (pitch != null) {
            StepperItem(
                title = stringResource(R.string.pitch_rate),
                preference = pitch,
                commit = commit
            )
        }
        if (language != null) {
            LanguageItem(
                preference = language,
                commit = commit
            )
        }

        if (voice != null) {
            MenuItem(
                title = stringResource(R.string.tts_voice),
                preference = voice,
                formatValue = { it?.value ?: "Default" },
                commit = commit
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FixedLayoutUserPreferences(
    commit: () -> Unit,
    language: Preference<Language?>? = null,
    readingProgression: EnumPreference<ReadingProgression>? = null,
    backgroundColor: Preference<Color>? = null,
    scroll: Preference<Boolean>? = null,
    scrollAxis: EnumPreference<Axis>? = null,
    fit: EnumPreference<Fit>? = null,
    spread: EnumPreference<Spread>? = null,
    offsetFirstPage: Preference<Boolean>? = null,
    pageSpacing: RangePreference<Double>? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        if (language != null || readingProgression != null) {
            PreferenceGroup(
                title = stringResource(R.string.language_and_direction),
                initiallyExpanded = true
            ) {
                language?.let {
                    AdaptiveLanguagePreferenceItem(
                        preference = it,
                        commit = commit
                    )
                }

                readingProgression?.let {
                    AdaptiveEnumPreferenceItem(
                        title = stringResource(R.string.reading_progression),
                        preference = it,
                        commit = commit,
                        formatValue = { value ->
                            when (value) {
                                ReadingProgression.LTR -> stringResource(R.string.left_to_right)
                                ReadingProgression.RTL -> stringResource(R.string.right_to_left)
                                else -> value.toString()
                            }
                        }
                    )
                }
            }
        }

        if (scroll != null || scrollAxis != null) {
            PreferenceGroup(
                title = stringResource(R.string.reading_mode),
                initiallyExpanded = true
            ) {
                scroll?.let {
                    AdaptiveSwitchPreferenceItem(
                        title = stringResource(R.string.scroll),
                        preference = it,
                        commit = commit
                    )
                }

                scrollAxis?.let {
                    AdaptiveEnumPreferenceItem(
                        title = stringResource(R.string.scroll_axis),
                        preference = it,
                        commit = commit,
                        formatValue = { value ->
                            when (value) {
                                Axis.HORIZONTAL -> stringResource(R.string.horizontal)
                                Axis.VERTICAL -> stringResource(R.string.vertical)
                                else -> value.toString()
                            }
                        }
                    )
                }
            }
        }

        if (
            fit != null ||
            spread != null ||
            offsetFirstPage != null ||
            pageSpacing != null ||
            backgroundColor != null
        ) {
            PreferenceGroup(
                title = stringResource(R.string.fixed_layout_settings),
                initiallyExpanded = true
            ) {
                fit?.let {
                    AdaptiveEnumPreferenceItem(
                        title = stringResource(R.string.fit),
                        preference = it,
                        commit = commit,
                        formatValue = { value ->
                            when (value) {
                                Fit.CONTAIN -> stringResource(R.string.contain)
                                Fit.COVER -> stringResource(R.string.cover)
                                Fit.WIDTH -> stringResource(R.string.width)
                                Fit.HEIGHT -> stringResource(R.string.height)
                                else -> value.toString()
                            }
                        }
                    )
                }

                spread?.let {
                    AdaptiveEnumPreferenceItem(
                        title = stringResource(R.string.spread),
                        preference = it,
                        commit = commit,
                        formatValue = { value ->
                            when (value) {
                                Spread.AUTO -> stringResource(R.string.auto)
                                Spread.NEVER -> stringResource(R.string.never)
                                Spread.ALWAYS -> stringResource(R.string.always)
                                else -> value.toString()
                            }
                        }
                    )
                }

                offsetFirstPage?.let {
                    AdaptiveSwitchPreferenceItem(
                        title = stringResource(R.string.offset_first_page),
                        preference = it,
                        commit = commit
                    )
                }

                pageSpacing?.let {
                    AdaptiveStepperPreferenceItem(
                        title = stringResource(R.string.page_spacing),
                        preference = it,
                        commit = commit
                    )
                }

                backgroundColor?.let {
                    AdaptiveColorPreferenceItem(
                        title = stringResource(R.string.background_color),
                        preference = it,
                        commit = commit
                    )
                }
            }
        }
    }
}



@Composable
private fun AdaptivePreferenceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun AdaptivePreferenceHeader(
    title: String,
    subtitle: String?,
    isEffective: Boolean,
    showClear: Boolean,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isEffective) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Важно: всегда держим место под крестик.
        // Иначе при появлении clear-кнопки текст будет перестраиваться.
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            if (showClear) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.reset),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AdaptiveLanguagePreferenceItem(
    preference: Preference<Language?>,
    commit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val languages = remember {
        Locale.getAvailableLocales()
            .map { Language(it).removeRegion() }
            .distinct()
            .sortedBy { it.locale.displayName }
    }

    AdaptiveMenuPreferenceItem(
        title = stringResource(R.string.language),
        preference = preference.withSupportedValues(languages + null),
        commit = commit,
        formatValue = { it?.locale?.displayName ?: stringResource(R.string.unknown) },
        modifier = modifier
    )
}

@Composable
private fun <T> AdaptiveMenuPreferenceItem(
    title: String,
    preference: EnumPreference<T>,
    commit: () -> Unit,
    formatValue: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedValue by remember(preference.value, preference.effectiveValue) {
        derivedStateOf { preference.value ?: preference.effectiveValue }
    }

    val subtitleText = formatValue(selectedValue)

    AdaptivePreferenceCard(
        modifier = modifier,
        onClick = { expanded = true }
    ) {
        AdaptivePreferenceHeader(
            title = title,
            subtitle = subtitleText,
            isEffective = preference.isEffective,
            showClear = preference.value != null,
            onClear = {
                preference.clear()
                commit()
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 220.dp)
        ) {
            preference.supportedValues.forEach { value ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = formatValue(value),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        preference.set(value)
                        commit()
                        expanded = false
                    },
                    trailingIcon = {
                        if (value == selectedValue) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> AdaptiveEnumPreferenceItem(
    title: String,
    preference: EnumPreference<T>,
    commit: () -> Unit,
    formatValue: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    val selectedValue by remember(preference.value, preference.effectiveValue) {
        derivedStateOf { preference.value ?: preference.effectiveValue }
    }

    val subtitleText = formatValue(selectedValue)

    AdaptivePreferenceCard(modifier = modifier) {
        AdaptivePreferenceHeader(
            title = title,
            subtitle = subtitleText,
            isEffective = preference.isEffective,
            showClear = preference.value != null,
            onClear = {
                preference.clear()
                commit()
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // FlowRow решает проблему "слишком зажатого" текста:
        // чипы переносятся на следующую строку, если им не хватает места.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            preference.supportedValues.forEach { option ->
                FilterChip(
                    selected = option == selectedValue,
                    onClick = {
                        if (option == preference.value) {
                            preference.clear()
                        } else {
                            preference.set(option)
                        }
                        commit()
                    },
                    label = {
                        Text(
                            text = formatValue(option),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AdaptiveSwitchPreferenceItem(
    title: String,
    preference: Preference<Boolean>,
    commit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val checked = preference.value ?: preference.effectiveValue

    AdaptivePreferenceCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (preference.isEffective) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = {
                    preference.set(it)
                    commit()
                }
            )

            // Резервируем место под clear-иконку
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (preference.value != null) {
                    IconButton(
                        onClick = {
                            preference.clear()
                            commit()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.reset),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdaptiveStepperPreferenceItem(
    title: String,
    preference: RangePreference<Double>,
    commit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val value = preference.value ?: preference.effectiveValue
    val formattedValue = preference.formatValue(value)

    AdaptivePreferenceCard(modifier = modifier) {
        AdaptivePreferenceHeader(
            title = title,
            subtitle = formattedValue,
            isEffective = preference.isEffective,
            showClear = preference.value != null,
            onClear = {
                preference.clear()
                commit()
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    preference.decrement()
                    commit()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = stringResource(R.string.decrease)
                )
            }

            Text(
                text = formattedValue,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            IconButton(
                onClick = {
                    preference.increment()
                    commit()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.increase)
                )
            }
        }
    }
}

@Composable
private fun AdaptiveColorPreferenceItem(
    title: String,
    preference: Preference<Color>,
    commit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPicking by remember { mutableStateOf(false) }

    val currentValue = preference.value ?: preference.effectiveValue
    val subtitleText = String.format("#%06X", 0xFFFFFF and currentValue.int)

    AdaptivePreferenceCard(
        modifier = modifier,
        onClick = { isPicking = true }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (preference.isEffective) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = ComposeColor(currentValue.int),
                        shape = RoundedCornerShape(8.dp)
                    )
            )

            // Резервируем место под clear-иконку
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (preference.value != null) {
                    IconButton(
                        onClick = {
                            preference.clear()
                            commit()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.reset),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (isPicking) {
        Dialog(onDismissRequest = { isPicking = false }) {
            Column(
                horizontalAlignment = Alignment.End
            ) {
                ColorPicker { color ->
                    isPicking = false
                    preference.set(Color(color))
                    commit()
                }

                TextButton(
                    onClick = {
                        isPicking = false
                        preference.clear()
                        commit()
                    }
                ) {
                    Text(stringResource(R.string.reset))
                }
            }
        }
    }
}










@Composable
private fun PresetChip(
    preset: Preset,
    clear: () -> Unit,
    commit: () -> Unit
) {
    FilterChip(
        selected = false,
        onClick = {
            clear()
            preset.apply()
            commit()
        },
        label = { Text(preset.title) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize)
            )
        }
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
private fun PresetsMenuButton(
    presets: List<Preset>,
    clear: () -> Unit,
    commit: () -> Unit,
) {
    if (presets.isEmpty()) return

    DropdownMenuButton(
        text = { Text("Presets") }
    ) { dismiss ->

        for (preset in presets) {
            DropdownMenuItem(
                text = { Text(preset.title) },
                onClick = {
                    dismiss()
                    clear()
                    preset.apply()
                    commit()
                }
            )
        }
    }
}

/**
 * A preset is a named group of settings applied together.
 */

/**
 * A preset is a named group of settings applied together.
 */
class Preset(
    val title: String,
    val apply: () -> Unit,
)

/**
 * Returns the presets associated with the [Configurable.Settings] receiver.
 */
val <P : Configurable.Preferences<P>> PreferencesEditor<P>.presets: List<Preset> get() =
    when (this) {
        is EpubPreferencesEditor ->
            when (layout) {
                EpubLayout.FIXED -> emptyList()
                EpubLayout.REFLOWABLE -> listOf(
                    Preset("Increase legibility") {
                        wordSpacing.set(0.6)
                        fontSize.set(1.4)
                        fontWeight.set(2.0)
                    },
                    Preset("Document") {
                        scroll.set(true)
                    },
                    Preset("Ebook") {
                        scroll.set(false)
                    },
                    Preset("Manga") {
                        scroll.set(false)
                        readingProgression.set(ReadingProgression.RTL)
                    }
                )
            }
        else ->
            emptyList()
    }
