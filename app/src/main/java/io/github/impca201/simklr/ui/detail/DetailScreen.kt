package io.github.impca201.simklr.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import io.github.impca201.simklr.data.model.NextEpisode
import io.github.impca201.simklr.data.model.Playback
import io.github.impca201.simklr.data.model.Shelves
import io.github.impca201.simklr.ui.components.ConfirmWatchedDialog
import kotlin.math.roundToInt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.impca201.simklr.data.AppSettings
import io.github.impca201.simklr.data.model.AiringState
import io.github.impca201.simklr.data.model.Episode
import io.github.impca201.simklr.data.model.EpisodeRef
import io.github.impca201.simklr.data.model.Images
import io.github.impca201.simklr.data.model.LibraryItem
import io.github.impca201.simklr.data.model.ListStatus
import io.github.impca201.simklr.data.model.MediaDetails
import io.github.impca201.simklr.data.model.MediaIds
import io.github.impca201.simklr.data.model.MediaType
import io.github.impca201.simklr.ui.LocalNavigator
import io.github.impca201.simklr.ui.Route
import io.github.impca201.simklr.ui.components.AiringBadge
import io.github.impca201.simklr.ui.components.EmptyState
import io.github.impca201.simklr.ui.components.Format
import io.github.impca201.simklr.ui.components.Poster
import io.github.impca201.simklr.ui.components.PosterRow
import io.github.impca201.simklr.ui.components.openUrl
import io.github.impca201.simklr.ui.components.rememberApp
import io.github.impca201.simklr.ui.components.share
import io.github.impca201.simklr.ui.library.SectionHeader
import kotlinx.coroutines.launch

fun ListStatus.label(type: MediaType): String =
    when (this) {
        ListStatus.Watching -> "Watching"
        ListStatus.PlanToWatch -> "Plan to watch"
        ListStatus.OnHold -> "On hold"
        ListStatus.Completed -> if (type == MediaType.Movie) "Watched" else "Completed"
        ListStatus.Dropped -> "Dropped"
    }

