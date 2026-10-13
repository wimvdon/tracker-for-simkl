package io.github.impca201.trackerforsimkl.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.impca201.trackerforsimkl.R
import io.github.impca201.trackerforsimkl.ui.theme.Motion
import io.github.impca201.trackerforsimkl.data.NavTab
import io.github.impca201.trackerforsimkl.data.model.CalendarEntry
import io.github.impca201.trackerforsimkl.data.model.MediaType
import io.github.impca201.trackerforsimkl.ui.LocalNavigator
import io.github.impca201.trackerforsimkl.ui.components.EmptyState
import io.github.impca201.trackerforsimkl.ui.components.Format
import io.github.impca201.trackerforsimkl.ui.components.Poster
import io.github.impca201.trackerforsimkl.ui.components.PosterBadge
import io.github.impca201.trackerforsimkl.ui.components.rememberApp
import io.github.impca201.trackerforsimkl.ui.library.SectionHeader
import io.github.impca201.trackerforsimkl.ui.library.bottomPadding
import io.github.impca201.trackerforsimkl.ui.main.MainTopBar
import io.github.impca201.trackerforsimkl.ui.components.label
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import io.github.impca201.trackerforsimkl.ui.components.PosterListSkeleton

/** Upcoming episodes and releases from SIMKL's calendars, by day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen() {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by app.library.state.collectAsState()
    var hidden by rememberSaveable { mutableStateOf(setOf<String>()) }
    var refreshing by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(Unit) { app.library.refreshCalendar() }

    val zone = ZoneId.systemDefault()
    val days =
        remember(state.calendar, state.items, hidden) {
            // Start of yesterday in the user's time zone (not UTC), so day groups below are never partial.
            val from = LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant()
            state.calendar
                .filterKeys { it.name !in hidden }
                .values.flatten()
                .filter { !it.date.isBefore(from) }
                // Only titles in the user's lists; SIMKL's full calendar is too long to be useful.
                .filter { state.item(it.type, it.simklId) != null }
                .sortedBy { it.date }
                .groupBy { it.date.atZone(zone).toLocalDate() }
        }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                MainTopBar(NavTab.Calendar.label(), scrollBehavior)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(MediaType.entries) { t ->
                        FilterChip(
                            selected = t.name !in hidden,
                            onClick = { hidden = if (t.name in hidden) hidden - t.name else hidden + t.name },
                            label = { Text(t.label()) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    app.library.refreshCalendar(force = true)
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            // Nothing from SIMKL yet: placeholder rows until the calendar arrives.
            AnimatedContent(
                targetState = days.takeIf { state.calendar.isNotEmpty() },
                contentKey = { if (it == null) 0 else if (it.isEmpty()) 1 else 2 },
                transitionSpec = { fadeIn(Motion.fadeSpec(210, delayMillis = 90)) togetherWith fadeOut(Motion.fadeSpec(90)) },
                label = "calendarContent",
                modifier = Modifier.fillMaxSize(),
            ) { shownDays ->
                if (shownDays == null) {
                    PosterListSkeleton(
                        posterWidth = 48.dp,
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding()),
                        lines = 2,
                    )
                } else if (shownDays.isEmpty()) {
                    EmptyState(
                        Icons.Rounded.EventBusy,
                        stringResource(R.string.screens_calendar_nothing),
                        stringResource(R.string.screens_calendar_mine_hint),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        shownDays.forEach { (day, entries) ->
                            item(key = "d-$day") { SectionHeader(Format.day(day), entries.size, Modifier.animateItem()) }
                            items(entries, key = { "${it.type}:${it.simklId}:${it.episode?.label}:${it.date}" }) { entry ->
                                CalendarRow(entry, Modifier.animateItem()) {
                                    navigator.openDetail(entry.type, entry.simklId, entry.title, entry.poster)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarRow(
    entry: CalendarEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Poster(entry.poster, entry.title, Modifier.width(48.dp), shape = RoundedCornerShape(8.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(entry.episode?.label, if (entry.type == MediaType.Movie) stringResource(R.string.screens_calendar_release) else null, Format.time(entry.date)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PosterBadge(entry.type.label())
        }
    }
}
