package io.github.impca201.trackerforsimkl.ui.detail

import android.icu.text.CompactDecimalFormat
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import io.github.impca201.trackerforsimkl.data.model.DetailActions
import io.github.impca201.trackerforsimkl.data.model.NextEpisode
import io.github.impca201.trackerforsimkl.data.model.DetailFacts
import io.github.impca201.trackerforsimkl.data.model.Rating
import io.github.impca201.trackerforsimkl.data.model.Playback
import io.github.impca201.trackerforsimkl.data.model.Shelves
import io.github.impca201.trackerforsimkl.data.model.ShowStatus
import io.github.impca201.trackerforsimkl.ui.components.ConfirmWatchedDialog
import io.github.impca201.trackerforsimkl.ui.theme.AppShapes
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.impca201.trackerforsimkl.R
import io.github.impca201.trackerforsimkl.data.AppSettings
import io.github.impca201.trackerforsimkl.data.model.Episode
import io.github.impca201.trackerforsimkl.data.model.EpisodeRef
import io.github.impca201.trackerforsimkl.data.model.Images
import io.github.impca201.trackerforsimkl.data.model.LibraryItem
import io.github.impca201.trackerforsimkl.data.model.ListStatus
import io.github.impca201.trackerforsimkl.data.model.MediaDetails
import io.github.impca201.trackerforsimkl.data.model.MediaIds
import io.github.impca201.trackerforsimkl.data.model.MediaType
import io.github.impca201.trackerforsimkl.ui.LocalNavigator
import io.github.impca201.trackerforsimkl.ui.Route
import io.github.impca201.trackerforsimkl.ui.components.AiringBadge
import io.github.impca201.trackerforsimkl.ui.theme.EmptyState
import io.github.impca201.trackerforsimkl.ui.components.Format
import io.github.impca201.trackerforsimkl.ui.components.Poster
import io.github.impca201.trackerforsimkl.ui.components.PosterRow
import io.github.impca201.trackerforsimkl.ui.components.openUrl
import io.github.impca201.trackerforsimkl.ui.components.rememberApp
import io.github.impca201.trackerforsimkl.ui.components.label
import io.github.impca201.trackerforsimkl.ui.components.share
import io.github.impca201.trackerforsimkl.ui.library.SectionHeader
import io.github.impca201.trackerforsimkl.ui.theme.Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale
import io.github.impca201.trackerforsimkl.ui.components.TextSkeleton

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
    val resources = LocalResources.current
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: resources.getString(R.string.detail_load_failed_fallback)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message?.let { resources.getString(R.string.detail_failure_reason, failure, it) } ?: failure)
            }
        }
    }

    val playback = library.playbackFor(route.type, route.simklId)
    var confirmPlayback by remember { mutableStateOf<Playback?>(null) }
    confirmPlayback?.let { pb ->
        ConfirmWatchedDialog(
            playback = pb,
            onConfirm = { run(resources.getString(R.string.detail_error_mark_watched)) { app.library.markPlaybackWatched(pb, item) } },
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.detail_back))
                    }
                },
                actions = {
                    // Putting a title in a list is the main thing to do here, so it sits in the title bar.
                    ListButton(
                        type = route.type,
                        item = item,
                        onStatus = { status ->
                            run(resources.getString(R.string.detail_error_update_list)) {
                                if (status == null) {
                                    app.library.remove(route.type, ids.simkl)
                                } else {
                                    app.library.setStatus(route.type, ids, title, poster, year, status)
                                }
                            }
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { context.share(title, webUrl(route.type, route.simklId, ids.slug)) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    ) { Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.detail_share)) }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = if (scrolled) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
                    ),
            )
        },
    ) { innerPadding ->
        // Fade through between the full-screen error and the content. The content draws behind the
        // transparent top bar (the fanart header); only the error keeps clear of it.
        AnimatedContent(
            targetState = details == null && error != null && item == null,
            transitionSpec = { fadeIn(Motion.fadeSpec(210, delayMillis = 90)) togetherWith fadeOut(Motion.fadeSpec(90)) },
            label = "detailContent",
            modifier = Modifier.fillMaxSize(),
        ) { showError ->
            if (showError) {
                EmptyState(
                    Icons.Rounded.ErrorOutline,
                    stringResource(R.string.detail_load_failed_title),
                    Modifier.padding(innerPadding),
                    body = error,
                ) {
                    Button(onClick = { reload++ }) { Text(stringResource(R.string.detail_try_again)) }
                }
                return@AnimatedContent
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
                        title = title,
                        item = item,
                        next = item?.let { Shelves.nextEpisode(it, settings.continueFromLatest, library.latestNext[it.key]) },
                        playback = playback,
                        onPlaybackWatched = { confirmPlayback = it },
                        onMovieWatched = { run(resources.getString(R.string.detail_error_mark_watched)) { app.library.markMovieWatched(ids, title, poster, year) } },
                        onWatchNext = { next ->
                            run(resources.getString(R.string.detail_error_mark_episode_watched, next.label)) {
                                app.library.setEpisodesWatched(route.type, ids, title, poster, listOf(next), true, episodes)
                            }
                        },
                        onCheckIn = { episode ->
                            run(resources.getString(R.string.detail_error_check_in)) {
                                app.library.checkIn(route.type, ids.simkl, episode)
                                snackbar.showSnackbar(resources.getString(R.string.detail_checked_in))
                            }
                        },
                    )
                }
                val d = details
                if (d == null) {
                    item(key = "loading") {
                        Box(Modifier.animateItem().fillMaxWidth()) {
                            Crossfade(targetState = error, animationSpec = Motion.fadeSpec(), label = "detailLoading") { failed ->
                                if (failed != null) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(failed, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Button(onClick = { reload++ }) { Text(stringResource(R.string.detail_try_again)) }
                                    }
                                } else {
                                    // Where the overview will be.
                                    TextSkeleton(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                                }
                            }
                        }
                    }
                } else {
                    item(key = "about") {
                        Box(Modifier.animateItem()) {
                            About(
                                d = d,
                                userRating = item?.userRating,
                                onRate = { rating -> run(resources.getString(R.string.detail_error_save_rating)) { app.library.rate(route.type, ids.simkl, rating) } },
                            )
                        }
                    }
                    if (d.trailers.isNotEmpty()) {
                        item(key = "trailers") { Box(Modifier.animateItem()) { Trailers(d) } }
                    }
                }
                val shownEpisodes = if (settings.hideSpecials) episodes.filterNot { it.special } else episodes
                // Only specials + "hide specials" leaves nothing; EpisodesBlock needs at least one season.
                if (route.type.isEpisodic && shownEpisodes.isNotEmpty()) {
                    episodesSection(
                        playback = playback,
                        onPlaybackWatched = { confirmPlayback = it },
                        episodes = shownEpisodes,
                        item = item,
                        onToggle = { refs, watched ->
                            // An empty change would still add the title to the library as Watching.
                            if (refs.isNotEmpty()) run(resources.getString(R.string.detail_error_update_episodes)) {
                                app.library.setEpisodesWatched(route.type, ids, title, poster, refs, watched, episodes)
                            }
                        },
                        onAllWatched = {
                            val refs =
                                episodes.filter { it.aired && !it.special && item?.isWatched(it.season, it.episode) != true }
                                    .map { EpisodeRef(it.season, it.episode) }
                            if (refs.isNotEmpty()) {
                                run(resources.getString(R.string.detail_error_mark_episodes_watched)) {
                                    app.library.setEpisodesWatched(route.type, ids, title, poster, refs, true, episodes)
                                }
                            }
                        },
                    )
                }
                if (d != null && d.recommendations.isNotEmpty()) {
                    item(key = "recs") {
                        Column(Modifier.animateItem()) {
                            SectionHeader(stringResource(R.string.detail_if_you_like), modifier = Modifier.padding(horizontal = 8.dp))
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
                val runtime = details?.runtime?.takeIf { it > 0 }?.let { stringResource(R.string.detail_runtime_min, it) }
                val meta =
                    listOfNotNull(
                        (details?.year ?: item?.year)?.toString(),
                        details?.animeType?.uppercase(),
                        runtime,
                        details?.certification,
                        details?.network,
                    ).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (type.isEpisodic && details != null) AiringBadge(details.airing)
            }
        }
    }
}