fun webUrl(
    type: MediaType,
    simklId: Long,
    slug: String? = null,
) = "https://simkl.com/${type.webPath}/$simklId" + (slug?.let { "/$it" } ?: "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    route: Route.Detail,
    settings: AppSettings,
) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val library by app.library.state.collectAsState()
    val item = library.item(route.type, route.simklId)

    var details by remember { mutableStateOf<MediaDetails?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(route.simklId, reload) {
        error = null
        try {
            val d = app.catalog.details(route.type, route.simklId, refresh = reload > 0)
            details = d
            app.library.rememberDetails(d)
        } catch (e: Exception) {
            error = e.message ?: "Could not load the details"
        }
    }
    LaunchedEffect(route.simklId, reload) {
        if (route.type.isEpisodic) episodes = runCatching { app.catalog.episodes(route.type, route.simklId, refresh = reload > 0) }.getOrDefault(emptyList())
    }

    val title = details?.title ?: item?.title ?: route.title ?: ""
    val ids = details?.ids ?: item?.ids ?: MediaIds(route.simklId)
    val poster = details?.poster ?: item?.poster ?: route.poster
    val year = details?.year ?: item?.year

    fun run(
        failure: String,
        block: suspend () -> Unit,
    ) {
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                snackbar.showSnackbar("$failure: ${e.message}")
            }
        }
    }

    val playback = library.playbackFor(route.type, route.simklId)
    var confirmPlayback by remember { mutableStateOf<Playback?>(null) }
    confirmPlayback?.let { pb ->
        ConfirmWatchedDialog(
            playback = pb,
            onConfirm = { run("Couldn't mark watched") { app.library.markPlaybackWatched(pb, item) } },
            onDismiss = { confirmPlayback = null },
        )
    }

    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { if (scrolled) Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }, colors = IconButtonDefaults.filledTonalIconButtonColors()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { context.share(title, webUrl(route.type, route.simklId, ids.slug)) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    ) { Icon(Icons.Rounded.Share, contentDescription = "Share") }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = if (scrolled) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
                    ),
            )
        },
    ) { _ ->
        if (details == null && error != null && item == null) {
            EmptyState(Icons.Rounded.ErrorOutline, "Couldn't load this title", error) {
                Button(onClick = { reload++ }) { Text("Try again") }
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { Header(route.type, title, poster, details, item, library.showInfo[item?.key ?: ""]?.fanart) }
            item(key = "actions") {
                Actions(
                    type = route.type,
                    item = item,
                    next = item?.let { Shelves.nextEpisode(it, settings.continueFromLatest, library.latestNext[it.key]) },
                    playback = playback,
                    onPlaybackWatched = { confirmPlayback = it },
                    onStatus = { status ->
                        run("Couldn't update the list") {
                            if (status == null) {
                                app.library.remove(route.type, ids.simkl)
                            } else {
                                app.library.setStatus(route.type, ids, title, poster, year, status)
                            }
                        }
                    },
                    onRate = { rating -> run("Couldn't save the rating") { app.library.rate(route.type, ids.simkl, rating) } },
                    onMovieWatched = { run("Couldn't mark watched") { app.library.markMovieWatched(ids, title, poster, year) } },
                    onWatchNext = { next ->
                        run("Couldn't mark ${next.label} watched") {
                            app.library.setEpisodesWatched(route.type, ids, title, poster, listOf(next), true, episodes)
                        }
                    },
                    onCheckIn = { episode ->
                        run("Couldn't check in") {
                            app.library.checkIn(route.type, ids.simkl, episode)
                            snackbar.showSnackbar("Checked in. SIMKL marks it watched when it ends.")
                        }
                    },
                )
            }
            val d = details
            if (d == null) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else {
                item(key = "about") { About(d) }
                if (d.trailers.isNotEmpty()) {
                    item(key = "trailers") { Trailers(d) }
                }
            }
            if (route.type.isEpisodic && episodes.isNotEmpty()) {
                episodesSection(
                    playback = playback,
                    onPlaybackWatched = { confirmPlayback = it },
                    episodes = if (settings.hideSpecials) episodes.filterNot { it.special } else episodes,
                    item = item,
                    onToggle = { refs, watched ->
                        run("Couldn't update the episodes") {
                            app.library.setEpisodesWatched(route.type, ids, title, poster, refs, watched, episodes)
                        }
                    },
                    onAllWatched = {
                        run("Couldn't mark the episodes watched") {
                            app.library.setEpisodesWatched(
                                route.type,
                                ids,
                                title,
                                poster,
                                episodes.filter { it.aired && !it.special && item?.isWatched(it.season, it.episode) != true }
                                    .map { EpisodeRef(it.season, it.episode) },
                                true,
                                episodes,
                            )
                        }
                    },
                )
            }
            if (d != null && d.recommendations.isNotEmpty()) {
                item(key = "recs") {
                    Column {
                        SectionHeader("If you like this", modifier = Modifier.padding(horizontal = 8.dp))
                        PosterRow(
                            d.recommendations,
                            onClick = { navigator.openDetail(it.type, it.simklId, it.title, it.poster) },
                            inLibrary = { library.item(it.type, it.simklId) != null },
                        )
                    }
                }
            }
            item(key = "links") { Links(route.type, ids) }
        }
    }
}

@Composable
private fun Header(
    type: MediaType,
    title: String,
    poster: String?,
    details: MediaDetails?,
    item: LibraryItem?,
    cachedFanart: String?,
) {
    val fanart = Images.fanart(details?.fanart ?: cachedFanart)
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            if (fanart != null) {
                AsyncImage(fanart, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (poster != null) {
                AsyncImage(Images.poster(poster, large = true), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.35f))
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to background.copy(alpha = 0.3f), 0.5f to background.copy(alpha = 0.2f), 1f to background),
                ),
            )
        }
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 170.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 8.dp) {
                Poster(poster, title, Modifier.width(120.dp), large = true, shape = RoundedCornerShape(16.dp))
            }
            Column(Modifier.weight(1f).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                details?.originalTitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                val meta =
                    listOfNotNull(
                        (details?.year ?: item?.year)?.toString(),
                        details?.animeType?.uppercase(),
                        details?.runtime?.takeIf { it > 0 }?.let { "$it min" },
                        details?.certification,
                        details?.network,
                    ).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (type.isEpisodic && details != null) AiringBadge(details.airing)
            }
        }
    }
}

