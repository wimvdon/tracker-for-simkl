package io.github.impca201.simklr.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.github.impca201.simklr.data.AppSettings
import io.github.impca201.simklr.data.LibraryState
import io.github.impca201.simklr.data.NavTab
import io.github.impca201.simklr.data.model.AiringState
import io.github.impca201.simklr.data.model.LibraryItem
import io.github.impca201.simklr.data.model.ListStatus
import io.github.impca201.simklr.data.model.MediaType
import io.github.impca201.simklr.data.model.Shelf
import io.github.impca201.simklr.data.model.Shelves
import io.github.impca201.simklr.ui.LocalNavigator
import io.github.impca201.simklr.ui.components.EmptyState
import io.github.impca201.simklr.ui.components.Format
import io.github.impca201.simklr.ui.components.rememberApp
import io.github.impca201.simklr.ui.main.MainTopBar
import kotlinx.coroutines.launch
import java.time.Instant

private fun Shelf.emptyIcon(): ImageVector =
    when (this) {
        Shelf.UpNext -> Icons.Rounded.PlayCircle
        Shelf.Waiting -> Icons.Rounded.HourglassEmpty
        Shelf.PlanToWatch -> Icons.Rounded.Bookmark
        Shelf.OnHold -> Icons.Rounded.Pause
        Shelf.Completed -> Icons.Rounded.CheckCircle
        Shelf.Dropped -> Icons.Rounded.Block
    }

private fun Shelf.emptyText(): String =
    when (this) {
        Shelf.UpNext -> "Nothing to watch right now. New episodes of shows you're watching show up here."
        Shelf.Waiting -> "Shows you're caught up on, waiting for new episodes, show up here."
        Shelf.PlanToWatch -> "Add shows you want to watch from Discover or search."
        else -> "Nothing here yet."
    }

/** TV Shows and Anime: the user's shows split into shelves, "Up next" first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowsScreen(
    type: MediaType,
    settings: AppSettings,
) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by app.library.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var sort by rememberSaveable { mutableStateOf(SortOrder.Recent) }
    val shelves = Shelf.entries
    val pagerState = rememberPagerState { shelves.size }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val now = remember(state.items) { Instant.now() }
    val byShelf =
        remember(state.items, type, sort) {
            state.items.filter { it.type == type }.groupBy { Shelves.shelfOf(it, now) }.mapValues { (_, v) -> sort.sort(v) }
        }

    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it) } }

    fun open(item: LibraryItem) = navigator.openDetail(item.type, item.ids.simkl, item.title, item.poster)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomPadding())) },
        topBar = {
            Column {
                MainTopBar(
                    title = if (type == MediaType.Tv) NavTab.Tv.label else NavTab.Anime.label,
                    scrollBehavior = scrollBehavior,
                    searchType = type,
                ) { SortMenu(sort) { sort = it } }
                PrimaryScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 16.dp) {
                    shelves.forEachIndexed { index, shelf ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { TabLabel(shelf.label, byShelf[shelf]?.size ?: 0) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) { page ->
            val shelf = shelves[page]
            val items = byShelf[shelf].orEmpty()
            PullToRefreshBox(
                isRefreshing = state.syncing,
                onRefresh = { scope.launch { app.library.sync() } },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    items.isEmpty() && state.loaded && !state.syncing ->
                        EmptyState(shelf.emptyIcon(), "No ${shelf.label.lowercase()} ${if (type == MediaType.Anime) "anime" else "shows"}", shelf.emptyText())
                    shelf == Shelf.UpNext -> UpNextList(items, state, ::open) { message -> scope.launch { snackbar.showSnackbar(message) } }
                    shelf == Shelf.Waiting -> WaitingList(items, state, ::open) { message -> scope.launch { snackbar.showSnackbar(message) } }
                    else -> {
                        val sections = splitByAiring(items, state)
                        LibraryGrid(
                            sections = sections,
                            state = state,
                            onOpen = ::open,
                            showProgress = shelf != Shelf.Completed && shelf != Shelf.PlanToWatch,
                            showAiring = true,
                        )
                    }
                }
            }
        }
    }
}

/** "Still airing" / "Ended" sections, so it's clear which shows will get new episodes. */
private fun splitByAiring(
    items: List<LibraryItem>,
    state: LibraryState,
): List<GridSection> {
    val groups = items.groupBy { Shelves.airingOf(it, state.showInfo[it.key], state.nextAirDate(it)) }
    val nonEmpty = groups.filterValues { it.isNotEmpty() }
    if (nonEmpty.size <= 1) return listOf(GridSection(null, items))
    return listOf(
        GridSection("Still airing", groups[AiringState.Airing].orEmpty()),
        GridSection("Ended", groups[AiringState.Ended].orEmpty()),
        GridSection("Status unknown", groups[AiringState.Unknown].orEmpty()),
    )
}