/**
 * The list button in the title bar: "Add to list", or the list the title is in. Opens the list
 * statuses, plus "Remove from my lists" once the title is in one.
 */
@Composable
private fun ListButton(
    type: MediaType,
    item: LibraryItem?,
    onStatus: (ListStatus?) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        if (item == null) {
            Button(onClick = { menu = true }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.detail_add_to_list), maxLines = 1)
            }
        } else {
            FilledTonalButton(onClick = { menu = true }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAddCheck, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(item.status.label(type), maxLines = 1)
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
                    text = { Text(stringResource(R.string.detail_remove_from_lists), color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menu = false
                        onStatus(null)
                    },
                )
            }
        }
    }
}

@Composable
private fun Actions(
    type: MediaType,
    title: String,
    item: LibraryItem?,
    next: NextEpisode?,
    playback: Playback?,
    onPlaybackWatched: (Playback) -> Unit,
    onMovieWatched: () -> Unit,
    onWatchNext: (EpisodeRef) -> Unit,
    onCheckIn: (EpisodeRef?) -> Unit,
) {
    var pendingCheckIn by remember { mutableStateOf<PendingCheckIn?>(null) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (type == MediaType.Movie) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // While the progress banner below is showing, its own Watched button does the same, so only one is shown.
                if (DetailActions.showMovieWatched(item?.status, playback)) {
                    FilledTonalButton(
                        onClick = onMovieWatched,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.detail_mark_as_watched))
                    }
                }
                OutlinedButton(onClick = { pendingCheckIn = PendingCheckIn(null) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.LiveTv, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.detail_check_in))
                }
            }
        }
        if (playback != null) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            playback.episode?.let { stringResource(R.string.detail_in_progress_episode, it.label, playback.progress.roundToInt()) }
                                ?: stringResource(R.string.detail_in_progress, playback.progress.roundToInt()),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        val progress by animateFloatAsState((playback.progress / 100).toFloat(), Motion.progressSpec(), label = "playbackProgress")
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        )
                    }
                    TextButton(onClick = { onPlaybackWatched(playback) }) { Text(stringResource(R.string.detail_mark_watched)) }
                }
            }
        }
        if (type.isEpisodic && next != null && item?.status == ListStatus.Watching) {
            // When the progress banner is on this very episode, its Watched button replaces this one.
            val showWatched = DetailActions.showNextEpisodeWatched(next.ref, playback)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showWatched) {
                    FilledTonalButton(
                        onClick = { onWatchNext(next.ref) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            next.title?.let { stringResource(R.string.detail_watched_episode_titled, next.ref.label, it) }
                                ?: stringResource(R.string.detail_watched_episode, next.ref.label),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // Alone in the row (the banner has the Watched button) it takes the full width.
                OutlinedButton(
                    onClick = { pendingCheckIn = PendingCheckIn(next.ref) },
                    modifier = if (showWatched) Modifier else Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.LiveTv, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.detail_check_in), maxLines = 1)
                }
            }
        }
        item?.memo?.let { memo ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(
                    stringResource(R.string.detail_your_note, memo),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
        }
        if (item != null && type.isEpisodic && item.totalEpisodes > 0) {
            Text(
                pluralStringResource(R.plurals.detail_episodes_watched, item.totalEpisodes, item.watchedEpisodes, item.totalEpisodes) +
                    (Format.ago(item.lastWatchedAt)?.let { " · " + stringResource(R.string.detail_last_watched, it) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    pendingCheckIn?.let { pending ->
        CheckInDialog(
            name = DetailActions.checkInName(title, pending.episode),
            onDismiss = { pendingCheckIn = null },
            onConfirm = {
                pendingCheckIn = null
                onCheckIn(pending.episode)
            },
        )
    }
}

/** What the person is about to check in to: a movie (no episode) or one episode. */
private data class PendingCheckIn(
    val episode: EpisodeRef?,
)

/** Says what checking in does before it happens, because the button on its own does not. */
@Composable
private fun CheckInDialog(
    name: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.LiveTv, null) },
        title = { Text(stringResource(R.string.detail_check_in_title)) },
        text = { Text(stringResource(R.string.detail_check_in_text, name)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.detail_check_in)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_cancel)) } },
    )
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
        title = { Text(stringResource(R.string.detail_your_rating)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value.toInt().toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(ratingWord(value.toInt())), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = value, onValueChange = { value = it }, valueRange = 1f..10f, steps = 8)
            }
        },
        confirmButton = { TextButton(onClick = { onRate(value.toInt()) }) { Text(stringResource(R.string.detail_save)) } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onRate(null) }) { Text(stringResource(R.string.detail_remove)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_cancel)) }
            }
        },
    )
}

