package io.github.impca201.simklr.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.impca201.simklr.data.NavTab
import io.github.impca201.simklr.data.model.CalendarEntry
import io.github.impca201.simklr.data.model.MediaType
import io.github.impca201.simklr.ui.LocalNavigator
import io.github.impca201.simklr.ui.components.EmptyState
import io.github.impca201.simklr.ui.components.Format
import io.github.impca201.simklr.ui.components.Poster
import io.github.impca201.simklr.ui.components.PosterBadge
import io.github.impca201.simklr.ui.components.rememberApp
import io.github.impca201.simklr.ui.library.SectionHeader
import io.github.impca201.simklr.ui.library.bottomPadding
import io.github.impca201.simklr.ui.main.MainTopBar
import io.github.impca201.simklr.ui.search.label
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Upcoming episodes and releases from SIMKL's calendars, by day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen() {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by app.library.state.collectAsState()
    var mine by rememberSaveable { mutableStateOf(true) }
    var hidden by rememberSaveable { mutableStateOf(setOf<String>()) }
    var refreshing by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(Unit) { app.library.refreshCalendar() }

    val zone = ZoneId.systemDefault()
    val days =
        remember(state.calendar, state.items, mine, hidden) {
            // Start of yesterday in the user's time zone (not UTC), so day groups below are never partial.
            val from = LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant()
            state.calendar
                .filterKeys { it.name !in hidden }
                .values.flatten()
                .filter { !it.date.isBefore(from) }
                .filter { !mine || state.item(it.type, it.simklId) != null }
                .sortedBy { it.date }
                .groupBy { it.date.atZone(zone).toLocalDate() }
        }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                MainTopBar(NavTab.Calendar.label, scrollBehavior)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = mine,
                            onClick = { mine = !mine },
                            label = { Text("My lists") },
                            leadingIcon = { Icon(Icons.Rounded.Bookmark, null, Modifier.size(18.dp)) },
                        )
                    }
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
            if (days.isEmpty()) {
                EmptyState(
                    Icons.Rounded.EventBusy,
                    if (state.calendar.isEmpty()) "Loading the calendar…" else "Nothing coming up",
                    if (mine) "Only titles in your lists are shown. Turn off “My lists” to see everything." else null,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    days.forEach { (day, entries) ->
                        item(key = "d-$day") { SectionHeader(Format.day(day), entries.size) }
                        items(entries, key = { "${it.type}:${it.simklId}:${it.episode?.label}:${it.date}" }) { entry ->
                            CalendarRow(entry, inList = state.item(entry.type, entry.simklId) != null) {
                                navigator.openDetail(entry.type, entry.simklId, entry.title, entry.poster)
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
    inList: Boolean,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Poster(entry.poster, entry.title, Modifier.width(48.dp), shape = RoundedCornerShape(8.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(entry.episode?.label, if (entry.type == MediaType.Movie) "Release" else null, Format.time(entry.date)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (inList) PosterBadge("In your list")
            PosterBadge(entry.type.label())
        }
    }
}
