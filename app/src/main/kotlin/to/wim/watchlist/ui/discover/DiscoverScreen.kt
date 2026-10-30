package to.wim.watchlist.ui.discover

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import to.wim.watchlist.R
import to.wim.watchlist.data.CatalogRepository
import to.wim.watchlist.data.DiscoverSection
import to.wim.watchlist.data.NavTab
import to.wim.watchlist.data.model.MediaSummary
import to.wim.watchlist.data.model.MediaType
import to.wim.watchlist.ui.LocalNavigator
import to.wim.watchlist.ui.components.PosterRow
import to.wim.watchlist.ui.components.openUrl
import to.wim.watchlist.ui.components.rememberApp
import to.wim.watchlist.ui.library.SectionHeader
import to.wim.watchlist.ui.library.bottomPadding
import to.wim.watchlist.ui.main.MainTopBar
import to.wim.watchlist.ui.theme.Motion
import to.wim.watchlist.ui.components.label
import kotlinx.coroutines.launch
import to.wim.watchlist.ui.components.PosterRowSkeleton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(types: List<MediaType>) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var typeName by rememberSaveable { mutableStateOf(types.first().name) }
    // A type turned off in Settings while it was selected falls back to the first one that is on.
    val type = types.firstOrNull { it.name == typeName } ?: types.first()
    val sections = remember(type) { CatalogRepository.sections(type) }
    var refresh by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomPadding())) },
        topBar = {
            Column {
                MainTopBar(NavTab.Discover.label(), scrollBehavior) {
                    IconButton(onClick = {
                        scope.launch {
                            val id = runCatching { app.catalog.random(type) }.getOrNull()
                            if (id != null) navigator.openDetail(type, id) else snackbar.showSnackbar(resources.getString(R.string.screens_discover_no_suggestion))
                        }
                    }) { Icon(Icons.Rounded.Casino, contentDescription = stringResource(R.string.screens_discover_surprise)) }
                }
                // TV shows, anime and movies are parallel sections of Discover, so they are tabs,
                // like the shelves of the library tabs. Only the types turned on in Settings; with
                // just one there is nothing to switch to.
                if (types.size > 1) {
                    PrimaryTabRow(selectedTabIndex = types.indexOf(type)) {
                        types.forEach { t ->
                            Tab(selected = t == type, onClick = { typeName = t.name }, text = { Text(t.label()) })
                        }
                    }
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                refresh++
                refreshing = false
            },
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding()), modifier = Modifier.fillMaxSize()) {
                sections.forEach { section ->
                    item(key = "${section.id}:$refresh") { SectionRow(section, refresh) }
                }
                item(key = "attribution") {
                    androidx.compose.material3.TextButton(
                        onClick = { context.openUrl("https://simkl.com/") },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) { Text(stringResource(R.string.screens_powered_by)) }
                }
            }
        }
    }
}

/** One discovery row; hidden when SIMKL has nothing for it. */
@Composable
private fun SectionRow(
    section: DiscoverSection,
    refresh: Int,
) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val library by app.library.state.collectAsState()
    var items by remember { mutableStateOf<List<MediaSummary>?>(null) }
    LaunchedEffect(section.id, refresh) { items = app.catalog.section(section, refresh = refresh > 0) }
    // Fade through from the placeholders to the posters; the row's height change animates with it.
    AnimatedContent(
        targetState = items,
        contentKey = { list -> if (list == null) 0 else if (list.isEmpty()) 1 else 2 },
        transitionSpec = { fadeIn(Motion.fadeSpec(210, delayMillis = 90)) togetherWith fadeOut(Motion.fadeSpec(90)) },
        label = "discoverRow",
    ) { list ->
        when {
            list == null ->
                Column {
                    SectionHeader(stringResource(section.title), modifier = Modifier.padding(horizontal = 8.dp))
                    PosterRowSkeleton()
                }
            list.isNotEmpty() ->
                Column {
                    SectionHeader(stringResource(section.title), modifier = Modifier.padding(horizontal = 8.dp))
                    PosterRow(
                        list.take(30),
                        onClick = { navigator.openDetail(it.type, it.simklId, it.title, it.poster) },
                        inLibrary = { library.item(it.type, it.simklId) != null },
                    )
                }
            else -> {}
        }
    }
}