@StringRes
private fun ratingWord(value: Int): Int =
    when (value) {
        1 -> R.string.detail_rating_1
        2 -> R.string.detail_rating_2
        3 -> R.string.detail_rating_3
        4 -> R.string.detail_rating_4
        5 -> R.string.detail_rating_5
        6 -> R.string.detail_rating_6
        7 -> R.string.detail_rating_7
        8 -> R.string.detail_rating_8
        9 -> R.string.detail_rating_9
        else -> R.string.detail_rating_10
    }

@Composable
private fun About(
    d: MediaDetails,
    userRating: Int?,
    onRate: (Int?) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        d.overview?.let { overview ->
            val stateText = stringResource(if (expanded) R.string.section_expanded else R.string.section_collapsed)
            Text(
                overview,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
                // Tells screen readers the text can be tapped to show all of it, as ExpandableSetting does.
                modifier =
                    Modifier
                        .animateContentSize()
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .semantics { stateDescription = stateText },
            )
        }
        // Ratings, rank and genres are information, not controls, so they sit in the info card.
        // Only the ratings and the first few facts show until the card is expanded.
        val facts: List<Fact> =
            listOfNotNull(
                d.rank?.let { Fact(Icons.Rounded.EmojiEvents, stringResource(R.string.detail_fact_rank), "#$it") },
                d.genres.takeIf { it.isNotEmpty() }?.let { genres ->
                    Fact(Icons.Rounded.Category, stringResource(R.string.detail_fact_genres), genres.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } })
                },
                d.status?.let {
                    Fact(Icons.Rounded.Info, stringResource(R.string.detail_fact_status), ShowStatus.fromApi(it)?.let { s -> stringResource(s.label) } ?: it.replaceFirstChar { c -> c.uppercase() })
                },
                d.airs?.let { Format.airs(it) }?.let { Fact(Icons.Rounded.Schedule, stringResource(R.string.detail_fact_airs), it) },
                d.firstAired?.let { date ->
                    Fact(
                        Icons.Rounded.CalendarMonth,
                        stringResource(if (d.type == MediaType.Movie) R.string.detail_fact_released else R.string.detail_fact_first_aired),
                        Format.date(date) ?: date,
                    )
                },
                d.totalEpisodes?.takeIf { it > 0 }?.let { Fact(Icons.Rounded.VideoLibrary, stringResource(R.string.detail_fact_episodes), it.toString()) },
                d.network?.let { Fact(Icons.Rounded.Tv, stringResource(R.string.detail_fact_network), it) },
                d.country?.let { Fact(Icons.Rounded.Public, stringResource(R.string.detail_fact_country), it) },
                d.director?.let { Fact(Icons.Rounded.Movie, stringResource(R.string.detail_fact_director), it) },
                d.budget?.let { Fact(Icons.Rounded.AttachMoney, stringResource(R.string.detail_fact_budget), money(it)) },
                d.revenue?.let { Fact(Icons.Rounded.AttachMoney, stringResource(R.string.detail_fact_box_office), money(it)) },
                d.alternativeTitles.takeIf { it.isNotEmpty() }?.let { Fact(Icons.Rounded.Translate, stringResource(R.string.detail_fact_also_known_as), it.take(4).joinToString(", ")) },
            )
        // Always shown: your own rating sits in it, next to the others.
        InfoCard(d.ratings, facts, userRating, onRate)
    }
}

