/*
 * Copyright 2025 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import java.text.SimpleDateFormat
import org.readium.r2.shared.accessibility.AccessibilityMetadataDisplayGuide
import org.readium.r2.shared.publication.*

import org.readium.r2.shared.util.Instant
import org.readium.r2.testapp.R
import org.readium.r2.testapp.utils.compose.ComposeBottomSheetDialogFragment

class PublicationMetadataDialogFragment : ComposeBottomSheetDialogFragment(
    isScrollable = false
) {
    private val viewModel: ReaderViewModel by activityViewModels()

    @Composable
    override fun Content() {
        PublicationMetadataScreen(
            publication = viewModel.publication,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        )
    }
}

@Composable
fun PublicationMetadataScreen(
    publication: Publication,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.metadata_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        item {
            PublicationMetadataCard(publication.metadata)
        }

        item {
            AccessibilityMetadataCard(
                guide = AccessibilityMetadataDisplayGuide(publication)
            )
        }
    }
}

@Composable
private fun PublicationMetadataCard(
    metadata: Metadata,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.publication_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            metadata.title?.let {
                MetadataRow(
                    icon = Icons.Default.Book,
                    label = stringResource(R.string.title_label),
                    value = it
                )
            }

            metadata.identifier?.let {
                MetadataRow(
                    icon = Icons.Default.Tag,
                    label = stringResource(R.string.identifier_label),
                    value = it
                )
            }

            MetadataContributorRow(
                icon = Icons.Default.Person,
                singleLabel = stringResource(R.string.author_label),
                pluralLabel = stringResource(R.string.authors_label),
                contributors = metadata.authors
            )

            MetadataContributorRow(
                icon = Icons.Default.Business,
                singleLabel = stringResource(R.string.publisher_label),
                pluralLabel = stringResource(R.string.publishers_label),
                contributors = metadata.publishers
            )

            metadata.published?.let { published ->
                val dateStr = SimpleDateFormat.getDateInstance()
                    .format(published.toJavaDate())
                MetadataRow(
                    icon = Icons.Default.DateRange,
                    label = stringResource(R.string.published_label),
                    value = dateStr
                )
            }
        }
    }
}

@Composable
private fun AccessibilityMetadataCard(
    guide: AccessibilityMetadataDisplayGuide,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var alwaysDisplayFields by remember { mutableStateOf(false) }
    var showDescriptiveStatements by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Accessible,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.accessibility_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingsSwitchCard(
                label = stringResource(R.string.show_fields_no_metadata),
                checked = alwaysDisplayFields,
                onCheckedChange = { alwaysDisplayFields = it }
            )

            SettingsSwitchCard(
                label = stringResource(R.string.show_descriptive_statements),
                checked = showDescriptiveStatements,
                onCheckedChange = { showDescriptiveStatements = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            guide.fields.forEach { field ->
                val shouldShow = alwaysDisplayFields || field.shouldDisplay
                if (field.statements.isNotEmpty() && shouldShow) {
                    AccessibilityFieldCard(
                        title = field.localizedTitle(context),
                        statements = field.statements,   // тип List<Statement> уже корректен
                        showDescriptive = showDescriptiveStatements
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchCard(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = checked,
                onCheckedChange = null, // handled by row click
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun AccessibilityFieldCard(
    title: String,
    statements: List<AccessibilityMetadataDisplayGuide.Statement>,
    showDescriptive: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            statements.forEach { statement ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        // Правильный способ: используем extension функцию localizedString
                        text = statement.localizedString(context, descriptive = showDescriptive),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MetadataContributorRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    singleLabel: String,
    pluralLabel: String,
    contributors: List<Contributor>,
    modifier: Modifier = Modifier
) {
    if (contributors.isEmpty()) return

    val label = if (contributors.size == 1) singleLabel else pluralLabel
    val value = contributors.joinToString { it.name }

    MetadataRow(
        icon = icon,
        label = label,
        value = value,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewPublicationMetadataScreen() {
    MaterialTheme {
        PublicationMetadataScreen(
            publication = Publication(
                manifest = Manifest(
                    metadata = Metadata(
                        localizedTitle = LocalizedString("Alice's Adventures in Wonderland"),
                        identifier = "urn:isbn:1503222683",
                        authors = listOf(
                            Contributor(name = "Lewis Carroll"),
                            Contributor(name = "Other Author")
                        ),
                        publishers = listOf(
                            Contributor(name = "Macmillan")
                        ),
                        published = Instant.now(),
                        accessibility = Accessibility(
                            conformsTo = setOf(Accessibility.Profile.EPUB_A11Y_10_WCAG_20_AA),
                            accessModes = setOf(Accessibility.AccessMode.TEXTUAL),
                            features = setOf(
                                Accessibility.Feature.DESCRIBED_MATH,
                                Accessibility.Feature.LARGE_PRINT
                            ),
                            hazards = setOf(
                                Accessibility.Hazard.FLASHING,
                                Accessibility.Hazard.NONE
                            )
                        )
                    )
                )
            )
        )
    }
}