package io.github.impca201.simklr.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.impca201.simklr.data.model.MediaType
import io.github.impca201.simklr.ui.components.Format
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
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.impca201.simklr.data.LibraryState
import io.github.impca201.simklr.data.model.AiringState
import io.github.impca201.simklr.data.model.LibraryItem
import io.github.impca201.simklr.data.model.Shelves
import io.github.impca201.simklr.ui.components.AiringBadge
import io.github.impca201.simklr.ui.components.Poster
import io.github.impca201.simklr.ui.components.PosterTile
import io.github.impca201.simklr.ui.main.FloatingBarSpace

enum class SortOrder(
    val label: String,
) {
    Recent("Recently watched"),
    Added("Recently added"),
    Title("Title"),
    Rating("My rating"),
    Release("Release date"),
    ;

    fun sort(items: List<LibraryItem>): List<LibraryItem> =
        when (this) {
            Recent -> items.sortedByDescending { it.lastWatchedAt ?: it.addedAt ?: "" }
            Added -> items.sortedByDescending { it.addedAt ?: "" }
            Title -> items.sortedBy { it.title.lowercase() }
            Rating -> items.sortedByDescending { it.userRating ?: 0 }
            // The library only carries the release year; titles keep a stable order within a year.
            Release -> items.sortedWith(compareByDescending<LibraryItem> { it.year ?: 0 }.thenBy { it.title.lowercase() })
        }
}

/** Ended or still running (TV, anime); released or upcoming (movies). */
enum class ReleaseFilter(
    val showLabel: String,
    val movieLabel: String,
) {
    All("All", "All"),
    Running("Still airing", "Upcoming"),
    Finished("Ended", "Released"),
}

enum class RatingFilter(
    val label: String,
) {
    Any("Any"),
    Rated("Rated by me"),
    Unrated("Not rated"),
}

data class LibraryFilter(
    val release: ReleaseFilter = ReleaseFilter.All,
    val rating: RatingFilter = RatingFilter.Any,
    /** SIMKL anime_type ("tv", "movie", "ova", ...), anime only. */
    val animeType: String? = null,
) {
    val active: Boolean get() = this != LibraryFilter()

    fun apply(
        items: List<LibraryItem>,
        state: LibraryState,
    ): List<LibraryItem> =
        items.filter { item ->
            val info = state.showInfo[item.key]
            val releaseOk =
                when (release) {
                    ReleaseFilter.All -> true
                    ReleaseFilter.Running ->
                        if (item.type == MediaType.Movie) {
                            info?.releaseStatus != null && info.releaseStatus != "released"
                        } else {
                            Shelves.airingOf(item, info, state.nextAirDate(item)) == AiringState.Airing
                        }
                    ReleaseFilter.Finished ->
                        if (item.type == MediaType.Movie) {
                            info?.releaseStatus == "released"
                        } else {
                            Shelves.airingOf(item, info, state.nextAirDate(item)) == AiringState.Ended
                        }
                }
            val ratingOk =
                when (rating) {
                    RatingFilter.Any -> true
                    RatingFilter.Rated -> item.userRating != null
                    RatingFilter.Unrated -> item.userRating == null
                }
            releaseOk && ratingOk && (animeType == null || item.animeType.equals(animeType, ignoreCase = true))
        }

    /** Compact form for rememberSaveable: "release|rating|animeType". */
    fun encode(): String = "${release.name}|${rating.name}|${animeType.orEmpty()}"

    companion object {
        val animeTypes = listOf("tv" to "TV", "movie" to "Movie", "ova" to "OVA", "ona" to "ONA", "special" to "Special")

        fun decode(value: String): LibraryFilter {
            val parts = value.split("|")
            return LibraryFilter(
                release = ReleaseFilter.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: ReleaseFilter.All,
                rating = RatingFilter.entries.firstOrNull { it.name == parts.getOrNull(1) } ?: RatingFilter.Any,
                animeType = parts.getOrNull(2)?.ifEmpty { null },
            )
        }
    }
}

/**
 * Sort and filter of each tab of a library screen, kept separately per tab for the session.
 * Indexes are tab positions.
 */