/** One row of the info card: an icon, what it is and its value. */
private data class Fact(
    val icon: ImageVector,
    val label: String,
    val value: String,
)

/**
 * The information about a title. The ratings stand out as tiles at the top; the facts below them
 * show only the first few until the card is expanded.
 */
@Composable
private fun InfoCard(
    ratings: List<Rating>,
    facts: List<Fact>,
    userRating: Int?,
    onRate: (Int?) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var rating by remember { mutableStateOf(false) }
    val stateText = stringResource(if (expanded) R.string.section_expanded else R.string.section_collapsed)
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, Motion.fadeSpec(), label = "infoChevron")
    Surface(shape = AppShapes.Item, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().animateContentSize().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The other ratings scroll on the left; your rating is pinned on the right, so it stays in
            // view however many ratings there are. IntrinsicSize.Min gives every tile the height of
            // the tallest one (a tile with a vote count has an extra line).
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ratings.forEach { RatingTile(it, Modifier.fillMaxHeight()) }
                }
                YourRatingTile(userRating, Modifier.fillMaxHeight()) { rating = true }
            }
            DetailFacts.visible(facts, expanded).forEach { FactRow(it) }
            if (DetailFacts.canExpand(facts.size)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .semantics { stateDescription = stateText }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(if (expanded) R.string.detail_show_less else R.string.detail_show_more),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp).rotate(chevron), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (rating) {
        RatingDialog(userRating, onDismiss = { rating = false }) {
            rating = false
            onRate(it)
        }
    }
}

