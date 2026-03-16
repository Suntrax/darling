package com.blissless.anime.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blissless.anime.data.models.AiringScheduleAnime
import com.blissless.anime.MainViewModel
import com.blissless.anime.data.models.AnimeMedia
import com.blissless.anime.data.models.ExploreAnime
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

val DayNames = listOf(
    "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
)

// Day abbreviations: Su for Sunday, two letters for weekdays, Sa for Saturday
val DayAbbreviations = listOf(
    "Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"
)

// Helper function to get days starting from current day
private fun getDaysFromCurrentDay(currentDay: Int): List<Int> {
    return (0..6).map { offset -> (currentDay + offset) % 7 }
}

// Sealed class for timeline items
private sealed class TimelineItem {
    data class Anime(val data: AiringScheduleAnime, val isPast: Boolean) : TimelineItem()
    data class NowIndicator(val timeString: String) : TimelineItem()
    data class DayHeader(val dayIndex: Int, val dayName: String) : TimelineItem()
}

@SuppressLint("FrequentlyChangingValue")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: MainViewModel,
    isOled: Boolean = false,
    isVisible: Boolean = false,
    disableMaterialColors: Boolean = false,
    onPlayEpisode: (AnimeMedia, Int) -> Unit = { _, _ -> },
    onShowAnimeDialog: (ExploreAnime) -> Unit = {}
) {
    val airingList by viewModel.airingAnimeList.collectAsState()
    val scheduleByDay by viewModel.airingSchedule.collectAsState()
    val isLoading by viewModel.isLoadingSchedule.collectAsState()

    val scope = rememberCoroutineScope()

    // Get current day of week (0 = Sunday, 6 = Saturday)
    val calendar = Calendar.getInstance()
    val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1

    // Track current time for updates
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }

    // Track last known day for auto-switch
    var lastKnownDay by remember { mutableIntStateOf(currentDayOfWeek) }

    // Get days ordered from current day
    val orderedDays = remember(currentDayOfWeek) { getDaysFromCurrentDay(currentDayOfWeek) }

    var viewMode by remember { mutableIntStateOf(0) } // 0 = All Upcoming, 1 = By Day
    var isRefreshing by remember { mutableStateOf(false) }

    // LazyListState for scrolling - use separate states for each mode
    val listStateAllUpcoming = rememberLazyListState()
    val listStateByDay = rememberLazyListState()

    // Track the day the user is currently viewing (based on scroll position)
    var visibleDayByScroll by remember { mutableIntStateOf(currentDayOfWeek) }

    // Track if we're programmatically scrolling (to prevent scroll feedback loop)
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    // For By Day mode, track selected day
    var selectedDay by remember { mutableIntStateOf(currentDayOfWeek) }

    // Stop refreshing when loading completes
    LaunchedEffect(isLoading) {
        if (!isLoading && isRefreshing) {
            isRefreshing = false
        }
    }

    // Fetch schedule on first load
    LaunchedEffect(Unit) {
        viewModel.fetchAiringSchedule()
    }

    // Auto-refresh every 1 minute
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60000) // 1 minute
            currentTime = System.currentTimeMillis() / 1000
            viewModel.fetchAiringSchedule()
        }
    }

    // Update currentTime every second for accurate "now" display and day change detection
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            val newTime = System.currentTimeMillis() / 1000
            currentTime = newTime

            // Check if day changed
            val newCalendar = Calendar.getInstance()
            val newDay = newCalendar.get(Calendar.DAY_OF_WEEK) - 1
            if (newDay != lastKnownDay) {
                lastKnownDay = newDay
                // Auto-switch to new day in By Day mode
                selectedDay = newDay
                visibleDayByScroll = newDay
            }
        }
    }

    // Calculate time range: from START of today to 7 days from now
    val startOfToday = remember(currentTime) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = currentTime * 1000L
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis / 1000
    }

    val endOfToday = remember(startOfToday) {
        startOfToday + (24 * 60 * 60) // End of today
    }

    val sevenDaysFromNow = remember(currentTime) {
        currentTime + (7 * 24 * 60 * 60) // 7 days in seconds
    }

    // Create filtered schedule - show anime airing from start of today to 7 days from now
    val filteredScheduleByDay = remember(scheduleByDay, startOfToday, endOfToday, currentTime, sevenDaysFromNow, currentDayOfWeek) {
        val result = mutableMapOf<Int, MutableList<AiringScheduleAnime>>()

        // Initialize all days
        for (i in 0..6) result[i] = mutableListOf()

        // Process all anime from the schedule
        scheduleByDay.values.flatten().forEach { anime ->
            // Calculate which day of week this anime airs on
            val animeCalendar = Calendar.getInstance()
            animeCalendar.timeInMillis = anime.airingAt * 1000L
            val animeDayOfWeek = animeCalendar.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday

            // For today: only include anime airing within today's timeframe (not next week)
            if (animeDayOfWeek == currentDayOfWeek) {
                if (anime.airingAt in startOfToday..endOfToday) {
                    result[animeDayOfWeek]?.add(anime)
                }
            } else {
                // For other days: include anime airing from now to 7 days from now
                if (anime.airingAt in currentTime..sevenDaysFromNow) {
                    result[animeDayOfWeek]?.add(anime)
                }
            }
        }

        // Sort each day's anime by airing time
        result.forEach { (_, list) ->
            list.sortBy { it.airingAt }
        }

        result
    }

    // Build timeline items for All Upcoming mode (all days with headers)
    val allUpcomingTimelineItems = remember(filteredScheduleByDay, orderedDays, currentTime, currentDayOfWeek) {
        val items = mutableListOf<TimelineItem>()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val nowTimeString = timeFormat.format(Date(currentTime * 1000))

        orderedDays.forEach { dayIndex ->
            val dayAnime = filteredScheduleByDay[dayIndex] ?: emptyList()
            val isToday = dayIndex == currentDayOfWeek

            // Add day header
            items.add(TimelineItem.DayHeader(dayIndex, DayNames[dayIndex]))

            if (dayAnime.isEmpty()) {
                // No anime for this day
            } else if (isToday) {
                // For today: show NOW indicator and separate past/future anime
                val pastAnime = dayAnime.filter { it.airingAt <= currentTime }
                val futureAnime = dayAnime.filter { it.airingAt > currentTime }

                // Past anime (already aired today)
                pastAnime.forEach { anime ->
                    items.add(TimelineItem.Anime(anime, isPast = true))
                }

                // NOW indicator - show even if no future anime
                items.add(TimelineItem.NowIndicator(nowTimeString))

                // Future anime
                futureAnime.forEach { anime ->
                    items.add(TimelineItem.Anime(anime, isPast = false))
                }
            } else {
                // Other days: just show anime
                dayAnime.forEach { anime ->
                    items.add(TimelineItem.Anime(anime, isPast = false))
                }
            }
        }
        items
    }

    // Build timeline items for By Day mode (only selected day)
    val byDayTimelineItems = remember(filteredScheduleByDay, selectedDay, currentTime, currentDayOfWeek) {
        val items = mutableListOf<TimelineItem>()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val nowTimeString = timeFormat.format(Date(currentTime * 1000))

        val dayAnime = filteredScheduleByDay[selectedDay] ?: emptyList()
        val isToday = selectedDay == currentDayOfWeek

        if (dayAnime.isEmpty()) {
            // Empty list
        } else if (isToday) {
            // For today: show NOW indicator at current time
            val pastAnime = dayAnime.filter { it.airingAt <= currentTime }
            val futureAnime = dayAnime.filter { it.airingAt > currentTime }

            // Past anime
            pastAnime.forEach { anime ->
                items.add(TimelineItem.Anime(anime, isPast = true))
            }

            // NOW indicator
            items.add(TimelineItem.NowIndicator(nowTimeString))

            // Future anime
            futureAnime.forEach { anime ->
                items.add(TimelineItem.Anime(anime, isPast = false))
            }
        } else {
            // Other days
            dayAnime.forEach { anime ->
                items.add(TimelineItem.Anime(anime, isPast = false))
            }
        }
        items
    }

    // Map day index to first item index in timeline for scrolling (All Upcoming mode)
    val dayToItemIndexMapAll = remember(allUpcomingTimelineItems) {
        val map = mutableMapOf<Int, Int>()
        allUpcomingTimelineItems.forEachIndexed { index, item ->
            if (item is TimelineItem.DayHeader) {
                map[item.dayIndex] = index
            }
        }
        map
    }

    // Find NOW indicator index for today
    val nowIndicatorIndexAll = remember(allUpcomingTimelineItems) {
        allUpcomingTimelineItems.indexOfFirst { it is TimelineItem.NowIndicator }
    }

    val nowIndicatorIndexByDay = remember(byDayTimelineItems) {
        byDayTimelineItems.indexOfFirst { it is TimelineItem.NowIndicator }
    }

    // Track visible day based on scroll position (only for All Upcoming mode)
    LaunchedEffect(listStateAllUpcoming.firstVisibleItemIndex, listStateAllUpcoming.firstVisibleItemScrollOffset, viewMode) {
        if (viewMode == 0 && !isProgrammaticScroll) {
            val firstVisibleIndex = listStateAllUpcoming.firstVisibleItemIndex

            if (firstVisibleIndex < allUpcomingTimelineItems.size) {
                // Find the day header for the first visible item
                var currentDay = currentDayOfWeek
                for (i in 0..firstVisibleIndex) {
                    if (allUpcomingTimelineItems.getOrNull(i) is TimelineItem.DayHeader) {
                        currentDay = (allUpcomingTimelineItems[i] as TimelineItem.DayHeader).dayIndex
                    }
                }
                if (visibleDayByScroll != currentDay) {
                    visibleDayByScroll = currentDay
                }
            }
        }
    }

    // Reset programmatic scroll flag
    LaunchedEffect(isProgrammaticScroll) {
        if (isProgrammaticScroll) {
            kotlinx.coroutines.delay(300)
            isProgrammaticScroll = false
        }
    }

    // Automatically scroll to NOW indicator when screen becomes visible or data loads
    LaunchedEffect(isVisible, nowIndicatorIndexAll, nowIndicatorIndexByDay) {
        if (isVisible) {
            val targetIndex = if (viewMode == 0) nowIndicatorIndexAll else nowIndicatorIndexByDay
            val listState = if (viewMode == 0) listStateAllUpcoming else listStateByDay
            if (targetIndex >= 0) {
                isProgrammaticScroll = true
                listState.animateScrollToItem(targetIndex, scrollOffset = -100)
            }
        }
    }

    // Calculate stats for today (aired vs upcoming)
    val todayPastCount = filteredScheduleByDay[currentDayOfWeek]?.count { it.airingAt <= currentTime } ?: 0
    val todayFutureCount = filteredScheduleByDay[currentDayOfWeek]?.count { it.airingAt > currentTime } ?: 0

    // Total anime airing this week (all days)
    val totalThisWeek = remember(filteredScheduleByDay) {
        filteredScheduleByDay.values.sumOf { it.size }
    }

    // Total upcoming this week (all days)
    val totalUpcomingThisWeek = remember(filteredScheduleByDay, currentTime) {
        filteredScheduleByDay.values.sumOf { dayList ->
            dayList.count { it.airingAt > currentTime }
        }
    }

    // Stats for selected day in By Day mode
    val selectedDayPastCount = filteredScheduleByDay[selectedDay]?.count { it.airingAt <= currentTime } ?: 0
    val selectedDayFutureCount = filteredScheduleByDay[selectedDay]?.count { it.airingAt > currentTime } ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isOled) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Airing Schedule",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))

            // Stats in top right - different based on view mode
            if (viewMode == 0) {
                // All Upcoming: show aired today / total this week
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$todayPastCount aired",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "$totalUpcomingThisWeek upcoming",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // By Day: show stats for currently selected day
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedDayPastCount > 0) {
                        Text(
                            text = "$selectedDayPastCount aired",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        text = "$selectedDayFutureCount upcoming",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // View mode toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = viewMode == 0,
                onClick = {
                    viewMode = 0
                    // Scroll to NOW indicator when switching to All Upcoming mode
                    if (nowIndicatorIndexAll >= 0) {
                        isProgrammaticScroll = true
                        scope.launch {
                            listStateAllUpcoming.animateScrollToItem(nowIndicatorIndexAll, scrollOffset = -100)
                        }
                    }
                },
                label = { 
                    Text(
                        "All Upcoming",
                        color = if (viewMode == 0 && disableMaterialColors) Color.Black else Color.Unspecified
                    ) 
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = if (disableMaterialColors) Color.Black else Color.White,
                    containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant
                )
            )
            FilterChip(
                selected = viewMode == 1,
                onClick = {
                    viewMode = 1
                    selectedDay = currentDayOfWeek
                    // Scroll to NOW indicator when switching to By Day mode
                    if (nowIndicatorIndexByDay >= 0) {
                        isProgrammaticScroll = true
                        scope.launch {
                            listStateByDay.animateScrollToItem(nowIndicatorIndexByDay, scrollOffset = -100)
                        }
                    }
                },
                label = { 
                    Text(
                        "By Day",
                        color = if (viewMode == 1 && disableMaterialColors) Color.Black else Color.Unspecified
                    ) 
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = if (disableMaterialColors) Color.Black else Color.White,
                    containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        // Day selector (only in All Upcoming mode) - ordered from current day
        if (viewMode == 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                orderedDays.forEach { dayIndex ->
                    val isSelected = visibleDayByScroll == dayIndex
                    val isToday = currentDayOfWeek == dayIndex
                    val dayAnimeCount = filteredScheduleByDay[dayIndex]?.size ?: 0

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            isProgrammaticScroll = true
                            visibleDayByScroll = dayIndex

                            // If clicking on Today, scroll to NOW indicator, else scroll to day header
                            val targetIndex = if (isToday && nowIndicatorIndexAll >= 0) {
                                nowIndicatorIndexAll
                            } else {
                                dayToItemIndexMapAll[dayIndex] ?: 0
                            }

                            scope.launch {
                                listStateAllUpcoming.animateScrollToItem(targetIndex, scrollOffset = if (isToday) -100 else 0)
                            }
                        },
                        label = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = DayAbbreviations[dayIndex],
                                    fontSize = 12.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected && disableMaterialColors) Color.Black else Color.Unspecified
                                )
                                if (dayAnimeCount > 0) {
                                    Text(
                                        text = "$dayAnimeCount",
                                        fontSize = 10.sp,
                                        color = if (isSelected) {
                                            if (disableMaterialColors) Color.Black else Color.White
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = if (disableMaterialColors) Color.Black else Color.White,
                            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = if (isToday) MaterialTheme.colorScheme.primary
                            else if (isOled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = if (isToday && !isSelected) {
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else null
                    )
                }
            }
        }

        // Day selector for By Day mode
        if (viewMode == 1) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                orderedDays.forEach { dayIndex ->
                    val isSelected = selectedDay == dayIndex
                    val isToday = currentDayOfWeek == dayIndex
                    val dayAnime = filteredScheduleByDay[dayIndex] ?: emptyList()
                    val dayAnimeCount = dayAnime.size

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val wasDifferentDay = selectedDay != dayIndex
                            selectedDay = dayIndex
                            // Scroll to top for other days, NOW indicator for today
                            if (isToday && nowIndicatorIndexByDay >= 0) {
                                isProgrammaticScroll = true
                                scope.launch {
                                    listStateByDay.animateScrollToItem(nowIndicatorIndexByDay, scrollOffset = -100)
                                }
                            } else if (wasDifferentDay) {
                                // Scroll to top of list for other days when changing day
                                isProgrammaticScroll = true
                                scope.launch {
                                    listStateByDay.animateScrollToItem(0)
                                }
                            }
                        },
                        label = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = DayAbbreviations[dayIndex],
                                    fontSize = 12.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected && disableMaterialColors) Color.Black else Color.Unspecified
                                )
                                if (dayAnimeCount > 0) {
                                    Text(
                                        text = "$dayAnimeCount",
                                        fontSize = 10.sp,
                                        color = if (isSelected) {
                                            if (disableMaterialColors) Color.Black else Color.White
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = if (disableMaterialColors) Color.Black else Color.White,
                            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = if (isToday) MaterialTheme.colorScheme.primary
                            else if (isOled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = if (isToday && !isSelected) {
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else null
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content with pull-to-refresh
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                currentTime = System.currentTimeMillis() / 1000
                viewModel.fetchAiringSchedule()
            },
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading && airingList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val timelineItems = if (viewMode == 0) allUpcomingTimelineItems else byDayTimelineItems
                val currentListState = if (viewMode == 0) listStateAllUpcoming else listStateByDay

                if (timelineItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No airing anime",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (viewMode == 1) "for this day" else "found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isOled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    TimelineScheduleList(
                        timelineItems = timelineItems,
                        currentDayOfWeek = currentDayOfWeek,
                        currentTime = currentTime,
                        isOled = isOled,
                        listState = currentListState,
                        onAnimeClick = { anime ->
                            val exploreAnime = ExploreAnime(
                                id = anime.id,
                                title = anime.title,
                                cover = anime.cover,
                                banner = null,
                                episodes = anime.episodes,
                                latestEpisode = anime.airingEpisode,
                                averageScore = anime.averageScore,
                                genres = anime.genres,
                                year = anime.year
                            )
                            onShowAnimeDialog(exploreAnime)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineScheduleList(
    timelineItems: List<TimelineItem>,
    currentDayOfWeek: Int,
    currentTime: Long,
    isOled: Boolean,
    listState: LazyListState,
    onAnimeClick: (AiringScheduleAnime) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        state = listState
    ) {
        itemsIndexed(
            items = timelineItems,
            key = { _, item ->
                when (item) {
                    is TimelineItem.Anime -> "anime_${item.data.id}_${item.data.airingEpisode}"
                    is TimelineItem.NowIndicator -> "now_indicator_${item.timeString}"
                    is TimelineItem.DayHeader -> "day_header_${item.dayIndex}"
                }
            }
        ) { index, item ->
            val slideInAnim = remember { Animatable(0.5f) }
            val alphaAnim = remember { Animatable(0.5f) }

            LaunchedEffect(item) {
                launch {
                    slideInAnim.animateTo(1f, animationSpec = tween(150, easing = FastOutSlowInEasing))
                }
                launch {
                    alphaAnim.animateTo(1f, animationSpec = tween(150))
                }
            }

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = (1f - slideInAnim.value) * 30f
                        this.alpha = alphaAnim.value
                    }
            ) {
                when (item) {
                    is TimelineItem.DayHeader -> {
                        DayHeaderItem(
                            dayName = item.dayName,
                            dayIndex = item.dayIndex,
                            isToday = item.dayIndex == currentDayOfWeek,
                            isOled = isOled
                        )
                    }
                    is TimelineItem.Anime -> {
                        TimelineAnimeItem(
                            timeString = timeFormat.format(Date(item.data.airingAt * 1000L)),
                            anime = item.data,
                            isOled = isOled,
                            isPast = item.isPast,
                            onClick = { onAnimeClick(item.data) }
                        )
                    }
                    is TimelineItem.NowIndicator -> {
                        CurrentTimeIndicator(
                            timeString = item.timeString,
                            isOled = isOled
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun DayHeaderItem(
    dayName: String,
    dayIndex: Int,
    isToday: Boolean,
    isOled: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 58.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isToday)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else
                (if (isOled) Color(0xFF2A2A2A) else MaterialTheme.colorScheme.surfaceVariant)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dayName.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isToday)
                    MaterialTheme.colorScheme.primary
                else
                    (if (isOled) Color.White else MaterialTheme.colorScheme.onSurface)
            )
            if (isToday) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "TODAY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineAnimeItem(
    timeString: String,
    anime: AiringScheduleAnime,
    isOled: Boolean,
    isPast: Boolean,
    onClick: () -> Unit
) {
    val lineColor = if (isPast) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.primary
    }

    val contentAlpha = if (isPast) 0.6f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Time marker column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(50.dp)
        ) {
            Text(
                text = timeString,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = lineColor.copy(alpha = contentAlpha)
            )

            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .size(12.dp)
                    .background(lineColor, CircleShape)
            )

            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(90.dp)
                    .background(lineColor.copy(alpha = 0.3f))
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant
            ),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = anime.cover,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(50.dp)
                        .height(70.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .alpha(contentAlpha)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = anime.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isOled) Color.White.copy(alpha = contentAlpha)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "Ep ${anime.airingEpisode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (!isPast && anime.timeUntilAiring != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val timeUntilText = remember(anime.timeUntilAiring) {
                            val seconds = anime.timeUntilAiring
                            val hours = seconds / 3600
                            val minutes = (seconds % 3600) / 60
                            when {
                                hours > 24 -> {
                                    val days = hours / 24
                                    val remainingHours = hours % 24
                                    "in ${days}d ${remainingHours}h"
                                }
                                hours > 0 -> "in ${hours}h ${minutes}m"
                                else -> "in ${minutes}m"
                            }
                        }
                        Text(
                            text = timeUntilText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (isPast) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Already aired",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View details",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun CurrentTimeIndicator(
    timeString: String,
    isOled: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(50.dp)
        ) {
            Text(
                text = timeString,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .size(12.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .background(MaterialTheme.colorScheme.secondary, CircleShape)
            )

            Column(
                modifier = Modifier.width(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(6) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
        ) {
            Text(
                text = "NOW",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
