package org.readium.r2.testapp.reader.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.r2.navigator.preferences.*
import org.readium.r2.shared.util.Language
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign
import org.readium.r2.testapp.shared.views.ButtonGroupItem
import org.readium.r2.testapp.shared.views.ColorItem
import org.readium.r2.testapp.shared.views.LanguageItem
import org.readium.r2.testapp.shared.views.StepperItem
import org.readium.r2.testapp.shared.views.SwitchItem

/*
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
    // Временно просто вызываем старую версию
    ReflowableUserPreferences(
        commit = commit,
        backgroundColor = backgroundColor,
        columnCount = columnCount,
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
        hyphens = hyphens,
        imageFilter = imageFilter,
        language = language,
        letterSpacing = letterSpacing,
        ligatures = ligatures,
        lineHeight = lineHeight,
        pageMargins = pageMargins,
        paragraphIndent = paragraphIndent,
        paragraphSpacing = paragraphSpacing,
        publisherStyles = publisherStyles,
        readingProgression = readingProgression,
        scroll = scroll,
        textAlign = textAlign,
        textColor = textColor,
        textNormalization = textNormalization,
        theme = theme,
        typeScale = typeScale,
        verticalText = verticalText,
        wordSpacing = wordSpacing
    )
}
*/

/*
@Composable
fun EnhancedMediaUserPreferences(
    commit: () -> Unit,
    language: Preference<Language?>? = null,
    voice: EnumPreference<AndroidTtsEngine.Voice.Id?>? = null,
    speed: RangePreference<Double>? = null,
    pitch: RangePreference<Double>? = null,
) {
    // Временно просто вызываем старую версию
    MediaUserPreferences(
        commit = commit,
        language = language,
        voice = voice,
        speed = speed,
        pitch = pitch
    )
}

*/
/*
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
    Column {
        // Группа 1: Язык и направление
        if (language != null || readingProgression != null) {
            PreferenceGroup(
                title = "Язык и направление",
                initiallyExpanded = false
            ) {
                if (language != null) {
                    LanguageItem(
                        preference = language,
                        commit = commit
                    )
                }

                if (readingProgression != null) {
                    ButtonGroupItem(
                        title = "Направление чтения",
                        preference = readingProgression,
                        commit = commit,
                        formatValue = { it.name }
                    )
                }
            }
        }

        // Группа 2: Внешний вид
        if (backgroundColor != null || scroll != null || scrollAxis != null) {
            PreferenceGroup(
                title = "Внешний вид",
                initiallyExpanded = false
            ) {
                if (backgroundColor != null) {
                    ColorItem(
                        title = "Цвет фона",
                        preference = backgroundColor,
                        commit = commit
                    )
                }

                if (scroll != null) {
                    SwitchItem(
                        title = "Прокрутка",
                        preference = scroll,
                        commit = commit
                    )
                }

                if (scrollAxis != null) {
                    ButtonGroupItem(
                        title = "Ось прокрутки",
                        preference = scrollAxis,
                        commit = commit
                    ) { value ->
                        when (value) {
                            Axis.HORIZONTAL -> "Горизонтальная"
                            Axis.VERTICAL -> "Вертикальная"
                        }
                    }
                }
            }
        }

        // Группа 3: Разметка страницы
        if (spread != null || fit != null || pageSpacing != null || offsetFirstPage != null) {
            PreferenceGroup(
                title = "Разметка страницы",
                initiallyExpanded = false
            ) {
                if (spread != null) {
                    ButtonGroupItem(
                        title = "Разворот",
                        preference = spread,
                        commit = commit
                    ) { value ->
                        when (value) {
                            Spread.AUTO -> "Авто"
                            Spread.NEVER -> "Не использовать"
                            Spread.ALWAYS -> "Всегда"
                        }
                    }

                    if (offsetFirstPage != null) {
                        SwitchItem(
                            title = "Смещение первой страницы",
                            preference = offsetFirstPage,
                            commit = commit
                        )
                    }
                }

                if (fit != null) {
                    ButtonGroupItem(
                        title = "Подгонка",
                        preference = fit,
                        commit = commit
                    ) { value ->
                        when (value) {
                            Fit.CONTAIN -> "Вместить"
                            Fit.COVER -> "Обрезать"
                            Fit.WIDTH -> "По ширине"
                            Fit.HEIGHT -> "По высоте"
                        }
                    }
                }

                if (pageSpacing != null) {
                    StepperItem(
                        title = "Межстраничный отступ",
                        preference = pageSpacing,
                        commit = commit
                    )
                }
            }
        }
    }
}


 */