@Composable
private fun UpNextList(
    items: List<LibraryItem>,
    state: LibraryState,
    onOpen: (LibraryItem) -> Unit,
    onError: (String) -> Unit,
) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(setOf<String>()) }
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottomPadding()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.key }) { item ->
            val next = item.nextToWatch
            val left = item.airedUnwatched
            ShowRow(
                item = item,
                headline = next?.let { listOfNotNull(it.ref.label, it.title).joinToString(" · ") } ?: "Continue watching",
                supporting =
                    listOfNotNull(
                        if (left > 0) "$left episode${if (left == 1) "" else "s"} left" else null,
                        Format.ago(item.lastWatchedAt)?.let { "watched $it" },
                    ).joinToString(" · ").ifEmpty { null },
                airing = Shelves.airingOf(item, state.showInfo[item.key], state.nextAirDate(item)),
                onClick = { onOpen(item) },
                modifier = Modifier.animateItem(),
            ) {
                if (item.key in busy) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else if (next != null) {
                    FilledTonalIconButton(onClick = {
                        busy = busy + item.key
                        scope.launch {
                            try {
                                app.library.markNextWatched(item)
                            } catch (e: Exception) {
                                onError("Couldn't mark ${next.ref.label} watched: ${e.message}")
                            }
                            busy = busy - item.key
                        }
                    }) { Icon(Icons.Rounded.Check, contentDescription = "Mark ${next.ref.label} watched") }
                }
            }
        }
    }
}

@Composable
private fun WaitingList(
    items: List<LibraryItem>,
    state: LibraryState,
    onOpen: (LibraryItem) -> Unit,
    onError: (String) -> Unit,
) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    val withDates = items.map { it to state.nextAirDate(it) }
    val comingSoon = withDates.filter { it.second != null }.sortedBy { it.second }
    val rest = withDates.filter { it.second == null }.map { it.first }
    val byAiring = rest.groupBy { Shelves.airingOf(it, state.showInfo[it.key], null) }
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomPadding()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (comingSoon.isNotEmpty()) {
            item(key = "h-soon") { SectionHeader("Coming soon", comingSoon.size) }
            items(comingSoon, key = { it.first.key }) { (item, date) ->
                val next = item.nextToWatch
                ShowRow(
                    item = item,
                    headline = listOfNotNull(next?.ref?.label, date?.let { Format.relative(it) }).joinToString(" · "),
                    supporting = next?.title ?: "${item.watchedEpisodes} episodes watched",
                    airing = AiringState.Airing,
                    onClick = { onOpen(item) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        val returning = byAiring[AiringState.Airing].orEmpty()
        if (returning.isNotEmpty()) {
            item(key = "h-returning") { SectionHeader("Still airing · no date yet", returning.size) }
            items(returning, key = { it.key }) { item ->
                ShowRow(item, "All caught up", item.lastWatched?.let { "Last watched $it" }, AiringState.Airing, { onOpen(item) }, Modifier.animateItem())
            }
        }
        val ended = byAiring[AiringState.Ended].orEmpty()
        if (ended.isNotEmpty()) {
            item(key = "h-ended") { SectionHeader("Ended · nothing left to watch", ended.size) }
            items(ended, key = { it.key }) { item ->
                ShowRow(item, "Show ended", "Move it to Completed?", AiringState.Ended, { onOpen(item) }, Modifier.animateItem()) {
                    IconButton(onClick = {
                        scope.launch {
                            try {
                                app.library.setStatus(item.type, item.ids, item.title, item.poster, item.year, ListStatus.Completed)
                            } catch (e: Exception) {
                                onError("Couldn't update ${item.title}: ${e.message}")
                            }
                        }
                    }) { Icon(Icons.Rounded.DoneAll, contentDescription = "Mark completed") }
                }
            }
        }
        val unknown = byAiring[AiringState.Unknown].orEmpty()
        if (unknown.isNotEmpty()) {
            item(key = "h-unknown") { SectionHeader("Caught up", unknown.size) }
            items(unknown, key = { it.key }) { item ->
                ShowRow(item, "All caught up", item.lastWatched?.let { "Last watched $it" }, AiringState.Unknown, { onOpen(item) }, Modifier.animateItem())
            }
        }
    }
}
