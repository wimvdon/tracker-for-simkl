package io.github.impca201.simklr.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.github.impca201.simklr.data.CatalogRepository
import io.github.impca201.simklr.data.DiscoverSection
import io.github.impca201.simklr.data.NavTab
import io.github.impca201.simklr.data.model.MediaSummary
import io.github.impca201.simklr.data.model.MediaType
import io.github.impca201.simklr.ui.LocalNavigator
import io.github.impca201.simklr.ui.Route
import io.github.impca201.simklr.ui.components.PosterRow
import io.github.impca201.simklr.ui.components.openUrl
import io.github.impca201.simklr.ui.components.rememberApp
import io.github.impca201.simklr.ui.library.SectionHeader
import io.github.impca201.simklr.ui.library.bottomPadding
import io.github.impca201.simklr.ui.main.MainTopBar
import io.github.impca201.simklr.ui.search.label
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen() {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var typeName by rememberSaveable { mutableStateOf(MediaType.Tv.name) }
    val type = MediaType.valueOf(typeName)
    val sections = remember(type) { CatalogRepository.sections(type) }
    var refresh by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomPadding())) },
        topBar = { MainTopBar(NavTab.Discover.label, scrollBehavior, type) },
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
                item(key = "search") {
                    Surface(
                        onClick = { navigator.push(Route.Search(type)) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(56.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Rounded.Search, null)
                            Text("Search ${type.label().lowercase()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item(key = "types") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MediaType.entries) { t ->
                            FilterChip(selected = t == type, onClick = { typeName = t.name }, label = { Text(t.label()) })
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    scope.launch {
                                        val id = runCatching { app.catalog.random(type) }.getOrNull()
                                        if (id != null) navigator.openDetail(type, id) else snackbar.showSnackbar("SIMKL had no suggestion this time")
                                    }
                                },
                                label = { Text("Surprise me") },
                                leadingIcon = { Icon(Icons.Rounded.Casino, null, Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
                sections.forEach { section ->
                    item(key = "${section.id}:$refresh") { SectionRow(section, refresh) }
                }
                item(key = "attribution") {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    androidx.compose.material3.TextButton(
                        onClick = { context.openUrl("https://simkl.com/") },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) { Text("Powered by SIMKL · simkl.com") }
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
    val list = items
    when {
        list == null ->
            Column {
                SectionHeader(section.title, modifier = Modifier.padding(horizontal = 8.dp))
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        list.isNotEmpty() ->
            Column {
                SectionHeader(section.title, modifier = Modifier.padding(horizontal = 8.dp))
                PosterRow(
                    list.take(30),
                    onClick = { navigator.openDetail(it.type, it.simklId, it.title, it.poster) },
                    inLibrary = { library.item(it.type, it.simklId) != null },
                )
            }
    }
}
