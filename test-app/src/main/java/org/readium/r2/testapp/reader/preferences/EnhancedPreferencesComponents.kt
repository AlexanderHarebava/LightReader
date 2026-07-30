package org.readium.r2.testapp.reader.preferences


import org.readium.r2.navigator.preferences.clear
// ... остальные импорты
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextAlign.Companion.Center
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.util.Locale
import org.readium.r2.navigator.preferences.EnumPreference
import org.readium.r2.navigator.preferences.Preference
import org.readium.r2.navigator.preferences.RangePreference
import org.readium.r2.navigator.preferences.clear
import org.readium.r2.navigator.preferences.toggle
import org.readium.r2.navigator.preferences.withSupportedValues
import org.readium.r2.shared.util.Language
import org.readium.r2.testapp.R
import org.readium.r2.testapp.utils.compose.ColorPicker

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_settings_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.weight(1f)
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") }
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PreferenceGroup(
    title: String,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp), // убрали horizontal = 16.dp
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            ListItem(
                headlineContent = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { expanded = !expanded }
            )

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp) // horizontal убран
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun PreviewSection(
    modifier: Modifier = Modifier,
    onPreviewClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(onClick = onPreviewClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.preview),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.preview_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.Visibility,
                contentDescription = stringResource(R.string.preview)
            )
        }
    }
}

@Composable
fun EnhancedResetButton(
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.reset_settings_title)) },
            text = { Text(stringResource(R.string.reset_settings_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset()
                        showConfirmDialog = false
                    }
                ) {
                    Text(stringResource(R.string.reset))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    FilledTonalButton(
        onClick = { showConfirmDialog = true },
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Icon(
            imageVector = Icons.Default.RestartAlt,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.reset_all))
    }
}

@Composable
fun PresetsCarousel(
    presets: List<Preset>,
    clear: () -> Unit,
    commit: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (presets.isEmpty()) return

    Column(
        modifier = Modifier.padding(horizontal = 16.dp) // единый отступ по краям
    ) {
        Text(
            text = stringResource(R.string.quick_settings),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(presets) { preset ->
                PresetChip(preset, clear, commit)
            }
        }
    }
}

// Вспомогательный компонент для анимированных элементов
@Composable
fun EnhancedItem(
    title: String,
    isActive: Boolean = true,
    currentValue: String? = null,
    onClick: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                currentValue?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                content()
                if (onClear != null) {
                    IconButton(
                        onClick = onClear,
                        enabled = isActive
                    ) {
                        Icon(
                            Icons.Default.Clear,
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
fun <T> EnhancedButtonGroupItem(
    title: String,
    preference: EnumPreference<T>,
    commit: () -> Unit,
    formatValue: @Composable (T) -> String,
    modifier: Modifier = Modifier
) {
    val selectedValue by remember(preference.value, preference.effectiveValue) {
        derivedStateOf { preference.value ?: preference.effectiveValue }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (preference.isEffective)
                MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Заголовок и кнопка сброса
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (preference.value != null) {
                    IconButton(
                        onClick = {
                            preference.clear()
                            commit()
                        },
                        enabled = preference.isEffective
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.reset),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Текущее значение
            Text(
                text = formatValue(selectedValue),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Кнопки в виде SegmentedButton
            Spacer(modifier = Modifier.height(12.dp))

            // Используем SingleChoiceSegmentedButtonRow
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                preference.supportedValues.forEach { option ->
                    val isSelected = option == selectedValue

                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = preference.supportedValues.indexOf(option),
                            count = preference.supportedValues.size
                        ),
                        onClick = {
                            if (option == preference.value) {
                                preference.clear()
                            } else {
                                preference.set(option)
                            }
                            commit()
                        },
                        selected = isSelected
                    ) {
                        Text(
                            text = formatValue(option),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}



@Composable
fun EnhancedLanguageItem(
    preference: Preference<Language?>,
    commit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val languages = remember {
        Locale.getAvailableLocales()
            .map { Language(it).removeRegion() }
            .distinct()
            .sortedBy { it.locale.displayName }
    }

    EnhancedMenuItem(
        title = stringResource(R.string.language),
        preference = preference.withSupportedValues(languages + null),
        commit = commit,
        formatValue = { it?.locale?.displayName ?: stringResource(R.string.unknown) },
        modifier = modifier
    )
}

@Composable
fun EnhancedColorItem(
    title: String,
    preference: Preference<ReadiumColor>,
    commit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPicking by remember { mutableStateOf(false) }
    val currentValue = preference.value ?: preference.effectiveValue

    EnhancedItem(
        title = title,
        currentValue = String.format("#%06X", 0xFFFFFF and currentValue.int),
        isActive = preference.isEffective,
        onClick = { isPicking = true },
        onClear = {
            preference.clear()
            commit()
        }.takeIf { preference.value != null },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    Color(currentValue.int),
                    RoundedCornerShape(8.dp)
                )
                .clickable { isPicking = true }
        )

        if (isPicking) {
            Dialog(onDismissRequest = { isPicking = false }) {
                Column(horizontalAlignment = Alignment.End) {
                    ColorPicker { color ->
                        isPicking = false
                        preference.set(ReadiumColor(color))
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
}








@Composable
fun <T> EnhancedMenuItem(
    title: String,
    preference: EnumPreference<T>,
    commit: () -> Unit,
    formatValue: @Composable (T) -> String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    EnhancedItem(
        title = title,
        currentValue = formatValue(preference.value ?: preference.effectiveValue),
        isActive = preference.isEffective,
        onClick = { expanded = true },
        onClear = {
            preference.clear()
            commit()
        }.takeIf { preference.value != null },
        modifier = modifier
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 200.dp)
        ) {
            preference.supportedValues.forEach { value ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = formatValue(value),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        preference.set(value)
                        commit()
                        expanded = false
                    },
                    trailingIcon = if (value == (preference.value ?: preference.effectiveValue)) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
fun <T : Comparable<T>> EnhancedStepperItem(
    title: String,
    preference: RangePreference<T>,
    commit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var animatedValue by remember(preference.value, preference.effectiveValue) {
        mutableStateOf(preference.value ?: preference.effectiveValue)
    }

    LaunchedEffect(preference.value, preference.effectiveValue) {
        animatedValue = preference.value ?: preference.effectiveValue
    }

    EnhancedItem(
        title = title,
        currentValue = preference.formatValue(animatedValue),
        isActive = preference.isEffective,
        onClear = {
            preference.clear()
            commit()
        }.takeIf { preference.value != null },
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = {
                    preference.decrement()
                    commit()
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = stringResource(R.string.decrease),
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                modifier = Modifier.width(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = preference.formatValue(animatedValue),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = {
                    preference.increment()
                    commit()
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.increase),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun EnhancedSwitchItem(
    title: String,
    preference: Preference<Boolean>,
    commit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isChecked = preference.value ?: preference.effectiveValue

    // Анимация цвета фона при переключении
    val backgroundColor by animateColorAsState(
        targetValue = if (isChecked)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(durationMillis = 300)
    )

    EnhancedItem(
        title = title,
        isActive = preference.isEffective,
        onClick = {
            preference.toggle()
            commit()
        },
        onClear = {
            preference.clear()
            commit()
        }.takeIf { preference.value != null },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .background(backgroundColor, RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            Switch(
                checked = isChecked,
                onCheckedChange = {
                    preference.set(it)
                    commit()
                }
            )
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