package org.readium.r2.testapp.reader.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.r2.navigator.preferences.*
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign
import org.readium.r2.shared.util.Language
import org.readium.r2.testapp.LITERATA
import org.readium.r2.testapp.R
import org.readium.r2.testapp.shared.views.ButtonGroupItem
import org.readium.r2.testapp.shared.views.ColorItem
import org.readium.r2.testapp.shared.views.LanguageItem
import org.readium.r2.testapp.shared.views.MenuItem
import org.readium.r2.testapp.shared.views.StepperItem
import org.readium.r2.testapp.shared.views.SwitchItem

@Composable
fun EnhancedReflowableUserPreferences(
    commit: () -> Unit,
    backgroundColor: Preference<Color>? = null,
    columnCount: EnumPreference<ColumnCount>? = null,
    fontFamily: Preference<FontFamily?>? = null,
    fontSize: RangePreference<Double>? = null,
    fontWeight: RangePreference<Double>? = null,
    hyphens: Preference<Boolean>? = null,
    imageFilter: EnumPreference<ImageFilter?>? = null,
    language: Preference<Language?>? = null,
    letterSpacing: RangePreference<Double>? = null,
    ligatures: Preference<Boolean>? = null,
    lineHeight: RangePreference<Double>? = null,
    pageMargins: RangePreference<Double>? = null,
    paragraphIndent: RangePreference<Double>? = null,
    paragraphSpacing: RangePreference<Double>? = null,
    publisherStyles: Preference<Boolean>? = null,
    readingProgression: EnumPreference<ReadingProgression>? = null,
    scroll: Preference<Boolean>? = null,
    textAlign: EnumPreference<ReadiumTextAlign?>? = null,
    textColor: Preference<Color>? = null,
    textNormalization: Preference<Boolean>? = null,
    theme: EnumPreference<Theme>? = null,
    typeScale: RangePreference<Double>? = null,
    verticalText: Preference<Boolean>? = null,
    wordSpacing: RangePreference<Double>? = null,
) {
    Column {
        // Группа 1: Язык и направление
        if (language != null || readingProgression != null || verticalText != null) {
            PreferenceGroup(
                title = stringResource(R.string.language_and_direction),
                initiallyExpanded = false
            ) {
                if (language != null) {
                    EnhancedLanguageItem(
                        preference = language,
                        commit = commit
                    )
                }

                if (readingProgression != null) {
                    EnhancedButtonGroupItem(
                        title = stringResource(R.string.reading_progression),
                        preference = readingProgression,
                        commit = commit,
                        formatValue = {
                            when (it) {
                                ReadingProgression.LTR -> stringResource(R.string.left_to_right)
                                ReadingProgression.RTL -> stringResource(R.string.right_to_left)
                                else -> it.toString()
                            }
                        }
                    )
                }

                if (verticalText != null) {
                    EnhancedSwitchItem(
                        title = stringResource(R.string.vertical_text),
                        preference = verticalText,
                        commit = commit
                    )
                }
            }
        }

        // Группа 2: Режим чтения
        if (scroll != null || columnCount != null || pageMargins != null) {
            PreferenceGroup(
                title = stringResource(R.string.reading_mode),
                initiallyExpanded = false
            ) {
                if (scroll != null) {
                    EnhancedSwitchItem(
                        title = stringResource(R.string.scroll),
                        preference = scroll,
                        commit = commit
                    )
                }

                if (columnCount != null) {
                    EnhancedButtonGroupItem(
                        title = stringResource(R.string.columns),
                        preference = columnCount,
                        commit = commit,
                        formatValue = { value ->
                            when (value) {
                                ColumnCount.AUTO -> stringResource(R.string.auto)
                                ColumnCount.ONE -> "1"
                                ColumnCount.TWO -> "2"
                                else -> value.toString()
                            }
                        }
                    )
                }

                if (pageMargins != null) {
                    EnhancedStepperItem(
                        title = stringResource(R.string.page_margins),
                        preference = pageMargins,
                        commit = commit
                    )
                }
            }
        }

        // Группа 3: Тема и цвета
        if (theme != null || textColor != null || imageFilter != null || backgroundColor != null) {
            PreferenceGroup(
                title = stringResource(R.string.theme_and_colors),
                initiallyExpanded = false
            ) {
                if (theme != null) {
                    EnhancedButtonGroupItem(
                        title = stringResource(R.string.theme),
                        preference = theme,
                        commit = commit,
                        formatValue = { value ->
                            when (value) {
                                Theme.LIGHT -> stringResource(R.string.light)
                                Theme.DARK -> stringResource(R.string.dark)
                                Theme.SEPIA -> stringResource(R.string.sepia)
                                else -> value.toString()
                            }
                        }
                    )
                }

                if (imageFilter != null) {
                    EnhancedButtonGroupItem(
                        title = stringResource(R.string.image_filter),
                        preference = imageFilter,
                        commit = commit,
                        formatValue = { value ->
                            when (value) {
                                ImageFilter.DARKEN -> stringResource(R.string.darken)
                                ImageFilter.INVERT -> stringResource(R.string.invert)
                                null -> stringResource(R.string.none)
                                else -> value.toString()
                            }
                        }
                    )
                }

                if (textColor != null) {
                    EnhancedColorItem(
                        title = stringResource(R.string.text_color),
                        preference = textColor,
                        commit = commit
                    )
                }

                if (backgroundColor != null) {
                    EnhancedColorItem(
                        title = stringResource(R.string.background_color),
                        preference = backgroundColor,
                        commit = commit
                    )
                }
            }
        }

        // Группа 4: Шрифты
        if (fontFamily != null || fontSize != null || fontWeight != null || textNormalization != null) {
            PreferenceGroup(
                title = stringResource(R.string.fonts),
                initiallyExpanded = false
            ) {
                if (fontFamily != null) {
                    EnhancedMenuItem(
                        title = stringResource(R.string.typeface),
                        preference = fontFamily.withSupportedValues(
                            null, FontFamily.LITERATA, FontFamily.SANS_SERIF,
                            FontFamily.IA_WRITER_DUOSPACE, FontFamily.ACCESSIBLE_DFA, FontFamily.OPEN_DYSLEXIC
                        ),
                        commit = commit,
                        formatValue = { value ->
                            when (value) {
                                null -> stringResource(R.string.original)
                                FontFamily.SANS_SERIF -> stringResource(R.string.sans_serif)
                                else -> value?.name ?: stringResource(R.string.original)
                            }
                        }
                    )
                }

                if (fontSize != null) {
                    EnhancedStepperItem(
                        title = stringResource(R.string.font_size),
                        preference = fontSize,
                        commit = commit
                    )
                }

                if (fontWeight != null) {
                    EnhancedStepperItem(
                        title = stringResource(R.string.font_weight),
                        preference = fontWeight,
                        commit = commit
                    )
                }

                if (textNormalization != null) {
                    EnhancedSwitchItem(
                        title = stringResource(R.string.text_normalization),
                        preference = textNormalization,
                        commit = commit
                    )
                }
            }
        }

        // Группа 5: Расширенные настройки текста (только если нет издательских стилей)
        val showAdvancedTextSettings = publisherStyles?.let {
            !(it.value ?: it.effectiveValue)
        } ?: true

        if (showAdvancedTextSettings && (
                textAlign != null ||
                    typeScale != null ||
                    lineHeight != null ||
                    paragraphIndent != null ||
                    paragraphSpacing != null ||
                    wordSpacing != null ||
                    letterSpacing != null ||
                    hyphens != null ||
                    ligatures != null
                )) {
            PreferenceGroup(
                title = stringResource(R.string.advanced_text_settings),
                initiallyExpanded = false
            ) {
                if (publisherStyles != null) {
                    SwitchItem(
                        title = stringResource(R.string.publisher_styles),
                        preference = publisherStyles,
                        commit = commit
                    )
                    Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                }

                if (textAlign != null) {
                    EnhancedButtonGroupItem(
                        title = stringResource(R.string.alignment),
                        preference = textAlign,
                        commit = commit,
                        formatValue = { value ->
                            when (value) {
                                ReadiumTextAlign.CENTER -> stringResource(R.string.center)
                                ReadiumTextAlign.JUSTIFY -> stringResource(R.string.justify)
                                ReadiumTextAlign.START -> stringResource(R.string.start)
                                ReadiumTextAlign.END -> stringResource(R.string.end)
                                ReadiumTextAlign.LEFT -> stringResource(R.string.left)
                                ReadiumTextAlign.RIGHT -> stringResource(R.string.right)
                                null -> stringResource(R.string.default_align)
                                else -> value.toString()
                            }
                        }
                    )
                }

                if (typeScale != null) {
                    EnhancedStepperItem(
                        title = stringResource(R.string.type_scale),
                        preference = typeScale,
                        commit = commit
                    )
                }

                if (lineHeight != null) {
                    EnhancedStepperItem(
                        title = stringResource(R.string.line_height),
                        preference = lineHeight,
                        commit = commit
                    )
                }

                if (paragraphIndent != null) {
                    EnhancedStepperItem(
                        title = stringResource(R.string.paragraph_indent),
                        preference = paragraphIndent,
                        commit = commit
                    )
                }

                if (paragraphSpacing != null) {
                    EnhancedStepperItem(
                        title = stringResource(R.string.paragraph_spacing),
                        preference = paragraphSpacing,
                        commit = commit
                    )
                }

                if (wordSpacing != null) {
                    EnhancedStepperItem(
                        title = stringResource(R.string.word_spacing),
                        preference = wordSpacing,
                        commit = commit
                    )
                }

                if (letterSpacing != null) {
                    EnhancedStepperItem(
                        title = stringResource(R.string.letter_spacing),
                        preference = letterSpacing,
                        commit = commit
                    )
                }

                if (hyphens != null) {
                    EnhancedSwitchItem(
                        title = stringResource(R.string.hyphens),
                        preference = hyphens,
                        commit = commit
                    )
                }

                if (ligatures != null) {
                    EnhancedSwitchItem(
                        title = stringResource(R.string.ligatures),
                        preference = ligatures,
                        commit = commit
                    )
                }
            }
        } else if (publisherStyles != null) {
            PreferenceGroup(
                title = stringResource(R.string.styles),
                initiallyExpanded = false
            ) {
                SwitchItem(
                    title = stringResource(R.string.publisher_styles),
                    preference = publisherStyles,
                    commit = commit
                )
            }
        }
    }
}

@Composable
fun EnhancedFixedLayoutUserPreferences(
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
    // Если нужно, можно создать подобную группировку для FixedLayout
    // Но раз вы сказали "не надо", оставим заглушку
    FixedLayoutUserPreferences(
        commit = commit,
        language = language,
        readingProgression = readingProgression,
        backgroundColor = backgroundColor,
        scroll = scroll,
        scrollAxis = scrollAxis,
        fit = fit,
        spread = spread,
        offsetFirstPage = offsetFirstPage,
        pageSpacing = pageSpacing
    )
}

@Composable
fun EnhancedMediaUserPreferences(
    commit: () -> Unit,
    language: Preference<Language?>? = null,
    voice: EnumPreference<AndroidTtsEngine.Voice.Id?>? = null,
    speed: RangePreference<Double>? = null,
    pitch: RangePreference<Double>? = null,
) {
    // Для медиа тоже можно сделать группы
    MediaUserPreferences(
        commit = commit,
        language = language,
        voice = voice,
        speed = speed,
        pitch = pitch
    )
}