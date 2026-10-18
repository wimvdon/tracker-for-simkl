package io.github.impca201.trackerforsimkl.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.impca201.trackerforsimkl.R
import io.github.impca201.trackerforsimkl.data.model.MediaSummary
import io.github.impca201.trackerforsimkl.data.model.MediaType
import io.github.impca201.trackerforsimkl.data.model.SearchSubject
import io.github.impca201.trackerforsimkl.ui.LocalNavigator
import io.github.impca201.trackerforsimkl.ui.theme.EmptyState
import io.github.impca201.trackerforsimkl.ui.components.Format
import io.github.impca201.trackerforsimkl.ui.components.PosterBadge
import io.github.impca201.trackerforsimkl.ui.components.PosterRow
import io.github.impca201.trackerforsimkl.ui.components.Poster
import io.github.impca201.trackerforsimkl.ui.components.rememberApp
import io.github.impca201.trackerforsimkl.ui.components.label
import io.github.impca201.trackerforsimkl.ui.library.SectionHeader
import io.github.impca201.trackerforsimkl.ui.theme.Motion
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(types: List<MediaType>) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val library by app.library.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var typeName by rememberSaveable { mutableStateOf<String?>(null) }
    // Only the types turned on in Settings; "All" when the chosen one was turned off.
    val type = types.firstOrNull { it.name == typeName }
    var results by remember { mutableStateOf<Map<MediaType, List<MediaSummary>>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }
    LaunchedEffect(query, type, types) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyMap()
            loading = false
            return@LaunchedEffect
        }
        // Loading covers the debounce too, so "No results" doesn't flash before the search runs.
        loading = true
        delay(350) // Debounce typing.
        results = app.catalog.search(q, type?.let { listOf(it) } ?: types)
        loading = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.screens_back)) }
                    },
                    title = {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.screens_search_placeholder)) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.screens_clear)) }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            shape = RoundedCornerShape(28.dp),
                            colors =
                                TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                            modifier = Modifier.fillMaxWidth().padding(end = 12.dp).focusRequester(focus),
                        )
                    },
                )
                if (types.size > 1) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = type == null, onClick = { typeName = null }, label = { Text(stringResource(R.string.screens_search_all)) }) }
                        items(types) { t ->
                            FilterChip(selected = type == t, onClick = { typeName = t.name }, label = { Text(t.label()) })
                        }
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp)) else Box(Modifier.padding(top = 12.dp))
            }
        },
    ) { padding ->
        val sections = results.filterValues { it.isNotEmpty() }
        AnimatedContent(
            targetState = sections,
            contentKey = { it.isEmpty() },
            transitionSpec = { fadeIn(Motion.fadeSpec(210, delayMillis = 90)) togetherWith fadeOut(Motion.fadeSpec(90)) },
            label = "searchContent",
            modifier = Modifier.fillMaxSize(),
        ) { shown ->
            if (shown.isEmpty()) {
                if (query.trim().length >= 2 && !loading) {
                    EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.screens_search_no_results, query.trim()), Modifier.padding(padding))
                } else {
                    EmptyState(Icons.Rounded.Search, stringResource(SearchSubject.of(types).titleRes()), Modifier.padding(padding), body = stringResource(R.string.screens_search_empty_body))
                }
            } else {
                LazyColumn(
                    contentPadding =
                        PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // "All" with several types: a row of posters per type, like Discover. A chosen
                    // type gets the full list with overviews.
                    if (type == null && types.size > 1) {
                        shown.forEach { (t, list) ->
                            item(key = "row-$t") {
                                Column(Modifier.animateItem()) {
                                    SectionHeader(t.label(), list.size, Modifier.padding(horizontal = 8.dp))
                                    PosterRow(
                                        list,
                                        onClick = { navigator.openDetail(it.type, it.simklId, it.title, it.poster) },
                                        inLibrary = { library.item(it.type, it.simklId) != null },
                                    )
                                }
                            }
                        }
                    } else {
                        shown.forEach { (t, list) ->
                            items(list, key = { "$t:${it.simklId}" }) { result ->
                                val inLibrary = library.item(result.type, result.simklId)
                                ResultRow(result, inLibrary?.status?.label(result.type), Modifier.animateItem()) {
                                    navigator.openDetail(result.type, result.simklId, result.title, result.poster)
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
fun ResultRow(
    item: MediaSummary,
    status: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, modifier = modifier, color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Poster(item.poster, item.title, Modifier.width(56.dp), shape = RoundedCornerShape(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(item.subtitle, item.rating?.let { "★ ${Format.rating(it)}" }).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.overview?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (status != null) PosterBadge(status)
        }
    }
}

/** The empty-screen title for the types that are on. */
private fun SearchSubject.titleRes(): Int =
    when (this) {
        SearchSubject.All -> R.string.screens_search_empty_title
        SearchSubject.TvAndAnime -> R.string.screens_search_empty_title_tv_anime
        SearchSubject.TvAndMovies -> R.string.screens_search_empty_title_tv_movies
        SearchSubject.AnimeAndMovies -> R.string.screens_search_empty_title_anime_movies
        SearchSubject.Tv -> R.string.screens_search_empty_title_tv
        SearchSubject.Anime -> R.string.screens_search_empty_title_anime
        SearchSubject.Movies -> R.string.screens_search_empty_title_movies
    }
