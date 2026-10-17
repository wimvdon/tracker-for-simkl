package io.github.impca201.trackerforsimkl.ui.library

import io.github.impca201.trackerforsimkl.ui.components.label
import androidx.compose.ui.res.stringResource
import io.github.impca201.trackerforsimkl.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.impca201.trackerforsimkl.data.NavTab
import io.github.impca201.trackerforsimkl.data.model.MediaType
import io.github.impca201.trackerforsimkl.data.model.MovieShelf
import io.github.impca201.trackerforsimkl.ui.LocalNavigator
import io.github.impca201.trackerforsimkl.ui.theme.EmptyState
import io.github.impca201.trackerforsimkl.ui.components.rememberApp
import io.github.impca201.trackerforsimkl.ui.main.MainTopBar
import kotlinx.coroutines.launch
import io.github.impca201.trackerforsimkl.ui.components.PosterGridSkeleton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen() {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by app.library.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val shelves = MovieShelf.entries
    val options = rememberTabOptions(shelves.map { if (it == MovieShelf.PlanToWatch) SortOrder.Added else SortOrder.Recent })
    val pagerState = rememberPagerState { shelves.size }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val byStatus =
        remember(state, options.version) {
            state.items.filter { it.type == MediaType.Movie }.groupBy { it.status }.mapValues { (status, v) ->
                val tab = shelves.indexOfFirst { it.status == status }
                if (tab < 0) v else options.sort(tab).sort(options.filter(tab).apply(v))
            }
        }

    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it) } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomPadding())) },
        topBar = {
            Column {
                MainTopBar(NavTab.Movies.label(), scrollBehavior) {
                    val page = pagerState.currentPage
                    FilterButton(options.filter(page), animeTypes = false) { options.setFilter(page, it) }
                    SortButton(options.sort(page), SortOrder.choices(planToWatch = shelves[page] == MovieShelf.PlanToWatch)) { options.setSort(page, it) }
                }
                PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                    shelves.forEachIndexed { index, shelf ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { TabLabel(shelf.label(), byStatus[shelf.status]?.size ?: 0) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) { page ->
            val shelf = shelves[page]
            val items = byStatus[shelf.status].orEmpty()
            PullToRefreshBox(
                isRefreshing = state.syncing,
                onRefresh = { scope.launch { app.library.sync() } },
                modifier = Modifier.fillMaxSize(),
            ) {
                val filter = options.filter(page)
                if (items.isEmpty() && filter.active) {
                    EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.lib_no_matches)) {
                        Button(onClick = { options.setFilter(page, LibraryFilter()) }) { Text(stringResource(R.string.lib_clear_filters)) }
                    }
                } else if (state.firstSync) {
                    PosterGridSkeleton(bottomPadding())
                } else if (items.isEmpty() && state.loaded && !state.syncing) {
                    val (icon, title) =
                        when (shelf) {
                            MovieShelf.PlanToWatch -> Icons.Rounded.Bookmark to R.string.lib_empty_plan_movies
                            MovieShelf.Completed -> Icons.Rounded.CheckCircle to R.string.lib_empty_watched_movies
                            MovieShelf.Dropped -> Icons.Rounded.Block to R.string.lib_empty_dropped_movies
                        }
                    EmptyState(
                        icon,
                        stringResource(title),
                        body = if (shelf == MovieShelf.PlanToWatch) stringResource(R.string.lib_empty_plan_movies_text) else null,
                    )
                } else {
                    LibraryGrid(
                        items = items,
                        state = state,
                        onOpen = { navigator.openDetail(it.type, it.ids.simkl, it.title, it.poster) },
                        showProgress = false,
                        showAiring = false,
                    )
                }
            }
        }
    }
}