class TabOptions(
    private val sorts: androidx.compose.runtime.MutableState<List<String>>,
    private val filters: androidx.compose.runtime.MutableState<List<String>>,
) {
    /** Changes whenever any tab's sort or filter changes (a remember key). */
    val version: Any get() = sorts.value to filters.value

    fun sort(tab: Int): SortOrder = SortOrder.entries.firstOrNull { it.name == sorts.value.getOrNull(tab) } ?: SortOrder.Recent

    fun filter(tab: Int): LibraryFilter = LibraryFilter.decode(filters.value.getOrNull(tab).orEmpty())

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
fun rememberTabOptions(defaultSorts: List<SortOrder>): TabOptions {
    val sorts = rememberSaveable { mutableStateOf(defaultSorts.map { it.name }) }
    val filters = rememberSaveable { mutableStateOf(defaultSorts.map { "" }) }
    return remember(sorts, filters) { TabOptions(sorts, filters) }
}

/**
 * The most used filters as chips above a tab's list: still airing / ended (upcoming / released
 * for movies) and, for anime, the type. Tapping a selected chip clears it.
 */
@Composable
fun QuickFilterChips(
    type: MediaType,
    filter: LibraryFilter,
    onFilter: (LibraryFilter) -> Unit,
) {
    val release =
        if (type == MediaType.Movie) {
            listOf(ReleaseFilter.Finished to "Released", ReleaseFilter.Running to "Upcoming")
        } else {
            listOf(ReleaseFilter.Running to "Still airing", ReleaseFilter.Finished to "Ended")
        }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(release) { (value, label) ->
            FilterChip(
                selected = filter.release == value,
                onClick = { onFilter(filter.copy(release = if (filter.release == value) ReleaseFilter.All else value)) },
                label = { Text(label) },
            )
        }
        if (type == MediaType.Anime) {
            items(LibraryFilter.animeTypes) { (key, label) ->
                FilterChip(
                    selected = filter.animeType == key,
                    onClick = { onFilter(filter.copy(animeType = if (filter.animeType == key) null else key)) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
fun bottomPadding(): Dp = FloatingBarSpace + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Top bar button opening the sort and rating-filter sheet; shows a dot while any filter is on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortFilterButton(
    type: MediaType,
    sort: SortOrder,
    onSort: (SortOrder) -> Unit,
    filter: LibraryFilter,
    onFilter: (LibraryFilter) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        BadgedBox(badge = { if (filter.active) Badge() }) {
            Icon(Icons.Rounded.Tune, contentDescription = "Sort and filter")
        }
    }
    if (!open) return
    ModalBottomSheet(onDismissRequest = { open = false }) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sort by", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            SortOrder.entries.forEach { order ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSort(order) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = order == sort, onClick = { onSort(order) })
                    Text(order.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text("My rating", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            ChipRow(RatingFilter.entries, filter.rating, { it.label }) { onFilter(filter.copy(rating = it)) }
            if (filter.active) {
                TextButton(onClick = { onFilter(LibraryFilter()) }, modifier = Modifier.align(Alignment.End)) { Text("Clear filters") }
            }
        }
    }
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
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
                    nextAir?.let { "Next ${Format.relative(it)}" }
                        ?: listOfNotNull(
                            item.year?.toString(),
                            item.userRating?.let { "★ $it" },
                            if (item.type.isEpisodic && item.totalEpisodes > 0) "${item.watchedEpisodes}/${item.totalEpisodes}" else null,
                        ).joinToString(" · ").ifEmpty { null },
                // A paused movie shows how far it got.
                progress = playback?.takeIf { item.type == MediaType.Movie }?.let { (it.progress / 100).toFloat() } ?: if (showProgress) progressOf(item) else null,
                rating = state.showInfo[item.key]?.rating?.takeIf { it > 0 },
                airing =
                    if (showAiring) Shelves.airingOf(item, state.showInfo[item.key], state.nextAirDate(item)) else AiringState.Unknown,
                onClick = { onOpen(item) },
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    count: Int? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        if (count != null) Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        shape = RoundedCornerShape(20.dp),
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
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    )
                }
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { trailing() }
        }
    }
}
