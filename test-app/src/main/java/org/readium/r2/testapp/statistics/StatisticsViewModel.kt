package org.readium.r2.testapp.statistics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.readium.r2.testapp.data.model.Book
import org.readium.r2.testapp.data.model.ReadingSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ChartTimeframe { WEEK, MONTH, YEAR }

data class ChartData(val label: String, val valueMillis: Long, val isToday: Boolean = false)

data class BookReadStat(val book: Book, val totalTimeMillis: Long, val lastReadTime: Long)

class StatisticsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as org.readium.r2.testapp.Application

    private val _selectedTimeframe = MutableStateFlow(ChartTimeframe.WEEK)
    val selectedTimeframe: StateFlow<ChartTimeframe> = _selectedTimeframe

    fun setTimeframe(timeframe: ChartTimeframe) {
        _selectedTimeframe.value = timeframe
    }

    val allSessions = app.bookRepository.getAllReadingSessions()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allBooks = app.bookRepository.books()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val chartData: StateFlow<List<ChartData>> = _selectedTimeframe.flatMapLatest { timeframe ->
        allSessions.map { sessions ->
            when (timeframe) {
                ChartTimeframe.WEEK -> calculateWeekStats(sessions)
                ChartTimeframe.MONTH -> calculateMonthStats(sessions)
                ChartTimeframe.YEAR -> calculateYearStats(sessions)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun calculateWeekStats(sessions: List<ReadingSession>): List<ChartData> {
        val todayStart = startOfDay(System.currentTimeMillis())

        return (0..6).map { index ->
            val dayStart = addDays(todayStart, index - 6)
            val dayEnd = addDays(dayStart, 1)

            val time = sessions
                .filter { it.startTime in dayStart until dayEnd }
                .sumOf { it.durationMillis }
                .coerceAtLeast(0L)

            val labelCal = Calendar.getInstance().apply {
                timeInMillis = dayStart
            }

            val label = SimpleDateFormat("EE", Locale.getDefault()).format(labelCal.time)

            ChartData(
                label = label,
                valueMillis = time,
                isToday = index == 6
            )
        }
    }




/*

    private fun ReadingSession.safeEndTime(): Long {
        return if (endTime > startTime) {
            endTime
        } else {
            startTime + durationMillis
        }
    }

    private fun readingIntervals(sessions: List<ReadingSession>): List<Pair<Long, Long>> {
        return sessions
            .map { session ->
                session.startTime to session.safeEndTime()
            }
            .filter { (start, end) -> end > start }
            .sortedBy { it.first }
    }

    private fun mergedDuration(intervals: List<Pair<Long, Long>>): Long {
        var total = 0L

        var currentStart: Long? = null
        var currentEnd: Long? = null

        for ((start, end) in intervals) {
            val cs = currentStart
            val ce = currentEnd

            if (cs == null || ce == null) {
                currentStart = start
                currentEnd = end
            } else if (start <= ce) {
                currentEnd = maxOf(ce, end)
            } else {
                total += ce - cs
                currentStart = start
                currentEnd = end
            }
        }

        val cs = currentStart
        val ce = currentEnd

        if (cs != null && ce != null) {
            total += ce - cs
        }

        return total
    }

    private fun durationInRange(
        sessions: List<ReadingSession>,
        rangeStart: Long,
        rangeEnd: Long
    ): Long {
        val intervals = readingIntervals(sessions).mapNotNull { (start, end) ->
            val clippedStart = maxOf(start, rangeStart)
            val clippedEnd = minOf(end, rangeEnd)

            if (clippedEnd > clippedStart) {
                clippedStart to clippedEnd
            } else {
                null
            }
        }

        return mergedDuration(intervals)
    }

*/






    private fun calculateMonthStats(sessions: List<ReadingSession>): List<ChartData> {
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        return (1..daysInMonth).map { day ->
            val startCal = Calendar.getInstance().apply {
                set(currentYear, currentMonth, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endCal = startCal.clone() as Calendar
            endCal.add(Calendar.DAY_OF_MONTH, 1)

            val time = sessions.filter { it.startTime in startCal.timeInMillis until endCal.timeInMillis }.sumOf { it.durationMillis }
            ChartData(day.toString(), time, isToday = (day == currentDay))
        }
    }

    private fun startOfDay(timeMillis: Long): Long {
        return Calendar.getInstance().apply {
            this.timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun addDays(timeMillis: Long, days: Int): Long {
        return Calendar.getInstance().apply {
            this.timeInMillis = timeMillis
            add(Calendar.DAY_OF_YEAR, days)
        }.timeInMillis
    }

    private fun calculateYearStats(sessions: List<ReadingSession>): List<ChartData> {
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH)

        return (0..11).map { month ->
            val startCal = Calendar.getInstance().apply {
                set(currentYear, month, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endCal = startCal.clone() as Calendar
            endCal.add(Calendar.MONTH, 1)

            val time = sessions.filter { it.startTime in startCal.timeInMillis until endCal.timeInMillis }.sumOf { it.durationMillis }
            val label = SimpleDateFormat("MMM", Locale.getDefault()).format(startCal.time)
            ChartData(label, time, isToday = (month == currentMonth))
        }
    }

    val currentStreak: StateFlow<Int> = allSessions.map { sessions ->
        if (sessions.isEmpty()) return@map 0

        val readDays = sessions
            .map { startOfDay(it.startTime) }
            .toSet()
            .sortedDescending()

        var currentDay = startOfDay(System.currentTimeMillis())

        // Если сегодня ещё не читали, разрешаем streak начаться со вчера.
        if (!readDays.contains(currentDay)) {
            currentDay = addDays(currentDay, -1)
            if (!readDays.contains(currentDay)) return@map 0
        }

        var streak = 0

        for (day in readDays) {
            // Пропускаем будущие даты, если они вдруг появились из-за сбоя времени.
            if (day > currentDay) continue

            if (day == currentDay) {
                streak++
                currentDay = addDays(currentDay, -1)
            } else {
                break
            }
        }

        streak
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val recentBooksStats: StateFlow<List<BookReadStat>> = combine(allSessions, allBooks) { sessions, books ->
        val bookMap = books.associateBy { it.id }
        sessions.groupBy { it.bookId }
            .mapNotNull { (bookId, bookSessions) ->
                val book = bookMap[bookId] ?: return@mapNotNull null
                val totalTime = bookSessions.sumOf { it.durationMillis }
                val lastRead = bookSessions.maxOf { it.startTime }
                BookReadStat(book, totalTime, lastRead)
            }
            .sortedByDescending { it.lastReadTime }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}