/** Your own rating, as a tile next to SIMKL's and the others'. Tap it to rate or change the rating. */
@Composable
private fun YourRatingTile(
    userRating: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxHeight().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(if (userRating != null) Icons.Rounded.Star else Icons.Rounded.StarOutline, null, Modifier.size(18.dp))
                Text(
                    userRating?.toString() ?: stringResource(R.string.detail_rate),
                    style = if (userRating != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(stringResource(R.string.detail_your_rating), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RatingTile(
    rating: Rating,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            Modifier.fillMaxHeight().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Star, null, Modifier.size(18.dp))
                Text(Format.rating(rating.value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(rating.source, style = MaterialTheme.typography.labelMedium)
            rating.votes?.let {
                Text(
                    stringResource(R.string.detail_votes, compact(it)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun FactRow(fact: Fact) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
        Icon(fact.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(fact.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp))
        Text(fact.value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private fun money(value: Long): String =
    when {
        value >= 1_000_000_000 -> "$%.2fB".format(value / 1_000_000_000.0)
        value >= 1_000_000 -> "$%.1fM".format(value / 1_000_000.0)
        else -> "$%,d".format(value)
    }

/** Locale-aware short vote count ("12K", "12 k", "1,2 Mio."). */
private fun compact(votes: Int): String =
    CompactDecimalFormat.getInstance(Locale.getDefault(), CompactDecimalFormat.CompactStyle.SHORT).format(votes.toLong())

@Composable
private fun Trailers(d: MediaDetails) {
    val context = LocalContext.current
    Column {
        SectionHeader(stringResource(R.string.detail_trailers), modifier = Modifier.padding(horizontal = 8.dp))
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
                            Icon(Icons.Rounded.PlayArrow, stringResource(R.string.detail_play), Modifier.padding(8.dp))
                        }
                    }
                    // One line even without a name, so the row keeps its height while it scrolls.
                    Text(t.name.orEmpty(), style = MaterialTheme.typography.labelMedium, minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    // Opens on the season being watched; fixed once the library entry is known, so marking
    // episodes doesn't make it jump. A season the user picked is kept.
    val defaultSeason = Shelves.startSeason(item, playback?.episode, seasons.keys) ?: seasons.keys.first()
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val hasItem = item != null
    LaunchedEffect(hasItem) { if (hasItem && selected == null) selected = defaultSeason }
    val current = selected?.takeIf { it in seasons } ?: defaultSeason
    val chips = rememberLazyListState()
    LaunchedEffect(current, seasons.size) {
        // Bring the chosen season's chip into view (it can be far right, e.g. season 6).
        val index = seasons.keys.indexOf(current)
        val info = chips.layoutInfo
        val visible = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (index >= 0 && (visible == null || visible.offset < info.viewportStartOffset || visible.offset + visible.size > info.viewportEndOffset)) {
            chips.animateScrollToItem(index)
        }
    }
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
    // Episodes whose full description is shown; tapping a row expands it, the checkbox marks it.
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }

    Column(Modifier.padding(top = 8.dp)) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(stringResource(R.string.detail_episodes), modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.detail_season_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.detail_mark_season_watched)) },
                        leadingIcon = { Icon(Icons.Rounded.DoneAll, null) },
                        onClick = {
                            menu = false
                            onToggle(list.filter { it.aired && !it.watched() }.map { it.ref() }, true)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.detail_mark_all_aired_watched)) },
                        leadingIcon = { Icon(Icons.Rounded.DoneAll, null) },
                        onClick = {
                            menu = false
                            onAllWatched()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.detail_mark_season_unwatched)) },
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
            LazyRow(state = chips, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(seasons.keys.toList()) { n ->
                    val eps = seasons[n].orEmpty()
                    val done = eps.isNotEmpty() && eps.all { e -> item?.isWatched(e.season, e.episode) == true }
                    FilterChip(
                        selected = n == current,
                        onClick = { selected = n },
                        label = { Text(if (n == 0) stringResource(R.string.detail_specials) else stringResource(R.string.detail_season_n, n)) },
                        leadingIcon = if (done) ({ Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp)) }) else null,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        list.forEach { e ->
            val watched = e.watched()
            val key = "${e.season}:${e.episode}:${e.special}"
            val open = key in expanded
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { expanded = if (open) expanded - key else expanded + key },
                        onLongClick =
                            if (e.aired) {
                                {
                                    // Long-press: everything up to and including this episode.
                                    val upTo = list.takeWhile { it != e } + e
                                    onToggle(upTo.filter { it.aired && !it.watched() }.map { it.ref() }, true)
                                }
                            } else {
                                null
                            },
                    ).animateContentSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .alpha(if (e.aired) 1f else 0.55f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = if (open) Alignment.Top else Alignment.CenterVertically,
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
                    Text(
                        e.title ?: stringResource(R.string.detail_episode_n, e.episode),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = if (open) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val date = io.github.impca201.trackerforsimkl.data.model.Dates.parse(e.date)
                    if (date != null) {
                        Text(
                            if (e.aired) Format.date(date) else stringResource(R.string.detail_airs_when, Format.relative(date)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!watched && playback != null && playback.episode == e.ref()) {
                        Text(
                            stringResource(R.string.detail_in_progress, playback.progress.roundToInt()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        val progress by animateFloatAsState((playback.progress / 100).toFloat(), Motion.progressSpec(), label = "episodeProgress")
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                        )
                    }
                    e.description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (open) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Checkbox(checked = watched, onCheckedChange = if (e.aired) ({ toggle(e, it) }) else null, enabled = e.aired)
            }
        }
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
        SectionHeader(stringResource(R.string.detail_links), modifier = Modifier.padding(horizontal = 8.dp))
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
