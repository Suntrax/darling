package com.blissless.anime.ui.screens

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blissless.anime.MainViewModel
import com.blissless.anime.data.models.AiringScheduleAnime
import com.blissless.anime.data.models.AnimeMedia
import com.blissless.anime.data.models.ExploreAnime
import com.blissless.anime.data.models.toDetailedAnimeData
import com.blissless.anime.ui.components.StatusColors
import com.blissless.anime.ui.components.StatusLabels
import com.blissless.anime.ui.components.rememberCinematicAnimation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

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
    preventAutoSync: Boolean = true,
    showStatusColors: Boolean = false,
    disableMaterialColors: Boolean = false,
    hideAdultContent: Boolean = false,
    preferEnglishTitles: Boolean = true,
    isLoggedIn: Boolean = false,
    onPlayEpisode: (AnimeMedia, Int, String?) -> Unit = { _, _, _ -> },
    onShowAnimeDialog: (ExploreAnime, ExploreAnime?) -> Unit = { _, _ -> },
    onClearAnimeStack: () -> Unit = {},
    onAnimeDialogOpen: (Boolean) -> Unit = {},
    onCharacterClick: (Int) -> Unit = {},
    onStaffClick: (Int) -> Unit = {},
    onViewAllCast: (Int, String) -> Unit = { _, _ -> },
    onViewAllStaff: (Int, String) -> Unit = { _, _ -> }
) {
    val airingList by viewModel.airingAnimeList.collectAsState()
    val scheduleByDay by viewModel.airingSchedule.collectAsState()
    val isLoading by viewModel.isLoadingSchedule.collectAsState()
    val localAnimeStatus by viewModel.localAnimeStatus.collectAsState()
    val apiError by viewModel.apiError.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()

    // Auto-fetch when airing list or schedule is empty and not loading
    LaunchedEffect(airingList, scheduleByDay, isLoading) {
        val scheduleHasData = scheduleByDay.values.any { it.isNotEmpty() }
        if ((airingList.isEmpty() || !scheduleHasData) && !isLoading) {
            viewModel.fetchAiringSchedule()
        }
    }

    // Filter adult content if setting is enabled
    val filteredAiringList = remember(airingList, hideAdultContent) {
        if (hideAdultContent) airingList.filter { !it.isAdult && !it.genres.any { g -> g.equals("Hentai", ignoreCase = true) } } else airingList
    }

    val scope = rememberCoroutineScope()

    // Get current day of week (0 = Sunday, 6 = Saturday)
    val calendar = Calendar.getInstance()
    var currentDayOfWeek by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_WEEK) - 1) }

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

    // Lock input during programmatic scroll to prevent spam
    var isInputLocked by remember { mutableStateOf(false) }

    // For By Day mode, track selected day
    var selectedDay by remember { mutableIntStateOf(currentDayOfWeek) }

    // Inline anime dialog state (like ExploreScreen)
    var selectedAnime by remember { mutableStateOf<ExploreAnime?>(null) }
    var showAnimeDialog by remember { mutableStateOf(false) }
    var firstOpenedAnime by remember { mutableStateOf<ExploreAnime?>(null) }

    val context = LocalContext.current

    // Get anime status and favorites from viewModel
    val aniListFavorites by viewModel.aniListFavorites.collectAsState()
    val currentlyWatching by viewModel.currentlyWatching.collectAsState()
    val planningToWatch by viewModel.planningToWatch.collectAsState()
    val completed by viewModel.completed.collectAsState()
    val onHold by viewModel.onHold.collectAsState()
    val dropped by viewModel.dropped.collectAsState()
    
    val animeStatusMap = remember(currentlyWatching, planningToWatch, completed, onHold, dropped, localAnimeStatus) {
        val map = mutableMapOf<Int, String>()
        currentlyWatching.forEach { map[it.id] = "CURRENT" }
        planningToWatch.forEach { map[it.id] = "PLANNING" }
        completed.forEach { map[it.id] = "COMPLETED" }
        onHold.forEach { map[it.id] = "PAUSED" }
        dropped.forEach { map[it.id] = "DROPPED" }
        localAnimeStatus.forEach { (id, entry) ->
            if (!map.containsKey(id)) {
                map[id] = entry.status
            }
        }
        map
    }
    
    val isFavoriteRateLimited by viewModel.isFavoriteRateLimited.collectAsState()
    
    // Force recomposition when lists change by tracking a version counter
    var listVersion by remember { mutableIntStateOf(0) }
    
    val favoriteIds = remember(listVersion, aniListFavorites) { aniListFavorites.map { it.id }.toSet() }
    
    // Update listVersion when lists change to trigger recomposition
    LaunchedEffect(currentlyWatching, planningToWatch, completed, onHold, dropped, aniListFavorites) {
        listVersion++
    }
    
    LaunchedEffect(isFavoriteRateLimited) {
        if (isFavoriteRateLimited) {
            Toast.makeText(context, "Please wait before toggling again", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Callback to clear anime stack (back navigation)
    val onClearAnimeStackHandler: () -> Unit = {
        val current = firstOpenedAnime
        if (selectedAnime != null && selectedAnime != firstOpenedAnime) {
            selectedAnime = firstOpenedAnime
        } else {
            showAnimeDialog = false
            selectedAnime = null
            firstOpenedAnime = null
        }
    }

    // Stop refreshing when loading completes
    LaunchedEffect(isLoading) {
        if (!isLoading && isRefreshing) {
            isRefreshing = false
        }
    }

    // Fetch schedule on first load (only if not preventing auto sync)
    LaunchedEffect(Unit) {
        if (!preventAutoSync) {
            viewModel.fetchAiringSchedule()
        }
    }

    // Reset pull-to-refresh when loading completes
    LaunchedEffect(isLoading) {
        if (!isLoading && isRefreshing) {
            isRefreshing = false
        }
    }

    // Auto-refresh every 5 minutes (respects cooldown internally, skip if preventing auto sync)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5 * 60000) // 5 minutes
            currentTime = System.currentTimeMillis() / 1000
            if (!preventAutoSync) {
                viewModel.fetchAiringSchedule()
            }
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
                currentDayOfWeek = newDay
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
    val filteredScheduleByDay = remember(scheduleByDay, startOfToday, endOfToday, currentTime, sevenDaysFromNow, currentDayOfWeek, hideAdultContent) {
        val result = mutableMapOf<Int, MutableList<AiringScheduleAnime>>()

        // Initialize all days
        for (i in 0..6) result[i] = mutableListOf()

        // Process all anime from the schedule
        scheduleByDay.values.flatten()
            .filter { !hideAdultContent || !it.isAdult }
            .forEach { anime ->
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
    // Update instantly during scroll, with debounce for settling
    val scrollJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val isScrolling by remember {
        derivedStateOf { listStateAllUpcoming.isScrollInProgress }
    }
    
    LaunchedEffect(listStateAllUpcoming.firstVisibleItemIndex, viewMode, currentDayOfWeek) {
        if (viewMode == 0 && !isProgrammaticScroll) {
            val firstVisibleIndex = listStateAllUpcoming.firstVisibleItemIndex

            if (firstVisibleIndex < allUpcomingTimelineItems.size) {
                var currentDay = currentDayOfWeek
                for (i in firstVisibleIndex downTo 0) {
                    val item = allUpcomingTimelineItems.getOrNull(i)
                    if (item is TimelineItem.DayHeader) {
                        currentDay = item.dayIndex
                        break
                    }
                }
                visibleDayByScroll = currentDay
            }
        }
    }

    // Reset programmatic scroll flag and unlock input (after animation completes)
    LaunchedEffect(isProgrammaticScroll) {
        if (isProgrammaticScroll) {
            kotlinx.coroutines.delay(550) // Slightly longer than animation duration
            isProgrammaticScroll = false
            isInputLocked = false
        }
    }

    // Automatically scroll to current day when auto sync is enabled
    LaunchedEffect(isVisible, preventAutoSync) {
        if (isVisible && !preventAutoSync) {
            val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
            currentDayOfWeek = today
            visibleDayByScroll = today
            selectedDay = today
            lastKnownDay = today
            // Scroll to current day in the list
            val targetIndex = if (viewMode == 0) nowIndicatorIndexAll else nowIndicatorIndexByDay
            val listState = if (viewMode == 0) listStateAllUpcoming else listStateByDay
            if (targetIndex >= 0) {
                listState.scrollToItem(targetIndex, scrollOffset = -100)
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
            .padding(top = 20.dp)
            .background(if (isOled) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        // Error/Offline Banner - always visible at top
        if (apiError != null || isOffline) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isOffline) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.errorContainer,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isOffline) Icons.Default.SignalWifiOff else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = if (isOffline) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isOffline) "No internet connection" else "AniList is currently unavailable",
                        color = if (isOffline) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = com.blissless.anime.R.mipmap.ic_launcher_round,
                contentDescription = "App",
                modifier = Modifier.size(32.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(12.dp))
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
                        scope.launch {
                            listStateAllUpcoming.scrollToItem(nowIndicatorIndexAll, scrollOffset = -100)
                        }
                    }
                },
                label = { 
                    Text(
                        "All Upcoming",
                        color = if (viewMode == 0) Color.Black else Color.Unspecified
                    ) 
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.Black,
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
                        scope.launch {
                            listStateByDay.scrollToItem(nowIndicatorIndexByDay, scrollOffset = -100)
                        }
                    }
                },
                label = { 
                    Text(
                        "By Day",
                        color = if (viewMode == 1) Color.Black else Color.Unspecified
                    ) 
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.Black,
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
                            visibleDayByScroll = dayIndex

                            // If clicking on Today, scroll to NOW indicator, else scroll to day header
                            val targetIndex = if (isToday && nowIndicatorIndexAll >= 0) {
                                nowIndicatorIndexAll
                            } else {
                                dayToItemIndexMapAll[dayIndex] ?: 0
                            }

                            scope.launch {
                                listStateAllUpcoming.scrollToItem(targetIndex, scrollOffset = if (isToday) -100 else 0)
                            }
                        },
                        label = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = DayAbbreviations[dayIndex],
                                    fontSize = 12.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else Color.Unspecified
                                )
                                if (dayAnimeCount > 0) {
                                    Text(
                                        text = "$dayAnimeCount",
                                        fontSize = 10.sp,
                                        color = if (isSelected) Color.Black else Color.Unspecified
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.Black,
                            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = Color.Unspecified
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
                            // Instant scroll for By Day mode
                            if (isToday && nowIndicatorIndexByDay >= 0) {
                                scope.launch {
                                    listStateByDay.scrollToItem(nowIndicatorIndexByDay, scrollOffset = -100)
                                }
                            } else if (wasDifferentDay) {
                                scope.launch {
                                    listStateByDay.scrollToItem(0)
                                }
                            }
                        },
                        label = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = DayAbbreviations[dayIndex],
                                    fontSize = 12.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else Color.Unspecified
                                )
                                if (dayAnimeCount > 0) {
                                    Text(
                                        text = "$dayAnimeCount",
                                        fontSize = 10.sp,
                                        color = if (isSelected) Color.Black else Color.Unspecified
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.Black,
                            containerColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = Color.Unspecified
                        ),
                        border = if (isToday && !isSelected) {
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else null
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content with pull-to-refresh
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                currentTime = System.currentTimeMillis() / 1000
                viewModel.fetchAiringSchedule(force = true)
            },
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading && airingList.isEmpty()) {
                ScheduleLoadingSkeleton(isOled)
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
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Swipe down to refresh",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    TimelineScheduleList(
                        timelineItems = timelineItems,
                        currentDayOfWeek = currentDayOfWeek,
                        currentTime = currentTime,
                        isOled = isOled,
                        showStatusColors = showStatusColors,
                        preferEnglishTitles = preferEnglishTitles,
                        animeStatusMap = animeStatusMap,
                        listState = currentListState,
                        screenKey = "schedule",
                        isVisible = isVisible,
                        onAnimeClick = { anime ->
                            val exploreAnime = ExploreAnime(
                                id = anime.id,
                                title = anime.title,
                                titleEnglish = anime.titleEnglish,
                                cover = anime.cover,
                                banner = null,
                                episodes = anime.episodes,
                                latestEpisode = anime.airingEpisode,
                                averageScore = anime.averageScore,
                                genres = anime.genres,
                                year = anime.year,
                                format = null,
                                malId = anime.malId
                            )
                            firstOpenedAnime = exploreAnime
                            selectedAnime = exploreAnime
                            showAnimeDialog = true
                            onAnimeDialogOpen(true)
                        }
                    )
                }
            }
        }
    }

    // Inline anime dialog
    if (showAnimeDialog && selectedAnime != null) {
        val currentStatus by remember(listVersion, selectedAnime!!.id) {
            derivedStateOf { animeStatusMap[selectedAnime!!.id] }
        }
        val isFavorite by remember(listVersion, favoriteIds, selectedAnime!!.id) {
            derivedStateOf { favoriteIds.contains(selectedAnime!!.id) }
        }
        
        DetailedAnimeScreen(
            anime = selectedAnime!!.toDetailedAnimeData(),
            viewModel = viewModel,
            isOled = isOled,
            currentStatus = currentStatus,
            isFavorite = isFavorite,
            isLoggedIn = isLoggedIn,
            onToggleFavorite = { _ -> viewModel.toggleAniListFavorite(selectedAnime!!.id) },
            onDismiss = {
                if (firstOpenedAnime != null && selectedAnime!!.id != firstOpenedAnime!!.id) {
                    selectedAnime = firstOpenedAnime
                } else {
                    showAnimeDialog = false
                    selectedAnime = null
                    firstOpenedAnime = null
                    onAnimeDialogOpen(false)
                }
            },
            onSwipeToClose = {
                showAnimeDialog = false
                selectedAnime = null
                firstOpenedAnime = null
                onAnimeDialogOpen(false)
            },
            onPlayEpisode = { episode, _ ->
                val animeMedia = AnimeMedia(
                    id = selectedAnime!!.id,
                    title = selectedAnime!!.title,
                    titleEnglish = selectedAnime!!.titleEnglish,
                    cover = selectedAnime!!.cover,
                    banner = selectedAnime!!.banner,
                    progress = 0,
                    totalEpisodes = selectedAnime!!.episodes,
                    latestEpisode = selectedAnime!!.latestEpisode,
                    status = "",
                    averageScore = selectedAnime!!.averageScore,
                    genres = selectedAnime!!.genres,
                    listStatus = "",
                    listEntryId = 0,
                    year = selectedAnime!!.year,
                    malId = selectedAnime!!.malId
                )
                onPlayEpisode(animeMedia, episode, null)
                showAnimeDialog = false
                selectedAnime = null
                firstOpenedAnime = null
                onAnimeDialogOpen(false)
            },
            onUpdateStatus = { status ->
                if (status != null) {
                    viewModel.addExploreAnimeToList(selectedAnime!!, status)
                }
            },
            onRemove = {
                viewModel.removeAnimeFromList(selectedAnime!!.id)
            },
            onRelationClick = { relation ->
                try {
                    scope.launch {
                        try {
                            delay(100)
                            val detailedData = viewModel.fetchDetailedAnimeData(relation.id)
                            if (detailedData != null) {
                                selectedAnime = ExploreAnime(
                                    id = relation.id,
                                    title = detailedData.title,
                                    titleEnglish = detailedData.titleEnglish,
                                    cover = detailedData.cover,
                                    banner = detailedData.banner,
                                    episodes = detailedData.episodes,
                                    latestEpisode = detailedData.latestEpisode,
                                    averageScore = detailedData.averageScore,
                                    genres = detailedData.genres,
                                    year = detailedData.year,
                                    format = detailedData.format
                                )
                            } else {
                                Toast.makeText(context, "Anime not found - ID: ${relation.id}", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            onCharacterClick = onCharacterClick,
            onStaffClick = onStaffClick,
            onViewAllCast = { onViewAllCast(selectedAnime!!.id, selectedAnime!!.title) },
            onViewAllStaff = { onViewAllStaff(selectedAnime!!.id, selectedAnime!!.title) }
        )
    }
}

@Composable
private fun TimelineScheduleList(
    timelineItems: List<TimelineItem>,
    currentDayOfWeek: Int,
    currentTime: Long,
    isOled: Boolean,
    showStatusColors: Boolean,
    preferEnglishTitles: Boolean,
    animeStatusMap: Map<Int, String>,
    listState: LazyListState,
    screenKey: String = "schedule",
    isVisible: Boolean = true,
    onAnimeClick: (AiringScheduleAnime) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val density = LocalDensity.current
    val translationYOffset = with(density) { (-30).dp.toPx() }
    
    val isScrolling by remember {
        derivedStateOf { listState.isScrollInProgress }
    }

    val cinematicProgress = rememberCinematicAnimation(screenKey, isVisible, true)

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
            val staggerDelay = minOf(index, 20) * 30f
            val staggerMs = staggerDelay / 1000f
            val rawProgress = ((cinematicProgress - staggerMs) / (1f - staggerMs))
            val easedProgress = easeOutCubic(rawProgress.coerceAtLeast(0f).coerceAtMost(1f))
            
            val introScale = 0.3f + easedProgress * 0.7f
            val introAlpha = easedProgress.coerceAtLeast(0f)
            val introTranslationY = translationYOffset * (1f - easedProgress)
            
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val itemInfo = visibleItems.find { it.index == index }
            
            val centerOffset = if (itemInfo != null) {
                val itemCenter = itemInfo.offset + itemInfo.size / 2
                val screenCenter = (layoutInfo.viewportSize.height / 2).toFloat()
                (itemCenter - screenCenter) / screenCenter
            } else {
                0f
            }
            
            val animatedOffset by animateFloatAsState(
                targetValue = if (isScrolling) centerOffset.coerceIn(-2f, 2f) else 0f,
                animationSpec = if (isScrolling) {
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                } else {
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                },
                label = "centerOffset"
            )
            
            val scrollScale = 1f - (animatedOffset.absoluteValue * 0.2f).coerceAtMost(0.2f)
            val scrollAlpha = 1f - (animatedOffset.absoluteValue * 0.4f).coerceAtMost(0.6f)
            val scrollParallax = animatedOffset * 25f
            
            val finalScale = scrollScale * introScale
            val finalAlpha = (scrollAlpha * introAlpha).coerceIn(0f, 1f)
            val finalTranslationY = scrollParallax + introTranslationY
            
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = finalScale
                        scaleY = finalScale
                        alpha = finalAlpha
                        translationY = finalTranslationY
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
                            showStatusColors = showStatusColors,
                            preferEnglishTitles = preferEnglishTitles,
                            animeStatus = animeStatusMap[item.data.id],
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

private fun easeOutCubic(t: Float): Float {
    val t1 = t - 1
    return t1 * t1 * t1 + 1
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
            .padding(start = 58.dp, end = 8.dp, top = 24.dp, bottom = 12.dp),
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
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = "TODAY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
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
    showStatusColors: Boolean,
    preferEnglishTitles: Boolean,
    animeStatus: String?,
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
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Time marker column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(50.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = timeString,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = lineColor.copy(alpha = contentAlpha)
            )

            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
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
                .weight(1f),
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
                        .width(70.dp)
                        .height(95.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .alpha(contentAlpha)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val displayTitle = if (preferEnglishTitles && !anime.titleEnglish.isNullOrEmpty()) anime.titleEnglish else anime.title
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isOled) Color.White.copy(alpha = contentAlpha)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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

                        if (animeStatus != null) {
                            val statusColor = StatusColors[animeStatus] ?: Color.Gray
                            val statusLabel = StatusLabels[animeStatus] ?: animeStatus
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = statusColor.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
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

@Composable
private fun ScheduleLoadingSkeleton(isOled: Boolean) {
    val skeletonColor = if (isOled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant
    val timeColor = if (isOled) Color(0xFF2A2A2A) else Color(0xFFC0C0C0)
    val dotColor = if (isOled) Color(0xFF2A2A2A) else Color(0xFFC0C0C0)
    val lineColor = if (isOled) Color(0xFF333333) else Color(0xFFE0E0E0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        items(6) { index ->
            // Day header skeleton
            if (index == 0 || index == 3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 58.dp, end = 8.dp, top = 24.dp, bottom = 12.dp)
                        .height(40.dp)
                        .background(skeletonColor, RoundedCornerShape(8.dp))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Time marker skeleton
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(50.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(14.dp)
                            .background(timeColor, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(dotColor, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .width(2.dp)
                            .height(90.dp)
                            .background(lineColor)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Card skeleton matching TimelineAnimeItem layout
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = skeletonColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cover image skeleton
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(95.dp)
                                .background(skeletonColor, RoundedCornerShape(8.dp))
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            // Title skeleton
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(14.dp)
                                    .background(timeColor, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .height(14.dp)
                                    .background(timeColor, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // Episode tag skeleton
                            Box(
                                modifier = Modifier
                                    .width(50.dp)
                                    .height(18.dp)
                                    .background(timeColor, RoundedCornerShape(4.dp))
                            )
                        }

                        // Arrow skeleton
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(timeColor, RoundedCornerShape(14.dp))
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
