package io.github.impca201.trackerforsimkl.ui.library

import io.github.impca201.trackerforsimkl.ui.components.label
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.impca201.trackerforsimkl.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.github.impca201.trackerforsimkl.data.AppSettings
import io.github.impca201.trackerforsimkl.data.LibraryState
import io.github.impca201.trackerforsimkl.data.NavTab
import io.github.impca201.trackerforsimkl.data.model.LibraryItem
import io.github.impca201.trackerforsimkl.data.model.MediaType
import io.github.impca201.trackerforsimkl.data.model.Playback
import io.github.impca201.trackerforsimkl.data.model.Shelf
import io.github.impca201.trackerforsimkl.data.model.Shelves
import io.github.impca201.trackerforsimkl.ui.LocalNavigator
import io.github.impca201.trackerforsimkl.ui.components.ConfirmWatchedDialog
import io.github.impca201.trackerforsimkl.ui.components.EmptyState
import io.github.impca201.trackerforsimkl.ui.components.Format
import io.github.impca201.trackerforsimkl.ui.components.rememberApp
import io.github.impca201.trackerforsimkl.ui.main.MainTopBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.roundToInt
import io.github.impca201.trackerforsimkl.ui.components.PosterGridSkeleton
import io.github.impca201.trackerforsimkl.ui.components.PosterListSkeleton

private fun Shelf.emptyIcon(): ImageVector =
    when (this) {
        Shelf.UpNext -> Icons.Rounded.PlayCircle
        Shelf.Watched -> Icons.Rounded.CheckCircle
        Shelf.PlanToWatch -> Icons.Rounded.Bookmark
        Shelf.OnHold -> Icons.Rounded.Pause
        Shelf.Dropped -> Icons.Rounded.Block
    }

@StringRes
private fun Shelf.emptyText(): Int =
    when (this) {
        Shelf.UpNext -> R.string.lib_empty_up_next_text
        Shelf.Watched -> R.string.lib_empty_watched_text
        Shelf.PlanToWatch -> R.string.lib_empty_plan_text
        else -> R.string.lib_empty_other_text
    }

@StringRes
private fun Shelf.emptyTitle(type: MediaType): Int {
    val anime = type == MediaType.Anime
    return when (this) {
        Shelf.UpNext -> if (anime) R.string.lib_empty_up_next_anime else R.string.lib_empty_up_next_shows
        Shelf.Watched -> if (anime) R.string.lib_empty_watched_anime else R.string.lib_empty_watched_shows
        Shelf.PlanToWatch -> if (anime) R.string.lib_empty_plan_anime else R.string.lib_empty_plan_shows
        Shelf.OnHold -> if (anime) R.string.lib_empty_on_hold_anime else R.string.lib_empty_on_hold_shows
        Shelf.Dropped -> if (anime) R.string.lib_empty_dropped_anime else R.string.lib_empty_dropped_shows
    }
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
    val shelves = Shelf.entries
    val options = rememberTabOptions(shelves.map { if (it == Shelf.PlanToWatch) SortOrder.Added else SortOrder.Recent })
    val pagerState = rememberPagerState { shelves.size }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val fromLatest = settings.continueFromLatest

    // Each tab has its own sort and filter.
    val byShelf =
        remember(state, type, fromLatest, options.version) {
            val now = Instant.now()
            state.items.filter { it.type == type }
                .groupBy { Shelves.shelfOf(it, now, fromLatest, state.latestNext[it.key]) }
                .mapValues { (shelf, v) ->
                    val tab = shelves.indexOf(shelf)
                    val sorted = options.sort(tab).sort(options.filter(tab).apply(v))
                    // Episodes in progress come first in Up next.
                    if (shelf == Shelf.UpNext) sorted.sortedByDescending { state.playbackFor(it.type, it.ids.simkl) != null } else sorted
                }
        }

    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it) } }
    LaunchedEffect(fromLatest) { if (fromLatest) app.library.refreshLatestNext() }

    fun open(item: LibraryItem) = navigator.openDetail(item.type, item.ids.simkl, item.title, item.poster)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomPadding())) },
        topBar = {
            Column {
                MainTopBar(
                    title = if (type == MediaType.Tv) NavTab.Tv.label() else NavTab.Anime.label(),
                    scrollBehavior = scrollBehavior,
                ) {
                    val page = pagerState.currentPage
                    if (type == MediaType.Anime) AnimeTypeButton(options.filter(page)) { options.setFilter(page, it) }
                    SortButton(options.sort(page), SortOrder.choices(planToWatch = shelves[page] == Shelf.PlanToWatch)) { options.setSort(page, it) }
                }
                PrimaryScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 16.dp) {
                    shelves.forEachIndexed { index, shelf ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { TabLabel(shelf.label(), byShelf[shelf]?.size ?: 0) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) { page ->
            val shelf = shelves[page]
            val items = byShelf[shelf].orEmpty()
            val filter = options.filter(page)
            Column(Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = state.syncing,
                onRefresh = { scope.launch { app.library.sync() } },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    items.isEmpty() && filter.active ->
                        EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.lib_no_matches)) {
                            TextButton(onClick = { options.setFilter(page, LibraryFilter()) }) { Text(stringResource(R.string.lib_clear_filters)) }
                        }
                    (state.firstSync || state.refreshingStale) && shelf == Shelf.UpNext ->
                        PosterListSkeleton(
                            posterWidth = 68.dp,
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottomPadding()),
                            spacing = 10.dp,
                        )
                    state.firstSync -> PosterGridSkeleton(bottomPadding())
                    items.isEmpty() && state.loaded && !state.syncing ->
                        EmptyState(shelf.emptyIcon(), stringResource(shelf.emptyTitle(type)), stringResource(shelf.emptyText()))
                    shelf == Shelf.UpNext ->
                        UpNextList(items, state, fromLatest, ::open) { message -> scope.launch { snackbar.showSnackbar(message) } }
                    else ->
                        LibraryGrid(
                            items = items,
                            state = state,
                            onOpen = ::open,
                            showProgress = shelf == Shelf.OnHold || shelf == Shelf.Dropped,
                            showAiring = true,
                        )
                }
            }
            }
        }
    }
}

