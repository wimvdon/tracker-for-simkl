package io.github.impca201.trackerforsimkl.ui.library

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import io.github.impca201.trackerforsimkl.R
import io.github.impca201.trackerforsimkl.ui.theme.AppShapes
import io.github.impca201.trackerforsimkl.ui.theme.Motion
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.impca201.trackerforsimkl.ui.components.Format
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.impca201.trackerforsimkl.data.LibraryState
import io.github.impca201.trackerforsimkl.data.model.AiringState
import io.github.impca201.trackerforsimkl.data.model.LibraryItem
import io.github.impca201.trackerforsimkl.data.model.MovieShelf
import io.github.impca201.trackerforsimkl.data.model.Shelf
import io.github.impca201.trackerforsimkl.data.model.Shelves
import io.github.impca201.trackerforsimkl.ui.components.AiringBadge
import io.github.impca201.trackerforsimkl.ui.components.Poster
import io.github.impca201.trackerforsimkl.ui.components.PosterTile
import io.github.impca201.trackerforsimkl.ui.main.LocalNavigationBarShown

enum class SortOrder(
    @StringRes val label: Int,
) {
    Recent(R.string.lib_sort_recent),
    Added(R.string.lib_sort_added),
    Title(R.string.lib_sort_title),
    Rating(R.string.lib_filter_my_rating),
    Release(R.string.lib_sort_release),
    EpisodesLeft(R.string.lib_sort_episodes_left),
    ;

    fun sort(items: List<LibraryItem>): List<LibraryItem> =
        when (this) {
            Recent -> items.sortedByDescending { it.lastWatchedAt ?: it.addedAt ?: "" }
            Added -> items.sortedByDescending { it.addedAt ?: "" }
            Title -> items.sortedBy { it.title.lowercase() }
            // Unrated titles go last; equal ratings by title.
            Rating -> items.sortedWith(compareByDescending<LibraryItem> { it.userRating ?: 0 }.thenBy { it.title.lowercase() })
            // The library only carries the release year; titles keep a stable order within a year.
            Release -> items.sortedWith(compareByDescending<LibraryItem> { it.year ?: 0 }.thenBy { it.title.lowercase() })
            // Closest to caught up first; most recently watched among equals.
            EpisodesLeft ->
                items.sortedWith(
                    compareBy<LibraryItem> { it.airedUnwatched }.thenByDescending { it.lastWatchedAt ?: it.addedAt ?: "" },
                )
        }
}

/**
 * What a library tab offers: its sort orders (the first is the default) and which filters make
 * sense. My rating only where titles were watched (nobody rates what they haven't seen); airing
 * or ended only for shows; the anime type only on Anime.
 */
data class TabSpec(
    val sorts: List<SortOrder>,
    val rating: Boolean,
    val airing: Boolean,
    val animeTypes: Boolean,
) {
    val defaultSort: SortOrder get() = sorts.first()

    /** False when the tab has nothing to filter by, so the filter button is left out. */
    val hasFilters: Boolean get() = rating || airing || animeTypes

    /** [filter] without the parts this tab doesn't offer. */
    fun allowed(filter: LibraryFilter): LibraryFilter =
        LibraryFilter(
            animeType = filter.animeType.takeIf { animeTypes },
            rating = if (rating) filter.rating else RatingFilter.Any,
            airing = if (airing) filter.airing else AiringFilter.Any,
        )

    /** [sort] if this tab offers it, else its default. */
    fun allowed(sort: SortOrder): SortOrder = sort.takeIf { it in sorts } ?: defaultSort

    companion object {
        private val all = listOf(SortOrder.Recent, SortOrder.Added, SortOrder.Title, SortOrder.Rating, SortOrder.Release)
        private val unwatched = listOf(SortOrder.Added, SortOrder.Title, SortOrder.Release)

        fun of(
            shelf: Shelf,
            anime: Boolean,
        ): TabSpec =
            when (shelf) {
                Shelf.UpNext ->
                    TabSpec(listOf(SortOrder.Recent, SortOrder.EpisodesLeft, SortOrder.Added, SortOrder.Title, SortOrder.Release), rating = false, airing = true, animeTypes = anime)
                Shelf.PlanToWatch -> TabSpec(unwatched, rating = false, airing = true, animeTypes = anime)
                Shelf.Watched, Shelf.OnHold, Shelf.Dropped -> TabSpec(all, rating = true, airing = true, animeTypes = anime)
            }

        fun of(shelf: MovieShelf): TabSpec =
            when (shelf) {
                MovieShelf.PlanToWatch -> TabSpec(unwatched, rating = false, airing = false, animeTypes = false)
                MovieShelf.Completed, MovieShelf.Dropped -> TabSpec(all, rating = true, airing = false, animeTypes = false)
            }
    }
}

