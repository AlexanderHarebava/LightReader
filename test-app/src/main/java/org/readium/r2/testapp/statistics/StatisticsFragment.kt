package org.readium.r2.testapp.statistics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.MediaType.Companion.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.File
import org.readium.r2.testapp.utils.compose.AppTheme
import java.util.concurrent.TimeUnit
import org.readium.r2.testapp.R
import org.readium.r2.testapp.bookshelf.BookshelfTheme
import org.readium.r2.testapp.data.model.Book
import org.readium.r2.testapp.utils.CoverGenerator

@Composable
fun formatReadingTime(millis: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    if (totalMinutes == 0L) return stringResource(id = R.string.stat_zero_min)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) stringResource(id = R.string.stat_hours_mins, hours, minutes)
    else stringResource(id = R.string.stat_mins, minutes)
}

class StatisticsFragment : Fragment() {
    private val viewModel: StatisticsViewModel by viewModels()

// StatisticsFragment.kt

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                BookshelfTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        StatisticsScreen(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel) {
    val streak by viewModel.currentStreak.collectAsState()
    val recentBooks by viewModel.recentBooksStats.collectAsState()
    val chartData by viewModel.chartData.collectAsState()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                text = stringResource(id = R.string.statistics),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }



        // Последняя читаемая книга выделена отдельно
        if (recentBooks.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(id = R.string.stat_currently_reading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                LastOpenedBookCard(recentBooks.first())
            }
        }

        // Секция графиков с кастомным переключателем вкладок
        item {
            ChartSection(
                chartData = chartData,
                selectedTimeframe = selectedTimeframe,
                onTimeframeSelected = { viewModel.setTimeframe(it) }
            )
        }

        // Список ранее прочитанных книг
        if (recentBooks.size > 1) {
            item {
                Text(
                    text = stringResource(id = R.string.stat_previously_read),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(recentBooks.drop(1)) { bookStat ->
                RecentBookItem(bookStat)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun StreakCard(streak: Int) {
    val orangeGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFFF5722), Color(0xFFFF9800))
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape = RoundedCornerShape(24.dp), clip = false),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .background(orangeGradient)
                .padding(24.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(
                    text = stringResource(id = R.string.stat_reading_streak).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = stringResource(id = R.string.stat_days_in_a_row, streak),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun StatBookCover(
    book: Book,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val coverFile = remember(book.cover) {
        book.cover
            .takeIf { it.isNotBlank() }
            ?.let { File(it) }
    }

    val hasRealCover = remember(coverFile) {
        coverFile?.exists() == true && coverFile.length() > 100
    }

    val placeholderPainter = painterResource(R.drawable.cover)

    val generatedCover = remember(book.title, hasRealCover) {
        if (!hasRealCover) {
            CoverGenerator.generate(
                title = book.title.orEmpty().ifBlank {
                    context.getString(R.string.txt_untitled)
                },
                widthPx = 180,
                heightPx = 260
            )
        } else {
            null
        }
    }

    Box(modifier = modifier) {
        if (hasRealCover) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverFile)
                    .crossfade(true)
                    .build(),
                contentDescription = book.title ?: stringResource(R.string.cover_image),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
                placeholder = placeholderPainter,
                error = placeholderPainter,
                fallback = placeholderPainter
            )
        } else if (generatedCover != null) {
            Image(
                bitmap = generatedCover.asImageBitmap(),
                contentDescription = book.title ?: stringResource(R.string.cover_image),
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun LastOpenedBookCard(bookStat: BookReadStat) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Элегантная имитация обложки книги
            StatBookCover(
                book = bookStat.book,
                modifier = Modifier
                    .size(55.dp, 80.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(18.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookStat.book.title ?: stringResource(id = R.string.stat_unknown_book),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.stat_time_in_book, formatReadingTime(bookStat.totalTimeMillis)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun ChartSection(
    chartData: List<ChartData>,
    selectedTimeframe: ChartTimeframe,
    onTimeframeSelected: (ChartTimeframe) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Кастомный переключатель вкладок (Pill/Capsule Selector)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ChartTimeframe.values().forEach { timeframe ->
                    val isSelected = selectedTimeframe == timeframe
                    val labelRes = when (timeframe) {
                        ChartTimeframe.WEEK -> R.string.stat_week
                        ChartTimeframe.MONTH -> R.string.stat_month
                        ChartTimeframe.YEAR -> R.string.stat_year
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onTimeframeSelected(timeframe) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (selectedTimeframe == ChartTimeframe.MONTH) {
                MonthHeatmap(chartData)
            } else {
                BarChart(chartData)
            }
        }
    }
}
@Composable
fun BarChart(chartData: List<ChartData>) {
    val maxMillis = chartData.maxOfOrNull { it.valueMillis }?.takeIf { it > 0 } ?: 1L
    val barWidth = if (chartData.size > 7) 14.dp else 24.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        chartData.forEach { data ->
            val targetWeight = (data.valueMillis.toFloat() / maxMillis)
                .coerceIn(0.04f, 1f)

            val animatedWeight by animateFloatAsState(
                targetValue = targetWeight,
                animationSpec = tween(durationMillis = 500),
                label = "bar_height"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Фиксированная зона под подпись времени,
                // чтобы график не выталкивал нижние подписи
                Box(
                    modifier = Modifier.height(32.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (data.valueMillis > 0) {
                        Text(
                            text = formatReadingTime(data.valueMillis)
                                .replaceFirst(" ", "\n"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                lineHeight = 10.sp
                            ),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Область для столбца с фиксированным весом
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .width(barWidth)
                            .fillMaxHeight(animatedWeight)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(
                                if (data.isToday) {
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.secondaryContainer,
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Нижняя подпись дня/месяца теперь всегда помещается
                Text(
                    text = data.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (data.isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (data.isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MonthHeatmap(chartData: List<ChartData>) {
    val maxMillis = chartData.maxOfOrNull { it.valueMillis }?.takeIf { it > 0 } ?: 1L
    val chunks = chartData.chunked(7)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chunks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                week.forEach { dayData ->
                    val intensity = (dayData.valueMillis.toFloat() / maxMillis).coerceIn(0f, 1f)
                    val hasRead = dayData.valueMillis > 0

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            // Скругленные квадраты (а-ля GitHub Contribution Graph) выглядят современнее строгих кругов
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (hasRead) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f + (0.85f * intensity))
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .border(
                                width = if (dayData.isToday) 2.dp else 0.dp,
                                color = if (dayData.isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayData.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (dayData.isToday) FontWeight.Bold else FontWeight.Medium,
                            color = if (hasRead) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                repeat(7 - week.size) {
                    Spacer(modifier = Modifier.size(34.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(8.dp))

        // Красивое подведение итогов месяца
        val totalTimeMonth = chartData.sumOf { it.valueMillis }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.stat_total_month, formatReadingTime(totalTimeMonth)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun RecentBookItem(bookStat: BookReadStat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatBookCover(
            book = bookStat.book,
            modifier = Modifier
                .size(40.dp, 56.dp)
                .clip(RoundedCornerShape(6.dp))
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            bookStat.book.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(id = R.string.stat_time, formatReadingTime(bookStat.totalTimeMillis)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}