@Composable
private fun Actions(
    type: MediaType,
    item: LibraryItem?,
    next: NextEpisode?,
    playback: Playback?,
    onPlaybackWatched: (Playback) -> Unit,
    onStatus: (ListStatus?) -> Unit,
    onRate: (Int?) -> Unit,
    onMovieWatched: () -> Unit,
    onWatchNext: (EpisodeRef) -> Unit,
    onCheckIn: (EpisodeRef?) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (item == null) {
                    Button(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add to list")
                    }
                } else {
                    FilledTonalButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(item.status.label(type))
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    ListStatus.forType(type).forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.label(type)) },
                            onClick = {
                                menu = false
                                onStatus(status)
                            },
                            trailingIcon = { if (item?.status == status) Icon(Icons.Rounded.Check, null) },
                        )
                    }
                    if (item != null) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Remove from my lists", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menu = false
                                onStatus(null)
                            },
                        )
                    }
                }
            }
            OutlinedButton(onClick = { rating = true }) {
                val rated = item?.userRating
                Icon(if (rated != null) Icons.Rounded.Star else Icons.Rounded.StarOutline, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(rated?.toString() ?: "Rate")
            }
        }
        if (type == MediaType.Movie) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item?.status != ListStatus.Completed) {
                    FilledTonalButton(
                        onClick = { if (playback != null) onPlaybackWatched(playback) else onMovieWatched() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mark as watched")
                    }
                }
                OutlinedButton(onClick = { onCheckIn(null) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Watching now")
                }
            }
        }
        if (playback != null) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "In progress" + (playback.episode?.let { " · ${it.label}" } ?: "") + " · ${playback.progress.roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        LinearProgressIndicator(
                            progress = { (playback.progress / 100).toFloat() },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        )
                    }
                    TextButton(onClick = { onPlaybackWatched(playback) }) { Text("Mark watched") }
                }
            }
        }
        if (type.isEpisodic && next != null && item?.status == ListStatus.Watching) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = { if (playback != null && playback.episode == next.ref) onPlaybackWatched(playback) else onWatchNext(next.ref) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Watched ${next.ref.label}" + (next.title?.let { " · $it" } ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = { onCheckIn(next.ref) }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Watching ${next.ref.label} now", modifier = Modifier.size(18.dp))
                }
            }
        }
        item?.memo?.let { memo ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(
                    "Your note: $memo",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
        }
        if (item != null && type.isEpisodic && item.totalEpisodes > 0) {
            Text(
                "${item.watchedEpisodes} of ${item.totalEpisodes} episodes watched" +
                    (item.lastWatchedAt?.let { " · last ${Format.ago(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (rating) {
        RatingDialog(item?.userRating, onDismiss = { rating = false }) {
            rating = false
            onRate(it)
        }
    }
}

@Composable
private fun RatingDialog(
    current: Int?,
    onDismiss: () -> Unit,
    onRate: (Int?) -> Unit,
) {
    var value by remember { mutableFloatStateOf((current ?: 7).toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Star, null) },
        title = { Text("Your rating") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value.toInt().toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text(ratingWord(value.toInt()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = value, onValueChange = { value = it }, valueRange = 1f..10f, steps = 8)
            }
        },
        confirmButton = { TextButton(onClick = { onRate(value.toInt()) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onRate(null) }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private fun ratingWord(value: Int) =
    when (value) {
        1 -> "Unwatchable"
        2 -> "Terrible"
        3 -> "Bad"
        4 -> "Poor"
        5 -> "Average"
        6 -> "Fair"
        7 -> "Good"
        8 -> "Very good"
        9 -> "Great"
        else -> "Masterpiece"
    }

@Composable
private fun About(d: MediaDetails) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (d.ratings.isNotEmpty() || d.rank != null) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(d.ratings) { r ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text("${r.source} ${Format.rating(r.value)}" + (r.votes?.let { " (${compact(it)})" } ?: "")) },
                        icon = { Icon(Icons.Rounded.Star, null, Modifier.size(16.dp), tint = Color(0xFFFFB300)) },
                    )
                }
                d.rank?.let { rank -> item { SuggestionChip(onClick = {}, label = { Text("Rank #$rank") }) } }
            }
        }
        if (d.genres.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(d.genres) { g -> AssistChip(onClick = {}, label = { Text(g.replaceFirstChar { it.uppercase() }) }) }
            }
        }
        d.overview?.let { overview ->
            Text(
                overview,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.animateContentSize().clickable { expanded = !expanded },
            )
        }
        val facts =
            listOfNotNull(
                d.status?.let { "Status" to it.replaceFirstChar { c -> c.uppercase() } },
                d.airs?.let { "Airs" to it },
                d.firstAired?.let { (if (d.type == MediaType.Movie) "Released" else "First aired") to (Format.date(it) ?: it) },
                d.totalEpisodes?.takeIf { it > 0 }?.let { "Episodes" to it.toString() },
                d.network?.let { "Network" to it },
                d.country?.let { "Country" to it },
                d.director?.let { "Director" to it },
                d.budget?.let { "Budget" to money(it) },
                d.revenue?.let { "Box office" to money(it) },
                d.alternativeTitles.takeIf { it.isNotEmpty() }?.let { "Also known as" to it.take(4).joinToString(", ") },
            )
        if (facts.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    facts.forEach { (label, value) ->
                        Row {
                            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
                            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private fun money(value: Long): String =
    when {
        value >= 1_000_000_000 -> "$%.2fB".format(value / 1_000_000_000.0)
        value >= 1_000_000 -> "$%.1fM".format(value / 1_000_000.0)
        else -> "$%,d".format(value)
    }

private fun compact(votes: Int): String =
    when {
        votes >= 1_000_000 -> "%.1fM".format(votes / 1_000_000.0)
        votes >= 1_000 -> "%.1fk".format(votes / 1_000.0)
        else -> votes.toString()
    }

@Composable
private fun Trailers(d: MediaDetails) {
    val context = LocalContext.current
    Column {
        SectionHeader("Trailers", modifier = Modifier.padding(horizontal = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(d.trailers) { t ->
                Column(
                    Modifier.width(220.dp).clip(RoundedCornerShape(16.dp)).clickable { context.openUrl("https://www.youtube.com/watch?v=${t.youtubeId}") },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AsyncImage(
                            "https://img.youtube.com/vi/${t.youtubeId}/mqdefault.jpg",
                            null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White) {
                            Icon(Icons.Rounded.PlayArrow, "Play", Modifier.padding(8.dp))
                        }
                    }
                    t.name?.let { Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.episodesSection(
    playback: Playback?,
    onPlaybackWatched: (Playback) -> Unit,
    episodes: List<Episode>,
    item: LibraryItem?,
    onToggle: (List<EpisodeRef>, Boolean) -> Unit,
    onAllWatched: () -> Unit,
) {
    val seasons = episodes.groupBy { if (it.special) 0 else it.season ?: 1 }.toSortedMap(compareBy { if (it == 0) Int.MAX_VALUE else it })
    item(key = "episodes") {
        EpisodesBlock(seasons, item, playback, onPlaybackWatched, onToggle, onAllWatched)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodesBlock(
    seasons: Map<Int, List<Episode>>,
    item: LibraryItem?,
    playback: Playback?,
    onPlaybackWatched: (Playback) -> Unit,
    onToggle: (List<EpisodeRef>, Boolean) -> Unit,
    onAllWatched: () -> Unit,
) {
    val defaultSeason =
        (playback?.episode?.season ?: item?.nextToWatch?.ref?.season)?.takeIf { it in seasons } ?: seasons.keys.first()
    var selected by rememberSaveable { mutableIntStateOf(defaultSeason) }
    val current = if (selected in seasons) selected else seasons.keys.first()
    val list = seasons[current].orEmpty()
    fun Episode.ref() = EpisodeRef(this.season, this.episode)
    fun Episode.watched() = item?.isWatched(this.season, this.episode) == true

    /** Marking the paused episode watched asks first, because it removes the saved progress. */
    fun toggle(
        e: Episode,
        watched: Boolean,
    ) {
        if (watched && playback != null && playback.episode == e.ref()) onPlaybackWatched(playback) else onToggle(listOf(e.ref()), watched)
    }
    var menu by remember { mutableStateOf(false) }

    Column(Modifier.padding(top = 8.dp)) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Episodes", modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Season options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Mark season watched") },
                        leadingIcon = { Icon(Icons.Rounded.DoneAll, null) },
                        onClick = {
                            menu = false
                            onToggle(list.filter { it.aired && !it.watched() }.map { it.ref() }, true)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Mark all aired episodes watched") },
                        leadingIcon = { Icon(Icons.Rounded.DoneAll, null) },
                        onClick = {
                            menu = false
                            onAllWatched()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Mark season unwatched") },
                        leadingIcon = { Icon(Icons.Rounded.RemoveDone, null) },
                        onClick = {
                            menu = false
                            onToggle(list.filter { it.watched() }.map { it.ref() }, false)
                        },
                    )
                }
            }
        }
        if (seasons.size > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(seasons.keys.toList()) { n ->
                    val eps = seasons[n].orEmpty()
                    val done = eps.isNotEmpty() && eps.all { e -> item?.isWatched(e.season, e.episode) == true }
                    FilterChip(
                        selected = n == current,
                        onClick = { selected = n },
                        label = { Text(if (n == 0) "Specials" else "Season $n") },
                        leadingIcon = if (done) ({ Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp)) }) else null,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        list.forEach { e ->
            val watched = e.watched()
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        enabled = e.aired,
                        onClick = { toggle(e, !watched) },
                        onLongClick = {
                            // Long-press: everything up to and including this episode.
                            val upTo = list.takeWhile { it != e } + e
                            onToggle(upTo.filter { it.aired && !it.watched() }.map { it.ref() }, true)
                        },
                    ).padding(horizontal = 16.dp, vertical = 8.dp)
                    .alpha(if (e.aired) 1f else 0.55f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Images.episode(e.image)?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    Surface(
                        Modifier.align(Alignment.BottomStart).padding(4.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White,
                    ) { Text(e.ref().label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(e.title ?: "Episode ${e.episode}", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val date = io.github.impca201.simklr.data.model.Dates.parse(e.date)
                    if (date != null) {
                        Text(
                            if (e.aired) Format.date(date) else "Airs ${Format.relative(date)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!watched && playback != null && playback.episode == e.ref()) {
                        Text(
                            "In progress · ${playback.progress.roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        LinearProgressIndicator(
                            progress = { (playback.progress / 100).toFloat() },
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                        )
                    }
                    e.description?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Checkbox(checked = watched, onCheckedChange = if (e.aired) ({ toggle(e, it) }) else null, enabled = e.aired)
            }
        }
        Text(
            "Tap an episode to toggle it, long-press to mark everything up to it.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Links(
    type: MediaType,
    ids: MediaIds,
) {
    val context = LocalContext.current
    val links =
        listOfNotNull(
            "SIMKL" to webUrl(type, ids.simkl, ids.slug),
            ids.imdb?.let { "IMDb" to "https://www.imdb.com/title/$it" },
            ids.tmdb?.let { "TMDB" to "https://www.themoviedb.org/${if (type == MediaType.Movie) "movie" else "tv"}/$it" },
            ids.mal?.let { "MyAnimeList" to "https://myanimelist.net/anime/$it" },
            ids.anilist?.let { "AniList" to "https://anilist.co/anime/$it" },
            ids.kitsu?.let { "Kitsu" to "https://kitsu.app/anime/$it" },
            ids.trakt?.let { "Trakt" to "https://trakt.tv/${if (type == MediaType.Movie) "movies" else "shows"}/$it" },
        )
    Column {
        SectionHeader("Links", modifier = Modifier.padding(horizontal = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(links) { (label, url) ->
                AssistChip(
                    onClick = { context.openUrl(url) },
                    label = { Text(label) },
                    trailingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp)) },
                )
            }
        }
    }
}