/** The my-rating filter of a library tab. */
enum class RatingFilter(
    @StringRes val label: Int,
) {
    Any(R.string.lib_filter_all),
    Rated(R.string.lib_filter_rated),
    Unrated(R.string.lib_filter_unrated),
}

/** Shows only: still airing or ended. Shows whose state isn't known yet only appear under Any. */
enum class AiringFilter(
    @StringRes val label: Int,
) {
    Any(R.string.lib_filter_all),
    Airing(R.string.lib_badge_airing),
    Ended(R.string.lib_badge_ended),
}

/**
 * The filter of a library tab: my rating, airing or ended, and for anime SIMKL's anime_type
 * ("tv", "movie", "ova", ...), null for all.
 */
data class LibraryFilter(
    val animeType: String? = null,
    val rating: RatingFilter = RatingFilter.Any,
    val airing: AiringFilter = AiringFilter.Any,
) {
    val active: Boolean get() = animeType != null || rating != RatingFilter.Any || airing != AiringFilter.Any

    /** [airingOf] gives a show's airing state; only needed when [airing] is set. */
    fun apply(
        items: List<LibraryItem>,
        airingOf: (LibraryItem) -> AiringState = { AiringState.Unknown },
    ): List<LibraryItem> =
        items.filter { item ->
            val ratingOk =
                when (rating) {
                    RatingFilter.Any -> true
                    RatingFilter.Rated -> item.userRating != null
                    RatingFilter.Unrated -> item.userRating == null
                }
            val airingOk =
                when (airing) {
                    AiringFilter.Any -> true
                    AiringFilter.Airing -> airingOf(item) == AiringState.Airing
                    AiringFilter.Ended -> airingOf(item) == AiringState.Ended
                }
            ratingOk && airingOk && (animeType == null || item.animeType.equals(animeType, ignoreCase = true))
        }

    /** Compact form for rememberSaveable: "animeType|rating|airing". */
    fun encode(): String = "${animeType.orEmpty()}|${rating.name}|${airing.name}"

    companion object {
        /** SIMKL anime_type and its label. */
        val animeTypes =
            listOf(
                "tv" to R.string.lib_anime_type_tv,
                "movie" to R.string.lib_anime_type_movie,
                "ova" to R.string.lib_anime_type_ova,
                "ona" to R.string.lib_anime_type_ona,
                "special" to R.string.lib_anime_type_special,
            )

        fun decode(value: String): LibraryFilter {
            val parts = value.split("|")
            return LibraryFilter(
                animeType = parts[0].ifEmpty { null },
                rating = RatingFilter.entries.firstOrNull { it.name == parts.getOrNull(1) } ?: RatingFilter.Any,
                airing = AiringFilter.entries.firstOrNull { it.name == parts.getOrNull(2) } ?: AiringFilter.Any,
            )
        }
    }
}

/**
 * Sort and filter of each tab of a library screen, kept separately per tab for the session.
 * Indexes are tab positions; [specs] say what each tab offers.
 */
class TabOptions(
    private val specs: List<TabSpec>,
    private val sorts: androidx.compose.runtime.MutableState<List<String>>,
    private val filters: androidx.compose.runtime.MutableState<List<String>>,
) {
    /** Changes whenever any tab's sort or filter changes (a remember key). */
    val version: Any get() = sorts.value to filters.value

    fun spec(tab: Int): TabSpec = specs[tab]

    fun sort(tab: Int): SortOrder = specs[tab].allowed(SortOrder.entries.firstOrNull { it.name == sorts.value.getOrNull(tab) } ?: specs[tab].defaultSort)

    fun filter(tab: Int): LibraryFilter = specs[tab].allowed(LibraryFilter.decode(filters.value.getOrNull(tab).orEmpty()))

    fun setSort(
        tab: Int,
        value: SortOrder,
    ) {
        sorts.value = sorts.value.toMutableList().also { it[tab] = value.name }
    }

    fun setFilter(
        tab: Int,
        value: LibraryFilter,
    ) {
        filters.value = filters.value.toMutableList().also { it[tab] = value.encode() }
    }
}

@Composable
fun rememberTabOptions(specs: List<TabSpec>): TabOptions {
    val sorts = rememberSaveable { mutableStateOf(specs.map { it.defaultSort.name }) }
    val filters = rememberSaveable { mutableStateOf(specs.map { "" }) }
    return remember(sorts, filters) { TabOptions(specs, sorts, filters) }
}

/** Room below a destination's content: the docked navigation bar, if shown, covers the system bar. */
@Composable
fun bottomPadding(): Dp =
    8.dp + if (LocalNavigationBarShown.current) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** The sort button of a library tab's title bar: a menu of sort orders, the current one ticked. */