@Composable
private fun UpNextList(
    items: List<LibraryItem>,
    state: LibraryState,
    fromLatest: Boolean,
    onOpen: (LibraryItem) -> Unit,
    onError: (String) -> Unit,
) {
    val app = rememberApp()
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf<Pair<Playback, LibraryItem>?>(null) }
    val listState = rememberLazyListState()

    // A list keeps its first visible item in view when items move. When the top show is watched
    // elsewhere and reorders after a sync, that would scroll it under the top bar; stay at the top.
    Snapshot.withoutReadObservation {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) listState.requestScrollToItem(0)
    }

    fun run(
        item: LibraryItem,
        label: String,
        block: suspend () -> Unit,
    ) {
        busy = busy + item.key
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(resources.getString(R.string.lib_mark_failed, label, e.message.orEmpty()))
            }
            busy = busy - item.key
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottomPadding()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.key }) { item ->
            val playback = state.playbackFor(item.type, item.ids.simkl)?.takeIf { it.episode != null }
            val next = Shelves.nextEpisode(item, fromLatest, state.latestNext[item.key])
            val left = item.airedUnwatched
            ShowRow(
                item = item,
                headline =
                    if (playback != null) {
                        stringResource(R.string.lib_playback_progress, playback.episode!!.label, playback.progress.roundToInt())
                    } else {
                        next?.let { listOfNotNull(it.ref.label, it.title).joinToString(" · ") } ?: stringResource(R.string.lib_continue_watching)
                    },
                supporting =
                    listOfNotNull(
                        playback?.episodeTitle,
                        if (playback == null && left > 0) pluralStringResource(R.plurals.lib_episodes_left, left, left) else null,
                        Format.ago(item.lastWatchedAt)?.let { stringResource(R.string.lib_watched_ago, it) },
                    ).joinToString(" · ").ifEmpty { null },
                airing = Shelves.airingOf(item, state.showInfo[item.key], state.nextAirDate(item)),
                onClick = { onOpen(item) },
                modifier = Modifier.animateItem(),
            ) {
                when {
                    item.key in busy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    playback != null ->
                        FilledTonalIconButton(onClick = { confirm = playback to item }) {
                            Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.lib_mark_episode_watched, playback.episode!!.label))
                        }
                    next != null ->
                        FilledTonalIconButton(onClick = {
                            run(item, next.ref.label) { app.library.markEpisodeWatched(item, next.ref) }
                        }) { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.lib_mark_episode_watched, next.ref.label)) }
                }
            }
        }
    }
    confirm?.let { (playback, item) ->
        ConfirmWatchedDialog(
            playback = playback,
            onConfirm = { run(item, playback.episode?.label ?: playback.title) { app.library.markPlaybackWatched(playback, item) } },
            onDismiss = { confirm = null },
        )
    }
}