@Composable
fun SortButton(
    sort: SortOrder,
    choices: List<SortOrder>,
    onSort: (SortOrder) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.lib_sort)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    onClick = {
                        open = false
                        onSort(option)
                    },
                    trailingIcon = { if (option == sort) Icon(Icons.Rounded.Check, null) },
                )
            }
        }
    }
}

/**
 * The filter button of a library tab's title bar: a menu with the filters the tab offers (see
 * [TabSpec]), the current choices ticked. A dot on the button shows that a filter is on.
 */
@Composable
fun FilterButton(
    filter: LibraryFilter,
    spec: TabSpec,
    onFilter: (LibraryFilter) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            BadgedBox(badge = { if (filter.active) Badge() }) {
                Icon(Icons.Rounded.FilterList, contentDescription = stringResource(R.string.lib_filter))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            var first = true

            @Composable
            fun <T> section(
                header: Int,
                options: List<T>,
                label: (T) -> Int,
                selected: (T) -> Boolean,
                pick: (T) -> LibraryFilter,
            ) {
                if (!first) HorizontalDivider()
                first = false
                MenuHeader(stringResource(header))
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label(option))) },
                        onClick = {
                            open = false
                            onFilter(pick(option))
                        },
                        trailingIcon = { if (selected(option)) Icon(Icons.Rounded.Check, null) },
                    )
                }
            }
            if (spec.rating) {
                section(R.string.lib_filter_my_rating, RatingFilter.entries, { it.label }, { it == filter.rating }) { filter.copy(rating = it) }
            }
            if (spec.airing) {
                section(R.string.lib_filter_airing_header, AiringFilter.entries, { it.label }, { it == filter.airing }) { filter.copy(airing = it) }
            }
            if (spec.animeTypes) {
                val types = listOf<Pair<String?, Int>>(null to R.string.lib_filter_all) + LibraryFilter.animeTypes
                section(R.string.lib_filter_type_header, types, { it.second }, { it.first == filter.animeType }) { filter.copy(animeType = it.first) }
            }
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
fun TabLabel(
    label: String,
    count: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, maxLines = 1)
        if (count > 0) {
            Badge(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) { Text(count.toString()) }
        }
    }
}

fun progressOf(item: LibraryItem): Float? =
    if (item.type.isEpisodic && item.totalEpisodes > 0) (item.watchedEpisodes.toFloat() / item.totalEpisodes).coerceIn(0f, 1f) else null

/** Poster grid of library items, with airing badges, ratings and the next air date. */
@Composable
fun LibraryGrid(
    items: List<LibraryItem>,
    state: LibraryState,
    onOpen: (LibraryItem) -> Unit,
    showProgress: Boolean,
    showAiring: Boolean,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = bottomPadding()),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(items, key = { it.key }) { item ->
            val playback = state.playbackFor(item.type, item.ids.simkl)
            val nextAir = if (item.type.isEpisodic) state.nextAirDate(item) else null
            PosterTile(
                title = item.title,
                poster = item.poster,
                subtitle =
                    nextAir?.let { stringResource(R.string.lib_next_air, Format.relative(it)) }
                        ?: listOfNotNull(
                            item.year?.toString(),
                            item.userRating?.let { "★ $it" },
                            if (item.type.isEpisodic && item.totalEpisodes > 0) "${item.watchedEpisodes}/${item.totalEpisodes}" else null,
                        ).joinToString(" · ").ifEmpty { null },
                // A title being watched shows how far the movie or episode got.
                progress = playback?.let { (it.progress / 100).toFloat() } ?: if (showProgress) progressOf(item) else null,
                rating = state.showInfo[item.key]?.rating?.takeIf { it > 0 },
                airing =
                    if (showAiring) Shelves.airingOf(item, state.showInfo[item.key], state.nextAirDate(item)) else AiringState.Unknown,
                onClick = { onOpen(item) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    count: Int? = null,
    modifier: Modifier = Modifier,
    /** Opens the full list; the header then ends in a ">" and the whole row is tappable. */
    onOpen: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen) else Modifier)
            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (count != null) Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onOpen != null) {
            Box(Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.lib_see_all),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Wide card used by the "Up next" and "Waiting" lists. */
@Composable
fun ShowRow(
    item: LibraryItem,
    headline: String?,
    supporting: String?,
    airing: AiringState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        shape = AppShapes.Item,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Poster(item.poster, item.title, Modifier.width(68.dp), shape = RoundedCornerShape(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    AiringBadge(airing)
                }
                if (headline != null) {
                    Text(
                        headline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                progressOf(item)?.let { p ->
                    val animated by animateFloatAsState(p, Motion.progressSpec(), label = "showProgress")
                    LinearProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    )
                }
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { trailing() }
        }
    }
}
